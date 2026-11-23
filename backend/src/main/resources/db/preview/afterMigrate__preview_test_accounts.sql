-- Invoked only for SyncOps branch preview databases after the normal migrations.
-- Preserve any existing accounts and their role assignments.
WITH preview_accounts (user_id, user_nm, role_id, pwd_hash, mfa_use_yn) AS (
    VALUES
        ('qaadmin', '[테스트] 시스템 관리자', 'ADMIN', '$2a$10$M4nScF7i7LVRVS.WFhqR9.Im8yQSrKcO5Zwqw2DCeT8PWKaYjVexm', 'Y'),
        ('exec1', '[테스트] 경영진', 'EXEC', '$2a$10$pdrBQgMlATgNZ9gi6wurA.C2B418UmZyCjrqXF/1iTUWARzrhYrLe', 'N'),
        ('fin1', '[테스트] 재무 담당', 'FIN', '$2a$10$CGdpQK7dFD.1Iby733Vl4etBZnBhRbRonCVvmxziovyVXnfjmR1xa', 'N'),
        ('sales1', '[테스트] 영업 파트장', 'SALES_LDR', '$2a$10$RZJWXAxl2MOIIANJJtpL4.ZL2R0589MwQpUNqoq4.oXK4/Trpa2Hm', 'N'),
        ('pm1', '[테스트] PM', 'PM', '$2a$10$B5AdMKcNhBH/NICGN98NCO/wdFFFdC3rIrAOKAdAH/JNJDC1KVYdu', 'N')
), inserted_accounts AS (
    INSERT INTO tb_sy_user (
        user_id, user_nm, email_addr, pwd_hash, pwd_chg_dttm, temp_pwd_yn, pwd_err_cnt,
        user_stat_cd, mfa_use_yn, mfa_err_cnt, reg_user_id
    )
    SELECT a.user_id, a.user_nm, a.user_id || '@example.invalid', a.pwd_hash,
           CURRENT_TIMESTAMP, 'N', 0, 'USE', a.mfa_use_yn, 0, 'PREVIEW_SEED'
    FROM preview_accounts a
    JOIN tb_sy_role r ON r.role_id = a.role_id AND r.use_yn = 'Y'
    ON CONFLICT (user_id) DO NOTHING
    RETURNING user_id
)
INSERT INTO tb_sy_user_role (user_id, role_id, reg_user_id)
SELECT a.user_id, a.role_id, 'PREVIEW_SEED'
FROM preview_accounts a
JOIN inserted_accounts i ON i.user_id = a.user_id;
