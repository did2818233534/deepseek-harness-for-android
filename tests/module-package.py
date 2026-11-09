#!/usr/bin/env python3
"""Release workflow checks: paired artifacts, host fallback, exact bases and version gates."""
import importlib.util,json,sys,tempfile,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('pack',root/'scripts/package-module-update.py');pack=importlib.util.module_from_spec(spec);spec.loader.exec_module(pack)
with tempfile.TemporaryDirectory(dir=root/'.local',prefix='module-package-') as tmp:
 pack.ROOT=Path(tmp);outputs=pack.ROOT/'outputs';outputs.mkdir()
 def build(version,code,resource=b'unchanged'):
  with zipfile.ZipFile(outputs/'app.apk','w') as z:
   z.writestr('assets/dsh-module.properties',f'moduleVersion={version}\nhostContract=unit-host\n')
   z.writestr('classes.dex',f'business-{version}')
   z.writestr('resources.arsc',resource)
  (outputs/'output-metadata.json').write_text(json.dumps({'applicationId':'unit.test','elements':[{'filters':[{'value':'arm64-v8a'}],'outputFile':'app.apk','versionCode':code,'versionName':str(code)}]}))
 def publish(bootstrap=False):
  sys.argv=['package-module-update','--outputs',str(outputs)]+(['--bootstrap'] if bootstrap else [])
  pack.main()
 build(4,4);publish(True)
 build(5,5);publish()
 release=json.loads((pack.ROOT/'dist/module-update-5-arm64-v8a/release.json').read_text())
 assert not release['requiresHostInstall'] and release['patch'].endswith('.dshota')
 build(6,6,b'new host resource');publish()
 release=json.loads((pack.ROOT/'dist/module-update-6-arm64-v8a/release.json').read_text())
 assert release['requiresHostInstall'] and release['hostPatch'].endswith('.dshpatch')
 build(7,6)
 try:publish();raise AssertionError('must reject unchanged APK version')
 except ValueError:pass
 build(7,7,b'new host resource')
 index=pack.ROOT/'.local/module-bases/unit.test-arm64-v8a/latest.json'
 record=json.loads(index.read_text());(index.parent/record['file']).write_bytes(b'corrupt baseline')
 try:publish();raise AssertionError('must reject changed archive')
 except ValueError:pass
print('PASS: bootstrap, compatible OTA + APK, host patch + APK, monotonic versions, exact-base integrity')
