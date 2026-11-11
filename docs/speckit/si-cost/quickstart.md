# 실행·검증 안내 — 원가관리시스템 기반 구축·공통코드

## 검증 순서와 착수 게이트

검증은 승인 확인 → 실제 구현/manifest·SQL 확인 → 독립 PostgreSQL 설치/초기값 → API 읽기 → 실제 저장/이력/경합 → 브라우저 → 실패 복구/재시작 → 종료 순서다. 이 문서는 실행 가능한 후속 walkthrough이며 이번 문서 배치에서 제품 검증을 수행했다는 결과 보고가 아니다. 현재 문서 폴더에는 backend/·frontend/·infra/docker-compose.yml과 제품 테스트 runner가 없으므로 제품 실행 결과는 모두 미검증이다.

원장 156개는 작성 전에 존재하는 requirement-registry.json·requirement-registry.md를 재사용한다. spec.md·plan.md·tasks.md와 Data/API 배치는 변경하지 않는다. canonical_id의 의미를 고정하고 API wire shape는 contracts/openapi.yaml을 참조한다. 관리 문서 외 새 실행·영속성 정본/seed 문서를 만들지 않는다.

다음이 남으면 설치·영속성 구현과 build-ready 판정은 중단한다.

| 게이트 | 확인 조건 | 연결 요구 |
| --- | --- | --- |
| 기술 결정 | T001에서 OQ-001(Java21/Boot3.5.x 대 고정 Java17/3.3.x), OQ-007(plan exact mybatis 대 승인 원장/spec jpa·JPA validate) 승인 정합화 | REQ-132, REQ-135, REQ-138, REQ-139, REQ-144 |
| API 미결 | OQ-003 오류 루트/meta/details, OQ-004 삭제 revision 위치·204/201 본문·없는 설정 초기화·저장결과 범위 승인. 아래 제안 의존 명령은 승인 후만 실행 | REQ-050, REQ-095, REQ-119, REQ-129, REQ-130 |
| 실제 원천 | T002의 SQL revision/해시·data 전체 대조, 적용 migration 상태·기존 구현 확인 | REQ-001, REQ-006, REQ-007, REQ-009 |
| 격리 환경 | 승인한 로컬/접근 제한 사설 주소·독립 테스트 DB/볼륨·포트. 기존 사용자 DB와 분리 | REQ-008, REQ-143 |
| 미완성 메타 | data-model.json.meta.request_key=null은 OQ-006 입력 대기. 호스트 값을 확보하고 소유 배치에서 정합화, BASIC-129로 추정 금지 | 추적 메타 완결성 확인; 독립 기능 요구 아님 |

## 환경 가정과 필수 변수

필수 도구는 Docker Compose가 동작하는 Docker, curl, jq, Bash, PostgreSQL 서비스 안의 psql이다. jq는 아래 검증 명령의 도구이며 제품의 신규 의존성 요구가 아니다. Node·Java·Gradle·frontend 시험 도구는 실제 manifest 및 공통 계약과 승인된 T001 결과를 대조한다. 사용자 로그인·비밀번호·토큰은 이번 API smoke의 선행 조건이 아니다. 임시 관리자 모든 기능 허용이므로 공개 바인딩을 하지 않는다(REQ-025, REQ-027, REQ-143).

아래 명령은 실제 구현 저장소 루트에서 같은 Bash 세션으로 실행한다. 현재 문서 작업 폴더에서 실행하지 않는다. 제품이 아직 없으면 test 실패가 정상적인 차단 증거이며 임의 파일/서버/mock으로 대체하지 않는다.

```bash
set -euo pipefail
for tool in docker curl jq bash; do command -v "$tool" >/dev/null; done
docker compose version
test -f infra/docker-compose.yml
test -f frontend/package.json
test -d backend
test -f .env.example
test -f backend/src/main/resources/db/migration/V1__full_schema.sql
test -f backend/src/main/resources/db/migration/V2__base_data.sql

read -r -p '접근 제한 nginx 주소(BASE_URL, 끝 / 제외): ' BASE_URL
read -r -p '검증 전용 Compose 프로젝트명: ' COMPOSE_PROJECT_NAME
read -r -p '승인된 환경설정 파일 경로(출력/공유 금지): ' ENV_FILE
: "${BASE_URL:?}" "${COMPOSE_PROJECT_NAME:?}" "${ENV_FILE:?}"
export BASE_URL COMPOSE_PROJECT_NAME
compose() { docker compose --env-file "$ENV_FILE" -p "$COMPOSE_PROJECT_NAME" -f infra/docker-compose.yml "$@"; }
compose config --quiet
compose config --services
read -r -p '실제 DB 서비스명: ' DB_SERVICE
read -r -p '실제 백엔드 서비스명: ' BACKEND_SERVICE
read -r -p '검증 전용 DB 사용자명: ' DB_USER
read -r -p '검증 전용 DB 이름: ' DB_NAME
: "${DB_SERVICE:?}" "${BACKEND_SERVICE:?}" "${DB_USER:?}" "${DB_NAME:?}"
```

Expected result: command exits successfully and the documented check passes.

BASE_URL·DB_SERVICE·BACKEND_SERVICE·DB_USER·DB_NAME·ENV_FILE·COMPOSE_PROJECT_NAME은 이 안내용 변수이며 제품의 환경변수 키를 새로 확정하는 것이 아니다. 실제 DB 접속 키와 포트는 .env.example/compose/승인 구현에서 확인한다. 비밀번호/접속 비밀은 환경설정에서만 공급하고 터미널 명령 인자·보고서·스크린샷·로그에 복사하지 않는다. 완전히 해석된 compose config는 비밀을 포함할 수 있으므로 출력하지 않는다. 별도 프로젝트명만으로 기존 DB/외부 volume 격리가 보장되지는 않으므로 실제 연결/volume도 승인한 독립 자원인지 확인한다.

기대: 실제3서비스명 확인, config 검증 성공, 비밀 노출0건. frontend/nginx를 별도 두 서비스로 나눠 총4개를 만들지 않는다. .dockerignore는 각 실제 build context에 node_modules/.git/dist/build/.next/coverage 제외를 확인한다(REQ-133, REQ-140, REQ-142).

## SQL 출처·설치·초기값 검증

### V01 — 출처와 최초 설치

Data 배치의 DDL revision d1e18c27afb077bfa5b1d92b982af194676ebd91 및 BOM 제거 텍스트 해시는 data-model.md의 기록을 참조한다. 현재 저장소의 raw bytes 해시와 혼동하지 않는다. project_data.sql 전체 revision/해시·실행본 diff는 아직 확보되지 않았다. T002/T006에서 다음 읽기 전용 명령과 실제 SQL 문맥 대조를 수행한다(REQ-001, REQ-006, REQ-136).

```bash
git rev-parse HEAD
sha256sum example/project_ddl.sql example/project_data.sql
sha256sum backend/src/main/resources/db/migration/V1__full_schema.sql backend/src/main/resources/db/migration/V2__base_data.sql
diff -u example/project_data.sql backend/src/main/resources/db/migration/V2__base_data.sql || test "$?" -eq 1
compose up --build -d
compose ps
```

기대: 실제 revision/raw hash와 원본/실행본 diff를 기록한다. diff의 차이 허용은 data 최상위 BEGIN2/COMMIT1 제거뿐이며 TEXT 내부 치환0회다. diff 명령 성공/차이 표시는 의미 검토 합격을 뜻하지 않는다. V1 전체 DDL/HQ → V2 전체 data 순으로 Flyway가 설치하며 HQ는 V1에서만1행이다. 적용 migration 수정/재실행·자동 baseline 금지다. 실패는 해당 트랜잭션을 롤백하고 구조 차이를 보고한다. 승인 기술이 JPA인 경우에만 정합화된 JPA ddl-auto=validate 통과를 실제 관측한다. MyBatis를 선택했다면 REQ-135에 대한 상류 승인 정합화 없이는 JPA 검증을 생략한 합격으로 바꾸지 않는다.

### V02 — 실제 PostgreSQL 계수

다음은 읽기 전용 SQL이다. 업무 테이블은 unquoted identifier로 생성되므로 물리 카탈로그의 소문자를 조회하며 업무 SQL은 따옴표로 재명명하지 않는다. current_schema와 DB가 실제 설치 대상인지 먼저 확인한다. schema를 임의 public으로 확정하지 않는다(REQ-003, REQ-134, REQ-135).

