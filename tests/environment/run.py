"""Run actual inventory/uninstall Java with platform stubs and an isolated fixture.
Usage: python3 tests/environment/run.py /path/to/org.json.jar
"""
from pathlib import Path
import subprocess, sys, tempfile
root=Path(__file__).resolve().parents[2]
jar=str(Path(sys.argv[1]).resolve())
with tempfile.TemporaryDirectory(prefix='dsh-env-test-') as temp:
    sources=list((root/'tests/environment/stubs').rglob('*.java'))
    sources += [root/'tests/environment/EnvironmentCheck.java']
    sources += [root/'app/src/full/java/dev/guga/dsh'/name for name in ['EnvironmentInfo.java','EditionTools.java']]
    subprocess.run(['javac','-cp',jar,'-d',temp,*map(str,sources)],check=True)
    subprocess.run(['java','-cp',temp+':'+jar,'dev.guga.dsh.EnvironmentCheck',temp+'/fixture'],check=True)
