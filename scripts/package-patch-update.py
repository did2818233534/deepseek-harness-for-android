#!/usr/bin/env python3
"""Publish full APK + optional previous-build patch; retain exact bases outside Git."""
import argparse, hashlib, importlib.util, json, shutil
from pathlib import Path
root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('delta',root/'scripts/make-apk-patch.py');delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--outputs',type=Path,default=root/'app/build/outputs/apk/full/claudeTest')
p.add_argument('--abi',default='arm64-v8a');p.add_argument('--bootstrap',action='store_true',help='First updater-capable build; no phone-usable patch is published')
a=p.parse_args();meta=json.loads((a.outputs/'output-metadata.json').read_text())
item=next(x for x in meta['elements'] if any(f.get('value')==a.abi for f in x['filters']))
source=a.outputs/item['outputFile'];code=item['versionCode'];package=meta['applicationId'];label=f'{package}-{a.abi}'
archive=root/'.local/patch-bases'/label;archive.mkdir(parents=True,exist_ok=True)
index=archive/'latest.json';before=json.loads(index.read_text()) if index.exists() else None
if before and code<=before['versionCode']:raise ValueError('Increment versionCode before publishing a new update')
if not before and not a.bootstrap:raise ValueError('No exact updater-capable base; use --bootstrap for first build')
out=root/'dist'/f'patch-update-{code}-{a.abi}';out.mkdir(parents=True,exist_ok=True)
apk=out/f'DSH-{code}-{a.abi}.apk';shutil.copyfile(source,apk)
if before and not a.bootstrap:
    base=archive/before['file'];assert hashlib.sha256(base.read_bytes()).hexdigest()==before['sha256']
    delta.create(base,apk,out/f'DSH-{before["versionCode"]}-to-{code}-{a.abi}.dshpatch')
sha=hashlib.sha256(apk.read_bytes()).hexdigest();saved=archive/f'{code}-{sha}.apk';shutil.copyfile(apk,saved)
record={'package':package,'abi':a.abi,'versionCode':code,'versionName':item['versionName'],'sha256':sha,'file':saved.name}
index.write_text(json.dumps(record,indent=2)+'\n')
(out/'release.json').write_text(json.dumps(record|{'file':apk.name,'baseVersionCode':before['versionCode'] if before and not a.bootstrap else None},indent=2)+'\n')
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in sorted(out.iterdir()) if f.name!='SHA256SUMS'))
print(out)
