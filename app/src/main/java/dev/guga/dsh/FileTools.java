package dev.guga.dsh;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Xml;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

final class FileTools {

  static final int MAX_BYTES = 16 * 1024 * 1024;
  final File root;

  FileTools(Context c) {
    root = new File(c.getFilesDir(), "workspace");
    root.mkdirs();
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c);
  }

  File resolve(String name) throws Exception {
    if (
      name.isEmpty() || name.startsWith("/") || name.contains("\\")
    ) throw new IOException("请使用工作区相对文件名");
    File f = new File(root, name).getCanonicalFile();
    if (
      !f.getPath().startsWith(root.getCanonicalPath() + File.separator)
    ) throw new IOException("不能访问工作区以外的文件");
    return f;
  }

  JSONArray list() throws Exception {
    JSONArray out = new JSONArray();
    listDir(root, out, 0);
    return out;
  }

  private void listDir(File d, JSONArray out, int depth) throws Exception {
    if (depth > 8) return;
    File[] files = d.listFiles();
    if (files == null) return;
    Arrays.sort(files, Comparator.comparing(File::getName));
    for (File f : files) {
      if (f.getName().startsWith(".dsh-")) continue;
      if (f.isDirectory()) listDir(f, out, depth + 1);
      else out.put(
        new JSONObject()
          .put("path", root.toPath().relativize(f.toPath()).toString())
          .put("bytes", f.length())
      );
      if (out.length() >= 500) return;
    }
  }

  synchronized String importFile(InputStream input, String name)
    throws Exception {
    name = name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
    if (name.trim().isEmpty()) name = "导入文件.txt";
    File f = resolve(name);
    int n = 1;
    while (f.exists()) {
      int dot = name.lastIndexOf('.');
      String base = dot > 0 ? name.substring(0, dot) : name,
        ext = dot > 0 ? name.substring(dot) : "";
      f = resolve(base + " (" + n++ + ")" + ext);
    }
    File tmp = File.createTempFile(".dsh-import-", ".tmp", root);
    try (FileOutputStream out = new FileOutputStream(tmp)) {
      copy(input, out, MAX_BYTES);
      out.getFD().sync();
    } catch (Exception e) {
      tmp.delete();
      throw e;
    }
    Files.move(tmp.toPath(), f.toPath());
    return f.getName();
  }

  static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    copy(in, out, limit);
    return out.toByteArray();
  }

  static void copy(InputStream in, OutputStream out, int max)
    throws IOException {
    byte[] b = new byte[8192];
    int n,
      total = 0;
    while ((n = in.read(b)) != -1) {
      total += n;
      if (total > max) throw new IOException(
        "文件超过大小限制（" + max / 1024 / 1024 + " MB）"
      );
      out.write(b, 0, n);
    }
  }

  JSONObject read(JSONObject a) throws Exception {
    File f = resolve(a.getString("path"));
    if (!f.isFile()) throw new IOException("文件不存在");
    if (f.length() > MAX_BYTES) throw new IOException(
      "文件超过 16 MB，适合使用电脑处理"
    );
    String name = f.getName().toLowerCase(Locale.ROOT),
      text;
    if (name.endsWith(".pdf")) {
      try (PDDocument doc = PDDocument.load(f)) {
        if (doc.getNumberOfPages() > 150) throw new IOException(
          "PDF 超过 150 页"
        );
        text = new PDFTextStripper().getText(doc);
        if (text.trim().isEmpty()) throw new IOException(
          "该 PDF 没有可提取文字，扫描件需要 OCR，本版未提供"
        );
      }
    } else if (name.endsWith(".docx")) {
      try (ZipFile zip = new ZipFile(f)) {
        ZipEntry e = zip.getEntry("word/document.xml");
        if (e == null) throw new IOException("不是有效 DOCX");
        try (InputStream in = zip.getInputStream(e)) {
          byte[] xml = readLimited(in, MAX_BYTES);
          XmlPullParser parser = Xml.newPullParser();
          parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
          StringBuilder b = new StringBuilder();
          for (
            int event = parser.getEventType();
            event != XmlPullParser.END_DOCUMENT;
            event = parser.next()
          ) {
            if (
              event == XmlPullParser.START_TAG && parser.getName().equals("t")
            ) b.append(parser.nextText());
            else if (
              event == XmlPullParser.END_TAG && parser.getName().equals("p")
            ) b.append('\n');
            else if (
              event == XmlPullParser.START_TAG && parser.getName().equals("tab")
            ) b.append('\t');
          }
          text = b.toString();
        }
      }
    } else {
      if (
        !name.matches(
          ".*\\.(txt|md|csv|tsv|json|html|htm|xml|log|yaml|yml|java|js|ts|py|css)$"
        )
      ) throw new IOException(
        "本版支持文本、CSV、DOCX 和文字 PDF；不支持 XLSX、旧版 DOC 或二进制文件"
      );
      byte[] b = Files.readAllBytes(f.toPath());
      text = StandardCharsets.UTF_8.newDecoder()
        .decode(java.nio.ByteBuffer.wrap(b))
        .toString();
    }
    int start = Math.max(0, Math.min(text.length(), a.optInt("offset", 0))),
      limit = Math.max(1, Math.min(40000, a.optInt("limit", 18000))),
      end = Math.min(text.length(), start + limit);
    return new JSONObject()
      .put("path", a.getString("path"))
      .put("text", text.substring(start, end))
      .put("totalChars", text.length())
      .put("nextOffset", end < text.length() ? end : JSONObject.NULL);
  }

  synchronized JSONObject write(JSONObject a, boolean document)
    throws Exception {
    File f = resolve(a.getString("path"));
    if (f.exists() && !a.optBoolean("overwrite", false)) throw new IOException(
      "文件已存在，请选择新文件名，或用户明确授权后设置 overwrite"
    );
    String content = a.getString("content");
    if (
      content.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024
    ) throw new IOException("输出超过 2 MB");
    f.getParentFile().mkdirs();
    File tmp = File.createTempFile(".dsh-write-", ".tmp", f.getParentFile());
    try {
      String lower = f.getName().toLowerCase(Locale.ROOT);
      if (document) {
        if (lower.endsWith(".docx")) writeDocx(tmp, content);
        else if (lower.endsWith(".pdf")) writePdf(tmp, content);
        else throw new IOException("文档输出仅支持 DOCX/PDF");
      } else {
        if (
          !lower.matches(
            ".*\\.(txt|md|csv|tsv|json|html|htm|xml|log|yaml|yml|java|js|ts|py|css)$"
          )
        ) throw new IOException(
          "请使用文本类扩展名；DOCX/PDF 请调用 create_document"
        );
        Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
      }
      try (RandomAccessFile file = new RandomAccessFile(tmp, "rw")) {
        file.getFD().sync();
      }
      Files.move(
        tmp.toPath(),
        f.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      );
    } finally {
      tmp.delete();
    }
    return new JSONObject()
      .put("path", a.getString("path"))
      .put("bytes", f.length())
      .put("status", "已生成，可在文件面板导出");
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private void writeDocx(File f, String text) throws Exception {
    try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
      entry(
        z,
        "[Content_Types].xml",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
      );
      entry(
        z,
        "_rels/.rels",
        "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
      );
      StringBuilder b = new StringBuilder(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
      );
      for (String line : text.split("\n", -1))
        b.append("<w:p><w:r><w:t xml:space=\"preserve\">")
          .append(esc(line))
          .append("</w:t></w:r></w:p>");
      b.append(
        "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:right=\"1440\"/></w:sectPr></w:body></w:document>"
      );
      entry(z, "word/document.xml", b.toString());
    }
  }

  private static void entry(ZipOutputStream z, String name, String s)
    throws Exception {
    z.putNextEntry(new ZipEntry(name));
    z.write(s.getBytes(StandardCharsets.UTF_8));
    z.closeEntry();
  }

  private void writePdf(File f, String text) throws Exception {
    PdfDocument doc = new PdfDocument();
    try {
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setTextSize(12);
      PdfDocument.Page page = null;
      int y = 800,
        num = 0;
      for (String paragraph : text.split("\n", -1)) {
        String rest = paragraph;
        do {
          if (y > 790) {
            if (page != null) doc.finishPage(page);
            page = doc.startPage(
              new PdfDocument.PageInfo.Builder(595, 842, ++num).create()
            );
            y = 52;
          }
          int count = rest.isEmpty()
            ? 0
            : Math.max(1, paint.breakText(rest, true, 491, null));
          page.getCanvas().drawText(rest.substring(0, count), 52, y, paint);
          y += 19;
          rest = rest.substring(count);
        } while (!rest.isEmpty());
      }
      if (page != null) doc.finishPage(page);
      try (FileOutputStream out = new FileOutputStream(f)) {
        doc.writeTo(out);
      }
    } finally {
      doc.close();
    }
  }
}
