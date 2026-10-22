#!/usr/bin/env python3
"""Package signed Gradle outputs and generate the Android updater manifest."""
import hashlib
import json
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
outputs = root / 'app/build/outputs/apk/release'
metadata = json.loads((outputs / 'output-metadata.json').read_text())
items = metadata['elements']
version = items[0]['versionName']
code = items[0]['versionCode']
repo = 'https://github.com/did2818233534/deepseek-harness-for-android'
destination = root / 'dist' / ('release-' + version)
destination.mkdir(parents=True, exist_ok=True)
assets = {}
checksums = []
for item in items:
    abi = next((f['value'] for f in item['filters'] if f['filterType'] == 'ABI'), 'universal')
    name = f'DSH-Pocket-{version}-{abi}.apk'
    target = destination / name
    shutil.copyfile(outputs / item['outputFile'], target)
    sha = hashlib.file_digest(target.open('rb'), 'sha256').hexdigest()
    checksums.append(f'{sha}  {name}')
    if abi != 'universal':
        assets[abi] = {'url': f'{repo}/releases/download/v{version}/{name}',
                       'size': target.stat().st_size, 'sha256': sha}
manifest = {'schema': 1, 'versionName': version, 'versionCode': code, 'assets': assets}
path = destination / 'latest.json'
path.write_text(json.dumps(manifest, indent=2) + '\n')
checksums.append(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  latest.json')
(destination / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
print(destination)
