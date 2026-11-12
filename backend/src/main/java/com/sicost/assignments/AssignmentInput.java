package com.sicost.assignments;

import com.sicost.assignmentsops.AssignmentActionsDtos.Filter;
import com.sicost.assignmentsops.AssignmentActionsInput;
import java.util.Map;
import java.util.Set;
import static com.sicost.common.validation.ProjectInputPolicy.*;

public final class AssignmentInput {
    private AssignmentInput() {}
    public record Query(Filter filters,int page,int size,String sort,String direction) {}
    public static int year(Map<String,String> query) {
        if(!query.containsKey("year"))throw invalid("year");
        return AssignmentActionsInput.integer(query.get("year"),"year",0);
    }
    public static Query list(Map<String,String> q) {
        AssignmentActionsInput.query(q,Set.of("year","projectId","jobCode","warning","q","includeCancelled","page","size","sort","direction"));
        String warning=AssignmentActionsInput.warning(q.get("warning"));
        String cancelled=q.getOrDefault("includeCancelled","false");
        if(!Set.of("true","false").contains(cancelled))throw invalid("includeCancelled");
        String sort=q.get("sort");
        if(sort!=null&&!Set.of("projectName","developerName","jobName","startDate","endDate","billingUnitPrice","contractMm","yearMm","extensionPossibility","inputType").contains(sort))throw invalid("sort");
        String direction=q.getOrDefault("direction","ASC");if(!Set.of("ASC","DESC").contains(direction))throw invalid("direction");
        Filter filter=new Filter(year(q),optional(q,"projectId",20),optional(q,"jobCode",10),warning,optionalText(q.get("q"),100,"q"),Boolean.parseBoolean(cancelled));
        return new Query(filter,AssignmentActionsInput.page(q),AssignmentActionsInput.size(q),sort,direction);
    }
    private static String optional(Map<String,String> q,String field,int max) {return q.containsKey(field)?text(q.get(field),max,field):null;}
}
