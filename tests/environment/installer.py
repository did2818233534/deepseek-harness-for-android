"""Exercise payload publishing without downloading or touching an installed environment."""
import ast, pathlib, shutil, tempfile
source=pathlib.Path(__file__).resolve().parents[2]/'app/src/full/assets/engine/install.py'
module=ast.parse(source.read_text())
function=next(n for n in module.body if isinstance(n,ast.FunctionDef) and n.name=='publish_payload')
namespace={'shutil':shutil}
exec(compile(ast.Module(body=[function],type_ignores=[]),str(source),'exec'),namespace)
with tempfile.TemporaryDirectory() as tmp:
    root=pathlib.Path(tmp);directory=root/'hash';directory.mkdir();(directory/'broken').write_text('old')
    def extract(p,staging):(staging/'complete').write_text('verified')
    namespace['extract']=extract
    publish=namespace['publish_payload'];publish({},directory,root/'stage',{})
    assert not (directory/'broken').exists() and (directory/'complete').read_text()=='verified'
    def fail(p,staging):raise IOError('download failed')
    namespace['extract']=fail
    try:publish({},directory,root/'failed',{})
    except IOError:pass
    else:raise AssertionError('failure swallowed')
    assert (directory/'complete').read_text()=='verified'
    publish({},directory,root/'shared',{'other':{'directory':'hash'}})
    assert not (root/'shared').exists()
print('PASS interrupted-uninstall residue, download failure preservation, shared payload reuse')
