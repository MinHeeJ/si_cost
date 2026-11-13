package com.sicost.b2;

import com.sicost.b7.B7Dtos.IssuesPage;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.b2.FinancialImportDtos.*;

@Repository
public class FinancialImportRepository {
    private final FinancialImportMapper mapper;
    private final ChangeHistoryWriter history;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public FinancialImportRepository(FinancialImportMapper mapper,ChangeHistoryWriter history,RevisionService revisions,CurrentUserProvider users,Clock clock) {
        this.mapper=mapper;this.history=history;this.revisions=revisions;this.users=users;this.clock=clock;
    }
    public String actor() { return users.userId(); }
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL); }
    public String revision(String month) { return revisions.revision(mapper.snapshot(month)); }
    public void expected(String expected,String actual) { revisions.requireExpected(expected,actual); }
    public Map<String,String> context(long id) { Map<String,String> result=new HashMap<>();mapper.metadata(id).forEach(m->result.put(m.item(),m.value()));return result; }
    public void metadata(long id,String item,String value) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_UPLD",Long.toString(id),"NEW",item,null,value,"B2 문맥"));
    }
    public Upload required(long id,boolean lock) {
        Upload upload=lock?mapper.lockUpload(id):mapper.upload(id);
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 기록을 찾을 수 없습니다.");return upload;
    }
    public long create(UploadWrite write,Checked checked,String fingerprint,String revision) {
        Draft draft=new Draft(write);one(mapper.insertUpload(draft));
        if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호가 없습니다.");long id=draft.getUploadId();
        metadata(id,"파일지문",fingerprint);metadata(id,"검증revision",revision);summaryMetadata(id,checked.summary());saveIssues(id,checked);return id;
    }
    public void replace(UploadWrite write,Checked checked,String revision) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),checked);
        metadata(write.uploadId(),"검증revision",revision);summaryMetadata(write.uploadId(),checked.summary());
    }
    private void summaryMetadata(long id,Summary summary) { metadata(id,"교체수",Integer.toString(summary.replaceCount()));metadata(id,"보존수",Integer.toString(summary.preserveCount())); }
    private void saveIssues(long id,Checked checked) { for(var issue:checked.issues())one(mapper.insertIssue(new IssueWrite(id,issue,actor(),now()))); }
    public Result result(Upload upload) {
        Map<String,String> context=context(upload.uploadId());boolean applied="APPLIED".equals(upload.status());
        var issues=mapper.issues(upload.uploadId());var items=applied?mapper.resultItems(upload.uploadId()):Collections.<ResultItem>emptyList();
        String revision=context.get(applied?"최초revision":"검증revision");
        if(revision==null||(applied&&items.size()!=upload.totalRows()))throw new IllegalStateException("재무 반영 결과를 확인하지 못했습니다.");
        Summary summary=new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),Integer.parseInt(context.get("교체수")),Integer.parseInt(context.get("보존수")));
        return new Result(Long.toString(upload.uploadId()),"B2","UI-10",apiMonth(upload.month()),upload.status(),summary,!applied&&upload.errorRows()==0,revision,
            applied&&Boolean.parseBoolean(context.get("변경여부")),upload.appliedAt()==null?null:upload.appliedAt().toString(),new IssuesPage(issues.stream().limit(50).toList(),issues.size(),1,50),items);
    }
    public void apply(Upload upload,Checked checked,List<StoredItem> old) {
        int next=old.stream().mapToInt(StoredItem::itemSn).max().orElse(0);
        if((long)next+checked.rows().size()>99999)throw new ApiException(409,"ID_EXHAUSTED","선택월 항목 번호의 범위를 초과합니다.");
        for(StoredItem row:old)if(row.uploadId()!=null)audit(upload.month(),row.itemSn(),fields(row),null,"B2:"+upload.uploadId());
        int removed=mapper.deleteUploaded(upload.month());
        if(removed!=checked.summary().replaceCount())throw new IllegalStateException("교체 범위가 변경되었습니다.");
        for(CheckedRow row:checked.rows()) {
            ItemWrite write=new ItemWrite(upload.month(),++next,row.value(),upload.uploadId(),actor(),now());one(mapper.insertItem(write));
            Map<String,Object> after=fields(row.value());after.put("AUTO_FILL_YN","N");after.put("UPLD_ID",upload.uploadId());
            audit(upload.month(),next,null,after,"B2:"+upload.uploadId()+":"+row.rowNumber());
        }
        metadata(upload.uploadId(),"최초revision",revision(upload.month()));metadata(upload.uploadId(),"변경여부","true");
        one(mapper.markApplied(new UploadWrite(upload.uploadId(),upload.filename(),upload.size(),upload.month(),checked.summary(),actor(),now())));
    }
    private void audit(String month,int sn,Map<String,Object> before,Map<String,Object> after,String reason) {
        Map<String,Object> source=before==null?after:before;
        for(String field:source.keySet())history.append(new ChangeHistoryWriter.Change("TB_FI_ITEM",month+":"+sn,before==null?"NEW":"DEL",field,text(before==null?null:before.get(field)),text(after==null?null:after.get(field)),reason));
    }
    private Map<String,Object> fields(StoredItem r) { var result=fields(new Values(r.finClass(),r.sgaItemCode(),r.itemName(),r.amount(),r.vatAmount(),r.ioDate(),r.repeat(),r.remark()));result.put("AUTO_FILL_YN",r.autoFill());result.put("UPLD_ID",r.uploadId());return result; }
    private Map<String,Object> fields(Values v) {
        Map<String,Object> values=new LinkedHashMap<>();String[] keys={"FIN_CLS_CD","SGA_ITEM_CD","ITEM_NM","AMT","VAT_AMT","IO_DT","RPT_YN","RMRK"};
        Object[] data={v.finClass(),v.sgaItemCode(),v.itemName(),v.amount(),v.vatAmount(),v.ioDate(),v.repeat(),v.remark()};for(int i=0;i<keys.length;i++)values.put(keys[i],data[i]);return values;
    }
    private String text(Object v) { return v instanceof BigDecimal d?d.toPlainString():v==null?null:v.toString(); }
    public static String apiMonth(String month) { return month.substring(0,4)+"-"+month.substring(4); }
    public static void one(int count) { if(count!=1)throw new IllegalStateException("재무 항목과 필수 기록을 저장하지 못했습니다."); }
}
