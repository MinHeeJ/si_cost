package com.sicost.apiops;

import java.util.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.apiops.StaffingQueryInput.invalid;

public final class WithdrawalInput {
    private WithdrawalInput() {}
    public record Query(Integer year,String period,String extension,String job,int page,int size) {}
    public static Query parse(MultiValueMap<String,String> raw,String body) {
        StaffingQueryInput.validate(raw,Set.of("year","period","extension","job","page","size"),body);
        var q=new org.springframework.util.LinkedMultiValueMap<String,String>();
        raw.forEach((k,v)->q.add(k,v.get(0).strip()));
        int size=StaffingQueryInput.integer(q.getFirst("size"),50,"size");
        if(!Set.of(20,50,100).contains(size))throw invalid("size","VALIDATION_ERROR");
        String job=q.getFirst("job");
        if(job!=null&&(job.isEmpty()||job.length()>10||job.chars().anyMatch(Character::isISOControl)))throw invalid("job","VALIDATION_ERROR");
        return new Query(q.containsKey("year")?StaffingQueryInput.integer(q.getFirst("year"),0,"year"):null,
            StaffingQueryInput.choice(q,"period",Set.of("ALL","SOON","LATER","PAST"),"ALL"),
            StaffingQueryInput.choice(q,"extension",Set.of("ALL","H","M","L","UNSET"),"ALL"),job,
            StaffingQueryInput.integer(q.getFirst("page"),1,"page"),size);
    }
}
