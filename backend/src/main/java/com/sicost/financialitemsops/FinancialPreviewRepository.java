package com.sicost.financialitemsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.sicost.common.financial.FinancialCalculation.TaxMonth;
import com.sicost.shared.SharedQueriesMapper;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.financialitemsops.FinancialPreviewDtos.*;

@Repository
public class FinancialPreviewRepository {
    private final FinancialPreviewMapper mapper;
    private final SharedQueriesMapper shared;

    private final ObjectMapper json;
    public FinancialPreviewRepository(FinancialPreviewMapper mapper,SharedQueriesMapper shared,ObjectMapper json){this.mapper=mapper;this.shared=shared;this.json=json;}
    public YearMonth cashBase(){String value=shared.cashBaseMonth();if(value==null)throw new IllegalStateException("운영 시작월이 없습니다.");return YearMonth.parse(value.substring(0,4)+"-"+value.substring(4));}
    public List<StoredMonth> yearStatus(String year){return mapper.yearStatus(year);}
    public ObjectNode snapshot(String month){return (ObjectNode)parse(mapper.snapshot(month));}
    public JsonNode previous(YearMonth month){return parse(mapper.previousItems(month.toString()));}
    public boolean closed(YearMonth month){String value=month.toString().replace("-","");return shared.monthCloses(value,value).stream().anyMatch(r->"CLOSE".equals(r.status()));}
    public List<TaxMonth> taxMonths(String month){return mapper.taxMonths(month);}
    public boolean itemCodeExists(String code,boolean includeInactive){return shared.codes("SGA_ITEM",includeInactive).stream().anyMatch(c->c.code().equals(code));}
    private JsonNode parse(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException("재무 자료를 읽지 못했습니다.");}}
}
