#!/usr/bin/env python3
"""Package both Android editions; pre-edition clients follow Lite."""
import hashlib
import json
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
repo = 'https://github.com/did2818233534/deepseek-harness-for-android'
checksums = []
expected = None
for edition, package in [('lite', 'dev.guga.dsh'), ('full', 'dev.guga.dsh.full')]:
    outputs = root / 'app/build/outputs/apk' / edition / 'release'
    meta = json.loads((outputs / 'output-metadata.json').read_text())
    if meta['applicationId'] != package:
        raise ValueError('Unexpected applicationId for ' + edition)
    assets = {}
    architectures = set()
    for item in meta['elements']:
        version, code = item['versionName'], item['versionCode']
        expected = expected or (version, code)
        if expected != (version, code):
            raise ValueError('Mismatched release versions')
        abi = next((f['value'] for f in item['filters'] if f['filterType'] == 'ABI'), 'universal')
        if abi in architectures:
            raise ValueError('Duplicate output ' + abi)
        architectures.add(abi)
        destination = root / 'dist' / ('release-' + version)
        destination.mkdir(parents=True, exist_ok=True)
        name = f'DSH-Pocket-{version}-{edition}-{abi}.apk'
        target = destination / name
        shutil.copyfile(outputs / item['outputFile'], target)
        with target.open('rb') as src:
            sha = hashlib.file_digest(src, 'sha256').hexdigest()
        checksums.append(f'{sha}  {name}')
        if abi != 'universal':
            assets[abi] = {'url': f'{repo}/releases/download/v{version}/{name}', 'size': target.stat().st_size, 'sha256': sha}
    if architectures != {'arm64-v8a','x86_64','universal'}:
        raise ValueError('Missing expected APKs for ' + edition)
    manifest = {'schema': 1, 'versionName': version, 'versionCode': code, 'assets': assets}
    for name in ([f'latest-{edition}.json','latest.json'] if edition == 'lite' else ['latest-full.json']):
        path = destination / name
        path.write_text(json.dumps(manifest, indent=2) + '\n')
        checksums.append(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {name}')
(destination / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
print(destination)
