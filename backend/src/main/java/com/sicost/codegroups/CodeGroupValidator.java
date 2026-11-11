package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.codegroups.CodeGroupDtos.*;

@Component
public class CodeGroupValidator {
    private final CodeReferenceGuard guard;
    public CodeGroupValidator(CodeReferenceGuard guard) { this.guard=guard; }
    public void requireUnused(String group,String code) { guard.requireUnused(group,code); }
    public StoredCode apply(StoredGroup group, StoredCode old, Change change, int order) {
        Set<String> allowed;
        if ("Y".equals(group.sysCodeYn())) {
            if (change.isNew()) throw conflict("SYSTEM_CODE_LOCKED");
            allowed=Set.of("name");
        } else if ("Y".equals(group.valCodeYn())) {
            allowed=change.isNew() ? Set.of("name","codeVal","unitNm") : Set.of("codeVal");
        } else allowed=Set.of("name","useYn","addVal1","addVal2");
        for (String key:change.values().keySet()) {
            if (!allowed.contains(key)) {
                if ("Y".equals(group.sysCodeYn())) throw conflict("SYSTEM_CODE_LOCKED");
                if ("Y".equals(group.valCodeYn())) throw conflict("VALUE_CODE_FIELD_LOCKED");
                throw InputValidator.invalid(change.field()+key);
            }
        }
        if (!change.isNew() && (old==null || "Y".equals(old.delYn()))) throw missing();
        String name = old==null ? null : old.name();
        String use = old==null ? "Y" : old.useYn();
        String a1 = old==null ? null : old.addVal1(), a2 = old==null ? null : old.addVal2();
        BigDecimal value=old==null ? null : old.codeVal(); String unit=old==null ? null : old.unitNm();
        if (change.isNew() || change.values().containsKey("name")) name=InputValidator.text(string(change,"name"),100,change.field()+"name");
        if (change.values().containsKey("useYn")) {
            use=InputValidator.yesNo(string(change,"useYn"),change.field()+"useYn");
            if (change.isNew() && !"Y".equals(use)) throw InputValidator.invalid(change.field()+"useYn");
        }
        if (!"Y".equals(group.sysCodeYn()) && "Y".equals(group.valCodeYn())) {
            if (change.isNew() || change.values().containsKey("codeVal")) value=DecimalValidator.decimal(optionalString(change,"codeVal"),true,change.field()+"codeVal");
            if (change.isNew()) {
                unit=change.values().containsKey("unitNm") ? string(change,"unitNm") : "원";
                if (!Set.of("원","%").contains(unit)) throw InputValidator.invalid(change.field()+"unitNm");
            }
        } else if (!"Y".equals(group.sysCodeYn())) {
            int extras=extras(group.groupId());
            for(int i=1;i<=2;i++) {
                String key="addVal"+i;
                if (i>extras && change.values().containsKey(key)) throw InputValidator.invalid(change.field()+key);
                if (i<=extras && (change.isNew() || change.values().containsKey(key))) {
                    String normalized=extra(group.groupId(),i,string(change,key),change.field()+key);
                    if(i==1) a1=normalized; else a2=normalized;
                }
            }
        }
        return new StoredCode(group.groupId(),change.code(),name,old==null?order:old.sortOrder(),use,"N",value,unit,a1,a2,
                old==null?null:old.addVal3(),old==null?null:old.regUserId(),old==null?null:old.regDttm(),old==null?null:old.modUserId(),old==null?null:old.modDttm());
    }
    public static int extras(String group) {
        return switch(group) { case "JOB","PROJ_STEP" -> 2; case "SKILL","BANK","RSM_LABEL","RSM_JOB_KW" -> 1; default -> 0; };
    }
    public static String extra(String group,int index,String input,String field) {
        String value=InputValidator.text(input,Integer.MAX_VALUE,field);
        if (group.equals("JOB") && index==2) {
            try { return DecimalValidator.jobAmount(value); } catch(ApiException e) { throw InputValidator.invalid(field); }
        }
        if (group.equals("BANK")) { if(!value.matches("[0-9]{3}")) throw InputValidator.invalid(field); return value; }
        if (group.equals("PROJ_STEP") && index==1) {
            if (!value.matches("[0-9]{1,3}") || Integer.parseInt(value)>100) throw InputValidator.invalid(field);
            return Integer.toString(Integer.parseInt(value));
        }
        if (group.equals("RSM_LABEL")) {
            value=value.toUpperCase(Locale.ROOT);
            if(!Set.of("NAME","TEL","MAIL","ADDR","SKILL","WANT","FROM","VNDR").contains(value)) throw InputValidator.invalid(field);
            return value;
        }
        LinkedHashSet<String> codes=new LinkedHashSet<>();
        for(String part:value.split(",",-1)) {
            try { codes.add(InputValidator.code(part)); } catch(ApiException e) { throw InputValidator.invalid(field); }
        }
        return InputValidator.text(String.join(",",codes),100,field);
    }
    public void references(StoredGroup group, Change change, StoredCode old, StoredCode next, Map<String,StoredCode> targets) {
        String csv=null, previous=null, field=null;
        if(Set.of("JOB","SKILL","RSM_JOB_KW").contains(group.groupId())) {
            csv=next.addVal1(); previous=old==null?null:old.addVal1(); field="addVal1";
        } else if(group.groupId().equals("PROJ_STEP")) {
            csv=next.addVal2(); previous=old==null?null:old.addVal2(); field="addVal2";
        }
        if(csv==null) return;
        Set<String> prior=previous==null?Set.of():new HashSet<>(Arrays.asList(previous.split(",")));
        for(String code:csv.split(",")) {
            StoredCode target=targets.get(code);
            if(target==null || "Y".equals(target.delYn()) || (!prior.contains(code) && !"Y".equals(target.useYn())))
                throw InputValidator.invalid(change.field()+field);
        }
    }
    private String string(Change change,String key) { return CodeGroupInput.text(change.values().get(key),change.field()+key); }
    private String optionalString(Change change,String key) {
        JsonNode node=change.values().get(key);
        return node==null ? null : CodeGroupInput.text(node,change.field()+key);
    }
    public static ApiException missing() { return new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다."); }
    public static ApiException conflict(String code) { return new ApiException(409,code,switch(code) {
        case "SYSTEM_CODE_LOCKED" -> "시스템 코드입니다. 이름만 바꿀 수 있습니다.";
        case "VALUE_CODE_FIELD_LOCKED" -> "값 코드의 기존 값만 바꿀 수 있습니다.";
        case "DUPLICATE_CODE" -> "이미 등록된 코드입니다. 삭제한 코드도 다시 사용할 수 없습니다.";
        case "DUPLICATE_GROUP" -> "이미 등록된 그룹 ID입니다.";
        case "DUPLICATE_GROUP_NAME" -> "이미 등록된 그룹 이름입니다.";
        default -> "순서의 허용 범위를 초과했습니다.";
    }); }
}
