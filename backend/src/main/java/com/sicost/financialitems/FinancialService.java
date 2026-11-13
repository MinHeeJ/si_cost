package com.sicost.financialitems;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.financialitems.FinancialDtos.*;
import static com.sicost.financialitems.FinancialRepository.*;

@Service
public class FinancialService {
    private final FinancialRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final FinancialEditorSources sources;
    public FinancialService(FinancialRepository repository,RevisionService revisions,CurrentUserProvider users,Clock clock,FinancialEditorSources sources){this.repository=repository;this.revisions=revisions;this.users=users;this.clock=clock;this.sources=sources;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> getFinancialItems(String value){
        users.currentUser();YearMonth month=value==null?YearMonth.from(today()):FinancialInput.month(value);
        if(month.isAfter(YearMonth.from(today())))throw conflict("PERIOD_READ_ONLY",month,"미래 월은 조회할 수 없습니다.");
        return editor(month,repository.snapshot(month));
    }
    public Map<String,Object> editor(YearMonth month,Snapshot snapshot){
        String year=month.toString().substring(0,4);List<YearMonth> closed=repository.closed();YearMonth base=repository.cashBase();
        String reason=closed.contains(month)?"마감된 월은 읽기 전용입니다.":month.getYear()!=today().getYear()?"지난 연도는 읽기 전용입니다.":month.isBefore(base)?"운영 시작 이전은 읽기 전용입니다.":null;
        Map<String,Object> editor=new LinkedHashMap<>();editor.put("month",month.toString());editor.put("today",today().toString());editor.put("cashBaseMonth",base.toString());editor.put("editable",reason==null);editor.put("readOnlyReason",reason);editor.put("closeStatus",closed.contains(month)?"CLOSE":"WORK");
        editor.put("revision",revision(snapshot));editor.put("uploadRevision",sources.uploadRevision(month));
        for(Kind kind:List.of(Kind.ITEMS,Kind.ADJUSTMENTS,Kind.TRANSFERS,Kind.LOANS,Kind.CARDS))editor.put(kind.collection(),snapshot.rows(kind).stream().map(r->repository.view(kind,r)).toList());
        editor.put("cardPayments",snapshot.rows(Kind.PAYMENTS).stream().map(r->repository.view(Kind.PAYMENTS,r)).toList());
        Map<String,Object> cardMonth=new LinkedHashMap<>();ObjectNode stored=snapshot.rows(Kind.CARD_MONTH).stream().findFirst().orElse(null);
        BigDecimal sum=stored==null?BigDecimal.ZERO:number(stored,"card_sum_amt"),cash=stored==null?BigDecimal.ZERO:number(stored,"cash_amt");
        cardMonth.put("month",month.toString());cardMonth.put("cardSumAmount",string(sum));cardMonth.put("cashAmount",string(cash));cardMonth.put("editYn",stored==null?"N":text(stored,"edit_yn"));cardMonth.put("difference",string(cash.subtract(sum)));cardMonth.put("exists",stored!=null);cardMonth.put("audit",stored==null?null:repository.audit(stored));editor.put("cardMonth",cardMonth);
        editor.put("automaticDrafts",reason==null&&snapshot.rows(Kind.ITEMS).isEmpty()?drafts(month):Collections.emptyList());editor.put("adjustmentTargetMonths",repository.targets(month));
        YearMonth first=YearMonth.of(today().getYear(),1);if(base.isAfter(first))first=base;while(!first.isAfter(YearMonth.from(today()))&&closed.contains(first))first=first.plusMonths(1);
        editor.put("firstEditableMonth",first.isAfter(YearMonth.from(today()))?null:first.toString());
        editor.put("corporateTax",sources.annual(year));editor.put("allocation",sources.allocation(year));editor.put("contributions",sources.contributions(month));
        Set<String> years=new TreeSet<>();snapshot.rows(Kind.TAX_PAYMENTS).forEach(r->years.add(text(r,"tax_yr")));editor.put("taxPayments",sources.payments(month,years));return editor;
    }
    private List<Map<String,Object>> drafts(YearMonth month){
        List<Map<String,Object>> drafts=new ArrayList<>();for(ObjectNode row:repository.rows(Kind.ITEMS,month.minusMonths(1))){if(!"Y".equals(text(row,"rpt_yn")))continue;
            Map<String,Object> draft=new LinkedHashMap<>(repository.view(Kind.ITEMS,row));draft.remove("itemSn");draft.remove("month");draft.remove("uploadId");draft.remove("audit");draft.put("sourceMonth",month.minusMonths(1).toString());draft.put("sourceItemSn",text(row,"item_sn"));draft.put("autoFill","Y");
            String date=text(row,"io_dt");if(date!=null){int day=LocalDate.parse(date).getDayOfMonth();draft.put("ioDate",month.atDay(Math.min(day,month.lengthOfMonth())).toString());}
            List<Map<String,String>> errors=new ArrayList<>();String code=text(row,"sga_item_cd");if(code!=null&&repository.code("SGA_ITEM",code)==null)errors.add(Map.of("field","sgaItemCode","message","삭제되었거나 없는 코드입니다."));draft.put("errors",errors);drafts.add(draft);
        }return drafts;
    }
    public String revision(Snapshot snapshot){return revisions.revision(FinancialRepository.canonical(snapshot));}
    public LocalDate today(){return LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));}
    public static ApiException conflict(String code,YearMonth month,String message){return new ApiException(409,code,message,null,Map.of("financial",Map.of("month",month.toString(),"business","FINANCIAL_ITEMS")));}
}
