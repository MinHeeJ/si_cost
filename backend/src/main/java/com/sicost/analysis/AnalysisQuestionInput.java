package com.sicost.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import java.util.Set;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

/** Validate JSON types before binding: a numeric identifier/question is never coerced into text. */
public final class AnalysisQuestionInput {
    private AnalysisQuestionInput() {}
    public static Question parse(JsonNode body) {
        object(body,Set.of("question","context"),"");
        String question=text(body,"question",true,"");
        JsonNode context=body.get("context");
        if(context==null||context.isNull())return new Question(question,null);
        object(context,Set.of("screenId","baseMonth","projectId","developerId"),"context.");
        return new Question(question,new Context(text(context,"screenId",false,"context."),text(context,"baseMonth",false,"context."),text(context,"projectId",false,"context."),text(context,"developerId",false,"context.")));
    }
    private static void object(JsonNode node,Set<String> allowed,String prefix) {
        if(node==null||!node.isObject())throw invalid(prefix.isEmpty()?"question":"context");
        node.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",prefix+key,null);});
    }
    private static String text(JsonNode node,String key,boolean required,String prefix) {
        JsonNode value=node.get(key);
        if(value==null||value.isNull()){if(required)throw invalid(prefix+key);return null;}
        if(!value.isTextual())throw invalid(prefix+key);
        return value.textValue();
    }
    private static ApiException invalid(String field){return new ApiException(400,"INVALID_INPUT","질문과 문맥은 지정된 문자 형식으로 입력해 주세요.",field,null);}
}
