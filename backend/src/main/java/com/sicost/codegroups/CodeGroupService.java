package com.sicost.codegroups;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.codegroups.CodeGroupDtos.*;
import static com.sicost.codegroups.CodeGroupValidator.*;

@Service
public class CodeGroupService {
    private final CodeGroupMapper mapper;
    private final CodeGroupValidator validator;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    public CodeGroupService(CodeGroupMapper mapper, CodeGroupValidator validator, RevisionService revisions,
                            ChangeHistoryWriter history, CurrentUserProvider users, CommitInvalidation invalidation) {
        this.mapper=mapper; this.validator=validator; this.revisions=revisions;
        this.history=history; this.users=users; this.invalidation=invalidation;
    }
    @Transactional(readOnly=true)
    public Groups groups() { users.currentUser(); return new Groups(mapper.groups()); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page page(String group,int page,int size) {
        users.currentUser(); InputValidator.page(page); InputValidator.size(size);
        Snapshot snapshot=snapshot(group,false); List<Code> all=views(snapshot.codes());
        int start=(int)Math.min((long)(page-1)*size,all.size());
        return new Page(all.subList(start,Math.min(start+size,all.size())),all.size(),page,size,revisions.revision(snapshot));
    }
    @Transactional
    public void create(Create input) {
        String actor=users.userId();
        Create normalized=new Create(InputValidator.groupId(input.groupId()),InputValidator.text(input.name(),50,"name"));
        mapper.lockGroups();
        if(mapper.group(normalized.groupId(),false)!=null) throw conflict("DUPLICATE_GROUP");
        if(mapper.duplicateName(normalized.name())>0) throw conflict("DUPLICATE_GROUP_NAME");
        int order=mapper.maxGroupOrder()+1; if(order>99999) throw conflict("ORDER_LIMIT");
        mapper.insertGroup(new GroupInsert(normalized,order,actor,now()));
        history.append(new ChangeHistoryWriter.Change("TB_SY_CODE_GRP",normalized.groupId(),"NEW","등록",null,
                normalized.groupId()+":"+normalized.name(),null));
        invalidation.afterCommit("code-groups");
    }
    @Transactional
    public SaveResult save(String group,Save input) {
        String actor=users.userId();
        lockDependencies(group);
        Snapshot before=snapshot(group,true); revisions.requireExpected(input.expectedRevision(),revisions.revision(before));
        Map<String,StoredCode> existing=index(before.codes());
        Map<String,StoredCode> finalRows=new LinkedHashMap<>(existing);
        int order=before.codes().stream().mapToInt(StoredCode::sortOrder).max().orElse(0);
        Set<String> seen=new HashSet<>();
        for(Change change:input.changes()) {
            if(!seen.add(change.code())) throw InputValidator.invalid(change.field()+"code");
            StoredCode old=existing.get(change.code());
            if(change.isNew() && "Y".equals(before.group().sysCodeYn())) throw conflict("SYSTEM_CODE_LOCKED");
            if(change.isNew() && old!=null) throw conflict("DUPLICATE_CODE");
            if(change.isNew() && ++order>99999) throw conflict("ORDER_LIMIT");
            finalRows.put(change.code(),validator.apply(before.group(),old,change,order));
        }
        Map<String,StoredCode> references=switch(group) {
            case "JOB" -> finalRows;
            case "SKILL","RSM_JOB_KW" -> index(mapper.codes("JOB",true));
            case "PROJ_STEP" -> index(mapper.codes("CTRT_TYPE",true));
            default -> Map.of();
        };
        for(Change change:input.changes()) validator.references(before.group(),change,existing.get(change.code()),finalRows.get(change.code()),references);
        boolean changed=false; LocalDateTime now=now();
        for(Change change:input.changes()) {
            StoredCode old=existing.get(change.code()), next=finalRows.get(change.code());
            if(old!=null && same(old,next)) continue;
            changed=true;
            Mutation mutation=new Mutation(next,actor,now);
            if(old==null) {
                mapper.insertCode(mutation);
                history.append(new ChangeHistoryWriter.Change("TB_SY_CODE",group+":"+next.code(),"NEW","등록",null,
                        next.code()+":"+next.name()+(next.codeVal()==null?"":":"+next.codeVal().toPlainString()+":"+next.unitNm()),null));
            } else {
                mapper.updateCode(mutation);
                audit(old,next);
            }
        }
        if(changed) invalidation.afterCommit("codes:"+group);
        Snapshot after=changed?snapshot(group,false):before;
        return new SaveResult(changed,changed?"저장했습니다.":"바뀐 내용이 없습니다.",revisions.revision(after),views(after.codes()));
    }
    @Transactional
    public void delete(String group,String code,String expectedRevision) {
        String actor=users.userId();
        Snapshot before=snapshot(group,true); revisions.requireExpected(expectedRevision,revisions.revision(before));
        if("Y".equals(before.group().sysCodeYn())) throw conflict("SYSTEM_CODE_LOCKED");
        if("Y".equals(before.group().valCodeYn())) throw conflict("VALUE_CODE_FIELD_LOCKED");
        StoredCode old=index(before.codes()).get(code);
        if(old==null || "Y".equals(old.delYn())) throw missing();
        validator.requireUnused(group,code);
        StoredCode deleted=new StoredCode(group,code,old.name(),old.sortOrder(),old.useYn(),"Y",old.codeVal(),old.unitNm(),
                old.addVal1(),old.addVal2(),old.addVal3(),old.regUserId(),old.regDttm(),old.modUserId(),old.modDttm());
        mapper.updateCode(new Mutation(deleted,actor,now()));
        history.append(new ChangeHistoryWriter.Change("TB_SY_CODE",group+":"+code,"DEL","DEL_YN","N","Y",null));
        invalidation.afterCommit("codes:"+group);
    }
    private void lockDependencies(String group) {
        Set<String> keys=new TreeSet<>(); keys.add(group);
        if(Set.of("SKILL","RSM_JOB_KW").contains(group)) keys.add("JOB");
        if(group.equals("PROJ_STEP")) keys.add("CTRT_TYPE");
        keys.forEach(key -> { if(mapper.group(key,true)==null) throw missing(); });
    }
    private Snapshot snapshot(String group,boolean lock) {
        StoredGroup row=mapper.group(group,lock); if(row==null) throw missing();
        return new Snapshot(row,mapper.codes(group,lock));
    }
    private Map<String,StoredCode> index(List<StoredCode> rows) {
        Map<String,StoredCode> result=new LinkedHashMap<>(); rows.forEach(row -> result.put(row.code(),row)); return result;
    }
    private List<Code> views(List<StoredCode> rows) {
        return rows.stream().filter(row -> "N".equals(row.delYn())).sorted(Comparator.comparingInt(StoredCode::sortOrder).thenComparing(StoredCode::code)).map(StoredCode::view).toList();
    }
    private boolean same(StoredCode a,StoredCode b) {
        return Objects.equals(a.name(),b.name()) && Objects.equals(a.useYn(),b.useYn()) && Objects.equals(a.addVal1(),b.addVal1())
            && Objects.equals(a.addVal2(),b.addVal2()) && decimalSame(a,b) && Objects.equals(a.unitNm(),b.unitNm());
    }
    private boolean decimalSame(StoredCode a,StoredCode b) { return a.codeVal()==null?b.codeVal()==null:b.codeVal()!=null && a.codeVal().compareTo(b.codeVal())==0; }
    private void audit(StoredCode old,StoredCode next) {
        change(old,"CODE_NM",old.name(),next.name()); change(old,"USE_YN",old.useYn(),next.useYn());
        change(old,"ADD_VAL1",old.addVal1(),next.addVal1()); change(old,"ADD_VAL2",old.addVal2(),next.addVal2());
        if(!decimalSame(old,next)) change(old,"CODE_VAL",old.codeVal()==null?null:old.codeVal().toPlainString(),next.codeVal()==null?null:next.codeVal().toPlainString());
    }
    private void change(StoredCode row,String item,String before,String after) {
        if(Objects.equals(before,after)) return;
        String type=item.equals("USE_YN")?(after.equals("N")?"STOP":"RESUME"):"UPD";
        history.append(new ChangeHistoryWriter.Change("TB_SY_CODE",row.groupId()+":"+row.code(),type,item,before,after,null));
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }
}
