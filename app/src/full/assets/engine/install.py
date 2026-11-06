"""Private package backend, not a terminal. Installs signed, relocatable data packages."""
import email, hashlib, io, json, lzma, os, re, shutil, ssl, subprocess, sys, tarfile, tempfile
from pathlib import Path, PurePosixPath
from urllib.request import urlopen
BASE='https://termux.net/'
prefix=Path(sys.prefix)
base=prefix.parent/'packages';base.mkdir(exist_ok=True)
name=sys.argv[1];arch=sys.argv[2]
if not re.fullmatch('[a-z0-9][a-z0-9+.-]{0,99}',name) or arch not in ('aarch64','x86_64'):raise ValueError('无效软件包名或架构')
def download(path,limit):
    if not re.fullmatch(r'[A-Za-z0-9+:%._/~=-]+',path) or '..' in PurePosixPath(path).parts:raise ValueError('非法仓库路径')
    with urlopen(BASE+path,context=ssl.create_default_context(cafile=str(prefix/'etc/tls/cert.pem')),timeout=35) as r:
        data=r.read(limit+1)
    if len(data)>limit:raise ValueError('软件包超过本次可处理大小')
    return data
def unpack_deb(blob):
    if not blob.startswith(b'!<arch>\n'):raise ValueError('不是 Debian 包')
    i=8;parts={}
    while i<len(blob):
        hdr=blob[i:i+60]
        if len(hdr)!=60 or hdr[58:]!=b'`\n':raise ValueError('软件包损坏')
        n=int(hdr[48:58]);i+=60
        if n<0 or i+n>len(blob):raise ValueError('软件包长度不符')
        parts[hdr[:16].decode().strip().rstrip('/')]=blob[i:i+n];i+=n+(n%2)
    return parts
def extract(p,dest):
    data=download(p['Filename'],int(p['Size']))
    if hashlib.sha256(data).hexdigest()!=p['SHA256']:raise ValueError('软件包校验失败')
    parts=unpack_deb(data)
    def archive(name):
        return tarfile.open(fileobj=io.BytesIO(next(v for k,v in parts.items() if k.startswith(name+'.'))),mode='r:*')
    with archive('control.tar') as t:
        if any(Path(x.name).name in ('preinst','postinst','prerm','postrm','triggers') for x in t):raise ValueError(p['Package']+' 需要尚未适配的安装脚本')
    with archive('data.tar') as t:
        root=PurePosixPath('data/data/com.termux/files/usr')
        for member in t:
            n=PurePosixPath(member.name)
            if n==root or n in root.parents:continue
            if not n.is_relative_to(root) or '..' in n.parts:raise ValueError('包路径不兼容')
            member.name=str(n.relative_to(root))
            if member.islnk():raise ValueError('不支持该包的硬链接')
            t.extract(member,path=dest,filter='data')
def publish_payload(package, directory, staging, installed):
    if any(record.get('directory')==directory.name for record in installed.values()):return
    # Verify a fresh payload before replacing untracked/partially removed data.
    staging.mkdir();extract(package,staging)
    if directory.is_symlink():directory.unlink()
    elif directory.exists():shutil.rmtree(directory)
    staging.rename(directory)

def main():
    manifest=base/'installed.json'
    installed=json.loads(manifest.read_text()) if manifest.exists() else {}
    if name in installed:return {'package':name,'alreadyInstalled':True}
    seeds=json.loads((prefix/'seed-packages.json').read_text())
    if name in seeds:return {'package':name,'version':seeds[name],'alreadyInstalled':True}
    with tempfile.TemporaryDirectory(dir=base) as tmp:
        d=Path(tmp);signed=d/'InRelease';out=d/'Release'
        signed.write_bytes(download('dists/stable/InRelease',1024*1024))
        subprocess.run(['/system/bin/linker64',str(prefix/'bin/gpgv'),'--keyring',str(prefix/'share/termux-keyring/termux-packages.gpg'),'--output',str(out),str(signed)],check=True)
        idx=f'main/binary-{arch}/Packages.xz'
        h,size,_=next(l.split() for l in email.message_from_bytes(out.read_bytes())['SHA256'].splitlines() if l.split() and l.split()[-1]==idx)
        b=download('dists/stable/'+idx,int(size))
        if hashlib.sha256(b).hexdigest()!=h:raise ValueError('索引校验失败')
        repo={p['Package']:p for p in map(email.message_from_string,lzma.decompress(b).decode().split('\n\n')) if p['Package']}
        staged={};dependencies={}
        def plan(n):
            if n in seeds or n in installed or n in staged:return
            p=repo.get(n)
            if not p:raise ValueError('仓库中没有 '+n)
            staged[n]=p;dependencies[n]=[]
            for dep in ((p['Pre-Depends'] or '')+','+(p['Depends'] or '')).split(','):
                if not dep.strip():continue
                for alt in dep.split('|'):
                    m=re.fullmatch(r'\s*([a-z0-9][a-z0-9.+-]*)(?:\s*\((<<|<=|=|>=|>>)\s*([^()]+)\))?\s*',alt)
                    if not m:continue
                    k,op,v=m.groups();version=seeds.get(k) or installed.get(k,{}).get('version') or (repo.get(k) or {}).get('Version')
                    if not version:continue
                    if op and subprocess.call(['/system/bin/linker64',str(prefix/'bin/dpkg'),'--compare-versions',version,op,v])!=0:continue
                    dependencies[n].append(k);plan(k);break
                else:raise ValueError('依赖不兼容: '+dep)
        plan(name)
        for k,p in staged.items():
            directory=base/p['SHA256']
            publish_payload(p,directory,d/k,installed)
            installed[k]={'version':p['Version'],'directory':directory.name,'dependencies':dependencies[k]}
        nextfile=d/'installed.json';nextfile.write_text(json.dumps(installed));os.replace(nextfile,manifest)
        return {'package':name,'installed':list(staged),'version':installed[name]['version']}
print(json.dumps(main()))
