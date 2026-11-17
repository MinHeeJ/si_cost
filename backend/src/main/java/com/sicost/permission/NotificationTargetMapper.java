package com.sicost.permission;

import org.apache.ibatis.annotations.*;

/** Minimal source identifiers used to authorize an existing notification, never response data. */
@Mapper
public interface NotificationTargetMapper {
    record Target(String projectId,String partId,String month,String userId,String screenId) {}
    @Select("SELECT proj_id AS project_id,NULL::varchar AS part_id,bill_ym AS month,NULL::varchar AS user_id,NULL::varchar AS screen_id FROM tb_bl_bill WHERE bill_id=#{id}")
    Target bill(String id);
    @Select("SELECT proj_id AS project_id,NULL::varchar AS part_id,NULL::varchar AS month,NULL::varchar AS user_id,NULL::varchar AS screen_id FROM tb_pj_asgn WHERE asgn_id=#{id}")
    Target assignment(String id);
    @Select("SELECT proj_id AS project_id,part_id,NULL::varchar AS month,NULL::varchar AS user_id,NULL::varchar AS screen_id FROM tb_sl_opp WHERE opp_id=#{id}")
    Target opportunity(String id);
    @Select("SELECT trgt_proj_id AS project_id,NULL::varchar AS part_id,trgt_ym AS month,reg_user_id AS user_id,scrn_id AS screen_id FROM tb_sy_upld WHERE upld_id=CAST(#{id} AS numeric)")
    Target upload(String id);
    @Select("SELECT p.part_id,p.hq_yn='Y' AS headquarters,(SELECT s.part_ldr_user_id FROM tb_bs_part_std s WHERE s.part_id=p.part_id AND s.aply_bgn_ym<=#{month} AND (s.aply_end_ym IS NULL OR s.aply_end_ym>=#{month}) ORDER BY s.aply_bgn_ym DESC LIMIT 1) AS leader_id FROM tb_bs_part p WHERE p.part_id=#{id}")
    com.sicost.common.notifications.NotificationRecipients.Part part(@Param("id") String id,@Param("month") String month);
}
