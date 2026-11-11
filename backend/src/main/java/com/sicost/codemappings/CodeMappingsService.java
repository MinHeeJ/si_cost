package com.sicost.codemappings;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeMappingsService {
    private final CodeMappingsMapper mapper;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final RevisionService revisions;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    public CodeMappingsService(CodeMappingsMapper mapper, CurrentUserProvider users, ChangeHistoryWriter history,
                               RevisionService revisions, CommitInvalidation invalidation, Clock clock) {
        this.mapper=mapper; this.users=users; this.history=history;
        this.revisions=revisions; this.invalidation=invalidation; this.clock=clock;
    }
    public record Mapping(long mapId, String sourceText, String groupId, String code, String codeName, String revision) {}
    public record Page(List<Mapping> items, long total, int page, int size) {}

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Page getCodeMappings(String sourceText, String groupId, int page, int size) {
        InputValidator.page(page); InputValidator.size(size);
        if(sourceText!=null && sourceText.codePointCount(0,sourceText.length())>100) throw InputValidator.invalid("sourceText");
        if(groupId!=null) groupId=InputValidator.groupId(groupId);
        var query=new CodeMappingsMapper.Query(sourceText,groupId,size,((long)page-1)*size);
        long total=mapper.count(query);
        var items=mapper.list(query).stream().map(r -> new Mapping(r.mapId(),r.sourceText(),r.groupId(),r.code(),r.codeName(),revisions.revision(r))).toList();
        return new Page(items,total,page,size);
    }
    @Transactional
    public void createCodeMapping(CodeMappingRequests.Create request) {
        String source=InputValidator.text(request.sourceText(),100,"sourceText");
        String group=InputValidator.groupId(request.groupId());
        String code=InputValidator.code(request.code());
        String actor=users.userId();
        String name=mapper.activeCodeName(group,code);
        if(name==null) throw InputValidator.invalid("code");
        String after=history.mappingJson(new ChangeHistoryWriter.MappingSnapshot(source,group,code,name));
        Long id=mapper.insert(new CodeMappingsMapper.Insert(source,group,code,actor,LocalDateTime.now(clock)));
        if(id==null) throw new ApiException(409,"DUPLICATE_MAPPING","같은 그룹에 이미 등록된 엑셀 표기입니다.","sourceText",null);
        history.append(new ChangeHistoryWriter.Change("TB_SY_CODE_MAP",group+":"+id,"NEW","SRC_TEXT",null,after,null));
        invalidation.afterCommit("code-mappings");
    }
    @Transactional
    public void deleteCodeMappings(CodeMappingRequests.Delete request) {
        users.userId();
        var ordered=request.items().stream().sorted(Comparator.comparingLong(CodeMappingRequests.DeleteItem::mapId)).toList();
        List<CodeMappingRow> rows=new ArrayList<>();
        for(var item:ordered) {
            var row=mapper.lock(item.mapId());
            if(row==null) throw stale();
            revisions.requireExpected(item.expectedRevision(),revisions.revision(row));
            rows.add(row);
        }
        List<String> before=rows.stream().map(r -> history.mappingJson(new ChangeHistoryWriter.MappingSnapshot(r.sourceText(),r.groupId(),r.code(),r.codeName()))).toList();
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            if(mapper.delete(row.mapId())!=1) throw stale();
            history.append(new ChangeHistoryWriter.Change("TB_SY_CODE_MAP",row.groupId()+":"+row.mapId(),"DEL","SRC_TEXT",before.get(i),null,null));
        }
        invalidation.afterCommit("code-mappings");
    }
    private ApiException stale() { return new ApiException(409,"STALE_WRITE","다른 사용자가 변경했습니다. 다시 조회한 뒤 삭제해 주세요."); }
}
