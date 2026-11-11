#!/usr/bin/env python3
"""Build local signed negative OTA fixtures without recompressing native runtimes.
Run after archiving host4-x86.apk and module5-x86.apk in .local/module-validation.
Never uses production signing material; only the local Android debug key.
"""
import hashlib, importlib.util, shutil, subprocess, zipfile, struct
from pathlib import Path
root=Path(__file__).resolve().parent.parent
work=root/'.local/module-validation';assets=root/'.local/module-test-assets';assets.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('delta',root/'scripts/make-apk-patch.py');delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)
# SDK is resolved through the repository's local configuration rather than a global install.
props=dict(line.split('=',1) for line in (root/'local.properties').read_text().splitlines() if '=' in line)
signer=Path(props['sdk.dir'])/'build-tools/35.0.0/apksigner'
base=work/'host4-x86.apk';target=work/'module5-x86.apk'
def sign(source,output,key,password):
 subprocess.run([str(signer),'sign','--ks',str(key),'--ks-pass','pass:'+password,'--out',str(output),str(source)],check=True,stdout=subprocess.DEVNULL)
key=work/'untrusted.jks'
if not key.exists():subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-storepass','test-only','-keypass','test-only','-alias','test','-dname','CN=TestOnly','-keyalg','RSA','-validity','30'],check=True)
sign(target,work/'wrong-signer.apk',key,'test-only')
raw=bytearray(target.read_bytes());name=b'assets/dsh-module.properties'
with zipfile.ZipFile(target) as z:
 meta=z.read(name.decode());offset=z.getinfo(name.decode()).header_offset+30
raw[offset:offset+len(name)]=b'assets/old-module.properties'
central=struct.unpack_from('<I',raw,raw.rfind(b'PK\x05\x06')+16)[0]
while raw[central:central+4]==b'PK\x01\x02':
 size,extra,comment=struct.unpack_from('<HHH',raw,central+28)
 if raw[central+46:central+46+size]==name:raw[central+46:central+46+size]=b'assets/old-module.properties'
 central+=46+size+extra+comment
# Retire old metadata under a different name; append replacement, preserving all other payloads.
(work/'incompatible-unsigned.apk').write_bytes(raw)
with zipfile.ZipFile(work/'incompatible-unsigned.apk','a') as z:
 lines=meta.decode().splitlines();lines=[('hostContract='+'0'*64) if line.startswith('hostContract=') else line for line in lines]
 z.writestr(name.decode(),'\n'.join(lines)+'\n')
sign(work/'incompatible-unsigned.apk',work/'incompatible.apk',root/'.local/android/debug.keystore','android')
shutil.copyfile(target,work/'resource-unsigned.apk')
with zipfile.ZipFile(work/'resource-unsigned.apk','a') as z:z.writestr('host-boundary-test.txt','requires host update')
sign(work/'resource-unsigned.apk',work/'resource.apk',root/'.local/android/debug.keystore','android')
raw=bytearray(target.read_bytes());raw[131072]^=1;(work/'corrupt-signature.apk').write_bytes(raw)
for apk,name in [('module5-x86.apk','module4-to5.dshota'),('wrong-signer.apk','wrong-signer.dshota'),('incompatible.apk','incompatible-host.dshota'),('resource.apk','resource-change.dshota'),('corrupt-signature.apk','corrupt-signature.dshota')]:
 delta.create(base,work/apk,assets/name)
 print(name,(assets/name).stat().st_size)
