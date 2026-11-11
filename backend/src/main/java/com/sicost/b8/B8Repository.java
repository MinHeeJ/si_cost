package com.sicost.b8;

import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Repository;
import com.sicost.b7.B7Dtos.Upload;
import com.sicost.b7.B7Dtos.UploadWrite;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssueWrite;
import com.sicost.b7.B7Dtos.Parsed;
import static com.sicost.b8.B8Dtos.*;

@Repository
public class B8Repository {
    private final B8Mapper mapper;
    private final MasterIdService ids;
    private final ChangeHistoryWriter history;
    public B8Repository(B8Mapper mapper,MasterIdService ids,ChangeHistoryWriter history){this.mapper=mapper;this.ids=ids;this.history=history;}
    public Upload lock(long id){return mapper.lockUpload(id);}
    public List<Issue> issues(long id){return mapper.issues(id);}
    public long create(UploadWrite write,Checked checked) {
        var draft=new UploadDraft(write);one(mapper.insertUpload(draft));
        if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호를 발급하지 못했습니다.");
        saveIssues(draft.getUploadId(),write,checked);return draft.getUploadId();
    }
    public void replace(UploadWrite write,Checked checked) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),write,checked);
    }
    private void saveIssues(long id,UploadWrite write,Checked checked) {
        for(Issue issue:checked.issues())one(mapper.insertIssue(new IssueWrite(id,issue,write.actor(),write.now())));
    }
    public List<NumberRow> apply(long uploadId,Checked checked,String actor,LocalDateTime now) {
        Set<String> direct=new HashSet<>();checked.developers().forEach(d->{if(d.managementNo()!=null)direct.add(d.managementNo());});
        List<NumberRow> finalNumbers=new ArrayList<>();
        for(Developer d:checked.developers()) {
            String number=d.managementNo();
            if(number==null)do{number=ids.nextManagementNo();}while(direct.contains(number));
            direct.add(number);String id=ids.nextDeveloperId();
            String month=YearMonth.from(d.hireDate()==null?now.toLocalDate():d.hireDate()).format(DateTimeFormatter.ofPattern("uuuuMM"));
            var write=new DeveloperWrite(id,number,d,actor,now,month);
            one(mapper.insertDeveloper(write));one(mapper.insertPrice(write));
            int order=0;for(String skill:d.skills())one(mapper.insertSkill(new SkillWrite(id,skill,++order,actor,now)));
            String reason="B8 업로드:"+uploadId;
            history.append(new ChangeHistoryWriter.Change("TB_HR_DEV",id,"NEW","등록",null,number+" "+d.name(),reason));
            history.append(new ChangeHistoryWriter.Change("TB_HR_DEV_PRC",id+":"+month,"NEW","첫 가격",null,
                "월 단가="+Objects.toString(d.monthlyRate(),"NULL")+", 월 급여·지급액="+Objects.toString(d.monthlyPay(),"NULL"),reason));
            finalNumbers.add(new NumberRow(d.rowNumber(),number,id));
        }
        one(mapper.markApplied(uploadId,actor,now));return List.copyOf(finalNumbers);
    }
    public List<NumberRow> recover(long uploadId,Parsed parsed) {
        var saved=mapper.recovered("B8 업로드:"+uploadId);
        if(saved.size()!=parsed.rows().size())throw new IllegalStateException("반영된 관리번호를 확인하지 못했습니다.");
        List<NumberRow> numbers=new ArrayList<>();
        for(int i=0;i<saved.size();i++)numbers.add(new NumberRow(parsed.rows().get(i).number(),saved.get(i).managementNo(),saved.get(i).id()));
        return numbers;
    }
    private void one(int n){if(n!=1)throw new IllegalStateException("업로드 결과를 저장하지 못했습니다.");}
}