```bash
compose exec -T "$DB_SERVICE" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" <<'SQL'
SELECT current_database(), current_schema();
SHOW timezone;
WITH business_tables AS (
  SELECT c.oid FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname=current_schema() AND c.relkind='r' AND left(c.relname,3)='tb_'
)
SELECT 'tables' AS metric, count(*) AS actual FROM business_tables
UNION ALL
SELECT 'columns', count(*) FROM pg_attribute a JOIN business_tables t ON t.oid=a.attrelid WHERE a.attnum>0 AND NOT a.attisdropped
UNION ALL
SELECT 'fk', count(*) FROM pg_constraint c JOIN business_tables t ON t.oid=c.conrelid WHERE c.contype='f'
UNION ALL
SELECT 'check', count(*) FROM pg_constraint c JOIN business_tables t ON t.oid=c.conrelid WHERE c.contype='c'
UNION ALL
SELECT 'uk', count(*) FROM pg_constraint c JOIN business_tables t ON t.oid=c.conrelid WHERE c.contype='u'
UNION ALL
SELECT 'explicit_indexes', count(*) FROM pg_index i JOIN business_tables t ON t.oid=i.indrelid
 WHERE NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid=i.indexrelid)
UNION ALL
SELECT 'sequences', count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname=current_schema() AND c.relkind='S';
SELECT count(*) AS calc_rule_columns FROM information_schema.columns
 WHERE table_schema=current_schema() AND table_name='tb_sy_calc_rule';
SELECT 'groups' AS metric, count(*) AS actual FROM TB_SY_CODE_GRP
UNION ALL SELECT 'system_groups',count(*) FROM TB_SY_CODE_GRP WHERE SYS_CODE_YN='Y'
UNION ALL SELECT 'codes',count(*) FROM TB_SY_CODE
UNION ALL SELECT 'standard_keys',count(DISTINCT STD_CD) FROM TB_SY_OPER_STD
UNION ALL SELECT 'roles',count(*) FROM TB_SY_ROLE
UNION ALL SELECT 'menus',count(*) FROM TB_SY_MENU
UNION ALL SELECT 'role_menu',count(*) FROM TB_SY_ROLE_MENU_AUTH
UNION ALL SELECT 'admin',count(*) FROM TB_SY_USER WHERE USER_ID='admin'
UNION ALL SELECT 'admin_role',count(*) FROM TB_SY_USER_ROLE WHERE USER_ID='admin' AND ROLE_ID='ADMIN'
UNION ALL SELECT 'hq',count(*) FROM TB_BS_PART WHERE PART_ID='HQ'
UNION ALL SELECT 'calc_rules',count(*) FROM TB_SY_CALC_RULE
UNION ALL SELECT 'mappings',count(*) FROM TB_SY_CODE_MAP
UNION ALL SELECT 'history',count(*) FROM TB_SY_CHG_HIST
UNION ALL SELECT 'preferences',count(*) FROM TB_SY_USER_PREF
UNION ALL SELECT 'area',count(*) FROM TB_SY_ADM_AREA
UNION ALL SELECT 'hdong',count(*) FROM TB_SY_ADM_HDONG;
SELECT STD_CD,APLY_BGN_YM,STD_VAL FROM TB_SY_OPER_STD
 WHERE STD_CD IN ('CASH_BASE_YM','CASH_BASE_AMT') ORDER BY STD_CD;
SQL
```

빈 독립 DB에서 기대: tables68/columns836/fk73/explicit_indexes78/sequences13/check46/uk5, calc_rule_columns11. Flyway 관리 테이블과 PK/UK 자동 인덱스는 업무 수량에서 제외한다. 초기 groups64/system_groups44/codes391/standard_keys43/roles5/menus40/role_menu150/admin1/admin_role1/hq1/calc_rules94, mappings/history/preferences/area/hdong 각각0이다. CASH_BASE_YM=202601·CASH_BASE_AMT=1150000000이다(REQ-002, REQ-004, REQ-005).

계수만으로 모든 구조 보존 합격은 아니다. data-model.md의 전수 필드/PK/UK/FK/CHECK/시퀀스/명시 인덱스/NULL/DEFAULT/코멘트를 SQL 원본과 대조한다. 초기값을 검증한 다음에만 아래 쓰기 smoke를 실행한다. 쓰기 후 그룹/코드/이력이 늘어나는 것은 정상이며 초기 수량을 다시 기대하지 않는다.

## API smoke — 실제 HTTP 읽기와 오류

### V03 — 초기 읽기

아래는 현재 Data/API 계약의 data 바인딩을 사용한다. OQ-003 승인 전에는 루트 ApiError 모양을 최종 확정하지 않는다. health는 데이터 필드를 새로 약속하지 않고 status와 success/meta만 확인한다. GET 결과·후속 검증에는 실제 응답만 사용하며 예시 JSON을 합성하지 않는다.

```bash
curl -fsS "$BASE_URL/api/health" | jq -e '.success == true and (.meta | type == "object")'
curl -fsS "$BASE_URL/api/current-user" | jq -e '.data.userId == "admin" and .data.name == "임시 관리자" and .data.roleIds == ["ADMIN"] and .data.developmentMode == true'
curl -fsS "$BASE_URL/api/menus" | jq -e '.success == true and (.data | type == "object")'
curl -fsS "$BASE_URL/api/code-groups" | jq -e '(.data.items | length) == 64 and ([.data.items[] | select(.sysCodeYn == "Y")] | length) == 44'
curl -fsS "$BASE_URL/api/codes/ALWNC" | jq -e '(.data.items | length) == 6 and ([.data.items[] | select(.code == "INS_RATE") | .codeVal] == ["9.4"])'
curl -fsS "$BASE_URL/api/period-context" | jq -e '.data.timezone == "Asia/Seoul" and (.data.currentMonth | test("^[0-9]{4}-[0-9]{2}$"))'
curl -fsS "$BASE_URL/api/me/preferences" | jq -e '([.data.items[] | select(.type == "PAGE_SIZE" and .key == "*") | .value] == [50]) and ([.data.items[] | select(.type == "MENU_FOLD" and .key == "*") | .value] == [null]) and all(.data.items[]; .stored == false)'
curl -fsS "$BASE_URL/api/code-mappings?page=1&size=50" | jq -e '.data.items == [] and .data.total == 0 and .data.page == 1 and .data.size == 50'
curl -fsS "$BASE_URL/api/operating-standards" | jq -e '(.data.items | length) == 43'
curl -fsS "$BASE_URL/api/params/CASH_BASE_AMT?month=2026-01" | jq -e '.data.key == "CASH_BASE_AMT" and (.data.value | tonumber) == 1150000000'
curl -fsS "$BASE_URL/api/month-close?from=2026-01&to=2026-01" | jq -e '.success == true'
curl -fsS "$BASE_URL/api/common-code-history?page=1&size=50" | jq -e '.data.items == [] and .data.total == 0'
```

Expected result: command exits successfully and the documented check passes.

전체 JSON/공개 DTO 세부값은 contracts/openapi.yaml의 getCurrentUser/getMenus/getCodeGroups/getCodes/getPeriodContext/getPreferences/getCodeMappings/getOperatingStandards/getParameter/getMonthClose/getCommonCodeHistory를 따른다. jq의 tonumber는 위 초기 정수 비교에만 사용하며 소수 정밀값의 제품 저장/검증에는 쓰지 않는다. ALLOW_TEAM200000원/ALLOW_GROUP400000원/INS_RATE9.4%/SEV_RATE8.33%/TEAM_FEE50000원/PEXP_FEE50000원 모두 SQL·DB·응답 문자열을 추가 대조한다. 표시용 뒤0 생략과 DB 원값 보존을 구분한다. 메뉴10묶음30화면·진단0건, currentDate/currentMonth가 서버/DB Asia/Seoul 현재일과 일치, 임의 마감0건도 확인한다(REQ-025, REQ-035, REQ-047, REQ-063, REQ-066, REQ-069, REQ-091, REQ-099, REQ-115, REQ-122, REQ-127).

### V04 — 호출자가 고칠 수 있는 오류

HTTP400/404 검사는 curl -f를 쓰지 않는다. 아래 status 추출은 실패 본문과 분리한다. OQ-003 승인 후 원문 code/message/field?/details?/requestId와 X-Request-Id가 보존되는지 함께 확인한다.

