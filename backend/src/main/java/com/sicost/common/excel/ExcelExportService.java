package com.sicost.common.excel;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExcelExportService {
    public static final int DATA_ROWS_PER_SHEET = 1_048_575;
    private final CurrentUserProvider users;
    private final AccessLogWriter logs;
    private final Clock clock;
    private final PlatformTransactionManager transactions;
    public ExcelExportService(CurrentUserProvider users, AccessLogWriter logs, Clock clock, PlatformTransactionManager transactions) {
        this.users = users; this.logs = logs; this.clock = clock; this.transactions = transactions;
    }
    public enum CellType { TEXT, NUMBER, MASKED_ACCOUNT }
    public record Column(String id, String title, CellType type) {}
    public record Specification(String kind, String uploadId, List<Column> columns, String conditions, String amountUnit) {}
    @FunctionalInterface public interface Rows { void read(Consumer<List<Object>> sink); }

    public Download generate(Specification specification, Rows rows, HttpServletRequest request) {
        return generate(specification, rows, request, workbook -> {});
    }
    public Download generateTemplate(List<Column> columns, List<String> instructions, HttpServletRequest request) {
        return generate(new Specification("거래처_등록양식", null, columns, "B7", "원"), sink -> {}, request, workbook -> {
            var text = workbook.createCellStyle(); text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < columns.size(); i++) workbook.getSheetAt(0).setDefaultColumnStyle(i, text);
            var guide = workbook.createSheet("입력안내");
            for (int i = 0; i < instructions.size(); i++) guide.createRow(i).createCell(0).setCellValue(instructions.get(i));
        });
    }
    private Download generate(Specification specification, Rows rows, HttpServletRequest request, Consumer<SXSSFWorkbook> customize) {
        validate(specification);
        Path file = null;
        try {
            file = Files.createTempFile("sicost-export-", ".xlsx");
            Path destination = file;
            LocalDateTime now = LocalDateTime.now(clock.withZone(TimeConfiguration.SEOUL));
            TransactionTemplate snapshot = new TransactionTemplate(transactions);
            snapshot.setReadOnly(true); snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Long total = snapshot.execute(status -> {
                CurrentUserProvider.CurrentUser actor = users.currentUser();
                SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                workbook.setCompressTempFiles(true);
                try (workbook; OutputStream output = Files.newOutputStream(destination)) {
                    SheetRows writer = new SheetRows(workbook, specification.columns());
                    rows.read(writer::append);
                    customize.accept(workbook);
                    var info = workbook.createSheet("다운로드정보");
                    information(info.createRow(0), "처리자ID", actor.userId());
                    information(info.createRow(1), "처리자명", actor.name());
                    information(info.createRow(2), "생성시각", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    information(info.createRow(3), "조건", specification.conditions());
                    information(info.createRow(4), "단위", specification.amountUnit());
                    Row count = info.createRow(5); count.createCell(0).setCellValue("총건수"); count.createCell(1).setCellValue(writer.total);
                    workbook.write(output);
                    return writer.total;
                } catch (IOException e) { throw new IllegalStateException("엑셀 파일을 생성하지 못했습니다."); }
                finally { workbook.dispose(); }
            });
            TransactionTemplate audit = new TransactionTemplate(transactions);
            audit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            audit.executeWithoutResult(status -> logs.append(new AccessLogWriter.Event("DNLD", "UI-15", specification.uploadId(), specification.kind(), total), request));
            String kind = specification.kind() + (specification.uploadId() == null ? "" : "_" + specification.uploadId());
            return new Download(file, kind + "_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");
        } catch (Exception e) {
            if (file != null) try { Files.deleteIfExists(file); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            if (e instanceof ApiException api) throw api;
            throw new ApiException(500, "INTERNAL_ERROR", "다운로드 파일 또는 접근기록을 생성하지 못했습니다. 다시 시도해 주세요.");
        }
    }
    private void validate(Specification specification) {
        if (!Set.of("거래처", "영업파트", "거래처_등록양식", "거래처_업로드검사").contains(specification.kind())) throw InputValidator.invalid("kind");
        if (specification.kind().equals("거래처_업로드검사")) {
            if (specification.uploadId() == null || !specification.uploadId().matches("[0-9]{1,12}")) throw InputValidator.invalid("uploadId");
        } else if (specification.uploadId() != null) throw InputValidator.invalid("uploadId");
        if (specification.columns() == null || specification.columns().isEmpty()) throw InputValidator.invalid("columns");
        for (Column column : specification.columns()) {
            if (column.id().toLowerCase(java.util.Locale.ROOT).matches(".*(accountnumber|acct_no|cipher|password|hash).*")) throw InputValidator.invalid("columns");
            if (column.id().equals("account") && column.type() != CellType.MASKED_ACCOUNT) throw InputValidator.invalid("columns");
        }
        if (!Set.of("원", "천원", "백만원").contains(specification.amountUnit())) throw InputValidator.invalid("amountUnit");
    }
    private void information(Row row, String label, String value) {
        row.createCell(0).setCellValue(label); row.createCell(1).setCellValue(value == null ? "" : value);
    }
    static final class SheetRows {
        final SXSSFWorkbook workbook;
        final List<Column> columns;
        SXSSFSheet sheet;
        long total;
        int sheets;
        int row;
        SheetRows(SXSSFWorkbook workbook, List<Column> columns) { this.workbook = workbook; this.columns = List.copyOf(columns); nextSheet(); }
        void nextSheet() {
            sheet = workbook.createSheet(++sheets == 1 ? "데이터" : "데이터_" + sheets);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) header.createCell(i).setCellValue(columns.get(i).title());
            row = 1;
        }
        void append(List<Object> values) {
            if (values.size() != columns.size()) throw InputValidator.invalid("columns");
            if (row > DATA_ROWS_PER_SHEET) nextSheet();
            Row output = sheet.createRow(row++);
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                var cell = output.createCell(i);
                if (value == null) continue;
                Column column = columns.get(i);
                if (column.type() == CellType.NUMBER) {
                    BigDecimal decimal = value instanceof BigDecimal number ? number : new BigDecimal(value.toString());
                    if (decimal.stripTrailingZeros().precision() > 15) throw InputValidator.invalid(column.id());
                    cell.setCellValue(decimal.doubleValue());
                } else {
                    String text = value.toString();
                    if (column.type() == CellType.MASKED_ACCOUNT && !text.matches("[^*\\r\\n]*\\*{4}[0-9]{1,4}")) throw InputValidator.invalid("account");
                    cell.setCellValue(text);
                }
            }
            total++;
        }
    }
    public static final class Download implements AutoCloseable {
        private final Path file;
        private final String filename;
        Download(Path file, String filename) { this.file = file; this.filename = filename; }
        public String filename() { return filename; }
        public long size() throws IOException { return Files.size(file); }
        public void transferTo(OutputStream output) throws IOException {
            try { Files.copy(file, output); }
            finally { close(); }
        }
        @Override public void close() throws IOException { Files.deleteIfExists(file); }
    }
}
