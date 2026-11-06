"""Build the Full edition's offline, per-ABI runtime assets from verified archives."""
import email, json, argparse, hashlib, os, shutil, subprocess, tempfile, zipfile
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('arch',choices=['aarch64','x86_64']);p.add_argument('--cache',type=Path,default=Path(__file__).resolve().parents[2] / '.local' / 'termux');a=p.parse_args()
arch=a.arch; cache=a.cache; assets=cache/'assets'/'termux';assets.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=cache) as tmp:
    t=Path(tmp);prefix=t/'data/data/com.termux/files/usr'
    z=zipfile.ZipFile(cache/f'bootstrap-{arch}.zip');z.extractall(prefix)
    for line in (prefix/'SYMLINKS.txt').read_text().splitlines():
        dest,n=line.split('←');f=prefix/n;f.parent.mkdir(parents=True,exist_ok=True);f.symlink_to(dest)
    (prefix/'SYMLINKS.txt').unlink()
    versions={x["Package"]:x["Version"] for x in map(email.message_from_string,(prefix/"var/lib/dpkg/status").read_text().split("\n\n")) if x["Package"]}
    for row in (cache/arch/'SHA256SUMS').read_text().splitlines():
        sha,name=row.split('  ');deb=cache/arch/'archives'/name
        with deb.open('rb') as src:
            if hashlib.file_digest(src,'sha256').hexdigest()!=sha:raise ValueError(name)
        info=email.message_from_bytes(subprocess.check_output(['dpkg-deb','-f',deb]));versions[info['Package']]=info['Version']
        subprocess.run(['dpkg-deb','-x',deb,t],check=True)
    (prefix/'seed-packages.json').write_text(json.dumps(versions))
    path=assets/(arch+'.zip');partial=path.with_suffix('.part')
    with zipfile.ZipFile(partial,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=3) as out:
        links=[]
        for f in sorted(prefix.rglob('*')):
            n=str(f.relative_to(prefix))
            if f.is_symlink():
                dest=os.readlink(f)
                if dest.startswith('/data/data/com.termux/files/usr/'):dest=os.path.relpath(dest[31:].lstrip('/'),str(f.relative_to(prefix).parent))
                links.append(dest+'←'+n)
            elif f.is_file():out.write(f,n)
        out.writestr('SYMLINKS.txt','\n'.join(links))
    partial.replace(path);print(path,path.stat().st_size)