```bash
result=$(curl -sS -w '\n%{http_code}' "$BASE_URL/api/common-code-history?page=0&size=30")
test "${result##*$'\n'}" = 400
printf '%s\n' "${result%$'\n'*}" | jq -e '.success == false and .code == "VALIDATION_ERROR" and (.requestId | length > 0)'
result=$(curl -sS -w '\n%{http_code}' "$BASE_URL/api/params/QS_NO_SUCH_STANDARD?month=2026-01")
test "${result##*$'\n'}" = 404
printf '%s\n' "${result%$'\n'*}" | jq -e '.code == "STANDARD_NOT_FOUND"'
result=$(curl -sS -w '\n%{http_code}' "$BASE_URL/api/month-close?from=2026-13&to=2026-01")
test "${result##*$'\n'}" = 400
```

불허 필드400 UNKNOWN_FIELD, 타입/필수/길이400 VALIDATION_ERROR, 실제 연월 오류400 INVALID_PERIOD, 모드/중복/참조/마감409를 구분한다. 앞 명령 하나가 여러 잘못된 값을 포함하므로 첫 오류 field의 우선순위를 새로 확정하지 않는다. page와 size 각각의 단독 오류는 별도 시험한다. 비밀/SQL/스택 노출0건·한국어 사유/requestId를 검사한다(REQ-021, REQ-068, REQ-128, REQ-129, REQ-130).

## 실제 쓰기·재조회 walkthrough

### V05 — 일반 그룹·코드·표기 대응의 영속성

독립 시험 DB에서만 실행한다. 다음 이름은 이 실행에서 생성하는 synthetic fixture이며 제품 seed나 frontend 상수가 아니다. 그룹 삭제 기능이 없으므로 테스트 자원은 DB 단위로 격리하되 이 안내는 DROP/TRUNCATE/volume 삭제를 실행하지 않는다.201 본문 없음·deleteGroupCode204/query expectedRevision·deleteCodeMappings204는 OQ-004의 현재 API 제안에 의존한다. 승인 전에는 쓰기 명령을 실행하거나 이 상태 코드를 확정값으로 인수하지 않는다.

```bash
GROUP_ID="QS_$(date +%Y%m%d%H%M%S)"
GROUP_NAME="검증 그룹 $GROUP_ID"
SOURCE_TEXT="검증 표기 $GROUP_ID"
body=$(jq -nc --arg id "$GROUP_ID" --arg name "$GROUP_NAME" '{groupId:$id,name:$name}')
status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/code-groups" -H 'Content-Type: application/json' --data "$body")
test "$status" = 201
curl -fsS "$BASE_URL/api/code-groups" | jq -e --arg id "$GROUP_ID" 'any(.data.items[]; .groupId == $id and .sysCodeYn == "N" and .valCodeYn == "N" and .useYn == "Y")'
REV=$(curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50" | jq -er '.data.groupRevision')
body=$(jq -nc --arg rev "$REV" '{expectedRevision:$rev,changes:[{code:"QS_CODE",isNew:true,name:"검증 코드"}]}')
curl -fsS -X PUT "$BASE_URL/api/code-groups/$GROUP_ID/codes" -H 'Content-Type: application/json' --data "$body" | jq -e '.data.changed == true'
curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50" | jq -e 'any(.data.items[]; .code == "QS_CODE" and .name == "검증 코드")'
curl -fsS "$BASE_URL/api/codes/$GROUP_ID" | jq -e 'any(.data.items[]; .code == "QS_CODE")'
body=$(jq -nc --arg text "$SOURCE_TEXT" --arg group "$GROUP_ID" '{sourceText:$text,groupId:$group,code:"QS_CODE"}')
status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/code-mappings" -H 'Content-Type: application/json' --data "$body")
test "$status" = 201
ROWS=$(curl -fsS --get "$BASE_URL/api/code-mappings" --data-urlencode "groupId=$GROUP_ID" --data-urlencode "sourceText=$SOURCE_TEXT" --data-urlencode 'page=1' --data-urlencode 'size=50')
printf '%s' "$ROWS" | jq -e --arg text "$SOURCE_TEXT" '.data.total == 1 and .data.items[0].sourceText == $text'
# 재등록은 동일 표기+그룹 UK 위반이며 다른 code라도 거부되어야 한다.
status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/code-mappings" -H 'Content-Type: application/json' --data "$body")
test "$status" = 409
curl -fsS --get "$BASE_URL/api/common-code-history" --data-urlencode "groupId=$GROUP_ID" --data-urlencode 'page=1' --data-urlencode 'size=50' | jq -e '.data.total >= 3'
```

Expected result: command exits successfully and the documented check passes.

기대: TB_SY_CODE_GRP 새 일반 그룹1행·NEW, TB_SY_CODE 신규 QS_CODE1행·NEW, TB_SY_CODE_MAP1행·NEW가 같은 실제 DB에서 확인된다. 이름/코드/수치와 actor=admin·등록시각은 서버 소유, group NEW·code NEW·mapping NEW의 이력을 대상으로 각각 읽는다. 이력 total≥3만으로 전후 JSON/처리자 검증을 대체하지 않는다. mapping NEW 이후 값은 sourceText/groupId/code/codeName JSON·이전NULL이어야 한다. 생성201의 미지정 id 응답을 기대하지 않고 GET에서 실제 id/revision을 얻는다(REQ-011, REQ-026, REQ-071, REQ-076, REQ-080, REQ-117, REQ-118, REQ-123, REQ-125).

### V06 — 동일 revision 충돌과 동일값 저장

```bash
REV=$(curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50" | jq -er '.data.groupRevision')
bodyA=$(jq -nc --arg rev "$REV" '{expectedRevision:$rev,changes:[{code:"QS_CODE",isNew:false,name:"검증 코드 A"}]}')
bodyB=$(jq -nc --arg rev "$REV" '{expectedRevision:$rev,changes:[{code:"QS_CODE",isNew:false,name:"검증 코드 B"}]}')
curl -fsS -X PUT "$BASE_URL/api/code-groups/$GROUP_ID/codes" -H 'Content-Type: application/json' --data "$bodyA" | jq -e '.data.changed == true'
result=$(curl -sS -w '\n%{http_code}' -X PUT "$BASE_URL/api/code-groups/$GROUP_ID/codes" -H 'Content-Type: application/json' --data "$bodyB")
test "${result##*$'\n'}" = 409
printf '%s\n' "${result%$'\n'*}" | jq -e '.code == "STALE_WRITE"'
AFTER=$(curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50")
printf '%s' "$AFTER" | jq -e 'any(.data.items[]; .code == "QS_CODE" and .name == "검증 코드 A")'
REV=$(printf '%s' "$AFTER" | jq -er '.data.groupRevision')
body=$(jq -nc --arg rev "$REV" '{expectedRevision:$rev,changes:[{code:"QS_CODE",isNew:false,name:"검증 코드 A"}]}')
curl -fsS -X PUT "$BASE_URL/api/code-groups/$GROUP_ID/codes" -H 'Content-Type: application/json' --data "$body" | jq -e '.data.changed == false'
```

Expected result: command exits successfully and the documented check passes.

이 명령은 두 클라이언트의 오래된 토큰을 순차 재현한다. 실제 동시 잠금 경합은 FX-REVISION/T014에서 별도로 두 요청을 동시에 시작해 선행1성공/후행1충돌을 관측한다. DB의 MOD_DTTM과 TB_SY_CHG_HIST를 각 요청 직전/직후 비교해 후행·동일값의 시각/이력 변화0건을 확인한다. 시각 양NULL/한NULL 및 값변경에 따른 revision 차이는 별도 fixture로 검사한다(REQ-028, REQ-029, REQ-030, REQ-031).

### V07 — 참조 차단·대응 실제삭제·코드 논리삭제

OQ-004의 query 전달과204가 승인된 후만 아래를 실행한다.409의 본문은 CODE_IN_USE·표기 대응 참조 유형/건수이며 코드 DEL_YN/이력0건 변경을 확인한다.

