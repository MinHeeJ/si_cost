package com.sicost.workforcerecommendationsops;

import com.sicost.common.api.ApiException;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;

public final class RecommendationBrowseInput {
    private RecommendationBrowseInput() {}
    public static void query(MultiValueMap<String,String> q,Set<String> allowed) {
        q.forEach((key,values)->{
            if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);
            if(values.size()!=1)throw invalid(key,"조회 항목은 한 번만 지정해 주세요.");
        });
    }
    public static HistoryQuery history(MultiValueMap<String,String> q) {
        query(q,Set.of("targetType","targetId","job","recommendationType","page","size"));
        String type=value(q,"targetType",11),id=value(q,"targetId",20),job=value(q,"job",10),kind=value(q,"recommendationType",4);
        if(type!=null&&!Set.of("OPPORTUNITY","PROJECT").contains(type))throw invalid("targetType","대상 유형을 확인해 주세요.");
        if((type==null)!=(id==null))throw invalid(type==null?"targetType":"targetId","대상 유형과 대상 ID를 함께 지정해 주세요.");
        if(kind!=null&&!Set.of("AI","NEXT").contains(kind))throw invalid("recommendationType","추천 유형을 확인해 주세요.");
        int page=integer(q,"page",1),size=integer(q,"size",50);
        if(page<1)throw invalid("page","페이지는 1 이상이어야 합니다.");
        if(!Set.of(20,50,100).contains(size))throw invalid("size","페이지 크기는 20, 50, 100 중에서 선택해 주세요.");
        return new HistoryQuery(type,id,job,kind,page,size);
    }
    private static String value(MultiValueMap<String,String> q,String key,int length) {
        if(!q.containsKey(key))return null;
        String v=q.getFirst(key);v=v==null?"":v.strip();
        if(v.isEmpty()||v.length()>length||!v.matches("[A-Za-z0-9_-]+"))throw invalid(key,"조회 항목의 형식과 길이를 확인해 주세요.");
        return v;
    }
    private static int integer(MultiValueMap<String,String> q,String key,int fallback) {
        if(!q.containsKey(key))return fallback;
        String v=q.getFirst(key);v=v==null?"":v.strip();
        if(!v.matches("[0-9]{1,9}"))throw invalid(key,"올바른 정수를 입력해 주세요.");
        return Integer.parseInt(v);
    }
    private static ApiException invalid(String field,String message){return new ApiException(400,"VALIDATION_ERROR",message,field,null);}
}
