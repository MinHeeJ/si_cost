package com.sicost.menumanagement;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.menumanagement.MenuManagementDtos.*;

@Repository
public class MenuManagementRepository {
    private final MenuManagementMapper mapper;
    public MenuManagementRepository(MenuManagementMapper mapper) { this.mapper = mapper; }
    public List<Stored> tree() { return mapper.tree().stream().sorted(Comparator.comparing(Stored::menuId)).toList(); }
    public void update(Mutation mutation) {
        if (mapper.update(mutation) != 1) throw new IllegalStateException("메뉴를 저장하지 못했습니다.");
    }
}
