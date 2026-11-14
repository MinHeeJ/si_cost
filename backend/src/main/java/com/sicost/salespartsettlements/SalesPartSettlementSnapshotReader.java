package com.sicost.salespartsettlements;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static com.sicost.salespartsettlements.SalesPartSettlementCalculator.money;

@Component
public class SalesPartSettlementSnapshotReader {
    private final ObjectMapper json;
    public SalesPartSettlementSnapshotReader(ObjectMapper json) {this.json=json;}
    public List<Row> read(List<Detail> details, String month) {
        return details.stream().map(d->{
            List<String> names=new ArrayList<>();String legacy=null;
            try {
                var value=json.readTree(d.projectText());
                if(!value.isArray())throw new IllegalArgumentException();
                for(var item:value) {if(!item.isTextual())throw new IllegalArgumentException();names.add(item.textValue());}
            } catch(Exception e) {legacy=d.projectText();names.clear();}
            List<Issue> issues=new ArrayList<>();
            issues.add(issue("SNAPSHOT_UNKNOWN","확정 당시 저장되지 않은 계산 근거는 미확인입니다.","basis",d.developerId(),month,"TB_CO_PART_SETL_DTL",false));
            boolean missing=d.remark()!=null&&d.remark().contains("시작 달 급여 0");
            if(missing)issues.add(issue("START_MONTH_MISSING_PAY","⚠ 월 급여 없음(시작 달 급여 0)","payAmount",d.developerId(),month,"TB_CO_PART_SETL_DTL",false));
            boolean estimated=d.remark()!=null&&d.remark().contains("급여 예정");
            return new Row(d.developerId(),d.name()==null?d.developerId():d.name(),d.managementNo()==null?d.developerId():d.managementNo(),d.employeeType(),List.copyOf(names),legacy,d.mm().toPlainString(),money(d.unitPrice()),money(d.pay()),money(d.expense()),money(d.retirement()),money(d.cost()),money(d.profit()),money(d.share()),d.remark(),
                new Basis("SNAPSHOT_UNKNOWN",estimated,missing,null,null,null,null,null,List.of(),List.copyOf(issues)),List.copyOf(issues));
        }).toList();
    }
}
