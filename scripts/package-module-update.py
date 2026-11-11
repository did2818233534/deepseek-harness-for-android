#!/usr/bin/env python3
"""Publish a full APK and, when host-compatible, an exact-base dynamic OTA patch."""
import argparse, hashlib, importlib.util, json, shutil, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('delta',ROOT/'scripts/make-apk-patch.py')
delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)
def properties(apk):
    with zipfile.ZipFile(apk) as z:
        return dict(line.split('=',1) for line in z.read('assets/dsh-module.properties').decode().splitlines() if '=' in line)
def immutable(apk):
    with zipfile.ZipFile(apk) as z:
        return {i.filename:hashlib.sha256(z.read(i)).hexdigest() for i in z.infolist()
                if not i.is_dir() and not i.filename.startswith('META-INF/')
                and not (i.filename.startswith('classes') and i.filename.endswith('.dex'))
                and i.filename not in {'AndroidManifest.xml','assets/dsh-module.properties','assets/runtime/main.cjs','assets/runtime/bootstrap.cjs','assets/reasoning-models.json'}}
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--outputs',type=Path,default=ROOT/'app/build/outputs/apk/full/claudeTest')
    p.add_argument('--abi',default='arm64-v8a');p.add_argument('--bootstrap',action='store_true')
    a=p.parse_args();meta=json.loads((a.outputs/'output-metadata.json').read_text())
    item=next(x for x in meta['elements'] if any(f.get('value')==a.abi for f in x['filters']))
    source=a.outputs/item['outputFile'];info=properties(source);version=int(info['moduleVersion'])
    archive=ROOT/'.local/module-bases'/f'{meta["applicationId"]}-{a.abi}';archive.mkdir(parents=True,exist_ok=True)
    index=archive/'latest.json';previous=json.loads(index.read_text()) if index.exists() else None
    if previous and version<=previous['moduleVersion']:raise ValueError('moduleVersion must increase')
    if previous and item['versionCode']<=previous['versionCode']:raise ValueError('APK versionCode must also increase')
    if not previous and not a.bootstrap:raise ValueError('First host release requires --bootstrap')
    out=ROOT/'dist'/f'module-update-{version}-{a.abi}';out.mkdir(parents=True,exist_ok=True)
    apk=out/f'DSH-{version}-{a.abi}.apk';shutil.copyfile(source,apk)
    record={'package':meta['applicationId'],'abi':a.abi,'moduleVersion':version,'versionCode':item['versionCode'],
            'versionName':item['versionName'],'hostContract':info['hostContract'],'requiresHostInstall':True}
    if previous and not a.bootstrap:
        base=archive/previous['file']
        if hashlib.sha256(base.read_bytes()).hexdigest()!=previous['sha256']:raise ValueError('Archived base is corrupt')
        if info['hostContract']==previous['hostContract'] and immutable(base)==immutable(apk):
            patch=out/f'DSH-{previous["moduleVersion"]}-to-{version}-{a.abi}.dshota'
            delta.create(base,apk,patch)
            record.update(requiresHostInstall=False,baseModuleVersion=previous['moduleVersion'],patch=patch.name)
        else:
            record['reason']='Host contract, Android resources or native runtime changed; install full APK'
            # A host delta still reduces transfer size, but Android installation remains necessary.
            patch=out/f'DSH-host-{previous["moduleVersion"]}-to-{version}-{a.abi}.dshpatch'
            delta.create(base,apk,patch);record['hostPatch']=patch.name
    sha=hashlib.sha256(apk.read_bytes()).hexdigest();saved=archive/f'{version}-{sha}.apk';shutil.copyfile(apk,saved)
    record.update(sha256=sha,file=apk.name)
    (out/'release.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n')
    index.write_text(json.dumps(record|{'file':saved.name},ensure_ascii=False,indent=2)+'\n')
    (out/'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in sorted(out.iterdir()) if f.name!='SHA256SUMS'))
    print(out)
if __name__=='__main__':main()
