package dev.guga.dsh.files;

import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Xml;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.*;
import org.xmlpull.v1.XmlPullParser;

/** Converts document contents after the workspace has resolved and validated the path. */
final class DocumentFormats {
  private DocumentFormats() {}

  static String readText(File f) throws Exception {
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
          byte[] xml = FileTools.readLimited(in, FileTools.MAX_BYTES);
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
          ".*\\.(txt|md|csv|tsv|json|html|htm|xml|log|yaml|yml|java|js|ts|py|css|svg)$"
        )
      ) throw new IOException(
        "本版支持文本、CSV、DOCX 和文字 PDF；不支持 XLSX、旧版 DOC 或二进制文件"
      );
      byte[] b = Files.readAllBytes(f.toPath());
      text = StandardCharsets.UTF_8.newDecoder()
        .decode(java.nio.ByteBuffer.wrap(b))
        .toString();
    }
    return text;
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static void writeDocx(File f, String text) throws Exception {
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

  static void writePdf(File f, String text) throws Exception {
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