```bash
REV=$(curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50" | jq -er '.data.groupRevision')
status=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE --get "$BASE_URL/api/code-groups/$GROUP_ID/codes/QS_CODE" --data-urlencode "expectedRevision=$REV")
test "$status" = 409
body=$(printf '%s' "$ROWS" | jq -c '{items:[.data.items[] | {mapId,expectedRevision:.revision}]}')
status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/code-mappings/delete" -H 'Content-Type: application/json' --data "$body")
test "$status" = 204
curl -fsS --get "$BASE_URL/api/code-mappings" --data-urlencode "groupId=$GROUP_ID" --data-urlencode 'page=1' --data-urlencode 'size=50' | jq -e '.data.total == 0'
REV=$(curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50" | jq -er '.data.groupRevision')
status=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE --get "$BASE_URL/api/code-groups/$GROUP_ID/codes/QS_CODE" --data-urlencode "expectedRevision=$REV")
test "$status" = 204
curl -fsS "$BASE_URL/api/code-groups/$GROUP_ID/codes?page=1&size=50" | jq -e '.data.total == 0'
```

Expected result: command exits successfully and the documented check passes.

추가 DB 증거: TB_SY_CODE_MAP 물리행 없음, DEL의 이전 JSON에 삭제 내용·이후NULL; TB_SY_CODE는 물리행/PK 보존·DEL_YN=Y, DEL 이력 N→Y. 같은 PK 재추가409 DUPLICATE_CODE를 확인한다. 다중 대응 중 하나만 revision 변경/누락인 FX-MAP-RACE는409·두행 유지·DEL0건을 별도 수행한다. 참조 가드의 모든 고정 매핑/CSV/과거 이름/EXTP·취소/중지/마감도 T042에서 독립 검증한다. 위 한 대응 참조 smoke로 전수 가드 합격을 주장하지 않는다(REQ-079, REQ-094, REQ-095, REQ-096, REQ-097, REQ-098, REQ-118, REQ-119, REQ-120, REQ-121).

### V08 — 설정 명시 저장·동시 등록·개별 초기화

```bash
PREFS=$(curl -fsS "$BASE_URL/api/me/preferences")
PREF_REV=$(printf '%s' "$PREFS" | jq -c '.data.items[] | select(.type=="PAGE_SIZE" and .key=="*") | .revision')
body=$(jq -nc --argjson rev "$PREF_REV" '{value:50,expectedRevision:$rev}')
curl -fsS -X PUT "$BASE_URL/api/me/preferences/PAGE_SIZE/%2A" -H 'Content-Type: application/json' --data "$body" | jq -e '.data.preference.stored == true and .data.preference.value == 50'
curl -fsS "$BASE_URL/api/me/preferences" | jq -e 'any(.data.items[]; .type=="PAGE_SIZE" and .key=="*" and .stored==true and .value==50)'
```

Expected result: command exits successfully and the documented check passes.

