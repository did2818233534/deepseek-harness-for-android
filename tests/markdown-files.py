"""Host-side regression for display-only repair; does not read user data."""
from pathlib import Path
import json, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
cases={
 '[文件](Making Embedded Systems 第1页.pdf)':'[文件](Making%20Embedded%20Systems%20第1页.pdf)',
 '[文件](报告 (最终).pdf)':'[文件](报告%20%28最终%29.pdf)',
 '[文件](a #1?.pdf)':'[文件](a%20%231%3F.pdf)',
 '[文件](a 100%.pdf)':'[文件](a%20100%25.pdf)',
 '[文件](a%20b c.pdf)':'[文件](a%20b%20c.pdf)',
}
raw='[文件](a b.pdf)'
for s in ['`'+raw+'`','```md\n'+raw+'\n```','~~~\n'+raw+'\n~~~','```\n'+raw,'[x](https://example.com/a)', '[x](<a b.pdf>)','[x](a.pdf "title")','[x](../a b.pdf)','[x](/a b.pdf)','[x](a.pdf)']:
 cases[s]=s
with tempfile.TemporaryDirectory(prefix='dsh-markdown-') as tmp:
 p=Path(tmp);code='package dev.guga.dsh; class Check {public static void main(String[] args){'
 for src,expected in cases.items():
  code+='if(!MarkdownFiles.normalize('+json.dumps(src)+').equals('+json.dumps(expected)+'))throw new AssertionError('+json.dumps(src)+');'
 code+='}}';(p/'Check.java').write_text(code)
 subprocess.run(['javac','-d',tmp,str(root/'app/src/main/java/dev/guga/dsh/MarkdownFiles.java'),str(p/'Check.java')],check=True)
 subprocess.run(['java','-cp',tmp,'dev.guga.dsh.Check'],check=True)
print(f'{len(cases)} Markdown file-link regressions passed')
