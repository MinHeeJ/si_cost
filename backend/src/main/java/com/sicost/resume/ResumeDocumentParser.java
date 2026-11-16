package com.sicost.resume;

import com.sicost.common.api.ApiException;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import org.apache.poi.poifs.filesystem.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Text only: no OCR, embedded attachment extraction, macros, renderers, external links, or model calls. */
@Component
public class ResumeDocumentParser {
    static final int MAX_CHARS=200_000,MAX_BYTES=10_485_760,MAX_EXPANDED=32*1024*1024;
    private final ExecutorService workers=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"resume-parser");t.setDaemon(true);return t;});
    @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
    public String extract(byte[] bytes,String filename) {
        if(bytes.length==0)throw error(400,"빈 파일은 읽을 수 없습니다.");if(bytes.length>MAX_BYTES)throw error(413,"파일은 10MiB 이하로 선택해 주세요.");
        String ext=filename==null?"":filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(!Set.of("txt","doc","docx","pdf","rtf","ppt","pptx","hwp","hwpx").contains(ext))throw error(415,"지원하지 않는 파일 형식입니다.");
        Future<String> job;
        try{job=workers.submit(()->parse(bytes,ext));}catch(RejectedExecutionException e){throw error(429,"추출 요청이 많습니다. 잠시 후 다시 시도해 주세요.");}
        try {String text=job.get(30,TimeUnit.SECONDS);if(text.length()>MAX_CHARS)throw limit();return text;}
        catch(TimeoutException e){job.cancel(true);throw limit();}
        catch(InterruptedException e){job.cancel(true);Thread.currentThread().interrupt();throw limit();}
        catch(ExecutionException e){if(e.getCause() instanceof ApiException a)throw a;throw error(422,"손상되었거나 암호/열기 제한이 있는 문서입니다. 직접 입력하거나 제한을 해제한 문서를 선택해 주세요.");}
    }
    String parse(byte[] bytes,String ext)throws Exception {
        boolean ole=bytes.length>=8&&ByteBuffer.wrap(bytes).getLong()==0xd0cf11e0a1b11ae1L;
        boolean zip=bytes.length>3&&bytes[0]=='P'&&bytes[1]=='K';String head=new String(bytes,0,Math.min(16,bytes.length),StandardCharsets.ISO_8859_1);
        String text;
        if(ole&&Set.of("docx","pptx").contains(ext))try(var fs=new POIFSFileSystem(new ByteArrayInputStream(bytes))){if(fs.getRoot().hasEntry("EncryptionInfo")||fs.getRoot().hasEntry("EncryptedPackage"))throw error(422,"암호/열기 제한 문서는 읽을 수 없습니다. 제한을 해제하거나 직접 입력해 주세요.");}
        switch(ext) {
            case "txt" -> {
                if(ole||zip||head.startsWith("%PDF-")||head.startsWith("{\\rtf")||head.startsWith("MZ")||bytes[0]==(byte)0x89||bytes[0]==(byte)0xff)throw error(415,"확장자와 실제 파일 형식이 다릅니다.");
                try{text=decode(bytes,StandardCharsets.UTF_8);}catch(CharacterCodingException e){try{text=decode(bytes,Charset.forName("MS949"));}catch(CharacterCodingException second){throw error(422,"UTF-8 또는 MS949 텍스트로 저장한 뒤 다시 선택해 주세요.");}}
                if(text.chars().anyMatch(c->c==0||c<8||c==11||c==12||c>=14&&c<32))throw error(415,"일반 텍스트 파일이 아닙니다.");
                if(text.startsWith("\uFEFF"))text=text.substring(1);
            }
            case "pdf" -> {
                if(!head.startsWith("%PDF-"))throw mismatch();
                try(var pdf=Loader.loadPDF(bytes)) {if(pdf.isEncrypted()||!pdf.getCurrentAccessPermission().canExtractContent())throw error(422,"암호/추출 제한 PDF는 읽을 수 없습니다.");var out=new LimitedWriter();new PDFTextStripper().writeText(pdf,out);text=out.toString();}
            }
            case "rtf" -> {if(!head.startsWith("{\\rtf"))throw mismatch();var doc=new DefaultStyledDocument();new RTFEditorKit().read(new ByteArrayInputStream(bytes),doc,0);text=doc.getText(0,doc.getLength());}
            case "doc" -> {if(!ole)throw mismatch();try(var fs=new POIFSFileSystem(new ByteArrayInputStream(bytes))){if(!fs.getRoot().hasEntry("WordDocument"))throw mismatch();try(var doc=new HWPFDocument(fs);var extractor=new WordExtractor(doc)){text=extractor.getText().replace('\u0007','\t');}}}
            case "ppt" -> {if(!ole)throw mismatch();try(var fs=new POIFSFileSystem(new ByteArrayInputStream(bytes))){if(!fs.getRoot().hasEntry("PowerPoint Document"))throw mismatch();try(var slides=new HSLFSlideShow(fs)){var out=new LimitedWriter();for(var slide:slides.getSlides())pptShapes(slide.getShapes(),out);text=out.toString();}}}
            case "hwp" -> {
                if(!ole){if(head.startsWith("HWP Document"))throw error(422,"구형 HWP 형식은 지원하지 않습니다. HWP5/HWPX 또는 TXT로 저장하거나 직접 입력해 주세요.");throw mismatch();}
                text=hwp(bytes);
            }
            default -> {if(!zip)throw mismatch();text=xmlArchive(bytes,ext);}
        }
        if(text.length()>MAX_CHARS)throw limit();return text.replace("\r\n","\n").replace('\r','\n');
    }
    private static String decode(byte[] b,Charset c)throws CharacterCodingException{return c.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}
    private static void pptShapes(List<HSLFShape> shapes,LimitedWriter out)throws IOException {
        for(var shape:shapes) {
            if(shape instanceof HSLFTable table){for(int row=0;row<table.getNumberOfRows();row++){for(int col=0;col<table.getNumberOfColumns();col++){var cell=table.getCell(row,col);if(cell!=null)out.write(cell.getText());out.append('\t');}out.append('\n');}}
            else if(shape instanceof HSLFTextShape text){out.write(text.getText());out.append('\n');}
            else if(shape instanceof HSLFGroupShape group)pptShapes(group.getShapes(),out);
        }
    }
    private String xmlArchive(byte[] bytes,String ext)throws Exception {
        Map<String,byte[]> files=new TreeMap<>();int total=0,count=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;while((e=zip.getNextEntry())!=null){if(++count>4000)throw limit();byte[] data=readBounded(zip,MAX_EXPANDED-total);total+=data.length;if(files.put(e.getName(),data)!=null)throw error(422,"중복 문서 항목이 있습니다.");}
        }
        List<byte[]> sections=new ArrayList<>();String kind;
        if(ext.equals("docx")){if(!files.containsKey("word/document.xml")||!files.containsKey("[Content_Types].xml"))throw mismatch();sections.add(files.get("word/document.xml"));kind="word";}
        else if(ext.equals("pptx")){if(!files.containsKey("ppt/presentation.xml")||!files.containsKey("[Content_Types].xml"))throw mismatch();files.entrySet().stream().filter(e->e.getKey().matches("ppt/slides/slide[0-9]+\\.xml")).sorted(Comparator.comparingInt(e->Integer.parseInt(e.getKey().replaceAll("\\D", "")))).forEach(e->sections.add(e.getValue()));kind="ppt";}
        else {if(!files.containsKey("mimetype")||!new String(files.get("mimetype"),StandardCharsets.UTF_8).strip().equals("application/hwp+zip"))throw mismatch();files.entrySet().stream().filter(e->e.getKey().matches("Contents/section[0-9]+\\.xml")).forEach(e->sections.add(e.getValue()));if(sections.isEmpty())throw error(422,"HWPX 본문이 없거나 열기 제한이 있습니다.");kind="hwp";}
        var out=new LimitedWriter();
        for(byte[] xml:sections) {
            SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            XMLReader reader=factory.newSAXParser().getXMLReader();reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            reader.setContentHandler(new DefaultHandler(){boolean text=false;int cellDepth=0;public void startElement(String uri,String local,String q,Attributes a){if(local.equals("t"))text=true;if(local.equals("tc"))cellDepth++;if(local.equals("tab"))out.append('\t');if(local.equals("br"))out.append('\n');}
                public void characters(char[] chars,int start,int len){if(text)out.write(chars,start,len);}
                public void endElement(String uri,String local,String q){if(local.equals("t"))text=false;if(local.equals("p"))out.append(cellDepth>0?' ':'\n');if(local.equals("tc")){cellDepth--;out.append('\t');}if(local.equals("tr"))out.append('\n');}});
            reader.parse(new InputSource(new ByteArrayInputStream(xml)));out.append('\n');
        }
        return out.toString();
    }
    // Hancom HWP5 open specification: FileHeader flags; compressed BodyText record tag 67; UTF-16LE controls.
    private String hwp(byte[] bytes)throws Exception {
        try(var fs=new POIFSFileSystem(new ByteArrayInputStream(bytes))){var root=fs.getRoot();if(!root.hasEntry("FileHeader"))throw mismatch();byte[] header;try(var in=root.createDocumentInputStream("FileHeader")){header=in.readAllBytes();}
            if(header.length<40||!new String(header,0,17,StandardCharsets.US_ASCII).equals("HWP Document File"))throw mismatch();if((header[35]&255)!=5)throw error(422,"구형 HWP는 지원하지 않습니다. HWP5 또는 HWPX로 저장하거나 직접 입력해 주세요.");
            int flags=ByteBuffer.wrap(header,36,4).order(ByteOrder.LITTLE_ENDIAN).getInt();if((flags&(2|4|16|256|1024))!=0)throw error(422,"암호/배포/DRM 제한 HWP는 읽을 수 없습니다.");
            if(!root.hasEntry("BodyText"))throw error(422,"HWP5 본문이 없습니다.");DirectoryNode body=(DirectoryNode)root.getEntry("BodyText");List<String> names=new ArrayList<>();body.forEach(e->{if(e.getName().matches("Section[0-9]+"))names.add(e.getName());});names.sort(Comparator.comparingInt(n->Integer.parseInt(n.substring(7))));var out=new LimitedWriter();
            for(String name:names){byte[] data;try(var input=body.createDocumentInputStream(name);InputStream in=(flags&1)!=0?new InflaterInputStream(input,new Inflater(true)):input){data=readBounded(in,MAX_EXPANDED);}
                ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);TreeMap<Integer,Integer> tableRows=new TreeMap<>();while(b.hasRemaining()){if(b.remaining()<4)throw error(422,"손상된 HWP 본문입니다.");int tag=b.getInt(),size=tag>>>20;if(size==0xfff){if(b.remaining()<4)throw error(422,"손상된 HWP 본문입니다.");size=b.getInt();}if(size<0||size>b.remaining())throw error(422,"손상된 HWP 본문입니다.");int end=b.position()+size;int level=(tag>>>10)&1023;tableRows.tailMap(level,false).clear();
                    if((tag&1023)==77)tableRows.put(level,-1);
                    if((tag&1023)==72&&size>=38&&tableRows.containsKey(level)){int row=b.getShort(b.position()+10)&65535;out.trimEnd();out.append(tableRows.get(level)==row?'\t':'\n');tableRows.put(level,row);}
                    boolean cellText=!tableRows.isEmpty()&&tableRows.lastEntry().getValue()>=0;
                    if((tag&1023)==67){while(b.position()+2<=end){int c=b.getShort()&65535;if(c>=32)out.append((char)c);else if(c==9){out.append('\t');b.position(Math.min(end,b.position()+14));}else if(c==10||c==13)out.append(cellText?' ':'\n');else if(c<=8||c==11||c==12||c>=14&&c<=23)b.position(Math.min(end,b.position()+14));}out.append(cellText?' ':'\n');}b.position(end);if(Thread.interrupted())throw limit();}
            }return out.toString();}
    }
    private static byte[] readBounded(InputStream in,int max)throws IOException {if(max<0)throw limit();var out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||out.size()+n>max)throw limit();out.write(buffer,0,n);}return out.toByteArray();}
    private static class LimitedWriter extends Writer {
        final StringBuilder value=new StringBuilder();
        void trimEnd(){while(!value.isEmpty()&&" \r\n".indexOf(value.charAt(value.length()-1))>=0)value.setLength(value.length()-1);}public void write(char[] chars,int off,int len){if(value.length()+len>MAX_CHARS||Thread.currentThread().isInterrupted())throw limit();value.append(chars,off,len);}public Writer append(char c){write(new char[]{c},0,1);return this;}public void flush(){}public void close(){}public String toString(){return value.toString();}
    }
    static ApiException limit(){return error(422,"추출 문자 200,000자 또는 30초 한도를 넘었습니다. 파일을 단순화하거나 직접 입력해 주세요.");}
    static ApiException mismatch(){return error(415,"확장자와 실제 문서 형식이 다릅니다.");}
    static ApiException error(int status,String message){return new ApiException(status,"RESUME_EXTRACTION_ERROR",message,"file",null);}
}