빈 초기 DB라면 기본값50을 명시 저장해도 TB_SY_USER_PREF에 INSERT1건이다. 기존값 동일 저장과 구분한다. 개별 초기화는 deletePreference의 승인된 revision 전달 방식으로 해당 admin/PAGE_SIZE/* 행만 삭제하며 GET stored=false/value50·다른 키 보존·TB_SY_CHG_HIST0건을 확인한다. 없는 설정 초기화 정책은 OQ-004이며 추정하지 않는다. FX-PREF에서 동시 최초 INSERT 후행409, 손상 JSON 항목 오류/revision과 기본값 임시 표시·자동삭제0을 검사한다. PAGE_SIZE 문자열"50"400·FAV11번째 거부·RECENT5·DENSE/COLS/AMT_UNIT 복원·필수열 숨김400은 UI smoke와 함께 확인한다(REQ-047, REQ-048, REQ-049, REQ-050, REQ-051, REQ-052, REQ-053, REQ-054, REQ-055, REQ-056, REQ-057, REQ-058, REQ-059, REQ-060).

### V09 — 기준·정밀도·마감 상태별 허용/거부

DB fixture를 제품 seed에 섞지 않는다. 실제 입력/응답 형식은 getOperatingStandards/saveOperatingStandards/getOperatingStandardHistory/getParameter의 OpenAPI와 data-model.md의 FX-STD-BOUNDARY/FX-CASH-CLOSE/FX-PRECISION을 따른다. 임의 미등록 기준키·계산 엔진·마감 쓰기 API를 만들지 않는다.

| 선행 조건 → 실행 | 관찰 가능한 결과 | canonical_id·작업 |
| --- | --- | --- |
| 소수4자리 값으로 수당/일반 기준 저장 → 공통 조회·DB 재조회 | 입력 원값 정확히 보존·반올림0, 소수5/정수12자리는400·본체/이력 불변 | REQ-017, REQ-019; T012/T039 |
| 일반 값그룹 기존 단위 변경 및 SYS+VAL 그룹 값 변경 | 각각409 VALUE_CODE_FIELD_LOCKED / SYSTEM_CODE_LOCKED·본체/이력0; SYS+VAL 이름만은 성공 후 재조회 | REQ-074, REQ-089; T039 |
| 일반 기준 같은 적용월 확인 후 저장/없는월 저장 | 해당월 UPDATE/새월 INSERT, 다른월 불변, 전체 키 이력 revision 갱신 | REQ-101, REQ-102, REQ-103; T045 |
| AI합99/101, DUN역전, 확률100초과 및 이후 예정 경계 위반 |400·한 요청 본체/이력 전체0건; AI합100·DUN정상·확률0/100은 허용 | REQ-105, REQ-106, REQ-107, REQ-110; T045/T047 |
| 기준 현재/과거/미래 적용월 이력 열기 | 모든 적용월 DESC·현재/예정/지난, 미래값 현재 오용0·유효행 없으면 기준값 없음 | REQ-099, REQ-100; T048/T050 |
| 기존 CASH_BASE_YM CLOSE 상태에서 새월/잔액 보내기 | 두 키409 CLOSED_MONTH·기존값/행/적용월/이력 불변, 새월 우회0 | REQ-113; T051 |
| 기존 시작월 미마감에서 두 CASH 키 별도요청 | 키당1행 UPDATE·원래 APLY_BGN_YM 보존·UPD 전후 이력·period-context 최신·계산/마감자료 불변 | REQ-109, REQ-111, REQ-112, REQ-114; T051/T053 |

## 브라우저 smoke와 부품 시험

### V10 — 공통 틀·설정·기간

실제 BASE_URL/UI-27에서 수행하고 네트워크·DB·이력·새로고침 증거를 함께 남긴다. 로그인/역할403을 기대하지 않는다. UI-27의 PC 안내도 권한 오류가 아니다.

| 여정·행동 | 눈에 보이는 결과와 DB/API 교차 증거 | canonical_id |
| --- | --- | --- |
| US1 메뉴·직접 /UI-27·새로고침·#UI-27 진입 | DB메뉴10묶음30화면, 공식 경로 동일·루프0·초기 진단0. FX-MENU 오류 노드/자손 제외·DB 자동수정0 | REQ-035, REQ-036, REQ-037, REQ-038 |
| 탭8개 후9번째 열기·LRU 취소·일반 전환·새로고침 | 대시보드 닫기 불가·취소 새탭0·편집/필터/기간/스크롤 유지·dirty●·새로고침 URL+대시보드만 | REQ-043, REQ-044, REQ-046 |
|1280/1024/375/360px에서 화면 확대·축소 | header56·menu240/64·여백24/16, 모바일탭없음·본문가로넘침0·표내부스크롤·UI27 PC안내/확대 편집복원 | REQ-039, REQ-040 |
| 헤더/제목과 AI·알림·사용자 메뉴 탐색 | 개발용 임시 관리자·화면설정만, AI/알림 준비 중·가짜 배지0·UI27 업무기간줄없음 | REQ-025, REQ-041, REQ-042 |
| US2/US3 설정·별10개/11번째·▲▼·6개 화면 재방문 | FAV10한도·순서서버복원, RECENT중복없는5·재방문선두, PC전용은 모바일 표시만 숨김 | REQ-047, REQ-048, REQ-050, REQ-051, REQ-052, REQ-058 |
| PAGE_SIZE/DENSE/COLS/AMT_UNIT 저장 후 새로고침 | 정수20/50/100·밀도 전 열린표 동기화·필수열숨김 거부·금액단위 선택만·편집값 유지·실패시 이전 적용값 | REQ-053, REQ-054, REQ-055, REQ-056, REQ-057, REQ-059, REQ-060 |
| US4 YEAR/YEAR_MONTH/누계 기간 변경 | CASH_BASE_YM/서버현재일 범위·누계 연월 일치·실제 CLOSE/WORK 배지, NONE쓰기400·역전 운영시작전/저장0 | REQ-061, REQ-063, REQ-064, REQ-065, REQ-066 |
| 두 테스트 부모에서 controlled PeriodSelector 조작 | type/value/min/max/disabled 부모소유·범위밖/disabled 이벤트0·유효1·부품내 DB/저장0 | REQ-062 |
| 모달 Tab·닫기·잘못된 입력 제출 | focus trap·복원·label·첫오류포커스, 입력/중복클릭 차단·자동저장0 | REQ-033, REQ-034, REQ-045 |

### V11 — UI-27의 독립 완료 결과

| 여정 | 수행·기대 결과 | canonical_id |
| --- | --- | --- |
| US5 코드 편집 | 첫그룹/스크롤/정렬·중지 흐림·다중행 변경분만 저장·다른 페이지 보존·취소 DB0·중복코드400·CTRT_TYPE TK 이름만 성공/이름+useYn409전체불변 | REQ-069, REQ-070, REQ-071, REQ-072, REQ-073, REQ-074, REQ-078 |
| US5 추가값·중지 | JOB/SKILL/RSM CSV정규화·최종 신규참조·기존중지유지·JOB15자리·PROJ_STEP0/100정수·BANK앞0·RSM8값·STOP/RESUME/active제외/includeInactive label | REQ-020, REQ-080, REQ-081, REQ-082, REQ-083, REQ-084, REQ-085, REQ-086, REQ-087, REQ-092, REQ-093 |
| US6 그룹 추가 | 정상201/NEW·새그룹선택, 중복ID/trim동명이름 경합409·채번경합방지·미저장 계속작성시 추가0 | REQ-075, REQ-076, REQ-077 |
| US7 수당 |4열·기존값만·신규모달 DB0/하단저장 후 기본빈0·단위원/%·시스템우선·정밀값조회·프로젝트 팀비/경비/정산 변경0 | REQ-017, REQ-019, REQ-023, REQ-088, REQ-089, REQ-090, REQ-091 |
| US8 코드 삭제 | 참조1건409·미참조DEL_YN=Y/물리행/PK/DEL보존·재사용409·모든 고정 참조 fixture | REQ-079, REQ-094, REQ-095, REQ-096, REQ-097, REQ-098 |
| US9 기준 변경 |43키 비페이지·묶음4입력/AI8개2줄·변경칸만·최소월이전409·AI합/예정경계실패400전체불변 | REQ-099, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108, REQ-110 |
| US10 적용 이력 | 현재/예정/지난·적용월DESC·미래값 현재사용0·기준없음0대체없음 | REQ-100, REQ-128 |
| US11 자금 두 키 | 기존시작월 CLOSE409·새월우회0·미마감동일행/원래적용월/UPD·기간갱신·업무자료불변 | REQ-109, REQ-111, REQ-112, REQ-113, REQ-114 |
| US12 대응 검색 | sourceText부분일치/groupId·정렬/페이지/total·빈문구·행revision | REQ-068, REQ-115 |
| US13 대응 추가 | 즉시201/NEW JSON·같은표기같은그룹409/다른그룹허용·업무자료불변·수정은삭제후재등록 | REQ-116, REQ-117, REQ-118, REQ-121 |
| US14 대응 선택삭제 | 확인·정상물리삭제/DEL이전JSON·한행누락/변경409두행유지/DEL0·groupId필터식별 | REQ-118, REQ-119, REQ-120 |
| US15 변경이력 | 대상5종·필터/정렬/긴값펼침·CODE_NM·처리자·키구분자·500자무절단·과거이력유지·쓰기API없음 | REQ-122, REQ-123, REQ-124, REQ-125, REQ-131, REQ-155 |

UI-27 네 탭은 같은 /UI-27이다. 새 하위 screenId/route를 만들지 않는다. DataTable<T>를 코드·대응·기준·이력이 실제 공유하는 Props/이벤트/사용처를 코드로 확인한다. 레퍼런스 시각 관찰·비교는 T019/T069 및 후속 ui-design.md에서 확보하며 이번 배치의 브라우저 관찰로 주장하지 않는다. 모든 화면 loading/empty/error 구분, 이번 permission 처리는 인증차단 대신 PC 안내/업무409 조건과 구별한다(REQ-012, REQ-067, REQ-137, REQ-141).

### V12 — 범위 회귀

UI-27 외 화면은 제목·해당 기간·“이 화면은 다음 단계에서 만듭니다.”만 보인다. 가짜수치/무동작저장/계산/업로드0개, 마감쓰기0개, AI/메일/엑셀입출력/인쇄/다크모드0개, UI-20/일시간단가/근태0개, 주소입력/행정구역적재0개, 계산규칙편집/엔진0개, 그룹변경삭제0개, 기준키신규/삭제/메타편집0개, 변경이력수정삭제0개를 실제 라우트/API/화면등록표로 확인한다. DDL 68테이블 존재는 해당 기능 구현의 허가가 아니다(T070; REQ-147, REQ-148, REQ-149, REQ-150, REQ-151, REQ-152, REQ-153, REQ-154, REQ-155).

## 빌드·단위/통합/E2E 시험 실행

### V13 — 실제 manifest 확인 후 runner 확정

현재 프로젝트 소스가 없으므로 npm test·test:e2e·Gradle wrapper 존재를 발명하지 않는다. 다음은 구현 저장소에서 build script와 Gradle test가 실제 있는지 확인하고 실행하는 조건부 명령이다. task 미존재/시험0개/통합DB 미연결이면 미검증이며 단순 exit0을 전체 합격으로 쓰지 않는다.

```bash
(cd frontend && npm run)
node -e 'const p=require("./frontend/package.json"); if (!p.scripts?.build) throw Error("build script 미정: T071 확인 필요");'
(cd frontend && npm ci && npm run build)
if test -f backend/gradlew; then
  (cd backend && bash ./gradlew tasks --all && bash ./gradlew test)
else
  printf '%s\n' 'backend Gradle wrapper 미확보: T002/T071에서 실제 빌드 명령 확정 필요' >&2
  exit 1
fi
read -r -p '확인한 frontend 부품시험 script 이름: ' FRONT_TEST_SCRIPT
read -r -p '확인한 브라우저 E2E script 이름: ' E2E_TEST_SCRIPT
export FRONT_TEST_SCRIPT E2E_TEST_SCRIPT
node -e 'const p=require("./frontend/package.json"); for (const k of [process.env.FRONT_TEST_SCRIPT,process.env.E2E_TEST_SCRIPT]) if(!k || !p.scripts?.[k]) throw Error("시험 script 미확인: "+k);'
(cd frontend && npm run "$FRONT_TEST_SCRIPT" && npm run "$E2E_TEST_SCRIPT")
```

npm ci는 실제 lockfile이 있는 경우만 사용하며 다른 승인 package manager/lockfile이면 T002에서 명령을 정합화한다. fixture 실행은 data-model.md의 FX 목록과 tasks.md의 T007/T014/T024/T033/T039/T042/T045/T051/T060/T063을 실제 시험 파일에 연결한다. backend test가 통합시험을 포함하지 않는 구조라면 실제 integration task를 tasks --all에서 확인·추가 실행하고 DB 주소가 검증 전용인지 확인한다. 시험 runner용 DB 환경변수 이름·비밀값·유효기간 정책은 추정하지 않는다. 필수 시험은 실제 PostgreSQL migration/재시작/트랜잭션/경합/전수 참조·React 부품·브라우저 E2E이며 mock만의 성공은 실패다(REQ-028, REQ-030, REQ-062, REQ-145, REQ-146).

## 실패·복구·재시작·종료

### V14 — DB 단절·저장 후 재조회 실패

독립 검증 서비스에만 적용한다. 개발자/사용자의 다른 DB를 중단하지 않는다. stop이 실패하거나 연결 대상 확인이 안 되면 중단한다.

```bash
compose stop "$DB_SERVICE"
trap 'compose start "$DB_SERVICE" >/dev/null' EXIT
result=$(curl -sS -w '\n%{http_code}' "$BASE_URL/api/health")
test "${result##*$'\n'}" = 503
printf '%s\n' "${result%$'\n'*}" | jq -e '.success == false and .code == "DB_UNAVAILABLE" and (.requestId | length > 0)'
compose start "$DB_SERVICE"
trap - EXIT
compose ps
curl -fsS "$BASE_URL/api/health" | jq -e '.success == true'
```

Expected result: command exits successfully and the documented check passes.

기대: 실제DB SELECT 실패503·복구200, 화면 오류 띠/문의번호/다시 시도·입력 보존·자동 저장/재시도0회. DB/서비스 readiness 이후 복구를 재확인하며 자동 쓰기 재전송을 하지 않는다. 별도의 네트워크 시험으로 저장 commit 성공 뒤 GET만 실패시켜 “저장됐지만 다시 불러오지 못했습니다.”를 확인하고 DB 본체/이력은1번만 commit되었는지 읽는다. 성공 응답 직후 performance 관측으로2초 이내 저장 브라우저 캐시/열린선택목록/설정 갱신, 다른 브라우저 다음조회 최신값을 확인한다(REQ-032, REQ-033, REQ-034, REQ-130, REQ-156).

### V15 — 재시작 중복0과 안전한 종료

```bash
compose restart "$BACKEND_SERVICE"
compose ps
curl -fsS "$BASE_URL/api/health" | jq -e '.success == true'
compose down
```

Expected result: command exits successfully and the documented check passes.

재시작 직전/직후 DB 행수·식별자·적용 migration을 비교해 중복0건을 확인한다. 쓰기 smoke를 이미 실행했다면 최초 seed 수량이 아니라 재시작 직전 상태가 비교 기준이다. 정상3서비스 healthy와 frontend 직접접근/새로고침을 최종 확인한 후 down한다. volume 삭제 옵션은 붙이지 않는다. 중복이면 Flyway 이력/적용 여부를 조사하고 적용 SQL 수정·재실행·자동 baseline·임의 초기화로 숨기지 않는다(REQ-007, REQ-008, REQ-009, REQ-133, REQ-136).

## 문제 해결과 보고

| 증상 | 확인·대응 | 금지 사항·완료 판단 |
| --- | --- | --- |
| 파일/manifest 없음 | 실제 구현 저장소인지 T002 확인 | 문서 폴더에 임의 서버·테스트 script를 만들지 않고 미검증 |
| 버전/영속성 충돌 | OQ-001/OQ-007·T001 승인 증거 확인 | jpa/mybatis 자동선택·병렬 도입·JPA validate 합격 주장 금지 |
| Flyway 설치 실패/구조 차이 | 실제 오류와 원본 SQL/DDL 표 대조·트랜잭션 rollback 관측 | 임의 DROP/TRUNCATE·자동 baseline·적용 migration 수정 금지 |
| API가 HTML 반환/직접 경로404 | nginx /api 프록시와 SPA fallback 분리 확인 | API 실패를 index.html로 덮지 않음 |
|409 STALE_WRITE | 현재 DB 재조회·차이 안내, 입력 보존 후 사용자 결정 | stale 요청 자동재저장 금지 |
|409 SYSTEM_CODE_LOCKED/VALUE_CODE_FIELD_LOCKED | SYS 우선·모드별 편집 가능 필드 확인 | 이름과 금지필드 혼합요청 부분성공 금지 |
|409 CODE_IN_USE | 참조 유형/건수·고정 guard fixture 확인 | 참조 삭제·비활성자료 제외·PK 재사용으로 우회 금지 |
|409 CLOSED_MONTH | 기존 시작월 CLOSE 또는 기준 최소월 확인 | 새 시작월/마감자료 변경으로 우회 금지 |
| 기준404/목록 기준값 없음 | 요청월 이하 유효행/키 존재 확인 |0/하드코딩 값 대체 금지 |
| 손상 설정·열 설정400 | 해당 항목 error/revision 확인·명시적 개별초기화 | 자동삭제·다른설정 변경 금지 |
| 모바일 UI-27 안내 |768px 미만 PC_ONLY 동작, 확대 편집복원 확인 | 로그인/403 오류로 바꾸지 않음 |
| DB 단절503 | DB 복구 후 health200·GET 재시도, 입력 유지 | 비밀 접속값·SQL/스택 노출·자동 저장 금지 |
| OQ-003/OQ-004 제안 차이 | 승인된 API 배치와 구현 응답 대조·소유자 결정 요청 | 이 문서만으로 wire 계약 재정의 금지 |

실행 보고에는 날짜/환경·명령·종료코드·비밀 제거 관측값·요구/작업ID·SQL revision/raw 및 실행본 hash/diff·DB 본체/이력/브라우저 재조회·2초 관측값·공통 Props 실제 사용처·미결OQ를 남긴다. 미실행·차단·실패·통과를 구분한다. 이번 배치에서 위 모든 제품 절차는 미실행/미검증이며 문서 자체검사 결과를 제품 검증 결과로 옮기지 않는다.

## 공통 계약 참조

승인 plan.md의 technology_stack·version_bom·required_outputs 값을 그대로 보존한다(REQ-138, REQ-139, REQ-140). 아래 표기는 OQ-001의 버전 충돌을 해소한 승인이 아니다.

```json
{
  "technology_stack": {
    "Frontend": "React + TypeScript + Vite",
    "Backend": "Java 17 + Spring Boot",
    "Database": "PostgreSQL",
    "Infrastructure": {
      "runtime": "docker",
      "healthcheck_required": true
    }
  },
  "version_bom": {
    "spring_boot": "3.3.x",
    "node": "20.x",
    "vite": "5.x",
    "react": "18.x",
    "postgres": "16.x"
  },
  "required_outputs": {
    "backend_dir": "backend",
    "frontend_dir": "frontend",
    "compose_file": "infra/docker-compose.yml",
    "health_endpoint": "/api/health"
  }
}
```

실행·영속성 의미상 정본은 plan.md의 exact marker JSON이다. 이 문서에 별도 정본을 만들지 않는다. 블록 값 resolved/imperative/mybatis/blocking/explicit/conflicts=[]와 승인 원장 REQ-144·spec.md의 jpa 및 REQ-135의 JPA ddl-auto=validate가 충돌한다. OQ-007 해소 전 구현·영속성 시험을 완료로 처리하지 않는다. imperative/blocking 요청 흐름을 유지하고 R2DBC·WebFlux·worker·202/polling/callback을 임의 도입하지 않는다.

## 요구 회계

각 행은 이 문서의 설계 판단/검증 절차에 대한 연결이지 구현·실행 합격이 아니다. covered는 아래 지정한 연산 또는 기존 화면/테이블의 문서 내 증거를 의미한다. 실제 제품 결과는 미검증이며 미결은 별도 승인 전 충족으로 판단하지 않는다. 승인 원장 156개를 각1행으로 보존한다.

| canonical_id | status | evidence |
| --- | --- | --- |
| REQ-001 | data-constraint | V01/V02·전체 SQL 원천 보존: HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |
| REQ-002 | data-constraint | V01/V02·행정구역 빈 테이블: 설치 후 행정구역 테이블 데이터 건수 0행이다. |
| REQ-003 | data-constraint | V01/V02·전체 DB 구조 보존: 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |
| REQ-004 | data-constraint | V01/V02·기본 초기값 적재: 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |
| REQ-005 | data-constraint | V01/V02·초기 자금 기준과 계산규칙: CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |
| REQ-006 | data-constraint | V01/V02·Flyway 실행본 래퍼 정리: 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. |
| REQ-007 | data-constraint | V01/V02·마이그레이션 불변·멱등성: 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |
| REQ-008 | data-constraint | V01/V02·설치 실패와 구조 은폐 금지: 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |
| REQ-009 | data-constraint | V01/V02·구조 차이 보고: 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |
| REQ-010 | data-constraint | V05/V09·T068·설치 후 쓰기 경계: 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |
| REQ-011 | data-constraint | V05/V09·T068·서버 감사 필드와 연월 저장: REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |
| REQ-012 | screen-only | V10·공통 틀/UI-27의 공통 컴포넌트 실제 재사용: 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. |
| REQ-013 | covered | V11 — putPreference/createCodeGroup/saveGroupCodes: 문자열 입력 검증 — 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. |
| REQ-014 | covered | V11 — createCodeGroup: 그룹ID 정규화 — 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. |
| REQ-015 | covered | V11 — saveGroupCodes: 코드 정규화 — 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. |
| REQ-016 | covered | V11 — createCodeGroup/saveGroupCodes: 명칭 경계와 식별자 불변 — 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. |
| REQ-017 | covered | V09 — saveGroupCodes/saveOperatingStandards: 정밀 십진값 범위 — 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. |
| REQ-018 | covered | V11 — saveGroupCodes/saveOperatingStandards: 숫자 입력 정리와 금지값 — 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. |
| REQ-019 | covered | V09 — saveGroupCodes/saveOperatingStandards: 소수 원값 보존 — 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. |
| REQ-020 | covered | V11 — saveGroupCodes: JOB 월단가 정수 — 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. |
| REQ-021 | covered | V04 — getMonthClose/putPreference/saveOperatingStandards: 실제 연월 검증 — 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. |
| REQ-022 | covered | V11 — putPreference/saveGroupCodes: Y/N·JSON 타입 — true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. |
| REQ-023 | covered | V09 — saveGroupCodes/saveOperatingStandards: 빈 수당·기준값 구분 — 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. |
| REQ-024 | screen-only | V10·공통 틀/UI-27의 공통 표시 함수: 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다. |
| REQ-025 | covered | V03 — getCurrentUser: 임시 사용자 컨텍스트 — GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다. |
| REQ-026 | covered | V05/V09·T068 — putPreference/deletePreference/createCodeGroup: 처리자 주입 거부 — 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. |
| REQ-027 | covered | V11 — getCurrentUser/putPreference/deletePreference: 인증·권한 유예 — /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다. |
| REQ-028 | covered | V06 — createCodeGroup/saveGroupCodes/deleteGroupCode: 원자적 업무 변경 — 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. |
| REQ-029 | covered | V06 — putPreference/deletePreference/getGroupCodes: 불투명 revision — 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. |
| REQ-030 | covered | V06 — putPreference/deletePreference/saveGroupCodes: 잠금 후 동시 수정 검출 — 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. |
| REQ-031 | covered | V06 — putPreference/saveGroupCodes/saveOperatingStandards: 실제 변경 없는 저장 — 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. |
| REQ-032 | covered | V14 — putPreference/deletePreference/createCodeGroup: commit 후 캐시 최신화 — 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다. |
| REQ-033 | screen-only | V14·공통 틀/UI-27의 한국어 오류와 재조회 실패 구분: 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다. |
| REQ-034 | screen-only | V14·공통 틀/UI-27의 중복 클릭·자동 재저장 금지: 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다. |
| REQ-035 | covered | V03 — getMenus: DB 메뉴 구성·정렬 — 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다. |
| REQ-036 | covered | V10 — UI-27: 메뉴 펼침·접힘 탐색 — 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다. |
| REQ-037 | covered | V10 — getMenus: 잘못된 메뉴 트리 진단 — 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다. |
| REQ-038 | covered | V10 — UI-27: 공식 라우트와 복구 — /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다. |
| REQ-039 | screen-only | V10·공통 틀/UI-27의 PC 전용 화면 보호: 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다. |
| REQ-040 | screen-only | V10·공통 틀/UI-27의 반응형 공통 레이아웃: 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다. |
| REQ-041 | screen-only | V10·공통 틀/UI-27의 헤더의 실제 제공 항목: AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다. |
| REQ-042 | screen-only | V10·공통 틀/UI-27의 화면 제목·기간 배치: UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. |
| REQ-043 | screen-only | V10·공통 틀/UI-27의 화면 탭과 LRU: 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다. |
| REQ-044 | screen-only | V10·공통 틀/UI-27의 편집 보존과 이동 경고: 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다. |
| REQ-045 | screen-only | V10·공통 틀/UI-27의 접근 가능한 모달·입력: 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다. |
| REQ-046 | screen-only | V10·공통 틀/UI-27의 새로고침 탭 초기화: 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. |
| REQ-047 | covered | V03 — getPreferences: 설정 조회와 기본값 — 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다. |
| REQ-048 | covered | V08/V10 — putPreference: 설정 명시 저장 — 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. |
| REQ-049 | covered | V08/V10 — getPreferences/putPreference: 손상 설정 격리 — 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. |
| REQ-050 | covered | V08/V10 — deletePreference: 개별 설정 초기화 — 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다. |
| REQ-051 | covered | V08/V10 — getPreferences/putPreference: 즐겨찾기 편집 — 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. |
| REQ-052 | screen-only | V08/V10·공통 틀/UI-27의 최근 화면 자동 갱신: 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |
| REQ-053 | covered | V08/V10 — getPreferences/putPreference: 메뉴 접힘 기억 — PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. |
| REQ-054 | covered | V08/V10 — getPreferences/putPreference: 목록 크기 설정 — 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. |
| REQ-055 | covered | V08/V10 — getPreferences/putPreference: 밀도 설정 동기화 — 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. |
| REQ-056 | covered | V08/V10 — getPreferences/putPreference: 표 열 설정 저장 — 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. |
| REQ-057 | covered | V08/V10 — getPreferences/putPreference: 금액 단위 선택 기억 — 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. |
| REQ-058 | covered | V08/V10 — getPreferences/putPreference: 설정 화면ID·표시 보호 — 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. |
| REQ-059 | covered | V08/V10 — getPreferences/putPreference: UI-27 열 정의 제한 — codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. |
| REQ-060 | covered | V08/V10 — getPreferences/putPreference: 설정 적용과 실패 보존 — 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다. |
| REQ-061 | covered | V10 — getPreferences/putPreference: 기간 설정 타입·범위 검사 — NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. |
| REQ-062 | screen-only | V10·공통 틀/UI-27의 controlled 기간 부품: YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다. |
| REQ-063 | covered | V03 — getPeriodContext: 화면 기간 선택·기억 — 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다. |
| REQ-064 | covered | V10 — getPeriodContext: 기간 범위와 운영 전 상태 — YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. |
| REQ-065 | covered | V10 — getPeriodContext: 연도 배지 — 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다. |
| REQ-066 | covered | V03 — getMonthClose: 실제 월 마감 조회 — CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다. |
| REQ-067 | screen-only | V11·공통 틀/UI-27의 UI-27 네 탭과 조회 상태: 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. |
| REQ-068 | covered | V04 — getGroupCodes/getCodeMappings/getOperatingStandards: 표 페이지 계약 — page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. |
| REQ-069 | covered | V03 — getCodeGroups: 그룹 목록과 선택 — 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다. |
| REQ-070 | covered | V11 — getGroupCodes: 일반 코드 조회 — GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다. |
| REQ-071 | covered | V11 — saveGroupCodes: 일반 코드 일괄 변경 저장 — 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다. |
| REQ-072 | covered | V11 — UI-27: 편집 취소 복원 — 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다. |
| REQ-073 | covered | V11 — saveGroupCodes: 부분 변경·중복 코드 검사 — 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. |
| REQ-074 | covered | V09 — saveGroupCodes/deleteGroupCode: 시스템 코드 변경 제한 — CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. |
| REQ-075 | covered | V11 — createCodeGroup: 그룹 중복과 채번 경합 — 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. |
| REQ-076 | covered | V05 — createCodeGroup: 업무 그룹 생성 — POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다. |
| REQ-077 | covered | V11 — UI-27: 그룹 추가 전 편집 처리 — 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다. |
| REQ-078 | covered | V11 — saveGroupCodes: 일반 신규 코드 편집 행 — 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다. |
| REQ-079 | covered | V07 — saveGroupCodes/deleteGroupCode: 그룹 내 코드 PK 재사용 금지 — DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. |
| REQ-080 | covered | V05 — saveGroupCodes: 신규 코드 기본값·정렬 채번 — 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. |
| REQ-081 | covered | V11 — saveGroupCodes: 정렬 순번 한계 — max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. |
| REQ-082 | covered | V11 — saveGroupCodes: 코드명 중복 허용 — 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. |
| REQ-083 | covered | V11 — saveGroupCodes: 그룹별 추가값 필수·길이 — 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. |
| REQ-084 | covered | V11 — saveGroupCodes: PROJ_STEP 진척 정수 — 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. |
| REQ-085 | covered | V11 — saveGroupCodes: BANK 코드와 RSM 라벨 — 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. |
| REQ-086 | covered | V11 — saveGroupCodes: RSM_LABEL 허용값 — name 입력은 NAME으로 저장; 목록 외 문자열은400이다. |
| REQ-087 | covered | V11 — saveGroupCodes: CSV 정규화·참조 — 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. |
| REQ-088 | covered | V11 — getGroupCodes: 수당 코드 조회·편집 형태 — 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다. |
| REQ-089 | covered | V09 — saveGroupCodes/deleteGroupCode: 값 코드 잠금 — 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. |
| REQ-090 | covered | V11 — saveGroupCodes: 수당 신규 코드 저장 — 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. |
| REQ-091 | covered | V03 — saveGroupCodes/getCodes: 수당 값 변경과 업무 불변 — ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다. |
| REQ-092 | covered | V11 — saveGroupCodes/getCodes: 일반 코드 중지·재개 — 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. |
| REQ-093 | covered | V11 — saveGroupCodes: 사용 상태 변경 이력 — USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다. |
| REQ-094 | covered | V07 — deleteGroupCode: 삭제 참조 차단 — 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. |
| REQ-095 | covered | V07 — deleteGroupCode: 일반 코드 논리 삭제 — 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다. |
| REQ-096 | covered | V07 — deleteGroupCode: 코드 삭제 이력 — 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다. |
| REQ-097 | covered | V07 — deleteGroupCode: 고정 참조 가드 — 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. |
| REQ-098 | covered | V07 — deleteGroupCode: 삭제 직전 재검사 — 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. |
| REQ-099 | covered | V03 — getOperatingStandards: 운영 기준 전체 조회 — 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다. |
| REQ-100 | covered | V09 — getOperatingStandardHistory: 기준 현재·과거·미래 이력 — 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다. |
| REQ-101 | covered | V09 — getOperatingStandards/saveOperatingStandards: 일반 기준 적용 최소월 — 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. |
| REQ-102 | covered | V09 — saveOperatingStandards: 기준 월별 추가·덮어쓰기 — 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다. |
| REQ-103 | covered | V09 — saveOperatingStandards: 기준 변경 키 중복 거부 — 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. |
| REQ-104 | covered | V09 — getOperatingStandards/saveOperatingStandards: 묶음 기준 표시 — AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다. |
| REQ-105 | covered | V09 — saveOperatingStandards: DUN 순서 검증 — D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. |
| REQ-106 | covered | V09 — saveOperatingStandards: 확률 범위 검증 — 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. |
| REQ-107 | covered | V09 — saveOperatingStandards: AI 점수 합계 검증 — 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. |
| REQ-108 | covered | V09 — saveOperatingStandards: 철수 통보일 정수 — 1과99,999,999,999 허용; 0 또는 소수는400이다. |
| REQ-109 | covered | V09 — getOperatingStandards/saveOperatingStandards: 자금 시작 연월 표현 — 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. |
| REQ-110 | covered | V09 — saveOperatingStandards: 기준 전체 영향구간 검사 — 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. |
| REQ-111 | covered | V09 — getOperatingStandards/saveOperatingStandards: 자금 두 키 즉시 갱신 — 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. |
| REQ-112 | covered | V09 — saveOperatingStandards: 자금 기준 변경 이력 — 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다. |
| REQ-113 | covered | V09 — getOperatingStandards/saveOperatingStandards: 기존 시작월 마감 잠금 — 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. |
| REQ-114 | covered | V09 — saveOperatingStandards: 자금 동시 잠금·후속 갱신 — 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. |
| REQ-115 | covered | V03 — getCodeMappings: 표기 대응 목록·검색 — SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다. |
| REQ-116 | covered | V11 — createCodeMapping: 대응 입력·중복 검사 — 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. |
| REQ-117 | covered | V05 — createCodeMapping: 대응 즉시 등록 — POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다. |
| REQ-118 | covered | V07 — createCodeMapping/deleteCodeMappings/getCommonCodeHistory: 대응 등록·삭제 이력 — NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. |
| REQ-119 | covered | V07 — deleteCodeMappings: 대응 다중 물리 삭제 — 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다. |
| REQ-120 | covered | V07 — deleteCodeMappings: 대응 삭제 충돌 — 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. |
| REQ-121 | covered | V07 — createCodeMapping/deleteCodeMappings: 대응 변경의 업무 불변 — 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다. |
| REQ-122 | covered | V03 — getCommonCodeHistory: 변경 이력 조회 — REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다. |
| REQ-123 | covered | V05 — saveGroupCodes/deleteGroupCode/createCodeMapping: 변경 유형과 과거 이름 추적 — 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. |
| REQ-124 | covered | V11 — getCommonCodeHistory: 이력 필터 도메인 — 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. |
| REQ-125 | covered | V05 — saveGroupCodes/deleteGroupCode/createCodeMapping: 이력 키·전후값 무손실 — 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. |
| REQ-126 | covered | V03 — getCodes: 공통 active 코드 조회 — GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다. |
| REQ-127 | covered | V03 — getParameter: 월별 단일 기준 조회 — GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다. |
| REQ-128 | covered | V04 — getParameter: 기준 미존재 거부 — 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. |
| REQ-129 | covered | V04 — getHealth/getCurrentUser/getMenus: API 필드와 서버 필드 보호 — SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. |
| REQ-130 | covered | V04 — getHealth/getCurrentUser/getMenus: 공통 오류 반환과 비밀 보호 — 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. |
| REQ-131 | data-constraint | V11·변경 이력 무기한 보존: 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |
| REQ-132 | non-functional | V13·착수 게이트·원문 구현 기술 요구: 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |
| REQ-133 | non-functional | V13·착수 게이트·3서비스 실행과 SPA: /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |
| REQ-134 | non-functional | V01/V02·서울 시간대 일관성: 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |
| REQ-135 | non-functional | V01/V02·Flyway 전용 생성/BigDecimal 유지; JPA ddl-auto=validate는 OQ-007 상류 정합화 승인 전 구현·시험 미확정 |
| REQ-136 | non-functional | V01/V02·마이그레이션 파일 경로: backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |
| REQ-137 | non-functional | V13·착수 게이트·프론트 구조와 계층 책임: UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |
| REQ-138 | non-functional | V13·착수 게이트·고정 technology_stack: React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |
| REQ-139 | non-functional | V13·착수 게이트·고정 Version BOM: BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |
| REQ-140 | non-functional | V13·착수 게이트·고정 required_outputs: backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |
| REQ-141 | non-functional | V13·착수 게이트·레퍼런스 시각 계약: sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |
| REQ-142 | non-functional | V13·착수 게이트·Docker 빌드 컨텍스트 제외: 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |
| REQ-143 | non-functional | V13·착수 게이트·격리 환경만 배포: 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |
| REQ-144 | non-functional | V13·착수 게이트·plan.md exact mybatis 블록을 정본으로 참조하되 원장 REQ-144의 jpa 의미는 보존; OQ-007 충돌로 요구 충족·build-ready 미확정 |
| REQ-145 | non-functional | V13·착수 게이트·실제 백엔드 산출물: backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |
| REQ-146 | non-functional | V13·착수 게이트·프론트·API 함께 검증: 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |
| REQ-147 | out-of-scope | V12·후속 업무 화면 경계: UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. 확인: 다른 업무 화면의 가짜 수치·무동작 저장버튼0개, 계산/저장/업로드0개다. |
| REQ-148 | out-of-scope | V12·월 마감 쓰기 제외: 월 마감/해제/스냅샷 생성은 구현하지 않는다. 확인: 마감 상태 읽기는 존재하되 마감 변경 API/생성 동작0개다. |
| REQ-149 | out-of-scope | V12·외부·출력·외형 확장 제외: 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. 확인: 헤더 준비 중 안내 외 AI/알림 업무0개·엑셀 파서/업로드/다운로드0개다. |
| REQ-150 | out-of-scope | V12·UI-20·단가·근태 제외: UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 확인: 메뉴/UI등록표/API에 UI-20이 없고 일/시간 단가 및 근태 업로드 동작0개다. |
| REQ-151 | out-of-scope | V12·행정구역 파일 작업 제외: 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. 확인: 주소입력 기능/행정구역 적재작업 없이 DDL 제약과0행 상태로 검증한다. |
| REQ-152 | out-of-scope | V12·계산 엔진과 규칙 편집 제외: 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. 확인: 94행 규칙 데이터가 있어도 규칙 편집 UI/계산 실행 API0개다. |
| REQ-153 | out-of-scope | V12·그룹 변경·삭제 제외: 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. 확인: 그룹 추가/조회만 있고 그룹 수정/삭제 UI·API0개다. |
| REQ-154 | out-of-scope | V12·기준 키 CRUD 확장 제외: 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. 확인: 값 변경 외 메타 수정/키 추가/키 삭제 API0개다. |
| REQ-155 | out-of-scope | V12·이력 변경·삭제 제외: 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. 확인: 이력 GET만 존재하며 기존/오래된 이력 삭제·수정 경로0개다. |
| REQ-156 | covered | V03 — getHealth: 실제 DB health 조회 — 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다. |
