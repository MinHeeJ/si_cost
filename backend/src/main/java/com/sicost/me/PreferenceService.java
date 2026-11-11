package com.sicost.me;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.me.PreferenceDtos.*;

@Service
public class PreferenceService {
    private final PreferenceMapper mapper;
    private final CurrentUserProvider user;
    private final RevisionService revisions;
    private final CommitInvalidation invalidation;
    private final PreferenceValidator validator;
    private final ObjectMapper json;
    public PreferenceService(PreferenceMapper mapper, CurrentUserProvider user, RevisionService revisions,
                             CommitInvalidation invalidation, PreferenceValidator validator, ObjectMapper json) {
        this.mapper=mapper;this.user=user;this.revisions=revisions;this.invalidation=invalidation;this.validator=validator;this.json=json;
    }
    @Transactional(readOnly=true)
    public PreferencesData getPreferences() {
        String actor=user.userId();
        var items=new LinkedHashMap<String,Preference>();
        for(String type:PreferenceValidator.GLOBAL)items.put(type+"/*",missing(type,"*"));
        for(String screen:mapper.screens()) {
            if(Set.of("UI-15","UI-23","UI-27").contains(screen))items.put("COLS/"+screen,missing("COLS",screen));
            if(!validator.periodType(screen).equals("NONE"))items.put("PERIOD/"+screen,missing("PERIOD",screen));
        }
        for(var row:mapper.list(actor))items.put(row.type()+"/"+row.key(),project(row));
        return new PreferencesData(List.copyOf(items.values()));
    }
    private Preference missing(String type,String key) {return new Preference(type,key,validator.defaultValue(type,key),null,false,null);}
    private Preference project(StoredPreference row) {
        String revision=revisions.revision(row);
        try {
            JsonNode value=json.readTree(row.value());validator.validate(row.type(),row.key(),value);
            return new Preference(row.type(),row.key(),value,revision,true,null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | ApiException e) {
            JsonNode fallback;
            try {fallback=validator.defaultValue(row.type(),row.key());} catch(ApiException invalid) {fallback=json.nullNode();}
            return new Preference(row.type(),row.key(),fallback,revision,true,new PreferenceIssue("INVALID_PREFERENCE","저장된 설정을 적용할 수 없습니다. 이 항목을 다시 선택하거나 초기화해 주세요."));
        }
    }
    @Transactional
    public PreferenceSaveResult putPreference(String type,String key,PutPreferenceRequest request) {
        validator.validate(type,key,request.value());
        var id=new PreferenceId(user.userId(),type,key);
        var old=mapper.lock(id);
        if(old==null) {if(request.expectedRevision()!=null)throw stale();}
        else revisions.requireExpected(request.expectedRevision(),revisions.revision(old));
        if(old!=null) {
            try {
                if(request.value().equals(json.readTree(old.value())))return new PreferenceSaveResult(false,project(old),"바뀐 내용이 없습니다.");
            } catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { /* Explicit save repairs only this row. */ }
        }
        var write=new PreferenceWrite(id,validator.serialized(request.value()));
        try {
            int changed=old==null?mapper.insert(write):mapper.update(write);
            if(changed!=1)throw stale();
        } catch(DuplicateKeyException e) {throw stale();}
        var saved=mapper.lock(id);
        if(saved==null)throw new IllegalStateException("저장 결과를 찾을 수 없습니다.");
        invalidation.afterCommit("preferences:"+id.userId()+":"+type+":"+key);
        return new PreferenceSaveResult(true,project(saved),null);
    }
    @Transactional
    public void deletePreference(String type,String key,String expectedRevision) {
        validator.identity(type,key);
        var id=new PreferenceId(user.userId(),type,key);var row=mapper.lock(id);
        if(row==null)throw stale();
        revisions.requireExpected(expectedRevision,revisions.revision(row));
        if(mapper.delete(id)!=1)throw stale();
        invalidation.afterCommit("preferences:"+id.userId()+":"+type+":"+key);
    }
    private ApiException stale() {return new ApiException(409,"STALE_WRITE","다른 사용자가 변경했습니다. 다시 조회한 뒤 저장해 주세요.");}
}
