#!/usr/bin/env python3
"""Cross-language generator/Android decoder tests using the same Java source."""
import gzip, hashlib, importlib.util, io, random, struct, subprocess, tempfile, zipfile
from pathlib import Path
root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('patch',root/'scripts/make-apk-patch.py');patch=importlib.util.module_from_spec(spec);spec.loader.exec_module(patch)
work=root/'.local/patch-unit';work.mkdir(parents=True,exist_ok=True)
(work/'PatchRunner.java').write_text('''import java.io.*;import dev.guga.updates.ApkPatch;
public class PatchRunner {public static void main(String[] args)throws Exception {
try(InputStream in=new FileInputStream(args[1])){ApkPatch.apply(new File(args[0]),in,new File(args[2]));}}}''')
subprocess.run(['javac','-d',str(work),str(root/'release-updater/src/main/java/dev/guga/updates/ApkPatch.java'),str(work/'PatchRunner.java')],check=True)
def apply(base,delta,out,ok=True):
    result=subprocess.run(['java','-cp',str(work),'PatchRunner',str(base),str(delta),str(out)],capture_output=True)
    assert (result.returncode==0)==ok,result.stderr.decode()
    if not ok:assert not out.exists()
def archive(path,payload,changed):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('assets/runtime.zip',payload)
        z.writestr('classes.dex',changed)
    with path.open('ab') as f:f.write(b'signed-trailer-'+changed[:32])
with tempfile.TemporaryDirectory(dir=work) as folder:
    p=Path(folder);base=p/'base.apk';new=p/'new.apk';delta=p/'update.dshpatch';out=p/'out.apk'
    payload=random.Random(5).randbytes(4*1024*1024)
    archive(base,payload,b'old classes'*1024);archive(new,payload,b'new code'*4096)
    info=patch.create(base,new,delta)
    assert info['patchBytes']<info['apkBytes']//10,info
    for n in range(3):apply(base,delta,out);assert out.read_bytes()==new.read_bytes()
    original=base.read_bytes();bad=p/'bad.dshpatch';data=delta.read_bytes()
    # Truncation, old APK mismatch, target hash corruption, magic and size guards.
    cases=[data[:70],data[:-8],b'BADMAGIC'+data[8:],data[:40]+b'\x00'*32+data[72:],data[:72]+struct.pack('>Q',2**63)+data[80:]]
    for blob in cases:bad.write_bytes(blob);apply(base,bad,out,False);assert base.read_bytes()==original
    bad.write_bytes(data);apply(new,bad,out,False)
    # Valid hashes but malicious operation ranges, lengths, types or trailing bytes.
    header=data[:80]
    for operations in [b'\x01'+struct.pack('>QQ',2**62,5)+b'\x00',b'\x02'+struct.pack('>Q',2**62),b'\xff',b'\x00more']:
        bad.write_bytes(header+gzip.compress(operations));apply(base,bad,out,False)
    apply(base,delta,out);assert out.read_bytes()==new.read_bytes()
    print('PASS: round trip x3, changed ZIP offsets/trailer, wrong base, truncation, hash, bounds, opcode, recovery; patch %.2f%% of APK'%(100*info['patchBytes']/info['apkBytes']))
