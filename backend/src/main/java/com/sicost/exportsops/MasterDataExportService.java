package com.sicost.exportsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.security.AccountCipher;
import com.sicost.common.validation.MasterDataValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class MasterDataExportService {
    private final MasterDataExportRepository repository;
    private final ExcelExportService excel;
    private final AccountCipher accounts;
    private final Clock clock;
    public MasterDataExportService(MasterDataExportRepository repository, ExcelExportService excel, AccountCipher accounts, Clock clock) {
        this.repository = repository; this.excel = excel; this.accounts = accounts; this.clock = clock;
    }
    private static final Map<String, String> TITLES = Map.ofEntries(
        Map.entry("type", "구분"), Map.entry("name", "상호"), Map.entry("businessNo", "사업자번호"), Map.entry("ceo", "대표자"),
        Map.entry("industry", "업종"), Map.entry("contactName", "담당자"), Map.entry("phone", "연락처"), Map.entry("taxEmail", "세금계산서 이메일"),
        Map.entry("account", "지급 계좌"), Map.entry("projectCount", "진행 프로젝트"), Map.entry("personCount", "투입 인력"), Map.entry("useYn", "사용 여부"),
        Map.entry("leaderName", "파트장"), Map.entry("profitRate", "배분율(%)"), Map.entry("effectiveMonth", "적용 시작월"), Map.entry("scheduledChange", "예정된 변경"));
    public Download create(MasterDataExportInput.Export input, HttpServletRequest request) {
        boolean clients = input.view().equals("CLIENTS");
        var columns = input.columns().stream().map(id -> new Column(id,
            id.equals("salesExpenseAmount") ? "공통관리비(1인·월, " + input.amountUnit() + ")" :
            !clients && id.equals("name") ? "파트명" : !clients && id.equals("personCount") ? "소속 인력 수" : TITLES.get(id),
            Set.of("projectCount", "personCount", "profitRate", "salesExpenseAmount").contains(id) ? CellType.NUMBER :
            id.equals("account") ? CellType.MASKED_ACCOUNT : CellType.TEXT)).toList();
        LocalDate date = LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
        var query = new MasterDataExportMapper.Query(input.filters(), date, date.format(DateTimeFormatter.ofPattern("yyyyMM")), input.sort(), input.direction());
        var specification = new Specification(clients ? "거래처" : "영업파트", null, columns,
            "type=" + Objects.toString(input.filters().type(), "전체") + "; useYn=" + Objects.toString(input.filters().useYn(), "전체") +
            "; q=" + Objects.toString(input.filters().q(), "") + "; sort=" + input.sort() + " " + input.direction(), input.amountUnit());
        return excel.generate(specification, sink -> repository.read(input.view(), query, row -> {
            List<Object> values = new ArrayList<>();
            for (String id : input.columns()) {
                Object value = row.get(id);
                if (id.equals("account")) {
                    String masked = accounts.masked((String) row.get("encryptedAccount"));
                    value = masked == null ? null : Objects.toString(row.get("bankName"), "") + " " + masked;
                } else if (id.equals("salesExpenseAmount") && value != null) {
                    value = MasterDataValidator.amountInUnit(new BigDecimal(value.toString()), input.amountUnit()).stripTrailingZeros();
                } else if (id.equals("scheduledChange") && row.get("futureMonth") != null) {
                    value = row.get("futureMonth") + " / " + Objects.toString(row.get("futureLeader"), "–") + " / " +
                        MasterDataValidator.decimalString(new BigDecimal(row.get("futureRate").toString())) + "% / " +
                        MasterDataValidator.decimalString(MasterDataValidator.amountInUnit(new BigDecimal(row.get("futureAmount").toString()), input.amountUnit())) + " " + input.amountUnit();
                } else if (id.equals("useYn")) value = "Y".equals(value) ? "사용" : "중지";
                values.add(value);
            }
            sink.accept(values);
        }), request);
    }
}
