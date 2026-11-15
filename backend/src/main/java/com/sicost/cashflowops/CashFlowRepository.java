package com.sicost.cashflowops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.developers.DeveloperRepository;
import com.sicost.financialitemsops.FinancialPreviewRepository;
import com.sicost.profitability.MonthlyCostMapper;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.*;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("cashFlowEvidenceRepository")
public class CashFlowRepository {
    public record Snapshot(YearMonth base,List<Person> people,List<Payroll> payroll,List<Standard> standards,
        List<CloseRow> closes,List<CodeLookup> rates) {}
    private final CashFlowMapper mapper;
    private final MonthlyCostMapper payroll;
    private final DeveloperRepository people;
    private final SharedQueriesMapper shared;
    private final FinancialPreviewRepository financial;
    private final ObjectMapper json;
    public CashFlowRepository(CashFlowMapper mapper,MonthlyCostMapper payroll,DeveloperRepository people,
        SharedQueriesMapper shared,FinancialPreviewRepository financial,ObjectMapper json) {
        this.mapper=mapper;this.payroll=payroll;this.people=people;this.shared=shared;this.financial=financial;this.json=json;
    }
    public YearMonth base(){return financial.cashBase();}
    public Snapshot load(int year) {
        YearMonth base=base();String end=String.format("%04d12",year);
        Range range=new Range("000101",end);
        return new Snapshot(base,people.historicalPeople(),payroll.payroll(range),payroll.standards(new Range("000101","999912")),
            shared.monthCloses(base.toString().replace("-",""),end),shared.codes("ALWNC",false));
    }
    public JsonNode financial(String month){return financial.snapshot(month);}
    public JsonNode receipts(String month){try{return json.readTree(mapper.receipts(month));}catch(Exception e){throw new IllegalStateException("입금 원천을 조회하지 못했습니다.",e);}}
}
