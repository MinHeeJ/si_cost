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
    public WithdrawalExportService(WithdrawalService withdrawals,ExcelExportService excel){this.withdrawals=withdrawals;this.excel=excel;}
    private static final List<Column> COLUMNS=List.of(
        new Column("assignmentId","배정 ID",CellType.TEXT),new Column("managementNumber","관리번호",CellType.TEXT),
        new Column("name","이름",CellType.TEXT),new Column("projectId","프로젝트 ID",CellType.TEXT),new Column("project","프로젝트",CellType.TEXT),
        new Column("job","직무",CellType.TEXT),new Column("withdrawalDate","철수일",CellType.DATE),new Column("remainingDays","남은 일수",CellType.NUMBER),
        new Column("extensionCount","연장 횟수",CellType.NUMBER),new Column("earlyWithdrawal","조기철수 이력",CellType.TEXT),
        new Column("extension","연장 가능성",CellType.TEXT),new Column("next","다음 배정",CellType.TEXT),new Column("basis","기준일",CellType.DATE));
    public Download export(WithdrawalInput.Query query,HttpServletRequest request){
        String conditions="year="+query.year()+",period="+query.period()+",extension="+query.extension()+",job="+Objects.toString(query.job(),"");
        return excel.generateWorkbook(new Specification("철수리스트",null,COLUMNS,conditions,"원","UI-05"),()->{
            var data=withdrawals.listAll(query);
            return new WorkbookData(List.of(new Sheet("철수 리스트",COLUMNS,sink->data.items().forEach(row->sink.accept(values(row,data.serverDate()))))),
                List.of(new Information("기준일",data.serverDate())),data.totalCount(),Integer.toString(data.year()));
        },request);
    }
    private List<Object> values(WithdrawalDtos.Row row,java.time.LocalDate date){
        String next=row.nextAssignments().stream().map(n->switch(n.kind()){
            case "CONFIRMED"->"확정 "+n.name()+" "+n.startDate();case "CANDIDATE"->"배정 후보 "+n.name()+" "+Objects.toString(n.startDate(),n.startMonth());default->"가배정 "+n.name()+" "+n.startMonth();
        }).collect(Collectors.joining(" / "));
        return Arrays.asList(row.assignmentId(),row.developer().managementNumber(),row.developer().name(),row.projectId(),row.projectName(),row.jobCode(),
            row.withdrawalDate(),row.remainingDays(),row.extensionCount(),row.earlyWithdrawal()?"Y":"N",row.extensionPossibility()==null?"미정":row.extensionPossibility(),next,date);
    }
}
