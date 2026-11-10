-- =====================================================================
-- SI 프로젝트 및 원가 관리 시스템  테이블 DDL (PostgreSQL 14+)
-- 생성: tools/gen_ddl_xlsx.py  ←  06_테이블설계.md   (2026-11-03)
-- 테이블 67개 / 컬럼 825개 / FK 73개
-- FK 기준: 06 설계에서 FK로 표시한 참조 전부(01 문서 2.9)
-- 실행 순서: 1 시퀀스 → 2 테이블(PK·UK·CHECK) → 3 FK → 4 인덱스 → 5 코멘트 → 6 기준 데이터
-- =====================================================================

-- 1. 시퀀스
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_CODE_MAP START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_NOTI START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_CHG_HIST START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_ACCS_LOG START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_UPLD START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_AI_QNA START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_PJ_ISSUE START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SL_TENT_ASGN START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SL_RCMD_LOG START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_BL_RCPT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_CO_PREV_ADJ START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_FI_LOAN START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS SQ_TB_FI_XFER START WITH 1 INCREMENT BY 1;

-- 2. 테이블

-- TB_SY_USER 사용자
CREATE TABLE TB_SY_USER (
    USER_ID                VARCHAR(30)    NOT NULL,
    USER_NM                VARCHAR(50)    NOT NULL,
    EMAIL_ADDR             VARCHAR(100)   NOT NULL,
    PWD_HASH               VARCHAR(200)   NOT NULL,
    PWD_CHG_DTTM           TIMESTAMP,
    TEMP_PWD_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
    PWD_ERR_CNT            NUMERIC(2)     NOT NULL,
    USER_STAT_CD           VARCHAR(10)    NOT NULL,
    LAST_LOGIN_DTTM        TIMESTAMP,
    MFA_USE_YN             CHAR(1)        DEFAULT 'N' NOT NULL,
    MFA_CODE_HASH          VARCHAR(200),
    MFA_EXPR_DTTM          TIMESTAMP,
    MFA_ERR_CNT            NUMERIC(2)     NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_USER PRIMARY KEY (USER_ID),
    CONSTRAINT CK_TB_SY_USER_01 CHECK (TEMP_PWD_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_USER_02 CHECK (MFA_USE_YN IN ('Y', 'N'))
);

-- TB_SY_ROLE 역할
CREATE TABLE TB_SY_ROLE (
    ROLE_ID                VARCHAR(20)    NOT NULL,
    ROLE_NM                VARCHAR(50)    NOT NULL,
    DATA_SCOPE_CD          VARCHAR(10)    NOT NULL,
    SNSTV_VIEW_YN          CHAR(1)        DEFAULT 'N' NOT NULL,
    SYS_ROLE_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
    SORT_ORD               NUMERIC(5)     NOT NULL,
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_ROLE PRIMARY KEY (ROLE_ID),
    CONSTRAINT UK_TB_SY_ROLE_01 UNIQUE (ROLE_NM),
    CONSTRAINT CK_TB_SY_ROLE_01 CHECK (SNSTV_VIEW_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_02 CHECK (SYS_ROLE_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_03 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_SY_USER_ROLE 사용자역할
CREATE TABLE TB_SY_USER_ROLE (
    USER_ID                VARCHAR(30)    NOT NULL,
    ROLE_ID                VARCHAR(20)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_USER_ROLE PRIMARY KEY (USER_ID, ROLE_ID)
);

-- TB_SY_USER_SCOPE 사용자담당범위
CREATE TABLE TB_SY_USER_SCOPE (
    USER_ID                VARCHAR(30)    NOT NULL,
    SCOPE_SN               NUMERIC(5)     NOT NULL,
    SCOPE_TYPE_CD          VARCHAR(10)    NOT NULL,
    PART_ID                VARCHAR(20),
    PROJ_ID                VARCHAR(20),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_USER_SCOPE PRIMARY KEY (USER_ID, SCOPE_SN)
);

-- TB_SY_MENU 메뉴
CREATE TABLE TB_SY_MENU (
    MENU_ID                VARCHAR(20)    NOT NULL,
    UPPER_MENU_ID          VARCHAR(20),
    MENU_NM                VARCHAR(50)    NOT NULL,
    SCRN_ID                VARCHAR(10),
    MENU_LVL               NUMERIC(1)     NOT NULL,
    SORT_ORD               NUMERIC(5)     NOT NULL,
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    PC_ONLY_YN             CHAR(1)        DEFAULT 'N' NOT NULL,
    ICON_NM                VARCHAR(30),
    LOCK_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_MENU PRIMARY KEY (MENU_ID),
    CONSTRAINT CK_TB_SY_MENU_01 CHECK (USE_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_MENU_02 CHECK (PC_ONLY_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_MENU_03 CHECK (LOCK_YN IN ('Y', 'N'))
);

-- TB_SY_ROLE_MENU_AUTH 역할메뉴권한
CREATE TABLE TB_SY_ROLE_MENU_AUTH (
    ROLE_ID                VARCHAR(20)    NOT NULL,
    MENU_ID                VARCHAR(20)    NOT NULL,
    INQ_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    UPD_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    DEL_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    UPLD_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    DNLD_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    CNFM_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_ROLE_MENU_AUTH PRIMARY KEY (ROLE_ID, MENU_ID),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_01 CHECK (INQ_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_02 CHECK (REG_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_03 CHECK (UPD_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_04 CHECK (DEL_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_05 CHECK (UPLD_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_06 CHECK (DNLD_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_07 CHECK (CNFM_YN IN ('Y', 'N'))
);

-- TB_SY_CODE_GRP 공통코드그룹
CREATE TABLE TB_SY_CODE_GRP (
    CODE_GRP_ID            VARCHAR(30)    NOT NULL,
    CODE_GRP_NM            VARCHAR(50)    NOT NULL,
    SYS_CODE_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
    VAL_CODE_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
    SORT_ORD               NUMERIC(5)     NOT NULL,
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_CODE_GRP PRIMARY KEY (CODE_GRP_ID),
    CONSTRAINT CK_TB_SY_CODE_GRP_01 CHECK (SYS_CODE_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_CODE_GRP_02 CHECK (VAL_CODE_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_CODE_GRP_03 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_SY_CODE 공통코드
CREATE TABLE TB_SY_CODE (
    CODE_GRP_ID            VARCHAR(30)    NOT NULL,
    CODE                   VARCHAR(20)    NOT NULL,
    CODE_NM                VARCHAR(100)   NOT NULL,
    SORT_ORD               NUMERIC(5)     NOT NULL,
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    DEL_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    CODE_VAL               NUMERIC(15,4),
    UNIT_NM                VARCHAR(10),
    ADD_VAL1               VARCHAR(100),
    ADD_VAL2               VARCHAR(100),
    ADD_VAL3               VARCHAR(100),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_CODE PRIMARY KEY (CODE_GRP_ID, CODE),
    CONSTRAINT CK_TB_SY_CODE_01 CHECK (USE_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_SY_CODE_02 CHECK (DEL_YN IN ('Y', 'N'))
);

-- TB_SY_CODE_MAP 엑셀표기대응
CREATE TABLE TB_SY_CODE_MAP (
    MAP_ID                 NUMERIC(10)    DEFAULT nextval('SQ_TB_SY_CODE_MAP') NOT NULL,
    SRC_TEXT               VARCHAR(100)   NOT NULL,
    CODE_GRP_ID            VARCHAR(30)    NOT NULL,
    CODE                   VARCHAR(20)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_CODE_MAP PRIMARY KEY (MAP_ID),
    CONSTRAINT UK_TB_SY_CODE_MAP_01 UNIQUE (SRC_TEXT, CODE_GRP_ID)
);

-- TB_SY_OPER_STD 운영기준값
CREATE TABLE TB_SY_OPER_STD (
    STD_CD                 VARCHAR(30)    NOT NULL,
    APLY_BGN_YM            CHAR(6)        NOT NULL,
    STD_NM                 VARCHAR(100)   NOT NULL,
    STD_VAL                NUMERIC(15,4)  NOT NULL,
    UNIT_NM                VARCHAR(10)    NOT NULL,
    STD_DESC               VARCHAR(300),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_OPER_STD PRIMARY KEY (STD_CD, APLY_BGN_YM)
);

-- TB_SY_USER_PREF 사용자화면설정
CREATE TABLE TB_SY_USER_PREF (
    USER_ID                VARCHAR(30)    NOT NULL,
    PREF_TYPE_CD           VARCHAR(20)    NOT NULL,
    PREF_KEY               VARCHAR(30)    NOT NULL,
    PREF_VAL               VARCHAR(2000)  NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_USER_PREF PRIMARY KEY (USER_ID, PREF_TYPE_CD, PREF_KEY)
);

-- TB_SY_NOTI 알림
CREATE TABLE TB_SY_NOTI (
    NOTI_ID                NUMERIC(12)    DEFAULT nextval('SQ_TB_SY_NOTI') NOT NULL,
    RCVR_USER_ID           VARCHAR(30)    NOT NULL,
    NOTI_TYPE_CD           VARCHAR(20)    NOT NULL,
    NOTI_CN                VARCHAR(300)   NOT NULL,
    LINK_SCRN_ID           VARCHAR(10),
    LINK_KEY               VARCHAR(50),
    SEND_MTHD_CD           VARCHAR(10)    NOT NULL,
    READ_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    READ_DTTM              TIMESTAMP,
    NOTI_DTTM              TIMESTAMP      NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_NOTI PRIMARY KEY (NOTI_ID),
    CONSTRAINT CK_TB_SY_NOTI_01 CHECK (READ_YN IN ('Y', 'N'))
);

-- TB_SY_CHG_HIST 변경이력
CREATE TABLE TB_SY_CHG_HIST (
    HIST_ID                NUMERIC(12)    DEFAULT nextval('SQ_TB_SY_CHG_HIST') NOT NULL,
    TRGT_TBL_NM            VARCHAR(30)    NOT NULL,
    TRGT_KEY               VARCHAR(100)   NOT NULL,
    CHG_TYPE_CD            VARCHAR(10)    NOT NULL,
    CHG_ITEM_NM            VARCHAR(50),
    BFR_VAL                VARCHAR(500),
    AFT_VAL                VARCHAR(500),
    CHG_RSN                VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_SY_CHG_HIST PRIMARY KEY (HIST_ID)
);

-- TB_SY_ACCS_LOG 접근기록
CREATE TABLE TB_SY_ACCS_LOG (
    LOG_ID                 NUMERIC(15)    DEFAULT nextval('SQ_TB_SY_ACCS_LOG') NOT NULL,
    USER_ID                VARCHAR(30)    NOT NULL,
    ACT_TYPE_CD            VARCHAR(20)    NOT NULL,
    SCRN_ID                VARCHAR(10),
    TRGT_DESC              VARCHAR(300),
    CLIENT_IP              VARCHAR(45),
    ACT_DTTM               TIMESTAMP      NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_SY_ACCS_LOG PRIMARY KEY (LOG_ID)
);

-- TB_SY_UPLD 엑셀업로드
CREATE TABLE TB_SY_UPLD (
    UPLD_ID                NUMERIC(12)    DEFAULT nextval('SQ_TB_SY_UPLD') NOT NULL,
    FORM_TYPE_CD           VARCHAR(10)    NOT NULL,
    SCRN_ID                VARCHAR(10)    NOT NULL,
    FILE_NM                VARCHAR(200)   NOT NULL,
    FILE_SIZE              NUMERIC(12)    NOT NULL,
    TRGT_YM                CHAR(6),
    TRGT_PROJ_ID           VARCHAR(20),
    TRGT_DSTRB_STEP_CD     VARCHAR(10),
    TOT_ROW_CNT            NUMERIC(7)     NOT NULL,
    SUCC_CNT               NUMERIC(7)     NOT NULL,
    WARN_CNT               NUMERIC(7)     NOT NULL,
    ERR_CNT                NUMERIC(7)     NOT NULL,
    UPLD_STAT_CD           VARCHAR(10)    NOT NULL,
    APLY_DTTM              TIMESTAMP,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_UPLD PRIMARY KEY (UPLD_ID)
);

-- TB_SY_UPLD_ERR 엑셀업로드검사결과
CREATE TABLE TB_SY_UPLD_ERR (
    UPLD_ID                NUMERIC(12)    NOT NULL,
    RSLT_SN                NUMERIC(7)     NOT NULL,
    RSLT_TYPE_CD           VARCHAR(10)    NOT NULL,
    ROW_NO                 NUMERIC(7)     NOT NULL,
    COL_NM                 VARCHAR(50),
    INPUT_VAL              VARCHAR(200),
    RSN_CN                 VARCHAR(300)   NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_UPLD_ERR PRIMARY KEY (UPLD_ID, RSLT_SN)
);

-- TB_SY_AI_QNA AI질문기록
CREATE TABLE TB_SY_AI_QNA (
    QNA_ID                 NUMERIC(12)    DEFAULT nextval('SQ_TB_SY_AI_QNA') NOT NULL,
    USER_ID                VARCHAR(30)    NOT NULL,
    QSTN_CN                VARCHAR(1000)  NOT NULL,
    ANS_CN                 TEXT,
    USE_DATA_DESC          VARCHAR(500),
    BASE_YM                CHAR(6),
    PRVS_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    ANS_STAT_CD            VARCHAR(10)    NOT NULL,
    QSTN_DTTM              TIMESTAMP      NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_AI_QNA PRIMARY KEY (QNA_ID),
    CONSTRAINT CK_TB_SY_AI_QNA_01 CHECK (PRVS_YN IN ('Y', 'N'))
);

-- TB_SY_ADM_AREA 행정구역
CREATE TABLE TB_SY_ADM_AREA (
    AREA_CD                VARCHAR(10)    NOT NULL,
    AREA_LVL_CD            VARCHAR(10)    NOT NULL,
    UPPER_AREA_CD          VARCHAR(10),
    SIDO_NM                VARCHAR(20)    NOT NULL,
    SIDO_FULL_NM           VARCHAR(30)    NOT NULL,
    SGG_NM                 VARCHAR(30),
    EMD_NM                 VARCHAR(30),
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_ADM_AREA PRIMARY KEY (AREA_CD),
    CONSTRAINT CK_TB_SY_ADM_AREA_01 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_SY_ADM_HDONG 행정동변환
CREATE TABLE TB_SY_ADM_HDONG (
    HDONG_CD               VARCHAR(10)    NOT NULL,
    AREA_CD                VARCHAR(10)    NOT NULL,
    HDONG_NM               VARCHAR(30)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_ADM_HDONG PRIMARY KEY (HDONG_CD, AREA_CD)
);

-- TB_BS_CLNT 거래처
CREATE TABLE TB_BS_CLNT (
    CLNT_ID                VARCHAR(20)    NOT NULL,
    CLNT_TYPE_CD           VARCHAR(10)    NOT NULL,
    CLNT_NM                VARCHAR(50)    NOT NULL,
    BIZ_REG_NO             VARCHAR(12)    NOT NULL,
    CEO_NM                 VARCHAR(20),
    INDUTY_CD              VARCHAR(10),
    CHRG_NM                VARCHAR(20),
    TELNO                  VARCHAR(20),
    TAX_EMAIL_ADDR         VARCHAR(100),
    BANK_CD                VARCHAR(10),
    ACCT_NO_ENC            VARCHAR(200),
    DPSTR_NM               VARCHAR(30),
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_BS_CLNT PRIMARY KEY (CLNT_ID),
    CONSTRAINT UK_TB_BS_CLNT_01 UNIQUE (BIZ_REG_NO),
    CONSTRAINT CK_TB_BS_CLNT_01 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_BS_PART 영업파트
CREATE TABLE TB_BS_PART (
    PART_ID                VARCHAR(20)    NOT NULL,
    PART_NM                VARCHAR(30)    NOT NULL,
    HQ_YN                  CHAR(1)        DEFAULT 'N' NOT NULL,
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_BS_PART PRIMARY KEY (PART_ID),
    CONSTRAINT UK_TB_BS_PART_01 UNIQUE (PART_NM),
    CONSTRAINT CK_TB_BS_PART_01 CHECK (HQ_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_BS_PART_02 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_BS_PART_STD 영업파트기준이력
CREATE TABLE TB_BS_PART_STD (
    PART_ID                VARCHAR(20)    NOT NULL,
    APLY_BGN_YM            CHAR(6)        NOT NULL,
    APLY_END_YM            CHAR(6),
    PART_LDR_USER_ID       VARCHAR(30)    NOT NULL,
    PRFT_DSTRB_RT          NUMERIC(5,2)   NOT NULL,
    SALES_EXPS_AMT         NUMERIC(15)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_BS_PART_STD PRIMARY KEY (PART_ID, APLY_BGN_YM)
);

-- TB_HR_DEV 개발자
CREATE TABLE TB_HR_DEV (
    DEV_ID                 VARCHAR(20)    NOT NULL,
    MNG_NO                 VARCHAR(20)    NOT NULL,
    DEV_NM                 VARCHAR(50)    NOT NULL,
    EMP_TYPE_CD            VARCHAR(10)    NOT NULL,
    BLNG_CLNT_ID           VARCHAR(20),
    GRADE_CD               VARCHAR(10),
    JOB_CD                 VARCHAR(10)    NOT NULL,
    SUB_JOB_CD             VARCHAR(10),
    JBPS_CD                VARCHAR(10),
    DRCT_CLS_CD            VARCHAR(10)    NOT NULL,
    PART_ID                VARCHAR(20)    NOT NULL,
    JNCMP_DT               DATE,
    RETIRE_DT              DATE,
    WORK_STAT_CD           VARCHAR(10)    NOT NULL,
    HOME_AREA_CD           VARCHAR(10),
    HOME_ADDR              VARCHAR(50),
    TELNO                  VARCHAR(20),
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_DEV PRIMARY KEY (DEV_ID),
    CONSTRAINT UK_TB_HR_DEV_01 UNIQUE (MNG_NO),
    CONSTRAINT CK_TB_HR_DEV_01 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_HR_DEV_SKILL 개발자보유스킬
CREATE TABLE TB_HR_DEV_SKILL (
    DEV_ID                 VARCHAR(20)    NOT NULL,
    SKILL_NM               VARCHAR(50)    NOT NULL,
    SORT_ORD               NUMERIC(3)     NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_DEV_SKILL PRIMARY KEY (DEV_ID, SKILL_NM)
);

-- TB_HR_DEV_PRC 개발자단가급여이력
CREATE TABLE TB_HR_DEV_PRC (
    DEV_ID                 VARCHAR(20)    NOT NULL,
    APLY_BGN_YM            CHAR(6)        NOT NULL,
    APLY_END_YM            CHAR(6),
    MON_UPRC               NUMERIC(15),
    MON_PAY_AMT            NUMERIC(15),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_DEV_PRC PRIMARY KEY (DEV_ID, APLY_BGN_YM)
);

-- TB_HR_DEV_STAT 개발자근무상태이력
CREATE TABLE TB_HR_DEV_STAT (
    DEV_ID                 VARCHAR(20)    NOT NULL,
    STAT_SN                NUMERIC(5)     NOT NULL,
    WORK_STAT_CD           VARCHAR(10)    NOT NULL,
    BGN_DT                 DATE           NOT NULL,
    END_DT                 DATE,
    STAT_RSN               VARCHAR(300),
    CLOSE_LEAVE_SN         NUMERIC(5),
    ORGNL_LEAVE_END_DT     DATE,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_DEV_STAT PRIMARY KEY (DEV_ID, STAT_SN)
);

-- TB_HR_MON_PAY 월별급여원가
CREATE TABLE TB_HR_MON_PAY (
    PAY_YM                 CHAR(6)        NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    PAY_AMT                NUMERIC(15)    NOT NULL,
    ALWNC_AMT              NUMERIC(15)    NOT NULL,
    INSR_CMPNY_AMT         NUMERIC(15)    NOT NULL,
    INSR_SELF_AMT          NUMERIC(15),
    RETIRE_RSV_AMT         NUMERIC(15)    NOT NULL,
    INCM_TAX_AMT           NUMERIC(15),
    WTHLD_TAX_AMT          NUMERIC(15),
    REAL_PAY_AMT           NUMERIC(15),
    VNDR_SPLY_AMT          NUMERIC(15),
    EMP_TYPE_CD            VARCHAR(10)    NOT NULL,
    DRCT_CLS_CD            VARCHAR(10)    NOT NULL,
    UPLD_ID                NUMERIC(12),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_MON_PAY PRIMARY KEY (PAY_YM, DEV_ID)
);

-- TB_HR_CAND 외부인력후보
CREATE TABLE TB_HR_CAND (
    CAND_ID                VARCHAR(20)    NOT NULL,
    CAND_NM                VARCHAR(50)    NOT NULL,
    EMP_TYPE_CD            VARCHAR(10)    NOT NULL,
    BLNG_CLNT_ID           VARCHAR(20),
    TELNO                  VARCHAR(20),
    EMAIL_ADDR             VARCHAR(100),
    PRVC_AGRE_YN           CHAR(1)        DEFAULT 'N' NOT NULL,
    PRVC_AGRE_DT           DATE,
    PRVC_AGRE_USER_ID      VARCHAR(30),
    JOB_CD                 VARCHAR(10)    NOT NULL,
    SUB_JOB_CD             VARCHAR(10),
    GRADE_CD               VARCHAR(10)    NOT NULL,
    HOME_AREA_CD           VARCHAR(10),
    HOME_ADDR              VARCHAR(50),
    HOPE_UPRC              NUMERIC(15)    NOT NULL,
    INPUT_PSBL_DT          DATE           NOT NULL,
    CNTC_STAT_CD           VARCHAR(10)    NOT NULL,
    LAST_CNTC_DT           DATE,
    CAND_SRC_CD            VARCHAR(10)    NOT NULL,
    MEMO_CN                VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_CAND PRIMARY KEY (CAND_ID),
    CONSTRAINT CK_TB_HR_CAND_01 CHECK (PRVC_AGRE_YN IN ('Y', 'N'))
);

-- TB_HR_CAND_SKILL 후보보유스킬
CREATE TABLE TB_HR_CAND_SKILL (
    CAND_ID                VARCHAR(20)    NOT NULL,
    SKILL_NM               VARCHAR(50)    NOT NULL,
    SORT_ORD               NUMERIC(3)     NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_CAND_SKILL PRIMARY KEY (CAND_ID, SKILL_NM)
);

-- TB_HR_CAND_CNTC 후보연락기록
CREATE TABLE TB_HR_CAND_CNTC (
    CAND_ID                VARCHAR(20)    NOT NULL,
    CNTC_SN                NUMERIC(5)     NOT NULL,
    CNTC_STAT_CD           VARCHAR(10)    NOT NULL,
    CNTC_MEMO              VARCHAR(200)   NOT NULL,
    CNTC_DT                DATE           NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_HR_CAND_CNTC PRIMARY KEY (CAND_ID, CNTC_SN)
);

-- TB_PJ_PROJ 프로젝트
CREATE TABLE TB_PJ_PROJ (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    PROJ_NM                VARCHAR(100)   NOT NULL,
    CUST_CLNT_ID           VARCHAR(20),
    CTRT_TYPE_CD           VARCHAR(10)    NOT NULL,
    PROJ_STAT_CD           VARCHAR(10)    NOT NULL,
    LOSE_DT                DATE,
    LOSE_RSN_CD            VARCHAR(10),
    LOSE_MEMO              VARCHAR(500),
    CTRT_BGN_DT            DATE           NOT NULL,
    CTRT_END_DT            DATE           NOT NULL,
    WORK_PLC_ADDR          VARCHAR(200),
    EST_AMT                NUMERIC(15),
    ORGNL_CTRT_AMT         NUMERIC(15),
    CTRT_AMT               NUMERIC(15)    NOT NULL,
    BDGT_AMT               NUMERIC(15),
    BDGT_MANUAL_YN         CHAR(1)        DEFAULT 'N' NOT NULL,
    TEAM_EXPS_AMT          NUMERIC(15)    NOT NULL,
    PROJ_EXPS_AMT          NUMERIC(15)    NOT NULL,
    FEATURE_CN             VARCHAR(500),
    OPP_ID                 VARCHAR(20),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_PROJ PRIMARY KEY (PROJ_ID),
    CONSTRAINT CK_TB_PJ_PROJ_01 CHECK (BDGT_MANUAL_YN IN ('Y', 'N'))
);

-- TB_PJ_PROJ_PART 프로젝트영업파트이력
CREATE TABLE TB_PJ_PROJ_PART (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    APLY_BGN_YM            CHAR(6)        NOT NULL,
    PART_ID                VARCHAR(20),
    CHG_RSN                VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_PROJ_PART PRIMARY KEY (PROJ_ID, APLY_BGN_YM)
);

-- TB_PJ_PROJ_CHG 계약변경이력
CREATE TABLE TB_PJ_PROJ_CHG (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    HIST_SN                NUMERIC(5)     NOT NULL,
    CHG_ITEM_CD            VARCHAR(10)    NOT NULL,
    CHG_ITEM_NM            VARCHAR(50)    NOT NULL,
    BFR_VAL                VARCHAR(500),
    AFT_VAL                VARCHAR(500),
    CHG_DT                 DATE           NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_PROJ_CHG PRIMARY KEY (PROJ_ID, HIST_SN)
);

-- TB_PJ_MNPW_PLAN 인원계획
CREATE TABLE TB_PJ_MNPW_PLAN (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    JOB_CD                 VARCHAR(10)    NOT NULL,
    PLAN_MM                NUMERIC(6,2)   NOT NULL,
    SRC_CD                 VARCHAR(10)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_MNPW_PLAN PRIMARY KEY (PROJ_ID, JOB_CD)
);

-- TB_PJ_STEP 프로젝트단계
CREATE TABLE TB_PJ_STEP (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    STEP_SN                NUMERIC(2)     NOT NULL,
    PROJ_STEP_CD           VARCHAR(10)    NOT NULL,
    STEP_WGHT_RT           NUMERIC(5,2)   NOT NULL,
    PLAN_BGN_DT            DATE           NOT NULL,
    PLAN_END_DT            DATE           NOT NULL,
    CMPL_DT                DATE,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_STEP PRIMARY KEY (PROJ_ID, STEP_SN)
);

-- TB_PJ_WEEK_MEMO 주간메모
CREATE TABLE TB_PJ_WEEK_MEMO (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    MEMO_SN                NUMERIC(5)     NOT NULL,
    WRT_DT                 DATE           NOT NULL,
    THIS_WEEK_CN           VARCHAR(200),
    NEXT_WEEK_CN           VARCHAR(200),
    REQ_CN                 VARCHAR(200),
    ACTL_PRGRS_RT          NUMERIC(3),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_WEEK_MEMO PRIMARY KEY (PROJ_ID, MEMO_SN)
);

-- TB_PJ_ISSUE 이슈
CREATE TABLE TB_PJ_ISSUE (
    ISSUE_ID               NUMERIC(10)    DEFAULT nextval('SQ_TB_PJ_ISSUE') NOT NULL,
    PROJ_ID                VARCHAR(20)    NOT NULL,
    ISSUE_TTL              VARCHAR(100)   NOT NULL,
    IMPR_CD                VARCHAR(10)    NOT NULL,
    DDLN_DT                DATE           NOT NULL,
    ISSUE_STAT_CD          VARCHAR(10)    NOT NULL,
    ACTN_CN                VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_ISSUE PRIMARY KEY (ISSUE_ID)
);

-- TB_PJ_TK_DSTRB 턴키배분표
CREATE TABLE TB_PJ_TK_DSTRB (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    DSTRB_STEP_CD          VARCHAR(10)    NOT NULL,
    ROW_SN                 NUMERIC(5)     NOT NULL,
    DSTRB_YM               CHAR(6)        NOT NULL,
    ROW_TYPE_CD            VARCHAR(10)    NOT NULL,
    DEV_ID                 VARCHAR(20),
    ITEM_NM                VARCHAR(50),
    DSTRB_AMT              NUMERIC(15)    NOT NULL,
    UPLD_ID                NUMERIC(12),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_TK_DSTRB PRIMARY KEY (PROJ_ID, DSTRB_STEP_CD, ROW_SN, DSTRB_YM)
);

-- TB_PJ_DRCT_EXPS 프로젝트직접경비
CREATE TABLE TB_PJ_DRCT_EXPS (
    PROJ_ID                VARCHAR(20)    NOT NULL,
    EXPS_YM                CHAR(6)        NOT NULL,
    EXPS_SN                NUMERIC(5)     NOT NULL,
    DRCT_EXPS_ITEM_CD      VARCHAR(10)    NOT NULL,
    EXPS_AMT               NUMERIC(15)    NOT NULL,
    RMRK                   VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_DRCT_EXPS PRIMARY KEY (PROJ_ID, EXPS_YM, EXPS_SN)
);

-- TB_PJ_ASGN 투입
CREATE TABLE TB_PJ_ASGN (
    ASGN_ID                VARCHAR(20)    NOT NULL,
    PROJ_ID                VARCHAR(20)    NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    JOB_CD                 VARCHAR(10)    NOT NULL,
    INPUT_BGN_DT           DATE           NOT NULL,
    INPUT_END_DT           DATE           NOT NULL,
    CTRT_MM                NUMERIC(4,2)   NOT NULL,
    BILL_UPRC              NUMERIC(15),
    INPUT_TYPE_CD          VARCHAR(10)    NOT NULL,
    CTRT_DEV_ID            VARCHAR(20),
    EXT_PSBL_CD            VARCHAR(10),
    EXT_CNT                NUMERIC(3)     NOT NULL,
    EARLY_WTHDR_YN         CHAR(1)        DEFAULT 'N' NOT NULL,
    ASGN_STAT_CD           VARCHAR(10)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_ASGN PRIMARY KEY (ASGN_ID),
    CONSTRAINT CK_TB_PJ_ASGN_01 CHECK (EARLY_WTHDR_YN IN ('Y', 'N'))
);

-- TB_PJ_ASGN_HIST 투입변경이력
CREATE TABLE TB_PJ_ASGN_HIST (
    ASGN_ID                VARCHAR(20)    NOT NULL,
    HIST_SN                NUMERIC(5)     NOT NULL,
    ASGN_CHG_TYPE_CD       VARCHAR(10)    NOT NULL,
    APLY_DT                DATE           NOT NULL,
    BFR_VAL                VARCHAR(200),
    AFT_VAL                VARCHAR(200),
    ASGN_CHG_RSN_CD        VARCHAR(10),
    CHG_MEMO               VARCHAR(200),
    NEW_UPRC               NUMERIC(15),
    NEW_CTRT_MM            NUMERIC(4,2),
    RTRO_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    CLOSE_CROSS_YN         CHAR(1)        DEFAULT 'N' NOT NULL,
    CRCT_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    CRCT_TRGT_SN           NUMERIC(5),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_PJ_ASGN_HIST PRIMARY KEY (ASGN_ID, HIST_SN),
    CONSTRAINT CK_TB_PJ_ASGN_HIST_01 CHECK (RTRO_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_PJ_ASGN_HIST_02 CHECK (CLOSE_CROSS_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_PJ_ASGN_HIST_03 CHECK (CRCT_YN IN ('Y', 'N'))
);

-- TB_PJ_ASGN_MON 월별투입
CREATE TABLE TB_PJ_ASGN_MON (
    ASGN_ID                VARCHAR(20)    NOT NULL,
    INPUT_YM               CHAR(6)        NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    PROJ_ID                VARCHAR(20)    NOT NULL,
    INPUT_DAY_CNT          NUMERIC(3)     NOT NULL,
    MON_MM                 NUMERIC(6,2)   NOT NULL,
    APLY_UPRC              NUMERIC(15),
    SALES_AMT              NUMERIC(15)    NOT NULL,
    FIX_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_PJ_ASGN_MON PRIMARY KEY (ASGN_ID, INPUT_YM),
    CONSTRAINT CK_TB_PJ_ASGN_MON_01 CHECK (FIX_YN IN ('Y', 'N'))
);

-- TB_SL_OPP 영업기회
CREATE TABLE TB_SL_OPP (
    OPP_ID                 VARCHAR(20)    NOT NULL,
    OPP_NM                 VARCHAR(100)   NOT NULL,
    CUST_CLNT_ID           VARCHAR(20)    NOT NULL,
    PART_ID                VARCHAR(20)    NOT NULL,
    SALES_STEP_CD          VARCHAR(10)    NOT NULL,
    WIN_PRBL_RT            NUMERIC(5,2)   NOT NULL,
    PRBL_MANUAL_YN         CHAR(1)        DEFAULT 'N' NOT NULL,
    EXPCT_AMT              NUMERIC(15)    NOT NULL,
    BGN_PLAN_YM            CHAR(6)        NOT NULL,
    CTRT_TYPE_CD           VARCHAR(10),
    EXPCT_PRD_MCNT         NUMERIC(3),
    NEXT_TODO_CN           VARCHAR(200),
    NEXT_TODO_DDLN_DT      DATE,
    WORK_PLC_ADDR          VARCHAR(200),
    RMRK                   VARCHAR(500),
    OPP_STAT_CD            VARCHAR(10)    NOT NULL,
    EXPCT_PRFT_RT          NUMERIC(7,2),
    APRV_STAT_CD           VARCHAR(10)    NOT NULL,
    APRV_PRFT_RT           NUMERIC(7,2),
    RSLT_DT                DATE,
    LOSE_RSN_CD            VARCHAR(10),
    LOSE_MEMO              VARCHAR(500),
    PROJ_ID                VARCHAR(20),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SL_OPP PRIMARY KEY (OPP_ID),
    CONSTRAINT CK_TB_SL_OPP_01 CHECK (PRBL_MANUAL_YN IN ('Y', 'N'))
);

-- TB_SL_OPP_NEED 영업기회필요인력
CREATE TABLE TB_SL_OPP_NEED (
    OPP_ID                 VARCHAR(20)    NOT NULL,
    NEED_SN                NUMERIC(3)     NOT NULL,
    JOB_CD                 VARCHAR(10)    NOT NULL,
    GRADE_CD               VARCHAR(10),
    NEED_CNT               NUMERIC(3)     NOT NULL,
    BGN_YM                 CHAR(6)        NOT NULL,
    PRD_MCNT               NUMERIC(3)     NOT NULL,
    MON_MM                 NUMERIC(4,2)   NOT NULL,
    EXPCT_UPRC             NUMERIC(15),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SL_OPP_NEED PRIMARY KEY (OPP_ID, NEED_SN)
);

-- TB_SL_TENT_ASGN 가배정
CREATE TABLE TB_SL_TENT_ASGN (
    TENT_ID                NUMERIC(10)    DEFAULT nextval('SQ_TB_SL_TENT_ASGN') NOT NULL,
    OPP_ID                 VARCHAR(20)    NOT NULL,
    NEED_SN                NUMERIC(3)     NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    BGN_YM                 CHAR(6)        NOT NULL,
    TENT_STAT_CD           VARCHAR(10)    NOT NULL,
    SRC_SCRN_ID            VARCHAR(10)    NOT NULL,
    PROC_DTTM              TIMESTAMP,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SL_TENT_ASGN PRIMARY KEY (TENT_ID)
);

-- TB_SL_APRV_HIST 견적승인이력
CREATE TABLE TB_SL_APRV_HIST (
    OPP_ID                 VARCHAR(20)    NOT NULL,
    HIST_SN                NUMERIC(5)     NOT NULL,
    APRV_ACT_CD            VARCHAR(10)    NOT NULL,
    PRFT_RT                NUMERIC(7,2)   NOT NULL,
    ACT_RSN                VARCHAR(300),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_SL_APRV_HIST PRIMARY KEY (OPP_ID, HIST_SN)
);

-- TB_SL_RCMD_LOG AI추천기록
CREATE TABLE TB_SL_RCMD_LOG (
    RCMD_ID                NUMERIC(12)    NOT NULL,
    RANK_ORD               NUMERIC(3)     NOT NULL,
    RCMD_TYPE_CD           VARCHAR(10)    NOT NULL,
    OPP_ID                 VARCHAR(20),
    NEED_SN                NUMERIC(3),
    PROJ_ID                VARCHAR(20),
    JOB_CD                 VARCHAR(10)    NOT NULL,
    BGN_YM                 CHAR(6)        NOT NULL,
    REQ_GRADE_CD           VARCHAR(10)    NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    TOT_SCORE              NUMERIC(5,1)   NOT NULL,
    SCORE_DTL              VARCHAR(500)   NOT NULL,
    RCMD_RSN               VARCHAR(300),
    USER_ACT_CD            VARCHAR(10),
    RCMD_EXCL_CD           VARCHAR(10),
    ACT_USER_ID            VARCHAR(30),
    ACT_DTTM               TIMESTAMP,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_SL_RCMD_LOG PRIMARY KEY (RCMD_ID, RANK_ORD)
);

-- TB_BL_BILL 청구
CREATE TABLE TB_BL_BILL (
    BILL_ID                VARCHAR(20)    NOT NULL,
    PROJ_ID                VARCHAR(20)    NOT NULL,
    BILL_ORD               NUMERIC(3)     NOT NULL,
    BILL_YM                CHAR(6)        NOT NULL,
    BILL_DT                DATE           NOT NULL,
    SPLY_AMT               NUMERIC(15)    NOT NULL,
    VAT_RT                 NUMERIC(5,2)   NOT NULL,
    VAT_AMT                NUMERIC(15)    NOT NULL,
    TOT_AMT                NUMERIC(15)    NOT NULL,
    RCPT_SUM_AMT           NUMERIC(15)    NOT NULL,
    UNPAID_AMT             NUMERIC(15)    NOT NULL,
    PAY_DUE_DT             DATE           NOT NULL,
    TAX_INVC_NO            VARCHAR(30),
    CNCL_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    CNCL_RSN               VARCHAR(500),
    CNCL_USER_ID           VARCHAR(30),
    CNCL_DTTM              TIMESTAMP,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_BL_BILL PRIMARY KEY (BILL_ID),
    CONSTRAINT CK_TB_BL_BILL_01 CHECK (CNCL_YN IN ('Y', 'N'))
);

-- TB_BL_RCPT 입금
CREATE TABLE TB_BL_RCPT (
    RCPT_ID                NUMERIC(12)    DEFAULT nextval('SQ_TB_BL_RCPT') NOT NULL,
    BILL_ID                VARCHAR(20)    NOT NULL,
    RCPT_DT                DATE           NOT NULL,
    RCPT_AMT               NUMERIC(15)    NOT NULL,
    CNCL_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    CNCL_RSN               VARCHAR(500),
    CNCL_USER_ID           VARCHAR(30),
    CNCL_DTTM              TIMESTAMP,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_BL_RCPT PRIMARY KEY (RCPT_ID),
    CONSTRAINT CK_TB_BL_RCPT_01 CHECK (CNCL_YN IN ('Y', 'N'))
);

-- TB_BL_DUN_HIST 미수알림이력
CREATE TABLE TB_BL_DUN_HIST (
    BILL_ID                VARCHAR(20)    NOT NULL,
    DUN_SN                 NUMERIC(5)     NOT NULL,
    DUN_STEP               NUMERIC(1)     NOT NULL,
    SEND_DT                DATE           NOT NULL,
    RCVR_DESC              VARCHAR(300)   NOT NULL,
    SEND_MTHD_CD           VARCHAR(10)    NOT NULL,
    RESEND_YN              CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_BL_DUN_HIST PRIMARY KEY (BILL_ID, DUN_SN),
    CONSTRAINT CK_TB_BL_DUN_HIST_01 CHECK (RESEND_YN IN ('Y', 'N'))
);

-- TB_CO_MON_COST 월별원가배분
CREATE TABLE TB_CO_MON_COST (
    COST_YM                CHAR(6)        NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    PROJ_ID                VARCHAR(20)    NOT NULL,
    ASGN_ID                VARCHAR(20)    NOT NULL,
    ASGN_MM                NUMERIC(6,2)   NOT NULL,
    FREE_MM                NUMERIC(6,2)   NOT NULL,
    SALES_AMT              NUMERIC(15)    NOT NULL,
    LABOR_ALLOC_AMT        NUMERIC(15)    NOT NULL,
    TEAM_EXPS_AMT          NUMERIC(15)    NOT NULL,
    PROJ_EXPS_AMT          NUMERIC(15)    NOT NULL,
    DRCT_EXPS_AMT          NUMERIC(15)    NOT NULL,
    COST_CLS_CD            VARCHAR(10)    NOT NULL,
    CTRT_TYPE_CD           VARCHAR(10),
    PART_ID                VARCHAR(20),
    FIX_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CO_MON_COST PRIMARY KEY (COST_YM, DEV_ID, PROJ_ID, ASGN_ID),
    CONSTRAINT CK_TB_CO_MON_COST_01 CHECK (FIX_YN IN ('Y', 'N'))
);

-- TB_CO_MON_PL 월별손익
CREATE TABLE TB_CO_MON_PL (
    PL_YM                  CHAR(6)        NOT NULL,
    SALES_AMT              NUMERIC(15)    NOT NULL,
    ALLOC_COST_AMT         NUMERIC(15),
    IDLE_COST_AMT          NUMERIC(15),
    DRCT_EXPS_AMT          NUMERIC(15),
    SALES_COST_AMT         NUMERIC(15)    NOT NULL,
    GROSS_PRFT_AMT         NUMERIC(15),
    SGA_INPUT_AMT          NUMERIC(15),
    INDRCT_LABOR_AMT       NUMERIC(15),
    RND_AMT                NUMERIC(15),
    SGA_TOT_AMT            NUMERIC(15)    NOT NULL,
    PREV_ADJ_AMT           NUMERIC(15),
    OPRT_PRFT_AMT          NUMERIC(15)    NOT NULL,
    NOPR_INCM_AMT          NUMERIC(15),
    NOPR_EXPS_AMT          NUMERIC(15),
    CORP_TAX_AMT           NUMERIC(15),
    NET_PRFT_AMT           NUMERIC(15),
    MIGR_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    FIX_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CO_MON_PL PRIMARY KEY (PL_YM),
    CONSTRAINT CK_TB_CO_MON_PL_01 CHECK (MIGR_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_CO_MON_PL_02 CHECK (FIX_YN IN ('Y', 'N'))
);

-- TB_CO_PREV_ADJ 전월조정
CREATE TABLE TB_CO_PREV_ADJ (
    ADJ_ID                 NUMERIC(10)    DEFAULT nextval('SQ_TB_CO_PREV_ADJ') NOT NULL,
    APLY_YM                CHAR(6)        NOT NULL,
    TRGT_YM                CHAR(6)        NOT NULL,
    ADJ_ITEM_CD            VARCHAR(10)    NOT NULL,
    ADJ_AMT                NUMERIC(15)    NOT NULL,
    ADJ_RSN                VARCHAR(300)   NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CO_PREV_ADJ PRIMARY KEY (ADJ_ID)
);

-- TB_CO_PART_SETL 영업파트정산
CREATE TABLE TB_CO_PART_SETL (
    PART_ID                VARCHAR(20)    NOT NULL,
    SETL_YM                CHAR(6)        NOT NULL,
    CORP_CARD_AMT          NUMERIC(15)    NOT NULL,
    PART_PRFT_AMT          NUMERIC(15),
    SETL_STAT_CD           VARCHAR(10)    NOT NULL,
    CNFM_USER_ID           VARCHAR(30),
    CNFM_DTTM              TIMESTAMP,
    CNCL_RSN               VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CO_PART_SETL PRIMARY KEY (PART_ID, SETL_YM)
);

-- TB_CO_PART_SETL_DTL 영업파트정산상세
CREATE TABLE TB_CO_PART_SETL_DTL (
    PART_ID                VARCHAR(20)    NOT NULL,
    SETL_YM                CHAR(6)        NOT NULL,
    DEV_ID                 VARCHAR(20)    NOT NULL,
    EMP_TYPE_CD            VARCHAR(10)    NOT NULL,
    PROJ_NM_LIST           VARCHAR(1000)  NOT NULL,
    MM                     NUMERIC(6,2)   NOT NULL,
    UPRC_AMT               NUMERIC(15)    NOT NULL,
    PAY_AMT                NUMERIC(15)    NOT NULL,
    SALES_EXPS_AMT         NUMERIC(15)    NOT NULL,
    RETIRE_AMT             NUMERIC(15)    NOT NULL,
    COST_AMT               NUMERIC(15)    NOT NULL,
    DIFF_AMT               NUMERIC(15)    NOT NULL,
    PRFT_AMT               NUMERIC(15)    NOT NULL,
    RMRK                   VARCHAR(100),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CO_PART_SETL_DTL PRIMARY KEY (PART_ID, SETL_YM, DEV_ID)
);

-- TB_FI_ITEM 재무항목
CREATE TABLE TB_FI_ITEM (
    FIN_YM                 CHAR(6)        NOT NULL,
    ITEM_SN                NUMERIC(5)     NOT NULL,
    FIN_CLS_CD             VARCHAR(10)    NOT NULL,
    SGA_ITEM_CD            VARCHAR(10),
    ITEM_NM                VARCHAR(50)    NOT NULL,
    AMT                    NUMERIC(15)    NOT NULL,
    VAT_AMT                NUMERIC(15)    NOT NULL,
    IO_DT                  DATE,
    RPT_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    AUTO_FILL_YN           CHAR(1)        DEFAULT 'N' NOT NULL,
    RMRK                   VARCHAR(500),
    UPLD_ID                NUMERIC(12),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_ITEM PRIMARY KEY (FIN_YM, ITEM_SN),
    CONSTRAINT CK_TB_FI_ITEM_01 CHECK (RPT_YN IN ('Y', 'N')),
    CONSTRAINT CK_TB_FI_ITEM_02 CHECK (AUTO_FILL_YN IN ('Y', 'N'))
);

-- TB_FI_CORP_TAX 연간법인세
CREATE TABLE TB_FI_CORP_TAX (
    TAX_YR                 CHAR(4)        NOT NULL,
    EST_TAX_AMT            NUMERIC(15)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_CORP_TAX PRIMARY KEY (TAX_YR)
);

-- TB_FI_TAX_PAY 법인세납부
CREATE TABLE TB_FI_TAX_PAY (
    TAX_YR                 CHAR(4)        NOT NULL,
    PAY_TYPE_CD            VARCHAR(10)    NOT NULL,
    PAY_DUE_DT             DATE           NOT NULL,
    PAY_AMT                NUMERIC(15),
    PAY_DT                 DATE,
    PAY_YN                 CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_TAX_PAY PRIMARY KEY (TAX_YR, PAY_TYPE_CD),
    CONSTRAINT CK_TB_FI_TAX_PAY_01 CHECK (PAY_YN IN ('Y', 'N'))
);

-- TB_FI_LOAN 대여금입출금
CREATE TABLE TB_FI_LOAN (
    LOAN_SN                NUMERIC(10)    DEFAULT nextval('SQ_TB_FI_LOAN') NOT NULL,
    LOAN_DT                DATE           NOT NULL,
    IO_CD                  VARCHAR(10)    NOT NULL,
    AMT                    NUMERIC(15)    NOT NULL,
    CNTPRT_NM              VARCHAR(100),
    RMRK                   VARCHAR(200),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_LOAN PRIMARY KEY (LOAN_SN)
);

-- TB_FI_CARD 법인카드
CREATE TABLE TB_FI_CARD (
    CARD_ID                VARCHAR(20)    NOT NULL,
    CARD_NM                VARCHAR(100)   NOT NULL,
    CARD_NO_L4             CHAR(4)        NOT NULL,
    USE_YN                 CHAR(1)        DEFAULT 'Y' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_CARD PRIMARY KEY (CARD_ID),
    CONSTRAINT CK_TB_FI_CARD_01 CHECK (USE_YN IN ('Y', 'N'))
);

-- TB_FI_CARD_MON 법인카드월합계
CREATE TABLE TB_FI_CARD_MON (
    PAY_YM                 CHAR(6)        NOT NULL,
    CARD_SUM_AMT           NUMERIC(15)    NOT NULL,
    CASH_AMT               NUMERIC(15)    NOT NULL,
    EDIT_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_CARD_MON PRIMARY KEY (PAY_YM),
    CONSTRAINT CK_TB_FI_CARD_MON_01 CHECK (EDIT_YN IN ('Y', 'N'))
);

-- TB_FI_CARD_PAY 법인카드월결제
CREATE TABLE TB_FI_CARD_PAY (
    PAY_YM                 CHAR(6)        NOT NULL,
    CARD_ID                VARCHAR(20)    NOT NULL,
    PAY_AMT                NUMERIC(15)    NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_CARD_PAY PRIMARY KEY (PAY_YM, CARD_ID)
);

-- TB_FI_XFER 계좌이체지출
CREATE TABLE TB_FI_XFER (
    XFER_SN                NUMERIC(10)    DEFAULT nextval('SQ_TB_FI_XFER') NOT NULL,
    XFER_DT                DATE           NOT NULL,
    XFER_CLS_CD            VARCHAR(10)    NOT NULL,
    ITEM_NM                VARCHAR(100)   NOT NULL,
    RCVR_NM                VARCHAR(100),
    AMT                    NUMERIC(15)    NOT NULL,
    RMRK                   VARCHAR(200),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_XFER PRIMARY KEY (XFER_SN)
);

-- TB_FI_BIZ_PLAN 사업계획목표
CREATE TABLE TB_FI_BIZ_PLAN (
    PLAN_YM                CHAR(6)        NOT NULL,
    PART_ID                VARCHAR(20)    NOT NULL,
    SALES_TGT_AMT          NUMERIC(15)    NOT NULL,
    OPRT_PRFT_TGT_AMT      NUMERIC(15)    NOT NULL,
    UPLD_ID                NUMERIC(12),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_FI_BIZ_PLAN PRIMARY KEY (PLAN_YM, PART_ID)
);

-- TB_CL_CLOSE 월마감
CREATE TABLE TB_CL_CLOSE (
    CLOSE_YM               CHAR(6)        NOT NULL,
    CLOSE_STAT_CD          VARCHAR(10)    NOT NULL,
    CLOSE_USER_ID          VARCHAR(30),
    CLOSE_DTTM             TIMESTAMP,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CL_CLOSE PRIMARY KEY (CLOSE_YM)
);

-- TB_CL_CLOSE_HIST 마감이력
CREATE TABLE TB_CL_CLOSE_HIST (
    CLOSE_YM               CHAR(6)        NOT NULL,
    HIST_SN                NUMERIC(5)     NOT NULL,
    ACT_CD                 VARCHAR(10)    NOT NULL,
    ACT_RSN                VARCHAR(500),
    REMAIN_CHK_CN          VARCHAR(500),
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_CL_CLOSE_HIST PRIMARY KEY (CLOSE_YM, HIST_SN)
);

-- TB_CL_CLOSE_CHK 마감점검결과
CREATE TABLE TB_CL_CLOSE_CHK (
    CLOSE_YM               CHAR(6)        NOT NULL,
    CHK_ITEM_CD            VARCHAR(10)    NOT NULL,
    CHK_STAT_CD            VARCHAR(10)    NOT NULL,
    CHK_CNT                NUMERIC(7),
    CHK_CN                 VARCHAR(300),
    LINK_SCRN_ID           VARCHAR(10),
    CHK_DTTM               TIMESTAMP      NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_CL_CLOSE_CHK PRIMARY KEY (CLOSE_YM, CHK_ITEM_CD)
);

-- 계산규칙 표: 화면 명세(v3.5)의 「이 화면이 쓰는 식」 내용을 담는다. 화면 파일에는 식 번호·이름만 있고, 식 내용은 이 표에서 CALC_ID로 읽는다.
-- 실행 순서: 07_DDL_PostgreSQL.sql → 07_DML_초기데이터.sql → 이 파일 → 07_DML_계산규칙.sql
-- 근거: 명세서 v3.3 화면별 파일의 식 93개(식-03·65·78은 쓰지 않아 없음)와 「계산 공통 원칙」(식-00)

-- TB_SY_CALC_RULE 계산규칙
CREATE TABLE TB_SY_CALC_RULE (
    CALC_ID                VARCHAR(10)    NOT NULL,
    SFR_ID                 VARCHAR(10),
    CALC_NM                VARCHAR(100)   NOT NULL,
    FRML_CN                TEXT           NOT NULL,
    DTL_CN                 TEXT           NOT NULL,
    USE_SCRN_CN            VARCHAR(200),
    SORT_ORD               NUMERIC(5)     NOT NULL,
    REG_USER_ID            VARCHAR(30)    NOT NULL,
    REG_DTTM               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID            VARCHAR(30),
    MOD_DTTM               TIMESTAMP,
    CONSTRAINT PK_TB_SY_CALC_RULE PRIMARY KEY (CALC_ID)
);


-- 3. 외래키
ALTER TABLE TB_SY_USER_ROLE ADD CONSTRAINT FK_TB_SY_USER_ROLE_01 FOREIGN KEY (USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_SY_USER_ROLE ADD CONSTRAINT FK_TB_SY_USER_ROLE_02 FOREIGN KEY (ROLE_ID) REFERENCES TB_SY_ROLE (ROLE_ID);
ALTER TABLE TB_SY_USER_SCOPE ADD CONSTRAINT FK_TB_SY_USER_SCOPE_01 FOREIGN KEY (USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_SY_USER_SCOPE ADD CONSTRAINT FK_TB_SY_USER_SCOPE_02 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID);
ALTER TABLE TB_SY_USER_SCOPE ADD CONSTRAINT FK_TB_SY_USER_SCOPE_03 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_SY_MENU ADD CONSTRAINT FK_TB_SY_MENU_01 FOREIGN KEY (UPPER_MENU_ID) REFERENCES TB_SY_MENU (MENU_ID);
ALTER TABLE TB_SY_ROLE_MENU_AUTH ADD CONSTRAINT FK_TB_SY_ROLE_MENU_AUTH_01 FOREIGN KEY (ROLE_ID) REFERENCES TB_SY_ROLE (ROLE_ID);
ALTER TABLE TB_SY_ROLE_MENU_AUTH ADD CONSTRAINT FK_TB_SY_ROLE_MENU_AUTH_02 FOREIGN KEY (MENU_ID) REFERENCES TB_SY_MENU (MENU_ID);
ALTER TABLE TB_SY_CODE ADD CONSTRAINT FK_TB_SY_CODE_01 FOREIGN KEY (CODE_GRP_ID) REFERENCES TB_SY_CODE_GRP (CODE_GRP_ID);
ALTER TABLE TB_SY_CODE_MAP ADD CONSTRAINT FK_TB_SY_CODE_MAP_01 FOREIGN KEY (CODE_GRP_ID, CODE) REFERENCES TB_SY_CODE (CODE_GRP_ID, CODE);
ALTER TABLE TB_SY_USER_PREF ADD CONSTRAINT FK_TB_SY_USER_PREF_01 FOREIGN KEY (USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_SY_NOTI ADD CONSTRAINT FK_TB_SY_NOTI_01 FOREIGN KEY (RCVR_USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_SY_UPLD_ERR ADD CONSTRAINT FK_TB_SY_UPLD_ERR_01 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD (UPLD_ID);
ALTER TABLE TB_SY_ADM_AREA ADD CONSTRAINT FK_TB_SY_ADM_AREA_01 FOREIGN KEY (UPPER_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD);
ALTER TABLE TB_SY_ADM_HDONG ADD CONSTRAINT FK_TB_SY_ADM_HDONG_01 FOREIGN KEY (AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD);
ALTER TABLE TB_BS_PART_STD ADD CONSTRAINT FK_TB_BS_PART_STD_01 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID);
ALTER TABLE TB_BS_PART_STD ADD CONSTRAINT FK_TB_BS_PART_STD_02 FOREIGN KEY (PART_LDR_USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_HR_DEV ADD CONSTRAINT FK_TB_HR_DEV_01 FOREIGN KEY (BLNG_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID);
ALTER TABLE TB_HR_DEV ADD CONSTRAINT FK_TB_HR_DEV_02 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID);
ALTER TABLE TB_HR_DEV ADD CONSTRAINT FK_TB_HR_DEV_03 FOREIGN KEY (HOME_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD);
ALTER TABLE TB_HR_DEV_SKILL ADD CONSTRAINT FK_TB_HR_DEV_SKILL_01 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_HR_DEV_PRC ADD CONSTRAINT FK_TB_HR_DEV_PRC_01 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_HR_DEV_STAT ADD CONSTRAINT FK_TB_HR_DEV_STAT_01 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_HR_MON_PAY ADD CONSTRAINT FK_TB_HR_MON_PAY_01 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_HR_MON_PAY ADD CONSTRAINT FK_TB_HR_MON_PAY_02 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD (UPLD_ID);
ALTER TABLE TB_HR_CAND ADD CONSTRAINT FK_TB_HR_CAND_01 FOREIGN KEY (BLNG_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID);
ALTER TABLE TB_HR_CAND ADD CONSTRAINT FK_TB_HR_CAND_02 FOREIGN KEY (HOME_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD);
ALTER TABLE TB_HR_CAND_SKILL ADD CONSTRAINT FK_TB_HR_CAND_SKILL_01 FOREIGN KEY (CAND_ID) REFERENCES TB_HR_CAND (CAND_ID);
ALTER TABLE TB_HR_CAND_CNTC ADD CONSTRAINT FK_TB_HR_CAND_CNTC_01 FOREIGN KEY (CAND_ID) REFERENCES TB_HR_CAND (CAND_ID);
ALTER TABLE TB_PJ_PROJ ADD CONSTRAINT FK_TB_PJ_PROJ_01 FOREIGN KEY (CUST_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID);
ALTER TABLE TB_PJ_PROJ ADD CONSTRAINT FK_TB_PJ_PROJ_02 FOREIGN KEY (OPP_ID) REFERENCES TB_SL_OPP (OPP_ID);
ALTER TABLE TB_PJ_PROJ_PART ADD CONSTRAINT FK_TB_PJ_PROJ_PART_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_PROJ_PART ADD CONSTRAINT FK_TB_PJ_PROJ_PART_02 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID);
ALTER TABLE TB_PJ_PROJ_CHG ADD CONSTRAINT FK_TB_PJ_PROJ_CHG_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_MNPW_PLAN ADD CONSTRAINT FK_TB_PJ_MNPW_PLAN_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_STEP ADD CONSTRAINT FK_TB_PJ_STEP_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_WEEK_MEMO ADD CONSTRAINT FK_TB_PJ_WEEK_MEMO_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_ISSUE ADD CONSTRAINT FK_TB_PJ_ISSUE_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_TK_DSTRB ADD CONSTRAINT FK_TB_PJ_TK_DSTRB_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_TK_DSTRB ADD CONSTRAINT FK_TB_PJ_TK_DSTRB_02 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_PJ_TK_DSTRB ADD CONSTRAINT FK_TB_PJ_TK_DSTRB_03 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD (UPLD_ID);
ALTER TABLE TB_PJ_DRCT_EXPS ADD CONSTRAINT FK_TB_PJ_DRCT_EXPS_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_ASGN ADD CONSTRAINT FK_TB_PJ_ASGN_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_PJ_ASGN ADD CONSTRAINT FK_TB_PJ_ASGN_02 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_PJ_ASGN ADD CONSTRAINT FK_TB_PJ_ASGN_03 FOREIGN KEY (CTRT_DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_PJ_ASGN_HIST ADD CONSTRAINT FK_TB_PJ_ASGN_HIST_01 FOREIGN KEY (ASGN_ID) REFERENCES TB_PJ_ASGN (ASGN_ID);
ALTER TABLE TB_PJ_ASGN_MON ADD CONSTRAINT FK_TB_PJ_ASGN_MON_01 FOREIGN KEY (ASGN_ID) REFERENCES TB_PJ_ASGN (ASGN_ID);
ALTER TABLE TB_SL_OPP ADD CONSTRAINT FK_TB_SL_OPP_01 FOREIGN KEY (CUST_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID);
ALTER TABLE TB_SL_OPP ADD CONSTRAINT FK_TB_SL_OPP_02 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID);
ALTER TABLE TB_SL_OPP ADD CONSTRAINT FK_TB_SL_OPP_03 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_SL_OPP_NEED ADD CONSTRAINT FK_TB_SL_OPP_NEED_01 FOREIGN KEY (OPP_ID) REFERENCES TB_SL_OPP (OPP_ID);
ALTER TABLE TB_SL_TENT_ASGN ADD CONSTRAINT FK_TB_SL_TENT_ASGN_01 FOREIGN KEY (OPP_ID, NEED_SN) REFERENCES TB_SL_OPP_NEED (OPP_ID, NEED_SN);
ALTER TABLE TB_SL_TENT_ASGN ADD CONSTRAINT FK_TB_SL_TENT_ASGN_02 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_SL_APRV_HIST ADD CONSTRAINT FK_TB_SL_APRV_HIST_01 FOREIGN KEY (OPP_ID) REFERENCES TB_SL_OPP (OPP_ID);
ALTER TABLE TB_SL_RCMD_LOG ADD CONSTRAINT FK_TB_SL_RCMD_LOG_01 FOREIGN KEY (OPP_ID) REFERENCES TB_SL_OPP (OPP_ID);
ALTER TABLE TB_SL_RCMD_LOG ADD CONSTRAINT FK_TB_SL_RCMD_LOG_02 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_SL_RCMD_LOG ADD CONSTRAINT FK_TB_SL_RCMD_LOG_03 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_BL_BILL ADD CONSTRAINT FK_TB_BL_BILL_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID);
ALTER TABLE TB_BL_BILL ADD CONSTRAINT FK_TB_BL_BILL_02 FOREIGN KEY (CNCL_USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_BL_RCPT ADD CONSTRAINT FK_TB_BL_RCPT_01 FOREIGN KEY (BILL_ID) REFERENCES TB_BL_BILL (BILL_ID);
ALTER TABLE TB_BL_RCPT ADD CONSTRAINT FK_TB_BL_RCPT_02 FOREIGN KEY (CNCL_USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_BL_DUN_HIST ADD CONSTRAINT FK_TB_BL_DUN_HIST_01 FOREIGN KEY (BILL_ID) REFERENCES TB_BL_BILL (BILL_ID);
ALTER TABLE TB_CO_PART_SETL ADD CONSTRAINT FK_TB_CO_PART_SETL_01 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID);
ALTER TABLE TB_CO_PART_SETL ADD CONSTRAINT FK_TB_CO_PART_SETL_02 FOREIGN KEY (CNFM_USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_CO_PART_SETL_DTL ADD CONSTRAINT FK_TB_CO_PART_SETL_DTL_01 FOREIGN KEY (PART_ID, SETL_YM) REFERENCES TB_CO_PART_SETL (PART_ID, SETL_YM);
ALTER TABLE TB_CO_PART_SETL_DTL ADD CONSTRAINT FK_TB_CO_PART_SETL_DTL_02 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID);
ALTER TABLE TB_FI_ITEM ADD CONSTRAINT FK_TB_FI_ITEM_01 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD (UPLD_ID);
ALTER TABLE TB_FI_CARD_PAY ADD CONSTRAINT FK_TB_FI_CARD_PAY_01 FOREIGN KEY (PAY_YM) REFERENCES TB_FI_CARD_MON (PAY_YM);
ALTER TABLE TB_FI_CARD_PAY ADD CONSTRAINT FK_TB_FI_CARD_PAY_02 FOREIGN KEY (CARD_ID) REFERENCES TB_FI_CARD (CARD_ID);
ALTER TABLE TB_FI_BIZ_PLAN ADD CONSTRAINT FK_TB_FI_BIZ_PLAN_01 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD (UPLD_ID);
ALTER TABLE TB_CL_CLOSE ADD CONSTRAINT FK_TB_CL_CLOSE_01 FOREIGN KEY (CLOSE_USER_ID) REFERENCES TB_SY_USER (USER_ID);
ALTER TABLE TB_CL_CLOSE_HIST ADD CONSTRAINT FK_TB_CL_CLOSE_HIST_01 FOREIGN KEY (CLOSE_YM) REFERENCES TB_CL_CLOSE (CLOSE_YM);
ALTER TABLE TB_CL_CLOSE_CHK ADD CONSTRAINT FK_TB_CL_CLOSE_CHK_01 FOREIGN KEY (CLOSE_YM) REFERENCES TB_CL_CLOSE (CLOSE_YM);

-- 4. 인덱스
CREATE INDEX IX_TB_SY_USER_ROLE_01 ON TB_SY_USER_ROLE (ROLE_ID);
CREATE INDEX IX_TB_SY_USER_SCOPE_01 ON TB_SY_USER_SCOPE (PART_ID);
CREATE INDEX IX_TB_SY_USER_SCOPE_02 ON TB_SY_USER_SCOPE (PROJ_ID);
CREATE INDEX IX_TB_SY_MENU_01 ON TB_SY_MENU (UPPER_MENU_ID);
CREATE INDEX IX_TB_SY_ROLE_MENU_AUTH_01 ON TB_SY_ROLE_MENU_AUTH (MENU_ID);
CREATE INDEX IX_TB_SY_CODE_MAP_01 ON TB_SY_CODE_MAP (CODE_GRP_ID, CODE);
CREATE INDEX IX_TB_SY_NOTI_01 ON TB_SY_NOTI (RCVR_USER_ID);
CREATE INDEX IX_TB_SY_NOTI_02 ON TB_SY_NOTI (NOTI_DTTM);
CREATE INDEX IX_TB_SY_CHG_HIST_01 ON TB_SY_CHG_HIST (TRGT_TBL_NM, TRGT_KEY);
CREATE INDEX IX_TB_SY_ACCS_LOG_01 ON TB_SY_ACCS_LOG (USER_ID);
CREATE INDEX IX_TB_SY_ACCS_LOG_02 ON TB_SY_ACCS_LOG (ACT_DTTM);
CREATE INDEX IX_TB_SY_AI_QNA_01 ON TB_SY_AI_QNA (USER_ID);
CREATE INDEX IX_TB_SY_ADM_AREA_01 ON TB_SY_ADM_AREA (AREA_LVL_CD);
CREATE INDEX IX_TB_SY_ADM_AREA_02 ON TB_SY_ADM_AREA (SIDO_NM);
CREATE INDEX IX_TB_SY_ADM_AREA_03 ON TB_SY_ADM_AREA (UPPER_AREA_CD);
CREATE INDEX IX_TB_SY_ADM_HDONG_01 ON TB_SY_ADM_HDONG (HDONG_NM);
CREATE INDEX IX_TB_SY_ADM_HDONG_02 ON TB_SY_ADM_HDONG (AREA_CD);
CREATE INDEX IX_TB_BS_CLNT_01 ON TB_BS_CLNT (CLNT_TYPE_CD);
CREATE INDEX IX_TB_BS_PART_STD_01 ON TB_BS_PART_STD (PART_LDR_USER_ID);
CREATE INDEX IX_TB_HR_DEV_01 ON TB_HR_DEV (EMP_TYPE_CD);
CREATE INDEX IX_TB_HR_DEV_02 ON TB_HR_DEV (JOB_CD);
CREATE INDEX IX_TB_HR_DEV_03 ON TB_HR_DEV (WORK_STAT_CD);
CREATE INDEX IX_TB_HR_DEV_04 ON TB_HR_DEV (BLNG_CLNT_ID);
CREATE INDEX IX_TB_HR_DEV_05 ON TB_HR_DEV (PART_ID);
CREATE INDEX IX_TB_HR_DEV_06 ON TB_HR_DEV (HOME_AREA_CD);
CREATE INDEX IX_TB_HR_MON_PAY_01 ON TB_HR_MON_PAY (DEV_ID);
CREATE INDEX IX_TB_HR_MON_PAY_02 ON TB_HR_MON_PAY (UPLD_ID);
CREATE INDEX IX_TB_HR_CAND_01 ON TB_HR_CAND (JOB_CD);
CREATE INDEX IX_TB_HR_CAND_02 ON TB_HR_CAND (BLNG_CLNT_ID);
CREATE INDEX IX_TB_HR_CAND_03 ON TB_HR_CAND (HOME_AREA_CD);
CREATE INDEX IX_TB_PJ_PROJ_01 ON TB_PJ_PROJ (CUST_CLNT_ID);
CREATE INDEX IX_TB_PJ_PROJ_02 ON TB_PJ_PROJ (CTRT_TYPE_CD);
CREATE INDEX IX_TB_PJ_PROJ_03 ON TB_PJ_PROJ (PROJ_STAT_CD);
CREATE INDEX IX_TB_PJ_PROJ_04 ON TB_PJ_PROJ (OPP_ID);
CREATE INDEX IX_TB_PJ_PROJ_PART_01 ON TB_PJ_PROJ_PART (PART_ID);
CREATE INDEX IX_TB_PJ_WEEK_MEMO_01 ON TB_PJ_WEEK_MEMO (WRT_DT);
CREATE INDEX IX_TB_PJ_ISSUE_01 ON TB_PJ_ISSUE (PROJ_ID);
CREATE INDEX IX_TB_PJ_ISSUE_02 ON TB_PJ_ISSUE (ISSUE_STAT_CD);
CREATE INDEX IX_TB_PJ_TK_DSTRB_01 ON TB_PJ_TK_DSTRB (DEV_ID);
CREATE INDEX IX_TB_PJ_TK_DSTRB_02 ON TB_PJ_TK_DSTRB (UPLD_ID);
CREATE INDEX IX_TB_PJ_ASGN_01 ON TB_PJ_ASGN (PROJ_ID);
CREATE INDEX IX_TB_PJ_ASGN_02 ON TB_PJ_ASGN (DEV_ID, INPUT_BGN_DT, INPUT_END_DT);
CREATE INDEX IX_TB_PJ_ASGN_03 ON TB_PJ_ASGN (CTRT_DEV_ID);
CREATE INDEX IX_TB_PJ_ASGN_HIST_01 ON TB_PJ_ASGN_HIST (ASGN_CHG_TYPE_CD);
CREATE INDEX IX_TB_PJ_ASGN_MON_01 ON TB_PJ_ASGN_MON (INPUT_YM);
CREATE INDEX IX_TB_PJ_ASGN_MON_02 ON TB_PJ_ASGN_MON (DEV_ID);
CREATE INDEX IX_TB_PJ_ASGN_MON_03 ON TB_PJ_ASGN_MON (PROJ_ID);
CREATE INDEX IX_TB_SL_OPP_01 ON TB_SL_OPP (PART_ID);
CREATE INDEX IX_TB_SL_OPP_02 ON TB_SL_OPP (OPP_STAT_CD);
CREATE INDEX IX_TB_SL_OPP_03 ON TB_SL_OPP (CUST_CLNT_ID);
CREATE INDEX IX_TB_SL_OPP_04 ON TB_SL_OPP (PROJ_ID);
CREATE INDEX IX_TB_SL_OPP_NEED_01 ON TB_SL_OPP_NEED (JOB_CD);
CREATE INDEX IX_TB_SL_TENT_ASGN_01 ON TB_SL_TENT_ASGN (OPP_ID, NEED_SN);
CREATE INDEX IX_TB_SL_TENT_ASGN_02 ON TB_SL_TENT_ASGN (DEV_ID);
CREATE INDEX IX_TB_SL_RCMD_LOG_01 ON TB_SL_RCMD_LOG (OPP_ID);
CREATE INDEX IX_TB_SL_RCMD_LOG_02 ON TB_SL_RCMD_LOG (PROJ_ID);
CREATE INDEX IX_TB_SL_RCMD_LOG_03 ON TB_SL_RCMD_LOG (DEV_ID);
CREATE INDEX IX_TB_BL_BILL_01 ON TB_BL_BILL (PROJ_ID);
CREATE INDEX IX_TB_BL_BILL_02 ON TB_BL_BILL (BILL_YM);
CREATE INDEX IX_TB_BL_BILL_03 ON TB_BL_BILL (PAY_DUE_DT);
CREATE INDEX IX_TB_BL_BILL_04 ON TB_BL_BILL (CNCL_USER_ID);
CREATE INDEX IX_TB_BL_RCPT_01 ON TB_BL_RCPT (BILL_ID);
CREATE INDEX IX_TB_BL_RCPT_02 ON TB_BL_RCPT (CNCL_USER_ID);
CREATE INDEX IX_TB_CO_MON_COST_01 ON TB_CO_MON_COST (DEV_ID);
CREATE INDEX IX_TB_CO_MON_COST_02 ON TB_CO_MON_COST (PROJ_ID);
CREATE INDEX IX_TB_CO_MON_COST_03 ON TB_CO_MON_COST (ASGN_ID);
CREATE INDEX IX_TB_CO_MON_COST_04 ON TB_CO_MON_COST (PART_ID);
CREATE INDEX IX_TB_CO_PREV_ADJ_01 ON TB_CO_PREV_ADJ (APLY_YM);
CREATE INDEX IX_TB_CO_PART_SETL_01 ON TB_CO_PART_SETL (CNFM_USER_ID);
CREATE INDEX IX_TB_CO_PART_SETL_DTL_01 ON TB_CO_PART_SETL_DTL (SETL_YM);
CREATE INDEX IX_TB_CO_PART_SETL_DTL_02 ON TB_CO_PART_SETL_DTL (DEV_ID);
CREATE INDEX IX_TB_FI_ITEM_01 ON TB_FI_ITEM (UPLD_ID);
CREATE INDEX IX_TB_FI_LOAN_01 ON TB_FI_LOAN (LOAN_DT);
CREATE INDEX IX_TB_FI_CARD_PAY_01 ON TB_FI_CARD_PAY (CARD_ID);
CREATE INDEX IX_TB_FI_XFER_01 ON TB_FI_XFER (XFER_DT);
CREATE INDEX IX_TB_FI_BIZ_PLAN_01 ON TB_FI_BIZ_PLAN (PART_ID);
CREATE INDEX IX_TB_FI_BIZ_PLAN_02 ON TB_FI_BIZ_PLAN (UPLD_ID);
CREATE INDEX IX_TB_CL_CLOSE_01 ON TB_CL_CLOSE (CLOSE_USER_ID);

-- 5. 코멘트
COMMENT ON TABLE TB_SY_USER IS '사용자';
COMMENT ON COLUMN TB_SY_USER.USER_ID IS '사용자ID: 로그인 아이디(예: fin01). 중복 불가';
COMMENT ON COLUMN TB_SY_USER.USER_NM IS '사용자명';
COMMENT ON COLUMN TB_SY_USER.EMAIL_ADDR IS '이메일주소: 임시 비밀번호·알림 발송';
COMMENT ON COLUMN TB_SY_USER.PWD_HASH IS '비밀번호해시: 단방향 해시 BCrypt(강도 10, 60자)(D-8)';
COMMENT ON COLUMN TB_SY_USER.PWD_CHG_DTTM IS '비밀번호변경일시: 90일 변경 안내 기준';
COMMENT ON COLUMN TB_SY_USER.TEMP_PWD_YN IS '임시비밀번호여부: Y면 첫 로그인 때 변경 강제';
COMMENT ON COLUMN TB_SY_USER.PWD_ERR_CNT IS '비밀번호오류횟수: 기본 0, 5회면 잠김';
COMMENT ON COLUMN TB_SY_USER.USER_STAT_CD IS '사용자상태코드: USE 사용 / LOCK 잠김 / STOP 중지';
COMMENT ON COLUMN TB_SY_USER.LAST_LOGIN_DTTM IS '마지막접속일시: UI-23 목록';
COMMENT ON COLUMN TB_SY_USER.MFA_USE_YN IS '2단계인증사용여부: 시스템 관리자 역할이 있으면 자동 Y(이메일 인증), 역할을 빼면 N(2026-10-29)';
COMMENT ON COLUMN TB_SY_USER.MFA_CODE_HASH IS '2단계인증번호해시: 이메일로 보낸 6자리 숫자의 단방향 해시. 다시 보내면 새 값으로 덮어써 이전 번호는 못 씀, 성공하면 NULL(2026-10-29 A25)';
COMMENT ON COLUMN TB_SY_USER.MFA_EXPR_DTTM IS '2단계인증만료일시: 보낸 시각 + 5분. 지나면 [다시 보내기] 안내';
COMMENT ON COLUMN TB_SY_USER.MFA_ERR_CNT IS '2단계인증오류횟수: 기본 0. 5번 틀리면 로그인 처음으로(계정 잠금 PWD_ERR_CNT와 따로), 비밀번호 로그인으로 새 번호를 보낼 때 0';
COMMENT ON COLUMN TB_SY_USER.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_USER.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_USER.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_USER.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_ROLE IS '역할';
COMMENT ON COLUMN TB_SY_ROLE.ROLE_ID IS '역할ID: 기본 5개 ADMIN, EXEC, FIN, SALES_LDR, PM. 새로 만든 역할은 R-nnn 순번 자동(R-001부터, 지운 번호 재사용 안 함, 2026-10-29)';
COMMENT ON COLUMN TB_SY_ROLE.ROLE_NM IS '역할명: 시스템 관리자 / 경영진 / 재무 담당 / 영업 파트장 / 일반 사용자(PM)';
COMMENT ON COLUMN TB_SY_ROLE.DATA_SCOPE_CD IS '데이터범위코드: ALL 전체 / PART 담당 파트 / PROJ 담당 프로젝트 (SELF 본인은 뺌, N-24-1)';
COMMENT ON COLUMN TB_SY_ROLE.SNSTV_VIEW_YN IS '민감항목보기여부: 급여·지급 단가·개인별 원가·계좌';
COMMENT ON COLUMN TB_SY_ROLE.SYS_ROLE_YN IS '시스템역할여부: 기본 5개 역할은 Y(삭제 불가)';
COMMENT ON COLUMN TB_SY_ROLE.SORT_ORD IS '정렬순서: 권한 화면 역할 탭 순서. 기본 5개는 1~5(시스템 관리자·경영진·재무 담당·영업 파트장·일반 사용자(PM)), 새로 만든 역할은 그때 가장 큰 번호 + 1. 바꾸는 화면 없음. 사용 중지해도 번호는 그대로(2026-10-30 A28)';
COMMENT ON COLUMN TB_SY_ROLE.USE_YN IS '사용여부: N = 사용 중지(추가한 역할만, 시스템 관리자만, 그 역할 사용자가 없을 때). 중지 역할은 역할 탭·사용자 등록 목록에서 숨김(2026-10-29)';
COMMENT ON COLUMN TB_SY_ROLE.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_ROLE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_ROLE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_ROLE.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_USER_ROLE IS '사용자역할';
COMMENT ON COLUMN TB_SY_USER_ROLE.USER_ID IS '사용자ID: → TB_SY_USER';
COMMENT ON COLUMN TB_SY_USER_ROLE.ROLE_ID IS '역할ID: → TB_SY_ROLE. 1명당 1개 이상(Q-23-1 사용자 결정 2026-10-19: 여러 개 허용)';
COMMENT ON COLUMN TB_SY_USER_ROLE.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_USER_ROLE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_USER_ROLE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_USER_ROLE.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_USER_SCOPE IS '사용자담당범위';
COMMENT ON COLUMN TB_SY_USER_SCOPE.USER_ID IS '사용자ID: → TB_SY_USER';
COMMENT ON COLUMN TB_SY_USER_SCOPE.SCOPE_SN IS '범위일련번호';
COMMENT ON COLUMN TB_SY_USER_SCOPE.SCOPE_TYPE_CD IS '범위유형코드: PART / PROJ';
COMMENT ON COLUMN TB_SY_USER_SCOPE.PART_ID IS '영업파트ID: SCOPE_TYPE_CD=PART일 때. ''HQ''(본사 영업 책임자)만 저장. 영업 파트의 파트장 범위는 TB_BS_PART_STD.PART_LDR_USER_ID(이번 달 줄)에서 읽음(2026-10-29)';
COMMENT ON COLUMN TB_SY_USER_SCOPE.PROJ_ID IS '프로젝트ID: SCOPE_TYPE_CD=PROJ일 때. PM 역할 사용자의 담당 프로젝트 = 그 프로젝트의 PM(프로젝트당 1명, 35번 3)';
COMMENT ON COLUMN TB_SY_USER_SCOPE.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_USER_SCOPE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_USER_SCOPE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_USER_SCOPE.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_MENU IS '메뉴';
COMMENT ON COLUMN TB_SY_MENU.MENU_ID IS '메뉴ID: 시스템이 자동으로 매김(입력칸 없음). 대메뉴 G001·G002…, 서브메뉴 M001·M002… 순번. 한 번 정하면 바뀌지 않음(2026-10-29 사용자 결정)';
COMMENT ON COLUMN TB_SY_MENU.UPPER_MENU_ID IS '상위메뉴ID: NULL이면 메뉴 묶음';
COMMENT ON COLUMN TB_SY_MENU.MENU_NM IS '메뉴명: 메뉴 화면에서 관리자가 수정 가능(Q-25-1)';
COMMENT ON COLUMN TB_SY_MENU.SCRN_ID IS '화면ID: 화면 번호(UI-nn). React 화면(라우트)과 짝. 묶음은 NULL';
COMMENT ON COLUMN TB_SY_MENU.MENU_LVL IS '메뉴레벨: 1 묶음(대메뉴) / 2 화면(서브메뉴). 등록할 때 고르고, 2면 상위 묶음을 고른다';
COMMENT ON COLUMN TB_SY_MENU.SORT_ORD IS '정렬순서: 끌어서 변경';
COMMENT ON COLUMN TB_SY_MENU.USE_YN IS '사용여부: N이면 모든 사용자에게 숨김(데이터는 유지)';
COMMENT ON COLUMN TB_SY_MENU.PC_ONLY_YN IS 'PC전용여부: 메뉴 화면의 “PC 전용” 체크(SFR-125-03). Y면 휴대폰 메뉴·즐겨찾기·최근에서 빠지고 주소로 직접 열어도 막는다(SFR-019-02). 초기값 UI-10·14·23·24·25·27 = Y. 휴대폰 표시 컬럼은 이 컬럼 하나로 합쳤다(2026-10-19)';
COMMENT ON COLUMN TB_SY_MENU.ICON_NM IS '아이콘명: 대메뉴만. 처음 들어 있는 대메뉴 10개는 시안 아이콘(chart·folder·wallet 등), 새로 등록한 대메뉴는 folder 고정(고르는 기능 없음). 서브메뉴는 NULL(2026-10-29 사용자 결정)';
COMMENT ON COLUMN TB_SY_MENU.LOCK_YN IS '끄기금지여부: 시스템 묶음 메뉴 전체(사용자·권한·메뉴·공통코드) = Y (Q-25-3 사용자 결정 2026-10-21). Y면 끄기 금지 + 다른 묶음으로 이동 금지(Q-25-2)';
COMMENT ON COLUMN TB_SY_MENU.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_MENU.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_MENU.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_MENU.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_ROLE_MENU_AUTH IS '역할메뉴권한';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.ROLE_ID IS '역할ID: → TB_SY_ROLE';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.MENU_ID IS '메뉴ID: → TB_SY_MENU (화면 메뉴만)';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.INQ_YN IS '조회여부: 새 메뉴는 모든 역할 N으로 생성';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.REG_YN IS '등록여부';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.UPD_YN IS '수정여부';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.DEL_YN IS '삭제여부: 사용중지 포함';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.UPLD_YN IS '업로드여부';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.DNLD_YN IS '다운로드여부';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.CNFM_YN IS '확정여부: 월 마감·파트 정산 확정·견적 승인(Q-공통-9 사용자 결정 2026-10-22. 청구 확정은 v2라 2026-10-30 D19에서 뺌)';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_ROLE_MENU_AUTH.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_CODE_GRP IS '공통코드그룹';
COMMENT ON COLUMN TB_SY_CODE_GRP.CODE_GRP_ID IS '코드그룹ID: 예: CTRT_TYPE. 새 그룹은 [그룹 추가]에서 직접 입력: 영문 대문자로 시작, 영문 대문자·숫자·_ 30자까지, 중복 불가, 만든 뒤 변경 불가(2026-10-29 A23)';
COMMENT ON COLUMN TB_SY_CODE_GRP.CODE_GRP_NM IS '코드그룹명';
COMMENT ON COLUMN TB_SY_CODE_GRP.SYS_CODE_YN IS '시스템코드여부: Y면 이름만 수정, 추가·삭제·사용끄기 금지. 명세서 공통-16의 시스템 코드 그룹 11개 + 프로젝트 상태·거래처 구분·데이터 범위 = 14개(Q-27-2 결정 2026-10-21, 단가 기준 2026-10-27 삭제) + 테이블 코드 칸 고정값 30개(2026-10-28, 2026-10-29 자금수지 3개 삭제, 2026-10-30 XFER_CLS 추가) = 44개(5장 시스템 여부 Y)';
COMMENT ON COLUMN TB_SY_CODE_GRP.VAL_CODE_YN IS '값코드여부: Y면 CODE_VAL·UNIT 사용(수당코드)';
COMMENT ON COLUMN TB_SY_CODE_GRP.SORT_ORD IS '정렬순서';
COMMENT ON COLUMN TB_SY_CODE_GRP.USE_YN IS '사용여부';
COMMENT ON COLUMN TB_SY_CODE_GRP.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_CODE_GRP.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_CODE_GRP.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_CODE_GRP.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_CODE IS '공통코드';
COMMENT ON COLUMN TB_SY_CODE.CODE_GRP_ID IS '코드그룹ID: → TB_SY_CODE_GRP';
COMMENT ON COLUMN TB_SY_CODE.CODE IS '코드: 그룹 안 중복 불가';
COMMENT ON COLUMN TB_SY_CODE.CODE_NM IS '코드명: 표에서 바로 수정';
COMMENT ON COLUMN TB_SY_CODE.SORT_ORD IS '정렬순서';
COMMENT ON COLUMN TB_SY_CODE.USE_YN IS '사용여부: 쓴 코드는 삭제 대신 N';
COMMENT ON COLUMN TB_SY_CODE.DEL_YN IS '삭제여부: 기본 N. 한 번도 쓰지 않은 업무 코드를 UI-27 [삭제]하면 Y(행은 남기고 코드 표·선택 목록에서 뺌, 같은 코드 값 재사용 불가). 쓴 코드는 Y로 못 바꿈(2026-11-01 사용자 결정 16/18, 04 SFR-127-05)';
COMMENT ON COLUMN TB_SY_CODE.CODE_VAL IS '코드값: 수당코드: 200000, 9.4 등. 「수당코드 추가」 창의 처음 값 0(2026-10-31 필수 표시)';
COMMENT ON COLUMN TB_SY_CODE.UNIT_NM IS '단위명: 원 / %';
COMMENT ON COLUMN TB_SY_CODE.ADD_VAL1 IS '추가값1: 그룹마다 뜻이 다름(JOB=관련 직무, SKILL=필요한 직무, PROJ_STEP=비중, BANK=은행 코드, RSM_LABEL=넣을 칸, RSM_JOB_KW=직무, 2026-11-03). 이 여섯 그룹에선 코드 추가 때 필수(화면에서 검사), 다른 그룹은 비움(2026-10-31 필수 표시)';
COMMENT ON COLUMN TB_SY_CODE.ADD_VAL2 IS '추가값2: 그룹마다 뜻이 다름(JOB=견적 기준 단가, PROJ_STEP=적용 계약유형). 이 두 그룹에선 코드 추가 때 필수(화면에서 검사)(2026-10-31 필수 표시)';
COMMENT ON COLUMN TB_SY_CODE.ADD_VAL3 IS '추가값3: 예비. 지금 쓰는 그룹 없음';
COMMENT ON COLUMN TB_SY_CODE.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_CODE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_CODE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_CODE.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_CODE_MAP IS '엑셀표기대응';
COMMENT ON COLUMN TB_SY_CODE_MAP.MAP_ID IS '대응ID: 시퀀스';
COMMENT ON COLUMN TB_SY_CODE_MAP.SRC_TEXT IS '엑셀표기: 예: ‘업체’, ‘정직원’';
COMMENT ON COLUMN TB_SY_CODE_MAP.CODE_GRP_ID IS '코드그룹ID';
COMMENT ON COLUMN TB_SY_CODE_MAP.CODE IS '코드: 바꿔 읽을 코드';
COMMENT ON COLUMN TB_SY_CODE_MAP.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_CODE_MAP.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_CODE_MAP.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_CODE_MAP.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_OPER_STD IS '운영기준값';
COMMENT ON COLUMN TB_SY_OPER_STD.STD_CD IS '기준값코드: §6 초기값 표. 값 하나에 코드 하나(화면에서 한 줄에 칸이 여러 개인 묶음 값도 칸마다 코드 따로, 2026-10-29)';
COMMENT ON COLUMN TB_SY_OPER_STD.APLY_BGN_YM IS '적용시작연월: 이력 관리';
COMMENT ON COLUMN TB_SY_OPER_STD.STD_NM IS '기준값명';
COMMENT ON COLUMN TB_SY_OPER_STD.STD_VAL IS '기준값: 숫자 하나. 연월(CASH_BASE_YM)은 YYYYMM 숫자(202601)';
COMMENT ON COLUMN TB_SY_OPER_STD.UNIT_NM IS '단위명: %, %p, 원, 일, 명, 개월, MM, 회, 분, 점, 연월';
COMMENT ON COLUMN TB_SY_OPER_STD.STD_DESC IS '기준값설명: 화면 「쓰는 곳」 칸(예: 식-89)';
COMMENT ON COLUMN TB_SY_OPER_STD.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_OPER_STD.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_OPER_STD.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_OPER_STD.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_USER_PREF IS '사용자화면설정';
COMMENT ON COLUMN TB_SY_USER_PREF.USER_ID IS '사용자ID';
COMMENT ON COLUMN TB_SY_USER_PREF.PREF_TYPE_CD IS '설정유형코드: FAV 즐겨찾기 / RECENT 최근 / COLS 열 설정 / PAGE_SIZE / AMT_UNIT / MENU_FOLD / DENSE / PERIOD';
COMMENT ON COLUMN TB_SY_USER_PREF.PREF_KEY IS '설정키: 화면ID 또는 ''*''. COLS 열 설정은 화면ID(화면마다), PAGE_SIZE·DENSE는 ''*''(계정에 한 값, 02 SFR-010-04·05, 2026-10-31 C22)';
COMMENT ON COLUMN TB_SY_USER_PREF.PREF_VAL IS '설정값: JSON 문자열';
COMMENT ON COLUMN TB_SY_USER_PREF.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_USER_PREF.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_USER_PREF.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_USER_PREF.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_NOTI IS '알림';
COMMENT ON COLUMN TB_SY_NOTI.NOTI_ID IS '알림ID: 시퀀스';
COMMENT ON COLUMN TB_SY_NOTI.RCVR_USER_ID IS '수신사용자ID';
COMMENT ON COLUMN TB_SY_NOTI.NOTI_TYPE_CD IS '알림유형코드: DUN 미수 / WTHDR / CASH_SHORT / UPLD_DONE / AI_DONE / QUOTE_REJ (6종, Q-공통-7)';
COMMENT ON COLUMN TB_SY_NOTI.NOTI_CN IS '알림내용';
COMMENT ON COLUMN TB_SY_NOTI.LINK_SCRN_ID IS '연결화면ID: 누르면 이동';
COMMENT ON COLUMN TB_SY_NOTI.LINK_KEY IS '연결키: 청구번호, 프로젝트ID 등';
COMMENT ON COLUMN TB_SY_NOTI.SEND_MTHD_CD IS '발송방법코드: SYS / MAIL / BOTH';
COMMENT ON COLUMN TB_SY_NOTI.READ_YN IS '읽음여부';
COMMENT ON COLUMN TB_SY_NOTI.READ_DTTM IS '읽은일시';
COMMENT ON COLUMN TB_SY_NOTI.NOTI_DTTM IS '알림일시: 30일 지나면 삭제 배치';
COMMENT ON COLUMN TB_SY_NOTI.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_NOTI.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_NOTI.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_NOTI.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_CHG_HIST IS '변경이력';
COMMENT ON COLUMN TB_SY_CHG_HIST.HIST_ID IS '이력ID: 시퀀스';
COMMENT ON COLUMN TB_SY_CHG_HIST.TRGT_TBL_NM IS '대상테이블명: TB_BS_CLNT, TB_HR_DEV, TB_SY_CODE, TB_SY_USER, TB_SY_ROLE_MENU_AUTH, TB_SY_OPER_STD 등';
COMMENT ON COLUMN TB_SY_CHG_HIST.TRGT_KEY IS '대상키: 대상 행의 PK 문자열';
COMMENT ON COLUMN TB_SY_CHG_HIST.CHG_TYPE_CD IS '변경유형코드: 공통코드 CHG_TYPE: NEW 신규 등록 / UPD 수정 / STOP 사용중지 / RESUME 다시 사용 / DEL 삭제(엑셀 표기 대응 줄 삭제). 퇴사 취소는 UPD(항목 「퇴사 취소」, CHG_RSN = 취소 사유)(2026-10-31 C1)';
COMMENT ON COLUMN TB_SY_CHG_HIST.CHG_ITEM_NM IS '변경항목명: 화면 표시용 한글 항목명';
COMMENT ON COLUMN TB_SY_CHG_HIST.BFR_VAL IS '변경전값';
COMMENT ON COLUMN TB_SY_CHG_HIST.AFT_VAL IS '변경후값';
COMMENT ON COLUMN TB_SY_CHG_HIST.CHG_RSN IS '변경사유';
COMMENT ON COLUMN TB_SY_CHG_HIST.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_CHG_HIST.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_SY_ACCS_LOG IS '접근기록';
COMMENT ON COLUMN TB_SY_ACCS_LOG.LOG_ID IS '로그ID: 시퀀스';
COMMENT ON COLUMN TB_SY_ACCS_LOG.USER_ID IS '사용자ID';
COMMENT ON COLUMN TB_SY_ACCS_LOG.ACT_TYPE_CD IS '작업유형코드: LOGIN / LOGOUT / LOGIN_FAIL / DNLD / CLOSE / UNCLOSE / AUTH_CHG / USER_REG / SNSTV_VIEW / ACCT_VIEW (비밀번호 초기화·잠금 해제는 남기지 않음, Q-23-3)';
COMMENT ON COLUMN TB_SY_ACCS_LOG.SCRN_ID IS '화면ID';
COMMENT ON COLUMN TB_SY_ACCS_LOG.TRGT_DESC IS '대상설명: 예: “개인 원가·수익표”, “2026-09”';
COMMENT ON COLUMN TB_SY_ACCS_LOG.CLIENT_IP IS '접속IP';
COMMENT ON COLUMN TB_SY_ACCS_LOG.ACT_DTTM IS '작업일시: 보관 3년(D-26)';
COMMENT ON COLUMN TB_SY_ACCS_LOG.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_ACCS_LOG.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_SY_UPLD IS '엑셀업로드';
COMMENT ON COLUMN TB_SY_UPLD.UPLD_ID IS '업로드ID';
COMMENT ON COLUMN TB_SY_UPLD.FORM_TYPE_CD IS '양식유형코드: B1 급여 / B2 재무항목 / B5 투입목록 / B6 턴키배분표 / B7 거래처 / B8 개발자 / B9 프로젝트 / B10 사업계획 / B11 전년손익';
COMMENT ON COLUMN TB_SY_UPLD.SCRN_ID IS '화면ID';
COMMENT ON COLUMN TB_SY_UPLD.FILE_NM IS '파일명: xlsx만';
COMMENT ON COLUMN TB_SY_UPLD.FILE_SIZE IS '파일크기: 10MB 제한(Q-공통-20 사용자 결정 2026-10-22)';
COMMENT ON COLUMN TB_SY_UPLD.TRGT_YM IS '대상연월: 한 달 양식';
COMMENT ON COLUMN TB_SY_UPLD.TRGT_PROJ_ID IS '대상프로젝트ID: B6';
COMMENT ON COLUMN TB_SY_UPLD.TRGT_DSTRB_STEP_CD IS '대상배분단계코드: B6. 올릴 때 고른 단계(EST 견적 / CTRT 최종 계약 / PLAN 실제 투입계획). 같은 단계를 다시 올리면 배분표 줄은 바뀌어도 「올린 기록」에서 예전 업로드의 단계를 보여 주려고 둠(2026-10-29 테이블 검증 2장 B4)';
COMMENT ON COLUMN TB_SY_UPLD.TOT_ROW_CNT IS '전체행수: 5,000행 제한';
COMMENT ON COLUMN TB_SY_UPLD.SUCC_CNT IS '성공건수';
COMMENT ON COLUMN TB_SY_UPLD.WARN_CNT IS '경고건수';
COMMENT ON COLUMN TB_SY_UPLD.ERR_CNT IS '오류건수';
COMMENT ON COLUMN TB_SY_UPLD.UPLD_STAT_CD IS '업로드상태코드: CHECKED 검사완료(반영 안 하고 창을 닫으면 이 상태로 남음) / APPLIED 반영 / CANCEL. 검사를 통과한 줄은 따로 두지 않고 [반영] 때 같은 파일을 다시 검사해 저장(02 SFR-012-08, 2026-10-31 C21)';
COMMENT ON COLUMN TB_SY_UPLD.APLY_DTTM IS '반영일시';
COMMENT ON COLUMN TB_SY_UPLD.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_UPLD.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_UPLD.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_UPLD.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_UPLD_ERR IS '엑셀업로드검사결과';
COMMENT ON COLUMN TB_SY_UPLD_ERR.UPLD_ID IS '업로드ID';
COMMENT ON COLUMN TB_SY_UPLD_ERR.RSLT_SN IS '결과일련번호';
COMMENT ON COLUMN TB_SY_UPLD_ERR.RSLT_TYPE_CD IS '결과유형코드: WARN / ERR';
COMMENT ON COLUMN TB_SY_UPLD_ERR.ROW_NO IS '행번호: 엑셀 행';
COMMENT ON COLUMN TB_SY_UPLD_ERR.COL_NM IS '열이름';
COMMENT ON COLUMN TB_SY_UPLD_ERR.INPUT_VAL IS '입력값';
COMMENT ON COLUMN TB_SY_UPLD_ERR.RSN_CN IS '사유내용: 예: “’정직원’을 ’정규직’으로 바꿔 읽었습니다”';
COMMENT ON COLUMN TB_SY_UPLD_ERR.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_UPLD_ERR.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_UPLD_ERR.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_UPLD_ERR.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_AI_QNA IS 'AI질문기록';
COMMENT ON COLUMN TB_SY_AI_QNA.QNA_ID IS '질의응답ID';
COMMENT ON COLUMN TB_SY_AI_QNA.USER_ID IS '사용자ID';
COMMENT ON COLUMN TB_SY_AI_QNA.QSTN_CN IS '질문내용';
COMMENT ON COLUMN TB_SY_AI_QNA.ANS_CN IS '답변내용: 결론·표·가정';
COMMENT ON COLUMN TB_SY_AI_QNA.USE_DATA_DESC IS '사용데이터설명: 데이터, 기간, 기준월';
COMMENT ON COLUMN TB_SY_AI_QNA.BASE_YM IS '기준연월';
COMMENT ON COLUMN TB_SY_AI_QNA.PRVS_YN IS '잠정여부: 마감 전 달 숫자면 Y';
COMMENT ON COLUMN TB_SY_AI_QNA.ANS_STAT_CD IS '답변상태코드: OK 답변(권한 밖 숫자를 빼고 답한 것 포함) / NO_ANS 답할 수 없음(데이터에 없음·원인 판단 불가·권한 밖 질문이라 답하지 않음, 화면 「이 내용을 볼 권한이 없습니다」 포함) / ERR 오류. 명세서 v1.9 「급여 없이 답하거나 답할 수 없음」(2026-10-30 C19)';
COMMENT ON COLUMN TB_SY_AI_QNA.QSTN_DTTM IS '질문일시';
COMMENT ON COLUMN TB_SY_AI_QNA.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_AI_QNA.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_AI_QNA.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_AI_QNA.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_ADM_AREA IS '행정구역';
COMMENT ON COLUMN TB_SY_ADM_AREA.AREA_CD IS '행정구역코드: 법정동코드 10자리. 시·도 단계(예: 1100000000 서울)·시·군·구 단계(1121500000 광진구)·읍·면·동 단계(자양동) 줄이 모두 있음(숫자는 설명용 예시)';
COMMENT ON COLUMN TB_SY_ADM_AREA.AREA_LVL_CD IS '단계코드: SIDO 시·도 / SGG 시·군·구 / EMD 읍·면·동';
COMMENT ON COLUMN TB_SY_ADM_AREA.UPPER_AREA_CD IS '상위행정구역코드: → TB_SY_ADM_AREA. 시·군·구는 시·도, 읍·면·동은 시·군·구(세종처럼 시·군·구가 없으면 시·도). 시·도 단계는 NULL. 화면 목록은 이 칸으로 거름';
COMMENT ON COLUMN TB_SY_ADM_AREA.SIDO_NM IS '시도명: 줄임 이름(서울·부산·경기·세종·제주 …). HOME_ADDR 맨 앞에 쓰고 근무지 점수 비교(식-70)에 쓰는 이름';
COMMENT ON COLUMN TB_SY_ADM_AREA.SIDO_FULL_NM IS '시도정식명: 파일의 정식 이름(서울특별시·경기도·강원특별자치도 …). 엑셀·근무지 글자를 줄임 이름으로 맞출 때 씀';
COMMENT ON COLUMN TB_SY_ADM_AREA.SGG_NM IS '시군구명: 예: 광진구, 성남시 분당구(두 단어도 한 값). 시·도 단계와 시·군·구가 없는 시·도는 NULL';
COMMENT ON COLUMN TB_SY_ADM_AREA.EMD_NM IS '읍면동명: 법정동 이름(예: 자양동, 조치원읍). 시·도·시·군·구 단계는 NULL';
COMMENT ON COLUMN TB_SY_ADM_AREA.USE_YN IS '사용여부: 새 파일에서 없어진 구역은 지우지 않고 N. 목록에는 Y만 나오고, 이미 저장된 거주지는 N이어도 그대로 보임';
COMMENT ON COLUMN TB_SY_ADM_AREA.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_ADM_AREA.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_ADM_AREA.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_ADM_AREA.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SY_ADM_HDONG IS '행정동변환';
COMMENT ON COLUMN TB_SY_ADM_HDONG.HDONG_CD IS '행정동코드: 행정동코드 10자리';
COMMENT ON COLUMN TB_SY_ADM_HDONG.AREA_CD IS '행정구역코드: → TB_SY_ADM_AREA. 이 행정동이 걸친 법정동(읍·면·동 단계). 행정동 하나가 법정동 여러 개에 걸치면 여러 줄';
COMMENT ON COLUMN TB_SY_ADM_HDONG.HDONG_NM IS '행정동명: 파일 표기 그대로(예: 자양제3동). 같은 시·군·구 안에서 이 이름으로 찾되 숫자 앞 「제」는 빼고 비교(자양3동 = 자양제3동, 02 SFR-021-04)';
COMMENT ON COLUMN TB_SY_ADM_HDONG.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_ADM_HDONG.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_ADM_HDONG.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_ADM_HDONG.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_BS_CLNT IS '거래처';
COMMENT ON COLUMN TB_BS_CLNT.CLNT_ID IS '거래처ID: C-001';
COMMENT ON COLUMN TB_BS_CLNT.CLNT_TYPE_CD IS '거래처구분코드: CUST 고객사 / VNDR 협력사 / BOTH 고객·협력';
COMMENT ON COLUMN TB_BS_CLNT.CLNT_NM IS '상호: 같은 상호 허용(사업자번호가 다르면 경고만, Q-15-2)';
COMMENT ON COLUMN TB_BS_CLNT.BIZ_REG_NO IS '사업자등록번호: 000-00-00000. 등록 후 변경 불가. 형식·중복만 검사, 국세청 조회 없음(Q-15-5)';
COMMENT ON COLUMN TB_BS_CLNT.CEO_NM IS '대표자명';
COMMENT ON COLUMN TB_BS_CLNT.INDUTY_CD IS '업종코드: 공통코드 INDUTY(35번 5). AI 추천 같은 업종 = 같은 코드';
COMMENT ON COLUMN TB_BS_CLNT.CHRG_NM IS '담당자명';
COMMENT ON COLUMN TB_BS_CLNT.TELNO IS '연락처';
COMMENT ON COLUMN TB_BS_CLNT.TAX_EMAIL_ADDR IS '세금계산서이메일: CUST·BOTH 필수';
COMMENT ON COLUMN TB_BS_CLNT.BANK_CD IS '은행코드: VNDR·BOTH 필수';
COMMENT ON COLUMN TB_BS_CLNT.ACCT_NO_ENC IS '계좌번호(암호화): 숫자·하이픈 8~20자 원문을 AES-256-GCM으로 암호화(IV·암호문·인증 태그를 Base64로 저장, 약 64자). 키는 서버 환경변수(D-8). 화면은 끝 4자리';
COMMENT ON COLUMN TB_BS_CLNT.DPSTR_NM IS '예금주명: VNDR·BOTH 필수';
COMMENT ON COLUMN TB_BS_CLNT.USE_YN IS '사용여부: 진행 프로젝트·소속 인력 있으면 N 불가';
COMMENT ON COLUMN TB_BS_CLNT.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_BS_CLNT.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_BS_CLNT.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_BS_CLNT.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_BS_PART IS '영업파트';
COMMENT ON COLUMN TB_BS_PART.PART_ID IS '영업파트ID: ''HQ''는 본사(직접 영업) 예약';
COMMENT ON COLUMN TB_BS_PART.PART_NM IS '영업파트명: 같은 이름 불가. 바꾸면 화면 표시는 모두 바뀜(ID 참조)';
COMMENT ON COLUMN TB_BS_PART.HQ_YN IS '본사직접영업여부: Y면 파트 이익금 정산 제외';
COMMENT ON COLUMN TB_BS_PART.USE_YN IS '사용여부: 소속 인력·진행 프로젝트 있으면 N 불가';
COMMENT ON COLUMN TB_BS_PART.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_BS_PART.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_BS_PART.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_BS_PART.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_BS_PART_STD IS '영업파트기준이력';
COMMENT ON COLUMN TB_BS_PART_STD.PART_ID IS '영업파트ID';
COMMENT ON COLUMN TB_BS_PART_STD.APLY_BGN_YM IS '적용시작연월: 마감월 다음 달 이후만. 이익 배분율·공통관리비가 바뀌는 줄은 이 달 이후에 TB_CO_PART_SETL.SETL_STAT_CD=CNFM인 달이 있으면 저장 불가(확정 취소 후 변경). 정산 테이블에 배분율을 따로 저장하지 않으므로 확정된 달의 머리글 배분율과 고정 이익금이 어긋나지 않게 함(2026-10-29 D14)';
COMMENT ON COLUMN TB_BS_PART_STD.APLY_END_YM IS '적용종료연월: 다음 기준 등록 시 자동 설정, 현재 기준은 NULL';
COMMENT ON COLUMN TB_BS_PART_STD.PART_LDR_USER_ID IS '파트장사용자ID: 사용 중이고 영업 파트장 역할인 사용자. 한 사람이 여러 파트 가능(Q-15-3). 이번 달 줄의 파트장 = 그 파트의 데이터 범위·미수 2단계 대상(2026-10-29)';
COMMENT ON COLUMN TB_BS_PART_STD.PRFT_DSTRB_RT IS '이익배분율: 0~100, 기본 40(예시값, 파트마다 다름, Q-15-6)';
COMMENT ON COLUMN TB_BS_PART_STD.SALES_EXPS_AMT IS '공통관리비금액: 1인·월, 기본 1,500,000(예시값, 파트마다 다름)';
COMMENT ON COLUMN TB_BS_PART_STD.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_BS_PART_STD.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_BS_PART_STD.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_BS_PART_STD.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_DEV IS '개발자';
COMMENT ON COLUMN TB_HR_DEV.DEV_ID IS '개발자ID: 내부 키';
COMMENT ON COLUMN TB_HR_DEV.MNG_NO IS '관리번호: K-0001. 비우면 자동(이미 쓰는 번호는 건너뜀), 직접 넣으면 영문·숫자·하이픈 20자 이내. 등록 후 수정 불가';
COMMENT ON COLUMN TB_HR_DEV.DEV_NM IS '개발자명';
COMMENT ON COLUMN TB_HR_DEV.EMP_TYPE_CD IS '직원구분코드: FT 정규직 / CT 계약직 / FL 프리랜서 / OS 외주 (시스템)';
COMMENT ON COLUMN TB_HR_DEV.BLNG_CLNT_ID IS '소속거래처ID: NULL = 자사, 외주는 협력사 필수';
COMMENT ON COLUMN TB_HR_DEV.GRADE_CD IS '등급코드: JR 초급 / MD 중급 / SR 고급 / EX 특급';
COMMENT ON COLUMN TB_HR_DEV.JOB_CD IS '주직무코드: PM, PL, PLN, ARC, BE, FE, UX, PUB, QA, DATA, OPS, AI';
COMMENT ON COLUMN TB_HR_DEV.SUB_JOB_CD IS '보조직무코드: 등록 화면 선택 입력(Q-02-3), AI 추천 직무 일치(식-70)';
COMMENT ON COLUMN TB_HR_DEV.JBPS_CD IS '직위코드: MEMBER 팀원 / TEAM 팀장 / GROUP 그룹장 (수당 계산). 등록 화면 입력, 기본 팀원(Q-02-3)';
COMMENT ON COLUMN TB_HR_DEV.DRCT_CLS_CD IS '직간접구분코드: DRCT 직접 / INDRCT 간접(관리직). 등록 화면 입력, 기본 DRCT(Q-02-5). 간접은 판관비';
COMMENT ON COLUMN TB_HR_DEV.PART_ID IS '영업파트ID: 파트 정산 집계 기준. 필수(화면·업로드). ''HQ'' = 본사(직접 영업), 파트 정산 제외. 배정 때 프로젝트 파트와 다르면 경고만(N-02-3)';
COMMENT ON COLUMN TB_HR_DEV.JNCMP_DT IS '입사일자';
COMMENT ON COLUMN TB_HR_DEV.RETIRE_DT IS '퇴사일자: **복사값**: 원본은 TB_HR_DEV_STAT의 RETIRE 줄 BGN_DT. 이 칸을 따로 고치는 화면·업로드는 없고, 퇴사 등록 때 같은 날짜로 넣고 퇴사 취소 때 NULL로 같이 바꿈. 두 값이 다르면 STAT 기준으로 맞춤. 가동률 등 계산이 이력을 뒤지지 않고 바로 읽으려고 둠(2026-10-29). 개발자 상세 [근무 상태 이력]에서 퇴사 등록 시 저장. 지나면 RETIRE·USE_YN=N(다시 사용 불가). 퇴사 취소 시 NULL. 가동률·인력 공급은 이 날짜의 다음 달부터 뺌(2026-10-27 사용자 ';
COMMENT ON COLUMN TB_HR_DEV.WORK_STAT_CD IS '근무상태코드: INPUT 투입중 / WTHDR 철수예정 / IDLE 대기 / LEAVE 휴직 / RETIRE 퇴사 (배치 갱신, Q-02-1 사용자 결정: 자동)';
COMMENT ON COLUMN TB_HR_DEV.HOME_AREA_CD IS '거주지행정구역코드: → TB_SY_ADM_AREA. 고른 마지막 단계의 코드(동까지 고르면 동, 시·군·구까지면 시·군·구, 시·도만이면 시·도). [수정]은 이 코드로 시·도·시·군·구·동 목록을 다시 채움(HOME_ADDR을 잘라 쓰지 않음). 거주지가 없으면 NULL(02 SFR-021, 2026-10-31 C9)';
COMMENT ON COLUMN TB_HR_DEV.HOME_ADDR IS '거주지: 시·도 + 시·군·구 + 동(예: 서울 강남구 역삼동, 동은 선택, 2026-10-27). 개발자 풀 등록·수정·엑셀 업로드에서 입력(Q-공통-12). HOME_AREA_CD로 만든 보여 주기용 글자: 시·도 줄임 이름 + 시·군·구 + 법정동을 띄어 써서 이음(「경기 성남시 분당구 정자동」, 「서울 광진구」, C9). 근무지 점수는 앞의 시·도로 비교(식-70)';
COMMENT ON COLUMN TB_HR_DEV.TELNO IS '연락처: 등록 화면에서 받지 않음(Q-02-3). 엑셀 이관 값만 보관';
COMMENT ON COLUMN TB_HR_DEV.USE_YN IS '사용여부';
COMMENT ON COLUMN TB_HR_DEV.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_DEV.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_DEV.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_DEV.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_DEV_SKILL IS '개발자보유스킬';
COMMENT ON COLUMN TB_HR_DEV_SKILL.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_HR_DEV_SKILL.SKILL_NM IS '스킬명: 쉼표로 입력한 값을 행으로 분리. 공통코드 SKILL 권장';
COMMENT ON COLUMN TB_HR_DEV_SKILL.SORT_ORD IS '정렬순서';
COMMENT ON COLUMN TB_HR_DEV_SKILL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_DEV_SKILL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_DEV_SKILL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_DEV_SKILL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_DEV_PRC IS '개발자단가급여이력';
COMMENT ON COLUMN TB_HR_DEV_PRC.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_HR_DEV_PRC.APLY_BGN_YM IS '적용시작연월: 화면에서 고르지 않고 자동(2026-10-29): 등록 때 첫 줄은 입사월(입사일이 없으면 등록한 달), 수정에서 월 단가·월 급여가 바뀌면 저장한 달로 새 줄. 같은 달에 또 바꾸면 그 달 줄을 고침. 다른 칸(등급 등)만 바꾸면 줄을 만들지 않음';
COMMENT ON COLUMN TB_HR_DEV_PRC.APLY_END_YM IS '적용종료연월: 새 줄이 생기면 앞 줄을 새 시작월의 전달로 닫음. 지금 줄은 NULL';
COMMENT ON COLUMN TB_HR_DEV_PRC.MON_UPRC IS '월단가: 고객에게 받는 기본 단가(목록 표시용)';
COMMENT ON COLUMN TB_HR_DEV_PRC.MON_PAY_AMT IS '월급여지급액: 회사가 주는 돈. 민감 항목';
COMMENT ON COLUMN TB_HR_DEV_PRC.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_DEV_PRC.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_DEV_PRC.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_DEV_PRC.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_DEV_STAT IS '개발자근무상태이력';
COMMENT ON COLUMN TB_HR_DEV_STAT.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_HR_DEV_STAT.STAT_SN IS '상태일련번호';
COMMENT ON COLUMN TB_HR_DEV_STAT.WORK_STAT_CD IS '근무상태코드: 직접 입력하는 상태: LEAVE 휴직 / RETIRE 퇴사(END_DT 없음) / WORK 재직(휴직 복귀, 진행 중인 휴직의 END_DT = 복귀일 전날). 개발자 상세 [근무 상태 이력]에서 등록(N-02-1, Q-28-5, 퇴사·재직 2026-10-27 사용자 결정)';
COMMENT ON COLUMN TB_HR_DEV_STAT.BGN_DT IS '시작일자: 휴직=시작일, 재직=복귀일, 퇴사=퇴사일. RETIRE 줄의 BGN_DT가 퇴사일 원본(TB_HR_DEV.RETIRE_DT는 복사값, 2026-10-29)';
COMMENT ON COLUMN TB_HR_DEV_STAT.END_DT IS '종료일자: NULL=복직일 미정. [휴직 종료] 시 실제 복직일로 변경';
COMMENT ON COLUMN TB_HR_DEV_STAT.STAT_RSN IS '상태사유: 예: 육아휴직';
COMMENT ON COLUMN TB_HR_DEV_STAT.CLOSE_LEAVE_SN IS '닫은휴직일련번호: WORK·RETIRE 줄만. 이 줄을 등록하면서 자동으로 닫은 휴직 줄의 STAT_SN(같은 DEV_ID). 닫은 휴직이 없으면 NULL. 아래 「휴직 닫기·되돌리기 규칙」';
COMMENT ON COLUMN TB_HR_DEV_STAT.ORGNL_LEAVE_END_DT IS '원래휴직종료일자: WORK·RETIRE 줄만. 닫기 직전 그 휴직 줄의 END_DT(NULL이면 복직일 미정이었음). 이 줄을 취소할 때 되돌릴 값';
COMMENT ON COLUMN TB_HR_DEV_STAT.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_DEV_STAT.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_DEV_STAT.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_DEV_STAT.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_MON_PAY IS '월별급여원가 - 부록 B.1 업로드';
COMMENT ON COLUMN TB_HR_MON_PAY.PAY_YM IS '급여연월: 같은 연월 재업로드 시 덮어씀, 마감월 불가';
COMMENT ON COLUMN TB_HR_MON_PAY.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_HR_MON_PAY.PAY_AMT IS '급여금액: 프리랜서는 지급액(원천세 차감 전)';
COMMENT ON COLUMN TB_HR_MON_PAY.ALWNC_AMT IS '수당금액: 직위 수당(식-08). 엑셀 칸 아님: 업로드 때 공통코드(ALLOW_TEAM·ALLOW_GROUP)와 그 달 직위로 계산해 저장, 코드를 고치면 마감 안 된 달은 다시 계산(C4)';
COMMENT ON COLUMN TB_HR_MON_PAY.INSR_CMPNY_AMT IS '4대보험회사부담금액: 업로드값 우선, 없으면 INS_RATE';
COMMENT ON COLUMN TB_HR_MON_PAY.INSR_SELF_AMT IS '4대보험본인부담금액: 급여 업로드 값. 개인 원가·수익표(UI-03)에서는 참고 열(원가 아님). 실지급액(식-17)과 자금수지표 제경비(다음 달, 식-48)에 씀. 비어 있으면 0으로 봄(프리랜서·외주는 원래 없음, 2026-10-30 E4)';
COMMENT ON COLUMN TB_HR_MON_PAY.RETIRE_RSV_AMT IS '퇴직금적립금액: 정규직만(식-09). 엑셀 칸 아님: 업로드 때 (급여 + 수당) × SEV_RATE로 계산해 저장, 코드를 고치면 마감 안 된 달은 다시 계산(C4)';
COMMENT ON COLUMN TB_HR_MON_PAY.INCM_TAX_AMT IS '근로소득세금액: 급여 업로드 값. 실지급액(식-17)과 자금수지표 제경비(다음 달, 식-48)에 씀. 비어 있으면 0으로 봄(2026-10-30 E4)';
COMMENT ON COLUMN TB_HR_MON_PAY.WTHLD_TAX_AMT IS '원천세금액: 3.3%(식-17). 급여 양식에 칸 없음, 업로드 때 시스템이 계산해 저장(2026-10-31)';
COMMENT ON COLUMN TB_HR_MON_PAY.REAL_PAY_AMT IS '실지급금액: 식-17. 급여 양식에 칸 없음, 업로드 때 시스템이 계산해 저장(2026-10-31). 자금수지표 정규직·계약직 지출(2026-10-28 경비정산금액 EXPS_RFND_AMT 칸 삭제)';
COMMENT ON COLUMN TB_HR_MON_PAY.VNDR_SPLY_AMT IS '외주공급가금액: 외주 원가';
COMMENT ON COLUMN TB_HR_MON_PAY.EMP_TYPE_CD IS '직원구분코드: 업로드 때 TB_HR_DEV.EMP_TYPE_CD를 복사(엑셀 칸 아님, 그 달 기준 보존). 그 달 계산의 직원구분은 이 값: 개인 월 인건비(식-10)·배분 분모(식-13)·파트 월 비용(식-42)·자금수지 인건비 줄(식-48·49)·고정비 비율(식-64). 직원구분을 바꾸면 다음에 올리는 급여부터 새 값, 지난 달 줄은 그대로. 마감 전 같은 달을 다시 올리면 다시 복사(예: 10월까지 정규직, 11월부터 프리랜서 → 9·10월 FT, 11월 FL)(2026-10-31 C17)';
COMMENT ON COLUMN TB_HR_MON_PAY.DRCT_CLS_CD IS '직간접구분코드: 업로드 때 TB_HR_DEV.DRCT_CLS_CD를 복사(엑셀 칸 아님, 그 달 기준 보존). 간접이면 판관비(식-15)';
COMMENT ON COLUMN TB_HR_MON_PAY.UPLD_ID IS '업로드ID';
COMMENT ON COLUMN TB_HR_MON_PAY.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_MON_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_MON_PAY.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_MON_PAY.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_CAND IS '외부인력후보';
COMMENT ON COLUMN TB_HR_CAND.CAND_ID IS '후보ID';
COMMENT ON COLUMN TB_HR_CAND.CAND_NM IS '후보명';
COMMENT ON COLUMN TB_HR_CAND.EMP_TYPE_CD IS '직원구분코드: FL / OS';
COMMENT ON COLUMN TB_HR_CAND.BLNG_CLNT_ID IS '소속거래처ID: OS면 필수. 거래처 중 협력사·고객·협력(사용 중)만 고름. FL이면 비움(2026-10-25)';
COMMENT ON COLUMN TB_HR_CAND.TELNO IS '전화번호: 선택, 숫자와 -(2026-10-27). 민감';
COMMENT ON COLUMN TB_HR_CAND.EMAIL_ADDR IS '이메일주소: 선택, 형식 검사(2026-10-27). 민감';
COMMENT ON COLUMN TB_HR_CAND.PRVC_AGRE_YN IS '개인정보동의여부: 기본 N. TELNO·EMAIL_ADDR 중 하나라도 있으면 Y여야 저장(2026-10-27)';
COMMENT ON COLUMN TB_HR_CAND.PRVC_AGRE_DT IS '개인정보동의확인일자: 처음 Y로 저장한 날';
COMMENT ON COLUMN TB_HR_CAND.PRVC_AGRE_USER_ID IS '개인정보동의확인자ID: 체크한 로그인 계정(TB_SY_USER.USER_ID). 상세에는 이름으로 「동의 확인 10-27 홍길동」(맨 앞 「사용자 ID 칸 표시」, 2026-10-30 C18)';
COMMENT ON COLUMN TB_HR_CAND.JOB_CD IS '직무코드: 주 직무';
COMMENT ON COLUMN TB_HR_CAND.SUB_JOB_CD IS '보조직무코드: 선택, JOB_CD와 달라야 함. 표시만(2026-10-27)';
COMMENT ON COLUMN TB_HR_CAND.GRADE_CD IS '등급코드: 기본 MD';
COMMENT ON COLUMN TB_HR_CAND.HOME_AREA_CD IS '거주지행정구역코드: → TB_SY_ADM_AREA. 고른 마지막 단계 코드(시·군·구 또는 시·도). [수정]은 이 코드로 목록을 다시 채움(02 SFR-021, 2026-10-31 C9)';
COMMENT ON COLUMN TB_HR_CAND.HOME_ADDR IS '거주지: 시·도 + 시·군·구(예: 서울 강남구). 등록 창에서 시·도 → 시·군·구 순서로 고름(개발자 풀과 같음, 2026-10-25). HOME_AREA_CD로 만든 보여 주기용 글자(시·도 줄임 이름 + 시·군·구). AI 추천 점수에는 쓰지 않음(C9)';
COMMENT ON COLUMN TB_HR_CAND.HOPE_UPRC IS '희망단가: 프리랜서=본인 지급액, 외주=업체 지급액(부가세 별도). 민감';
COMMENT ON COLUMN TB_HR_CAND.INPUT_PSBL_DT IS '투입가능일자: 기본 오늘+30일';
COMMENT ON COLUMN TB_HR_CAND.CNTC_STAT_CD IS '연락상태코드: BEFORE 연락 전 / DONE 연락함 / ITVW 면접 예정 / HOLD 보류 / CTRT 계약 완료';
COMMENT ON COLUMN TB_HR_CAND.LAST_CNTC_DT IS '최근연락일자: 등록 때 등록일, 연락 기록 저장 때 그날(상태가 그대로여도). 정보 수정은 안 바꿈. 30일 넘게 연락 안 함 = 오늘 − 이 값 > 30일, 연락 상태 보류·계약 완료는 뺌(2026-10-31 C8)';
COMMENT ON COLUMN TB_HR_CAND.CAND_SRC_CD IS '후보출처코드: 공통코드 CAND_SRC: SITE 구인 사이트 / VNDR 협력사 추천 / ACQ 지인 추천 / PAST 과거 투입';
COMMENT ON COLUMN TB_HR_CAND.MEMO_CN IS '메모: 후보 자체 메모(등록·수정 창, 상세 「메모」). 연락할 때마다 남기는 메모는 TB_HR_CAND_CNTC.CNTC_MEMO(2026-10-29 C15)';
COMMENT ON COLUMN TB_HR_CAND.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_CAND.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_CAND.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_CAND.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_CAND_SKILL IS '후보보유스킬';
COMMENT ON COLUMN TB_HR_CAND_SKILL.CAND_ID IS '후보ID';
COMMENT ON COLUMN TB_HR_CAND_SKILL.SKILL_NM IS '스킬명: 쉼표로 입력한 값을 행으로 분리. 공통코드 SKILL 권장(개발자 보유 스킬과 같음, 2026-10-30 C16)';
COMMENT ON COLUMN TB_HR_CAND_SKILL.SORT_ORD IS '정렬순서: 입력한 순서(1부터). 목록·상세 기술 칩과 수정 창이 이 순서(2026-10-30 C16)';
COMMENT ON COLUMN TB_HR_CAND_SKILL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_CAND_SKILL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_CAND_SKILL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_CAND_SKILL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_HR_CAND_CNTC IS '후보연락기록';
COMMENT ON COLUMN TB_HR_CAND_CNTC.CAND_ID IS '후보ID';
COMMENT ON COLUMN TB_HR_CAND_CNTC.CNTC_SN IS '연락일련번호: 최신이 위';
COMMENT ON COLUMN TB_HR_CAND_CNTC.CNTC_STAT_CD IS '연락상태코드: 저장 시 후보 상태도 변경';
COMMENT ON COLUMN TB_HR_CAND_CNTC.CNTC_MEMO IS '연락메모: 필수. 등록·정보 수정 때는 시스템이 「등록」·「정보 수정」을 넣음(최근 연락일은 등록 때만 바뀜, 2026-10-31 C8)';
COMMENT ON COLUMN TB_HR_CAND_CNTC.CNTC_DT IS '연락일자: 오늘';
COMMENT ON COLUMN TB_HR_CAND_CNTC.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_HR_CAND_CNTC.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_HR_CAND_CNTC.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_HR_CAND_CNTC.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_PROJ IS '프로젝트';
COMMENT ON COLUMN TB_PJ_PROJ.PROJ_ID IS '프로젝트ID: P-2601 자동';
COMMENT ON COLUMN TB_PJ_PROJ.PROJ_NM IS '프로젝트명';
COMMENT ON COLUMN TB_PJ_PROJ.CUST_CLNT_ID IS '고객사거래처ID: CUST·BOTH만. 내부는 NULL';
COMMENT ON COLUMN TB_PJ_PROJ.CTRT_TYPE_CD IS '계약유형코드: TK 턴키 / MO 월단위 / DP 파견 / IN 내부 (시스템)';
COMMENT ON COLUMN TB_PJ_PROJ.PROJ_STAT_CD IS '프로젝트상태코드: ING 진행 / PRE 수주예정 / END 종료 / LOSE 실주. 등록·수주 처리에서 진행/수주예정을 고르고(Q-01-1), 수정 폼에서는 바꾸지 않는다. PRE일 때만 상세 [⋯]에서 ING 또는 LOSE로 바꾸고(ING→PRE 되돌리기 없음), 종료는 계약 종료일이 지나면 자동(2026-10-30 B1, 매일 02:00 배치 ③, 02 PER-004)';
COMMENT ON COLUMN TB_PJ_PROJ.LOSE_DT IS '실주처리일자: 실주 처리한 날. 화면에서 받지 않고 처리한 날(오늘)로 저장. PROJ_STAT_CD=LOSE일 때만 값(03 SFR-101-13)';
COMMENT ON COLUMN TB_PJ_PROJ.LOSE_RSN_CD IS '실주사유코드: 공통코드 LOSE_RSN(영업 기회와 같은 목록). PROJ_STAT_CD=LOSE일 때 필수';
COMMENT ON COLUMN TB_PJ_PROJ.LOSE_MEMO IS '실주메모: 실주 처리 창의 메모(선택)';
COMMENT ON COLUMN TB_PJ_PROJ.CTRT_BGN_DT IS '계약시작일자';
COMMENT ON COLUMN TB_PJ_PROJ.CTRT_END_DT IS '계약종료일자: 시작일 이후';
COMMENT ON COLUMN TB_PJ_PROJ.WORK_PLC_ADDR IS '근무지주소: 근무지 주소 전체 텍스트(예: 서울 중구 을지로 66). 내부는 NULL. AI 추천 근무지 점수는 맨 앞 시·도로 거주지와 비교(식-70). 맨 앞 단어는 TB_SY_ADM_AREA의 시·도 정식·줄임 이름으로 맞춰 줄임 이름으로 바꿔 비교(「서울특별시」·「서울시」→「서울」). 근무지는 행정구역 목록·코드를 쓰지 않음(02 SFR-021, C9). 공통코드 「근무지」 폐지(2026-10-27)';
COMMENT ON COLUMN TB_PJ_PROJ.EST_AMT IS '견적금액: 부가세 제외. 등록·수정 폼에서 입력, 수정으로 바뀌면 TB_PJ_PROJ_CHG에 ETC(견적 금액) 한 줄(2026-10-29 B9). 참고용이라 계산에 쓰지 않고 상세 화면에도 보이지 않음(2026-10-30 B12)';
COMMENT ON COLUMN TB_PJ_PROJ.ORGNL_CTRT_AMT IS '원계약금액: 식-56. 등록·수주 처리 때 CTRT_AMT 복사. 수정에서 CTRT_AMT를 바꿀 때 취소 안 된 청구(TB_BL_BILL CNCL_YN=''N'')가 하나도 없으면 같이 바꾸고(정정), 있으면 그대로(추가 계약)(2026-10-29 B11)';
COMMENT ON COLUMN TB_PJ_PROJ.CTRT_AMT IS '계약금액: 현재 계약금액(부가세 제외). 내부는 입력 선택, 비우면 0(매출 아님, Q-01-2)';
COMMENT ON COLUMN TB_PJ_PROJ.BDGT_AMT IS '예산금액: 기본 CTRT_AMT × (1 − 목표 마진율 TGT_MRGN_RT)(식-19)';
COMMENT ON COLUMN TB_PJ_PROJ.BDGT_MANUAL_YN IS '예산수기입력여부: Y면 계약금액 바뀌어도 자동 덮어쓰지 않음. 등록·수정 폼에서 예산을 직접 고치거나 상세 예산 [변경]으로 저장하면 Y(2026-10-29 B8). 변경 때 TB_PJ_PROJ_CHG에 BUDGET 한 줄';
COMMENT ON COLUMN TB_PJ_PROJ.TEAM_EXPS_AMT IS '팀비금액: 1인당 월(회식비), 기본 TEAM_FEE';
COMMENT ON COLUMN TB_PJ_PROJ.PROJ_EXPS_AMT IS '프로젝트경비금액: 1인당 월(물품 구매비), 기본 PEXP_FEE';
COMMENT ON COLUMN TB_PJ_PROJ.FEATURE_CN IS '특징내용: AI 추천 스킬 매칭에 사용';
COMMENT ON COLUMN TB_PJ_PROJ.OPP_ID IS '영업기회ID: 수주 처리로 생성된 경우';
COMMENT ON COLUMN TB_PJ_PROJ.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_PROJ.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_PROJ.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_PROJ.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_PROJ_PART IS '프로젝트영업파트이력';
COMMENT ON COLUMN TB_PJ_PROJ_PART.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_PROJ_PART.APLY_BGN_YM IS '적용시작연월: 최초 = 계약 시작월. 변경은 마감월 다음 달부터. 새 변경을 넣으면 마감 안 된 달부터 시작하는 이전 변경 줄(최초 줄 제외)은 지우고 새 줄 1개로 바꿈(2026-10-29 덮어쓰기). 그 달의 파트 = 적용시작연월 ≤ 그 달인 가장 최근 줄';
COMMENT ON COLUMN TB_PJ_PROJ_PART.PART_ID IS '영업파트ID: NULL = 미지정, ''HQ'' = 본사';
COMMENT ON COLUMN TB_PJ_PROJ_PART.CHG_RSN IS '변경사유: 선택. 영업 파트 변경 창의 사유. 프로젝트 상세 영업 파트 변경 이력 줄에서 사유가 있는 줄 옆 ⓘ에 마우스를 올리면 「사유: … · 변경자」로 보임(B14)';
COMMENT ON COLUMN TB_PJ_PROJ_PART.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_PROJ_PART.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_PROJ_PART.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_PROJ_PART.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_PROJ_CHG IS '계약변경이력';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.HIST_SN IS '이력일련번호: 최초 등록 시 ‘계약’ 1줄';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.CHG_ITEM_CD IS '변경항목코드: 공통코드 CHG_ITEM: CTRT 계약(등록·수주 처리 때 자동 첫 줄) / AMT / PERIOD / PART / LOC / BUDGET / TEAM 팀비 / PEXP 프로젝트경비 / ETC';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.CHG_ITEM_NM IS '변경항목명: 프로젝트명, 계약 종료일 등 실제 칸 이름';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.BFR_VAL IS '변경전값';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.AFT_VAL IS '변경후값';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.CHG_DT IS '변경일자';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_PROJ_CHG.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_MNPW_PLAN IS '인원계획';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.JOB_CD IS '직무코드';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.PLAN_MM IS '계획MM: 프로젝트 전체 기간의 직무별 계획 MM. 프로젝트 상세 [인원 계획] 탭에서 입력(SFR-101-11). 빈 칸으로 저장하면 0(2026-10-29). UI-11의 그 해 몫은 기간 비율로 계산(식-67). UI-11은 계획 0이고 투입도 없는 직무 줄은 보이지 않음';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.SRC_CD IS '출처코드: 공통코드 SRC: MANUAL / OPP(수주 시 필요 인력에서 생성)';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_MNPW_PLAN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_STEP IS '프로젝트단계';
COMMENT ON COLUMN TB_PJ_STEP.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_STEP.STEP_SN IS '단계일련번호: 순서';
COMMENT ON COLUMN TB_PJ_STEP.PROJ_STEP_CD IS '프로젝트단계코드: 공통코드 PROJ_STEP';
COMMENT ON COLUMN TB_PJ_STEP.STEP_WGHT_RT IS '단계비중: 계약유형별 고정값(식-72)';
COMMENT ON COLUMN TB_PJ_STEP.PLAN_BGN_DT IS '계획시작일자: 첫 단계 = 계약 시작일';
COMMENT ON COLUMN TB_PJ_STEP.PLAN_END_DT IS '계획종료일자: 마지막 단계 = 계약 종료일, 겹침 불가';
COMMENT ON COLUMN TB_PJ_STEP.CMPL_DT IS '완료일자: [완료] 클릭 시 오늘. 완료 후 계획일 수정 불가';
COMMENT ON COLUMN TB_PJ_STEP.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_STEP.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_STEP.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_STEP.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_WEEK_MEMO IS '주간메모';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.MEMO_SN IS '메모일련번호';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.WRT_DT IS '작성일자: 처음 적은 날(수정해도 유지)';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.THIS_WEEK_CN IS '이번주한일: 선택(2026-10-27)';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.NEXT_WEEK_CN IS '다음주할일: 선택(2026-10-27)';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.REQ_CN IS '요청사항';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.ACTL_PRGRS_RT IS '실적진척률: 0~100 정수, 턴키·내부만';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_WEEK_MEMO.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_ISSUE IS '이슈';
COMMENT ON COLUMN TB_PJ_ISSUE.ISSUE_ID IS '이슈ID';
COMMENT ON COLUMN TB_PJ_ISSUE.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_ISSUE.ISSUE_TTL IS '이슈제목';
COMMENT ON COLUMN TB_PJ_ISSUE.IMPR_CD IS '중요도코드: HIGH / MID(기본) / LOW';
COMMENT ON COLUMN TB_PJ_ISSUE.DDLN_DT IS '기한일자: 기본 오늘+7';
COMMENT ON COLUMN TB_PJ_ISSUE.ISSUE_STAT_CD IS '이슈상태코드: OPEN 열림 / ACT 조치 중 / CLOSE 종료';
COMMENT ON COLUMN TB_PJ_ISSUE.ACTN_CN IS '조치내용';
COMMENT ON COLUMN TB_PJ_ISSUE.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_ISSUE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_ISSUE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_ISSUE.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_TK_DSTRB IS '턴키배분표';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.PROJ_ID IS '프로젝트ID: 턴키만';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.DSTRB_STEP_CD IS '배분단계코드: EST 견적 / CTRT 최종 계약 / PLAN 실제 투입계획 (식-53)';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.ROW_SN IS '행일련번호';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.DSTRB_YM IS '배분연월';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.ROW_TYPE_CD IS '행유형코드: DEV 사람 줄 / ITEM 항목 줄(제경비·기술료)';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.DEV_ID IS '개발자ID: 사람 줄. 직무는 저장하지 않음: 화면에서 이 프로젝트의 TB_PJ_ASGN.JOB_CD(배정취소 제외, 여러 개면 모두)를 조회해 보이고, 배정이 없으면 TB_HR_DEV.JOB_CD (2026-10-29)';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.ITEM_NM IS '항목명: 항목 줄';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.DSTRB_AMT IS '배분금액';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.UPLD_ID IS '업로드ID: B.6';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_TK_DSTRB.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_DRCT_EXPS IS '프로젝트직접경비';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.EXPS_YM IS '경비연월';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.EXPS_SN IS '경비일련번호';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.DRCT_EXPS_ITEM_CD IS '직접경비항목코드: 공통코드 DRCT_EXPS_ITEM(출장비·장비 임대·회식비·기타, 2026-10-28)';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.EXPS_AMT IS '경비금액: 이 프로젝트에서 쓴 금액(부가세 칸 없음, 2026-10-28)';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.RMRK IS '비고: 화면의 메모';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_DRCT_EXPS.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_ASGN IS '투입';
COMMENT ON COLUMN TB_PJ_ASGN.ASGN_ID IS '투입ID: A-0001';
COMMENT ON COLUMN TB_PJ_ASGN.PROJ_ID IS '프로젝트ID: 종료·실주 안 된 프로젝트';
COMMENT ON COLUMN TB_PJ_ASGN.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_PJ_ASGN.JOB_CD IS '직무코드';
COMMENT ON COLUMN TB_PJ_ASGN.INPUT_BGN_DT IS '투입시작일자';
COMMENT ON COLUMN TB_PJ_ASGN.INPUT_END_DT IS '투입종료일자: 철수일. 연장·조기철수 시 변경';
COMMENT ON COLUMN TB_PJ_ASGN.CTRT_MM IS '월MM: 현재 월 MM: 오늘이 속한 달에 적용되는 값(시작 전 투입은 시작 달 값). 변경 기록을 저장할 때와 매달 1일 배치로 다시 맞춘다(2026-10-31 C6). 달별 값은 TB_PJ_ASGN_MON.MON_MM(식-01). 기본 1.00(풀 출근, 0 초과 1.0 이하). 풀로 출근하지 않으면 줄여서 금액을 맞춤(예: 주 3일 0.6). 일·시간 단가는 없음(2026-10-27)';
COMMENT ON COLUMN TB_PJ_ASGN.BILL_UPRC IS '청구단가: 현재 단가: 오늘이 속한 달에 적용되는 값(시작 전 투입은 시작 달 값). 변경 기록을 저장할 때와 매달 1일 배치로 다시 맞춘다(2026-10-31 C6). 달별 청구는 TB_PJ_ASGN_MON.APLY_UPRC(식-02). 기본값 개발자 월 단가, 대체투입이면 계약상 인력 단가(Q-식-12). 턴키는 NULL(배분표), 내부는 NULL, 무상지원은 0(칸 숨김, 2026-10-26 사용자 결정)';
COMMENT ON COLUMN TB_PJ_ASGN.INPUT_TYPE_CD IS '투입구분코드: NEW 신규 / ADD 추가투입 / SUB 대체투입 / FREE 무상지원 (MOV 이관은 안 씀, 2026-10-27) (시스템)';
COMMENT ON COLUMN TB_PJ_ASGN.CTRT_DEV_ID IS '계약상개발자ID: 대체투입일 때 청구서에 쓰는 이름. 배정 창에서는 이 프로젝트에 배정된 적 있는 사람(철수 포함) 중에서 고르고 개발자 ID를 저장, 업로드(B.5)는 관리번호. 계산에는 안 씀(2026-10-30 B17)';
COMMENT ON COLUMN TB_PJ_ASGN.EXT_PSBL_CD IS '연장가능성코드: H 높음 / M 보통 / L 낮음 / NULL 미정';
COMMENT ON COLUMN TB_PJ_ASGN.EXT_CNT IS '연장횟수: 이력에서 정정 제외하고 셈(배치/트리거 갱신)';
COMMENT ON COLUMN TB_PJ_ASGN.EARLY_WTHDR_YN IS '조기철수여부';
COMMENT ON COLUMN TB_PJ_ASGN.ASGN_STAT_CD IS '투입상태코드: ACT 유효 / CNCL 배정취소(Q-12-5 사용자 결정: 지우지 않고 이력에 취소로 남김)';
COMMENT ON COLUMN TB_PJ_ASGN.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_ASGN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_ASGN.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_ASGN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_PJ_ASGN_HIST IS '투입변경이력';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.ASGN_ID IS '투입ID';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.HIST_SN IS '이력일련번호';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.ASGN_CHG_TYPE_CD IS '투입변경유형코드: 공통코드 ASGN_CHG_TYPE: INPUT 투입 / EXT 연장 / EARLY 조기철수 / UPRC 단가변경 / EXTP 연장가능성변경 / KIND 투입구분변경(배정 전체에 적용, 2026-10-26) / CRCT 정정 / CNCL 취소 / RATE 월MM변경(Q-12-6, 예전 이름 투입률 변경)';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.APLY_DT IS '적용일자: 투입일 이전 불가. UPRC·RATE 줄은 적용 시작월 1일(화면에서 월만 고름, 2026-10-26 사용자 결정)';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.BFR_VAL IS '변경전값';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.AFT_VAL IS '변경후값';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.ASGN_CHG_RSN_CD IS '투입변경사유코드: 공통코드 ASGN_CHG_RSN: CUST 고객 요청 / SCHD 일정 변경 / NEGO 단가 재협상 / HR 인력 사정 / FIX 입력 오류 / ETC. 대체투입 [같이 처리]의 조기 철수도 확인 창에서 이 코드를 고르고 메모(CHG_MEMO) 기본값은 「대체투입(실제 투입자)」(C7)';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.CHG_MEMO IS '변경메모';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.NEW_UPRC IS '새청구단가: INPUT·UPRC 줄만. 월 단가. 그 달 단가 = 적용 시작월(APLY_DT의 월) ≤ 그 달인 최근 줄 값(식-02, 2026-10-26 사용자 결정)';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.NEW_CTRT_MM IS '새월MM: INPUT·RATE 줄만. 그 달 월 MM = 적용 시작월 ≤ 그 달인 최근 줄 값(식-01, 2026-10-26 사용자 결정)';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.RTRO_YN IS '소급입력여부: 적용일이 오늘 이전';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.CLOSE_CROSS_YN IS '마감월걸침여부';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.CRCT_YN IS '정정됨여부: 이 줄이 정정된 경우 Y';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.CRCT_TRGT_SN IS '정정대상일련번호: CRCT 줄이 가리키는 원래 줄';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_ASGN_HIST.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_PJ_ASGN_MON IS '월별투입 - 마감 스냅샷';
COMMENT ON COLUMN TB_PJ_ASGN_MON.ASGN_ID IS '투입ID';
COMMENT ON COLUMN TB_PJ_ASGN_MON.INPUT_YM IS '투입연월';
COMMENT ON COLUMN TB_PJ_ASGN_MON.DEV_ID IS '개발자ID: 조회 편의(비정규화)';
COMMENT ON COLUMN TB_PJ_ASGN_MON.PROJ_ID IS '프로젝트ID: 비정규화';
COMMENT ON COLUMN TB_PJ_ASGN_MON.INPUT_DAY_CNT IS '투입일수: 달력일';
COMMENT ON COLUMN TB_PJ_ASGN_MON.MON_MM IS '월MM: 식-01, 소수 둘째 자리 반올림';
COMMENT ON COLUMN TB_PJ_ASGN_MON.APLY_UPRC IS '적용단가: 그 달 적용 청구 단가';
COMMENT ON COLUMN TB_PJ_ASGN_MON.SALES_AMT IS '매출금액: 식-02, 무상지원·내부 0';
COMMENT ON COLUMN TB_PJ_ASGN_MON.FIX_YN IS '마감고정여부: 마감되면 Y';
COMMENT ON COLUMN TB_PJ_ASGN_MON.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_PJ_ASGN_MON.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_PJ_ASGN_MON.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_PJ_ASGN_MON.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SL_OPP IS '영업기회';
COMMENT ON COLUMN TB_SL_OPP.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_OPP.OPP_NM IS '기회명';
COMMENT ON COLUMN TB_SL_OPP.CUST_CLNT_ID IS '고객사거래처ID: 사용 중인 고객사';
COMMENT ON COLUMN TB_SL_OPP.PART_ID IS '영업파트ID';
COMMENT ON COLUMN TB_SL_OPP.SALES_STEP_CD IS '영업단계코드: FIND 발굴 / PROP 제안 / NEGO 협상 / PRE 계약 직전 (시스템)';
COMMENT ON COLUMN TB_SL_OPP.WIN_PRBL_RT IS '수주확률: 0~100, 단계 기본값 10/30/60/90';
COMMENT ON COLUMN TB_SL_OPP.PRBL_MANUAL_YN IS '확률수기여부: Y면 단계 이동해도 유지';
COMMENT ON COLUMN TB_SL_OPP.EXPCT_AMT IS '예상금액: 부가세 제외, 0 초과';
COMMENT ON COLUMN TB_SL_OPP.BGN_PLAN_YM IS '시작예정연월';
COMMENT ON COLUMN TB_SL_OPP.CTRT_TYPE_CD IS '계약유형코드: 공통코드 CTRT_TYPE 중 TK 턴키 / MO 월단위 / DP 파견(IN 내부 제외). 수주 처리 창 기본값, AI 인력 추천 파견 자리 판정(식-69)';
COMMENT ON COLUMN TB_SL_OPP.EXPCT_PRD_MCNT IS '예상기간개월수: 1 이상. 비우면 6개월로 봄. 매출 예측(식-92), 수주 처리 창 종료일 기본값';
COMMENT ON COLUMN TB_SL_OPP.NEXT_TODO_CN IS '다음할일';
COMMENT ON COLUMN TB_SL_OPP.NEXT_TODO_DDLN_DT IS '다음할일기한: 지나면 빨강';
COMMENT ON COLUMN TB_SL_OPP.WORK_PLC_ADDR IS '근무지주소: 주소 전체(프로젝트 근무지와 같이 직접 입력). AI 인력 추천 근무지 점수(식-70, 맨 앞 시·도 비교는 프로젝트 근무지와 같음, 02 SFR-021), 수주 처리 때 TB_PJ_PROJ.WORK_PLC_ADDR로 넘김(2026-10-29 C12)';
COMMENT ON COLUMN TB_SL_OPP.RMRK IS '비고: AI 인력 추천 대상이 이 기회면 특징으로 씀(식-70)';
COMMENT ON COLUMN TB_SL_OPP.OPP_STAT_CD IS '기회상태코드: ING 진행 / WIN 수주 / LOSE 실주 / STOP 사용 안 함(Q-공통-21·Q-29-2, [다시 사용]이면 ING로 돌아감)';
COMMENT ON COLUMN TB_SL_OPP.EXPCT_PRFT_RT IS '예상이익률: 저장 시 계산(식-84)';
COMMENT ON COLUMN TB_SL_OPP.APRV_STAT_CD IS '승인상태코드: NONE 승인 불필요 / WAIT 승인 대기 / OK 승인 / REJ 반려';
COMMENT ON COLUMN TB_SL_OPP.APRV_PRFT_RT IS '승인시이익률: 재저장 비교(±0.5%p)';
COMMENT ON COLUMN TB_SL_OPP.RSLT_DT IS '결과일자: 수주·실주일, 사용 안 함(STOP) 처리일. [다시 사용]으로 ING가 되면 NULL(2026-10-30 C6)';
COMMENT ON COLUMN TB_SL_OPP.LOSE_RSN_CD IS '실주사유코드: PRICE / TECH / SCHD / CNCL / ETC';
COMMENT ON COLUMN TB_SL_OPP.LOSE_MEMO IS '실주메모';
COMMENT ON COLUMN TB_SL_OPP.PROJ_ID IS '프로젝트ID: 수주 시 생성된 프로젝트';
COMMENT ON COLUMN TB_SL_OPP.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SL_OPP.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SL_OPP.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SL_OPP.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SL_OPP_NEED IS '영업기회필요인력';
COMMENT ON COLUMN TB_SL_OPP_NEED.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_OPP_NEED.NEED_SN IS '필요일련번호: 1줄 이상';
COMMENT ON COLUMN TB_SL_OPP_NEED.JOB_CD IS '직무코드';
COMMENT ON COLUMN TB_SL_OPP_NEED.GRADE_CD IS '등급코드';
COMMENT ON COLUMN TB_SL_OPP_NEED.NEED_CNT IS '필요인원: 1 이상 정수';
COMMENT ON COLUMN TB_SL_OPP_NEED.BGN_YM IS '시작연월';
COMMENT ON COLUMN TB_SL_OPP_NEED.PRD_MCNT IS '기간개월수: 1 이상';
COMMENT ON COLUMN TB_SL_OPP_NEED.MON_MM IS '월MM: 0 초과 1.0 이하';
COMMENT ON COLUMN TB_SL_OPP_NEED.EXPCT_UPRC IS '예상단가: 비우면 직무 기준 단가(Q-식-80)';
COMMENT ON COLUMN TB_SL_OPP_NEED.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SL_OPP_NEED.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SL_OPP_NEED.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SL_OPP_NEED.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SL_TENT_ASGN IS '가배정';
COMMENT ON COLUMN TB_SL_TENT_ASGN.TENT_ID IS '가배정ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.NEED_SN IS '필요일련번호: 가배정 자리(그 기회의 필요 인력 줄). UI-30은 조건 「가배정 자리」(같은 직무 줄 자동, 없으면 첫 줄), UI-05는 누른 추천 카드의 줄, UI-29 수정 화면은 그 표의 줄(2026-10-29)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.BGN_YM IS '시작연월: max(철수 다음 달, 자리 시작월)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.TENT_STAT_CD IS '가배정상태코드: ACT 가배정 / REL 해제 / CNVT 배정 후보로 넘김 / DONE 배정 확정(UI-12 [확정] 후 투입 저장) / DROP 후보에서 뺌(UI-12 [빼기]). UI-12 「배정 후보」 목록은 CNVT만 보임(2026-10-28). 한 사람은 ACT 줄이 하나만: 다른 기회에 새로 가배정하면(UI-05·UI-29·UI-30) 이전 ACT 줄은 REL(2026-10-29)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.SRC_SCRN_ID IS '출처화면ID: UI-05 / UI-29 / UI-30';
COMMENT ON COLUMN TB_SL_TENT_ASGN.PROC_DTTM IS '처리일시: ACT에서 상태가 바뀐 때: 해제(실주·삭제·바꾸기)·배정 후보로 넘김(수주 처리)·확정·빼기. 수정일시(MOD_DTTM)는 시작월만 고쳐도 바뀌어서 따로 둠(2026-10-28, 예전 REL_DTTM 해제일시)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SL_TENT_ASGN.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SL_APRV_HIST IS '견적승인이력';
COMMENT ON COLUMN TB_SL_APRV_HIST.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_APRV_HIST.HIST_SN IS '이력일련번호';
COMMENT ON COLUMN TB_SL_APRV_HIST.APRV_ACT_CD IS '승인작업코드: REQ 요청 / OK 승인 / REJ 반려 / AUTO_REL 요구 해제';
COMMENT ON COLUMN TB_SL_APRV_HIST.PRFT_RT IS '이익률: 그 시점 예상 이익률';
COMMENT ON COLUMN TB_SL_APRV_HIST.ACT_RSN IS '작업사유: 반려 사유. 반려 확인 창의 「반려 사유」(선택)에서 입력, 기회 상세 반려 띠·담당자 알림에 표시(2026-10-29 C9)';
COMMENT ON COLUMN TB_SL_APRV_HIST.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SL_APRV_HIST.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_SL_RCMD_LOG IS 'AI추천기록';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_ID IS '추천ID: 추천 1회';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RANK_ORD IS '순위: 1~5(Q-30-1)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_TYPE_CD IS '추천유형코드: AI = AI 인력 추천(UI-30) / NEXT = 다음 자리(UI-05)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_RCMD_LOG.NEED_SN IS '필요일련번호';
COMMENT ON COLUMN TB_SL_RCMD_LOG.PROJ_ID IS '프로젝트ID';
COMMENT ON COLUMN TB_SL_RCMD_LOG.JOB_CD IS '직무코드';
COMMENT ON COLUMN TB_SL_RCMD_LOG.BGN_YM IS '시작연월';
COMMENT ON COLUMN TB_SL_RCMD_LOG.REQ_GRADE_CD IS '요청등급코드: 등급 점수를 매길 때 비교한 기준 등급(공통코드 GRADE: JR 초급 / MD 중급 / SR 고급 / EX 특급). AI = 추천 조건에서 고른 등급(필수, 처음 값 SR). NEXT = 자리(TB_SL_OPP_NEED)의 필요 등급, 비어 있으면 MD(중급으로 비교, 식-80). 후보 개발자 등급이 비어 있어도 이 값은 있음(2026-10-30 C11)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.DEV_ID IS '개발자ID: 후보';
COMMENT ON COLUMN TB_SL_RCMD_LOG.TOT_SCORE IS '총점: 100점 만점';
COMMENT ON COLUMN TB_SL_RCMD_LOG.SCORE_DTL IS '항목별점수: JSON(직무·스킬·고객/업종·등급·성과·비용·가용일·근무지)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_RSN IS '추천이유';
COMMENT ON COLUMN TB_SL_RCMD_LOG.USER_ACT_CD IS '사용자작업코드: ASGN 배정 / TENT 가배정 / EXCL 제외';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_EXCL_CD IS '추천제외사유코드: 공통코드 RCMD_EXCL. 제외(EXCL)일 때 필수(Q-30-3). 추천할 때 같은 대상(OPP_ID 또는 PROJ_ID) + JOB_CD에 EXCL 기록이 있는 DEV_ID는 그 대상이 닫힐 때까지 후보에서 뺀다(2026-10-24 사용자 결정, 컬럼 추가 없음)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.ACT_USER_ID IS '작업사용자ID: USER_ACT_CD(배정·가배정·제외)를 누른 사용자. 로그 테이블이라 MOD_* 가 없어 따로 둠(2026-10-29 C14)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.ACT_DTTM IS '작업일시: USER_ACT_CD를 넣은 시각(2026-10-29 C14)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_BL_BILL IS '청구';
COMMENT ON COLUMN TB_BL_BILL.BILL_ID IS '청구번호: B-202610-001. B-청구월(YYYYMM)-그 달 안 순번 3자리(001부터). 취소된 번호는 다시 쓰지 않음(01 2.7, 2026-10-30 D12)';
COMMENT ON COLUMN TB_BL_BILL.PROJ_ID IS '프로젝트ID: 내부·수주예정 제외';
COMMENT ON COLUMN TB_BL_BILL.BILL_ORD IS '청구차수: 그 프로젝트의 모든 청구(취소 포함) 중 가장 큰 차수 + 1. 등록할 때 저장하고 바꾸지 않음. 취소된 차수는 다시 쓰지 않음(청구월 순서와 다를 수 있음, 03 SFR-113-04, 2026-10-31 C10)';
COMMENT ON COLUMN TB_BL_BILL.BILL_YM IS '청구연월: 귀속월. 기본 이번 달';
COMMENT ON COLUMN TB_BL_BILL.BILL_DT IS '청구일자: 턴키 매출 인식 기준(식-04)';
COMMENT ON COLUMN TB_BL_BILL.SPLY_AMT IS '공급가액: 추정값 자동 채움 후 수정 가능';
COMMENT ON COLUMN TB_BL_BILL.VAT_RT IS '부가세율: % 입력, 기본 10(운영 기준값 VAT_RT), 면세·영세율 0(Q-13-4)';
COMMENT ON COLUMN TB_BL_BILL.VAT_AMT IS '부가세금액: 공급가 × 부가세율 원 단위 버림(직접 입력 없음)';
COMMENT ON COLUMN TB_BL_BILL.TOT_AMT IS '합계금액: 공급가 + 부가세';
COMMENT ON COLUMN TB_BL_BILL.RCPT_SUM_AMT IS '입금합계금액: 입금 등록 시 갱신';
COMMENT ON COLUMN TB_BL_BILL.UNPAID_AMT IS '미수금액: 합계 − 입금합계';
COMMENT ON COLUMN TB_BL_BILL.PAY_DUE_DT IS '입금예정일자: 청구 창에서 입력(필수). 기본 청구월 다음 달 25일(Q-식-10)';
COMMENT ON COLUMN TB_BL_BILL.TAX_INVC_NO IS '세금계산서번호: 청구 창에서 입력. 비워도 저장, 비면 “번호 미입력” 경고와 월 마감 점검 누락(Q-13-2)';
COMMENT ON COLUMN TB_BL_BILL.CNCL_YN IS '취소여부: Y=취소됨. 잘못 등록한 청구를 지우지 않고 남김. 청구월이 마감 전이고 정상 입금이 없을 때만 취소. 취소된 청구는 턴키 매출·미수금·청구 누락 점검·미수 알림에서 뺌(사용자 결정 2026-10-26). 차수(BILL_ORD)는 비워 두고 다시 쓰지 않음(2026-10-31 C10)';
COMMENT ON COLUMN TB_BL_BILL.CNCL_RSN IS '취소사유: 취소할 때 필수';
COMMENT ON COLUMN TB_BL_BILL.CNCL_USER_ID IS '취소자ID';
COMMENT ON COLUMN TB_BL_BILL.CNCL_DTTM IS '취소일시';
COMMENT ON COLUMN TB_BL_BILL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_BL_BILL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_BL_BILL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_BL_BILL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_BL_RCPT IS '입금';
COMMENT ON COLUMN TB_BL_RCPT.RCPT_ID IS '입금ID';
COMMENT ON COLUMN TB_BL_RCPT.BILL_ID IS '청구번호';
COMMENT ON COLUMN TB_BL_RCPT.RCPT_DT IS '입금일자: 자금수지 실적 기준';
COMMENT ON COLUMN TB_BL_RCPT.RCPT_AMT IS '입금금액: 0 초과, 미수금 이하';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_YN IS '취소여부: Y=취소됨. 지우지 않고 남김, 입금합계에서 뺌(Q-13-5). RCPT_DT가 속한 달이 마감되면 취소 불가(청구월 무관, 2026-10-25)';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_RSN IS '취소사유: 취소할 때 필수';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_USER_ID IS '취소자ID';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_DTTM IS '취소일시';
COMMENT ON COLUMN TB_BL_RCPT.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_BL_RCPT.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_BL_RCPT.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_BL_RCPT.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_BL_DUN_HIST IS '미수알림이력';
COMMENT ON COLUMN TB_BL_DUN_HIST.BILL_ID IS '청구번호';
COMMENT ON COLUMN TB_BL_DUN_HIST.DUN_SN IS '알림일련번호';
COMMENT ON COLUMN TB_BL_DUN_HIST.DUN_STEP IS '알림단계: 1 / 2 / 3 (7·30·60일)';
COMMENT ON COLUMN TB_BL_DUN_HIST.SEND_DT IS '발송일자';
COMMENT ON COLUMN TB_BL_DUN_HIST.RCVR_DESC IS '수신자설명: 받은 사람 목록';
COMMENT ON COLUMN TB_BL_DUN_HIST.SEND_MTHD_CD IS '발송방법코드: BOTH(메일+시스템)';
COMMENT ON COLUMN TB_BL_DUN_HIST.RESEND_YN IS '다시알림여부: [다시 알림]';
COMMENT ON COLUMN TB_BL_DUN_HIST.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_BL_DUN_HIST.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_CO_MON_COST IS '월별원가배분 - 마감 스냅샷';
COMMENT ON COLUMN TB_CO_MON_COST.COST_YM IS '원가연월';
COMMENT ON COLUMN TB_CO_MON_COST.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_CO_MON_COST.PROJ_ID IS '프로젝트ID: 대기 몫은 ''IDLE'', 간접 인력 줄은 ''INDRCT''(2026-10-28)';
COMMENT ON COLUMN TB_CO_MON_COST.ASGN_ID IS '투입ID: TB_PJ_ASGN.ASGN_ID. **한 줄 = 그 달 투입 1건**(2026-10-28). 대기 줄은 ''IDLE'', 간접 인력 줄은 ''INDRCT''(정해진 값이라 FK 없음, 01 규칙). 같은 사람이 같은 프로젝트에 투입 2건(예: 유상 0.5 + 무상지원 0.5, 직무가 다른 추가투입)이면 2줄로 저장';
COMMENT ON COLUMN TB_CO_MON_COST.ASGN_MM IS '투입MM: 이 투입의 그 달 MM(= TB_PJ_ASGN_MON.MON_MM). 대기 줄은 1.0 − 그 달 MM 합, 간접 줄은 1.0';
COMMENT ON COLUMN TB_CO_MON_COST.FREE_MM IS '무상지원MM: 이 투입이 무상지원(TB_PJ_ASGN.INPUT_TYPE_CD=FREE)이면 ASGN_MM과 같고, 아니면 0. 무상지원 원가(식-27) = FREE_MM > 0인 줄의 LABOR_ALLOC_AMT 합';
COMMENT ON COLUMN TB_CO_MON_COST.SALES_AMT IS '매출금액: 개인 단가(턴키는 배분 금액)';
COMMENT ON COLUMN TB_CO_MON_COST.LABOR_ALLOC_AMT IS '인건비배분금액: 식-13 / 대기는 식-14';
COMMENT ON COLUMN TB_CO_MON_COST.TEAM_EXPS_AMT IS '팀비금액: 식-11. 마감 때 TB_PJ_PROJ.TEAM_EXPS_AMT(그때 값) × 이 줄 ASGN_MM, 원 단위 버림. 외주·대기(''IDLE'')·간접 줄은 0. 프로젝트 팀비를 나중에 바꿔도 이 값은 그대로(2026-10-28)';
COMMENT ON COLUMN TB_CO_MON_COST.PROJ_EXPS_AMT IS '프로젝트경비금액: 식-11. 마감 때 TB_PJ_PROJ.PROJ_EXPS_AMT(그때 값) × 이 줄 ASGN_MM, 원 단위 버림. 외주·대기·간접 줄은 0(2026-10-28)';
COMMENT ON COLUMN TB_CO_MON_COST.DRCT_EXPS_AMT IS '직접경비금액: 식-11. = TEAM_EXPS_AMT + PROJ_EXPS_AMT(두 칸 합, 마감 때 같이 저장). 합계만 쓰는 화면(UI-04 누적 원가·UI-08 등)은 이 칸 합에 그 달 TB_PJ_DRCT_EXPS.EXPS_AMT 합(추가 직접 경비)을 더한다(2026-10-31)';
COMMENT ON COLUMN TB_CO_MON_COST.COST_CLS_CD IS '원가구분코드: ALLOC 매출원가 / IDLE 대기원가 / RND 연구개발비 / INDRCT 간접인건비 / PRE 선투입';
COMMENT ON COLUMN TB_CO_MON_COST.CTRT_TYPE_CD IS '계약유형코드: 유형별 기여도 집계용';
COMMENT ON COLUMN TB_CO_MON_COST.PART_ID IS '영업파트ID: 그 달 프로젝트 파트';
COMMENT ON COLUMN TB_CO_MON_COST.FIX_YN IS '마감고정여부';
COMMENT ON COLUMN TB_CO_MON_COST.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_MON_COST.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_MON_COST.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_MON_COST.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CO_MON_PL IS '월별손익 - 마감 스냅샷 · 2025(시작한 달 전) 이관';
COMMENT ON COLUMN TB_CO_MON_PL.PL_YM IS '손익연월';
COMMENT ON COLUMN TB_CO_MON_PL.SALES_AMT IS '매출액';
COMMENT ON COLUMN TB_CO_MON_PL.ALLOC_COST_AMT IS '배분원가: 내부 제외. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.IDLE_COST_AMT IS '대기원가: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.DRCT_EXPS_AMT IS '직접경비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.SALES_COST_AMT IS '매출원가: 식-28';
COMMENT ON COLUMN TB_CO_MON_PL.GROSS_PRFT_AMT IS '매출총이익: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.SGA_INPUT_AMT IS '판관비입력: UI-10. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.INDRCT_LABOR_AMT IS '간접인건비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.RND_AMT IS '연구개발비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.SGA_TOT_AMT IS '판관비합계: 식-29';
COMMENT ON COLUMN TB_CO_MON_PL.PREV_ADJ_AMT IS '전월조정: 식-59. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.OPRT_PRFT_AMT IS '영업이익: 식-30';
COMMENT ON COLUMN TB_CO_MON_PL.NOPR_INCM_AMT IS '영업외수익: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.NOPR_EXPS_AMT IS '영업외비용: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.CORP_TAX_AMT IS '법인세월할: 식-31, 마감 시 고정. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.NET_PRFT_AMT IS '당기순이익: 식-32. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.MIGR_YN IS '이관여부: 2025년(시작한 달 전) B.11 = Y(세부 구성 일부만). B.11 열(2026-10-29 사용자 결정, 테이블 검증 2장 E11): 필수 = 연월·매출·매출원가·판관비 합계·영업이익, 있으면 넣음 = 배분원가·대기원가·직접경비·매출총이익·판관비 입력·간접인건비·연구개발비·전월조정·영업외수익·영업외비용·법인세·당기순이익. Y인 달은 엑셀 값을 그대로 저장하고 다시 계산하지 않음. 빈칸은 0이 아니라 NULL(화면 「–」). N인 달은 모든 금액 칸에 값이 있음';
COMMENT ON COLUMN TB_CO_MON_PL.FIX_YN IS '마감고정여부';
COMMENT ON COLUMN TB_CO_MON_PL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_MON_PL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_MON_PL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_MON_PL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CO_PREV_ADJ IS '전월조정';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_ID IS '조정ID';
COMMENT ON COLUMN TB_CO_PREV_ADJ.APLY_YM IS '반영연월: 조정을 넣는 이번 달';
COMMENT ON COLUMN TB_CO_PREV_ADJ.TRGT_YM IS '대상연월: 원래 마감된 달';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_ITEM_CD IS '조정항목코드: SALES 매출 / COST 원가 / SGA 판관비 / ETC 기타 (UI-10 전월 조정 표, 35번 1)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_AMT IS '조정금액: 영업이익에 더하는 값(이익 늘면 +, 줄면 −, Q-식-55 결정 2026-10-21)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_RSN IS '조정사유: UI-10 전월 조정 표의 사유(필수, SFR-110-12)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_PREV_ADJ.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_PREV_ADJ.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CO_PART_SETL IS '영업파트정산';
COMMENT ON COLUMN TB_CO_PART_SETL.PART_ID IS '영업파트ID';
COMMENT ON COLUMN TB_CO_PART_SETL.SETL_YM IS '정산연월';
COMMENT ON COLUMN TB_CO_PART_SETL.CORP_CARD_AMT IS '법인카드사용금액: 기본 0';
COMMENT ON COLUMN TB_CO_PART_SETL.PART_PRFT_AMT IS '파트이익금: 확정 시 고정(식-44)';
COMMENT ON COLUMN TB_CO_PART_SETL.SETL_STAT_CD IS '정산상태코드: WORK 작업중 / CNFM 확정 / AUTO 마감 시 자동 확정(확정자 = 마감한 사람, 2026-10-25)';
COMMENT ON COLUMN TB_CO_PART_SETL.CNFM_USER_ID IS '확정자ID';
COMMENT ON COLUMN TB_CO_PART_SETL.CNFM_DTTM IS '확정일시';
COMMENT ON COLUMN TB_CO_PART_SETL.CNCL_RSN IS '확정취소사유: 마지막 취소 사유';
COMMENT ON COLUMN TB_CO_PART_SETL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_PART_SETL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_PART_SETL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_PART_SETL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CO_PART_SETL_DTL IS '영업파트정산상세';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PART_ID IS '영업파트ID';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.SETL_YM IS '정산연월';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.EMP_TYPE_CD IS '직원구분코드: 확정 때(직접 확정·월 마감 자동 확정) 그 달 직원구분을 저장: TB_HR_MON_PAY.EMP_TYPE_CD, 그 달 급여가 없으면 TB_HR_DEV.EMP_TYPE_CD. 확정된 달 화면 「구분」 칸은 이 값(나중에 개발자 풀 직원구분을 바꿔도 그대로)(2026-10-31 C17)';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PROJ_NM_LIST IS '프로젝트명목록: 확정 때(직접 확정·월 마감 자동 확정) 그 사람이 그 달 투입한 프로젝트 이름을 모두 「, 」로 이어 저장(같은 프로젝트는 한 번, 그 달 MM 큰 순, 같으면 이름순). 예: 가온은행 차세대, 누리카드 정산. 프로젝트명은 확정 때 이름 그대로 남음. 화면은 첫 이름 + 「외 n건」, 마우스를 올리면 전부. 작업중(확정 전·확정 취소 뒤)에는 그 달 투입에서 구해 보여 줌. 줄은 그 달 MM > 0인 사람만 생기므로 항상 1건 이상(2026-10-30 D15)';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.MM IS 'MM';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.UPRC_AMT IS '월단가금액: 식-41';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PAY_AMT IS '급여금액: 시작 달 일할';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.SALES_EXPS_AMT IS '공통관리비금액: 외주 0';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.RETIRE_AMT IS '퇴직금금액: 정규직만';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.COST_AMT IS '비용계: 식-42';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.DIFF_AMT IS '차액: 월 단가 − 비용 계(식-43, 적자면 음수). 화면 열 이름은 「이익」(2026-10-29), 요약은 「이익 합계」(2026-10-31 C14)';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PRFT_AMT IS '이익금: 식-43, 음수 가능';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.RMRK IS '비고: 해당하는 것을 「 · 」로 이어 씀: 공통관리비(계약직·프리랜서) 또는 공통관리비+퇴직금(정규직), 외주는 둘 다 없음 / 시작 달 n/말일일(시작 달 일할) / ⚠ 월 급여 없음(시작 달 급여 0)(시작 달인데 개발자 풀 월 급여·지급액이 비어 있음, 화면 빨간 글씨, 막지 않음, N-02-4) / 무상지원. 하나도 없으면 비움(화면 「–」). 예: 「공통관리비+퇴직금 · 시작 달 12/31일 · ⚠ 월 급여 없음(시작 달 급여 0)」(04 SFR-119-02, 2026-10-31 C14)';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_ITEM IS '재무항목';
COMMENT ON COLUMN TB_FI_ITEM.FIN_YM IS '재무연월';
COMMENT ON COLUMN TB_FI_ITEM.ITEM_SN IS '항목일련번호';
COMMENT ON COLUMN TB_FI_ITEM.FIN_CLS_CD IS '재무구분코드: SGA 판관비 / NOPR_IN 영업외수익 / NOPR_OUT 영업외비용 (시스템)';
COMMENT ON COLUMN TB_FI_ITEM.SGA_ITEM_CD IS '판관비항목코드: 공통코드 SGA_ITEM. 직접 쓴 항목은 비움(Q-10-1)';
COMMENT ON COLUMN TB_FI_ITEM.ITEM_NM IS '세부항목명: 필수';
COMMENT ON COLUMN TB_FI_ITEM.AMT IS '금액: 부가세 제외, 필수';
COMMENT ON COLUMN TB_FI_ITEM.VAT_AMT IS '부가세금액: 화면·엑셀(B.2)에서 선택 입력, 비우면 0으로 저장. 자금수지 지출에만 더함(금액 + 부가세)(2026-10-31 C13)';
COMMENT ON COLUMN TB_FI_ITEM.IO_DT IS '입출금일자: 판관비·영업외비용은 자금수지 제경비 달(없으면 그 달), 영업외수익은 자금수지 잡수익 예정일(2026-10-28 지급 관리 삭제)';
COMMENT ON COLUMN TB_FI_ITEM.RPT_YN IS '매달반복여부';
COMMENT ON COLUMN TB_FI_ITEM.AUTO_FILL_YN IS '자동채움여부: “자동” 표시';
COMMENT ON COLUMN TB_FI_ITEM.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_ITEM.UPLD_ID IS '업로드ID: 엑셀(B.2)로 올린 줄이면 그 업로드 ID, 직접 입력·자동 채움 줄은 NULL. 같은 달 재업로드 때 이 값이 있는 줄만 지우고 새로 넣는다(2026-10-29 사용자 결정, 테이블 검증 2장 E9)';
COMMENT ON COLUMN TB_FI_ITEM.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_ITEM.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_ITEM.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_ITEM.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CORP_TAX IS '연간법인세';
COMMENT ON COLUMN TB_FI_CORP_TAX.TAX_YR IS '과세연도';
COMMENT ON COLUMN TB_FI_CORP_TAX.EST_TAX_AMT IS '연간예상법인세: 바꾸면 마감 안 된 달부터 재분배. 새 해 줄은 그 해 재무 항목을 처음 열 때 작년 값으로 만들어짐(없으면 0), 재무가 고침(2026-10-29)';
COMMENT ON COLUMN TB_FI_CORP_TAX.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CORP_TAX.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CORP_TAX.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CORP_TAX.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_TAX_PAY IS '법인세납부';
COMMENT ON COLUMN TB_FI_TAX_PAY.TAX_YR IS '과세연도';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_TYPE_CD IS '납부유형코드: CNFM 확정 신고(다음 해 3월) / MID 중간예납(8월)';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_DUE_DT IS '납부예정일자';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_AMT IS '납부금액: 금액 원본. MID: 재무가 세무사 안내 금액을 입력(비어 있으면 0으로 봄). CNFM: NULL이면 자동 = TB_FI_CORP_TAX.EST_TAX_AMT − 같은 과세연도 MID의 PAY_AMT(식-50)로 보여 주고, 사람이 고치면 그 값을 저장(납부 확인 전에 자동 값과 같게 넣으면 NULL로 둠). 납부 확인 때 실제 낸 금액을 숫자로 저장(자동 값과 같아도 NULL로 두지 않음)하고, 그 뒤로는 연간 예상액을 고쳐도 바뀌지 않으며 PAY_YN=Y 줄은 고칠 수 없음(2026-10-30 B2)';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_DT IS '납부일자: 납부 확인 때 넣는 실제 낸 날(기본 PAY_DUE_DT). 자금수지 「법인세 납부」는 PAY_YN=Y면 이 날의 달, N이면 PAY_DUE_DT의 달에 잡힘(2026-10-29)';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_YN IS '납부여부: N 예정 / Y 납부 확인. 날짜가 지나도 자동으로 Y가 되지 않고 [납부 확인]으로만 Y(2026-10-29). 화면 상태: Y → 납부, N이고 PAY_DUE_DT < 오늘 → 확인 필요(대시보드 경고), 그 밖 → 예정';
COMMENT ON COLUMN TB_FI_TAX_PAY.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_TAX_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_TAX_PAY.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_TAX_PAY.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_LOAN IS '대여금입출금';
COMMENT ON COLUMN TB_FI_LOAN.LOAN_SN IS '대여금일련번호';
COMMENT ON COLUMN TB_FI_LOAN.LOAN_DT IS '입출금일자: 이 날짜의 달 자금수지에 잡힘';
COMMENT ON COLUMN TB_FI_LOAN.IO_CD IS '입출금구분코드: IN 받음 / OUT 갚음';
COMMENT ON COLUMN TB_FI_LOAN.AMT IS '금액: 양수로 저장, 갚음은 자금수지에서 −';
COMMENT ON COLUMN TB_FI_LOAN.CNTPRT_NM IS '상대명: 대표이사·은행 등';
COMMENT ON COLUMN TB_FI_LOAN.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_LOAN.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_LOAN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_LOAN.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_LOAN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CARD IS '법인카드';
COMMENT ON COLUMN TB_FI_CARD.CARD_ID IS '카드ID: K-01 형식';
COMMENT ON COLUMN TB_FI_CARD.CARD_NM IS '카드명: 예: 영업 1팀 카드';
COMMENT ON COLUMN TB_FI_CARD.CARD_NO_L4 IS '카드번호끝4자리: 전체 카드번호는 저장하지 않음';
COMMENT ON COLUMN TB_FI_CARD.USE_YN IS '사용여부: 화면 [삭제]는 N으로 바꿈(물리 삭제 안 함, D-4). N이어도 결제 금액이 있는 달에는 목록에 보임. [되살리기]는 Y';
COMMENT ON COLUMN TB_FI_CARD.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CARD.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CARD.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CARD_MON IS '법인카드월합계';
COMMENT ON COLUMN TB_FI_CARD_MON.PAY_YM IS '결제연월: 통장에서 카드 대금이 나간 달';
COMMENT ON COLUMN TB_FI_CARD_MON.CARD_SUM_AMT IS '카드별합계금액: TB_FI_CARD_PAY 합계(저장 때 계산)';
COMMENT ON COLUMN TB_FI_CARD_MON.CASH_AMT IS '자금수지반영금액: 기본은 카드별 합계. 카드사 청구서와 다르면 재무가 직접 고침';
COMMENT ON COLUMN TB_FI_CARD_MON.EDIT_YN IS '직접수정여부: Y면 화면에 카드별 합계와의 차액 표시, [자동 합계로]를 누르면 N';
COMMENT ON COLUMN TB_FI_CARD_MON.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CARD_MON.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD_MON.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CARD_MON.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CARD_PAY IS '법인카드월결제';
COMMENT ON COLUMN TB_FI_CARD_PAY.PAY_YM IS '결제연월: → TB_FI_CARD_MON';
COMMENT ON COLUMN TB_FI_CARD_PAY.CARD_ID IS '카드ID: → TB_FI_CARD';
COMMENT ON COLUMN TB_FI_CARD_PAY.PAY_AMT IS '결제금액: 그 달 카드 대금(부가세 포함). 빈칸은 0';
COMMENT ON COLUMN TB_FI_CARD_PAY.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CARD_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD_PAY.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CARD_PAY.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_XFER IS '계좌이체지출';
COMMENT ON COLUMN TB_FI_XFER.XFER_SN IS '이체일련번호';
COMMENT ON COLUMN TB_FI_XFER.XFER_DT IS '이체일자: 이 날짜의 달 자금수지에 잡힘';
COMMENT ON COLUMN TB_FI_XFER.XFER_CLS_CD IS '이체구분코드: 공통코드 XFER_CLS: EXP 계좌이체(자금수지 제경비) / SPCL 특별비(자금수지 미지급 인건비). 화면 기본 선택은 EXP';
COMMENT ON COLUMN TB_FI_XFER.ITEM_NM IS '내용: 예: 장비 임대, 퇴직금(홍길동), 퇴직연금 11월 납입';
COMMENT ON COLUMN TB_FI_XFER.RCVR_NM IS '받는곳명: 업체 이름(특별비는 받는 사람)';
COMMENT ON COLUMN TB_FI_XFER.AMT IS '금액: 실제 나간 금액(부가세 포함)';
COMMENT ON COLUMN TB_FI_XFER.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_XFER.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_XFER.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_XFER.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_XFER.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_BIZ_PLAN IS '사업계획목표 - 부록 B.10';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.PLAN_YM IS '계획연월';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.PART_ID IS '영업파트ID: ''ALL'' 고정. 파트별 목표는 넣지 않음(Q-18-2 결정)';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.SALES_TGT_AMT IS '매출목표금액: 업로드 빈칸은 0(목표 없음, Q-18-3)';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.OPRT_PRFT_TGT_AMT IS '영업이익목표금액: 업로드 빈칸은 0';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.UPLD_ID IS '업로드ID';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CL_CLOSE IS '월마감';
COMMENT ON COLUMN TB_CL_CLOSE.CLOSE_YM IS '마감연월';
COMMENT ON COLUMN TB_CL_CLOSE.CLOSE_STAT_CD IS '마감상태코드: WORK 작업중 / CLOSE 마감';
COMMENT ON COLUMN TB_CL_CLOSE.CLOSE_USER_ID IS '마감자ID';
COMMENT ON COLUMN TB_CL_CLOSE.CLOSE_DTTM IS '마감일시';
COMMENT ON COLUMN TB_CL_CLOSE.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CL_CLOSE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CL_CLOSE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CL_CLOSE.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CL_CLOSE_HIST IS '마감이력';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.CLOSE_YM IS '마감연월';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.HIST_SN IS '이력일련번호';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.ACT_CD IS '작업코드: CLOSE 마감 / OPEN 해제';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.ACT_RSN IS '작업사유: 해제는 필수';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.REMAIN_CHK_CN IS '남은점검항목: 마감 시 남은 항목(Q-14-3)';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.REG_DTTM IS '등록일시';
COMMENT ON TABLE TB_CL_CLOSE_CHK IS '마감점검결과';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CLOSE_YM IS '마감연월';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_ITEM_CD IS '점검항목코드: PAY 급여 업로드 / MM_OVER MM 1.0 초과자 / BILL 세금계산서 번호 미입력(그 달 취소 안 된 청구 중 TAX_INVC_NO 빈 건. 청구 확정은 v2라 판정하지 않음, 2026-10-29 D9) / FI 재무 항목 / SETL 영업 파트 정산 / BILL_MISS 청구 누락 점검. 이름은 마감 화면과 같게(2026-10-30 E16) (PAYOUT 지급 미처리는 2026-10-28 삭제)';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_STAT_CD IS '점검상태코드: OK 정상 / WARN 확인 필요';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_CNT IS '점검건수';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_CN IS '점검내용: 한 줄 설명';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.LINK_SCRN_ID IS '연결화면ID: [확인하기]';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_DTTM IS '점검일시: [다시 점검]';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';

COMMENT ON TABLE TB_SY_CALC_RULE IS '계산규칙';
COMMENT ON COLUMN TB_SY_CALC_RULE.CALC_ID IS '식번호: 명세서 식 번호 그대로(예: 식-16). 식-00은 계산 공통 원칙';
COMMENT ON COLUMN TB_SY_CALC_RULE.SFR_ID IS '계산식 묶음 요구사항ID: SFR-901~912. 식-00은 없음';
COMMENT ON COLUMN TB_SY_CALC_RULE.CALC_NM IS '식이름';
COMMENT ON COLUMN TB_SY_CALC_RULE.FRML_CN IS '수식: 한 줄 식';
COMMENT ON COLUMN TB_SY_CALC_RULE.DTL_CN IS '상세내용: 쓰는 화면·정의·수식·예시·예외·관련 테이블·결정(명세서 글 그대로, Markdown)';
COMMENT ON COLUMN TB_SY_CALC_RULE.USE_SCRN_CN IS '쓰는화면: 예: UI-01, UI-12';
COMMENT ON COLUMN TB_SY_CALC_RULE.SORT_ORD IS '정렬순서: 식 번호 숫자';
COMMENT ON COLUMN TB_SY_CALC_RULE.REG_USER_ID IS '등록자ID';
COMMENT ON COLUMN TB_SY_CALC_RULE.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_CALC_RULE.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_CALC_RULE.MOD_DTTM IS '수정일시';

-- 6. 기준 데이터 (FK가 참조하는 예약값)
-- 본사(직접 영업) 예약 파트. TB_PJ_PROJ_PART.PART_ID = 'HQ' 가 참조한다
INSERT INTO TB_BS_PART (PART_ID, PART_NM, HQ_YN, USE_YN, REG_USER_ID) VALUES ('HQ', '본사', 'Y', 'Y', 'SYSTEM');
