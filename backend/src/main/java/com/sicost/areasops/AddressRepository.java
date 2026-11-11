package com.sicost.areasops;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import org.springframework.stereotype.Repository;
import static com.sicost.areasops.AddressDtos.*;

@Repository
public class AddressRepository {
    private final AddressMapper mapper;
    private final CurrentUserProvider users;
    public AddressRepository(AddressMapper mapper, CurrentUserProvider users) {
        this.mapper=mapper;this.users=users;
    }
    public AddressMapper mapper() { return mapper; }
    public void actor() { users.userId(); }
    public Area required(String code) {
        Area area=mapper.find(code);
        if (area==null) throw new ApiException(404,"NOT_FOUND","행정구역을 찾을 수 없습니다.");
        return area;
    }
}
