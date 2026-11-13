package com.sicost.profitability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.MasterDataValidator;
import com.sicost.me.PreferenceService;
import com.sicost.me.PreferenceValidator;
import com.sicost.shared.SharedQueriesService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** Only projection/serialization here: financial rules remain in the common period calculators. */
@Service
public class IndividualProfitabilityExportService {
    private final ExcelExportService excel;
    private final IndividualProfitabilityService profitability;

    private final PreferenceService preferences;
    private final PreferenceValidator validator;
    private final SharedQueriesService shared;
    private final ObjectMapper json;
    public IndividualProfitabilityExportService(ExcelExportService excel, IndividualProfitabilityService profitability,
        PreferenceService preferences, PreferenceValidator validator,
        SharedQueriesService shared, ObjectMapper json) {
        this.excel=excel; this.profitability=profitability; this.preferences=preferences;
        this.validator=validator; this.shared=shared; this.json=json;
    }
    public Download create(IndividualProfitabilityExportInput.Export input, HttpServletRequest request) {
        // Identity metadata is validated first; visible columns resolve from preferences inside the snapshot.
        var specification = new Specification("개인원가수익", null, List.of(new Column("name", "이름", CellType.TEXT)),
            conditions(input.query()), input.unit()==null ? "천원" : input.unit(), "UI-03");
        return excel.generateWorkbook(specification, () -> workbook(input), request);
    }
    private WorkbookData workbook(IndividualProfitabilityExportInput.Export input) {
        var settings = preferences.getPreferences();
        ObjectNode columns = (ObjectNode) validator.defaultValue("COLS", "UI-03").deepCopy();
        String unit = "천원";
        for (var preference : settings.items()) {
            if ("COLS".equals(preference.type()) && "UI-03".equals(preference.key()) && preference.value().isObject())
                columns = (ObjectNode) preference.value().deepCopy();
            if ("AMT_UNIT".equals(preference.type()) && preference.value().isTextual()) unit=preference.value().asText();
        }
        ObjectNode view = (ObjectNode) columns.get("individualProfitability");
        if (input.order()!=null) view.set("order", json.valueToTree(input.order()));
        if (input.hidden()!=null) view.set("hidden", json.valueToTree(input.hidden()));
        validator.validate("COLS", "UI-03", columns);
        if (input.unit()!=null) unit=input.unit();
        validator.validate("AMT_UNIT", "*", json.getNodeFactory().textNode(unit));
        List<String> order = strings(view.get("order")), hidden = strings(view.get("hidden"));
        List<String> visible = order.stream().filter(id -> !hidden.contains(id)).toList();
        String resolvedUnit=unit;
        var snapshot=profitability.snapshot(input.query());
        // Independent post-projection LOSS populations, never reuse the selected-view filtered people.
        var selected=profitability.project(snapshot,input.query(),true);
        var excluded=profitability.project(snapshot,input.query().withTurnkey(false),true);
        Map<String,String> employeeTypes=new HashMap<>();
        shared.getCodes("EMP_TYPE",true).items().forEach(c -> employeeTypes.put(c.code(),c.name()));
        var parentColumns=visible.stream().map(id -> new Column(id,title(id,input.query().view(),resolvedUnit),
            text(id) ? CellType.TEXT : CellType.NUMBER)).toList();
        List<Sheet> sheets=List.of(
            parentSheet("개인원가수익",selected,parentColumns,resolvedUnit,employeeTypes),
            parentSheet("턴키단가미포함",excluded,parentColumns,resolvedUnit,employeeTypes),
            new Sheet("월별배분상세",detailColumns(resolvedUnit),sink -> details(sink,snapshot,selected,resolvedUnit)));
        var period=selected.context().period();
        return new WorkbookData(sheets,List.of(
            new Information("기간", period.year()+"년 "+Objects.toString(period.fromMonth(),"자료 없음")+" ~ "+Objects.toString(period.toMonth(),"자료 없음")),
            new Information("단위",resolvedUnit), new Information("보기",input.query().view()),
            new Information("턴키 단가",input.query().includeTurnkey() ? "포함" : "제외"),
            new Information("인원",selected.total()), new Information("미확인수",selected.unknownCount()),
            new Information("예정 인원",selected.estimatedCount()),new Information("턴키단가미포함 인원",excluded.total()),
            new Information("턴키단가미포함 미확인수",excluded.unknownCount()),
            new Information("열순서",String.join(",",order)), new Information("숨김열",String.join(",",hidden)),
            new Information("부모/상세 기준",input.query().view().equals("MONTHLY_AVERAGE")
                ? "부모: 사람별 근무월 N의 월평균 / 월별배분상세: 평균 전 원본 월 금액·MM" : "부모: 연 누계 / 월별배분상세: 원본 월 금액·MM"),
            new Information("정밀도 안내","금액/MM/건수는 숫자 셀입니다. Excel은 15자리 초과 금액을 반올림할 수 있습니다. 정확한 값은 API에서 확인하세요."),
            new Information("개인 단가 안내","청구·입금 매출이 아닌 개인 배분 참고액입니다.")),selected.total(),Integer.toString(period.year()));
    }
    private Sheet parentSheet(String name, IndividualProfitabilityData data, List<Column> columns, String unit, Map<String,String> types) {
        return new Sheet(name,columns,sink -> {
            for(var person:data.items()) sink.accept(columns.stream().map(c -> parentCell(person,c.id(),unit,types)).toList());
            if(!data.items().isEmpty()) {
                JsonNode metrics=json.valueToTree(data.totals());
                sink.accept(columns.stream().map(c -> "name".equals(c.id()) ? (Object)"전체 합계"
                    : text(c.id()) || "utilization".equals(c.id()) ? null : numeric(metrics.get(c.id()),c.id(),unit)).toList());
            }
        });
    }
    private Object parentCell(IndividualProfitabilityPerson person,String id,String unit,Map<String,String> types) {
        return switch(id) {
            case "name" -> person.name()+" ("+person.managementNo()+")";
            case "companyName" -> person.companyName(); case "position" -> person.position();
            case "employeeType" -> types.getOrDefault(person.employeeType(),person.employeeType());
            case "hireDate" -> person.hireDate()==null ? null : person.hireDate().toString();
            case "utilization" -> decimal(person.utilization(),false,unit);
            default -> numeric(json.valueToTree(person.metrics()).get(id),id,unit);
        };
    }
    private Object numeric(JsonNode value,String id,String unit) {
        return value==null || value.isNull() ? null : decimal(value.asText(),!Set.of("mm","profitRate").contains(id),unit);
    }
    private static Object decimal(String value,boolean amount,String unit) {
        return value==null ? null : amount ? MasterDataValidator.amountInUnit(new BigDecimal(value),unit) : new BigDecimal(value);
    }
    private static boolean text(String id) {return Set.of("name","companyName","position","employeeType","hireDate").contains(id);}
    private static List<String> strings(JsonNode node) {var result=new ArrayList<String>();node.forEach(n -> result.add(n.asText()));return result;}
    private static String conditions(IndividualProfitabilityInput.Query q) {
        return "year="+Objects.toString(q.year(),"서버 올해")+"; view="+q.view()+"; includeTurnkey="+q.includeTurnkey()
            +"; name="+Objects.toString(q.name(),"")+"; employeeType="+Objects.toString(q.employeeType(),"전체")
            +"; companyId="+Objects.toString(q.companyId(),"전체")+"; partId="+Objects.toString(q.partId(),"전체")
            +"; jobCode="+Objects.toString(q.jobCode(),"전체")+"; show="+q.show()+"; sort="+Objects.toString(q.sort(),"기본")+" "+q.direction();
    }
    private static String title(String id,String view,String unit) {
        String label=switch(id) {
            case "name" -> "이름(관리번호)"; case "companyName" -> "소속"; case "position" -> "직위";
            case "employeeType" -> "직원구분"; case "hireDate" -> "입사일"; case "utilization" -> "가동률(%)";
            case "mm" -> "MM"; case "revenue" -> "개인 단가"; case "pay" -> "급여"; case "allowance" -> "수당";
            case "companyInsurance" -> "회사보험"; case "retirementReserve" -> "퇴직충당금";
            case "personalCost" -> "개인 원가"; case "teamExpense" -> "팀비"; case "projectExpense" -> "프로젝트경비";
            case "totalCost" -> "원가합계"; case "profit" -> "영업이익"; case "profitRate" -> "이익률(%)";
            case "selfInsurance" -> "본인보험"; case "withholdingTax" -> "원천세"; case "incomeTax" -> "근로소득세";
            case "netPay" -> "실지급액"; default -> throw new IllegalArgumentException("열 정의 오류");
        };
        return text(id) || Set.of("mm","utilization","profitRate").contains(id) ? label
            : label+" ("+("MONTHLY_AVERAGE".equals(view) ? "월평균" : "누계")+", "+unit+")";
    }
    private static List<Column> detailColumns(String unit) {
        List<String> ids=List.of("view","turnkey","rowType","personId","managementNo","name","month","projectId","assignmentId","projectName","estimated","costClass","contractType","inputType","fixed","assignmentMm","freeMm","personalRevenue","laborAllocated","teamExpense","projectExpense","totalCost","profit","profitRate","pay","allowance","companyInsurance","retirementReserve","costComplete","revenueComplete");
        List<String> titles=List.of("부모 보기(상세는 원본)","턴키 단가","행 종류","개발자ID","관리번호","이름","월","프로젝트ID","투입ID","프로젝트명","예정","원가분류","계약유형","투입유형","확정","MM","무상MM","개인 단가","인건비","팀비","프로젝트경비","원가","이익","이익률(%)","급여","수당","회사보험","퇴직충당금","원가완전","단가완전");
        var result=new ArrayList<Column>();
        for(int i=0;i<ids.size();i++) result.add(new Column(ids.get(i),titles.get(i)+(i>=17&&i<=27&&i!=23 ? " (원본, "+unit+")" : ""),i>=15&&i<=27 ? CellType.NUMBER : CellType.TEXT));
        return List.copyOf(result);
    }
    private void details(Consumer<List<Object>> sink,IndividualProfitabilityService.Snapshot snapshot,IndividualProfitabilityData selected,String unit) {
        for(var person:selected.items()) for(var month:profitability.detailMonths(snapshot,person.personId(),selected.context().includeTurnkey())) {
            var labor=month.laborSource();
            var parent=new ArrayList<Object>(Arrays.asList(selected.context().view(),selected.context().includeTurnkey() ? "포함":"제외","MONTH",person.personId(),person.managementNo(),person.name(),month.month(),null,null,null,labor.estimated() ? "Y":"N",labor.directClass(),null,null,null,
                decimal(month.assignmentMm(),false,unit),null,decimal(month.completeness().revenueComplete() ? month.knownPersonalRevenue() : null,true,unit),decimal(labor.laborCost(),true,unit),null,null,null,null,null,
                decimal(labor.pay(),true,unit),decimal(labor.allowance(),true,unit),decimal(labor.companyInsurance(),true,unit),decimal(labor.retirementReserve(),true,unit),
                month.completeness().costComplete() ? "Y":"N",month.completeness().revenueComplete() ? "Y":"N"));
            sink.accept(parent);
            for(var row:month.rows()) sink.accept(Arrays.asList(selected.context().view(),selected.context().includeTurnkey() ? "포함":"제외","ALLOCATION",row.personId(),person.managementNo(),person.name(),row.month(),row.projectId(),row.assignmentId(),row.projectName(),row.estimated() ? "Y":"N",row.costClass(),row.contractType(),row.inputType(),row.fixed(),
                decimal(row.assignmentMm(),false,unit),decimal(row.freeMm(),false,unit),decimal(row.personalRevenue(),true,unit),decimal(row.laborAllocated(),true,unit),decimal(row.teamExpense(),true,unit),decimal(row.projectExpense(),true,unit),decimal(row.totalCost(),true,unit),decimal(row.profit(),true,unit),decimal(row.profitRate(),false,unit),null,null,null,null,row.costComplete() ? "Y":"N",row.revenueComplete() ? "Y":"N"));
        }
    }
}
