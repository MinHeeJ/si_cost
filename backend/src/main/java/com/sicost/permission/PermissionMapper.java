package com.sicost.permission;

import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PermissionMapper {
    record Role(String roleId,String scope,String sensitive) {}
    record Menu(String menuId,String parentId,String screenId,String useYn,int sortOrder,boolean pcOnly,
                boolean inquiry,boolean registration,boolean update,boolean deletion,boolean upload,boolean download,boolean confirmation) {}
    @Select("SELECT r.role_id, r.data_scope_cd AS scope, r.snstv_view_yn AS sensitive FROM tb_sy_user_role ur JOIN tb_sy_role r ON r.role_id=ur.role_id WHERE ur.user_id=#{id} AND r.use_yn='Y'")
    List<Role> roles(String id);
    @Select("SELECT m.menu_id, m.upper_menu_id AS parent_id,m.scrn_id AS screen_id,m.use_yn,m.sort_ord AS sort_order,m.pc_only_yn='Y' AS pc_only, COALESCE(bool_or(a.inq_yn='Y'),false) AS inquiry,COALESCE(bool_or(a.reg_yn='Y'),false) AS registration,COALESCE(bool_or(a.upd_yn='Y'),false) AS update,COALESCE(bool_or(a.del_yn='Y'),false) AS deletion,COALESCE(bool_or(a.upld_yn='Y'),false) AS upload,COALESCE(bool_or(a.dnld_yn='Y'),false) AS download,COALESCE(bool_or(a.cnfm_yn='Y'),false) AS confirmation FROM tb_sy_menu m LEFT JOIN tb_sy_role_menu_auth a ON a.menu_id=m.menu_id AND a.role_id IN (SELECT r.role_id FROM tb_sy_user_role ur JOIN tb_sy_role r ON r.role_id=ur.role_id WHERE ur.user_id=#{id} AND r.use_yn='Y') GROUP BY m.menu_id ORDER BY m.sort_ord,m.menu_id")
    List<Menu> menus(String id);
    @Select("SELECT DISTINCT part_id FROM tb_sy_user_scope WHERE user_id=#{id} AND scope_type_cd='PART' AND part_id IS NOT NULL UNION SELECT s.part_id FROM tb_bs_part_std s JOIN tb_bs_part p ON p.part_id=s.part_id WHERE s.part_ldr_user_id=#{id} AND p.hq_yn='N' AND s.aply_bgn_ym<=#{month} AND (s.aply_end_ym IS NULL OR s.aply_end_ym>=#{month})")
    List<String> parts(@Param("id") String id,@Param("month") String month);
    @Select("SELECT DISTINCT proj_id FROM tb_sy_user_scope WHERE user_id=#{id} AND scope_type_cd='PROJ' AND proj_id IS NOT NULL") List<String> projects(String id);
    @Select("SELECT part_id FROM tb_pj_proj_part WHERE proj_id=#{id} AND aply_bgn_ym<=#{month} ORDER BY aply_bgn_ym DESC LIMIT 1")
    String projectPart(@Param("id") String id,@Param("month") String month);
    @Select("SELECT DISTINCT proj_id FROM tb_pj_asgn WHERE dev_id=#{id}")
    List<String> developerProjects(String id);
    @Select("SELECT EXISTS (SELECT 1 FROM tb_sy_user WHERE user_id=#{id} AND user_stat_cd='USE')")
    boolean active(String id);
    @Select("SELECT user_id FROM tb_sy_ai_qna WHERE qna_id=CAST(#{id} AS NUMERIC)")
    String questionOwner(String id);
    record BillScope(String projectId,String month) {}
    @Select("SELECT proj_id AS project_id,bill_ym AS month FROM tb_bl_bill WHERE bill_id=#{id}")
    BillScope bill(String id);
    @Select("SELECT clnt_type_cd FROM tb_bs_clnt WHERE clnt_id=#{id}")
    String clientType(String id);
    record Upload(String formType,String ownerId,String month,String projectId) {}
    @Select("SELECT form_type_cd AS form_type,reg_user_id AS owner_id,trgt_ym AS month,trgt_proj_id AS project_id FROM tb_sy_upld WHERE upld_id=CAST(#{id} AS NUMERIC)")
    Upload upload(String id);
    record PersonOption(String id, String name, String managementNo, String jobCode,
                        String gradeCode, String status, LocalDate withdrawalDate) {}
    @ConstructorArgs({
        @Arg(column="id", javaType=String.class),
        @Arg(column="name", javaType=String.class),
        @Arg(column="managementNo", javaType=String.class),
        @Arg(column="jobCode", javaType=String.class),
        @Arg(column="gradeCode", javaType=String.class),
        @Arg(column="status", javaType=String.class),
        @Arg(column="withdrawalDate", javaType=LocalDate.class, jdbcType=JdbcType.DATE)
    })
    @Select("""
        SELECT d.dev_id AS "id", d.dev_nm AS "name", d.mng_no AS "managementNo",
               d.job_cd AS "jobCode", d.grade_cd AS "gradeCode", d.work_stat_cd AS "status",
               (SELECT max(a.input_end_dt) FROM tb_pj_asgn a
                WHERE a.dev_id=d.dev_id AND a.asgn_stat_cd='ACT'
                  AND a.input_bgn_dt <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
                  AND a.input_end_dt >= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date) AS "withdrawalDate"
        FROM tb_hr_dev d
        WHERE d.use_yn='Y' AND d.drct_cls_cd='DRCT' AND d.work_stat_cd<>'RETIRE'
          AND (d.dev_nm ILIKE '%' || #{q} || '%' OR d.mng_no ILIKE '%' || #{q} || '%')
        ORDER BY d.dev_nm, d.dev_id LIMIT 50
        """)
    List<PersonOption> assignmentPeople(String q);
    @Select("SELECT count(*) FROM tb_hr_dev WHERE use_yn='Y' AND drct_cls_cd='DRCT' AND work_stat_cd<>'RETIRE' AND (dev_nm ILIKE '%' || #{q} || '%' OR mng_no ILIKE '%' || #{q} || '%')")
    long assignmentPeopleCount(String q);
}
