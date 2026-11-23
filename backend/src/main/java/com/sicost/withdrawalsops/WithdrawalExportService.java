package com.sicost.withdrawalsops;

import com.sicost.apiops.*;
import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class WithdrawalExportService {
    private final WithdrawalService withdrawals;
    private final ExcelExportService excel;
    private com.sicost.common.excel.ExportLabels labels;
    @org.springframework.beans.factory.annotation.Autowired
    public void setLabels(com.sicost.common.excel.ExportLabels labels) { this.labels = labels; }
    public WithdrawalExportService(WithdrawalService withdrawals,ExcelExportService excel){this.withdrawals=withdrawals;this.excel=excel;}
    private static final List<Column> COLUMNS=List.of(
        new Column("name","이름",CellType.TEXT),new Column("managementNumber","관리번호",CellType.TEXT),new Column("project","프로젝트",CellType.TEXT),
        new Column("job","직무",CellType.TEXT),new Column("withdrawalDate","철수일",CellType.DATE),new Column("remainingDays","남은 일수",CellType.NUMBER),
        new Column("extensionCount","연장 횟수",CellType.NUMBER),new Column("earlyWithdrawal","조기철수 이력",CellType.TEXT),
        new Column("extension","연장 가능성",CellType.TEXT),new Column("next","다음 배정",CellType.TEXT));
    public Download export(WithdrawalInput.Query query,HttpServletRequest request){
        String period = switch (query.period()) { case "SOON" -> "3개월 안"; case "LATER" -> "3개월 뒤"; case "PAST" -> "이미 철수"; default -> "전체"; };
        String extension = switch (query.extension()) { case "ALL" -> "전체"; case "UNSET" -> "미정"; default -> com.sicost.common.excel.ExportLabels.name(labels.labels("EXT_PSBL"),query.extension()); };
        String conditions="기간="+Objects.toString(query.year(),"전체")+",철수 시점="+period+",연장 가능성="+extension+",직무="+(query.job()==null?"전체":com.sicost.common.excel.ExportLabels.name(labels.labels("JOB"),query.job()));
        return excel.generateWorkbook(new Specification("철수리스트",null,COLUMNS,conditions,"원","UI-05"),()->{
            var data=withdrawals.listAll(query);
            var jobs = labels.labels("JOB");
            var extensions = labels.labels("EXT_PSBL");
            return new WorkbookData(List.of(new Sheet("철수 리스트",COLUMNS,sink->data.items().forEach(row->sink.accept(values(row,jobs,extensions))))),
                List.of(new Information("기준일",data.serverDate())),data.totalCount(),Integer.toString(data.year()));
        },request);
    }
    private List<Object> values(WithdrawalDtos.Row row,Map<String,String> jobs,Map<String,String> extensions){
        String next=row.remainingDays()<0 ? "–" : row.nextAssignments().stream().map(n -> {
            String kind = switch(n.kind()) { case "CONFIRMED" -> "확정"; case "CANDIDATE" -> "배정 후보"; default -> "가배정"; };
            String start = n.startDate()==null ? Objects.toString(n.startMonth(),"–") : n.startDate().toString();
            return kind + " · " + Objects.toString(n.name(),"–") + " · " + start;
        }).collect(Collectors.joining(" / "));
        return Arrays.asList(row.developer().name(),row.developer().managementNumber(),row.projectName(),com.sicost.common.excel.ExportLabels.name(jobs,row.jobCode()),
            row.withdrawalDate(),row.remainingDays(),row.extensionCount(),row.earlyWithdrawal()?"조기철수 이력":"–",
            row.extensionPossibility()==null?"미정":com.sicost.common.excel.ExportLabels.name(extensions,row.extensionPossibility()),next);
    }
}
