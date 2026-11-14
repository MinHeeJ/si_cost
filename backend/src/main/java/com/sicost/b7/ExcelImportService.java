package com.sicost.b7;

import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b7.B7Dtos.*;
import com.sicost.b7.B7Dtos.CellValue;

@Service
public class ExcelImportService {
    public static final List<String> HEADERS = List.of("구분","상호","사업자등록번호","대표자","업종","담당자","연락처","세금계산서 이메일","은행","계좌번호","예금주");
    private static final Set<String> REQUIRED = Set.of("구분","상호","사업자등록번호");
    public String filename(MultipartFile file) {
        if (file == null) throw failure(400,"VALIDATION_ERROR","파일을 선택해 주세요.");
        String name = file.getOriginalFilename();
        if (name == null) throw failure(400,"VALIDATION_ERROR","파일 이름을 확인해 주세요.");
        name = name.replace('\\','/'); name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.codePointCount(0,name.length()) > 200 || name.matches(".*[\\p{Cntrl}].*"))
            throw failure(400,"VALIDATION_ERROR","파일 이름은 1~200자로 입력해 주세요.");
        if (file.getSize() > 10_485_760) throw failure(413,"FILE_TOO_LARGE","파일은 10MiB 이하로 선택해 주세요.");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw failure(415,"UNSUPPORTED_FILE_TYPE","암호가 없는 XLSX 파일만 사용할 수 있습니다.");
        return name;
    }
    public Parsed parse(MultipartFile file) {
        return parse(file, new ImportDefinition(HEADERS, REQUIRED, Set.of("사업자등록번호","연락처","계좌번호"), Set.of(),
            Map.of("사업자번호","사업자등록번호","사업자등록번호(필수)","사업자등록번호")));
    }
    public Parsed parse(MultipartFile file, ImportDefinition definition) {
        String name = filename(file);
        byte[] bytes;
        try { bytes = file.getBytes().clone(); } catch (IOException e) { throw failure(400,"VALIDATION_ERROR","파일을 읽을 수 없습니다."); }
        if (bytes.length >= 8 && bytes[0] == (byte)0xd0 && bytes[1] == (byte)0xcf)
            throw failure(415,"UNSUPPORTED_FILE_TYPE","암호 보호 파일은 사용할 수 없습니다.");
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K')
            throw failure(415,"UNSUPPORTED_FILE_TYPE","실제 XLSX 파일을 선택해 주세요.");
        try (OPCPackage document = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            if (document.getPartsByContentType(XSSFRelation.WORKBOOK.getContentType()).size() != 1)
                throw failure(415,"UNSUPPORTED_FILE_TYPE","실제 XLSX 통합 문서를 선택해 주세요.");
            try (XSSFWorkbook book = new XSSFWorkbook(document)) {
            if (book.getNumberOfSheets() == 0) throw failure(400,"EMPTY_FILE","첫 시트에 데이터를 입력해 주세요.");
            Sheet sheet = book.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.KOREA); formatter.setUseCachedValuesForFormulaCells(true);
            Map<String,Integer> columns = null; List<Issue> fileIssues = new ArrayList<>(); List<SourceRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (columns == null) {
                    Map<String,Integer> candidate = new LinkedHashMap<>(); Set<String> duplicates = new HashSet<>();
                    for (Cell cell : row) {
                        String title = definition.header(formatter.formatCellValue(cell));
                        if (definition.headers().contains(title) && candidate.putIfAbsent(title,cell.getColumnIndex()) != null) duplicates.add(title);
                    }
                    if (!candidate.keySet().containsAll(definition.required())) continue;
                    if (!duplicates.isEmpty()) throw failure(400,"INVALID_FILE_HEADER","동일 항목의 제목이 중복되었습니다.");
                    columns = candidate;
                    for (Cell cell : row) {
                        String title = definition.header(formatter.formatCellValue(cell));
                        if (!title.isEmpty() && !definition.headers().contains(title)) fileIssues.add(new Issue(0,"WARN",row.getRowNum()+1,"[파일]",excerpt(title,200),"알 수 없는 열은 반영하지 않습니다."));
                    }
                    continue;
                }
                boolean blank = true;
                for (Cell cell : row) if (cell.getCellType() == CellType.FORMULA || !formatter.formatCellValue(cell).isBlank()) { blank = false; break; }
                if (blank) continue;
                if (rows.size() == 5000) throw failure(413,"TOO_MANY_ROWS","데이터는 5,000행 이하로 입력해 주세요.");
                Map<String,CellValue> values = new LinkedHashMap<>();
                for (String title : definition.headers()) {
                    Integer index = columns.get(title); Cell cell = index == null ? null : row.getCell(index);
                    String value = "", error = null;
                    if (cell != null) {
                        CellType type = cell.getCellType();
                        if (type == CellType.FORMULA) {
                            var xml = ((XSSFCell)cell).getCTCell();
                            if (!xml.isSetV() || xml.getV() == null || (xml.getV().isEmpty() && cell.getCachedFormulaResultType() != CellType.STRING))
                                error = "수식의 저장된 계산값이 없습니다.";
                            type = cell.getCachedFormulaResultType();
                        }
                        if (type == CellType.ERROR) error = "셀 오류를 수정해 주세요.";
                        else value = formatter.formatCellValue(cell).strip();
                        if (definition.exactNumbers().contains(title) && type == CellType.NUMERIC) {
                            String raw = ((XSSFCell)cell).getRawValue();
                            if (raw != null) value = new BigDecimal(raw).stripTrailingZeros().toPlainString();
                        }
                        if (definition.dates().contains(title) && type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            value = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                        }
                        if (definition.monthDates().contains(title) && type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            value = java.time.YearMonth.from(cell.getLocalDateTimeCellValue()).toString();
                        }
                        if (definition.identifiers().contains(title) && type == CellType.NUMERIC) {
                            BigDecimal number = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
                            if (number.scale() <= 0 && number.precision() - number.scale() >= 16) error = "텍스트 형식으로 입력하세요";
                        }
                    }
                    values.put(title,new CellValue(value,error));
                }
                rows.add(new SourceRow(row.getRowNum()+1,values));
            }
            if (columns == null) throw failure(400,"INVALID_FILE_HEADER",String.join("·", definition.required())+" 제목이 필요합니다.");
            if (rows.isEmpty()) throw failure(400,"EMPTY_FILE","검사할 데이터가 없습니다.");
            return new Parsed(name,bytes.length,columns,rows,fileIssues);
            }
        } catch (ApiException e) { throw e; }
        catch (org.apache.poi.EncryptedDocumentException e) { throw failure(415,"UNSUPPORTED_FILE_TYPE","암호 보호 파일은 사용할 수 없습니다."); }
        catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) { throw failure(415,"UNSUPPORTED_FILE_TYPE","실제 XLSX 통합 문서를 선택해 주세요."); }
        catch (Exception e) { throw failure(400,"VALIDATION_ERROR","손상된 XLSX 파일입니다. 파일을 확인해 주세요."); }
        finally { Arrays.fill(bytes,(byte)0); }
    }
    private String header(String value) {
        String normalized = value.replaceAll("[\\s\\p{Z}]", "").replaceFirst("\\*+$", "");
        if (Set.of("사업자번호","사업자등록번호","사업자등록번호(필수)").contains(normalized)) return "사업자등록번호";
        if (normalized.equals("세금계산서이메일")) return "세금계산서 이메일";
        return normalized;
    }
    static String excerpt(String value,int max) { return value.codePointCount(0,value.length()) <= max ? value : value.substring(0,value.offsetByCodePoints(0,max-3))+"..."; }
    static ApiException failure(int status,String code,String message) { return new ApiException(status,code,message,"file",null); }
}
