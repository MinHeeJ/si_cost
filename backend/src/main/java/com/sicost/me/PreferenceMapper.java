package com.sicost.me;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.me.PreferenceDtos.*;

@Mapper
public interface PreferenceMapper {
    List<StoredPreference> list(@Param("userId") String userId);
    StoredPreference lock(PreferenceId id);
    int insert(PreferenceWrite write);
    int update(PreferenceWrite write);
    int delete(PreferenceId id);
    List<String> screens();
    String cashBaseMonth();
}
