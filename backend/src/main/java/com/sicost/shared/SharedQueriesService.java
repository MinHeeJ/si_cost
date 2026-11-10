package com.sicost.shared;

import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.PeriodValidator;
import com.sicost.shared.SharedQueryDtos.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class SharedQueriesService {
    private final SharedQueriesMapper mapper;
    private final Clock clock;
    public SharedQueriesService(SharedQueriesMapper mapper,Clock clock) { this.mapper=mapper; this.clock=clock; }
    public Items<CodeLookup> getCodes(String group,boolean includeInactive) {
        if(!group.matches("[A-Z][A-Z0-9_]{0,29}")) throw InputValidator.invalid("group");
        return new Items<>(mapper.codes(group,includeInactive));
    }
    public Parameter getParameter(String key,String month) {
        if(key.isBlank() || key.length()>30) throw InputValidator.invalid("key");
        String requested=month==null?YearMonth.from(today()).toString():parseMonth(month,"month").toString();
        Parameter result=mapper.parameter(key,DatabaseMonthCodec.toDatabase(requested));
        if(result==null) throw new ApiException(404,"STANDARD_NOT_FOUND","요청 월에 유효한 운영 기준값이 없습니다.");
        return result;
    }
    public Items<MonthClose> getMonthClose(String from,String to) {
        YearMonth start=parseMonth(from,"from"), end=parseMonth(to,"to");
        if(start.isAfter(end)) throw invalidPeriod("to");
        YearMonth base=cashBase();
        return new Items<>(closes(start,end,base));
    }
    public PeriodContext getPeriodContext() {
        LocalDate date=today(); YearMonth current=YearMonth.from(date),base=cashBase();
        return new PeriodContext(date,current.toString(),"Asia/Seoul",base.toString(),closes(current,current,base).get(0));
    }
    public HistoryPage getCommonCodeHistory(String targetType,String groupId,int page,int size) {
        if(targetType!=null && !Set.of("GROUP","CODE","VALUE_CODE","OPER_STD","CODE_MAP").contains(targetType)) throw InputValidator.invalid("targetType");
        if(groupId!=null && !groupId.matches("[A-Z][A-Z0-9_]{0,29}")) throw InputValidator.invalid("groupId");
        InputValidator.page(page); InputValidator.size(size);
        HistoryQuery query=new HistoryQuery(targetType,groupId,size,((long)page-1)*size);
        return new HistoryPage(mapper.history(query),mapper.historyCount(query),page,size);
    }
    private LocalDate today() { return clock.instant().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(); }
    private YearMonth cashBase() {
        String value=mapper.cashBaseMonth();
        if(value==null) throw new IllegalStateException("운영 시작월이 없습니다.");
        return YearMonth.parse(DatabaseMonthCodec.toApi(value));
    }
    private List<MonthClose> closes(YearMonth from,YearMonth to,YearMonth base) {
        Map<String,String> actual=new HashMap<>();
        mapper.monthCloses(DatabaseMonthCodec.toDatabase(from.toString()),DatabaseMonthCodec.toDatabase(to.toString()))
            .forEach(row -> actual.put(row.month(),row.status()));
        List<MonthClose> result=new ArrayList<>();
        for(YearMonth month=from;!month.isAfter(to);month=month.plusMonths(1)) {
            String status=month.isBefore(base)?"이관·결산완료":
                "CLOSE".equals(actual.get(DatabaseMonthCodec.toDatabase(month.toString())))?"마감":"작업중";
            result.add(new MonthClose(month.toString(),status));
        }
        return result;
    }
    private YearMonth parseMonth(String value,String field) {
        try { return PeriodValidator.month(value); }
        catch(ApiException e) { throw invalidPeriod(field); }
    }
    private ApiException invalidPeriod(String field) { return new ApiException(400,"INVALID_PERIOD","실제 연월과 조회 범위의 순서를 확인해 주세요.",field,null); }
}
