package com.sicost.apiops;
import java.time.LocalDateTime;
import java.util.List;
public final class TentativeSelectionDtos {
    private TentativeSelectionDtos(){}
    public record Need(String opportunityId,int needSn,String startMonth,int months,int count,String status){}
    public record Active(String id,String opportunityId,int needSn,String developerId,String startMonth,String status){}
    public record Write(String id,String opportunityId,int needSn,String developerId,String startMonth,String sourceScreenId,String actor,LocalDateTime now){}
    public record Result(boolean changed,String revision,List<String> warnings,String opportunityId,String tentativeId){}
}
