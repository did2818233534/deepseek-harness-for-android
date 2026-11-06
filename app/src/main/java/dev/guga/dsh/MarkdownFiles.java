package dev.guga.dsh;

import java.util.regex.*;

/** Display-only repair for model-generated local file links with unescaped spaces. */
final class MarkdownFiles {
  private static final Pattern TOKENS=Pattern.compile("(`{3,}|~{3,})[^\\n]*\\n[\\s\\S]*?(?:\\n\\1[^\\n]*(?:\\n|$)|$)|(`+)[^`]*?\\2|\\[([^\\]\\n]+)\\]\\(((?:[^()\\n]|\\([^()\\n]*\\))+)\\)");
  private static String encode(String path){
    StringBuilder out=new StringBuilder();
    for(int i=0;i<path.length();i++){
      char c=path.charAt(i);
      if(c=='%' && i+2<path.length() && Character.digit(path.charAt(i+1),16)>=0 && Character.digit(path.charAt(i+2),16)>=0){out.append(path,i,i+3);i+=2;continue;}
      switch(c){case ' ':out.append("%20");break;case '(':out.append("%28");break;case ')':out.append("%29");break;case '#':out.append("%23");break;case '?':out.append("%3F");break;case '%':out.append("%25");break;default:out.append(c);}
    }
    return out.toString();
  }
  static String normalize(String source){
    Matcher m=TOKENS.matcher(source);StringBuffer out=new StringBuffer();
    while(m.find()){
      String replacement=m.group();String dest=m.group(4);
      if(dest!=null && dest.contains(" ") && !dest.startsWith("<") && !dest.contains("\"") && !dest.contains("'") && !dest.contains(":") && !dest.startsWith("/") && !dest.contains("..") && dest.trim().matches(".*\\.[A-Za-z0-9]{1,10}"))
        replacement="["+m.group(3)+"]("+encode(dest)+")";
      m.appendReplacement(out,Matcher.quoteReplacement(replacement));
    }
    m.appendTail(out);return out.toString();
  }
}
