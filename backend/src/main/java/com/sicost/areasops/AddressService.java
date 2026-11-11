package com.sicost.areasops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.areasops.AddressDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AddressService {
    private final AddressRepository repository;
    public AddressService(AddressRepository repository) { this.repository=repository; }
    public Areas getSidos() {
        repository.actor();return new Areas(repository.mapper().sidos());
    }
    public Areas getChildren(String parentCode, String level) {
        String parent=AddressInput.code(parentCode,"parentCode"),childLevel=AddressInput.level(level);
        repository.actor();repository.required(parent);
        return new Areas(repository.mapper().children(parent,childLevel));
    }
    public AreaPath getPath(String code) {
        String selected=AddressInput.code(code,"code");repository.actor();
        return path(repository.required(selected));
    }
    public Selection resolve(String homeAreaCode, String originalCode, String maxLevel) {
        if (!Set.of("SGG","EMD").contains(maxLevel)) throw InputValidator.invalid("maxLevel");
        repository.actor();
        String code=InputValidator.optionalText(homeAreaCode,10,"homeAreaCode");
        if (code==null) return new Selection(null,null);
        try { code=AddressInput.code(code,"homeAreaCode"); }
        catch (ApiException e) { throw invalid(); }
        Area leaf=repository.mapper().find(code);
        if (leaf==null) throw invalid();
        AreaPath path=path(leaf);
        if (("SGG".equals(maxLevel) && "EMD".equals(leaf.level()))
                || (!Objects.equals(code,originalCode) && path.items().stream().anyMatch(a -> !"Y".equals(a.useYn())))) throw invalid();
        return new Selection(code,path.homeAddress());
    }
    private AreaPath path(Area leaf) {
        LinkedList<Area> items=new LinkedList<>();Set<String> visited=new HashSet<>();
        Area current=leaf;
        while (current!=null) {
            if (!visited.add(current.code()) || items.size()>=3 || current.name()==null || current.name().isBlank()) throw invalid();
            items.addFirst(current);
            if (current.parentCode()==null) break;
            current=repository.mapper().find(current.parentCode());
            if (current==null) throw invalid();
        }
        if (!"SIDO".equals(items.getFirst().level()) || items.getFirst().parentCode()!=null) throw invalid();
        for (int i=1;i<items.size();i++) {
            String before=items.get(i-1).level(),after=items.get(i).level();
            if (!("SIDO".equals(before) && Set.of("SGG","EMD").contains(after)
                    || "SGG".equals(before) && "EMD".equals(after))) throw invalid();
        }
        String display=items.stream().map(Area::name).map(String::strip).collect(Collectors.joining(" "));
        if (display.codePointCount(0,display.length())>50) throw invalid();
        return new AreaPath(List.copyOf(items),display);
    }
    private ApiException invalid() {
        return new ApiException(400,"INVALID_AREA","거주지의 행정구역 경로와 50자 이내 표시명을 확인해 주세요.","homeAreaCode",null);
    }
}
