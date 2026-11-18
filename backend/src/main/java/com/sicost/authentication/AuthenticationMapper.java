package com.sicost.authentication;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthenticationMapper {
    record Account(String userId, String email, String passwordHash, String temporary, int errors,
                   String status, LocalDateTime changedAt, String codeHash, LocalDateTime codeExpiresAt, int codeErrors) {
        @Override public String toString() { return "Account[redacted]"; }
    }
    String COLUMNS = "user_id, email_addr AS email, pwd_hash AS password_hash, temp_pwd_yn AS temporary, pwd_err_cnt AS errors, user_stat_cd AS status, pwd_chg_dttm AS changed_at, mfa_code_hash AS code_hash, mfa_expr_dttm AS code_expires_at, mfa_err_cnt AS code_errors";
    @Select("SELECT " + COLUMNS + " FROM tb_sy_user WHERE user_id=#{id}") Account find(String id);
    @Select("SELECT " + COLUMNS + " FROM tb_sy_user WHERE user_id=#{id} FOR UPDATE") Account lock(String id);
    @Select("SELECT EXISTS(SELECT 1 FROM tb_sy_user_role ur JOIN tb_sy_role r ON r.role_id=ur.role_id WHERE ur.user_id=#{id} AND r.role_id='ADMIN' AND r.use_yn='Y')") boolean administrator(String id);
    @Select("SELECT std_val FROM tb_sy_oper_std WHERE std_cd='SESSION_MIN' AND aply_bgn_ym<=#{month} ORDER BY aply_bgn_ym DESC LIMIT 1") BigDecimal sessionMinutes(String month);
    @Update("UPDATE tb_sy_user SET pwd_err_cnt=#{errors}, user_stat_cd=#{status}, mod_user_id=#{actor}, mod_dttm=#{now} WHERE user_id=#{id}")
    int failure(@Param("id") String id,@Param("errors") int errors,@Param("status") String status,@Param("actor") String actor,@Param("now") LocalDateTime now);
    @Update("UPDATE tb_sy_user SET pwd_err_cnt=#{errors} WHERE user_id=#{id}")
    int errors(@Param("id") String id,@Param("errors") int errors);
    @Update("UPDATE tb_sy_user SET pwd_err_cnt=0, mfa_use_yn=#{mfa} WHERE user_id=#{id}")
    int passwordVerified(@Param("id") String id,@Param("mfa") String mfa);
    @Update("UPDATE tb_sy_user SET last_login_dttm=#{now} WHERE user_id=#{id}")
    int login(@Param("id") String id,@Param("now") LocalDateTime now);
    @Update("UPDATE tb_sy_user SET mfa_code_hash=#{hash}, mfa_expr_dttm=#{expires}, mfa_err_cnt=#{errors} WHERE user_id=#{id}")
    int code(@Param("id") String id,@Param("hash") String hash,@Param("expires") LocalDateTime expires,@Param("errors") int errors);
    @Update("UPDATE tb_sy_user SET mfa_code_hash=NULL, mfa_expr_dttm=NULL, mfa_err_cnt=0 WHERE user_id=#{id} AND mfa_code_hash=#{hash}")
    int discard(@Param("id") String id,@Param("hash") String hash);
    @Update("UPDATE tb_sy_user SET pwd_hash=#{hash}, pwd_chg_dttm=#{now}, temp_pwd_yn=#{temporary}, pwd_err_cnt=0, mfa_code_hash=NULL, mfa_expr_dttm=NULL, mfa_err_cnt=0, mod_user_id=#{actor}, mod_dttm=#{now} WHERE user_id=#{id}")
    int password(@Param("id") String id,@Param("hash") String hash,@Param("temporary") String temporary,@Param("actor") String actor,@Param("now") LocalDateTime now);
    @Update("UPDATE tb_sy_user SET email_addr=#{email}, mfa_use_yn='Y', mod_user_id='SYSTEM', mod_dttm=#{now} WHERE user_id=#{id}")
    int operatorEmail(@Param("id") String id,@Param("email") String email,@Param("now") LocalDateTime now);
    @Update("UPDATE tb_sy_user SET mfa_use_yn=CASE WHEN EXISTS(SELECT 1 FROM tb_sy_user_role ur JOIN tb_sy_role r ON r.role_id=ur.role_id WHERE ur.user_id=tb_sy_user.user_id AND r.role_id='ADMIN' AND r.use_yn='Y') THEN 'Y' ELSE 'N' END WHERE user_id=#{id}")
    int synchronizeMfa(String id);
}
