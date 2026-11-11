package com.sicost.codegroups;

import com.sicost.common.api.ApiException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CodeReferenceGuard {
    private final CodeGroupMapper mapper;
    public CodeReferenceGuard(CodeGroupMapper mapper) { this.mapper=mapper; }
    public void requireUnused(String group,String code) {
        var references=mapper.references(group,code);
        if(!references.isEmpty()) throw new ApiException(409,"CODE_IN_USE","사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.",null,Map.of("references",references));
    }
}
