# 물리 데이터 모델 / DB 계약 보강

## 실패 원인과 경계

제공된 실패 보고의 `table_source=headings`는 `Entity Registry`, `Field Mutability Matrix`, `BASIC-137` 등의 절 제목을 테이블로 오인했다. 이들은 영속 엔터티가 아니다. 참조 data-model.md 69~74행의 물리 테이블 표, 294행부터의 전수 필드 표, 1969행부터의 키/CHECK/인덱스 표를 기준으로 대조했다. 물리 식별자는 `TB_*`이며 PostgreSQL에서는 따옴표 없는 이름이 소문자로 접힌다. `TB_SY_USER`를 `t_b_s_y_u_s_e_r`로 새로 만들지 않는다.

참조 문서와 기존 V1 SQL의 테이블/필드 이름 대조: 68개 테이블, 836개 필드, 이름 누락 없음. 제공 보고서의 28개 제목을 맞추기 위한 가짜 테이블이나 비업무 seed는 추가하지 않았다. 플랫폼 검증기의 소스는 이 저장소에서 발견되지 않았으므로 그 추출기를 수정하거나 원래 검증이 통과했다고 주장하지 않는다. 플랫폼에서는 제목 fallback보다 `entity/table` 표와 `Entity | Field` 표를 우선 추출해야 한다.

## 변경

- V1~V6는 수정하지 않는다. V7은 월원가/급여/월투입/턴키/사용자설정의 복합 PK와 순서를 확인하고 원천의 업무 의미를 DB 주석에 보강한다. 새 테이블/컬럼/업무 데이터는 만들지 않는다.
- schema.sql의 해당 주석도 같은 의미로 맞춘다. `spring.sql.init.mode=never`를 유지하여 정상 시작은 Flyway만 사용한다.
- SchemaContractVerifier는 정상 Spring Boot 시작 과정에서 Flyway 이후 패키징된 `db/catalog.json`과 실제 DB의 68개 엔터티/836개 컬럼 타입·NULL 허용·PK 순서를 대조한다. 불일치 시 `DB_SCHEMA_CONTRACT_MISMATCH`와 식별자만 보고하며, DB 자동 보정/재적재는 하지 않는다. NUMERIC(p)와 PostgreSQL 카탈로그 NUMERIC(p,0)는 동등하게 처리한다.
- 개발용 검사: `python3 scripts/check_db_contract.py`. 패키징된 물리 카탈로그로 V1/schema.sql을 검사하며 `.aiops-spec`를 읽지 않는다. 플랫폼 추출기와는 별개의 로컬 검사다.
- DB 회귀는 Flyway 설치와 schema.sql 2회 실행을 비교한다. 모든 PK/FK/UK/CHECK 정의 및 모든 인덱스 정의를 비교하고, 컬럼 누락/타입/NULL/PK 손상 감지와 재시작 migration 무실행도 확인한다.
- 기존 설치 시험의 과거 기대값을 V3/V4/V6의 추가 시퀀스 및 V5 주소 적재에 맞춘다. 주소 데이터는 설치 후 DB에서 얻은 건수가 재시작 후 보존되는지 확인한다.

## 기존 API / UI 연결 확인

참조 OpenAPI의 getMonthlyCosts, getIndividualProfitability, getIndividualProfitabilityPerson, getIndividualProfitabilityExport를 확인했다. MonthlyCostController → MonthlyCostService → MonthlyCostRepository → MonthlyCostMapper.xml이 실제 급여/월투입/원가사본/턴키배분표를 조회한다. Repository는 readOnly/REPEATABLE_READ를 사용한다. 프런트 individualProfitabilityApi.ts는 같은 상대 `/api/individual-profitability` 경로를 호출한다. 기존 연결을 정적 데이터로 교체하지 않았고 이번 보강에서 프런트 요청 계약을 바꾸지 않았다.

`.aiops-spec`는 작성 시 참조만 했다. 추가 코드/스크립트/시험/런타임은 이 디렉터리를 직접 읽지 않는다. Boot 3.3.13의 pom.xml에는 기존 flyway-database-postgresql 의존성이 버전 없이 이미 있다.

## 실제 검증 결과

독립 PostgreSQL 16.9 컨테이너에서 수행했으며 운영/기존 preview DB는 변경하지 않았다.

- 로컬 SQL 계약 검사: 두 SQL 모두 ok=true, 68 테이블/836 필드, 누락/위반 없음.
- SchemaContractDatabaseTest, SchemaInstallationTest, InstallationRollbackTest: 7 tests, 0 failures, 0 errors, 0 skipped.
- Maven 실행 JAR 패키징 성공.
- 정상 Boot 기동 후 GET /api/health, /api/monthly-costs?year=2026&month=1, /api/individual-profitability?year=2026 모두 성공 JSON 응답. 초기 업무 원천이 없는 독립 DB이므로 items는 빈 배열이며, 가짜 인력/원가를 채우지 않았다.
- 실행 DB에서 직접 확인: 물리 업무 테이블 68, 컬럼 836, FK 73.
- npm run build 성공.
- 프런트 관련 선택 시험: 2 files / 266 tests 통과.
- git diff --check 통과.

전체 회귀가 통과한 것은 아니다. 같은 작업 트리 전체 실행 결과:

- 백엔드: 3384 tests, 6 failures, 0 errors, 425 skipped. 실패는 RuntimeCheckContractTest 2건, MonthlyCostContractTest 날짜 직렬화 3건, IndividualProfitabilityContractTest 계산 안내 비교 1건이다. 독립 기존 업무 데이터 복사본을 요구하는 시험은 별도 환경변수가 없어 실행하지 않았다.
- 프런트: 111 files 중 15 failed / 96 passed, 1500 tests 중 42 failed / 1458 passed. 라우터/공통 화면/재무 및 일부 개인원가 표시 시험 등을 포함한다. 관련 선택 실행은 통과하지만 전체 실행은 실패했다.
- 기존 durable OpenAPI 시험 fixture는 참조 OpenAPI 대비 개인원가 4개 경로가 빠져 있고 preferences 경로도 다르다. 이번 변경은 해당 전체 fixture 동기화나 기존 전체 회귀 실패를 해결한 것으로 주장하지 않는다.

이번 결과는 물리 DB 계약 보강 및 독립 설치 검증 결과이며, 플랫폼 원본 추출기 통과·모든 기능/브라우저 수락 통과를 뜻하지 않는다.
