package com.sicost.resume;

import com.sicost.common.api.ApiException;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hslf.usermodel.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ResumeDocumentParserTest {
    final ResumeDocumentParser parser=new ResumeDocumentParser();
    @AfterEach void close(){parser.close();}
    @Test void utf8BomMs949AndInvalidEncoding(){
        assertThat(parser.extract("\ufeff성 명 : 김 하 늘\n기술: Java".getBytes(StandardCharsets.UTF_8),"resume.TXT")).contains("성 명 : 김 하 늘");
        assertThat(parser.extract("성명: 시험인물".getBytes(Charset.forName("MS949")),"resume.txt")).contains("시험인물");
        assertThatThrownBy(()->parser.extract(new byte[]{(byte)0x81},"r.txt")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
    }
    @Test void extractsActualDocDocxPptPptxPdfRtfBodiesAndTableCells()throws Exception {
        try(var in=getClass().getResourceAsStream("/resume/empty.doc");var doc=new HWPFDocument(in);var out=new ByteArrayOutputStream()){
            doc.getRange().insertBefore("NAME: Actual DOC Person\rSKILL: Java, SQL\r");doc.write(out);
            assertThat(parser.extract(out.toByteArray(),"r.doc")).contains("Actual DOC Person","Java");
        }
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var table=doc.createTable(2,2);table.getRow(0).getCell(0).setText("성명");table.getRow(0).getCell(1).setText("문서표인물");table.getRow(1).getCell(0).setText("기술");table.getRow(1).getCell(1).setText("Java");doc.write(out);
            assertThat(parser.extract(out.toByteArray(),"r.docx")).contains("성명","\t문서표인물","Java");
        }
        try(var doc=new HSLFSlideShow();var out=new ByteArrayOutputStream()){
            var table=doc.createSlide().createTable(1,2);table.getCell(0,0).setText("성명");table.getCell(0,1).setText("슬라이드표인물");doc.write(out);
            assertThat(parser.extract(out.toByteArray(),"r.ppt")).contains("성명\t슬라이드표인물");
        }
        try(var doc=new XMLSlideShow();var out=new ByteArrayOutputStream()){
            var table=doc.createSlide().createTable();var row=table.addRow();row.addCell().setText("성명");row.addCell().setText("새슬라이드표인물");doc.write(out);
            assertThat(parser.extract(out.toByteArray(),"r.pptx")).contains("성명","\t새슬라이드표인물");
        }
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            var page=new PDPage();doc.addPage(page);try(var content=new PDPageContentStream(doc,page)){content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(30,700);content.showText("NAME: PDF Person");content.endText();}doc.save(out);
            assertThat(parser.extract(out.toByteArray(),"r.pdf")).contains("NAME: PDF Person");
        }
        assertThat(parser.extract("{\\rtf1\\ansi NAME: Real RTF Person\\par SKILL: SQL}".getBytes(StandardCharsets.US_ASCII),"r.rtf")).contains("Real RTF Person","SQL");
    }
    @Test void hwp5CompressedBodyAndHwpxTableAreActuallyRead()throws Exception {
        String value="성명: 한글본문인물\r기술: Java\r";byte[] text=value.getBytes(StandardCharsets.UTF_16LE);
        var data=ByteBuffer.allocate(4+text.length).order(ByteOrder.LITTLE_ENDIAN).putInt(67|(text.length<<20)).put(text).array();
        byte[] compressed;try(var out=new ByteArrayOutputStream();var z=new DeflaterOutputStream(out,new Deflater(6,true))){z.write(data);z.finish();compressed=out.toByteArray();}
        try(var fs=new POIFSFileSystem();var out=new ByteArrayOutputStream()){
            byte[] header=new byte[256];System.arraycopy("HWP Document File".getBytes(StandardCharsets.US_ASCII),0,header,0,17);header[35]=5;header[36]=1;
            fs.getRoot().createDocument("FileHeader",new ByteArrayInputStream(header));fs.getRoot().createDirectory("BodyText").createDocument("Section0",new ByteArrayInputStream(compressed));fs.writeFilesystem(out);
            assertThat(parser.extract(out.toByteArray(),"r.hwp")).contains("한글본문인물","Java");
        }
        byte[] hwpx=zip(Map.of("mimetype","application/hwp+zip","Contents/section0.xml","<s xmlns:hp='urn:hwp'><hp:tr><hp:tc><hp:p><hp:t>성명</hp:t></hp:p></hp:tc><hp:tc><hp:p><hp:t>한글표인물</hp:t></hp:p></hp:tc></hp:tr></s>"));
        assertThat(parser.extract(hwpx,"r.hwpx")).contains("성명 \t한글표인물");
    }
    @Test void scannedPdfIsEmptyAndEncryptionIsNotASuccess()throws Exception {
        // Separate assertions against independently authored, real office documents.
        try(var hwp=getClass().getResourceAsStream("/resume/hwplib-table.hwp")){assertThat(parser.extract(hwp.readAllBytes(),"table.hwp")).isNotBlank().doesNotContain("HWP Document File");}
        try(var hwpx=getClass().getResourceAsStream("/resume/hwpxlib-table.hwpx")){assertThat(parser.extract(hwpx.readAllBytes(),"table.hwpx")).contains("C반 기말고사","국어","영어","수학");}
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){doc.addPage(new PDPage());doc.save(out);assertThat(parser.extract(out.toByteArray(),"scan.pdf")).isBlank();}
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){doc.addPage(new PDPage());doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","secret",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));doc.save(out);assertThatThrownBy(()->parser.extract(out.toByteArray(),"locked.pdf")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));}
    }
    @Test void limitsSpoofingDamageAndXxeDoNotProducePartialSuggestions()throws Exception {
        assertThatThrownBy(()->parser.extract(new byte[0],"r.txt")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->parser.extract(new byte[10485761],"r.txt")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(413));
        assertThatThrownBy(()->parser.extract(" ".repeat(10485760).getBytes(),"r.txt")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
    }
    @Test void rejectsTextLimitAndDisguisedDocuments()throws Exception {
        assertThatThrownBy(()->parser.extract("x".repeat(200001).getBytes(),"r.txt")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
        assertThatThrownBy(()->parser.extract("MZ fake exe".getBytes(),"r.txt")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(415));
        assertThatThrownBy(()->parser.extract("plain text".getBytes(),"r.docx")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(415));
        byte[] xxe=zip(Map.of("mimetype","application/hwp+zip","Contents/section0.xml","<!DOCTYPE s [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><s><t>&xxe;</t></s>"));
        assertThatThrownBy(()->parser.extract(xxe,"r.hwpx")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
    }
    static byte[] zip(Map<String,String> entries)throws IOException {try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}zip.finish();return out.toByteArray();}}
}
