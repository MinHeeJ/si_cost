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
    final ResumeDocumentParser parser=new ResumeDocumentParser(){
        @Override public String extract(byte[] bytes,String filename){
            String result=super.extract(bytes,filename);
            // Explicit opt-in verifies the same real binary fixtures through a running API.
            String base=System.getenv("SICOST_RESUME_API_URL");
            if(base!=null&&!base.isBlank())verifyLiveApi(base,bytes,filename);
            return result;
        }
    };
    private static void verifyLiveApi(String base,byte[] bytes,String filename){
        String boundary="qa16"+UUID.randomUUID();
        try(var body=new ByteArrayOutputStream()){
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"targetType\"\r\n\r\nCANDIDATE\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/resume-extractions")).timeout(java.time.Duration.ofSeconds(40)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("live API %s: %s",filename,response.body()).isEqualTo(200);
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            assertThat(json.path("success").asBoolean()).isTrue();
            String expected=switch(filename){
                case "resume.TXT" -> "김하늘";case "resume.txt" -> "시험인물";
                case "r.doc" -> "Actual DOC Person";case "r.docx" -> "문서표인물";
                case "r.ppt" -> "슬라이드표인물";case "r.pptx" -> "새슬라이드표인물";
                case "r.pdf" -> "PDF Person";case "r.rtf" -> "Real RTF Person";
                case "r.hwp" -> "한글본문인물";case "r.hwpx" -> "한글표인물";
                case "exact10m.docx" -> "ExactSize";default -> null;
            };
            if(expected!=null)assertThat(json.path("data").path("values").path("name").asText()).as("live extracted name %s",filename).isEqualTo(expected);
            System.out.println("LIVE_RESUME_API_OK "+filename);
        }catch(Exception e){throw new AssertionError("Live resume API fixture failed: "+filename,e);}
    }
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
    @Test void exactlyTenMiBValidDocumentIsAccepted()throws Exception {
        byte[] sample=storedDocx(0);byte[] exact=storedDocx(ResumeDocumentParser.MAX_BYTES-sample.length);
        assertThat(exact).hasSize(ResumeDocumentParser.MAX_BYTES);
        assertThat(parser.extract(exact,"exact10m.docx")).contains("ExactSize");
    }
    private static byte[] storedDocx(int padding)throws IOException {
        var entries=new LinkedHashMap<String,byte[]>();entries.put("[Content_Types].xml","<Types/>".getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml","<w:document xmlns:w='urn:word'><w:p><w:r><w:t>Name: ExactSize</w:t></w:r></w:p></w:document>".getBytes(StandardCharsets.UTF_8));entries.put("padding",new byte[padding]);
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)){for(var entry:entries.entrySet()){
            var crc=new CRC32();crc.update(entry.getValue());var item=new ZipEntry(entry.getKey());item.setMethod(ZipEntry.STORED);item.setSize(entry.getValue().length);item.setCrc(crc.getValue());zip.putNextEntry(item);zip.write(entry.getValue());zip.closeEntry();
        }zip.finish();return out.toByteArray();}
    }
    static byte[] zip(Map<String,String> entries)throws IOException {try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}zip.finish();return out.toByteArray();}}
}
