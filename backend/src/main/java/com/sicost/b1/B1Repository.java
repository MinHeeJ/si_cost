package com.sicost.b1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.ApiException;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.b1.B1Dtos.*;

@Repository
public class B1Repository {
    private final B1Mapper mapper;
    private final ChangeHistoryWriter history;
    private final ObjectMapper json;
    public B1Repository(B1Mapper mapper,ChangeHistoryWriter history,ObjectMapper json) { this.mapper=mapper;this.history=history;this.json=json; }
    public long create(UploadWrite write,Checked checked,String digest,String revision) {
        Draft draft=new Draft(write);one(mapper.insertUpload(draft));
        if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호를 발급하지 못했습니다.");
        long id=draft.getUploadId();
        metadata(id,"파일지문",null,digest,"B1 문맥");
        metadata(id,"검증revision",null,revision,"B1 문맥");saveIssues(id,write,checked);
        return id;
    }
    public void replace(UploadWrite write,Checked checked,String revision) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),write,checked);
        metadata(write.uploadId(),"검증revision",context(write.uploadId()).get("검증revision"),revision,"B1 문맥");
    }
    private void saveIssues(long id,UploadWrite write,Checked checked) {
        for(var issue:checked.issues())one(mapper.insertIssue(new IssueWrite(id,issue,write.actor(),write.now())));
    }
    public Map<String,String> context(long id) {
        Map<String,String> result=new HashMap<>();mapper.receipts(id,"B1 문맥").forEach(r->result.put(r.item(),r.value()));return result;
    }
    private void metadata(long id,String item,String before,String after,String reason) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_UPLD",Long.toString(id),before==null?"NEW":"UPD",item,before,after,reason));
    }
    public Replacement preview(String month,Checked checked) {
        Map<String,Values> old=values(month);Set<String> ids=new HashSet<>();int created=0,changed=0;
        for(CheckedRow row:checked.rows()) {
            if(!ids.add(row.developer().id()))continue;
            Values previous=old.get(row.developer().id());
            if(previous==null)created++;else if(!same(previous,row.values()))changed++;
        }
        var removed=mapper.members(month).stream().filter(r->!ids.contains(r.developerId())).toList();
        return new Replacement(created,changed,removed.size(),removed);
    }
    public Map<String,Values> values(String month) {
        Map<String,Values> result=new TreeMap<>();mapper.payroll(month).forEach(p->result.put(p.developerId(),p.values()));return result;
    }
    public boolean apply(Upload upload,Checked checked,String actor,LocalDateTime now) {
        Map<String,Values> old=values(upload.month());Set<String> incoming=new HashSet<>();boolean changed=false;
        for(CheckedRow row:checked.rows()) {
            String dev=row.developer().id();incoming.add(dev);Values before=old.get(dev);Values after=row.values();
            if(before==null||!same(before,after)) {
                var write=new PayWrite(upload.month(),dev,after,upload.uploadId(),actor,now);
                one(before==null?mapper.insertPay(write):mapper.updatePay(write));audit(upload.month(),dev,before,after,upload.uploadId());changed=true;
            }
        }
        for(var entry:old.entrySet())if(!incoming.contains(entry.getKey())) {
            one(mapper.deletePay(upload.month(),entry.getKey()));audit(upload.month(),entry.getKey(),entry.getValue(),null,upload.uploadId());changed=true;
        }
        return changed;
    }
    public void receipt(long id,Checked checked,String revision,boolean changed) {
        metadata(id,"최초revision",null,revision,"B1 결과");metadata(id,"변경여부",null,Boolean.toString(changed),"B1 결과");
        for(CheckedRow row:checked.rows())metadata(id,"행:"+row.rowNumber(),null,row.developer().id(),"B1 결과");
    }
    public Result result(Upload upload) {
        var issues=mapper.issues(upload.uploadId());
        Summary summary=new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),
            (int)issues.stream().filter(i->i.severity().equals("WARN")&&i.columnName().equals("[파일]")).count(),0);
        Map<String,String> receipt=new HashMap<>();mapper.receipts(upload.uploadId(),"B1 결과").forEach(r->receipt.put(r.item(),r.value()));
        boolean applied=upload.status().equals("APPLIED");List<ResultRow> rows=new ArrayList<>();
        if(applied)receipt.forEach((k,v)->{if(k.startsWith("행:"))rows.add(new ResultRow(Integer.parseInt(k.substring(2)),v));});
        rows.sort(Comparator.comparingInt(ResultRow::rowNumber));
        String revision=applied?receipt.get("최초revision"):context(upload.uploadId()).get("검증revision");
        if(revision==null||(applied&&rows.size()!=upload.totalRows()))throw new IllegalStateException("급여 반영 결과 이력을 확인하지 못했습니다.");
        return new Result(upload.uploadId(),"B1","UI-02",upload.status(),summary,List.copyOf(rows),applied&&Boolean.parseBoolean(receipt.get("변경여부")),apiMonth(upload.month()),revision);
    }
    private void audit(String month,String dev,Values before,Values after,long uploadId) {
        Map<String,Object> previous=fields(before),next=fields(after);
        for(String key:previous.keySet()) {
            String b=text(previous.get(key)),a=text(next.get(key));
            if(!Objects.equals(b,a))history.append(new ChangeHistoryWriter.Change("TB_HR_MON_PAY",month+":"+dev,before==null?"NEW":after==null?"DEL":"UPD",key,b,a,"B1 업로드:"+uploadId));
        }
    }
    private Map<String,Object> fields(Values value) {
        Map<String,Object> result=new LinkedHashMap<>();
        String[] names={"PAY_AMT","ALWNC_AMT","INSR_CMPNY_AMT","INSR_SELF_AMT","RETIRE_RSV_AMT","INCM_TAX_AMT","WTHLD_TAX_AMT","REAL_PAY_AMT","VNDR_SPLY_AMT","EMP_TYPE_CD","DRCT_CLS_CD"};
        Object[] values=value==null?new Object[names.length]:new Object[]{value.pay(),value.allowance(),value.companyInsurance(),value.selfInsurance(),value.retirement(),value.incomeTax(),value.withholding(),value.realPay(),value.vendorSupply(),value.employeeType(),value.directClass()};
        for(int i=0;i<names.length;i++)result.put(names[i],values[i]);return result;
    }
    public boolean same(Values a,Values b) { return fields(a).entrySet().stream().allMatch(e->Objects.equals(text(e.getValue()),text(fields(b).get(e.getKey())))); }
    private String text(Object value) { return value instanceof BigDecimal n?n.stripTrailingZeros().toPlainString():value==null?null:value.toString(); }
    public static String apiMonth(String month) { return com.sicost.common.persistence.DatabaseMonthCodec.toApi(month); }
    public static ApiException mismatch() { return new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","같은 처리자·양식·화면·파일·대상월의 검사를 사용해 주세요."); }
    public static void one(int count) { if(count!=1)throw new IllegalStateException("급여와 필수 기록을 저장하지 못했습니다."); }
}
