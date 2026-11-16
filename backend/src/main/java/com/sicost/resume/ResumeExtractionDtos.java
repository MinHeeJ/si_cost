package com.sicost.resume;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

public final class ResumeExtractionDtos {
    private ResumeExtractionDtos() {}
    public record Code(String groupId,String code,String name,String addVal1,int sortOrder) {}
    public record Area(String code,String level,String parentCode,String sido,String fullSido,String sgg,String emd) {}
    public record Vendor(String id,String name) {}
    public record Dictionary(List<Code> codes,List<Area> areas,List<Vendor> vendors) {}
    public record Field(String field,boolean valid,String confidence,String notice,String fragment,List<String> matchedWords,List<Object> candidates) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Extraction(String targetType,Map<String,Object> values,List<Field> fields,List<String> warnings,String careerReference,String notice) {}
}
