package com.sicost.areasops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.areasops.AddressDtos.*;

@Mapper
public interface AddressMapper {
    List<Area> sidos();
    List<Area> children(@Param("parentCode") String parentCode, @Param("level") String level);
    Area find(@Param("code") String code);
}
