#!/usr/bin/env python3
"""Fetch a pinned Termux bootstrap and authenticated offline apt archives for Full."""
import argparse, concurrent.futures, hashlib, lzma, re, subprocess, tempfile, zipfile
from pathlib import Path
from urllib.request import urlopen
from urllib.parse import urljoin
from email.parser import Parser

BOOT = 'https://github.com/termux-play-store/termux-packages/releases/download/bootstrap-2026.11.01-r1/'
DIGEST = {'aarch64':'f1331d99eb3cb61186b3c782551eaf912433b22d4590c23055374426519f4a84','x86_64':'4bbb565eb5e9b92fc0121d1a5d929f3c02699d92795b2d104d97b792150464ff'}
REPO = 'https://termux.net/'
def get(url):
    with urlopen(url, timeout=90) as r: return r.read()
def checked(path,url,sha):
    if path.exists() and hashlib.file_digest(path.open('rb'),'sha256').hexdigest()==sha: return
    data=get(url)
    if hashlib.sha256(data).hexdigest()!=sha: raise ValueError(f'Checksum mismatch: {url}')
    tmp=path.with_suffix('.part');tmp.write_bytes(data);tmp.replace(path)
def run(arch,root):
    root.mkdir(exist_ok=True,parents=True)
    name=f'bootstrap-{arch}.zip'; boot=root/name; checked(boot,BOOT+name,DIGEST[arch])
    with zipfile.ZipFile(boot) as z:
        key=z.read('share/termux-keyring/termux-packages.gpg')
        status=z.read('var/lib/dpkg/status').decode()
    def paragraphs(s):return [Parser().parsestr(p) for p in s.split('\n\n') if p.strip()]
    indexpath=f'main/binary-{arch}/Packages.xz'
    with tempfile.TemporaryDirectory() as t:
        d=Path(t);(d/'key.gpg').write_bytes(key);(d/'InRelease').write_bytes(get(REPO+'dists/stable/InRelease'))
        subprocess.run(['gpgv','--keyring',str(d/'key.gpg'),'--output',str(d/'Release'),str(d/'InRelease')],check=True)
        entry=next(x.split() for x in Parser().parsestr((d/'Release').read_text())['SHA256'].splitlines() if x.split() and x.split()[-1]==indexpath)
        compressed=get(REPO+'dists/stable/'+indexpath)
        if len(compressed)!=int(entry[1]) or hashlib.sha256(compressed).hexdigest()!=entry[0]:raise ValueError('Unverified Packages index')
    packages={p['Package']:p for p in paragraphs(lzma.decompress(compressed).decode())}
    # Include the current version of bootstrap packages reachable from requested packages:
    # a name alone does not prove the preinstalled version satisfies a dependency.
    seen={}; pending=['python','python-pillow']; installed={p['Package'] for p in paragraphs(status)}
    while pending:
        n=pending.pop()
        if n in seen:continue
        p=packages[n];seen[n]=p
        for field in ('Depends','Pre-Depends'):
            for dep in (p[field] or '').split(','):
                if not dep.strip():continue
                options=[x.strip().split(' ')[0] for x in dep.split('|')]
                candidate=next((x for x in options if x in packages),None)
                if not candidate:
                    if any(x in installed for x in options):continue
                    raise ValueError(f'Unresolved {n}: {dep}')
                pending.append(candidate)
    dest=root/arch/'archives';dest.mkdir(parents=True,exist_ok=True)
    def fetch(p):
        filename=p['Filename'];name=Path(filename).name
        checked(dest/name,urljoin(REPO,filename),p['SHA256'])
        return p['SHA256']+'  '+name
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: manifest=sorted(pool.map(fetch,seen.values()))
    (dest.parent/'SHA256SUMS').write_text('\n'.join(manifest)+'\n')
    print(arch,len(manifest),'verified packages')
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('arch',choices=DIGEST);a.add_argument('--cache',type=Path,default=Path('/opt/dsh-android/termux'));args=a.parse_args();run(args.arch,args.cache)
