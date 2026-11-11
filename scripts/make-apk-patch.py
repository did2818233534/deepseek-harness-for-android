#!/usr/bin/env python3
"""Generate a byte-exact DSH APK patch by reusing unchanged compressed ZIP payloads."""
import argparse, gzip, hashlib, json, struct, zipfile
from pathlib import Path

def sha(b): return hashlib.sha256(b).digest()
def payloads(path, blob):
    with zipfile.ZipFile(path) as z:
        for entry in sorted(z.infolist(), key=lambda x:x.header_offset):
            off=entry.header_offset
            if blob[off:off+4]!=b'PK\x03\x04': raise ValueError('Invalid ZIP local header')
            names,extra=struct.unpack_from('<HH',blob,off+26)
            start=off+30+names+extra
            end=start+entry.compress_size
            if end>len(blob): raise ValueError('ZIP payload out of bounds')
            yield start,end

def create(base_path,target_path,output):
    base,target=Path(base_path).read_bytes(),Path(target_path).read_bytes()
    if len(target)>512*1024*1024: raise ValueError('APK exceeds supported size')
    lookup={}
    for start,end in payloads(base_path,base):
        if end-start>=64: lookup.setdefault((end-start,sha(base[start:end])),start)
    output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
    reused=0
    with output.open('wb') as raw:
        raw.write(b'DSHPAT01'+sha(base)+sha(target)+struct.pack('>Q',len(target)))
        with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0,filename='',compresslevel=9) as out:
            def literal(data):
                if data: out.write(b'\x02'+struct.pack('>Q',len(data))+data)
            cursor=0
            for start,end in payloads(target_path,target):
                old=lookup.get((end-start,sha(target[start:end])))
                if old is None: continue
                literal(target[cursor:start]);out.write(b'\x01'+struct.pack('>QQ',old,end-start))
                reused+=end-start;cursor=end
            literal(target[cursor:]);out.write(b'\x00')
    info={'format':'DSHPAT01','baseSha256':sha(base).hex(),'targetSha256':sha(target).hex(),
          'apkBytes':len(target),'patchBytes':output.stat().st_size,'reusedBytes':reused,
          'patchSha256':hashlib.sha256(output.read_bytes()).hexdigest()}
    output.with_suffix(output.suffix+'.json').write_text(json.dumps(info,indent=2)+'\n')
    return info
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('base');p.add_argument('target');p.add_argument('output')
    a=p.parse_args();print(json.dumps(create(a.base,a.target,a.output),indent=2))
