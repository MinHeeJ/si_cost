# 기능 명세: 원가관리시스템 기반 구축·공통 화면·공통코드 관리
<!-- speckit:section sec-0f8855d5140f49c7b0de1b1816e23280 -->

기능 식별자: 001-feature / BASIC-129 / si-cost

작성 기준일: 2026-11-10. 상태: 검토 후보 — OQ-001 기술 버전 충돌 해소 전 구현 준비 완료가 아니다. Git 저장소 메타데이터가 없는 문서 작업 폴더이므로 branch 이름은 확인 불가다.

입력: SPEC-REQUEST.md 1~476행 전체. 양식: .specify/templates/spec-template.md의 사용자 시나리오·요구사항·핵심 데이터·측정 결과·가정 구조를 유지하고 추적 계약을 확장했다. 이번 호출 산출물은 spec.md와 원장 sidecar 두 개뿐이다. 다른 8개 관리 문서는 후속 단계에서 생성한다. 구현·DB·브라우저 검증을 실행한 것으로 주장하지 않는다.

## 범위와 계약 우선순위
<!-- speckit:section sec-0d69bff16f28419e9edbd9ea4e8e849e -->

실제 구현은 전체 DB/초기값, 임시 관리자·공통 서비스, DB 메뉴·공통 틀·탭·설정·기간, UI-27 네 탭의 조회/변경/저장/이력이다. 다른 등록 화면은 제목·해당 기간 줄·“이 화면은 다음 단계에서 만듭니다.”만 제공한다. 임시 관리자에서도 시스템 코드·중복·참조·마감 보호는 실제 적용한다.

명확화(451~476행)는 삭제 정책·시스템+값 그룹의 우선순위·격리 환경·이력 무기한 보존을 확정한다. estimated:true 또는 추정 초안은 승인으로 취급하지 않는다. 원문 없는 역할/상태/CRUD/비밀번호 흐름은 만들지 않는다. 고정 technology_stack/version BOM/required_outputs는 보존하되 상세 본문과의 버전 충돌은 OQ로 드러낸다.

## Source Decomposition Ledger
<!-- speckit:section sec-bbed8f9cd41e417086cfc8270d35118a -->

같은 원문 선언 안의 독립 행동을 아래처럼 분리한다. 부모 포괄 REQ를 별도로 만들지 않는다. source_id=null은 명시된 요구 ID가 없다는 뜻이며 괄호에 실제 원문 위치와 부모 기능을 표시한다. 원문이 여러 문장/행으로 설명하는 기능도 등록·조회·검증·이력의 독립 요구로 보존한다.

| source_id | parent capability | canonical_id | behavior class | source-backed behavior | child acceptance proof |
| --- | --- | --- | --- | --- | --- |
| null | L17 / data-model.md | REQ-001 | integrity check | example/project_ddl.sql과 example/project_data.sql 전체를 DB와 초기값의 원천으로 사용한다. | HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |
| null | L22 / data-model.md | REQ-002 | transaction persistence | 행정구역은 DDL 테이블과 제약만 생성하고 데이터는 0행으로 유지한다. | 설치 후 행정구역 테이블 데이터 건수 0행이다. |
| null | L42 / data-model.md | REQ-003 | integrity check | 빈 개발 DB에 원문 수량과 모든 컬럼·제약·인덱스·시퀀스·코멘트를 보존한다. | 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |
| null | L43 / seed-data | REQ-004 | transaction persistence | 원문 초기값 수량과 HQ 적재 주체를 보존하고 대응·이력·설정은 빈 상태로 시작한다. | 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |
| null | L44 / data-model.md | REQ-005 | transaction persistence | 계산규칙과 초기 CASH_BASE_YM/CASH_BASE_AMT를 그대로 적재한다. | CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |
| null | L45 / research.md | REQ-006 | integrity check | data의 최상위 BEGIN 2개와 COMMIT 1개만 실행용 사본에서 제거하고 TEXT 내부 문자열을 보존한다. | 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. |
| null | L46 / data-model.md | REQ-007 | integrity check | 적용된 마이그레이션을 수정하거나 재실행하지 않고 재시작 중복 적재를 방지한다. | 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |
| null | L46 / data-model.md | REQ-008 | validation/rejection | 설치 실패는 트랜잭션 롤백하며 임의 DROP/TRUNCATE·자동 baseline으로 불일치를 숨기지 않는다. | 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |
| null | L47 / data-model.md | REQ-009 | validation/rejection | 이미 존재하는 DB 구조가 다르면 추정 변경 없이 차이와 실패 항목을 보고한다. | 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |
| null | L51 / data-model.md | REQ-010 | integrity check | 업무 쓰기는 명시한 6테이블에 한정하고 나머지 업무 테이블은 조회용이다. | 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |
| null | L62 / data-model.md | REQ-011 | transaction persistence | 등록·수정 필드는 서버가 기록하며 이력은 등록 필드만 쓰고 DB 연월은 YYYYMM으로 저장한다. | REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |
| null | L68 / global-ui | REQ-012 | integrity check | 공통 부품을 UI-27에 실제 적용하고 Props·이벤트·사용처를 기록하며 개별 업무 분기를 넣지 않는다. | 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. |
| null | L86 / input-service | REQ-013 | validation/rejection | trim 후 필수/길이를 검사하고 초과값을 잘라 저장하지 않는다. | 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. |
| null | L87 / input-service | REQ-014 | validation/rejection | 그룹ID를 대문자로 정규화하고 원문 패턴과 길이를 검사한다. | 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. |
| null | L88 / input-service | REQ-015 | validation/rejection | 코드를 대문자로 정규화하고 원문 패턴과 길이를 검사한다. | 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. |
| null | L89 / input-service | REQ-016 | validation/rejection | 그룹명 1~50자·코드명 1~100자를 검증하고 등록된 ID/코드는 변경하지 못한다. | 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. |
| null | L90 / input-service | REQ-017 | validation/rejection | CODE_VAL·STD_VAL을 숫자 문자열과 BigDecimal로 검사·저장한다. | 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. |
| null | L91 / input-service | REQ-018 | validation/rejection | 공백·쉼표·끝 원/%만 정리하고 음수·지수·NaN·Infinity·다른 문자를 거부한다. | 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. |
| null | L92 / input-service | REQ-019 | integrity check | 자동 반올림·버림 없이 허용 정밀도를 저장하고 화면 뒤 0만 생략한다. | 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. |
| null | L93 / input-service | REQ-020 | validation/rejection | JOB.ADD_VAL2는 정리 후 0~999,999,999,999,999원 정수 문자열로 저장한다. | 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. |
| null | L94 / input-service | REQ-021 | validation/rejection | 연월을 0001-01~9999-12의 실제 YYYY-MM으로 검사한다. | 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. |
| null | L95 / input-service | REQ-022 | validation/rejection | Y/N·JSON boolean·PREF_VAL 최대 2000자를 엄격히 검사한다. | true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. |
| null | L97 / input-service | REQ-023 | validation/rejection | 신규 수당 기본/빈 CODE_VAL은 0을 허용하고 기준값 빈칸은 거부하며 % 임의100 상한을 추가하지 않는다. | 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. |
| null | L97 / global-ui | REQ-024 | query | 금액·음수·0·NULL·일자·일시 및 숫자/문자/상태 정렬은 원문 표시 계약을 따른다. | 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다. |
| null | L101 / current-user | REQ-025 | query | CurrentUserProvider가 DB admin·roleIds=[ADMIN]·표시명 임시 관리자·developmentMode=true를 공급한다. | GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다.; api_endpoint:GET /api/current-user |
| null | L101 / current-user | REQ-026 | validation/rejection | 등록자·수정자·설정 USER_ID는 admin으로 고정하고 요청의 다른 처리자 지정을 거부한다. | 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. |
| null | L102 / current-user | REQ-027 | authorization | 현재 권한 서비스는 허용·DataScope=ALL이며 한 공급자로 교체 가능하고 인증 차단은 만들지 않는다. | /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다. |
| null | L103 / transaction-service | REQ-028 | transaction persistence | 코드 한 그룹·기준 한 요청·대응 추가/다중 삭제는 단일 트랜잭션이며 키 오름차순 잠금한다. | 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. |
| null | L104 / revision-service | REQ-029 | query | 조회 revision은 식별자·저장값·등록/수정 시각으로 계산하며 그룹 전체 코드·기준키 전체 이력을 포함한다. | 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. |
| null | L105 / revision-service | REQ-030 | validation/rejection | 대상 잠금 후 expectedRevision을 최신값과 비교하고 불일치면 409 STALE_WRITE다. | 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. |
| null | L106 / transaction-service | REQ-031 | transaction persistence | revision 일치 후 변경이 없으면 200 changed=false와 안내를 반환하고 수정시각·이력을 갱신하지 않는다. | 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. |
| null | L107 / cache-service | REQ-032 | query | 성공 응답 후 2초 이내 현재 브라우저 관련 캐시·선택목록·설정을 갱신하고 서버 캐시는 commit 후 무효화한다. | 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다. |
| null | L108 / global-ui | REQ-033 | query | 필드 오류·첫 포커스·오류 띠·다시 시도·requestId와 commit 후 재조회 실패를 구분한다. | 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다. |
| null | L108 / global-ui | REQ-034 | validation/rejection | 처리 중 중복 클릭을 막고 입력을 보존하며 자동 저장/재시도는 하지 않는다. | 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다. |
| null | L114 / DB 메뉴와 공식 경로 탐색 | REQ-035 | query | USE_YN=Y의 정상 DB 부모/자식과 이름·아이콘·PC 전용 여부를 읽고 형제를 SORT_ORD/MENU_ID순 표시한다. | 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다.; api_endpoint:GET /api/menus |
| null | L129 / DB 메뉴와 공식 경로 탐색 | REQ-036 | query | 현재 묶음 하나를 기본 펼치며 대시보드 직접 이동·부모 이름 이동/아이콘 토글·접힌 hover/focus 목록을 제공한다. | 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다. |
| null | L130 / DB 메뉴와 공식 경로 탐색 | REQ-037 | validation/rejection | 비정상 노드와 자손·중복 screen 탈락 트리를 제외하고 진단을 남기며 정상 부분만 표시한다. | 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다. |
| null | L131 / DB 메뉴와 공식 경로 탐색 | REQ-038 | query | /{screenId}·/→/UI-16·hash replace를 지원하고 유효 화면 부재 시 원문 순서로 복구한다. | /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다. |
| null | L132 / global-ui | REQ-039 | authorization | 폭768px 미만에서 PC 전용 메뉴/즐겨찾기/최근을 숨기고 직접 접근은 PC 안내를 표시한다. | 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다. |
| null | L136 / global-ui | REQ-040 | query | AppLayout·Header·Sidebar·ScreenTabs·TitleBar를 원문 치수와 breakpoint에 맞춰 배치한다. | 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다. |
| null | L137 / global-ui | REQ-041 | query | 메뉴 토글·대시보드 이동·임시 관리자/화면 설정을 제공하며 AI/알림은 준비 중 안내만 한다. | AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다. |
| null | L138 / global-ui | REQ-042 | query | 묶음›화면명·즐겨찾기·UI-ID·작업을 표시하고 업무 기간은 제목 아래에 둔다. | UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. |
| null | L139 / global-ui | REQ-043 | state transition | 화면을 재활성화 또는 새 탭으로 열고 대시보드 포함 최대8개·9번째는 닫을 수 있는 LRU 확인을 거친다. | 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다. |
| null | L140 / global-ui | REQ-044 | state transition | 단순 탭 전환은 상태를 보존하고 그룹/하위탭 변경·닫기·상태 폐기 이동은 미저장 확인을 거친다. | 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다. |
| null | L140 / global-ui | REQ-045 | query | 모달 focus trap·닫기 후 포커스 복원과 입력 label/Tab/첫 오류 포커스를 제공한다. | 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다. |
| null | L141 / global-ui | REQ-046 | state transition | 새로고침은 URL 화면과 고정 대시보드로 시작하며 열린 탭 목록은 서버 복원하지 않는다. | 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. |
| null | L145 / 화면 설정 저장·복원·초기화 | REQ-047 | query | admin 설정 GET은 value/revision/stored를 반환하며 행 없음은 기본값·MENU_FOLD만 null이다. | 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다.; api_endpoint:GET /api/me/preferences |
| null | L145 / 화면 설정 저장·복원·초기화 | REQ-048 | transaction persistence | 최초 PUT은 expectedRevision=null·기존은 토큰이며 기본값과 같아도 최초 명시 저장은 INSERT한다. | 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다.; api_endpoint:PUT /api/me/preferences/{type}/{key} |
| null | L145 / 화면 설정 저장·복원·초기화 | REQ-049 | validation/rejection | 잘못된 JSON은 항목 오류/revision과 기본값 임시 표시로 격리하고 자동 삭제하지 않는다. | 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. |
| null | L145 / 화면 설정 저장·복원·초기화 | REQ-050 | command | 이 설정 초기화는 해당 admin·type·key 행만 물리 삭제하며 UI-27 이력을 남기지 않는다. | 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다.; api_endpoint:DELETE /api/me/preferences/{type}/{key} |
| null | L149 / 즐겨찾기 구성 | REQ-051 | command | 화면 별 추가/해제·▲▼ 순서를 FAV에 저장하고 중복 없이 최대10개를 허용한다. | 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. |
| null | L150 / global-ui | REQ-052 | command | 활성화순 RECENT를 중복 없이 최대5개로 저장하고 재방문을 맨 앞으로 옮긴다. | 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |
| null | L151 / 화면 설정 저장·복원·초기화 | REQ-053 | command | MENU_FOLD boolean을 저장하고 미저장 PC=false·태블릿=true, 모바일 슬라이드는 임시 상태다. | PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. |
| null | L152 / 화면 설정 저장·복원·초기화 | REQ-054 | validation/rejection | PAGE_SIZE는 JSON 정수20/50/100만 저장하며 기본50이다. | 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. |
| null | L153 / 화면 설정 저장·복원·초기화 | REQ-055 | command | DENSE 넓게/좁게를 기본 넓게로 서버 저장하고 열린 목록과 사용자 메뉴에 동일 적용한다. | 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. |
| null | L154 / 화면 설정 저장·복원·초기화 | REQ-056 | command | COLS는 표별 전체 순서와 선택 숨김을 저장하고 필수열 숨김·중복·미등록열을 거부한다. | 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. |
| null | L155 / 화면 설정 저장·복원·초기화 | REQ-057 | command | AMT_UNIT 원/천원/백만원을 기본 천원으로 저장/복원하되 업무 금액 변환은 하지 않는다. | 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. |
| null | L158 / 즐겨찾기 구성 | REQ-058 | validation/rejection | 즐겨찾기/최근은 등록 screenId만 허용하고 PC 숨김은 배열 삭제가 아닌 표시만 숨긴다. | 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. |
| null | L159 / 화면 설정 저장·복원·초기화 | REQ-059 | validation/rejection | UI-27 viewId와 필수열을 고정하고 체크/작업열·아직 표 없는 다른 화면의 COLS를 받지 않는다. | codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. |
| null | L161 / 화면 설정 저장·복원·초기화 | REQ-060 | query | PAGE_SIZE·DENSE는 서버 성공 후 열린 목록에 반영하고 실패 시 이전값/편집값을 유지한다. | 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다. |
| null | L162 / 화면 업무 기간 선택 | REQ-061 | validation/rejection | PERIOD는 화면 유형·연월 일치·허용범위를 검사하고 NONE 쓰기를 거부하며 범위 밖 저장값을 조용히 바꾸지 않는다. | NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. |
| null | L166 / global-ui | REQ-062 | query | PeriodSelector는 부모의 type/value/min/max/disabled를 쓰고 DB 조회나 설정 저장을 내부에서 하지 않는다. | YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다. |
| null | L168 / 화면 업무 기간 선택 | REQ-063 | command | ScreenPeriodBar는 서버 현재일/설정과 연결하고 누계 연도·기준월을 범위로 제한하여 기간을 저장한다. | 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다.; api_endpoint:GET /api/period-context |
| null | L177 / 화면 업무 기간 선택 | REQ-064 | validation/rejection | CASH_BASE_YM와 현재일로 유형별 범위를 산정하고 범위 역전은 운영 시작 전·비활성·저장 금지다. | YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. |
| null | L178 / 화면 업무 기간 선택 | REQ-065 | query | 날짜 비교로 올해/지난 해·읽기 전용/내년·계획 배지를 표시하고 업무 계산/재계산은 하지 않는다. | 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다. |
| null | L179 / 화면 업무 기간 선택 | REQ-066 | query | isClosed는 시작월 이전 이관·결산완료, CLOSE는 마감, WORK/행 없음은 작업중으로 판단한다. | CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다.; api_endpoint:GET /api/month-close |
| null | L185 / global-ui | REQ-067 | query | 코드/엑셀 표기 대응/운영 기준값/변경 이력 4탭과 loading/empty/error를 구분한다. | 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. |
| null | L185 / pagination-service | REQ-068 | validation/rejection | 코드/대응/이력은 page≥1·size20/50/100·기본50, 기준값은 전체43키 비페이지 목록이다. | page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. |
| null | L186 / 그룹의 일반·시스템 코드 편집 | REQ-069 | query | 전체 그룹을 SORT_ORD/CODE_GRP_ID순으로 표시하고 첫 선택·코드 수·시스템 잠금·스크롤 위치를 제공한다. | 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다.; api_endpoint:GET /api/code-groups |
| null | L187 / 그룹의 일반·시스템 코드 편집 | REQ-070 | query | 일반 표는 지정 열과 저장된 SORT_ORD/CODE순이며 미삭제 중지 코드도 흐리게 표시한다. | GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다.; api_endpoint:GET /api/code-groups/{group}/codes |
| null | L187 / 그룹의 일반·시스템 코드 편집 | REQ-071 | command | 이름·사용·허용 추가값을 여러 행 편집하여 그룹 단위 저장1회로 반영한다. | 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다.; api_endpoint:PUT /api/code-groups/{group}/codes |
| null | L188 / 그룹의 일반·시스템 코드 편집 | REQ-072 | state transition | 취소는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. | 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다. |
| null | L188 / 그룹의 일반·시스템 코드 편집 | REQ-073 | validation/rejection | 저장은 변경분만 받고 다른 페이지 행은 삭제하지 않으며 동일 code 요청2회는 거부한다. | 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. |
| null | L189 / 그룹의 일반·시스템 코드 편집 | REQ-074 | validation/rejection | SYS_CODE_YN=Y는 이름만 변경하고 다른 변경이 섞이면 전체409 SYSTEM_CODE_LOCKED다. | CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. |
| null | L193 / 일반 업무 그룹 추가 | REQ-075 | validation/rejection | groupId/name 필수·중복ID/trim후 동일명 거부하며 그룹 테이블 직렬화 잠금으로 중복/채번 경합을 막는다. | 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. |
| null | L194 / 일반 업무 그룹 추가 | REQ-076 | transaction persistence | 새 그룹은 SYS=N·VAL=N·USE=Y·SORT=max+1로 저장하고 NEW 이력 후 선택/재조회한다. | POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다.; api_endpoint:POST /api/code-groups |
| null | L194 / 일반 업무 그룹 추가 | REQ-077 | validation/rejection | 미저장 코드가 있으면 저장/취소 후 그룹을 추가하며 임의 추가값 컬럼 정의는 제공하지 않는다. | 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다. |
| null | L195 / 그룹의 일반·시스템 코드 편집 | REQ-078 | command | 코드 추가창은 code/name/해당 추가값을 검증하고 DB가 아닌 신규 편집 행에만 추가한다. | 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다. |
| null | L195 / 그룹의 일반·시스템 코드 편집 | REQ-079 | validation/rejection | 같은 그룹 code는 삭제된 행을 포함하여 중복409 DUPLICATE_CODE다. | DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. |
| null | L196 / 그룹의 일반·시스템 코드 편집 | REQ-080 | transaction persistence | 새 코드는 USE=Y·DEL=N·그룹 max+1이고 같은 요청 신규는 전달 순서로 채번한다. | 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. |
| null | L196 / 그룹의 일반·시스템 코드 편집 | REQ-081 | validation/rejection | SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 그룹 전체 저장을 롤백한다. | max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. |
| null | L197 / 그룹의 일반·시스템 코드 편집 | REQ-082 | validation/rejection | 동일 코드명은 허용하고 그룹ID/코드로 식별한다. | 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. |
| null | L201 / 그룹의 일반·시스템 코드 편집 | REQ-083 | validation/rejection | 적용 그룹 추가값은 빈칸 불가·정규화 후100자 이하이고 다른 그룹 ADD_VAL은 NULL·ADD_VAL3은 미사용이다. | 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. |
| null | L207 / 그룹의 일반·시스템 코드 편집 | REQ-084 | validation/rejection | PROJ_STEP.ADD_VAL1은 0~100 포함 정수이고 ADD_VAL2는 CTRT_TYPE CSV다. | 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. |
| null | L208 / 그룹의 일반·시스템 코드 편집 | REQ-085 | validation/rejection | BANK.ADD_VAL1은 정확한3자리 숫자로 앞0을 보존한다. | 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. |
| null | L209 / 그룹의 일반·시스템 코드 편집 | REQ-086 | validation/rejection | RSM_LABEL.ADD_VAL1은 명시8개 값 중 하나를 대문자 정규화해 저장한다. | name 입력은 NAME으로 저장; 목록 외 문자열은400이다. |
| null | L212 / 그룹의 일반·시스템 코드 편집 | REQ-087 | validation/rejection | CSV trim/대문자/빈항목 거부/첫 순서 중복제거 후 실제 미삭제·신규 active 참조를 최종 상태로 검사한다. | 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. |
| null | L216 / 수당 값 코드 편집 | REQ-088 | query | 값 코드 그룹은 코드/이름/값/단위4열이며 기존 값만 편집한다. | 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다. |
| null | L216 / 수당 값 코드 편집 | REQ-089 | validation/rejection | 기존 값 이외 변경/삭제는409 VALUE_CODE_FIELD_LOCKED이며 시스템 값 그룹은 시스템 규칙이 우선이다. | 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. |
| null | L217 / 수당 값 코드 편집 | REQ-090 | command | 수당코드 추가는 code/name 필수·값 기본/빈0·unitNm 원/% 기본원이며 그룹 저장 때 반영한다. | 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. |
| null | L218 / 수당 값 코드 편집 | REQ-091 | command | 초기 수당은 SQL 값을 읽고 변경 후 공통 조회 값/단위만 갱신하며 업무 테이블은 바꾸지 않는다. | ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다. |
| null | L222 / 그룹의 일반·시스템 코드 편집 | REQ-092 | state transition | USE_YN N/Y 저장은 STOP/RESUME이며 active 선택 제외와 기존 label 조회를 구분한다. | 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. |
| null | L222 / 그룹의 일반·시스템 코드 편집 | REQ-093 | audit/history | 일반 업무 코드 중지·재개 시 STOP/RESUME 이력을 기록한다. | USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다. |
| null | L223 / 미사용 일반 코드 삭제 | REQ-094 | validation/rejection | 삭제 전 편집 처리·확인 후 참조가1건이라도 있으면409 CODE_IN_USE와 유형/건수/안내로 거부한다. | 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. |
| null | L223 / 미사용 일반 코드 삭제 | REQ-095 | state transition | 미참조 일반 업무 코드는 DEL_YN=Y로 논리 삭제하고 물리행/PK를 보존한다. | 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다.; api_endpoint:DELETE /api/code-groups/{group}/codes/{code} |
| null | L223 / 미사용 일반 코드 삭제 | REQ-096 | audit/history | 일반 코드 논리 삭제 시 DEL 이력을 남긴다. | 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다. |
| null | L224 / 미사용 일반 코드 삭제 | REQ-097 | integrity check | CodeReferenceGuard는 고정 매핑과 공통 대응을 검사하며 취소·중지·마감 참조도 세고 자기 NEW 이력은 제외한다. | 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. |
| null | L239 / 미사용 일반 코드 삭제 | REQ-098 | validation/rejection | 삭제 직전 대상 그룹/행을 잠그고 실제 참조를 재검사하며 ALWNC는 삭제 대상에서 제외한다. | 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. |
| null | L245 / 운영 기준값 변경 | REQ-099 | query | SQL43키를 STD_CD순으로 읽어 이름/값/단위/적용월/설명/값변경/이력을 표시한다. | 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다.; api_endpoint:GET /api/operating-standards |
| null | L246 / 운영 기준 적용 이력 조회 | REQ-100 | query | 현재값은 현재월 이하 최신행이고 이력은 적용월 DESC·현재/예정/지난 상태로 보여준다. | 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다.; api_endpoint:GET /api/operating-standards/{key}/history |
| null | L247 / 운영 기준값 변경 | REQ-101 | validation/rejection | value/effectiveMonth를 필수로 받아 최소월=max(시작월,마지막 CLOSE+1) 전은409 CLOSED_MONTH다. | 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. |
| null | L248 / 운영 기준값 변경 | REQ-102 | transaction persistence | 같은 적용월은 전후 확인 후 UPDATE·없으면 INSERT하며 다른월은 보존하고 키 전체 이력 revision을 비교한다. | 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다.; api_endpoint:PUT /api/operating-standards |
| null | L248 / 운영 기준값 변경 | REQ-103 | validation/rejection | 한 요청 키 중복을 거부하고 미변경 칸은 재저장하지 않는다. | 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. |
| null | L249 / 운영 기준값 변경 | REQ-104 | query | DUN/EXT_PRBL/STEP_PRBL/BILL_MISS/AI_SCORE를 지정 묶음·최대4입력/줄로 표시한다. | AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다. |
| null | L256 / 운영 기준값 변경 | REQ-105 | validation/rejection | DUN_D1≥1 및 D1<D2<D3를 BigDecimal 일반 정밀도 안에서 검사한다. | D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. |
| null | L257 / 운영 기준값 변경 | REQ-106 | validation/rejection | EXT_PRBL·STEP_PRBL은 각각0~100 포함만 허용한다. | 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. |
| null | L258 / 운영 기준값 변경 | REQ-107 | validation/rejection | AI_SCORE8개는 각0~100·합계 정확히100을 BigDecimal로 비교한다. | 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. |
| null | L259 / 운영 기준값 변경 | REQ-108 | validation/rejection | WTHDR_NOTI_D는1 이상·최대99,999,999,999 정수다. | 1과99,999,999,999 허용; 0 또는 소수는400이다. |
| null | L260 / 자금 시작월·기초잔액 변경 | REQ-109 | validation/rejection | CASH_BASE_YM 화면은 YYYY-MM·API value는 YYYYMM6자리·DB는 정수로 보존한다. | 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. |
| null | L263 / 운영 기준값 변경 | REQ-110 | validation/rejection | 묶음 미변경키도 잠그고 해당월·영향구간·이후 예정 경계에서 합/순서/범위를 검사한다. | 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. |
| null | L267 / 자금 시작월·기초잔액 변경 | REQ-111 | transaction persistence | CASH_BASE_YM/AMT는 키당1행·별도요청·effectiveMonth 없이 즉시 UPDATE하고 원래 적용월을 유지한다. | 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. |
| null | L267 / 자금 시작월·기초잔액 변경 | REQ-112 | audit/history | 자금 두 키 변경은 UPD 이력을 기록한다. | 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다. |
| null | L268 / 자금 시작월·기초잔액 변경 | REQ-113 | validation/rejection | 기존 CASH_BASE_YM이 CLOSE이면 두 키 모두 읽기 전용·409 CLOSED_MONTH로 변경을 막는다. | 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. |
| null | L269 / 자금 시작월·기초잔액 변경 | REQ-114 | integrity check | 두행 동시 잠금 후 기존 시작월 재검사하고 저장 후 period-context/기준조회만 갱신한다. | 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. |
| null | L275 / 엑셀 표기 대응 검색 | REQ-115 | query | 대응을 sourceText 부분일치/groupId로 필터하고 원문 순서·페이지로 조회한다. | SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다.; api_endpoint:GET /api/code-mappings |
| null | L276 / 엑셀 표기 대응 등록 | REQ-116 | validation/rejection | trim 1~100자 sourceText·실재 groupId/active code를 검사하고 UK(sourceText,groupId)를 보호한다. | 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. |
| null | L277 / 엑셀 표기 대응 등록 | REQ-117 | transaction persistence | 대응 추가는 즉시 DB에 저장하고 수정 대신 삭제 후 재등록한다. | POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다.; api_endpoint:POST /api/code-mappings |
| null | L277 / 엑셀 표기 대응 등록 | REQ-118 | audit/history | 대응 NEW/DEL은 삭제 전후 식별내용을 이력에 남겨 물리삭제 후에도 식별한다. | NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. |
| null | L277 / 엑셀 표기 대응 선택 삭제 | REQ-119 | transaction persistence | 선택 확인 후 여러 대응을 한 트랜잭션으로 DELETE한다. | 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다.; api_endpoint:POST /api/code-mappings/delete |
| null | L277 / 엑셀 표기 대응 선택 삭제 | REQ-120 | validation/rejection | 삭제 목록에 누락/변경 행이 하나라도 있으면409로 전체 삭제를 롤백한다. | 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. |
| null | L278 / 엑셀 표기 대응 등록 | REQ-121 | integrity check | 대응 변경으로 기존 업무 자료는 수정하지 않는다. | 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다. |
| null | L282 / 공통코드 변경 이력 열람 | REQ-122 | query | 허용5종 대상만 원문 열·필터·정렬·페이지로 조회하고 긴 값 펼침/처리자 표시를 제공한다. | REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다.; api_endpoint:GET /api/common-code-history |
| null | L282 / history-service | REQ-123 | audit/history | NEW/UPD/STOP/RESUME/DEL을 기록하고 codeName의 CHG_ITEM_NM은 CODE_NM으로 고정한다. | 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. |
| null | L284 / 공통코드 변경 이력 열람 | REQ-124 | validation/rejection | targetType은 명시5값·생략전체이며 groupId는 해당 그룹/코드/대응 이력만 반환한다. | 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. |
| null | L286 / history-service | REQ-125 | audit/history | 실제 테이블명과 대상별 TRGT_KEY·컬럼별 전후값·NEW/DEL JSON을500자 이내로 저장하고 잘라내지 않는다. | 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. |
| null | L290 / code-query-service | REQ-126 | query | CodeQueryService/useCodes는 active 미삭제 코드를 정렬하여 원문 공개필드를 반환하고 includeInactive label을 구분한다. | GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다.; api_endpoint:GET /api/codes/{group} |
| null | L291 / standard-query-service | REQ-127 | query | OperatingStandardService.get은 요청월 이하 최신 값/단위/적용월을 DB에서 반환한다. | GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다.; api_endpoint:GET /api/params/{key} |
| null | L291 / standard-query-service | REQ-128 | validation/rejection | 키 또는 해당월 값이 없으면404 STANDARD_NOT_FOUND이며0/하드코딩 값으로 대체하지 않는다. | 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. |
| null | L295 / api-common | REQ-129 | validation/rejection | camelCase·십진문자열·목록 봉투·expectedRevision을 따르고 허용외/주입 필드는400·모드 금지 필드는409다. | SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. |
| null | L324 / api-common | REQ-130 | query | 원문 상태별 오류코드·한국어 사유·필드·참조건수를 반환하고 SQL/스택/비밀값을 노출하지 않는다. | 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. |
| null | L476 / data-model.md | REQ-131 | integrity check | 변경 이력은 보존 기한 없이 유지하고 기간 경과로 삭제하지 않는다. | 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |
| null | L30 / Technology Stack Contract | REQ-132 | runtime constraint | React·TypeScript·Vite·React Router·Java21·Spring Boot3.5.x·Gradle·Spring MVC/JPA/Flyway·PostgreSQL16 요구를 추적한다. | 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |
| null | L31 / Technology Stack Contract | REQ-133 | runtime constraint | 프론트/nginx·백엔드·DB 3서비스 compose·/api 프록시·SPA fallback·환경설정·.env.example을 제공한다. | /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |
| null | L32 / Technology Stack Contract | REQ-134 | runtime constraint | 서버·DB 세션·현재일·연월·화면 일시는 Asia/Seoul로 통일한다. | 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |
| null | L33 / Technology Stack Contract | REQ-135 | runtime constraint | JPA ddl-auto=validate·Flyway만 생성·따옴표 없는 업무 SQL 식별자·BigDecimal을 사용한다. | JPA validate 통과·Hibernate DDL 생성0회·float/double 금액변환0회다. |
| null | L39 / Technology Stack Contract | REQ-136 | runtime constraint | V1__full_schema.sql과 V2__base_data.sql을 지정 Flyway 경로와 순서로 제공한다. | backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |
| null | L80 / Technology Stack Contract | REQ-137 | runtime constraint | 지정 src 경로·hook/client 호출·서버 검증/조회/저장 책임을 지키고 iframe/DOM 문자열 복제를 금지한다. | UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |
| null | L371 / Technology Stack Contract | REQ-138 | runtime constraint | 신규생성 실행 계약의 Frontend/Backend/Database/Infrastructure 값을 보존한다. | React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |
| null | L398 / Technology Stack Contract | REQ-139 | runtime constraint | spring_boot3.3.x·node20.x·vite5.x·react18.x·postgres16.x를 원문 그대로 보존한다. | BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |
| null | L405 / Technology Stack Contract | REQ-140 | runtime constraint | backend_dir·frontend_dir·compose_file·health_endpoint 경로를 고정 계약대로 제공한다. | backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |
| null | L412 / global-ui | REQ-141 | runtime constraint | 공통 레이아웃/UI-27의 구조·색·간격·글꼴·아이콘·표·폼·모달 방향을 제품에 적용한다. | sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |
| null | L426 / Technology Stack Contract | REQ-142 | runtime constraint | 각 compose build context에 .dockerignore를 두고 지정 개발 산출물을 제외한다. | 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |
| null | L466 / Technology Stack Contract | REQ-143 | runtime constraint | 임시 관리자 단계는 로컬/접근 제한 사설 개발·검증 환경에 한정하고 공개 배포하지 않는다. | 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |
| null | L419 / Technology Stack Contract | REQ-144 | runtime constraint | 후속 plan.md가 exact marker+JSON의 semantic SSOT이며 구현은 imperative·jpa·blocking 결정을 따른다. | resolved/imperative/jpa/blocking/explicit 및 blocking_priority/conflicts가 변경 없이 후속 plan.md로 복사된다. |
| null | L422 / Technology Stack Contract | REQ-145 | delivery constraint | 신규생성 구현은 backend 변경과 실제 DB/migration을 포함해야 한다. | backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |
| null | L425 / Technology Stack Contract | REQ-146 | delivery constraint | preview/quickstart에서 backend API와 frontend 화면을 함께 검증한다. | 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |
| null | L21 / 범위 경계 | REQ-147 | exclusion | UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. | 다른 업무 화면의 가짜 수치·무동작 저장버튼0개, 계산/저장/업로드0개다. |
| null | L21 / 범위 경계 | REQ-148 | exclusion | 월 마감/해제/스냅샷 생성은 구현하지 않는다. | 마감 상태 읽기는 존재하되 마감 변경 API/생성 동작0개다. |
| null | L21 / 범위 경계 | REQ-149 | exclusion | 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. | 헤더 준비 중 안내 외 AI/알림 업무0개·엑셀 파서/업로드/다운로드0개다. |
| null | L21 / 범위 경계 | REQ-150 | exclusion | UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. | 메뉴/UI등록표/API에 UI-20이 없고 일/시간 단가 및 근태 업로드 동작0개다. |
| null | L22 / 범위 경계 | REQ-151 | exclusion | 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. | 주소입력 기능/행정구역 적재작업 없이 DDL 제약과0행 상태로 검증한다. |
| null | L44 / 범위 경계 | REQ-152 | exclusion | 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. | 94행 규칙 데이터가 있어도 규칙 편집 UI/계산 실행 API0개다. |
| null | L186 / 범위 경계 | REQ-153 | exclusion | 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. | 그룹 추가/조회만 있고 그룹 수정/삭제 UI·API0개다. |
| null | L245 / 범위 경계 | REQ-154 | exclusion | 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. | 값 변경 외 메타 수정/키 추가/키 삭제 API0개다. |
| null | L282 / 범위 경계 | REQ-155 | exclusion | 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. | 이력 GET만 존재하며 기존/오래된 이력 삭제·수정 경로0개다. |
| null | L470 / health-service | REQ-156 | query | /api/health가 실제 DB 연결 SELECT를 검사하고 정상200·실패503을 반환한다. | 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다.; api_endpoint:GET /api/health |

## Capability-to-Story Ledger
<!-- speckit:section sec-852db4bd8c424f8fb630177943c766fb -->

| capability | classification | owning User Story | grouping rationale | independent E2E proof |
| --- | --- | --- | --- | --- |
| DB 메뉴와 공식 경로 탐색 (REQ-035, REQ-036, REQ-037, REQ-038) | primary-story | US1 | 단일 진입점: 메뉴 또는 직접 URL; 배우 목표: 실제 메뉴에서 등록 화면으로 안전하게 진입한다. 완료 결과: DB 메뉴와 공식 경로의 동일 화면 진입·오류 트리 제외; 다른 이름의 화면/완료 결과와 병합하지 않음. | 유효 메뉴의 화면을 열고 직접 URL·새로고침·hash를 확인한다. → DB 메뉴와 공식 경로의 동일 화면 진입·오류 트리 제외 |
| 화면 설정 저장·복원·초기화 (REQ-047, REQ-048, REQ-049, REQ-050, REQ-053, REQ-054, REQ-055, REQ-056, REQ-057, REQ-059, REQ-060) | primary-story | US2 | 단일 진입점: 사용자 메뉴 → 화면 설정; 배우 목표: 개인 목록 표시를 서버에 저장하여 다시 사용한다. 완료 결과: 변경 설정 복원 및 다른 설정을 보존한 개별 초기화; 다른 이름의 화면/완료 결과와 병합하지 않음. | 설정을 바꾸고 재조회/새로고침한 다음 해당 설정만 초기화한다. → 변경 설정 복원 및 다른 설정을 보존한 개별 초기화 |
| 즐겨찾기 구성 (REQ-051, REQ-058) | primary-story | US3 | 단일 진입점: 제목 ☆·즐겨찾기 목록; 배우 목표: 자주 쓰는 화면을 순서대로 다시 연다. 완료 결과: 최대10개 순서 복원·11번째 거부·모바일 표시만 숨김; 다른 이름의 화면/완료 결과와 병합하지 않음. | 별을 추가하고 순서를 바꾸어 새로고침한다. → 최대10개 순서 복원·11번째 거부·모바일 표시만 숨김 |
| 화면 업무 기간 선택 (REQ-061, REQ-063, REQ-064, REQ-065, REQ-066) | primary-story | US4 | 단일 진입점: 제목 아래 ScreenPeriodBar; 배우 목표: 허용된 연도/월/누계 기간을 선택하고 기억한다. 완료 결과: 선택 기간 복원·실제 DB 마감 표시·NONE 쓰기 거부; 다른 이름의 화면/완료 결과와 병합하지 않음. | 기간을 선택하고 재조회하여 유형/범위/마감 배지를 확인한다. → 선택 기간 복원·실제 DB 마감 표시·NONE 쓰기 거부 |
| 그룹의 일반·시스템 코드 편집 (REQ-069, REQ-070, REQ-071, REQ-072, REQ-073, REQ-074, REQ-078, REQ-079, REQ-080, REQ-081, REQ-082, REQ-083, REQ-084, REQ-085, REQ-086, REQ-087, REQ-092, REQ-093) | primary-story | US5 | 단일 진입점: /UI-27 → 코드 탭 → 그룹; 배우 목표: 선택 그룹의 허용 코드 속성을 정확히 저장한다. 완료 결과: 한 그룹 저장의 실제 변경·재조회·제약 위반 원자 롤백; 다른 이름의 화면/완료 결과와 병합하지 않음. | 일반 그룹의 코드 추가/수정/중지를 저장하고 시스템 코드 제한을 확인한다. → 한 그룹 저장의 실제 변경·재조회·제약 위반 원자 롤백 |
| 일반 업무 그룹 추가 (REQ-075, REQ-076, REQ-077) | primary-story | US6 | 단일 진입점: /UI-27 → 코드 → 그룹 추가; 배우 목표: 새 일반 업무 코드 그룹을 만든다. 완료 결과: 새 그룹 선택·기본 플래그·채번·NEW 이력 및 중복 경합 차단; 다른 이름의 화면/완료 결과와 병합하지 않음. | 중복 없는 groupId/name으로 그룹을 추가한다. → 새 그룹 선택·기본 플래그·채번·NEW 이력 및 중복 경합 차단 |
| 수당 값 코드 편집 (REQ-088, REQ-089, REQ-090, REQ-091) | primary-story | US7 | 단일 진입점: /UI-27 → 코드 → VAL_CODE_YN=Y 그룹; 배우 목표: 수당 기준 코드를 정밀하게 추가·변경한다. 완료 결과: 정확한 값/단위 재조회·잠금 필드 차단·기존 업무 금액 불변; 다른 이름의 화면/완료 결과와 병합하지 않음. | 수당 신규를 하단 저장하고 기존 값만 변경하여 공통 조회한다. → 정확한 값/단위 재조회·잠금 필드 차단·기존 업무 금액 불변 |
| 미사용 일반 코드 삭제 (REQ-094, REQ-095, REQ-096, REQ-097, REQ-098) | primary-story | US8 | 단일 진입점: /UI-27 → 코드 → 삭제; 배우 목표: 사용되지 않는 일반 코드만 목록에서 제거한다. 완료 결과: 참조1건409·미참조 DEL_YN=Y·물리행/PK/DEL 이력 보존; 다른 이름의 화면/완료 결과와 병합하지 않음. | 참조 fixture와 미참조 fixture를 각각 확인 후 삭제한다. → 참조1건409·미참조 DEL_YN=Y·물리행/PK/DEL 이력 보존 |
| 운영 기준값 변경 (REQ-099, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108, REQ-110) | primary-story | US9 | 단일 진입점: /UI-27 → 운영 기준값 → 값 변경; 배우 목표: 유효 적용월의 운영 기준값을 일관되게 변경한다. 완료 결과: 변경 월별 값만 저장·다른월 보존·묶음 경계 위반 전체 롤백; 다른 이름의 화면/완료 결과와 병합하지 않음. | 적용월과 변경 칸을 보내고 현재/예정 경계 및 재조회 값을 확인한다. → 변경 월별 값만 저장·다른월 보존·묶음 경계 위반 전체 롤백 |
| 운영 기준 적용 이력 조회 (REQ-100) | primary-story | US10 | 단일 진입점: /UI-27 → 운영 기준값 → 이력; 배우 목표: 현재·예정·지난 적용값을 구분한다. 완료 결과: 전체 적용월 DESC·상태 구분·미래값의 현재값 오용 없음; 다른 이름의 화면/완료 결과와 병합하지 않음. | 현재/미래/과거 행이 있는 key의 이력을 연다. → 전체 적용월 DESC·상태 구분·미래값의 현재값 오용 없음 |
| 자금 시작월·기초잔액 변경 (REQ-109, REQ-111, REQ-112, REQ-113, REQ-114) | primary-story | US11 | 단일 진입점: /UI-27 → 운영 기준값 → 자금 두 키; 배우 목표: 미마감 초기 자금 기준만 즉시 수정한다. 완료 결과: CLOSE409 우회차단·미마감 동일행 UPDATE·원래 적용월 보존; 다른 이름의 화면/완료 결과와 병합하지 않음. | 기존 시작월 CLOSE 여부별로 자금 두 키 저장을 요청한다. → CLOSE409 우회차단·미마감 동일행 UPDATE·원래 적용월 보존 |
| 엑셀 표기 대응 검색 (REQ-115) | primary-story | US12 | 단일 진입점: /UI-27 → 엑셀 표기 대응; 배우 목표: 등록 표기를 그룹과 문자열로 찾아 확인한다. 완료 결과: 코드/이름·정렬·페이지·빈 결과를 정확히 표시; 다른 이름의 화면/완료 결과와 병합하지 않음. | sourceText 부분일치와 groupId를 선택하여 조회한다. → 코드/이름·정렬·페이지·빈 결과를 정확히 표시 |
| 엑셀 표기 대응 등록 (REQ-116, REQ-117, REQ-118, REQ-121) | primary-story | US13 | 단일 진입점: /UI-27 → 엑셀 표기 대응 → 추가; 배우 목표: 원문 표기를 active 코드와 연결한다. 완료 결과: 즉시 DB 등록·NEW 이력·같은그룹 중복409·업무자료 불변; 다른 이름의 화면/완료 결과와 병합하지 않음. | 실제 active code를 선택해 표기를 추가한다. → 즉시 DB 등록·NEW 이력·같은그룹 중복409·업무자료 불변 |
| 엑셀 표기 대응 선택 삭제 (REQ-119, REQ-120) | primary-story | US14 | 단일 진입점: /UI-27 → 엑셀 표기 대응 → 선택 삭제; 배우 목표: 선택한 표기 대응을 이력을 남겨 제거한다. 완료 결과: 정상 다중 물리삭제/DEL 이력·하나 충돌 시 전체0건; 다른 이름의 화면/완료 결과와 병합하지 않음. | 복수 대응을 확인하여 삭제하고 충돌 fixture로 다시 검증한다. → 정상 다중 물리삭제/DEL 이력·하나 충돌 시 전체0건 |
| 공통코드 변경 이력 열람 (REQ-122, REQ-124) | primary-story | US15 | 단일 진입점: /UI-27 → 변경 이력; 배우 목표: 코드와 기준/대응의 변경 사실을 추적한다. 완료 결과: 대상5종·필터·정렬·전후값·처리자 표시와 조회 전용; 다른 이름의 화면/완료 결과와 병합하지 않음. | targetType/groupId와 페이지를 바꾸고 긴 전후 값을 펼친다. → 대상5종·필터·정렬·전후값·처리자 표시와 조회 전용 |
| 전체 SQL 원천 보존 (REQ-001) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 전체 SQL 원천 보존의 공통/산출물 계약이다. | HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |
| 행정구역 빈 테이블 (REQ-002) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 행정구역 빈 테이블의 공통/산출물 계약이다. | 설치 후 행정구역 테이블 데이터 건수 0행이다. |
| 전체 DB 구조 보존 (REQ-003) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 전체 DB 구조 보존의 공통/산출물 계약이다. | 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |
| 기본 초기값 적재 (REQ-004) | non-ui | spec.md / seed-data | 독립 주화면이 아닌 기본 초기값 적재의 공통/산출물 계약이다. | 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |
| 초기 자금 기준과 계산규칙 (REQ-005) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 초기 자금 기준과 계산규칙의 공통/산출물 계약이다. | CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |
| Flyway 실행본 래퍼 정리 (REQ-006) | non-ui | spec.md / research.md | 독립 주화면이 아닌 Flyway 실행본 래퍼 정리의 공통/산출물 계약이다. | 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. |
| 마이그레이션 불변·멱등성 (REQ-007) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 마이그레이션 불변·멱등성의 공통/산출물 계약이다. | 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |
| 설치 실패와 구조 은폐 금지 (REQ-008) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 설치 실패와 구조 은폐 금지의 공통/산출물 계약이다. | 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |
| 구조 차이 보고 (REQ-009) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 구조 차이 보고의 공통/산출물 계약이다. | 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |
| 설치 후 쓰기 경계 (REQ-010) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 설치 후 쓰기 경계의 공통/산출물 계약이다. | 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |
| 서버 감사 필드와 연월 저장 (REQ-011) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 서버 감사 필드와 연월 저장의 공통/산출물 계약이다. | REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |
| 공통 컴포넌트 실제 재사용 (REQ-012) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 공통 컴포넌트 실제 재사용의 공통/산출물 계약이다. | 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. |
| 문자열 입력 검증 (REQ-013) | non-ui | spec.md / input-service | 독립 주화면이 아닌 문자열 입력 검증의 공통/산출물 계약이다. | 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. |
| 그룹ID 정규화 (REQ-014) | non-ui | spec.md / input-service | 독립 주화면이 아닌 그룹ID 정규화의 공통/산출물 계약이다. | 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. |
| 코드 정규화 (REQ-015) | non-ui | spec.md / input-service | 독립 주화면이 아닌 코드 정규화의 공통/산출물 계약이다. | 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. |
| 명칭 경계와 식별자 불변 (REQ-016) | non-ui | spec.md / input-service | 독립 주화면이 아닌 명칭 경계와 식별자 불변의 공통/산출물 계약이다. | 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. |
| 정밀 십진값 범위 (REQ-017) | non-ui | spec.md / input-service | 독립 주화면이 아닌 정밀 십진값 범위의 공통/산출물 계약이다. | 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. |
| 숫자 입력 정리와 금지값 (REQ-018) | non-ui | spec.md / input-service | 독립 주화면이 아닌 숫자 입력 정리와 금지값의 공통/산출물 계약이다. | 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. |
| 소수 원값 보존 (REQ-019) | non-ui | spec.md / input-service | 독립 주화면이 아닌 소수 원값 보존의 공통/산출물 계약이다. | 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. |
| JOB 월단가 정수 (REQ-020) | non-ui | spec.md / input-service | 독립 주화면이 아닌 JOB 월단가 정수의 공통/산출물 계약이다. | 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. |
| 실제 연월 검증 (REQ-021) | non-ui | spec.md / input-service | 독립 주화면이 아닌 실제 연월 검증의 공통/산출물 계약이다. | 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. |
| Y/N·JSON 타입 (REQ-022) | non-ui | spec.md / input-service | 독립 주화면이 아닌 Y/N·JSON 타입의 공통/산출물 계약이다. | true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. |
| 빈 수당·기준값 구분 (REQ-023) | non-ui | spec.md / input-service | 독립 주화면이 아닌 빈 수당·기준값 구분의 공통/산출물 계약이다. | 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. |
| 공통 표시 함수 (REQ-024) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 공통 표시 함수의 공통/산출물 계약이다. | 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다. |
| 임시 사용자 컨텍스트 (REQ-025) | non-ui | spec.md / current-user | 독립 주화면이 아닌 임시 사용자 컨텍스트의 공통/산출물 계약이다. | GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다.; api_endpoint:GET /api/current-user |
| 처리자 주입 거부 (REQ-026) | non-ui | spec.md / current-user | 독립 주화면이 아닌 처리자 주입 거부의 공통/산출물 계약이다. | 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. |
| 인증·권한 유예 (REQ-027) | non-ui | spec.md / current-user | 독립 주화면이 아닌 인증·권한 유예의 공통/산출물 계약이다. | /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다. |
| 원자적 업무 변경 (REQ-028) | non-ui | spec.md / transaction-service | 독립 주화면이 아닌 원자적 업무 변경의 공통/산출물 계약이다. | 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. |
| 불투명 revision (REQ-029) | non-ui | spec.md / revision-service | 독립 주화면이 아닌 불투명 revision의 공통/산출물 계약이다. | 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. |
| 잠금 후 동시 수정 검출 (REQ-030) | non-ui | spec.md / revision-service | 독립 주화면이 아닌 잠금 후 동시 수정 검출의 공통/산출물 계약이다. | 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. |
| 실제 변경 없는 저장 (REQ-031) | non-ui | spec.md / transaction-service | 독립 주화면이 아닌 실제 변경 없는 저장의 공통/산출물 계약이다. | 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. |
| commit 후 캐시 최신화 (REQ-032) | non-ui | spec.md / cache-service | 독립 주화면이 아닌 commit 후 캐시 최신화의 공통/산출물 계약이다. | 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다. |
| 한국어 오류와 재조회 실패 구분 (REQ-033) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 한국어 오류와 재조회 실패 구분의 공통/산출물 계약이다. | 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다. |
| 중복 클릭·자동 재저장 금지 (REQ-034) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 중복 클릭·자동 재저장 금지의 공통/산출물 계약이다. | 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다. |
| PC 전용 화면 보호 (REQ-039) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 PC 전용 화면 보호의 공통/산출물 계약이다. | 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다. |
| 반응형 공통 레이아웃 (REQ-040) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 반응형 공통 레이아웃의 공통/산출물 계약이다. | 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다. |
| 헤더의 실제 제공 항목 (REQ-041) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 헤더의 실제 제공 항목의 공통/산출물 계약이다. | AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다. |
| 화면 제목·기간 배치 (REQ-042) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 화면 제목·기간 배치의 공통/산출물 계약이다. | UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. |
| 화면 탭과 LRU (REQ-043) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 화면 탭과 LRU의 공통/산출물 계약이다. | 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다. |
| 편집 보존과 이동 경고 (REQ-044) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 편집 보존과 이동 경고의 공통/산출물 계약이다. | 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다. |
| 접근 가능한 모달·입력 (REQ-045) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 접근 가능한 모달·입력의 공통/산출물 계약이다. | 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다. |
| 새로고침 탭 초기화 (REQ-046) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 새로고침 탭 초기화의 공통/산출물 계약이다. | 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. |
| 최근 화면 자동 갱신 (REQ-052) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 최근 화면 자동 갱신의 공통/산출물 계약이다. | 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |
| controlled 기간 부품 (REQ-062) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 controlled 기간 부품의 공통/산출물 계약이다. | YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다. |
| UI-27 네 탭과 조회 상태 (REQ-067) | supporting-constraint | spec.md / global-ui | 독립 주화면이 아닌 UI-27 네 탭과 조회 상태의 공통/산출물 계약이다. | 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. |
| 표 페이지 계약 (REQ-068) | non-ui | spec.md / pagination-service | 독립 주화면이 아닌 표 페이지 계약의 공통/산출물 계약이다. | page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. |
| 변경 유형과 과거 이름 추적 (REQ-123) | non-ui | spec.md / history-service | 독립 주화면이 아닌 변경 유형과 과거 이름 추적의 공통/산출물 계약이다. | 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. |
| 이력 키·전후값 무손실 (REQ-125) | non-ui | spec.md / history-service | 독립 주화면이 아닌 이력 키·전후값 무손실의 공통/산출물 계약이다. | 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. |
| 공통 active 코드 조회 (REQ-126) | non-ui | spec.md / code-query-service | 독립 주화면이 아닌 공통 active 코드 조회의 공통/산출물 계약이다. | GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다.; api_endpoint:GET /api/codes/{group} |
| 월별 단일 기준 조회 (REQ-127) | non-ui | spec.md / standard-query-service | 독립 주화면이 아닌 월별 단일 기준 조회의 공통/산출물 계약이다. | GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다.; api_endpoint:GET /api/params/{key} |
| 기준 미존재 거부 (REQ-128) | non-ui | spec.md / standard-query-service | 독립 주화면이 아닌 기준 미존재 거부의 공통/산출물 계약이다. | 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. |
| API 필드와 서버 필드 보호 (REQ-129) | non-ui | spec.md / api-common | 독립 주화면이 아닌 API 필드와 서버 필드 보호의 공통/산출물 계약이다. | SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. |
| 공통 오류 반환과 비밀 보호 (REQ-130) | non-ui | spec.md / api-common | 독립 주화면이 아닌 공통 오류 반환과 비밀 보호의 공통/산출물 계약이다. | 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. |
| 변경 이력 무기한 보존 (REQ-131) | non-ui | spec.md / data-model.md | 독립 주화면이 아닌 변경 이력 무기한 보존의 공통/산출물 계약이다. | 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |
| 원문 구현 기술 요구 (REQ-132) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 원문 구현 기술 요구의 공통/산출물 계약이다. | 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |
| 3서비스 실행과 SPA (REQ-133) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 3서비스 실행과 SPA의 공통/산출물 계약이다. | /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |
| 서울 시간대 일관성 (REQ-134) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 서울 시간대 일관성의 공통/산출물 계약이다. | 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |
| Flyway 전용 스키마·정밀도 (REQ-135) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 Flyway 전용 스키마·정밀도의 공통/산출물 계약이다. | JPA validate 통과·Hibernate DDL 생성0회·float/double 금액변환0회다. |
| 마이그레이션 파일 경로 (REQ-136) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 마이그레이션 파일 경로의 공통/산출물 계약이다. | backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |
| 프론트 구조와 계층 책임 (REQ-137) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 프론트 구조와 계층 책임의 공통/산출물 계약이다. | UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |
| 고정 technology_stack (REQ-138) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 고정 technology_stack의 공통/산출물 계약이다. | React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |
| 고정 Version BOM (REQ-139) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 고정 Version BOM의 공통/산출물 계약이다. | BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |
| 고정 required_outputs (REQ-140) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 고정 required_outputs의 공통/산출물 계약이다. | backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |
| 레퍼런스 시각 계약 (REQ-141) | non-ui | spec.md / global-ui | 독립 주화면이 아닌 레퍼런스 시각 계약의 공통/산출물 계약이다. | sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |
| Docker 빌드 컨텍스트 제외 (REQ-142) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 Docker 빌드 컨텍스트 제외의 공통/산출물 계약이다. | 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |
| 격리 환경만 배포 (REQ-143) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 격리 환경만 배포의 공통/산출물 계약이다. | 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |
| 실행·영속성 결정 보존 (REQ-144) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 실행·영속성 결정 보존의 공통/산출물 계약이다. | resolved/imperative/jpa/blocking/explicit 및 blocking_priority/conflicts가 변경 없이 후속 plan.md로 복사된다. |
| 실제 백엔드 산출물 (REQ-145) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 실제 백엔드 산출물의 공통/산출물 계약이다. | backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |
| 프론트·API 함께 검증 (REQ-146) | non-ui | spec.md / Technology Stack Contract | 독립 주화면이 아닌 프론트·API 함께 검증의 공통/산출물 계약이다. | 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |
| 후속 업무 화면 경계 (REQ-147) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 후속 업무 화면 경계의 공통/산출물 계약이다. | 다른 업무 화면의 가짜 수치·무동작 저장버튼0개, 계산/저장/업로드0개다. |
| 월 마감 쓰기 제외 (REQ-148) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 월 마감 쓰기 제외의 공통/산출물 계약이다. | 마감 상태 읽기는 존재하되 마감 변경 API/생성 동작0개다. |
| 외부·출력·외형 확장 제외 (REQ-149) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 외부·출력·외형 확장 제외의 공통/산출물 계약이다. | 헤더 준비 중 안내 외 AI/알림 업무0개·엑셀 파서/업로드/다운로드0개다. |
| UI-20·단가·근태 제외 (REQ-150) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 UI-20·단가·근태 제외의 공통/산출물 계약이다. | 메뉴/UI등록표/API에 UI-20이 없고 일/시간 단가 및 근태 업로드 동작0개다. |
| 행정구역 파일 작업 제외 (REQ-151) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 행정구역 파일 작업 제외의 공통/산출물 계약이다. | 주소입력 기능/행정구역 적재작업 없이 DDL 제약과0행 상태로 검증한다. |
| 계산 엔진과 규칙 편집 제외 (REQ-152) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 계산 엔진과 규칙 편집 제외의 공통/산출물 계약이다. | 94행 규칙 데이터가 있어도 규칙 편집 UI/계산 실행 API0개다. |
| 그룹 변경·삭제 제외 (REQ-153) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 그룹 변경·삭제 제외의 공통/산출물 계약이다. | 그룹 추가/조회만 있고 그룹 수정/삭제 UI·API0개다. |
| 기준 키 CRUD 확장 제외 (REQ-154) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 기준 키 CRUD 확장 제외의 공통/산출물 계약이다. | 값 변경 외 메타 수정/키 추가/키 삭제 API0개다. |
| 이력 변경·삭제 제외 (REQ-155) | out-of-scope | spec.md / 범위 경계 | 독립 주화면이 아닌 이력 변경·삭제 제외의 공통/산출물 계약이다. | 이력 GET만 존재하며 기존/오래된 이력 삭제·수정 경로0개다. |
| 실제 DB health 조회 (REQ-156) | non-ui | spec.md / health-service | 독립 주화면이 아닌 실제 DB health 조회의 공통/산출물 계약이다. | 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다.; api_endpoint:GET /api/health |

## 사용자 시나리오 및 시험
<!-- speckit:section sec-dd188096013b4ae6ab0fd5f4a06a489f -->

모든 사용자 여정의 실행자는 실제 인증 역할이 아닌 임시 관리자 컨텍스트다. 각 여정은 독립 fixture와 DB/API/브라우저 관찰로 검증한다. 하나의 탭 안이라도 추가·삭제·조회가 서로 다른 완료 결과를 가지면 별도 Story로 나눴다. 단일 일반 코드 편집 여정 안의 입력 검증/저장/재조회/상태이력은 같은 그룹 저장 목표의 자식 요구이며 독립 화면을 발명하지 않는다.

### US1 — DB 메뉴와 공식 경로 탐색 (우선순위: P1)
<!-- speckit:section sec-fdc8f695e8d347a6b3135f46a16e7a22 -->

진입점: 메뉴 또는 직접 URL

배우 목표: 실제 메뉴에서 등록 화면으로 안전하게 진입한다.

완료 결과: DB 메뉴와 공식 경로의 동일 화면 진입·오류 트리 제외

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 유효 메뉴의 화면을 열고 직접 URL·새로고침·hash를 확인한다. → DB 메뉴와 공식 경로의 동일 화면 진입·오류 트리 제외

인수 시나리오:

1. **Given** 메뉴 또는 직접 URL에 접근한 임시 관리자와 DB 메뉴 구성·정렬의 정상·거부 fixture가 있다. **When** GET /api/menus를 해당 입력으로 호출한다. **Then** REQ-035: 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다.; api_endpoint:GET /api/menus

2. **Given** 메뉴 또는 직접 URL에 접근한 임시 관리자와 메뉴 펼침·접힘 탐색의 정상·거부 fixture가 있다. **When** DB 메뉴에서 화면을 선택하거나 등록 URL로 직접 접근한다. **Then** REQ-036: 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다.

3. **Given** 메뉴 또는 직접 URL에 접근한 임시 관리자와 잘못된 메뉴 트리 진단의 정상·거부 fixture가 있다. **When** DB 메뉴에서 화면을 선택하거나 등록 URL로 직접 접근한다. **Then** REQ-037: 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다.

4. **Given** 메뉴 또는 직접 URL에 접근한 임시 관리자와 공식 라우트와 복구의 정상·거부 fixture가 있다. **When** DB 메뉴에서 화면을 선택하거나 등록 URL로 직접 접근한다. **Then** REQ-038: /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다.

### US2 — 화면 설정 저장·복원·초기화 (우선순위: P2)
<!-- speckit:section sec-f0a96731175b49e58af1b1cfdedbc370 -->

진입점: 사용자 메뉴 → 화면 설정

배우 목표: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

완료 결과: 변경 설정 복원 및 다른 설정을 보존한 개별 초기화

우선순위 근거: 핵심 코드 기능을 일관된 개인 설정·기간으로 사용할 수 있게 한다.

독립 시험: 설정을 바꾸고 재조회/새로고침한 다음 해당 설정만 초기화한다. → 변경 설정 복원 및 다른 설정을 보존한 개별 초기화

인수 시나리오:

1. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 설정 조회와 기본값의 정상·거부 fixture가 있다. **When** GET /api/me/preferences를 해당 입력으로 호출한다. **Then** REQ-047: 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다.; api_endpoint:GET /api/me/preferences

2. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 설정 명시 저장의 정상·거부 fixture가 있다. **When** PUT /api/me/preferences/{type}/{key}를 해당 입력으로 호출한다. **Then** REQ-048: 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다.; api_endpoint:PUT /api/me/preferences/{type}/{key}

3. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 손상 설정 격리의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-049: 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다.

4. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 개별 설정 초기화의 정상·거부 fixture가 있다. **When** DELETE /api/me/preferences/{type}/{key}를 해당 입력으로 호출한다. **Then** REQ-050: 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다.; api_endpoint:DELETE /api/me/preferences/{type}/{key}

5. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 메뉴 접힘 기억의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-053: PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다.

6. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 목록 크기 설정의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-054: 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다.

7. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 밀도 설정 동기화의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-055: 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다.

8. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 표 열 설정 저장의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-056: 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다.

9. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 금액 단위 선택 기억의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-057: 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다.

10. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 UI-27 열 정의 제한의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-059: codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다.

11. **Given** 사용자 메뉴 → 화면 설정에 접근한 임시 관리자와 설정 적용과 실패 보존의 정상·거부 fixture가 있다. **When** 해당 설정을 화면에서 바꾸고 저장 또는 이 설정 초기화를 누른다. **Then** REQ-060: 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다.

### US3 — 즐겨찾기 구성 (우선순위: P2)
<!-- speckit:section sec-a14293c5708f4b2b85a2b859aba7de96 -->

진입점: 제목 ☆·즐겨찾기 목록

배우 목표: 자주 쓰는 화면을 순서대로 다시 연다.

완료 결과: 최대10개 순서 복원·11번째 거부·모바일 표시만 숨김

우선순위 근거: 핵심 코드 기능을 일관된 개인 설정·기간으로 사용할 수 있게 한다.

독립 시험: 별을 추가하고 순서를 바꾸어 새로고침한다. → 최대10개 순서 복원·11번째 거부·모바일 표시만 숨김

인수 시나리오:

1. **Given** 제목 ☆·즐겨찾기 목록에 접근한 임시 관리자와 즐겨찾기 편집의 정상·거부 fixture가 있다. **When** 화면 제목 별을 누르거나 즐겨찾기 목록의 순서를 바꾼다. **Then** REQ-051: 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다.

2. **Given** 제목 ☆·즐겨찾기 목록에 접근한 임시 관리자와 설정 화면ID·표시 보호의 정상·거부 fixture가 있다. **When** 화면 제목 별을 누르거나 즐겨찾기 목록의 순서를 바꾼다. **Then** REQ-058: 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다.

### US4 — 화면 업무 기간 선택 (우선순위: P2)
<!-- speckit:section sec-7ad5512eac704b808502cf805e7d8624 -->

진입점: 제목 아래 ScreenPeriodBar

배우 목표: 허용된 연도/월/누계 기간을 선택하고 기억한다.

완료 결과: 선택 기간 복원·실제 DB 마감 표시·NONE 쓰기 거부

우선순위 근거: 핵심 코드 기능을 일관된 개인 설정·기간으로 사용할 수 있게 한다.

독립 시험: 기간을 선택하고 재조회하여 유형/범위/마감 배지를 확인한다. → 선택 기간 복원·실제 DB 마감 표시·NONE 쓰기 거부

인수 시나리오:

1. **Given** 제목 아래 ScreenPeriodBar에 접근한 임시 관리자와 기간 설정 타입·범위 검사의 정상·거부 fixture가 있다. **When** 허용/경계 기간을 선택하고 기간 상태를 다시 조회한다. **Then** REQ-061: NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다.

2. **Given** 제목 아래 ScreenPeriodBar에 접근한 임시 관리자와 화면 기간 선택·기억의 정상·거부 fixture가 있다. **When** GET /api/period-context를 해당 입력으로 호출한다. **Then** REQ-063: 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다.; api_endpoint:GET /api/period-context

3. **Given** 제목 아래 ScreenPeriodBar에 접근한 임시 관리자와 기간 범위와 운영 전 상태의 정상·거부 fixture가 있다. **When** 허용/경계 기간을 선택하고 기간 상태를 다시 조회한다. **Then** REQ-064: YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다.

4. **Given** 제목 아래 ScreenPeriodBar에 접근한 임시 관리자와 연도 배지의 정상·거부 fixture가 있다. **When** 허용/경계 기간을 선택하고 기간 상태를 다시 조회한다. **Then** REQ-065: 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다.

5. **Given** 제목 아래 ScreenPeriodBar에 접근한 임시 관리자와 실제 월 마감 조회의 정상·거부 fixture가 있다. **When** GET /api/month-close를 해당 입력으로 호출한다. **Then** REQ-066: CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다.; api_endpoint:GET /api/month-close

### US5 — 그룹의 일반·시스템 코드 편집 (우선순위: P1)
<!-- speckit:section sec-3b1913c1ebb6434c950953c64ac3a9da -->

진입점: /UI-27 → 코드 탭 → 그룹

배우 목표: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

완료 결과: 한 그룹 저장의 실제 변경·재조회·제약 위반 원자 롤백

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 일반 그룹의 코드 추가/수정/중지를 저장하고 시스템 코드 제한을 확인한다. → 한 그룹 저장의 실제 변경·재조회·제약 위반 원자 롤백

인수 시나리오:

1. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 그룹 목록과 선택의 정상·거부 fixture가 있다. **When** GET /api/code-groups를 해당 입력으로 호출한다. **Then** REQ-069: 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다.; api_endpoint:GET /api/code-groups

2. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 일반 코드 조회의 정상·거부 fixture가 있다. **When** GET /api/code-groups/{group}/codes를 해당 입력으로 호출한다. **Then** REQ-070: GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다.; api_endpoint:GET /api/code-groups/{group}/codes

3. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 일반 코드 일괄 변경 저장의 정상·거부 fixture가 있다. **When** PUT /api/code-groups/{group}/codes를 해당 입력으로 호출한다. **Then** REQ-071: 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다.; api_endpoint:PUT /api/code-groups/{group}/codes

4. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 편집 취소 복원의 정상·거부 fixture가 있다. **When** 신규/기존 편집 후 취소를 누른다. **Then** REQ-072: 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다.

5. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 부분 변경·중복 코드 검사의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-073: 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다.

6. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 시스템 코드 변경 제한의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-074: CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다.

7. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 일반 신규 코드 편집 행의 정상·거부 fixture가 있다. **When** 코드 추가 창에 필수값을 입력하고 추가를 누른다. **Then** REQ-078: 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다.

8. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 그룹 내 코드 PK 재사용 금지의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-079: DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다.

9. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 신규 코드 기본값·정렬 채번의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-080: 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다.

10. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 정렬 순번 한계의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-081: max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다.

11. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 코드명 중복 허용의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-082: 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다.

12. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 그룹별 추가값 필수·길이의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-083: 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다.

13. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 PROJ_STEP 진척 정수의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-084: 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다.

14. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 BANK 코드와 RSM 라벨의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-085: 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다.

15. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 RSM_LABEL 허용값의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-086: name 입력은 NAME으로 저장; 목록 외 문자열은400이다.

16. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 CSV 정규화·참조의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-087: 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다.

17. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 일반 코드 중지·재개의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-092: 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다.

18. **Given** /UI-27 → 코드 탭 → 그룹에 접근한 임시 관리자와 사용 상태 변경 이력의 정상·거부 fixture가 있다. **When** 해당 변경을 저장한 뒤 GET /api/common-code-history를 조회한다. **Then** REQ-093: USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다.

### US6 — 일반 업무 그룹 추가 (우선순위: P1)
<!-- speckit:section sec-6a4aafdf57894cd8bfeb8289c00419ad -->

진입점: /UI-27 → 코드 → 그룹 추가

배우 목표: 새 일반 업무 코드 그룹을 만든다.

완료 결과: 새 그룹 선택·기본 플래그·채번·NEW 이력 및 중복 경합 차단

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 중복 없는 groupId/name으로 그룹을 추가한다. → 새 그룹 선택·기본 플래그·채번·NEW 이력 및 중복 경합 차단

인수 시나리오:

1. **Given** /UI-27 → 코드 → 그룹 추가에 접근한 임시 관리자와 그룹 중복과 채번 경합의 정상·거부 fixture가 있다. **When** 그룹 추가 창에서 groupId/name을 입력하고 추가를 누른다. **Then** REQ-075: 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다.

2. **Given** /UI-27 → 코드 → 그룹 추가에 접근한 임시 관리자와 업무 그룹 생성의 정상·거부 fixture가 있다. **When** POST /api/code-groups를 해당 입력으로 호출한다. **Then** REQ-076: POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다.; api_endpoint:POST /api/code-groups

3. **Given** /UI-27 → 코드 → 그룹 추가에 접근한 임시 관리자와 그룹 추가 전 편집 처리의 정상·거부 fixture가 있다. **When** 그룹 추가 창에서 groupId/name을 입력하고 추가를 누른다. **Then** REQ-077: 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다.

### US7 — 수당 값 코드 편집 (우선순위: P1)
<!-- speckit:section sec-49459f6cb00a4dacb8ddfac3c62222f3 -->

진입점: /UI-27 → 코드 → VAL_CODE_YN=Y 그룹

배우 목표: 수당 기준 코드를 정밀하게 추가·변경한다.

완료 결과: 정확한 값/단위 재조회·잠금 필드 차단·기존 업무 금액 불변

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 수당 신규를 하단 저장하고 기존 값만 변경하여 공통 조회한다. → 정확한 값/단위 재조회·잠금 필드 차단·기존 업무 금액 불변

인수 시나리오:

1. **Given** /UI-27 → 코드 → VAL_CODE_YN=Y 그룹에 접근한 임시 관리자와 수당 코드 조회·편집 형태의 정상·거부 fixture가 있다. **When** 해당 그룹 또는 기준 목록을 화면에서 다시 연다. **Then** REQ-088: 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다.

2. **Given** /UI-27 → 코드 → VAL_CODE_YN=Y 그룹에 접근한 임시 관리자와 값 코드 잠금의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-089: 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다.

3. **Given** /UI-27 → 코드 → VAL_CODE_YN=Y 그룹에 접근한 임시 관리자와 수당 신규 코드 저장의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-090: 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다.

4. **Given** /UI-27 → 코드 → VAL_CODE_YN=Y 그룹에 접근한 임시 관리자와 수당 값 변경과 업무 불변의 정상·거부 fixture가 있다. **When** 해당 code/변경필드/expectedRevision으로 PUT /api/code-groups/{group}/codes를 호출한다. **Then** REQ-091: ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다.

### US8 — 미사용 일반 코드 삭제 (우선순위: P1)
<!-- speckit:section sec-1a849f6f1b3b4d00a82d6179a9c4ef81 -->

진입점: /UI-27 → 코드 → 삭제

배우 목표: 사용되지 않는 일반 코드만 목록에서 제거한다.

완료 결과: 참조1건409·미참조 DEL_YN=Y·물리행/PK/DEL 이력 보존

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 참조 fixture와 미참조 fixture를 각각 확인 후 삭제한다. → 참조1건409·미참조 DEL_YN=Y·물리행/PK/DEL 이력 보존

인수 시나리오:

1. **Given** /UI-27 → 코드 → 삭제에 접근한 임시 관리자와 삭제 참조 차단의 정상·거부 fixture가 있다. **When** 일반 코드 삭제 확인 후 DELETE /api/code-groups/{group}/codes/{code}를 요청한다. **Then** REQ-094: 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다.

2. **Given** /UI-27 → 코드 → 삭제에 접근한 임시 관리자와 일반 코드 논리 삭제의 정상·거부 fixture가 있다. **When** DELETE /api/code-groups/{group}/codes/{code}를 해당 입력으로 호출한다. **Then** REQ-095: 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다.; api_endpoint:DELETE /api/code-groups/{group}/codes/{code}

3. **Given** /UI-27 → 코드 → 삭제에 접근한 임시 관리자와 코드 삭제 이력의 정상·거부 fixture가 있다. **When** 일반 코드 삭제 확인 후 DELETE /api/code-groups/{group}/codes/{code}를 요청한다. **Then** REQ-096: 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다.

4. **Given** /UI-27 → 코드 → 삭제에 접근한 임시 관리자와 고정 참조 가드의 정상·거부 fixture가 있다. **When** 일반 코드 삭제 확인 후 DELETE /api/code-groups/{group}/codes/{code}를 요청한다. **Then** REQ-097: 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다.

5. **Given** /UI-27 → 코드 → 삭제에 접근한 임시 관리자와 삭제 직전 재검사의 정상·거부 fixture가 있다. **When** 일반 코드 삭제 확인 후 DELETE /api/code-groups/{group}/codes/{code}를 요청한다. **Then** REQ-098: 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다.

### US9 — 운영 기준값 변경 (우선순위: P1)
<!-- speckit:section sec-2bf172604668494e956ff8ec1b2ffbc2 -->

진입점: /UI-27 → 운영 기준값 → 값 변경

배우 목표: 유효 적용월의 운영 기준값을 일관되게 변경한다.

완료 결과: 변경 월별 값만 저장·다른월 보존·묶음 경계 위반 전체 롤백

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 적용월과 변경 칸을 보내고 현재/예정 경계 및 재조회 값을 확인한다. → 변경 월별 값만 저장·다른월 보존·묶음 경계 위반 전체 롤백

인수 시나리오:

1. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 운영 기준 전체 조회의 정상·거부 fixture가 있다. **When** GET /api/operating-standards를 해당 입력으로 호출한다. **Then** REQ-099: 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다.; api_endpoint:GET /api/operating-standards

2. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 일반 기준 적용 최소월의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-101: 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다.

3. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 기준 월별 추가·덮어쓰기의 정상·거부 fixture가 있다. **When** PUT /api/operating-standards를 해당 입력으로 호출한다. **Then** REQ-102: 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다.; api_endpoint:PUT /api/operating-standards

4. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 기준 변경 키 중복 거부의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-103: 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다.

5. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 묶음 기준 표시의 정상·거부 fixture가 있다. **When** 해당 그룹 또는 기준 목록을 화면에서 다시 연다. **Then** REQ-104: AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다.

6. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 DUN 순서 검증의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-105: D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다.

7. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 확률 범위 검증의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-106: 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다.

8. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 AI 점수 합계 검증의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-107: 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다.

9. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 철수 통보일 정수의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-108: 1과99,999,999,999 허용; 0 또는 소수는400이다.

10. **Given** /UI-27 → 운영 기준값 → 값 변경에 접근한 임시 관리자와 기준 전체 영향구간 검사의 정상·거부 fixture가 있다. **When** 해당 key/value/effectiveMonth/expectedRevision으로 PUT /api/operating-standards를 호출한다. **Then** REQ-110: 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다.

### US10 — 운영 기준 적용 이력 조회 (우선순위: P1)
<!-- speckit:section sec-5f9ec53bc5404dbc9ba86eb411695b97 -->

진입점: /UI-27 → 운영 기준값 → 이력

배우 목표: 현재·예정·지난 적용값을 구분한다.

완료 결과: 전체 적용월 DESC·상태 구분·미래값의 현재값 오용 없음

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 현재/미래/과거 행이 있는 key의 이력을 연다. → 전체 적용월 DESC·상태 구분·미래값의 현재값 오용 없음

인수 시나리오:

1. **Given** /UI-27 → 운영 기준값 → 이력에 접근한 임시 관리자와 기준 현재·과거·미래 이력의 정상·거부 fixture가 있다. **When** GET /api/operating-standards/{key}/history를 해당 입력으로 호출한다. **Then** REQ-100: 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다.; api_endpoint:GET /api/operating-standards/{key}/history

### US11 — 자금 시작월·기초잔액 변경 (우선순위: P1)
<!-- speckit:section sec-db0cbd2efd9547599e2b3d65d3e24f2d -->

진입점: /UI-27 → 운영 기준값 → 자금 두 키

배우 목표: 미마감 초기 자금 기준만 즉시 수정한다.

완료 결과: CLOSE409 우회차단·미마감 동일행 UPDATE·원래 적용월 보존

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 기존 시작월 CLOSE 여부별로 자금 두 키 저장을 요청한다. → CLOSE409 우회차단·미마감 동일행 UPDATE·원래 적용월 보존

인수 시나리오:

1. **Given** /UI-27 → 운영 기준값 → 자금 두 키에 접근한 임시 관리자와 자금 시작 연월 표현의 정상·거부 fixture가 있다. **When** effectiveMonth 없이 자금 두 키 전용 PUT /api/operating-standards를 호출한다. **Then** REQ-109: 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다.

2. **Given** /UI-27 → 운영 기준값 → 자금 두 키에 접근한 임시 관리자와 자금 두 키 즉시 갱신의 정상·거부 fixture가 있다. **When** effectiveMonth 없이 자금 두 키 전용 PUT /api/operating-standards를 호출한다. **Then** REQ-111: 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다.

3. **Given** /UI-27 → 운영 기준값 → 자금 두 키에 접근한 임시 관리자와 자금 기준 변경 이력의 정상·거부 fixture가 있다. **When** effectiveMonth 없이 자금 두 키 전용 PUT /api/operating-standards를 호출한다. **Then** REQ-112: 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다.

4. **Given** /UI-27 → 운영 기준값 → 자금 두 키에 접근한 임시 관리자와 기존 시작월 마감 잠금의 정상·거부 fixture가 있다. **When** effectiveMonth 없이 자금 두 키 전용 PUT /api/operating-standards를 호출한다. **Then** REQ-113: 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다.

5. **Given** /UI-27 → 운영 기준값 → 자금 두 키에 접근한 임시 관리자와 자금 동시 잠금·후속 갱신의 정상·거부 fixture가 있다. **When** effectiveMonth 없이 자금 두 키 전용 PUT /api/operating-standards를 호출한다. **Then** REQ-114: 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다.

### US12 — 엑셀 표기 대응 검색 (우선순위: P1)
<!-- speckit:section sec-0e835b9f3e4240ef941b01f4cf41d3a5 -->

진입점: /UI-27 → 엑셀 표기 대응

배우 목표: 등록 표기를 그룹과 문자열로 찾아 확인한다.

완료 결과: 코드/이름·정렬·페이지·빈 결과를 정확히 표시

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: sourceText 부분일치와 groupId를 선택하여 조회한다. → 코드/이름·정렬·페이지·빈 결과를 정확히 표시

인수 시나리오:

1. **Given** /UI-27 → 엑셀 표기 대응에 접근한 임시 관리자와 표기 대응 목록·검색의 정상·거부 fixture가 있다. **When** GET /api/code-mappings를 해당 입력으로 호출한다. **Then** REQ-115: SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다.; api_endpoint:GET /api/code-mappings

### US13 — 엑셀 표기 대응 등록 (우선순위: P1)
<!-- speckit:section sec-7b52e09b636f4713ad3cd04595f8f366 -->

진입점: /UI-27 → 엑셀 표기 대응 → 추가

배우 목표: 원문 표기를 active 코드와 연결한다.

완료 결과: 즉시 DB 등록·NEW 이력·같은그룹 중복409·업무자료 불변

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 실제 active code를 선택해 표기를 추가한다. → 즉시 DB 등록·NEW 이력·같은그룹 중복409·업무자료 불변

인수 시나리오:

1. **Given** /UI-27 → 엑셀 표기 대응 → 추가에 접근한 임시 관리자와 대응 입력·중복 검사의 정상·거부 fixture가 있다. **When** active 코드를 선택하고 POST /api/code-mappings를 호출한다. **Then** REQ-116: 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다.

2. **Given** /UI-27 → 엑셀 표기 대응 → 추가에 접근한 임시 관리자와 대응 즉시 등록의 정상·거부 fixture가 있다. **When** POST /api/code-mappings를 해당 입력으로 호출한다. **Then** REQ-117: POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다.; api_endpoint:POST /api/code-mappings

3. **Given** /UI-27 → 엑셀 표기 대응 → 추가에 접근한 임시 관리자와 대응 등록·삭제 이력의 정상·거부 fixture가 있다. **When** active 코드를 선택하고 POST /api/code-mappings를 호출한다. **Then** REQ-118: NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다.

4. **Given** /UI-27 → 엑셀 표기 대응 → 추가에 접근한 임시 관리자와 대응 변경의 업무 불변의 정상·거부 fixture가 있다. **When** active 코드를 선택하고 POST /api/code-mappings를 호출한다. **Then** REQ-121: 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다.

### US14 — 엑셀 표기 대응 선택 삭제 (우선순위: P1)
<!-- speckit:section sec-fda88ee081e04a7487933592f840df70 -->

진입점: /UI-27 → 엑셀 표기 대응 → 선택 삭제

배우 목표: 선택한 표기 대응을 이력을 남겨 제거한다.

완료 결과: 정상 다중 물리삭제/DEL 이력·하나 충돌 시 전체0건

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: 복수 대응을 확인하여 삭제하고 충돌 fixture로 다시 검증한다. → 정상 다중 물리삭제/DEL 이력·하나 충돌 시 전체0건

인수 시나리오:

1. **Given** /UI-27 → 엑셀 표기 대응 → 선택 삭제에 접근한 임시 관리자와 대응 다중 물리 삭제의 정상·거부 fixture가 있다. **When** POST /api/code-mappings/delete를 해당 입력으로 호출한다. **Then** REQ-119: 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다.; api_endpoint:POST /api/code-mappings/delete

2. **Given** /UI-27 → 엑셀 표기 대응 → 선택 삭제에 접근한 임시 관리자와 대응 삭제 충돌의 정상·거부 fixture가 있다. **When** 선택 확인 후 POST /api/code-mappings/delete를 호출한다. **Then** REQ-120: 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다.

### US15 — 공통코드 변경 이력 열람 (우선순위: P1)
<!-- speckit:section sec-ac431d019d09416bbdb1334bb8d191b4 -->

진입점: /UI-27 → 변경 이력

배우 목표: 코드와 기준/대응의 변경 사실을 추적한다.

완료 결과: 대상5종·필터·정렬·전후값·처리자 표시와 조회 전용

우선순위 근거: DB 저장·데이터 보호를 실제로 검증하는 핵심 범위다.

독립 시험: targetType/groupId와 페이지를 바꾸고 긴 전후 값을 펼친다. → 대상5종·필터·정렬·전후값·처리자 표시와 조회 전용

인수 시나리오:

1. **Given** /UI-27 → 변경 이력에 접근한 임시 관리자와 변경 이력 조회의 정상·거부 fixture가 있다. **When** GET /api/common-code-history를 해당 입력으로 호출한다. **Then** REQ-122: REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다.; api_endpoint:GET /api/common-code-history

2. **Given** /UI-27 → 변경 이력에 접근한 임시 관리자와 이력 필터 도메인의 정상·거부 fixture가 있다. **When** 필터와 페이지로 GET /api/common-code-history를 요청하고 긴 값을 펼친다. **Then** REQ-124: 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다.

## Requirement-to-Design Ownership Ledger
<!-- speckit:section sec-5d946c757c9347bb91135060d98a811e -->

| canonical_id | registry disposition | design owner kind | owner ref | accounting writer | rationale |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | data-contract | artifact | spec.md / data-model.md | skeleton | 전체 SQL 원천 보존의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-002 | data-contract | artifact | spec.md / data-model.md | skeleton | 행정구역 빈 테이블의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-003 | data-contract | artifact | spec.md / data-model.md | skeleton | 전체 DB 구조 보존의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-004 | data-contract | artifact | spec.md / seed-data | skeleton | 기본 초기값 적재의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-005 | data-contract | artifact | spec.md / data-model.md | skeleton | 초기 자금 기준과 계산규칙의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-006 | data-contract | artifact | spec.md / research.md | skeleton | Flyway 실행본 래퍼 정리의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-007 | data-contract | artifact | spec.md / data-model.md | skeleton | 마이그레이션 불변·멱등성의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-008 | data-contract | artifact | spec.md / data-model.md | skeleton | 설치 실패와 구조 은폐 금지의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-009 | data-contract | artifact | spec.md / data-model.md | skeleton | 구조 차이 보고의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-010 | data-contract | artifact | spec.md / data-model.md | skeleton | 설치 후 쓰기 경계의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-011 | data-contract | artifact | spec.md / data-model.md | skeleton | 서버 감사 필드와 연월 저장의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-012 | ui-supporting | global-ui | 공통 셸 / 공통 컴포넌트 실제 재사용 | skeleton | 공통 컴포넌트 실제 재사용의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-013 | api-contract | artifact | spec.md / input-service | skeleton | 문자열 입력 검증의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-014 | api-contract | artifact | spec.md / input-service | skeleton | 그룹ID 정규화의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-015 | api-contract | artifact | spec.md / input-service | skeleton | 코드 정규화의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-016 | api-contract | artifact | spec.md / input-service | skeleton | 명칭 경계와 식별자 불변의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-017 | api-contract | artifact | spec.md / input-service | skeleton | 정밀 십진값 범위의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-018 | api-contract | artifact | spec.md / input-service | skeleton | 숫자 입력 정리와 금지값의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-019 | api-contract | artifact | spec.md / input-service | skeleton | 소수 원값 보존의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-020 | api-contract | artifact | spec.md / input-service | skeleton | JOB 월단가 정수의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-021 | api-contract | artifact | spec.md / input-service | skeleton | 실제 연월 검증의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-022 | api-contract | artifact | spec.md / input-service | skeleton | Y/N·JSON 타입의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-023 | api-contract | artifact | spec.md / input-service | skeleton | 빈 수당·기준값 구분의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-024 | ui-supporting | global-ui | 공통 셸 / 공통 표시 함수 | skeleton | 공통 표시 함수의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-025 | api-contract | artifact | spec.md / current-user | skeleton | 임시 사용자 컨텍스트의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-026 | api-contract | artifact | spec.md / current-user | skeleton | 처리자 주입 거부의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-027 | api-contract | artifact | spec.md / current-user | skeleton | 인증·권한 유예의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-028 | api-contract | artifact | spec.md / transaction-service | skeleton | 원자적 업무 변경의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-029 | api-contract | artifact | spec.md / revision-service | skeleton | 불투명 revision의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-030 | api-contract | artifact | spec.md / revision-service | skeleton | 잠금 후 동시 수정 검출의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-031 | api-contract | artifact | spec.md / transaction-service | skeleton | 실제 변경 없는 저장의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-032 | api-contract | artifact | spec.md / cache-service | skeleton | commit 후 캐시 최신화의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-033 | ui-supporting | global-ui | 공통 셸 / 한국어 오류와 재조회 실패 구분 | skeleton | 한국어 오류와 재조회 실패 구분의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-034 | ui-supporting | global-ui | 공통 셸 / 중복 클릭·자동 재저장 금지 | skeleton | 중복 클릭·자동 재저장 금지의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-035 | ui-primary | feature-story | US1 | feature-story | DB 메뉴 구성·정렬의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-036 | ui-primary | feature-story | US1 | feature-story | 메뉴 펼침·접힘 탐색의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-037 | ui-primary | feature-story | US1 | feature-story | 잘못된 메뉴 트리 진단의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-038 | ui-primary | feature-story | US1 | feature-story | 공식 라우트와 복구의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-039 | ui-supporting | global-ui | 공통 셸 / PC 전용 화면 보호 | skeleton | PC 전용 화면 보호의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-040 | ui-supporting | global-ui | 공통 셸 / 반응형 공통 레이아웃 | skeleton | 반응형 공통 레이아웃의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-041 | ui-supporting | global-ui | 공통 셸 / 헤더의 실제 제공 항목 | skeleton | 헤더의 실제 제공 항목의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-042 | ui-supporting | global-ui | 공통 셸 / 화면 제목·기간 배치 | skeleton | 화면 제목·기간 배치의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-043 | ui-supporting | global-ui | 공통 셸 / 화면 탭과 LRU | skeleton | 화면 탭과 LRU의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-044 | ui-supporting | global-ui | 공통 셸 / 편집 보존과 이동 경고 | skeleton | 편집 보존과 이동 경고의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-045 | ui-supporting | global-ui | 공통 셸 / 접근 가능한 모달·입력 | skeleton | 접근 가능한 모달·입력의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-046 | ui-supporting | global-ui | 공통 셸 / 새로고침 탭 초기화 | skeleton | 새로고침 탭 초기화의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-047 | ui-primary | feature-story | US2 | feature-story | 설정 조회와 기본값의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-048 | ui-primary | feature-story | US2 | feature-story | 설정 명시 저장의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-049 | ui-primary | feature-story | US2 | feature-story | 손상 설정 격리의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-050 | ui-primary | feature-story | US2 | feature-story | 개별 설정 초기화의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-051 | ui-primary | feature-story | US3 | feature-story | 즐겨찾기 편집의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-052 | ui-supporting | global-ui | 공통 셸 / 최근 화면 자동 갱신 | skeleton | 최근 화면 자동 갱신의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-053 | ui-primary | feature-story | US2 | feature-story | 메뉴 접힘 기억의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-054 | ui-primary | feature-story | US2 | feature-story | 목록 크기 설정의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-055 | ui-primary | feature-story | US2 | feature-story | 밀도 설정 동기화의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-056 | ui-primary | feature-story | US2 | feature-story | 표 열 설정 저장의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-057 | ui-primary | feature-story | US2 | feature-story | 금액 단위 선택 기억의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-058 | ui-primary | feature-story | US3 | feature-story | 설정 화면ID·표시 보호의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-059 | ui-primary | feature-story | US2 | feature-story | UI-27 열 정의 제한의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-060 | ui-primary | feature-story | US2 | feature-story | 설정 적용과 실패 보존의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-061 | ui-primary | feature-story | US4 | feature-story | 기간 설정 타입·범위 검사의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-062 | ui-supporting | global-ui | 공통 셸 / controlled 기간 부품 | skeleton | controlled 기간 부품의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-063 | ui-primary | feature-story | US4 | feature-story | 화면 기간 선택·기억의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-064 | ui-primary | feature-story | US4 | feature-story | 기간 범위와 운영 전 상태의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-065 | ui-primary | feature-story | US4 | feature-story | 연도 배지의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-066 | ui-primary | feature-story | US4 | feature-story | 실제 월 마감 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-067 | ui-supporting | global-ui | 공통 셸 / UI-27 네 탭과 조회 상태 | skeleton | UI-27 네 탭과 조회 상태의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-068 | api-contract | artifact | spec.md / pagination-service | skeleton | 표 페이지 계약의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-069 | ui-primary | feature-story | US5 | feature-story | 그룹 목록과 선택의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-070 | ui-primary | feature-story | US5 | feature-story | 일반 코드 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-071 | ui-primary | feature-story | US5 | feature-story | 일반 코드 일괄 변경 저장의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-072 | ui-primary | feature-story | US5 | feature-story | 편집 취소 복원의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-073 | ui-primary | feature-story | US5 | feature-story | 부분 변경·중복 코드 검사의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-074 | ui-primary | feature-story | US5 | feature-story | 시스템 코드 변경 제한의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-075 | ui-primary | feature-story | US6 | feature-story | 그룹 중복과 채번 경합의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-076 | ui-primary | feature-story | US6 | feature-story | 업무 그룹 생성의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-077 | ui-primary | feature-story | US6 | feature-story | 그룹 추가 전 편집 처리의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-078 | ui-primary | feature-story | US5 | feature-story | 일반 신규 코드 편집 행의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-079 | ui-primary | feature-story | US5 | feature-story | 그룹 내 코드 PK 재사용 금지의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-080 | ui-primary | feature-story | US5 | feature-story | 신규 코드 기본값·정렬 채번의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-081 | ui-primary | feature-story | US5 | feature-story | 정렬 순번 한계의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-082 | ui-primary | feature-story | US5 | feature-story | 코드명 중복 허용의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-083 | ui-primary | feature-story | US5 | feature-story | 그룹별 추가값 필수·길이의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-084 | ui-primary | feature-story | US5 | feature-story | PROJ_STEP 진척 정수의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-085 | ui-primary | feature-story | US5 | feature-story | BANK 코드와 RSM 라벨의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-086 | ui-primary | feature-story | US5 | feature-story | RSM_LABEL 허용값의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-087 | ui-primary | feature-story | US5 | feature-story | CSV 정규화·참조의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-088 | ui-primary | feature-story | US7 | feature-story | 수당 코드 조회·편집 형태의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-089 | ui-primary | feature-story | US7 | feature-story | 값 코드 잠금의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-090 | ui-primary | feature-story | US7 | feature-story | 수당 신규 코드 저장의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-091 | ui-primary | feature-story | US7 | feature-story | 수당 값 변경과 업무 불변의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-092 | ui-primary | feature-story | US5 | feature-story | 일반 코드 중지·재개의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-093 | ui-primary | feature-story | US5 | feature-story | 사용 상태 변경 이력의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-094 | ui-primary | feature-story | US8 | feature-story | 삭제 참조 차단의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-095 | ui-primary | feature-story | US8 | feature-story | 일반 코드 논리 삭제의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-096 | ui-primary | feature-story | US8 | feature-story | 코드 삭제 이력의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-097 | ui-primary | feature-story | US8 | feature-story | 고정 참조 가드의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-098 | ui-primary | feature-story | US8 | feature-story | 삭제 직전 재검사의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-099 | ui-primary | feature-story | US9 | feature-story | 운영 기준 전체 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-100 | ui-primary | feature-story | US10 | feature-story | 기준 현재·과거·미래 이력의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-101 | ui-primary | feature-story | US9 | feature-story | 일반 기준 적용 최소월의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-102 | ui-primary | feature-story | US9 | feature-story | 기준 월별 추가·덮어쓰기의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-103 | ui-primary | feature-story | US9 | feature-story | 기준 변경 키 중복 거부의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-104 | ui-primary | feature-story | US9 | feature-story | 묶음 기준 표시의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-105 | ui-primary | feature-story | US9 | feature-story | DUN 순서 검증의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-106 | ui-primary | feature-story | US9 | feature-story | 확률 범위 검증의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-107 | ui-primary | feature-story | US9 | feature-story | AI 점수 합계 검증의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-108 | ui-primary | feature-story | US9 | feature-story | 철수 통보일 정수의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-109 | ui-primary | feature-story | US11 | feature-story | 자금 시작 연월 표현의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-110 | ui-primary | feature-story | US9 | feature-story | 기준 전체 영향구간 검사의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-111 | ui-primary | feature-story | US11 | feature-story | 자금 두 키 즉시 갱신의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-112 | ui-primary | feature-story | US11 | feature-story | 자금 기준 변경 이력의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-113 | ui-primary | feature-story | US11 | feature-story | 기존 시작월 마감 잠금의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-114 | ui-primary | feature-story | US11 | feature-story | 자금 동시 잠금·후속 갱신의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-115 | ui-primary | feature-story | US12 | feature-story | 표기 대응 목록·검색의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-116 | ui-primary | feature-story | US13 | feature-story | 대응 입력·중복 검사의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-117 | ui-primary | feature-story | US13 | feature-story | 대응 즉시 등록의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-118 | ui-primary | feature-story | US13 | feature-story | 대응 등록·삭제 이력의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-119 | ui-primary | feature-story | US14 | feature-story | 대응 다중 물리 삭제의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-120 | ui-primary | feature-story | US14 | feature-story | 대응 삭제 충돌의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-121 | ui-primary | feature-story | US13 | feature-story | 대응 변경의 업무 불변의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-122 | ui-primary | feature-story | US15 | feature-story | 변경 이력 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-123 | api-contract | artifact | spec.md / history-service | skeleton | 변경 유형과 과거 이름 추적의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-124 | ui-primary | feature-story | US15 | feature-story | 이력 필터 도메인의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-125 | api-contract | artifact | spec.md / history-service | skeleton | 이력 키·전후값 무손실의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-126 | api-contract | artifact | spec.md / code-query-service | skeleton | 공통 active 코드 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-127 | api-contract | artifact | spec.md / standard-query-service | skeleton | 월별 단일 기준 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-128 | api-contract | artifact | spec.md / standard-query-service | skeleton | 기준 미존재 거부의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-129 | api-contract | artifact | spec.md / api-common | skeleton | API 필드와 서버 필드 보호의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-130 | api-contract | artifact | spec.md / api-common | skeleton | 공통 오류 반환과 비밀 보호의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-131 | data-contract | artifact | spec.md / data-model.md | skeleton | 변경 이력 무기한 보존의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-132 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 원문 구현 기술 요구의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-133 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 3서비스 실행과 SPA의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-134 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 서울 시간대 일관성의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-135 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | Flyway 전용 스키마·정밀도의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-136 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 마이그레이션 파일 경로의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-137 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 프론트 구조와 계층 책임의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-138 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 고정 technology_stack의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-139 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 고정 Version BOM의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-140 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 고정 required_outputs의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-141 | non-functional | artifact | spec.md / global-ui | skeleton | 레퍼런스 시각 계약의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-142 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | Docker 빌드 컨텍스트 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-143 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 격리 환경만 배포의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-144 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 실행·영속성 결정 보존의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-145 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 실제 백엔드 산출물의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-146 | non-functional | artifact | spec.md / Technology Stack Contract | skeleton | 프론트·API 함께 검증의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-147 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 후속 업무 화면 경계의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-148 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 월 마감 쓰기 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-149 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 외부·출력·외형 확장 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-150 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | UI-20·단가·근태 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-151 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 행정구역 파일 작업 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-152 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 계산 엔진과 규칙 편집 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-153 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 그룹 변경·삭제 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-154 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 기준 키 CRUD 확장 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-155 | out-of-scope | artifact | spec.md / 범위 경계 | skeleton | 이력 변경·삭제 제외의 구현/검증 계약을 이 소유자에서 구체화한다. |
| REQ-156 | architecture-runtime | artifact | spec.md / health-service | skeleton | 실제 DB health 조회의 구현/검증 계약을 이 소유자에서 구체화한다. |

## 요구사항
<!-- speckit:section sec-679cefa607b14da994fedd5e1e941887 -->

다음 원장 표가 canonical_id 의미의 기준이며 기능 상세와 합격 기준은 동일 키를 사용한다. 원문에서 요구 ID는 선언되지 않았으므로 source_id는 모두 null이다. API 목록을 요구별 가짜 endpoint로 늘리지 않고 원문 자원 연산을 공유한다.

### 기능 요구사항
<!-- speckit:section sec-30e06c80d2144872935e6ec5aeb2c5d1 -->

#### 공통 컴포넌트 실제 재사용부터 실제 변경 없는 저장까지
<!-- speckit:section sec-01179a63d8ab42b09adbcb3e386f9b02 -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-012 | null | functional | ui-supporting | 공통 컴포넌트 실제 재사용 | 공통 부품을 UI-27에 실제 적용하고 Props·이벤트·사용처를 기록하며 개별 업무 분기를 넣지 않는다. | 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. | si-cost | SPEC-REQUEST.md:L68 — 레이아웃·표·검색·페이지·입력·모달·배지·이력을 재사용 가능한 React·TypeScript 컴포넌트로 구성한다. 현재 UI-27에서 실제 사용하고, 후속 화면도 동일 경로/인터페이스를 재사용하도록 Props·이벤트·사용처를 기록한다. 컬럼·렌더러·행 작업·필드 정의를 화면에서 전달하고 공통 부품 안에 개별 업무 분기를 넣지 않는다. |
| REQ-013 | null | functional | api-contract | 문자열 입력 검증 | trim 후 필수/길이를 검사하고 초과값을 잘라 저장하지 않는다. | 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. | si-cost | SPEC-REQUEST.md:L86 — &#124;문자열&#124;trim 후 길이 검사, 필수 빈칸400. 초과값을 잘라 저장하지 않음&#124; |
| REQ-014 | null | functional | api-contract | 그룹ID 정규화 | 그룹ID를 대문자로 정규화하고 원문 패턴과 길이를 검사한다. | 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. | si-cost | SPEC-REQUEST.md:L87 — &#124;그룹ID&#124;소문자→대문자 후 `^[A-Z][A-Z0-9_]{0,29}$`,1~30자&#124; |
| REQ-015 | null | functional | api-contract | 코드 정규화 | 코드를 대문자로 정규화하고 원문 패턴과 길이를 검사한다. | 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. | si-cost | SPEC-REQUEST.md:L88 — &#124;코드&#124;소문자→대문자 후 `^[A-Z0-9_]{1,20}$`,1~20자&#124; |
| REQ-016 | null | functional | api-contract | 명칭 경계와 식별자 불변 | 그룹명 1~50자·코드명 1~100자를 검증하고 등록된 ID/코드는 변경하지 못한다. | 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. | si-cost | SPEC-REQUEST.md:L89 — &#124;그룹명/코드명&#124;각1~50/1~100자. 등록된 ID/코드는 불변&#124; |
| REQ-017 | null | functional | api-contract | 정밀 십진값 범위 | CODE_VAL·STD_VAL을 숫자 문자열과 BigDecimal로 검사·저장한다. | 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. | si-cost | SPEC-REQUEST.md:L90 — &#124;CODE_VAL·STD_VAL&#124;0~99,999,999,999.9999 포함, 정수부 최대11·소수 최대4자리. 숫자 문자열로 주고받고 BigDecimal로 검사/저장&#124; |
| REQ-018 | null | functional | api-contract | 숫자 입력 정리와 금지값 | 공백·쉼표·끝 원/%만 정리하고 음수·지수·NaN·Infinity·다른 문자를 거부한다. | 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. | si-cost | SPEC-REQUEST.md:L91 — &#124;숫자 입력 처리&#124;공백·쉼표·끝의 원/% 표시는 제거 후 검사. 음수·지수표기·NaN·Infinity·그 밖의 문자는400. 소수5자리 또는 정수12자리 입력도400&#124; |
| REQ-019 | null | functional | api-contract | 소수 원값 보존 | 자동 반올림·버림 없이 허용 정밀도를 저장하고 화면 뒤 0만 생략한다. | 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. | si-cost | SPEC-REQUEST.md:L92 — &#124;반올림&#124;CODE_VAL/STD_VAL은 입력 원값 보존, 자동 반올림/버림0회. 허용 소수 이내는 정확히 저장하며 화면 뒤0만 생략&#124; |
| REQ-020 | null | functional | api-contract | JOB 월단가 정수 | JOB.ADD_VAL2는 정리 후 0~999,999,999,999,999원 정수 문자열로 저장한다. | 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. | si-cost | SPEC-REQUEST.md:L93 — &#124;JOB.ADD_VAL2 월단가&#124;0~999,999,999,999,999원 포함·소수0자리. 쉼표/공백/끝의 ‘원’ 정리 후 정수 문자열로 ADD_VAL2 저장. 음수/소수/상한 초과400&#124; |
| REQ-021 | null | functional | api-contract | 실제 연월 검증 | 연월을 0001-01~9999-12의 실제 YYYY-MM으로 검사한다. | 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. | si-cost | SPEC-REQUEST.md:L94 — &#124;연월&#124;0001-01~9999-12의 실제 연월. 월00/13·형식 오류400, 월 가감은 날짜 라이브러리 사용&#124; |
| REQ-022 | null | functional | api-contract | Y/N·JSON 타입 | Y/N·JSON boolean·PREF_VAL 최대 2000자를 엄격히 검사한다. | true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. | si-cost | SPEC-REQUEST.md:L95 — &#124;Y/N·JSON&#124;Y/N만, boolean은 JSON true/false만. PREF_VAL JSON 전체 최대2000자. 타입 오류400&#124; |
| REQ-023 | null | functional | api-contract | 빈 수당·기준값 구분 | 신규 수당 기본/빈 CODE_VAL은 0을 허용하고 기준값 빈칸은 거부하며 % 임의100 상한을 추가하지 않는다. | 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. | si-cost | SPEC-REQUEST.md:L97 — 기준값 빈칸은400, 수당 CODE_VAL의 신규 기본0·비운 값0은 허용한다. 단위가 %라는 이유만으로 임의100 상한을 추가하지 않으며 별도 제한은6.2의 표만 적용한다. 금액 원 단위 콤마, 음수 표시 함수는 ‘−’,0은‘0’,NULL은‘–’, 일자YYYY-MM-DD·일시YYYY-MM-DD HH:mm, 숫자 오른쪽/tabular-nums·문자 왼쪽·상태 가운데다. 표시 함수는 저장값을 바꾸지 않는다. |
| REQ-024 | null | functional | ui-supporting | 공통 표시 함수 | 금액·음수·0·NULL·일자·일시 및 숫자/문자/상태 정렬은 원문 표시 계약을 따른다. | 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다. | si-cost | SPEC-REQUEST.md:L97 — 기준값 빈칸은400, 수당 CODE_VAL의 신규 기본0·비운 값0은 허용한다. 단위가 %라는 이유만으로 임의100 상한을 추가하지 않으며 별도 제한은6.2의 표만 적용한다. 금액 원 단위 콤마, 음수 표시 함수는 ‘−’,0은‘0’,NULL은‘–’, 일자YYYY-MM-DD·일시YYYY-MM-DD HH:mm, 숫자 오른쪽/tabular-nums·문자 왼쪽·상태 가운데다. 표시 함수는 저장값을 바꾸지 않는다. |
| REQ-025 | null | functional | api-contract | 임시 사용자 컨텍스트 | CurrentUserProvider가 DB admin·roleIds=[ADMIN]·표시명 임시 관리자·developmentMode=true를 공급한다. | GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다.; api_endpoint:GET /api/current-user | si-cost | SPEC-REQUEST.md:L101 — - CurrentUserProvider는 DB의 admin·roleIds=[ADMIN]·표시명‘임시 관리자’·developmentMode=true를 제공한다. 헤더에 개발용임을 표시한다. 등록자·수정자·설정 USER_ID는 admin이며 요청으로 다른 처리자를 지정하지 못한다. |
| REQ-026 | null | functional | api-contract | 처리자 주입 거부 | 등록자·수정자·설정 USER_ID는 admin으로 고정하고 요청의 다른 처리자 지정을 거부한다. | 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. | si-cost | SPEC-REQUEST.md:L101 — - CurrentUserProvider는 DB의 admin·roleIds=[ADMIN]·표시명‘임시 관리자’·developmentMode=true를 제공한다. 헤더에 개발용임을 표시한다. 등록자·수정자·설정 USER_ID는 admin이며 요청으로 다른 처리자를 지정하지 못한다. |
| REQ-027 | null | functional | api-contract | 인증·권한 유예 | 현재 권한 서비스는 허용·DataScope=ALL이며 한 공급자로 교체 가능하고 인증 차단은 만들지 않는다. | /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다. | si-cost | SPEC-REQUEST.md:L102 — - 인증 리다이렉트/401·역할별403·민감칸 가림은0건이다. 권한 서비스의 현재 결과는 허용·DataScope=ALL이며 공급자는 한곳에서 교체 가능하게 둔다. 관리자 ID를 각 업무 로직에 복제하지 않는다. |
| REQ-028 | null | functional | api-contract | 원자적 업무 변경 | 코드 한 그룹·기준 한 요청·대응 추가/다중 삭제는 단일 트랜잭션이며 키 오름차순 잠금한다. | 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. | si-cost | SPEC-REQUEST.md:L103 — - 코드 한 그룹 저장, 운영기준 한 요청, 대응 추가/다중 삭제는 각각 단일 트랜잭션이다. 한 항목 실패 시 데이터와 이력 모두0건 변경이다. 잠금은 키 오름차순이다. |
| REQ-029 | null | functional | api-contract | 불투명 revision | 조회 revision은 식별자·저장값·등록/수정 시각으로 계산하며 그룹 전체 코드·기준키 전체 이력을 포함한다. | 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. | si-cost | SPEC-REQUEST.md:L104 — - 조회는 불투명 revision, 변경은 expectedRevision을 사용한다. 비교값은 기존 식별자·저장값·REG_DTTM/MOD_DTTM으로 계산한다. 그룹은 그룹과 소속 전체 코드 행, 기준키는 전체 적용월 이력을 포함한다. 시각 양쪽NULL은 시각만 같고 한쪽NULL은 다르다. 양쪽NULL이어도 값이 바뀌면 다른 revision이다. |
| REQ-030 | null | functional | api-contract | 잠금 후 동시 수정 검출 | 대상 잠금 후 expectedRevision을 최신값과 비교하고 불일치면 409 STALE_WRITE다. | 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. | si-cost | SPEC-REQUEST.md:L105 — - 대상 그룹/기준키 잠금 후 최신값과 비교하고 불일치면409 STALE_WRITE다. 두 클라이언트가 같은 revision으로 다른 값을 저장하면 선행1건 성공·후행1건409, 후행 DB/이력 변경0건이다. 스키마에 version 컬럼은 추가하지 않는다. |
| REQ-031 | null | functional | api-contract | 실제 변경 없는 저장 | revision 일치 후 변경이 없으면 200 changed=false와 안내를 반환하고 수정시각·이력을 갱신하지 않는다. | 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. | si-cost | SPEC-REQUEST.md:L106 — - revision 일치 후 실제 변경0이면200 changed=false·“바뀐 내용이 없습니다.”, 수정시각/이력 갱신0건이다. 변경이 있으면 commit 후 changed=true와 재조회 결과를 반영한다. |

#### commit 후 캐시 최신화부터 즐겨찾기 편집까지
<!-- speckit:section sec-8737a46ed62a44d1811f8baecc3028d7 -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-032 | null | functional | api-contract | commit 후 캐시 최신화 | 성공 응답 후 2초 이내 현재 브라우저 관련 캐시·선택목록·설정을 갱신하고 서버 캐시는 commit 후 무효화한다. | 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다. | si-cost | SPEC-REQUEST.md:L107 — - 저장 성공 응답 수신 후2초 이내에 저장 브라우저의 관련 캐시·열린 선택 목록·설정을 최신값으로 갱신한다. 서버 캐시는 commit 이후 무효화한다. 다른 브라우저는 다음 조회에서 최신값을 받는다. 통신 오류는 재시도 안내이며 오래된 값을 성공 결과로 표시하지 않는다. |
| REQ-033 | null | functional | ui-supporting | 한국어 오류와 재조회 실패 구분 | 필드 오류·첫 포커스·오류 띠·다시 시도·requestId와 commit 후 재조회 실패를 구분한다. | 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다. | si-cost | SPEC-REQUEST.md:L108 — - 오류는 `{code,message,field?,details?,requestId}`·X-Request-Id다. 필드 오류·첫 오류 포커스, 연결/500의 오류 띠·[다시 시도]를 제공한다. commit 후 재조회만 실패하면 “저장됐지만 다시 불러오지 못했습니다.”로 구분한다. 자동 저장/재시도0회·처리 중 중복 클릭 차단·입력 보존이다. |
| REQ-034 | null | functional | ui-supporting | 중복 클릭·자동 재저장 금지 | 처리 중 중복 클릭을 막고 입력을 보존하며 자동 저장/재시도는 하지 않는다. | 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다. | si-cost | SPEC-REQUEST.md:L108 — - 오류는 `{code,message,field?,details?,requestId}`·X-Request-Id다. 필드 오류·첫 오류 포커스, 연결/500의 오류 띠·[다시 시도]를 제공한다. commit 후 재조회만 실패하면 “저장됐지만 다시 불러오지 못했습니다.”로 구분한다. 자동 저장/재시도0회·처리 중 중복 클릭 차단·입력 보존이다. |
| REQ-035 | null | functional | ui-primary | DB 메뉴 구성·정렬 | USE_YN=Y의 정상 DB 부모/자식과 이름·아이콘·PC 전용 여부를 읽고 형제를 SORT_ORD/MENU_ID순 표시한다. | 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다.; api_endpoint:GET /api/menus | si-cost | SPEC-REQUEST.md:L114 — TB_SY_MENU를 API로 읽어 USE_YN=Y의 정상 부모/자식 관계를 구성한다. 형제는 SORT_ORD/MENU_ID순, 이름·아이콘·PC 전용 여부는 DB 원천이다. 아래는 초기값 검증 목록이며 프론트 하드코딩 메뉴 원천으로 사용하지 않는다. |
| REQ-036 | null | functional | ui-primary | 메뉴 펼침·접힘 탐색 | 현재 묶음 하나를 기본 펼치며 대시보드 직접 이동·부모 이름 이동/아이콘 토글·접힌 hover/focus 목록을 제공한다. | 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다. | si-cost | SPEC-REQUEST.md:L129 — - 현재 묶음을 자동 펼치고 기본은 한 묶음만 펼친다. 대시보드는 그룹+자식 데이터여도 한 항목으로 바로 이동한다. 화면ID가 있는 부모의 이름은 이동·펼침 아이콘은 토글이다. 접힌 메뉴는 hover/focus 하위 목록을 제공한다. |
| REQ-037 | null | functional | ui-primary | 잘못된 메뉴 트리 진단 | 비정상 노드와 자손·중복 screen 탈락 트리를 제외하고 진단을 남기며 정상 부분만 표시한다. | 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다. | si-cost | SPEC-REQUEST.md:L130 — - 없는/중지 부모·자기참조·순환·MENU_LVL 깊이 불일치·미등록 화면ID는 해당 노드와 자손을 제외하고 진단을 남긴다. 중복 SCRN_ID는 정상 계층→SORT_ORD→MENU_ID순 대표1개, 탈락 트리도 제외한다. 다른 부모로 자동 이동하거나 DML을 수정하지 않는다. 정상 부분은 표시하되 초기 메뉴 오류가 남으면 합격하지 않는다. |
| REQ-038 | null | functional | ui-primary | 공식 라우트와 복구 | /{screenId}·/→/UI-16·hash replace를 지원하고 유효 화면 부재 시 원문 순서로 복구한다. | /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다. | si-cost | SPEC-REQUEST.md:L131 — - React 등록표는 screenId/컴포넌트/기간 유형만 관리한다. 공식 경로는 `/{screenId}`, `/`는 `/UI-16`, `#UI-xx`는 공식 경로로 replace한다. 유효 메뉴가 없는 주소는 대시보드, 대시보드도 없으면 정상 첫 화면, 정상 화면0개면 메뉴 오류다. 리다이렉트 반복은0회다. |
| REQ-039 | null | functional | ui-supporting | PC 전용 화면 보호 | 폭768px 미만에서 PC 전용 메뉴/즐겨찾기/최근을 숨기고 직접 접근은 PC 안내를 표시한다. | 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다. | si-cost | SPEC-REQUEST.md:L132 — - PC_ONLY_YN=Y 초기 화면은 UI-10·14·23·24·25·27이다. 폭768px 미만에서 메뉴·즐겨찾기·최근에서 숨긴다. 직접 접근/창 축소는 본문 대신 PC 사용 안내·이전/대시보드 이동을 보여 준다. 미저장 상태는 유지하고 폭을 넓히면 복원한다. |
| REQ-040 | null | functional | ui-supporting | 반응형 공통 레이아웃 | AppLayout·Header·Sidebar·ScreenTabs·TitleBar를 원문 치수와 breakpoint에 맞춰 배치한다. | 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다. | si-cost | SPEC-REQUEST.md:L136 — - AppLayout이 Header/Sidebar/ScreenTabs/TitleBar/본문을 조합한다. 헤더56px, PC≥1280 메뉴 펼침240/접힘64px·여백24px, 태블릿768~1279 기본접힘·여백16px, 모바일≤767 메뉴 슬라이드·화면탭 없음이다. 최소360px에서 본문 전체 가로 넘침0, 넓은 표만 내부 스크롤한다. |
| REQ-041 | null | functional | ui-supporting | 헤더의 실제 제공 항목 | 메뉴 토글·대시보드 이동·임시 관리자/화면 설정을 제공하며 AI/알림은 준비 중 안내만 한다. | AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다. | si-cost | SPEC-REQUEST.md:L137 — - 헤더는 메뉴 토글·로고/시스템명(대시보드 이동)·AI/알림·임시 관리자 메뉴다. AI/알림 클릭은 “준비 중” 안내, 가짜 배지/대화는0개다. 사용자 메뉴는 화면 설정만 제공한다. 비밀번호·로그아웃·타이머는 없다. |
| REQ-042 | null | functional | ui-supporting | 화면 제목·기간 배치 | 묶음›화면명·즐겨찾기·UI-ID·작업을 표시하고 업무 기간은 제목 아래에 둔다. | UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. | si-cost | SPEC-REQUEST.md:L138 — - 제목은 묶음›화면명·즐겨찾기·UI-ID·해당 화면 작업이다. 업무 기간은 헤더가 아닌 제목 아래 본문 줄이다. UI-27은 업무 기간 줄이 없고 자체 적용월 입력을 사용한다. |
| REQ-043 | null | functional | ui-supporting | 화면 탭과 LRU | 화면을 재활성화 또는 새 탭으로 열고 대시보드 포함 최대8개·9번째는 닫을 수 있는 LRU 확인을 거친다. | 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다. | si-cost | SPEC-REQUEST.md:L139 — - 메뉴 클릭은 탭 생성, 열린 화면이면 재활성화다. 최대8개(대시보드 포함), 대시보드는 맨 앞·닫기 불가다.9번째는 가장 오래 안 본 닫을 수 있는 탭을 제시해 확인한다. 취소하면 새 탭0개·기존 탭 유지다. |
| REQ-044 | null | functional | ui-supporting | 편집 보존과 이동 경고 | 단순 탭 전환은 상태를 보존하고 그룹/하위탭 변경·닫기·상태 폐기 이동은 미저장 확인을 거친다. | 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다. | si-cost | SPEC-REQUEST.md:L140 — - 일반 탭 전환은 편집·필터·기간·스크롤 보존이다. UI-27의 그룹/하위 탭 변경·탭 닫기·상태를 버리는 이동은 “저장하지 않은 내용이 있습니다.”와 [계속 작성]/[나가기], 취소 시 상태를 유지한다. dirty 탭은 ●, 새로고침/창 닫기는 beforeunload다. 모달은 focus trap·닫기 후 포커스 복원, 입력은 label·Tab·오류 포커스를 제공한다. |
| REQ-045 | null | functional | ui-supporting | 접근 가능한 모달·입력 | 모달 focus trap·닫기 후 포커스 복원과 입력 label/Tab/첫 오류 포커스를 제공한다. | 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다. | si-cost | SPEC-REQUEST.md:L140 — - 일반 탭 전환은 편집·필터·기간·스크롤 보존이다. UI-27의 그룹/하위 탭 변경·탭 닫기·상태를 버리는 이동은 “저장하지 않은 내용이 있습니다.”와 [계속 작성]/[나가기], 취소 시 상태를 유지한다. dirty 탭은 ●, 새로고침/창 닫기는 beforeunload다. 모달은 focus trap·닫기 후 포커스 복원, 입력은 label·Tab·오류 포커스를 제공한다. |
| REQ-046 | null | functional | ui-supporting | 새로고침 탭 초기화 | 새로고침은 URL 화면과 고정 대시보드로 시작하며 열린 탭 목록은 서버 복원하지 않는다. | 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. | si-cost | SPEC-REQUEST.md:L141 — - 새로고침은 URL 화면+고정 대시보드로 시작한다. 열린 탭 목록 자체를 서버 복원하지 않는다. |
| REQ-047 | null | functional | ui-primary | 설정 조회와 기본값 | admin 설정 GET은 value/revision/stored를 반환하며 행 없음은 기본값·MENU_FOLD만 null이다. | 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다.; api_endpoint:GET /api/me/preferences | si-cost | SPEC-REQUEST.md:L145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다. |
| REQ-048 | null | functional | ui-primary | 설정 명시 저장 | 최초 PUT은 expectedRevision=null·기존은 토큰이며 기본값과 같아도 최초 명시 저장은 INSERT한다. | 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다.; api_endpoint:PUT /api/me/preferences/{type}/{key} | si-cost | SPEC-REQUEST.md:L145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다. |
| REQ-049 | null | functional | ui-primary | 손상 설정 격리 | 잘못된 JSON은 항목 오류/revision과 기본값 임시 표시로 격리하고 자동 삭제하지 않는다. | 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. | si-cost | SPEC-REQUEST.md:L145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다. |
| REQ-050 | null | functional | ui-primary | 개별 설정 초기화 | 이 설정 초기화는 해당 admin·type·key 행만 물리 삭제하며 UI-27 이력을 남기지 않는다. | 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다.; api_endpoint:DELETE /api/me/preferences/{type}/{key} | si-cost | SPEC-REQUEST.md:L145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다. |
| REQ-051 | null | functional | ui-primary | 즐겨찾기 편집 | 화면 별 추가/해제·▲▼ 순서를 FAV에 저장하고 중복 없이 최대10개를 허용한다. | 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. | si-cost | SPEC-REQUEST.md:L149 — &#124;FAV / *&#124;화면ID 배열·기본[]·최대10개·중복 불가. 별 추가/해제, 목록 편집▲▼로 순서 변경.11번째는 거부&#124; |

#### 최근 화면 자동 갱신부터 일반 코드 일괄 변경 저장까지
<!-- speckit:section sec-417fb2314b4342699c461fa44850a779 -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-052 | null | functional | ui-supporting | 최근 화면 자동 갱신 | 활성화순 RECENT를 중복 없이 최대5개로 저장하고 재방문을 맨 앞으로 옮긴다. | 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. | si-cost | SPEC-REQUEST.md:L150 — &#124;RECENT / *&#124;최근 활성화순 화면ID 배열·기본[]·최대5개. 같은 화면 재방문은 맨 앞으로 이동·중복 제거·초과 뒤에서 제거&#124; |
| REQ-053 | null | functional | ui-primary | 메뉴 접힘 기억 | MENU_FOLD boolean을 저장하고 미저장 PC=false·태블릿=true, 모바일 슬라이드는 임시 상태다. | PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. | si-cost | SPEC-REQUEST.md:L151 — &#124;MENU_FOLD / *&#124;boolean. 저장값 없으면 PC=false·태블릿=true. 모바일 슬라이드 열림은 임시 상태이며 이 값을 변경하지 않음&#124; |
| REQ-054 | null | functional | ui-primary | 목록 크기 설정 | PAGE_SIZE는 JSON 정수20/50/100만 저장하며 기본50이다. | 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. | si-cost | SPEC-REQUEST.md:L152 — &#124;PAGE_SIZE / *&#124;JSON 정수20/50/100·기본50. 문자열‘50’ 거부&#124; |
| REQ-055 | null | functional | ui-primary | 밀도 설정 동기화 | DENSE 넓게/좁게를 기본 넓게로 서버 저장하고 열린 목록과 사용자 메뉴에 동일 적용한다. | 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. | si-cost | SPEC-REQUEST.md:L153 — &#124;DENSE / *&#124;‘넓게’/‘좁게’·기본넓게. 모든 열린 목록과 사용자 메뉴에 같은값 적용&#124; |
| REQ-056 | null | functional | ui-primary | 표 열 설정 저장 | COLS는 표별 전체 순서와 선택 숨김을 저장하고 필수열 숨김·중복·미등록열을 거부한다. | 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. | si-cost | SPEC-REQUEST.md:L154 — &#124;COLS / screenId&#124;표별 `{viewId:{order:[columnId],hidden:[columnId]}}`·기본 전체열/기본순서. 필수열 숨김·중복·미등록열 거부&#124; |
| REQ-057 | null | functional | ui-primary | 금액 단위 선택 기억 | AMT_UNIT 원/천원/백만원을 기본 천원으로 저장/복원하되 업무 금액 변환은 하지 않는다. | 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. | si-cost | SPEC-REQUEST.md:L155 — &#124;AMT_UNIT / *&#124;‘원’/‘천원’/‘백만원’·기본천원. 선택/저장/복원만, 실제 업무 금액 변환은 이번 범위에서 제외&#124; |
| REQ-058 | null | functional | ui-primary | 설정 화면ID·표시 보호 | 즐겨찾기/최근은 등록 screenId만 허용하고 PC 숨김은 배열 삭제가 아닌 표시만 숨긴다. | 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. | si-cost | SPEC-REQUEST.md:L158 — - 즐겨찾기/최근 항목은 등록된 화면ID만 허용한다. 숨겨진 PC 전용 항목은 배열에서 삭제하지 않고 표시만 숨긴다. 메뉴 접힘 상태에서는 두 목록을 숨긴다. 빈 안내는 각각 “화면 제목 옆 ☆를 누르면 추가됩니다”·“아직 없습니다”다. |
| REQ-059 | null | functional | ui-primary | UI-27 열 정의 제한 | UI-27 viewId와 필수열을 고정하고 체크/작업열·아직 표 없는 다른 화면의 COLS를 받지 않는다. | codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. | si-cost | SPEC-REQUEST.md:L159 — - UI-27의 viewId는 codes/valueCodes/mappings/standards/history다. order는 해당 표 정의의 전체 열 순열, hidden은 선택 열 부분집합이다. 코드/값코드의 code/name, 대응의 sourceText/groupId/code, 기준의 key/name/value, 이력의 occurredAt/target는 필수열이다. 선택 체크·작업 버튼은 열 설정에서 제외한다. 다른 화면의 COLS는 해당 화면에 표 정의가 생긴 뒤 허용한다. |
| REQ-060 | null | functional | ui-primary | 설정 적용과 실패 보존 | PAGE_SIZE·DENSE는 서버 성공 후 열린 목록에 반영하고 실패 시 이전값/편집값을 유지한다. | 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다. | si-cost | SPEC-REQUEST.md:L161 — - PAGE_SIZE·DENSE는 목록과 사용자 메뉴 어디서 바꿔도 서버 저장 후 열린 목록에 반영한다. 필터/페이지/열 설정·밀도는 미저장 편집값을 삭제하지 않는다. 설정 저장 실패는 이전 적용값으로 유지·오류 안내다. localStorage만으로 합격하지 않는다. |
| REQ-061 | null | functional | ui-primary | 기간 설정 타입·범위 검사 | PERIOD는 화면 유형·연월 일치·허용범위를 검사하고 NONE 쓰기를 거부하며 범위 밖 저장값을 조용히 바꾸지 않는다. | NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. | si-cost | SPEC-REQUEST.md:L162 — - PERIOD는 등록표 유형·연도와 월 일치·4.4의 범위를 검사한다. NONE 화면의 PERIOD 쓰기는400이다. 저장된 기간이 새 허용범위 밖이면 DB를 조용히 바꾸지 않고 안내와 기본기간 표시, 사용자가 다시 고를 때 저장한다. |
| REQ-062 | null | functional | ui-supporting | controlled 기간 부품 | PeriodSelector는 부모의 type/value/min/max/disabled를 쓰고 DB 조회나 설정 저장을 내부에서 하지 않는다. | YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다. | si-cost | SPEC-REQUEST.md:L166 — PeriodSelector는 React·TypeScript **controlled** 부품이다. Props는 `type:'YEAR'&#124;'YEAR_MONTH', value:string, onChange(nextValue), disabled?, min?, max?, ariaLabel?`다. YEAR는 YYYY, YEAR_MONTH는 YYYY-MM만 전달한다. 값/범위/disabled는 부모 소유, 내부 DB 조회·설정 저장0회다. 범위 밖 화살표·disabled 입력은 비활성이고 변경 이벤트0회, 유효 선택1회는 이벤트1회다. |
| REQ-063 | null | functional | ui-primary | 화면 기간 선택·기억 | ScreenPeriodBar는 서버 현재일/설정과 연결하고 누계 연도·기준월을 범위로 제한하여 기간을 저장한다. | 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다.; api_endpoint:GET /api/period-context | si-cost | SPEC-REQUEST.md:L168 — ScreenPeriodBar가 서버 현재일·설정과 연결한다. 누계형은 연도+기준월·‘1~n월 누계’를 조합하며 연도 변경 시 기존 월을 새 범위로 제한한다. 기본기간은 현재 연월을 범위 안으로 제한한 값이다. 화면별 유형은 아래와 같다. |
| REQ-064 | null | functional | ui-primary | 기간 범위와 운영 전 상태 | CASH_BASE_YM와 현재일로 유형별 범위를 산정하고 범위 역전은 운영 시작 전·비활성·저장 금지다. | YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. | si-cost | SPEC-REQUEST.md:L177 — - 현재 빈 업무화면의 YEAR 범위는 CASH_BASE_YM의 연도~현재연도+1, YEAR_MONTH는 CASH_BASE_YM~현재월이다. 누계형은 시작연도~현재연도, UI-07/08/09만 시작연도-1부터 허용한다. 누계 기준월은 선택연도의1월~12월, 현재연도는 현재월까지만이다. 범위 역전은 ‘운영 시작 전’·선택 비활성·PERIOD 저장0회다. |
| REQ-065 | null | functional | ui-primary | 연도 배지 | 날짜 비교로 올해/지난 해·읽기 전용/내년·계획 배지를 표시하고 업무 계산/재계산은 하지 않는다. | 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다. | si-cost | SPEC-REQUEST.md:L178 — - 연도 배지는 올해/지난 해·읽기 전용/내년·계획을 날짜 비교로 표시한다. 월 배지는 아래 상태 조회를 사용한다. 이번에는 기간 선택·표시·기억까지만 구현하고 업무 데이터의 계산/쓰기/재계산은 없다. |
| REQ-066 | null | functional | ui-primary | 실제 월 마감 조회 | isClosed는 시작월 이전 이관·결산완료, CLOSE는 마감, WORK/행 없음은 작업중으로 판단한다. | CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다.; api_endpoint:GET /api/month-close | si-cost | SPEC-REQUEST.md:L179 — - isClosed(ym)는 CASH_BASE_YM 이전이면 이관·결산완료, 이후 TB_CL_CLOSE.CLOSE_STAT_CD=CLOSE면 마감, WORK/행 없음이면 작업중이다. 서버 현재일과 DB로 판단하고9개월 마감 같은 가상 기록은0건이다. 마감 쓰기 API는0개다. |
| REQ-067 | null | functional | ui-supporting | UI-27 네 탭과 조회 상태 | 코드/엑셀 표기 대응/운영 기준값/변경 이력 4탭과 loading/empty/error를 구분한다. | 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. | si-cost | SPEC-REQUEST.md:L185 — - [코드] [엑셀 표기 대응] [운영 기준값] [변경 이력]4탭이다. 로딩·빈 결과·API 오류를 구분한다. 코드/대응/변경 이력은 페이지20/50/100·기본50, page≥1, 잘못된 size/page는400이다. 운영 기준값은43키를 묶음 줄로 모두 표시하고 페이지를 나누지 않는다. 공통 표의 페이지 기능은 선택 옵션이다. 기본 정렬은 각 절에 고정하며 페이지 이동도 편집값을 유지한다. |
| REQ-068 | null | functional | api-contract | 표 페이지 계약 | 코드/대응/이력은 page≥1·size20/50/100·기본50, 기준값은 전체43키 비페이지 목록이다. | page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. | si-cost | SPEC-REQUEST.md:L185 — - [코드] [엑셀 표기 대응] [운영 기준값] [변경 이력]4탭이다. 로딩·빈 결과·API 오류를 구분한다. 코드/대응/변경 이력은 페이지20/50/100·기본50, page≥1, 잘못된 size/page는400이다. 운영 기준값은43키를 묶음 줄로 모두 표시하고 페이지를 나누지 않는다. 공통 표의 페이지 기능은 선택 옵션이다. 기본 정렬은 각 절에 고정하며 페이지 이동도 편집값을 유지한다. |
| REQ-069 | null | functional | ui-primary | 그룹 목록과 선택 | 전체 그룹을 SORT_ORD/CODE_GRP_ID순으로 표시하고 첫 선택·코드 수·시스템 잠금·스크롤 위치를 제공한다. | 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다.; api_endpoint:GET /api/code-groups | si-cost | SPEC-REQUEST.md:L186 — - 그룹은 DB 전체를 SORT_ORD/CODE_GRP_ID순으로 표시한다. 초기64개(시스템44), 첫 그룹 자동 선택·선택 강조·업무코드 수·시스템 자물쇠다. 목록 높이 안 별도 스크롤·선택 후 위치 유지. 그룹 이름/사용 여부 변경·그룹 삭제 기능은0개다. |
| REQ-070 | null | functional | ui-primary | 일반 코드 조회 | 일반 표는 지정 열과 저장된 SORT_ORD/CODE순이며 미삭제 중지 코드도 흐리게 표시한다. | GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다.; api_endpoint:GET /api/code-groups/{group}/codes | si-cost | SPEC-REQUEST.md:L187 — - 일반 코드 표는 code/name/addVal1~2(적용 그룹만)/sortOrder/useYn/deleteAction, 이름·사용·허용 추가값만 편집한다. 순서는 저장된 SORT_ORD·CODE순·읽기 전용이다. DEL_YN=N의 중지 코드도 흐리게 표시한다. 코드 표의 선택 체크/일괄 중지 버튼은0개, 여러 행을 수정 후 [저장]1회로 반영한다. |
| REQ-071 | null | functional | ui-primary | 일반 코드 일괄 변경 저장 | 이름·사용·허용 추가값을 여러 행 편집하여 그룹 단위 저장1회로 반영한다. | 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다.; api_endpoint:PUT /api/code-groups/{group}/codes | si-cost | SPEC-REQUEST.md:L187 — - 일반 코드 표는 code/name/addVal1~2(적용 그룹만)/sortOrder/useYn/deleteAction, 이름·사용·허용 추가값만 편집한다. 순서는 저장된 SORT_ORD·CODE순·읽기 전용이다. DEL_YN=N의 중지 코드도 흐리게 표시한다. 코드 표의 선택 체크/일괄 중지 버튼은0개, 여러 행을 수정 후 [저장]1회로 반영한다. |

#### 편집 취소 복원부터 수당 값 변경과 업무 불변까지
<!-- speckit:section sec-01525bfd177a4fdd8635c2007d3bb6cd -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-072 | null | functional | ui-primary | 편집 취소 복원 | 취소는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. | 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다. | si-cost | SPEC-REQUEST.md:L188 — - [취소]는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. [저장]은 변경분만 전송하고 현재 페이지에 없는 행을 삭제하지 않는다. 동일 code를 한 요청에2회 보내면400이다. |
| REQ-073 | null | functional | ui-primary | 부분 변경·중복 코드 검사 | 저장은 변경분만 받고 다른 페이지 행은 삭제하지 않으며 동일 code 요청2회는 거부한다. | 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. | si-cost | SPEC-REQUEST.md:L188 — - [취소]는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. [저장]은 변경분만 전송하고 현재 페이지에 없는 행을 삭제하지 않는다. 동일 code를 한 요청에2회 보내면400이다. |
| REQ-074 | null | functional | ui-primary | 시스템 코드 변경 제한 | SYS_CODE_YN=Y는 이름만 변경하고 다른 변경이 섞이면 전체409 SYSTEM_CODE_LOCKED다. | CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. | si-cost | SPEC-REQUEST.md:L189 — - SYS_CODE_YN=Y는 이름만 변경한다. 신규·삭제·순서·사용·추가값·값/단위 변경은409 SYSTEM_CODE_LOCKED이고 같은 요청의 이름 변경도 롤백한다. “이름만 바꿀 수 있습니다.”를 표시한다. |
| REQ-075 | null | functional | ui-primary | 그룹 중복과 채번 경합 | groupId/name 필수·중복ID/trim후 동일명 거부하며 그룹 테이블 직렬화 잠금으로 중복/채번 경합을 막는다. | 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. | si-cost | SPEC-REQUEST.md:L193 — - [그룹 추가]: groupId/name 필수·3.2 규칙, ID 중복409 DUPLICATE_GROUP, 이름도 trim 후 같은 문자열이면409 DUPLICATE_GROUP_NAME이다. 그룹 추가 중 그룹 테이블을 직렬화 잠금해 이름 중복과 SORT_ORD 채번 경합을 막는다. |
| REQ-076 | null | functional | ui-primary | 업무 그룹 생성 | 새 그룹은 SYS=N·VAL=N·USE=Y·SORT=max+1로 저장하고 NEW 이력 후 선택/재조회한다. | POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다.; api_endpoint:POST /api/code-groups | si-cost | SPEC-REQUEST.md:L194 — - 새 그룹은 SYS_CODE_YN=N·VAL_CODE_YN=N·USE_YN=Y·SORT_ORD=max+1이다. DB 저장과 NEW 이력 후 선택·재조회한다. 미저장 코드가 있으면 먼저 저장/취소한다. 임의 추가값 컬럼 정의는 제공하지 않는다. |
| REQ-077 | null | functional | ui-primary | 그룹 추가 전 편집 처리 | 미저장 코드가 있으면 저장/취소 후 그룹을 추가하며 임의 추가값 컬럼 정의는 제공하지 않는다. | 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다. | si-cost | SPEC-REQUEST.md:L194 — - 새 그룹은 SYS_CODE_YN=N·VAL_CODE_YN=N·USE_YN=Y·SORT_ORD=max+1이다. DB 저장과 NEW 이력 후 선택·재조회한다. 미저장 코드가 있으면 먼저 저장/취소한다. 임의 추가값 컬럼 정의는 제공하지 않는다. |
| REQ-078 | null | functional | ui-primary | 일반 신규 코드 편집 행 | 코드 추가창은 code/name/해당 추가값을 검증하고 DB가 아닌 신규 편집 행에만 추가한다. | 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다. | si-cost | SPEC-REQUEST.md:L195 — - 일반 [코드 추가] 창은 code/name·해당 추가값 필수다. 소문자는 대문자 정규화, 같은 그룹 중복은 DEL_YN=Y도409 DUPLICATE_CODE다. 창 [추가]는 편집 목록의 신규 행으로만 넣고 하단 [저장] 때 DB에 저장한다. 신규 취소는 DB 변경0건이다. |
| REQ-079 | null | functional | ui-primary | 그룹 내 코드 PK 재사용 금지 | 같은 그룹 code는 삭제된 행을 포함하여 중복409 DUPLICATE_CODE다. | DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. | si-cost | SPEC-REQUEST.md:L195 — - 일반 [코드 추가] 창은 code/name·해당 추가값 필수다. 소문자는 대문자 정규화, 같은 그룹 중복은 DEL_YN=Y도409 DUPLICATE_CODE다. 창 [추가]는 편집 목록의 신규 행으로만 넣고 하단 [저장] 때 DB에 저장한다. 신규 취소는 DB 변경0건이다. |
| REQ-080 | null | functional | ui-primary | 신규 코드 기본값·정렬 채번 | 새 코드는 USE=Y·DEL=N·그룹 max+1이고 같은 요청 신규는 전달 순서로 채번한다. | 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. | si-cost | SPEC-REQUEST.md:L196 — - 새 코드는 USE_YN=Y·DEL_YN=N, SORT_ORD는 그룹 내 max+1이며 같은 요청의 신규는 전달 순서다. SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 전체 롤백한다. 저장된 PK는 변경하지 않는다. |
| REQ-081 | null | functional | ui-primary | 정렬 순번 한계 | SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 그룹 전체 저장을 롤백한다. | max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. | si-cost | SPEC-REQUEST.md:L196 — - 새 코드는 USE_YN=Y·DEL_YN=N, SORT_ORD는 그룹 내 max+1이며 같은 요청의 신규는 전달 순서다. SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 전체 롤백한다. 저장된 PK는 변경하지 않는다. |
| REQ-082 | null | functional | ui-primary | 코드명 중복 허용 | 동일 코드명은 허용하고 그룹ID/코드로 식별한다. | 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. | si-cost | SPEC-REQUEST.md:L197 — - 코드명 중복은 허용하며 그룹ID/코드로 구분한다. |
| REQ-083 | null | functional | ui-primary | 그룹별 추가값 필수·길이 | 적용 그룹 추가값은 빈칸 불가·정규화 후100자 이하이고 다른 그룹 ADD_VAL은 NULL·ADD_VAL3은 미사용이다. | 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. | si-cost | SPEC-REQUEST.md:L201 — 추가·수정 시 아래 필드는 빈칸 불가, 정규화 후 각각100자 이하다. 다른 그룹의 ADD_VAL은 NULL, ADD_VAL3은 이번에 사용하지 않는다. 알려진 다른 그룹 필드를 보내면400이다. |
| REQ-084 | null | functional | ui-primary | PROJ_STEP 진척 정수 | PROJ_STEP.ADD_VAL1은 0~100 포함 정수이고 ADD_VAL2는 CTRT_TYPE CSV다. | 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. | si-cost | SPEC-REQUEST.md:L207 — &#124;PROJ_STEP&#124;0~100 포함 정수·소수 거부&#124;CTRT_TYPE 코드 CSV&#124; |
| REQ-085 | null | functional | ui-primary | BANK 코드와 RSM 라벨 | BANK.ADD_VAL1은 정확한3자리 숫자로 앞0을 보존한다. | 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. | si-cost | SPEC-REQUEST.md:L208 — &#124;BANK&#124;정확한 숫자3자리·앞0 보존&#124;없음&#124; |
| REQ-086 | null | functional | ui-primary | RSM_LABEL 허용값 | RSM_LABEL.ADD_VAL1은 명시8개 값 중 하나를 대문자 정규화해 저장한다. | name 입력은 NAME으로 저장; 목록 외 문자열은400이다. | si-cost | SPEC-REQUEST.md:L209 — &#124;RSM_LABEL&#124;NAME/TEL/MAIL/ADDR/SKILL/WANT/FROM/VNDR 중1개·대문자 정규화&#124;없음&#124; |
| REQ-087 | null | functional | ui-primary | CSV 정규화·참조 | CSV trim/대문자/빈항목 거부/첫 순서 중복제거 후 실제 미삭제·신규 active 참조를 최종 상태로 검사한다. | 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. | si-cost | SPEC-REQUEST.md:L212 — CSV는 쉼표 분리→항목trim/대문자→빈 항목 거부→첫 등장 순서로 중복 제거→쉼표 결합이다. 최소1개, 실제 코드 존재·미삭제·신규 참조는 USE_YN=Y를 검사한다. JOB 자기 자신 포함은 허용하며 필수는 아니다. 같은 저장 요청에서 새로 추가되는 JOB 참조도 최종 상태로 검사한다. 기존 중지 참조를 그대로 유지하는 것은 허용한다. |
| REQ-088 | null | functional | ui-primary | 수당 코드 조회·편집 형태 | 값 코드 그룹은 코드/이름/값/단위4열이며 기존 값만 편집한다. | 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다. | si-cost | SPEC-REQUEST.md:L216 — - VAL_CODE_YN=Y 그룹은 **코드/이름/값/단위**4열이다. 기존은 값만 편집, 코드·이름·단위는 읽기 전용이다. 사용/삭제/순서 작업은 없다. API의 기존 값 이외 필드 변경·삭제는409 VALUE_CODE_FIELD_LOCKED다. |
| REQ-089 | null | functional | ui-primary | 값 코드 잠금 | 기존 값 이외 변경/삭제는409 VALUE_CODE_FIELD_LOCKED이며 시스템 값 그룹은 시스템 규칙이 우선이다. | 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. | si-cost | SPEC-REQUEST.md:L216 — - VAL_CODE_YN=Y 그룹은 **코드/이름/값/단위**4열이다. 기존은 값만 편집, 코드·이름·단위는 읽기 전용이다. 사용/삭제/순서 작업은 없다. API의 기존 값 이외 필드 변경·삭제는409 VALUE_CODE_FIELD_LOCKED다. |
| REQ-090 | null | functional | ui-primary | 수당 신규 코드 저장 | 수당코드 추가는 code/name 필수·값 기본/빈0·unitNm 원/% 기본원이며 그룹 저장 때 반영한다. | 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. | si-cost | SPEC-REQUEST.md:L217 — - [수당코드 추가]는 코드·이름 필수, 값 기본0·비우면0, 단위는 원/% 중 선택·기본원이다. 3.2의 CODE_VAL 정밀도·코드 규칙을 적용한다. 추가 창은 신규 편집 행, 하단 저장은 그룹 단위 트랜잭션이다. |
| REQ-091 | null | functional | ui-primary | 수당 값 변경과 업무 불변 | 초기 수당은 SQL 값을 읽고 변경 후 공통 조회 값/단위만 갱신하며 업무 테이블은 바꾸지 않는다. | ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다. | si-cost | SPEC-REQUEST.md:L218 — - 초기 ALWNC6개는 ALLOW_TEAM=200000원, ALLOW_GROUP=400000원, INS_RATE=9.4%, SEV_RATE=8.33%, TEAM_FEE/PEXP_FEE=50000원이며 실제 값은 SQL에서 읽는다. 저장 후 공통 조회의 값/단위가 바뀌고 업무 테이블 값은 그대로다. 급여·보험·원가 계산 엔진은 생성하지 않는다. |

#### 일반 코드 중지·재개부터 자금 두 키 즉시 갱신까지
<!-- speckit:section sec-13a71407b60040a588abcb4f9d06a0bf -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-092 | null | functional | ui-primary | 일반 코드 중지·재개 | USE_YN N/Y 저장은 STOP/RESUME이며 active 선택 제외와 기존 label 조회를 구분한다. | 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. | si-cost | SPEC-REQUEST.md:L222 — - 일반 업무 코드의 USE_YN 체크를 N/Y로 바꿔 저장하면 STOP/RESUME 이력이다. 중지 코드는 신규 옵션에서 제외하고 기존 이름 조회는 가능하다. 다른 테이블의 저장 코드를 수정한 건수는0건이다. |
| REQ-093 | null | functional | ui-primary | 사용 상태 변경 이력 | 일반 업무 코드 중지·재개 시 STOP/RESUME 이력을 기록한다. | USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다. | si-cost | SPEC-REQUEST.md:L222 — - 일반 업무 코드의 USE_YN 체크를 N/Y로 바꿔 저장하면 STOP/RESUME 이력이다. 중지 코드는 신규 옵션에서 제외하고 기존 이름 조회는 가능하다. 다른 테이블의 저장 코드를 수정한 건수는0건이다. |
| REQ-094 | null | functional | ui-primary | 삭제 참조 차단 | 삭제 전 편집 처리·확인 후 참조가1건이라도 있으면409 CODE_IN_USE와 유형/건수/안내로 거부한다. | 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. | si-cost | SPEC-REQUEST.md:L223 — - 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다. |
| REQ-095 | null | functional | ui-primary | 일반 코드 논리 삭제 | 미참조 일반 업무 코드는 DEL_YN=Y로 논리 삭제하고 물리행/PK를 보존한다. | 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다.; api_endpoint:DELETE /api/code-groups/{group}/codes/{code} | si-cost | SPEC-REQUEST.md:L223 — - 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다. |
| REQ-096 | null | functional | ui-primary | 코드 삭제 이력 | 일반 코드 논리 삭제 시 DEL 이력을 남긴다. | 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다. | si-cost | SPEC-REQUEST.md:L223 — - 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다. |
| REQ-097 | null | functional | ui-primary | 고정 참조 가드 | CodeReferenceGuard는 고정 매핑과 공통 대응을 검사하며 취소·중지·마감 참조도 세고 자기 NEW 이력은 제외한다. | 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. | si-cost | SPEC-REQUEST.md:L224 — - CodeReferenceGuard는 다음 고정 매핑을 사용한다. 취소·중지·마감 자료도 검사하며 코드 자신의 NEW 이력은 사용으로 세지 않는다. 모든 그룹은 추가로 TB_SY_CODE_MAP의 group/code 참조를 검사한다. 요청의 테이블명으로 SQL을 만들지 않는다. |
| REQ-098 | null | functional | ui-primary | 삭제 직전 재검사 | 삭제 직전 대상 그룹/행을 잠그고 실제 참조를 재검사하며 ALWNC는 삭제 대상에서 제외한다. | 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. | si-cost | SPEC-REQUEST.md:L239 — ALWNC는5.4의 값 코드라 삭제 대상이 아니다. 참조 목록은 서버 코드에 명시하고 실제 사용을 fixture로 확인한다. 삭제 직전 대상 그룹/행을 잠그고 재검사한다. |
| REQ-099 | null | functional | ui-primary | 운영 기준 전체 조회 | SQL43키를 STD_CD순으로 읽어 이름/값/단위/적용월/설명/값변경/이력을 표시한다. | 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다.; api_endpoint:GET /api/operating-standards | si-cost | SPEC-REQUEST.md:L245 — - [운영 기준값]은 SQL의43키 전체를 읽어 이름·값·단위·적용 시작월·STD_DESC·[값 변경]/[이력]을 표시한다. 키·이름·단위·설명은 읽기 전용이고 신규 키/삭제 기능은 없다. 목록은 STD_CD순이다. |
| REQ-100 | null | functional | ui-primary | 기준 현재·과거·미래 이력 | 현재값은 현재월 이하 최신행이고 이력은 적용월 DESC·현재/예정/지난 상태로 보여준다. | 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다.; api_endpoint:GET /api/operating-standards/{key}/history | si-cost | SPEC-REQUEST.md:L246 — - 현재값은 APLY_BGN_YM≤현재월 중 가장 최근 행, 이력은 모든 적용월 DESC·현재/예정/지난 상태다. 미래 값은 현재값으로 사용하지 않는다. 해당월 기준이 없으면 ‘기준값 없음’으로 표시하며 임의 기본값 대입은0회다. |
| REQ-101 | null | functional | ui-primary | 일반 기준 적용 최소월 | value/effectiveMonth를 필수로 받아 최소월=max(시작월,마지막 CLOSE+1) 전은409 CLOSED_MONTH다. | 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. | si-cost | SPEC-REQUEST.md:L247 — - 일반 값 변경은 value/effectiveMonth 필수다. 최소월은 max(CASH_BASE_YM, 마지막 CLOSE월+1), CLOSE가 없으면 CASH_BASE_YM이다. 기본월은 max(현재월,최소월)이다. 최소월 전은409 CLOSED_MONTH다. |
| REQ-102 | null | functional | ui-primary | 기준 월별 추가·덮어쓰기 | 같은 적용월은 전후 확인 후 UPDATE·없으면 INSERT하며 다른월은 보존하고 키 전체 이력 revision을 비교한다. | 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다.; api_endpoint:PUT /api/operating-standards | si-cost | SPEC-REQUEST.md:L248 — - 같은 적용월 행이 있으면 덮어쓸 월/전후를 확인한 뒤 expectedRevision으로 UPDATE, 없으면 INSERT한다. 다른 적용월 행은 유지한다. 한 요청의 변경 키는 중복 불가이며 미변경 칸을 재저장하지 않는다. 키별 전체 이력 revision을 잠금 후 비교한다. |
| REQ-103 | null | functional | ui-primary | 기준 변경 키 중복 거부 | 한 요청 키 중복을 거부하고 미변경 칸은 재저장하지 않는다. | 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. | si-cost | SPEC-REQUEST.md:L248 — - 같은 적용월 행이 있으면 덮어쓸 월/전후를 확인한 뒤 expectedRevision으로 UPDATE, 없으면 INSERT한다. 다른 적용월 행은 유지한다. 한 요청의 변경 키는 중복 불가이며 미변경 칸을 재저장하지 않는다. 키별 전체 이력 revision을 잠금 후 비교한다. |
| REQ-104 | null | functional | ui-primary | 묶음 기준 표시 | DUN/EXT_PRBL/STEP_PRBL/BILL_MISS/AI_SCORE를 지정 묶음·최대4입력/줄로 표시한다. | AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다. | si-cost | SPEC-REQUEST.md:L249 — - 묶음 줄은 DUN_D1/2/3, EXT_PRBL_H/M/L/NULL, STEP_PRBL_1/2/3/4, BILL_MISS_RT/MIN, AI_SCORE_JOB/SKILL/CUST/GRADE/PERF/COST/AVAIL/LOC다. 최대4입력/줄·AI8개는2줄이며 저장은 각 STD_CD 행이다. 변경 칸만 같은 적용월로 한 요청에 보낸다. |
| REQ-105 | null | functional | ui-primary | DUN 순서 검증 | DUN_D1≥1 및 D1<D2<D3를 BigDecimal 일반 정밀도 안에서 검사한다. | D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. | si-cost | SPEC-REQUEST.md:L256 — &#124;DUN_D1/2/3&#124;D1≥1, D1<D2<D3. 일반 최대4소수 범위 적용&#124; |
| REQ-106 | null | functional | ui-primary | 확률 범위 검증 | EXT_PRBL·STEP_PRBL은 각각0~100 포함만 허용한다. | 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. | si-cost | SPEC-REQUEST.md:L257 — &#124;EXT_PRBL·STEP_PRBL&#124;각0~100 포함&#124; |
| REQ-107 | null | functional | ui-primary | AI 점수 합계 검증 | AI_SCORE8개는 각0~100·합계 정확히100을 BigDecimal로 비교한다. | 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. | si-cost | SPEC-REQUEST.md:L258 — &#124;AI_SCORE8개&#124;각0~100·합계정확히100. BigDecimal 합으로 비교&#124; |
| REQ-108 | null | functional | ui-primary | 철수 통보일 정수 | WTHDR_NOTI_D는1 이상·최대99,999,999,999 정수다. | 1과99,999,999,999 허용; 0 또는 소수는400이다. | si-cost | SPEC-REQUEST.md:L259 — &#124;WTHDR_NOTI_D&#124;1 이상 정수·최대99,999,999,999&#124; |
| REQ-109 | null | functional | ui-primary | 자금 시작 연월 표현 | CASH_BASE_YM 화면은 YYYY-MM·API value는 YYYYMM6자리·DB는 정수로 보존한다. | 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. | si-cost | SPEC-REQUEST.md:L260 — &#124;CASH_BASE_YM&#124;화면 YYYY-MM, API value는 YYYYMM6자리 문자열. 실제 연월·소수 없음. DB는 정수, 읽을 때6자리로 복원&#124; |
| REQ-110 | null | functional | ui-primary | 기준 전체 영향구간 검사 | 묶음 미변경키도 잠그고 해당월·영향구간·이후 예정 경계에서 합/순서/범위를 검사한다. | 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. | si-cost | SPEC-REQUEST.md:L263 — 묶음은 미변경 키까지 같은 트랜잭션에서 잠그고 변경값과 해당 적용월의 다른 유효값을 합쳐 검사한다. 새 이력으로 영향받는 구간과 이후 예정 기준의 경계월에서도 합100/순서/범위를 검사하고 한 경계라도 위반하면400·전체 롤백이다. 단위나 기준명으로 추가 업무 상한을 추정하지 않는다. |
| REQ-111 | null | functional | ui-primary | 자금 두 키 즉시 갱신 | CASH_BASE_YM/AMT는 키당1행·별도요청·effectiveMonth 없이 즉시 UPDATE하고 원래 적용월을 유지한다. | 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. | si-cost | SPEC-REQUEST.md:L267 — - 두 키는 키마다1행을 유지한다. 적용 시작월 입력 없이 별도 저장 요청으로 즉시 UPDATE, 원래 APLY_BGN_YM은 보존하고 UPD 이력을 남긴다. 일반 이력 요청과 섞으면400이다. |

#### 자금 기준 변경 이력부터 실제 DB health 조회까지
<!-- speckit:section sec-e2f21db078334a3eb50afb624aeb3fde -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-112 | null | functional | ui-primary | 자금 기준 변경 이력 | 자금 두 키 변경은 UPD 이력을 기록한다. | 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다. | si-cost | SPEC-REQUEST.md:L267 — - 두 키는 키마다1행을 유지한다. 적용 시작월 입력 없이 별도 저장 요청으로 즉시 UPDATE, 원래 APLY_BGN_YM은 보존하고 UPD 이력을 남긴다. 일반 이력 요청과 섞으면400이다. |
| REQ-113 | null | functional | ui-primary | 기존 시작월 마감 잠금 | 기존 CASH_BASE_YM이 CLOSE이면 두 키 모두 읽기 전용·409 CLOSED_MONTH로 변경을 막는다. | 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. | si-cost | SPEC-REQUEST.md:L268 — - 저장 전 **기존 CASH_BASE_YM**이 TB_CL_CLOSE에서 CLOSE인지 검사한다. 마감됐으면 두 키 모두 읽기 전용·설명·API409 CLOSED_MONTH다. 시작월을 먼저 바꿔 잠금을 우회하지 못한다. |
| REQ-114 | null | functional | ui-primary | 자금 동시 잠금·후속 갱신 | 두행 동시 잠금 후 기존 시작월 재검사하고 저장 후 period-context/기준조회만 갱신한다. | 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. | si-cost | SPEC-REQUEST.md:L269 — - 두 행을 함께 잠그고 기존 시작월로 재검사한다. 미마감일 때만 새 연월/잔액을 저장한다. 저장 후 period-context·기준 조회를 갱신하며 자금수지 계산·마감 자료 변경은0건이다. |
| REQ-115 | null | functional | ui-primary | 표기 대응 목록·검색 | 대응을 sourceText 부분일치/groupId로 필터하고 원문 순서·페이지로 조회한다. | SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다.; api_endpoint:GET /api/code-mappings | si-cost | SPEC-REQUEST.md:L275 — - 표 열은 선택·엑셀 표기·그룹·바꿀 코드(코드/이름), 정렬SRC_TEXT/CODE_GRP_ID/MAP_ID순, 기본50·필터sourceText부분일치/groupId다. 빈 상태는 “등록된 표기가 없습니다.”다. |
| REQ-116 | null | functional | ui-primary | 대응 입력·중복 검사 | trim 1~100자 sourceText·실재 groupId/active code를 검사하고 UK(sourceText,groupId)를 보호한다. | 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. | si-cost | SPEC-REQUEST.md:L276 — - [추가]의 sourceText는trim 후1~100자, groupId/code 필수·실재 그룹과 active 코드만 허용한다. UK(sourceText,groupId) 중복은409 DUPLICATE_MAPPING이고 다른 코드여도 중복이다. 다른 그룹의 같은 표기는 허용한다. 주/보조·메모·사용 여부·적용일 컬럼은 추가하지 않는다. |
| REQ-117 | null | functional | ui-primary | 대응 즉시 등록 | 대응 추가는 즉시 DB에 저장하고 수정 대신 삭제 후 재등록한다. | POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다.; api_endpoint:POST /api/code-mappings | si-cost | SPEC-REQUEST.md:L277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다. |
| REQ-118 | null | functional | ui-primary | 대응 등록·삭제 이력 | 대응 NEW/DEL은 삭제 전후 식별내용을 이력에 남겨 물리삭제 후에도 식별한다. | NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. | si-cost | SPEC-REQUEST.md:L277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다. |
| REQ-119 | null | functional | ui-primary | 대응 다중 물리 삭제 | 선택 확인 후 여러 대응을 한 트랜잭션으로 DELETE한다. | 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다.; api_endpoint:POST /api/code-mappings/delete | si-cost | SPEC-REQUEST.md:L277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다. |
| REQ-120 | null | functional | ui-primary | 대응 삭제 충돌 | 삭제 목록에 누락/변경 행이 하나라도 있으면409로 전체 삭제를 롤백한다. | 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. | si-cost | SPEC-REQUEST.md:L277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다. |
| REQ-121 | null | functional | ui-primary | 대응 변경의 업무 불변 | 대응 변경으로 기존 업무 자료는 수정하지 않는다. | 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다. | si-cost | SPEC-REQUEST.md:L278 — - 대응 변경으로 기존 업무 자료를 고친 건수는0건이다. 이번에는 엑셀 파서·파일 업로드/다운로드를 구현하지 않는다. |
| REQ-122 | null | functional | ui-primary | 변경 이력 조회 | 허용5종 대상만 원문 열·필터·정렬·페이지로 조회하고 긴 값 펼침/처리자 표시를 제공한다. | REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다.; api_endpoint:GET /api/common-code-history | si-cost | SPEC-REQUEST.md:L282 — [변경 이력]은 코드 그룹·코드·값코드·기준값·표기 대응만 조회한다. 필터targetType/groupId·기본전체, 정렬REG_DTTM DESC/HIST_ID DESC, 페이지20/50/100·기본50이다. 열은 일시·대상·그룹/코드/키·유형·항목·이전·이후·처리자이며 긴 값은 펼쳐 읽는다. 신규/값변경/중지/재개/삭제는 NEW/UPD/STOP/RESUME/DEL로 기록한다. codeName 이력의 CHG_ITEM_NM은 CODE_NM으로 고정해 과거 이름을 추적한다. SYSTEM은‘시스템’, admin은‘임시 관리자’로 표시한다. 이력 수정·삭제 API는0개다. |
| REQ-123 | null | functional | api-contract | 변경 유형과 과거 이름 추적 | NEW/UPD/STOP/RESUME/DEL을 기록하고 codeName의 CHG_ITEM_NM은 CODE_NM으로 고정한다. | 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. | si-cost | SPEC-REQUEST.md:L282 — [변경 이력]은 코드 그룹·코드·값코드·기준값·표기 대응만 조회한다. 필터targetType/groupId·기본전체, 정렬REG_DTTM DESC/HIST_ID DESC, 페이지20/50/100·기본50이다. 열은 일시·대상·그룹/코드/키·유형·항목·이전·이후·처리자이며 긴 값은 펼쳐 읽는다. 신규/값변경/중지/재개/삭제는 NEW/UPD/STOP/RESUME/DEL로 기록한다. codeName 이력의 CHG_ITEM_NM은 CODE_NM으로 고정해 과거 이름을 추적한다. SYSTEM은‘시스템’, admin은‘임시 관리자’로 표시한다. 이력 수정·삭제 API는0개다. |
| REQ-124 | null | functional | ui-primary | 이력 필터 도메인 | targetType은 명시5값·생략전체이며 groupId는 해당 그룹/코드/대응 이력만 반환한다. | 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. | si-cost | SPEC-REQUEST.md:L284 — targetType은 GROUP/CODE/VALUE_CODE/OPER_STD/CODE_MAP이며 생략하면 전체, 잘못된 값은400이다. CODE/VALUE_CODE는 그룹의 VAL_CODE_YN으로 구분한다. groupId가 있으면 그 그룹의 그룹/코드/대응 이력만 조회하고 운영 기준값은 제외한다. |
| REQ-125 | null | functional | api-contract | 이력 키·전후값 무손실 | 실제 테이블명과 대상별 TRGT_KEY·컬럼별 전후값·NEW/DEL JSON을500자 이내로 저장하고 잘라내지 않는다. | 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. | si-cost | SPEC-REQUEST.md:L286 — TRGT_TBL_NM은 실제 테이블명, TRGT_KEY는 그룹=`groupId`, 코드=`groupId:code`, 대응=`groupId:mapId`, 기준=`key:YYYYMM`이다. UPD는 변경 컬럼별 전후 값, STOP/RESUME는 USE_YN, 코드 DEL은 DEL_YN의 N→Y다. NEW는 항목‘등록’·이전NULL·이후 식별자/이름/값을 남긴다. 대응 NEW/DEL은 sourceText/groupId/code/codeName의 JSON을 이후/이전 값에 각각 남기고 반대쪽은NULL이다. 삭제 후에도 groupId 필터가 동작하도록 키의 정확한 접두 구분자를 사용한다. 이력 값은500자 이내며 조용히 잘라 저장하지 않는다. |
| REQ-126 | null | functional | api-contract | 공통 active 코드 조회 | CodeQueryService/useCodes는 active 미삭제 코드를 정렬하여 원문 공개필드를 반환하고 includeInactive label을 구분한다. | GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다.; api_endpoint:GET /api/codes/{group} | si-cost | SPEC-REQUEST.md:L290 — - CodeQueryService/useCodes(groupId)는 USE_YN=Y·DEL_YN=N 코드를 SORT_ORD/CODE순으로 반환한다. code/name/addVal1~3/codeVal/unitNm을 제공한다. 저장된 중지 코드 label 조회는 includeInactive로 구분하고 신규 선택에 끼워 넣지 않는다. |
| REQ-127 | null | functional | api-contract | 월별 단일 기준 조회 | OperatingStandardService.get은 요청월 이하 최신 값/단위/적용월을 DB에서 반환한다. | GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다.; api_endpoint:GET /api/params/{key} | si-cost | SPEC-REQUEST.md:L291 — - OperatingStandardService.get(key,month)는 요청월 이하 최신 적용월의 정확한 값·단위·적용월을 반환한다. 키/해당월 값 없음은404 STANDARD_NOT_FOUND,0이나 하드코딩 값으로 대체하지 않는다. 조회는 DB 값을 읽는 기능이며 계산 엔진을 실행하지 않는다. |
| REQ-128 | null | functional | api-contract | 기준 미존재 거부 | 키 또는 해당월 값이 없으면404 STANDARD_NOT_FOUND이며0/하드코딩 값으로 대체하지 않는다. | 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. | si-cost | SPEC-REQUEST.md:L291 — - OperatingStandardService.get(key,month)는 요청월 이하 최신 적용월의 정확한 값·단위·적용월을 반환한다. 키/해당월 값 없음은404 STANDARD_NOT_FOUND,0이나 하드코딩 값으로 대체하지 않는다. 조회는 DB 값을 읽는 기능이며 계산 엔진을 실행하지 않는다. |
| REQ-129 | null | functional | api-contract | API 필드와 서버 필드 보호 | camelCase·십진문자열·목록 봉투·expectedRevision을 따르고 허용외/주입 필드는400·모드 금지 필드는409다. | SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. | si-cost | SPEC-REQUEST.md:L295 — 공통 접두사는 `/api`, JSON camelCase다. 목록은 `{items,total,page,size}`·page1부터, 전체 건수는현재 페이지 크기와 구분한다. codeVal/value는 십진 문자열, 기존 값 변경은 expectedRevision 필수다. 허용되지 않은 필드400, 모드상 금지 필드409다. 처리자·SYS/VAL/DEL·등록시각 주입을 거부한다. |
| REQ-130 | null | functional | api-contract | 공통 오류 반환과 비밀 보호 | 원문 상태별 오류코드·한국어 사유·필드·참조건수를 반환하고 SQL/스택/비밀값을 노출하지 않는다. | 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. | si-cost | SPEC-REQUEST.md:L324 — 오류는400 VALIDATION_ERROR/INVALID_PERIOD/UNKNOWN_FIELD,404 NOT_FOUND/STANDARD_NOT_FOUND,409 STALE_WRITE/DUPLICATE_GROUP/DUPLICATE_GROUP_NAME/DUPLICATE_CODE/DUPLICATE_MAPPING/SYSTEM_CODE_LOCKED/VALUE_CODE_FIELD_LOCKED/CODE_IN_USE/CLOSED_MONTH/ORDER_LIMIT,500 INTERNAL_ERROR,503 DB_UNAVAILABLE다. 한국어 사유·필드·참조건수를 제공하고 SQL/스택/비밀값은 노출하지 않는다. |
| REQ-156 | null | functional | architecture-runtime | 실제 DB health 조회 | /api/health가 실제 DB 연결 SELECT를 검사하고 정상200·실패503을 반환한다. | 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다.; api_endpoint:GET /api/health | si-cost | SPEC-REQUEST.md:L470 — /api/health는 실제 DB 연결을 검사하고 정상 200, 연결 실패 503을 반환한다. |

### 비기능 요구사항
<!-- speckit:section sec-7d36ec986d494237874a1845c2ae6845 -->

#### 원문 구현 기술 요구부터 프론트·API 함께 검증까지
<!-- speckit:section sec-bf30138bb98c446e8614f1d411f40d4a -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-132 | null | non-functional | non-functional | 원문 구현 기술 요구 | React·TypeScript·Vite·React Router·Java21·Spring Boot3.5.x·Gradle·Spring MVC/JPA/Flyway·PostgreSQL16 요구를 추적한다. | 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. | si-cost | SPEC-REQUEST.md:L30 — - React·TypeScript·Vite·React Router, Java21·Spring Boot3.5.x·Gradle·Spring MVC/JPA/Flyway, PostgreSQL16을 사용한다. 기본 경로는 `frontend/`, `backend/`, `infra/docker-compose.yml`이다. |
| REQ-133 | null | non-functional | non-functional | 3서비스 실행과 SPA | 프론트/nginx·백엔드·DB 3서비스 compose·/api 프록시·SPA fallback·환경설정·.env.example을 제공한다. | /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. | si-cost | SPEC-REQUEST.md:L31 — - 프론트/nginx·백엔드·DB의3서비스를 compose로 실행한다. nginx는 `/api`를 프록시하고 화면 직접 접근/새로고침을 SPA fallback으로 처리한다. DB 접속값은 환경설정, `.env.example`은 자리표시자와 실행 안내로 제공한다. |
| REQ-134 | null | non-functional | non-functional | 서울 시간대 일관성 | 서버·DB 세션·현재일·연월·화면 일시는 Asia/Seoul로 통일한다. | 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. | si-cost | SPEC-REQUEST.md:L32 — - 서버·DB 세션 시간대는 Asia/Seoul로 통일한다. 현재일·연월·등록/수정시각과 화면 일시를 이 기준으로 처리한다. |
| REQ-135 | null | non-functional | non-functional | Flyway 전용 스키마·정밀도 | JPA ddl-auto=validate·Flyway만 생성·따옴표 없는 업무 SQL 식별자·BigDecimal을 사용한다. | JPA validate 통과·Hibernate DDL 생성0회·float/double 금액변환0회다. | si-cost | SPEC-REQUEST.md:L33 — - JPA `ddl-auto=validate`, 테이블 생성은 Flyway만 수행한다. 업무 SQL 식별자는 따옴표 없이 사용한다. 이번 숫자 입력·검증은 Java BigDecimal을 사용하며 float/double 변환을 하지 않는다. 실제 급여·원가 계산은 만들지 않는다. |
| REQ-136 | null | non-functional | non-functional | 마이그레이션 파일 경로 | V1__full_schema.sql과 V2__base_data.sql을 지정 Flyway 경로와 순서로 제공한다. | backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. | si-cost | SPEC-REQUEST.md:L39 — &#124;1&#124;`backend/src/main/resources/db/migration/V1__full_schema.sql`&#124;project_ddl 전체·마지막 HQ 예약 파트 포함&#124;<br>&#124;2&#124;같은 경로 `V2__base_data.sql`&#124;project_data 전체&#124; |
| REQ-137 | null | non-functional | non-functional | 프론트 구조와 계층 책임 | 지정 src 경로·hook/client 호출·서버 검증/조회/저장 책임을 지키고 iframe/DOM 문자열 복제를 금지한다. | UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. | si-cost | SPEC-REQUEST.md:L80 — 프론트 기본 경로는 `src/components/{layout,common}/`, `src/screens/common-codes/`, `src/hooks/`, `src/lib/format.ts`다. API 호출은 hook/client, 검증·조회·저장은 서버 서비스가 담당한다. HTML 전체 iframe·DOM 문자열 복제로 구현하지 않는다. |
| REQ-138 | null | non-functional | non-functional | 고정 technology_stack | 신규생성 실행 계약의 Frontend/Backend/Database/Infrastructure 값을 보존한다. | React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. | si-cost | SPEC-REQUEST.md:L371 — - Frontend: React + TypeScript + Vite<br>- Backend: Java 17 + Spring Boot<br>- Database: PostgreSQL<br>- Infrastructure: {'runtime': 'docker', 'healthcheck_required': True} |
| REQ-139 | null | non-functional | non-functional | 고정 Version BOM | spring_boot3.3.x·node20.x·vite5.x·react18.x·postgres16.x를 원문 그대로 보존한다. | BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. | si-cost | SPEC-REQUEST.md:L398 — - spring_boot: 3.3.x<br>- node: 20.x<br>- vite: 5.x<br>- react: 18.x<br>- postgres: 16.x |
| REQ-140 | null | non-functional | non-functional | 고정 required_outputs | backend_dir·frontend_dir·compose_file·health_endpoint 경로를 고정 계약대로 제공한다. | backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. | si-cost | SPEC-REQUEST.md:L405 — - backend_dir: backend<br>- frontend_dir: frontend<br>- compose_file: infra/docker-compose.yml<br>- health_endpoint: /api/health |
| REQ-141 | null | non-functional | non-functional | 레퍼런스 시각 계약 | 공통 레이아웃/UI-27의 구조·색·간격·글꼴·아이콘·표·폼·모달 방향을 제품에 적용한다. | sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. | si-cost | SPEC-REQUEST.md:L412 — - 적용 방식: 레퍼런스는 복제 대상이 아니라 UI/UX 구현 계약이다. sidebar, header, dashboard card, table/form density, spacing, color token, responsive behavior를 대상 도메인에 맞게 적용해야 한다. |
| REQ-142 | null | non-functional | non-functional | Docker 빌드 컨텍스트 제외 | 각 compose build context에 .dockerignore를 두고 지정 개발 산출물을 제외한다. | 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. | si-cost | SPEC-REQUEST.md:L426 — - compose 의 각 build context 디렉터리(예: context: .. 는 저장소 루트)에 .dockerignore 를 두고 node_modules, .git, dist, build, .next, coverage 를 제외한다. |
| REQ-143 | null | non-functional | non-functional | 격리 환경만 배포 | 임시 관리자 단계는 로컬/접근 제한 사설 개발·검증 환경에 한정하고 공개 배포하지 않는다. | 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. | si-cost | SPEC-REQUEST.md:L466 —   **A**: 이번 단계는 로컬 또는 접근 대상이 제한된 사설 개발·검증 환경에서만 실행한다. 임시 관리자 컨텍스트로 모든 기능을 사용할 수 있으므로 인터넷에 누구나 접근할 수 있는 형태로 배포하지 않는다. 공개 배포는 로그인·실제 권한 적용과 검증을 완료한 이후에 진행한다. |
| REQ-144 | null | non-functional | non-functional | 실행·영속성 결정 보존 | 후속 plan.md가 exact marker+JSON의 semantic SSOT이며 구현은 imperative·jpa·blocking 결정을 따른다. | resolved/imperative/jpa/blocking/explicit 및 blocking_priority/conflicts가 변경 없이 후속 plan.md로 복사된다. | si-cost | SPEC-REQUEST.md:L419 — - plan.md는 실행·영속성 계약의 exact marker+JSON block을 보존하고, 생성된 9개 문서의 semantic SSOT(의미상 정본)로 확정한다. |
| REQ-145 | null | non-functional | non-functional | 실제 백엔드 산출물 | 신규생성 구현은 backend 변경과 실제 DB/migration을 포함해야 한다. | backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. | si-cost | SPEC-REQUEST.md:L422 — - backend/ 변경이 없으면 신규생성 구현은 불완전한 것으로 간주한다. |
| REQ-146 | null | non-functional | non-functional | 프론트·API 함께 검증 | preview/quickstart에서 backend API와 frontend 화면을 함께 검증한다. | 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. | si-cost | SPEC-REQUEST.md:L425 — - preview 또는 quickstart 검증에서 backend API와 frontend 화면을 함께 확인한다. |

#### REQ-137 — 프론트 구조와 계층 책임

입력 항목:

- 구현 구조의 기준 경로는 `src/components/{layout,common}/`, `src/screens/common-codes/`, `src/hooks/`, `src/lib/format.ts`다. API 요청은 hook/client를 통하며 검증·조회·저장 대상은 서버 서비스로 전달한다. 이는 사용자 입력 폼의 추가가 아닌 코드 계층의 입력·의존 경계다. 근거: 원장 REQ-137의 SPEC-REQUEST.md:L80 인용.

#### REQ-138 — 고정 technology_stack

입력 항목:

- 신규생성 실행 계약의 Frontend=`React + TypeScript + Vite`, Backend=`Java 17 + Spring Boot`, Database=`PostgreSQL`, Infrastructure의 runtime=`docker`, healthcheck_required=true를 입력 계약으로 보존한다. 근거: 원장 REQ-138의 SPEC-REQUEST.md:L371 인용.

검증 기준:

- AC-REQ-138-1: 기술 계약을 대조하면 위 Frontend/Backend/Database/Infrastructure 값이 그대로 남고 healthcheck_required가 true다. 원문 L30의 Java21과 충돌한다는 이유로 Backend 값을 바꾸지 않는다.
- OQ-001 / 확인 필요: Java 17과 Java21 중 최종 구현 버전은 명시 승인 전 확정하지 않는다. 고정 계약 보존과 구현 버전 승인 완료를 구분한다.

#### REQ-139 — 고정 Version BOM

입력 항목:

- 버전 계약은 spring_boot=`3.3.x`, node=`20.x`, vite=`5.x`, react=`18.x`, postgres=`16.x`다. 근거: 원장 REQ-139의 SPEC-REQUEST.md:L398 인용. 실제 patch 버전은 이 입력에 선언되어 있지 않다.

검증 기준:

- AC-REQ-139-1: Version BOM의 다섯 키와 값을 대조하면 spring_boot=3.3.x, node=20.x, vite=5.x, react=18.x, postgres=16.x가 각각 일치한다. spring_boot를 원문 L30의 3.5.x로 조용히 대체하지 않는다.
- OQ-001 / 확인 필요: Spring Boot3.3.x와 Spring Boot3.5.x의 최종 선택은 승인 전 미정이다. 계열 내 정확한 patch 버전도 확인 필요이며 추정값을 승인값으로 기록하지 않는다.

#### REQ-140 — 고정 required_outputs

입력 항목:

- 산출물 위치 계약은 backend_dir=`backend`, frontend_dir=`frontend`, compose_file=`infra/docker-compose.yml`, health_endpoint=`/api/health`다. 사용자에게 경로를 입력받는 화면을 뜻하지 않는다. 근거: 원장 REQ-140의 SPEC-REQUEST.md:L405 인용.

#### REQ-144 — 실행·영속성 결정 보존

입력 항목:

- 후속 plan.md로 전달할 입력은 이 문서의 `aiops:execution-persistence-contract:start`와 `aiops:execution-persistence-contract:end` exact marker 및 그 사이 JSON 전체다. decision_status=`resolved`, execution_model=`imperative`, persistence_technology=`jpa`, access_mode=`blocking`, selection_source=`explicit`, blocking_priority=[`mybatis`,`jpa`,`spring-jdbc`], conflicts=[]를 보존한다. 근거: 원장 REQ-144의 SPEC-REQUEST.md:L419 인용과 기존 「실행·영속성 결정의 보존」 블록.

검증 기준:

- AC-REQ-144-1: 후속 plan.md 작성 시 양쪽 marker와 JSON 전체를 대조하면 위 키·값 및 blocking_priority의 순서가 모두 일치한다. 우선순위 첫 값이 mybatis라는 이유로 명시 선택된 jpa를 바꾸지 않는다.
- AC-REQ-144-2: 후속 문서 사이 실행·영속성 결정이 다르면 plan.md의 해당 계약을 의미상 정본으로 대조하여 충돌을 드러낸다. 이 검증은 이번 호출에서 plan.md를 생성하거나 검사했다는 뜻이 아니다. 실행·영속성의 resolved는 OQ-001의 Java/Boot 버전 승인 완료를 뜻하지 않는다.

### 데이터 요구사항
<!-- speckit:section sec-96abbdc5094744cdb11324e3f850ffd3 -->

#### 전체 SQL 원천 보존부터 변경 이력 무기한 보존까지
<!-- speckit:section sec-13ee98a2c3004c4bae1a7bd11a26d0e6 -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-001 | null | data | data-contract | 전체 SQL 원천 보존 | example/project_ddl.sql과 example/project_data.sql 전체를 DB와 초기값의 원천으로 사용한다. | HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. | si-cost | SPEC-REQUEST.md:L17 — - 작업 저장소: `https://github.com/MinHeeJ/si_cost`. 저장소 루트 `example/project_ddl.sql` 전체가 생성할 DB 정의, `example/project_data.sql` 전체가 기본 초기값이다. example 경로여도 전체를 사용한다. |
| REQ-002 | null | data | data-contract | 행정구역 빈 테이블 | 행정구역은 DDL 테이블과 제약만 생성하고 데이터는 0행으로 유지한다. | 설치 후 행정구역 테이블 데이터 건수 0행이다. | si-cost | SPEC-REQUEST.md:L22 — - 행정구역은 DDL의 테이블·제약만 생성하고 데이터는0행으로 둔다. 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건이 아니다. |
| REQ-003 | null | data | data-contract | 전체 DB 구조 보존 | 빈 개발 DB에 원문 수량과 모든 컬럼·제약·인덱스·시퀀스·코멘트를 보존한다. | 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. | si-cost | SPEC-REQUEST.md:L42 — - 빈 개발 DB에서 **68테이블·836컬럼·73 FK·78 명시 인덱스·13 시퀀스·46 CHECK·5 UK**를 생성한다. TB_SY_CALC_RULE 11컬럼도 포함하며 컬럼·타입·NULL·DEFAULT·PK·UK·CHECK·FK·인덱스·시퀀스·코멘트를 보존한다. |
| REQ-004 | null | data | data-contract | 기본 초기값 적재 | 원문 초기값 수량과 HQ 적재 주체를 보존하고 대응·이력·설정은 빈 상태로 시작한다. | 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. | si-cost | SPEC-REQUEST.md:L43 — - 초기값은 그룹64(시스템44), 코드391, 운영기준43, 역할5, 메뉴40(10묶음+30화면), 역할메뉴150, 관리자/역할 연결 각1행, 계산규칙94행이다. HQ1행은 DDL에서만 적재한다. TB_SY_CODE_MAP·변경 이력·사용자 설정은 빈 상태로 시작한다. |
| REQ-005 | null | data | data-contract | 초기 자금 기준과 계산규칙 | 계산규칙과 초기 CASH_BASE_YM/CASH_BASE_AMT를 그대로 적재한다. | CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. | si-cost | SPEC-REQUEST.md:L44 — - 계산규칙 테이블·데이터는 그대로 적재하고 편집 화면·계산 실행 엔진은 생성하지 않는다. 초기 CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 월 마감 기록을 임의로 만들지 않는다. |
| REQ-006 | null | data | data-contract | Flyway 실행본 래퍼 정리 | data의 최상위 BEGIN 2개와 COMMIT 1개만 실행용 사본에서 제거하고 TEXT 내부 문자열을 보존한다. | 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. | si-cost | SPEC-REQUEST.md:L45 — - data의 최상위 BEGIN2개/COMMIT1개는 Flyway 트랜잭션 래퍼와 중복되지 않게 실행용 사본에서만 제거한다. 계산규칙 TEXT 안의 문자열은 치환하지 않는다. 원본/실행본 차이·사용한 revision/해시를 기록한다. |
| REQ-007 | null | data | data-contract | 마이그레이션 불변·멱등성 | 적용된 마이그레이션을 수정하거나 재실행하지 않고 재시작 중복 적재를 방지한다. | 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. | si-cost | SPEC-REQUEST.md:L46 — - 적용된 마이그레이션은 수정·재실행하지 않는다. 재시작 시 중복 적재0건이어야 한다. 미적용 파일은 원본과 대조해 완성한다. 설치 실패는 해당 트랜잭션을 롤백하고 지정 개발 DB에서 수정·검증하며 임의 DROP/TRUNCATE·자동 baseline으로 구조 불일치를 숨기지 않는다. |
| REQ-008 | null | data | data-contract | 설치 실패와 구조 은폐 금지 | 설치 실패는 트랜잭션 롤백하며 임의 DROP/TRUNCATE·자동 baseline으로 불일치를 숨기지 않는다. | 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. | si-cost | SPEC-REQUEST.md:L46 — - 적용된 마이그레이션은 수정·재실행하지 않는다. 재시작 시 중복 적재0건이어야 한다. 미적용 파일은 원본과 대조해 완성한다. 설치 실패는 해당 트랜잭션을 롤백하고 지정 개발 DB에서 수정·검증하며 임의 DROP/TRUNCATE·자동 baseline으로 구조 불일치를 숨기지 않는다. |
| REQ-009 | null | data | data-contract | 구조 차이 보고 | 이미 존재하는 DB 구조가 다르면 추정 변경 없이 차이와 실패 항목을 보고한다. | 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. | si-cost | SPEC-REQUEST.md:L47 — - 초기 구조와 이미 존재하는 구조가 다르면 이름·타입을 추정해 바꾸지 않고 차이와 실패 항목을 보고한다. 초기 관리자 비밀번호·메일 주소를 프론트에 노출하지 않는다. |
| REQ-010 | null | data | data-contract | 설치 후 쓰기 경계 | 업무 쓰기는 명시한 6테이블에 한정하고 나머지 업무 테이블은 조회용이다. | 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. | si-cost | SPEC-REQUEST.md:L51 — 설치 이후 업무 쓰기는 아래6테이블만 사용한다. 사용자·역할·메뉴·마감·프로젝트 등은 조회용이다. 수당/기준값 변경으로 프로젝트 팀비·경비나 정산금액을 변경한 건수는0건이다. |
| REQ-011 | null | data | data-contract | 서버 감사 필드와 연월 저장 | 등록·수정 필드는 서버가 기록하며 이력은 등록 필드만 쓰고 DB 연월은 YYYYMM으로 저장한다. | REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. | si-cost | SPEC-REQUEST.md:L62 — REG_USER_ID는 VARCHAR(30) NOT NULL, REG_DTTM은 기본 CURRENT_TIMESTAMP, MOD_USER_ID/MOD_DTTM은 nullable이며 서버가 기록한다. 이력 테이블에는 DDL의 등록 필드만 쓴다. 화면/API 연월은 YYYY-MM, DB는 YYYYMM이다. |
| REQ-131 | null | data | data-contract | 변경 이력 무기한 보존 | 변경 이력은 보존 기한 없이 유지하고 기간 경과로 삭제하지 않는다. | 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. | si-cost | SPEC-REQUEST.md:L476 —   **A**: 보존 기한 없이 유지하고, 기간에 따른 삭제는 하지 않는다 |

#### REQ-001 — 전체 SQL 원천 보존

검증 기준:

- AC-REQ-001-1: 원장에 지정된 `example/project_ddl.sql` 전체와 생성용 실행본을 대조하면 마지막 HQ 예약 파트를 포함하여 DB 정의가 누락되지 않는다. `example/project_data.sql` 전체와 초기값 실행본도 대조하여 일부 표본만 사용하지 않았음을 확인한다. 근거: SPEC-REQUEST.md:L17의 원장 인용과 acceptance_hint.
- AC-REQ-001-2: 실행본 차이는 REQ-006이 허용한 최상위 트랜잭션 래퍼 정리와 구분하여 검토한다. 전체 원천 보존 여부를 일부 API 성공만으로 대체하지 않는다. 실제 원본 revision/해시는 OQ-002 / 확인 필요이며 이번 문서 보완에서 확보·검증한 것으로 기록하지 않는다.

#### REQ-003 — 전체 DB 구조 보존

입력 항목:

- 빈 개발 DB와 `example/project_ddl.sql` 전체의 구조 정의를 입력으로 한다. 대조 대상은 컬럼·타입·NULL·DEFAULT·PK·UK·CHECK·FK·인덱스·시퀀스·코멘트이며 `TB_SY_CALC_RULE`의 11컬럼을 포함한다. 근거: 원장 REQ-003의 SPEC-REQUEST.md:L42 인용.

검증 기준:

- AC-REQ-003-1: 빈 개발 DB에 마이그레이션 적용 후 구조를 조회하면 68테이블·836컬럼·73 FK·78 명시 인덱스·13 시퀀스·46 CHECK·5 UK가 일치하고 `TB_SY_CALC_RULE`은 11컬럼이다.
- AC-REQ-003-2: 수량 대조와 별개로 각 컬럼·타입·NULL·DEFAULT·PK·UK·CHECK·FK·인덱스·시퀀스·코멘트가 원본과 일치해야 한다. 수량만 같고 정의가 다른 구조를 합격으로 처리하지 않는다. 실제 전체 객체명은 OQ-002 / 확인 필요이며 추정하지 않는다.

#### REQ-004 — 기본 초기값 적재

입력 항목:

- `example/project_data.sql`의 기본 초기값 전체와 DDL에서만 적재하는 HQ 1행을 입력으로 한다. 그룹·코드·운영기준·역할·메뉴·역할메뉴·관리자/역할 연결·계산규칙을 대조하고 `TB_SY_CODE_MAP`·변경 이력·사용자 설정은 빈 초기 상태를 기준으로 한다. 근거: 원장 REQ-004의 SPEC-REQUEST.md:L43 인용.

검증 기준:

- AC-REQ-004-1: 초기 설치 직후 DB 조회 결과는 그룹64(시스템44)·코드391·운영기준43·역할5·메뉴40(10묶음+30화면)·역할메뉴150·관리자/역할 연결 각1행·계산규칙94행이다.
- AC-REQ-004-2: HQ는 DDL에서만 1행 적재되어 중복이 없고 `TB_SY_CODE_MAP`·변경 이력·사용자 설정은 각각 0행이다. 업무 조작으로 이력이 생기기 전 초기 상태에서 확인한다.

#### REQ-005 — 초기 자금 기준과 계산규칙

입력 항목:

- 원문 SQL의 계산규칙 테이블·데이터 및 초기 `CASH_BASE_YM=202601`, `CASH_BASE_AMT=1150000000`을 사용한다. 임의 월 마감 기록은 초기값 입력으로 만들지 않는다. 근거: 원장 REQ-005의 SPEC-REQUEST.md:L44 인용.

검증 기준:

- AC-REQ-005-1: 초기 적재 후 DB에서 계산규칙을 원문 데이터와 대조하면 그대로 보존되고 `CASH_BASE_YM=202601`, `CASH_BASE_AMT=1150000000`이 조회된다.
- AC-REQ-005-2: 초기 설치가 임의로 생성한 월 마감 기록은 0행이다. 계산규칙을 적재했다는 사실을 계산 실행 엔진이나 편집 화면을 제공한다는 뜻으로 해석하지 않는다. 해당 제외 범위는 REQ-152가 소유한다.
<!-- decision-answer:4e0f16bb16dc8ada:start -->
- 확정 처리 규칙: 계산규칙과 초기 현금 기준연월·기준금액은 원문과 불일치하는 항목이 0개인 것을 합격 기준으로 제안합니다. 초기 현금 기준연월과 기준금액은 각각 202601, 1150000000원입니다.
<!-- decision-answer:4e0f16bb16dc8ada:end -->

#### REQ-006 — Flyway 실행본 래퍼 정리

입력 항목:

- `example/project_data.sql` 원본과 Flyway 실행용 사본, 최상위 `BEGIN` 2개/`COMMIT` 1개, 계산규칙 TEXT 내부 문자열을 구분하여 대조한다. 사용한 revision/해시와 원본·실행본 차이를 기록 대상으로 한다. 근거: 원장 REQ-006의 SPEC-REQUEST.md:L45 인용.

검증 기준:

- AC-REQ-006-1: 원본과 실행본 diff는 최상위 `BEGIN` 2개와 `COMMIT` 1개의 제거만 보여 주고 원본 파일은 보존된다. 계산규칙 TEXT 내부의 같은 문자열은 치환되지 않는다.
- AC-REQ-006-2: 실제 사용한 revision/해시와 원본·실행본 차이가 기록되어 대조 가능해야 한다. OQ-002 / 확인 필요: 현재 미확보인 revision/해시의 실제 값과 원문이 정하지 않은 해시 알고리즘은 만들어 적지 않는다.

#### REQ-007 — 마이그레이션 불변·멱등성

입력 항목:

- 적용된 마이그레이션과 미적용 파일, 미적용 파일을 대조할 원본 SQL 및 초기 적재가 완료된 개발 DB의 재시작을 입력 조건으로 한다. 적용 여부를 구분하여 이미 적용된 파일을 수정·재실행하는 입력으로 취급하지 않는다. 근거: 원장 REQ-007의 SPEC-REQUEST.md:L46 인용.

#### REQ-008 — 설치 실패와 구조 은폐 금지

입력 항목:

- 지정 개발 DB에서 실행 중 실패한 설치 트랜잭션과 구조 불일치가 검사 대상이다. 실패한 실행과 수정·검증할 개발 DB를 구분하며 사용자 보유 DB를 초기화 대상으로 가정하지 않는다. 근거: 원장 REQ-008의 SPEC-REQUEST.md:L46 인용. 구체적인 실패 유발 데이터는 OQ-004 / 확인 필요이며 새로운 실패 정책을 확정하지 않는다.

#### REQ-010 — 설치 후 쓰기 경계

입력 항목:

- 설치 이후 코드·대응·운영 기준값·사용자 설정·변경 이력의 업무 변경 요청 및 변경 전 DB 상태가 경계 검사 대상이다. 쓰기 대상은 기존 「핵심 데이터 계약」과 「2.3 이번 쓰기 대상과 핵심 컬럼」의 `TB_SY_CODE_GRP`, `TB_SY_CODE`, `TB_SY_CODE_MAP`, `TB_SY_OPER_STD`, `TB_SY_USER_PREF`, `TB_SY_CHG_HIST`다.
- 사용자·역할·메뉴·마감·프로젝트 등은 조회 입력이며 수당/기준값 변경을 프로젝트 팀비·경비·정산금액의 변경 요청으로 확장하지 않는다. 근거: 원장 REQ-010의 SPEC-REQUEST.md:L51 인용 및 기존 쓰기 대상 표.

#### REQ-011 — 서버 감사 필드와 연월 저장

검증 기준:

- AC-REQ-011-1: 실제 등록 후 DB 조회에서 서버가 기록한 `REG_USER_ID=admin`과 `REG_DTTM`을 확인한다. `REG_USER_ID`는 VARCHAR(30) NOT NULL, `REG_DTTM`은 기본 CURRENT_TIMESTAMP이며 `MOD_USER_ID`/`MOD_DTTM`의 nullable 정의를 보존한다. 실제 수정 시 수정 필드는 서버가 기록한다. 요청 처리자 주입의 거부는 REQ-026과 구분하여 검증한다.
- AC-REQ-011-2: 변경 이력 생성 후 이력 테이블을 조회하면 DDL의 등록 필드만 사용하고 이력에 수정 필드를 새로 추가하지 않는다. 화면/API의 일반 연월 `2026-01`이 DB에서 `202601`로 저장되는지 확인한다. `CASH_BASE_YM`의 API value는 예외적으로 YYYYMM6자리 문자열을 쓰는 REQ-109의 별도 계약을 따른다. 근거: 원장 REQ-011의 SPEC-REQUEST.md:L62 인용 및 REQ-109의 L260 인용.

#### REQ-131 — 변경 이력 무기한 보존

검증 기준:

- AC-REQ-131-1: 과거 변경 이력이 있는 상태에서 이력을 조회하면 기간 경과만으로 사라지지 않고 계속 조회된다. 보존 기한을 임의 숫자로 설정하여 만료된 것으로 처리하지 않는다. 근거: 원장 REQ-131의 SPEC-REQUEST.md:L476 인용.
- AC-REQ-131-2: 기간 기준 이력 삭제 작업이 없고 이력 삭제 API도 제공하지 않는지 확인한다. 삭제 API 제외는 REQ-155의 L282 근거, 기간 경과에 따른 삭제 금지는 REQ-131의 L476 근거로 각각 구분한다.

### 범위 제외 요구사항
<!-- speckit:section sec-555e1eba726b44eca7a9315f676f8d99 -->

#### 후속 업무 화면 경계부터 이력 변경·삭제 제외까지
<!-- speckit:section sec-11f9d900714a4507926ccbf9d9ed0525 -->

| canonical_id | source_id | type | disposition | title | statement | acceptance_hint | related_domain | source_locator.quote |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ-147 | null | out-of-scope | out-of-scope | 후속 업무 화면 경계 | UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. | 다른 업무 화면의 가짜 수치·무동작 저장버튼0개, 계산/저장/업로드0개다. | si-cost | SPEC-REQUEST.md:L21 — - 다른 업무 화면은 제목·해당 기간 줄·“이 화면은 다음 단계에서 만듭니다.”만 표시한다. 해당 화면의 계산·저장·업로드, 월 마감/해제/스냅샷 생성, 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 제외한다. UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 가짜 업무 수치·무동작 저장 버튼은0개다. |
| REQ-148 | null | out-of-scope | out-of-scope | 월 마감 쓰기 제외 | 월 마감/해제/스냅샷 생성은 구현하지 않는다. | 마감 상태 읽기는 존재하되 마감 변경 API/생성 동작0개다. | si-cost | SPEC-REQUEST.md:L21 — - 다른 업무 화면은 제목·해당 기간 줄·“이 화면은 다음 단계에서 만듭니다.”만 표시한다. 해당 화면의 계산·저장·업로드, 월 마감/해제/스냅샷 생성, 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 제외한다. UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 가짜 업무 수치·무동작 저장 버튼은0개다. |
| REQ-149 | null | out-of-scope | out-of-scope | 외부·출력·외형 확장 제외 | 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. | 헤더 준비 중 안내 외 AI/알림 업무0개·엑셀 파서/업로드/다운로드0개다. | si-cost | SPEC-REQUEST.md:L21 — - 다른 업무 화면은 제목·해당 기간 줄·“이 화면은 다음 단계에서 만듭니다.”만 표시한다. 해당 화면의 계산·저장·업로드, 월 마감/해제/스냅샷 생성, 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 제외한다. UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 가짜 업무 수치·무동작 저장 버튼은0개다. |
| REQ-150 | null | out-of-scope | out-of-scope | UI-20·단가·근태 제외 | UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. | 메뉴/UI등록표/API에 UI-20이 없고 일/시간 단가 및 근태 업로드 동작0개다. | si-cost | SPEC-REQUEST.md:L21 — - 다른 업무 화면은 제목·해당 기간 줄·“이 화면은 다음 단계에서 만듭니다.”만 표시한다. 해당 화면의 계산·저장·업로드, 월 마감/해제/스냅샷 생성, 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 제외한다. UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 가짜 업무 수치·무동작 저장 버튼은0개다. |
| REQ-151 | null | out-of-scope | out-of-scope | 행정구역 파일 작업 제외 | 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. | 주소입력 기능/행정구역 적재작업 없이 DDL 제약과0행 상태로 검증한다. | si-cost | SPEC-REQUEST.md:L22 — - 행정구역은 DDL의 테이블·제약만 생성하고 데이터는0행으로 둔다. 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건이 아니다. |
| REQ-152 | null | out-of-scope | out-of-scope | 계산 엔진과 규칙 편집 제외 | 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. | 94행 규칙 데이터가 있어도 규칙 편집 UI/계산 실행 API0개다. | si-cost | SPEC-REQUEST.md:L44 — - 계산규칙 테이블·데이터는 그대로 적재하고 편집 화면·계산 실행 엔진은 생성하지 않는다. 초기 CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 월 마감 기록을 임의로 만들지 않는다. |
| REQ-153 | null | out-of-scope | out-of-scope | 그룹 변경·삭제 제외 | 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. | 그룹 추가/조회만 있고 그룹 수정/삭제 UI·API0개다. | si-cost | SPEC-REQUEST.md:L186 — - 그룹은 DB 전체를 SORT_ORD/CODE_GRP_ID순으로 표시한다. 초기64개(시스템44), 첫 그룹 자동 선택·선택 강조·업무코드 수·시스템 자물쇠다. 목록 높이 안 별도 스크롤·선택 후 위치 유지. 그룹 이름/사용 여부 변경·그룹 삭제 기능은0개다. |
| REQ-154 | null | out-of-scope | out-of-scope | 기준 키 CRUD 확장 제외 | 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. | 값 변경 외 메타 수정/키 추가/키 삭제 API0개다. | si-cost | SPEC-REQUEST.md:L245 — - [운영 기준값]은 SQL의43키 전체를 읽어 이름·값·단위·적용 시작월·STD_DESC·[값 변경]/[이력]을 표시한다. 키·이름·단위·설명은 읽기 전용이고 신규 키/삭제 기능은 없다. 목록은 STD_CD순이다. |
| REQ-155 | null | out-of-scope | out-of-scope | 이력 변경·삭제 제외 | 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. | 이력 GET만 존재하며 기존/오래된 이력 삭제·수정 경로0개다. | si-cost | SPEC-REQUEST.md:L282 — [변경 이력]은 코드 그룹·코드·값코드·기준값·표기 대응만 조회한다. 필터targetType/groupId·기본전체, 정렬REG_DTTM DESC/HIST_ID DESC, 페이지20/50/100·기본50이다. 열은 일시·대상·그룹/코드/키·유형·항목·이전·이후·처리자이며 긴 값은 펼쳐 읽는다. 신규/값변경/중지/재개/삭제는 NEW/UPD/STOP/RESUME/DEL로 기록한다. codeName 이력의 CHG_ITEM_NM은 CODE_NM으로 고정해 과거 이름을 추적한다. SYSTEM은‘시스템’, admin은‘임시 관리자’로 표시한다. 이력 수정·삭제 API는0개다. |

## 합격 기준
<!-- speckit:section sec-bfafa806466a40edb453d668a1c3642c -->

각 절은 요구의 사용자/업무 목적, 입력 항목, 처리 규칙, 관찰 가능한 합격 결과를 소유한다. 기능 요구 외 설치·비기능·제외 요구의 합격 결과도 각 원장 행에 별도로 보존했다. 아래 source 인용은 상세 규칙의 근거이며 뒤의 원문 상세 계약과 함께 적용한다.

### REQ-012 — 공통 컴포넌트 실제 재사용 (source_id: null)
<!-- speckit:section sec-f651126b95404ad9831fe127ce489b36 -->

업무 목적: 공통 컴포넌트 실제 재사용을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 공통 부품을 UI-27에 실제 적용하고 Props·이벤트·사용처를 기록하며 개별 업무 분기를 넣지 않는다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 공통 부품을 UI-27에 실제 적용하고 Props·이벤트·사용처를 기록하며 개별 업무 분기를 넣지 않는다. 원문 근거: SPEC-REQUEST.md:68 — 레이아웃·표·검색·페이지·입력·모달·배지·이력을 재사용 가능한 React·TypeScript 컴포넌트로 구성한다. 현재 UI-27에서 실제 사용하고, 후속 화면도 동일 경로/인터페이스를 재사용하도록 Props·이벤트·사용처를 기록한다. 컬럼·렌더러·행 작업·필드 정의를 화면에서 전달하고 공통 부품 안에 개별 업무 분기를 넣지 않는다.

검증 기준:

- AC-REQ-012: 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-013 — 문자열 입력 검증 (source_id: null)
<!-- speckit:section sec-cff002814f16453a9aada812dace119d -->

업무 목적: 문자열 입력 검증을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: trim 후 필수/길이를 검사하고 초과값을 잘라 저장하지 않는다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: trim 후 필수/길이를 검사하고 초과값을 잘라 저장하지 않는다. 원문 근거: SPEC-REQUEST.md:86 — |문자열|trim 후 길이 검사, 필수 빈칸400. 초과값을 잘라 저장하지 않음|

검증 기준:

- AC-REQ-013: 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다.
- 사례 1: 필수 공백은 400
- 사례 2: 최대 길이 초과는 원문 입력을 보존한 채 400이다.

### REQ-014 — 그룹ID 정규화 (source_id: null)
<!-- speckit:section sec-6ef7de4bf13547d080c05499dd1cf3b3 -->

업무 목적: 그룹ID 정규화을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 그룹ID를 대문자로 정규화하고 원문 패턴과 길이를 검사한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 그룹ID를 대문자로 정규화하고 원문 패턴과 길이를 검사한다. 원문 근거: SPEC-REQUEST.md:87 — |그룹ID|소문자→대문자 후 `^[A-Z][A-Z0-9_]{0,29}$`,1~30자|

검증 기준:

- AC-REQ-014: 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다.
- 사례 1: 소문자 그룹ID는 대문자로 저장
- 사례 2: 31자 또는 숫자 시작 ID는 400이다.

### REQ-015 — 코드 정규화 (source_id: null)
<!-- speckit:section sec-3d612160ed7d42bc9a71328863809409 -->

업무 목적: 코드 정규화을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 코드를 대문자로 정규화하고 원문 패턴과 길이를 검사한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 코드를 대문자로 정규화하고 원문 패턴과 길이를 검사한다. 원문 근거: SPEC-REQUEST.md:88 — |코드|소문자→대문자 후 `^[A-Z0-9_]{1,20}$`,1~20자|

검증 기준:

- AC-REQ-015: 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다.
- 사례 1: 20자 영숫자/_ 코드는 허용
- 사례 2: 21자 또는 허용 외 문자는 400이다.

### REQ-016 — 명칭 경계와 식별자 불변 (source_id: null)
<!-- speckit:section sec-fdac3ebe452347f7b60d1ae8ab196787 -->

업무 목적: 명칭 경계와 식별자 불변을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 그룹명 1~50자·코드명 1~100자를 검증하고 등록된 ID/코드는 변경하지 못한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 그룹명 1~50자·코드명 1~100자를 검증하고 등록된 ID/코드는 변경하지 못한다. 원문 근거: SPEC-REQUEST.md:89 — |그룹명/코드명|각1~50/1~100자. 등록된 ID/코드는 불변|

검증 기준:

- AC-REQ-016: 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다.
- 사례 1: 그룹명50·코드명100자는 허용
- 사례 2: 51·101자 및 등록 PK 변경은 거부된다.

### REQ-017 — 정밀 십진값 범위 (source_id: null)
<!-- speckit:section sec-2bea2a2878be4bfc94d488ca45ca307d -->

업무 목적: 정밀 십진값 범위을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: CODE_VAL·STD_VAL을 숫자 문자열과 BigDecimal로 검사·저장한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: CODE_VAL·STD_VAL을 숫자 문자열과 BigDecimal로 검사·저장한다. 원문 근거: SPEC-REQUEST.md:90 — |CODE_VAL·STD_VAL|0~99,999,999,999.9999 포함, 정수부 최대11·소수 최대4자리. 숫자 문자열로 주고받고 BigDecimal로 검사/저장|

검증 기준:

- AC-REQ-017: 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다.
- 사례 1: 0과 99,999,999,999.9999 허용
- 사례 2: 정수12자리 또는 소수5자리는 400이다.

### REQ-018 — 숫자 입력 정리와 금지값 (source_id: null)
<!-- speckit:section sec-2bf05b768cbf440799f56e6bc7c00d19 -->

업무 목적: 숫자 입력 정리와 금지값을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 공백·쉼표·끝 원/%만 정리하고 음수·지수·NaN·Infinity·다른 문자를 거부한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 공백·쉼표·끝 원/%만 정리하고 음수·지수·NaN·Infinity·다른 문자를 거부한다. 원문 근거: SPEC-REQUEST.md:91 — |숫자 입력 처리|공백·쉼표·끝의 원/% 표시는 제거 후 검사. 음수·지수표기·NaN·Infinity·그 밖의 문자는400. 소수5자리 또는 정수12자리 입력도400|

검증 기준:

- AC-REQ-018: 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다.
- 사례 1: 쉼표/원 기호를 제거한 값은 저장
- 사례 2: 음수 또는 지수표기는 400이다.

### REQ-019 — 소수 원값 보존 (source_id: null)
<!-- speckit:section sec-978fa737a3f6426a9dc1e8718419e6d8 -->

업무 목적: 소수 원값 보존을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 자동 반올림·버림 없이 허용 정밀도를 저장하고 화면 뒤 0만 생략한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 자동 반올림·버림 없이 허용 정밀도를 저장하고 화면 뒤 0만 생략한다. 원문 근거: SPEC-REQUEST.md:92 — |반올림|CODE_VAL/STD_VAL은 입력 원값 보존, 자동 반올림/버림0회. 허용 소수 이내는 정확히 저장하며 화면 뒤0만 생략|

검증 기준:

- AC-REQ-019: 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다.
- 사례 1: 소수4자리 입력이 DB에서 정확히 재조회
- 사례 2: 자동 반올림/버림 0회다.

### REQ-020 — JOB 월단가 정수 (source_id: null)
<!-- speckit:section sec-12579855fa29441b814e80f5e45adb8d -->

업무 목적: JOB 월단가 정수을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: JOB.ADD_VAL2는 정리 후 0~999,999,999,999,999원 정수 문자열로 저장한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: JOB.ADD_VAL2는 정리 후 0~999,999,999,999,999원 정수 문자열로 저장한다. 원문 근거: SPEC-REQUEST.md:93 — |JOB.ADD_VAL2 월단가|0~999,999,999,999,999원 포함·소수0자리. 쉼표/공백/끝의 ‘원’ 정리 후 정수 문자열로 ADD_VAL2 저장. 음수/소수/상한 초과400|

검증 기준:

- AC-REQ-020: 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다.
- 사례 1: 0과 999,999,999,999,999 허용
- 사례 2: 음수·소수·상한 초과는 400이다.
<!-- decision-answer:12c06d916cb6dc04:start -->
- 확정 처리 규칙: 공백·쉼표·끝의 ‘원’을 제거한 뒤 정수 문자열로 검증한다. 음수·소수·최댓값 초과·그 밖의 문자는 400 입력 오류로 거부하고 저장하지 않는다. 자동 반올림·버림·범위 보정은 하지 않는다.
<!-- decision-answer:12c06d916cb6dc04:end -->

### REQ-021 — 실제 연월 검증 (source_id: null)
<!-- speckit:section sec-308caf0c552e4f02b49dc849e3ba146b -->

업무 목적: 실제 연월 검증을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 연월을 0001-01~9999-12의 실제 YYYY-MM으로 검사한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 연월을 0001-01~9999-12의 실제 YYYY-MM으로 검사한다. 원문 근거: SPEC-REQUEST.md:94 — |연월|0001-01~9999-12의 실제 연월. 월00/13·형식 오류400, 월 가감은 날짜 라이브러리 사용|

검증 기준:

- AC-REQ-021: 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다.
- 사례 1: 0001-01·9999-12 허용
- 사례 2: 월00/13·잘못된 형식은 400이다.

### REQ-022 — Y/N·JSON 타입 (source_id: null)
<!-- speckit:section sec-aa3a2cb4d39844a3a826a17be8d75115 -->

업무 목적: Y/N·JSON 타입을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: Y/N·JSON boolean·PREF_VAL 최대 2000자를 엄격히 검사한다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: Y/N·JSON boolean·PREF_VAL 최대 2000자를 엄격히 검사한다. 원문 근거: SPEC-REQUEST.md:95 — |Y/N·JSON|Y/N만, boolean은 JSON true/false만. PREF_VAL JSON 전체 최대2000자. 타입 오류400|

검증 기준:

- AC-REQ-022: true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다.
- 사례 1: true/false와 2000자 이내 JSON 허용
- 사례 2: 문자열 boolean/2001자 JSON은 400이다.

### REQ-023 — 빈 수당·기준값 구분 (source_id: null)
<!-- speckit:section sec-e226255b9b55409786f3766342222774 -->

업무 목적: 빈 수당·기준값 구분을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 신규 수당 기본/빈 CODE_VAL은 0을 허용하고 기준값 빈칸은 거부하며 % 임의100 상한을 추가하지 않는다.

입력 항목: 3.2의 원문 문자열/숫자 문자열/연월/Y/N/JSON 및 적용 그룹/필드.

처리 규칙: 신규 수당 기본/빈 CODE_VAL은 0을 허용하고 기준값 빈칸은 거부하며 % 임의100 상한을 추가하지 않는다. 원문 근거: SPEC-REQUEST.md:97 — 기준값 빈칸은400, 수당 CODE_VAL의 신규 기본0·비운 값0은 허용한다. 단위가 %라는 이유만으로 임의100 상한을 추가하지 않으며 별도 제한은6.2의 표만 적용한다. 금액 원 단위 콤마, 음수 표시 함수는 ‘−’,0은‘0’,NULL은‘–’, 일자YYYY-MM-DD·일시YYYY-MM-DD HH:mm, 숫자 오른쪽/tabular-nums·문자 왼쪽·상태 가운데다. 표시 함수는 저장값을 바꾸지 않는다.

검증 기준:

- AC-REQ-023: 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다.
- 사례 1: 수당 빈값은 0으로 재조회
- 사례 2: 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다.

### REQ-024 — 공통 표시 함수 (source_id: null)
<!-- speckit:section sec-a3b3986c46964ba5b71d41d0c74ce5d6 -->

업무 목적: 공통 표시 함수을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 금액·음수·0·NULL·일자·일시 및 숫자/문자/상태 정렬은 원문 표시 계약을 따른다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 금액·음수·0·NULL·일자·일시 및 숫자/문자/상태 정렬은 원문 표시 계약을 따른다. 원문 근거: SPEC-REQUEST.md:97 — 기준값 빈칸은400, 수당 CODE_VAL의 신규 기본0·비운 값0은 허용한다. 단위가 %라는 이유만으로 임의100 상한을 추가하지 않으며 별도 제한은6.2의 표만 적용한다. 금액 원 단위 콤마, 음수 표시 함수는 ‘−’,0은‘0’,NULL은‘–’, 일자YYYY-MM-DD·일시YYYY-MM-DD HH:mm, 숫자 오른쪽/tabular-nums·문자 왼쪽·상태 가운데다. 표시 함수는 저장값을 바꾸지 않는다.

검증 기준:

- AC-REQ-024: 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다.
- 사례 1: 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’
- 사례 2: 표시 전후 저장값은 같다.
<!-- decision-answer:94dd472057dffd88:start -->
- 확정 처리 규칙: - 금액 1234567 → 1,234,567 - 음수 -1234567 → −1,234,567 - 0 → 0 - NULL → – - 일자 → 2026-11-10 - 일시 → 2026-11-10 14:05 - 숫자 → 오른쪽 정렬, tabular-nums - 문자 → 왼쪽 정렬 - 상태 → 가운데 정렬 여기서 정렬은 셀 안의 가로 배치다. 목록 행의 정렬 순서는 각 화면에 명시된 기준을 따른다.
<!-- decision-answer:94dd472057dffd88:end -->

### REQ-025 — 임시 사용자 컨텍스트 (source_id: null)
<!-- speckit:section sec-e0e6f8804e7a4975a03468f0dbbde2bd -->

업무 목적: 임시 사용자 컨텍스트을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: CurrentUserProvider가 DB admin·roleIds=[ADMIN]·표시명 임시 관리자·developmentMode=true를 공급한다.

입력 항목: DB admin·CurrentUserProvider; 요청 처리자 필드는 신뢰하지 않는다.

처리 규칙: CurrentUserProvider가 DB admin·roleIds=[ADMIN]·표시명 임시 관리자·developmentMode=true를 공급한다. 원문 근거: SPEC-REQUEST.md:101 — - CurrentUserProvider는 DB의 admin·roleIds=[ADMIN]·표시명‘임시 관리자’·developmentMode=true를 제공한다. 헤더에 개발용임을 표시한다. 등록자·수정자·설정 USER_ID는 admin이며 요청으로 다른 처리자를 지정하지 못한다.

검증 기준:

- AC-REQ-025: GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다.; api_endpoint:GET /api/current-user
- 사례 1: GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다.
- 사례 2: api_endpoint:GET /api/current-user

연결 연산: GET /api/current-user (getCurrentUser); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-026 — 처리자 주입 거부 (source_id: null)
<!-- speckit:section sec-2e6a8e6100e34fca9199fe5384b210db -->

업무 목적: 처리자 주입 거부을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 등록자·수정자·설정 USER_ID는 admin으로 고정하고 요청의 다른 처리자 지정을 거부한다.

입력 항목: DB admin·CurrentUserProvider; 요청 처리자 필드는 신뢰하지 않는다.

처리 규칙: 등록자·수정자·설정 USER_ID는 admin으로 고정하고 요청의 다른 처리자 지정을 거부한다. 원문 근거: SPEC-REQUEST.md:101 — - CurrentUserProvider는 DB의 admin·roleIds=[ADMIN]·표시명‘임시 관리자’·developmentMode=true를 제공한다. 헤더에 개발용임을 표시한다. 등록자·수정자·설정 USER_ID는 admin이며 요청으로 다른 처리자를 지정하지 못한다.

검증 기준:

- AC-REQ-026: 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-027 — 인증·권한 유예 (source_id: null)
<!-- speckit:section sec-1df9c5f6be5f4dff8f43d45649aa26df -->

업무 목적: 인증·권한 유예을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 현재 권한 서비스는 허용·DataScope=ALL이며 한 공급자로 교체 가능하고 인증 차단은 만들지 않는다.

입력 항목: DB admin·CurrentUserProvider; 요청 처리자 필드는 신뢰하지 않는다.

처리 규칙: 현재 권한 서비스는 허용·DataScope=ALL이며 한 공급자로 교체 가능하고 인증 차단은 만들지 않는다. 원문 근거: SPEC-REQUEST.md:102 — - 인증 리다이렉트/401·역할별403·민감칸 가림은0건이다. 권한 서비스의 현재 결과는 허용·DataScope=ALL이며 공급자는 한곳에서 교체 가능하게 둔다. 관리자 ID를 각 업무 로직에 복제하지 않는다.

검증 기준:

- AC-REQ-027: /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-028 — 원자적 업무 변경 (source_id: null)
<!-- speckit:section sec-f2c7881e65324ae69166be5a98c02b4d -->

업무 목적: 원자적 업무 변경을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 코드 한 그룹·기준 한 요청·대응 추가/다중 삭제는 단일 트랜잭션이며 키 오름차순 잠금한다.

입력 항목: 코드 그룹 또는 기준 요청 또는 대응 요청의 변경분·정렬된 잠금 키·관련 이력.

처리 규칙: 코드 한 그룹·기준 한 요청·대응 추가/다중 삭제는 단일 트랜잭션이며 키 오름차순 잠금한다. 원문 근거: SPEC-REQUEST.md:103 — - 코드 한 그룹 저장, 운영기준 한 요청, 대응 추가/다중 삭제는 각각 단일 트랜잭션이다. 한 항목 실패 시 데이터와 이력 모두0건 변경이다. 잠금은 키 오름차순이다.

검증 기준:

- AC-REQ-028: 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다.
- 사례 1: 하나 실패하면 데이터/이력 모두 0건 변경
- 사례 2: 성공하면 대상과 이력이 함께 commit된다.

### REQ-029 — 불투명 revision (source_id: null)
<!-- speckit:section sec-18ed8db09c4647a398487997e59dfca5 -->

업무 목적: 불투명 revision을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 조회 revision은 식별자·저장값·등록/수정 시각으로 계산하며 그룹 전체 코드·기준키 전체 이력을 포함한다.

입력 항목: 기존 PK/저장값/REG_DTTM/MOD_DTTM·클라이언트 expectedRevision·잠긴 최신 행.

처리 규칙: 조회 revision은 식별자·저장값·등록/수정 시각으로 계산하며 그룹 전체 코드·기준키 전체 이력을 포함한다. 원문 근거: SPEC-REQUEST.md:104 — - 조회는 불투명 revision, 변경은 expectedRevision을 사용한다. 비교값은 기존 식별자·저장값·REG_DTTM/MOD_DTTM으로 계산한다. 그룹은 그룹과 소속 전체 코드 행, 기준키는 전체 적용월 이력을 포함한다. 시각 양쪽NULL은 시각만 같고 한쪽NULL은 다르다. 양쪽NULL이어도 값이 바뀌면 다른 revision이다.

검증 기준:

- AC-REQ-029: 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다.
<!-- decision-answer:b2588ee87c7a8af4:start -->
- 확정 처리 규칙: 비교 대상 전체를 정규화 JSON으로 만들고, UTF-8 기준 SHA-256을 계산해 소문자 64자리 문자열로 반환한다. 필드명은 이름순, 행은 PK순으로 정렬한다. NULL·숫자·시각의 직렬화 방식을 고정하고 DB 시각 정밀도를 보존한다.  동일 스냅샷은 동일한 식별값이어야 한다. 식별자·저장값·등록/수정 시각 또는 비교 대상 행의 추가·변경·삭제가 있으면 다른 식별값이어야 한다. 수정 시각이 양쪽 NULL이어도 저장값이 다르면 불일치다.  저장 시 대상 잠금 후 최신 DB 값으로 재계산해 expectedRevision과 비교한다. 불일치는 409 STALE_WRITE, 해당 요청의 데이터·변경 이력 변경은 0건이다.
<!-- decision-answer:b2588ee87c7a8af4:end -->

### REQ-030 — 잠금 후 동시 수정 검출 (source_id: null)
<!-- speckit:section sec-7d5d29df8d7d4d2db57ce36a78c46aae -->

업무 목적: 잠금 후 동시 수정 검출을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 대상 잠금 후 expectedRevision을 최신값과 비교하고 불일치면 409 STALE_WRITE다.

입력 항목: 기존 PK/저장값/REG_DTTM/MOD_DTTM·클라이언트 expectedRevision·잠긴 최신 행.

처리 규칙: 대상 잠금 후 expectedRevision을 최신값과 비교하고 불일치면 409 STALE_WRITE다. 원문 근거: SPEC-REQUEST.md:105 — - 대상 그룹/기준키 잠금 후 최신값과 비교하고 불일치면409 STALE_WRITE다. 두 클라이언트가 같은 revision으로 다른 값을 저장하면 선행1건 성공·후행1건409, 후행 DB/이력 변경0건이다. 스키마에 version 컬럼은 추가하지 않는다.

검증 기준:

- AC-REQ-030: 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다.
- 사례 1: 같은 revision의 두 저장 중 선행 1건 성공
- 사례 2: 후행 1건409이며 후행 데이터/이력 변경0건이다.

### REQ-031 — 실제 변경 없는 저장 (source_id: null)
<!-- speckit:section sec-a089e26b48c04b01b1cf68e2147483ba -->

업무 목적: 실제 변경 없는 저장을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: revision 일치 후 변경이 없으면 200 changed=false와 안내를 반환하고 수정시각·이력을 갱신하지 않는다.

입력 항목: 코드 그룹 또는 기준 요청 또는 대응 요청의 변경분·정렬된 잠금 키·관련 이력.

처리 규칙: revision 일치 후 변경이 없으면 200 changed=false와 안내를 반환하고 수정시각·이력을 갱신하지 않는다. 원문 근거: SPEC-REQUEST.md:106 — - revision 일치 후 실제 변경0이면200 changed=false·“바뀐 내용이 없습니다.”, 수정시각/이력 갱신0건이다. 변경이 있으면 commit 후 changed=true와 재조회 결과를 반영한다.

검증 기준:

- AC-REQ-031: 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다.
- 사례 1: 동일값 저장은 changed=false·이력0건
- 사례 2: 실제 변경 commit 후 changed=true와 재조회 결과다.

### REQ-032 — commit 후 캐시 최신화 (source_id: null)
<!-- speckit:section sec-e4ce2a5399194d09a986f37043548a83 -->

업무 목적: commit 후 캐시 최신화을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 성공 응답 후 2초 이내 현재 브라우저 관련 캐시·선택목록·설정을 갱신하고 서버 캐시는 commit 후 무효화한다.

입력 항목: commit 결과·changed·저장 브라우저의 관련 query/cache/선택목록/설정.

처리 규칙: 성공 응답 후 2초 이내 현재 브라우저 관련 캐시·선택목록·설정을 갱신하고 서버 캐시는 commit 후 무효화한다. 원문 근거: SPEC-REQUEST.md:107 — - 저장 성공 응답 수신 후2초 이내에 저장 브라우저의 관련 캐시·열린 선택 목록·설정을 최신값으로 갱신한다. 서버 캐시는 commit 이후 무효화한다. 다른 브라우저는 다음 조회에서 최신값을 받는다. 통신 오류는 재시도 안내이며 오래된 값을 성공 결과로 표시하지 않는다.

검증 기준:

- AC-REQ-032: 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다.
- 사례 1: 저장 브라우저 2초 내 최신값
- 사례 2: 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다.

### REQ-033 — 한국어 오류와 재조회 실패 구분 (source_id: null)
<!-- speckit:section sec-72b43059a03b4eeeba2f1461049829d5 -->

업무 목적: 한국어 오류와 재조회 실패 구분을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 필드 오류·첫 포커스·오류 띠·다시 시도·requestId와 commit 후 재조회 실패를 구분한다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 필드 오류·첫 포커스·오류 띠·다시 시도·requestId와 commit 후 재조회 실패를 구분한다. 원문 근거: SPEC-REQUEST.md:108 — - 오류는 `{code,message,field?,details?,requestId}`·X-Request-Id다. 필드 오류·첫 오류 포커스, 연결/500의 오류 띠·[다시 시도]를 제공한다. commit 후 재조회만 실패하면 “저장됐지만 다시 불러오지 못했습니다.”로 구분한다. 자동 저장/재시도0회·처리 중 중복 클릭 차단·입력 보존이다.

검증 기준:

- AC-REQ-033: 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다.
- 사례 1: 500/연결 실패는 오류 띠·재시도
- 사례 2: commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다.

### REQ-034 — 중복 클릭·자동 재저장 금지 (source_id: null)
<!-- speckit:section sec-db95f1acbb9d4901b17e527735aba7a0 -->

업무 목적: 중복 클릭·자동 재저장 금지을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 처리 중 중복 클릭을 막고 입력을 보존하며 자동 저장/재시도는 하지 않는다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 처리 중 중복 클릭을 막고 입력을 보존하며 자동 저장/재시도는 하지 않는다. 원문 근거: SPEC-REQUEST.md:108 — - 오류는 `{code,message,field?,details?,requestId}`·X-Request-Id다. 필드 오류·첫 오류 포커스, 연결/500의 오류 띠·[다시 시도]를 제공한다. commit 후 재조회만 실패하면 “저장됐지만 다시 불러오지 못했습니다.”로 구분한다. 자동 저장/재시도0회·처리 중 중복 클릭 차단·입력 보존이다.

검증 기준:

- AC-REQ-034: 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다.
- 사례 1: 처리 중 반복 클릭의 추가 요청0건
- 사례 2: 실패 후 입력 유지·자동 재저장0회다.

### REQ-035 — DB 메뉴 구성·정렬 (source_id: null)
<!-- speckit:section sec-121e4301abab459283533ef51aae63b2 -->

업무 목적: 실제 메뉴에서 등록 화면으로 안전하게 진입한다.

관찰 가능한 동작: USE_YN=Y의 정상 DB 부모/자식과 이름·아이콘·PC 전용 여부를 읽고 형제를 SORT_ORD/MENU_ID순 표시한다.

입력 항목: TB_SY_MENU의 부모/자식·MENU_ID/SORT_ORD/USE_YN/SCRN_ID/MENU_LVL/PC_ONLY_YN, screenId URL/hash.

처리 규칙: USE_YN=Y의 정상 DB 부모/자식과 이름·아이콘·PC 전용 여부를 읽고 형제를 SORT_ORD/MENU_ID순 표시한다. 원문 근거: SPEC-REQUEST.md:114 — TB_SY_MENU를 API로 읽어 USE_YN=Y의 정상 부모/자식 관계를 구성한다. 형제는 SORT_ORD/MENU_ID순, 이름·아이콘·PC 전용 여부는 DB 원천이다. 아래는 초기값 검증 목록이며 프론트 하드코딩 메뉴 원천으로 사용하지 않는다.

검증 기준:

- AC-REQ-035: 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다.; api_endpoint:GET /api/menus
- 사례 1: 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다.
- 사례 2: api_endpoint:GET /api/menus

연결 연산: GET /api/menus (getMenus); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-036 — 메뉴 펼침·접힘 탐색 (source_id: null)
<!-- speckit:section sec-d8cb7dd9e8ff4a62912b7f13a46488a8 -->

업무 목적: 실제 메뉴에서 등록 화면으로 안전하게 진입한다.

관찰 가능한 동작: 현재 묶음 하나를 기본 펼치며 대시보드 직접 이동·부모 이름 이동/아이콘 토글·접힌 hover/focus 목록을 제공한다.

입력 항목: TB_SY_MENU의 부모/자식·MENU_ID/SORT_ORD/USE_YN/SCRN_ID/MENU_LVL/PC_ONLY_YN, screenId URL/hash.

처리 규칙: 현재 묶음 하나를 기본 펼치며 대시보드 직접 이동·부모 이름 이동/아이콘 토글·접힌 hover/focus 목록을 제공한다. 원문 근거: SPEC-REQUEST.md:129 — - 현재 묶음을 자동 펼치고 기본은 한 묶음만 펼친다. 대시보드는 그룹+자식 데이터여도 한 항목으로 바로 이동한다. 화면ID가 있는 부모의 이름은 이동·펼침 아이콘은 토글이다. 접힌 메뉴는 hover/focus 하위 목록을 제공한다.

검증 기준:

- AC-REQ-036: 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다.
- 사례 1: 부모 이름은 화면 이동, 펼침 아이콘은 토글
- 사례 2: 접힌 메뉴 키보드 focus에서 하위 목록이 보인다.

### REQ-037 — 잘못된 메뉴 트리 진단 (source_id: null)
<!-- speckit:section sec-0f9ec650aaf84cd58668301edaddb943 -->

업무 목적: 실제 메뉴에서 등록 화면으로 안전하게 진입한다.

관찰 가능한 동작: 비정상 노드와 자손·중복 screen 탈락 트리를 제외하고 진단을 남기며 정상 부분만 표시한다.

입력 항목: TB_SY_MENU의 부모/자식·MENU_ID/SORT_ORD/USE_YN/SCRN_ID/MENU_LVL/PC_ONLY_YN, screenId URL/hash.

처리 규칙: 비정상 노드와 자손·중복 screen 탈락 트리를 제외하고 진단을 남기며 정상 부분만 표시한다. 원문 근거: SPEC-REQUEST.md:130 — - 없는/중지 부모·자기참조·순환·MENU_LVL 깊이 불일치·미등록 화면ID는 해당 노드와 자손을 제외하고 진단을 남긴다. 중복 SCRN_ID는 정상 계층→SORT_ORD→MENU_ID순 대표1개, 탈락 트리도 제외한다. 다른 부모로 자동 이동하거나 DML을 수정하지 않는다. 정상 부분은 표시하되 초기 메뉴 오류가 남으면 합격하지 않는다.

검증 기준:

- AC-REQ-037: 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다.
- 사례 1: 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외
- 사례 2: DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다.

### REQ-038 — 공식 라우트와 복구 (source_id: null)
<!-- speckit:section sec-cc4034ce7b304199b5594b3b9aa8544e -->

업무 목적: 실제 메뉴에서 등록 화면으로 안전하게 진입한다.

관찰 가능한 동작: /{screenId}·/→/UI-16·hash replace를 지원하고 유효 화면 부재 시 원문 순서로 복구한다.

입력 항목: TB_SY_MENU의 부모/자식·MENU_ID/SORT_ORD/USE_YN/SCRN_ID/MENU_LVL/PC_ONLY_YN, screenId URL/hash.

처리 규칙: /{screenId}·/→/UI-16·hash replace를 지원하고 유효 화면 부재 시 원문 순서로 복구한다. 원문 근거: SPEC-REQUEST.md:131 — - React 등록표는 screenId/컴포넌트/기간 유형만 관리한다. 공식 경로는 `/{screenId}`, `/`는 `/UI-16`, `#UI-xx`는 공식 경로로 replace한다. 유효 메뉴가 없는 주소는 대시보드, 대시보드도 없으면 정상 첫 화면, 정상 화면0개면 메뉴 오류다. 리다이렉트 반복은0회다.

검증 기준:

- AC-REQ-038: /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다.
- 사례 1: /UI-27 새로고침 및 #UI-27 replace 성공
- 사례 2: 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다.

### REQ-039 — PC 전용 화면 보호 (source_id: null)
<!-- speckit:section sec-1e88928ce6284729bb053a7a8d42a674 -->

업무 목적: PC 전용 화면 보호을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 폭768px 미만에서 PC 전용 메뉴/즐겨찾기/최근을 숨기고 직접 접근은 PC 안내를 표시한다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 폭768px 미만에서 PC 전용 메뉴/즐겨찾기/최근을 숨기고 직접 접근은 PC 안내를 표시한다. 원문 근거: SPEC-REQUEST.md:132 — - PC_ONLY_YN=Y 초기 화면은 UI-10·14·23·24·25·27이다. 폭768px 미만에서 메뉴·즐겨찾기·최근에서 숨긴다. 직접 접근/창 축소는 본문 대신 PC 사용 안내·이전/대시보드 이동을 보여 준다. 미저장 상태는 유지하고 폭을 넓히면 복원한다.

검증 기준:

- AC-REQ-039: 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다.
- 사례 1: 767px UI-27 본문 대신 안내·이동 수단
- 사례 2: 폭 확대 시 미저장 편집이 복원된다.

### REQ-040 — 반응형 공통 레이아웃 (source_id: null)
<!-- speckit:section sec-3aba01b263734aafbcdddb75b433cea4 -->

업무 목적: 반응형 공통 레이아웃을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: AppLayout·Header·Sidebar·ScreenTabs·TitleBar를 원문 치수와 breakpoint에 맞춰 배치한다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: AppLayout·Header·Sidebar·ScreenTabs·TitleBar를 원문 치수와 breakpoint에 맞춰 배치한다. 원문 근거: SPEC-REQUEST.md:136 — - AppLayout이 Header/Sidebar/ScreenTabs/TitleBar/본문을 조합한다. 헤더56px, PC≥1280 메뉴 펼침240/접힘64px·여백24px, 태블릿768~1279 기본접힘·여백16px, 모바일≤767 메뉴 슬라이드·화면탭 없음이다. 최소360px에서 본문 전체 가로 넘침0, 넓은 표만 내부 스크롤한다.

검증 기준:

- AC-REQ-040: 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다.
- 사례 1: 1280px 메뉴240/64px·여백24px
- 사례 2: 1024px 기본접힘·16px, 360px 전체 가로넘침0이다.

### REQ-041 — 헤더의 실제 제공 항목 (source_id: null)
<!-- speckit:section sec-a6fadeb79e9e4323aa5202e659667537 -->

업무 목적: 헤더의 실제 제공 항목을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 메뉴 토글·대시보드 이동·임시 관리자/화면 설정을 제공하며 AI/알림은 준비 중 안내만 한다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 메뉴 토글·대시보드 이동·임시 관리자/화면 설정을 제공하며 AI/알림은 준비 중 안내만 한다. 원문 근거: SPEC-REQUEST.md:137 — - 헤더는 메뉴 토글·로고/시스템명(대시보드 이동)·AI/알림·임시 관리자 메뉴다. AI/알림 클릭은 “준비 중” 안내, 가짜 배지/대화는0개다. 사용자 메뉴는 화면 설정만 제공한다. 비밀번호·로그아웃·타이머는 없다.

검증 기준:

- AC-REQ-041: AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다.
- 사례 1: AI/알림 클릭은 준비 중이고 가짜 배지/대화0개
- 사례 2: 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다.

### REQ-042 — 화면 제목·기간 배치 (source_id: null)
<!-- speckit:section sec-c31a0215483a452990fbc5e76b024842 -->

업무 목적: 화면 제목·기간 배치을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 묶음›화면명·즐겨찾기·UI-ID·작업을 표시하고 업무 기간은 제목 아래에 둔다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 묶음›화면명·즐겨찾기·UI-ID·작업을 표시하고 업무 기간은 제목 아래에 둔다. 원문 근거: SPEC-REQUEST.md:138 — - 제목은 묶음›화면명·즐겨찾기·UI-ID·해당 화면 작업이다. 업무 기간은 헤더가 아닌 제목 아래 본문 줄이다. UI-27은 업무 기간 줄이 없고 자체 적용월 입력을 사용한다.

검증 기준:

- AC-REQ-042: UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다.

### REQ-043 — 화면 탭과 LRU (source_id: null)
<!-- speckit:section sec-445e2068aa354a269a24d85c17eef9bb -->

업무 목적: 화면 탭과 LRU을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 화면을 재활성화 또는 새 탭으로 열고 대시보드 포함 최대8개·9번째는 닫을 수 있는 LRU 확인을 거친다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 화면을 재활성화 또는 새 탭으로 열고 대시보드 포함 최대8개·9번째는 닫을 수 있는 LRU 확인을 거친다. 원문 근거: SPEC-REQUEST.md:139 — - 메뉴 클릭은 탭 생성, 열린 화면이면 재활성화다. 최대8개(대시보드 포함), 대시보드는 맨 앞·닫기 불가다.9번째는 가장 오래 안 본 닫을 수 있는 탭을 제시해 확인한다. 취소하면 새 탭0개·기존 탭 유지다.

검증 기준:

- AC-REQ-043: 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다.
- 사례 1: 대시보드 닫기 불가
- 사례 2: 9번째 취소 시 새 탭0개·기존8개 유지다.

### REQ-044 — 편집 보존과 이동 경고 (source_id: null)
<!-- speckit:section sec-f0faf9d7d4284f4982865a5e5ac9e71a -->

업무 목적: 편집 보존과 이동 경고을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 단순 탭 전환은 상태를 보존하고 그룹/하위탭 변경·닫기·상태 폐기 이동은 미저장 확인을 거친다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 단순 탭 전환은 상태를 보존하고 그룹/하위탭 변경·닫기·상태 폐기 이동은 미저장 확인을 거친다. 원문 근거: SPEC-REQUEST.md:140 — - 일반 탭 전환은 편집·필터·기간·스크롤 보존이다. UI-27의 그룹/하위 탭 변경·탭 닫기·상태를 버리는 이동은 “저장하지 않은 내용이 있습니다.”와 [계속 작성]/[나가기], 취소 시 상태를 유지한다. dirty 탭은 ●, 새로고침/창 닫기는 beforeunload다. 모달은 focus trap·닫기 후 포커스 복원, 입력은 label·Tab·오류 포커스를 제공한다.

검증 기준:

- AC-REQ-044: 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다.
- 사례 1: 계속 작성 시 입력/필터/기간/스크롤 유지
- 사례 2: 새로고침/닫기는 beforeunload·dirty 탭은 ●다.

### REQ-045 — 접근 가능한 모달·입력 (source_id: null)
<!-- speckit:section sec-3e694f2bb605425986d4ec13afa5d8c7 -->

업무 목적: 접근 가능한 모달·입력을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 모달 focus trap·닫기 후 포커스 복원과 입력 label/Tab/첫 오류 포커스를 제공한다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 모달 focus trap·닫기 후 포커스 복원과 입력 label/Tab/첫 오류 포커스를 제공한다. 원문 근거: SPEC-REQUEST.md:140 — - 일반 탭 전환은 편집·필터·기간·스크롤 보존이다. UI-27의 그룹/하위 탭 변경·탭 닫기·상태를 버리는 이동은 “저장하지 않은 내용이 있습니다.”와 [계속 작성]/[나가기], 취소 시 상태를 유지한다. dirty 탭은 ●, 새로고침/창 닫기는 beforeunload다. 모달은 focus trap·닫기 후 포커스 복원, 입력은 label·Tab·오류 포커스를 제공한다.

검증 기준:

- AC-REQ-045: 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다.
- 사례 1: 모달 내 Tab 순환·닫기 후 원래 포커스
- 사례 2: 잘못된 입력 제출 시 첫 오류 필드로 이동한다.

### REQ-046 — 새로고침 탭 초기화 (source_id: null)
<!-- speckit:section sec-52ddd75ed4d44495b58f7d001b4292d8 -->

업무 목적: 새로고침 탭 초기화을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 새로고침은 URL 화면과 고정 대시보드로 시작하며 열린 탭 목록은 서버 복원하지 않는다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 새로고침은 URL 화면과 고정 대시보드로 시작하며 열린 탭 목록은 서버 복원하지 않는다. 원문 근거: SPEC-REQUEST.md:141 — - 새로고침은 URL 화면+고정 대시보드로 시작한다. 열린 탭 목록 자체를 서버 복원하지 않는다.

검증 기준:

- AC-REQ-046: 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-047 — 설정 조회와 기본값 (source_id: null)
<!-- speckit:section sec-5ba603a4b2274d049ebc7533ef20a145 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: admin 설정 GET은 value/revision/stored를 반환하며 행 없음은 기본값·MENU_FOLD만 null이다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: admin 설정 GET은 value/revision/stored를 반환하며 행 없음은 기본값·MENU_FOLD만 null이다. 원문 근거: SPEC-REQUEST.md:145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다.

검증 기준:

- AC-REQ-047: 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다.; api_endpoint:GET /api/me/preferences
- 사례 1: 없는 설정은 stored=false
- 사례 2: MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다.; api_endpoint:GET /api/me/preferences

연결 연산: GET /api/me/preferences (getPreferences); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-048 — 설정 명시 저장 (source_id: null)
<!-- speckit:section sec-024d8419cf8d423c9bc08e5cb77410ea -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: 최초 PUT은 expectedRevision=null·기존은 토큰이며 기본값과 같아도 최초 명시 저장은 INSERT한다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: 최초 PUT은 expectedRevision=null·기존은 토큰이며 기본값과 같아도 최초 명시 저장은 INSERT한다. 원문 근거: SPEC-REQUEST.md:145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다.

검증 기준:

- AC-REQ-048: 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다.; api_endpoint:PUT /api/me/preferences/{type}/{key}
- 사례 1: 기본값 최초 PUT 후 stored=true
- 사례 2: 동시 최초 등록 후행은409 STALE_WRITE다.; api_endpoint:PUT /api/me/preferences/{type}/{key}

연결 연산: PUT /api/me/preferences/{type}/{key} (putPreference); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-049 — 손상 설정 격리 (source_id: null)
<!-- speckit:section sec-518fba24a1934715b61bf986676c80e2 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: 잘못된 JSON은 항목 오류/revision과 기본값 임시 표시로 격리하고 자동 삭제하지 않는다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: 잘못된 JSON은 항목 오류/revision과 기본값 임시 표시로 격리하고 자동 삭제하지 않는다. 원문 근거: SPEC-REQUEST.md:145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다.

검증 기준:

- AC-REQ-049: 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다.
- 사례 1: 손상 항목 조회는 오류와 revision
- 사례 2: 다른 설정값 유지·문제 행 자동 DELETE0건이다.

### REQ-050 — 개별 설정 초기화 (source_id: null)
<!-- speckit:section sec-96d64c142b094ee283402bd709dea1df -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: 이 설정 초기화는 해당 admin·type·key 행만 물리 삭제하며 UI-27 이력을 남기지 않는다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: 이 설정 초기화는 해당 admin·type·key 행만 물리 삭제하며 UI-27 이력을 남기지 않는다. 원문 근거: SPEC-REQUEST.md:145 — TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다.

검증 기준:

- AC-REQ-050: 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다.; api_endpoint:DELETE /api/me/preferences/{type}/{key}
- 사례 1: 초기화 후 해당 GET은 stored=false·기본값
- 사례 2: 다른 설정 행 및 UI-27 이력은 그대로다.; api_endpoint:DELETE /api/me/preferences/{type}/{key}

연결 연산: DELETE /api/me/preferences/{type}/{key} (deletePreference); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-051 — 즐겨찾기 편집 (source_id: null)
<!-- speckit:section sec-6b0346fd093a474395a5098b74f9666a -->

업무 목적: 자주 쓰는 화면을 순서대로 다시 연다.

관찰 가능한 동작: 화면 별 추가/해제·▲▼ 순서를 FAV에 저장하고 중복 없이 최대10개를 허용한다.

입력 항목: 등록된 screenId 배열·기존 FAV value/revision·목록 순서·현재 화면폭.

처리 규칙: 화면 별 추가/해제·▲▼ 순서를 FAV에 저장하고 중복 없이 최대10개를 허용한다. 원문 근거: SPEC-REQUEST.md:149 — |FAV / *|화면ID 배열·기본[]·최대10개·중복 불가. 별 추가/해제, 목록 편집▲▼로 순서 변경.11번째는 거부|

검증 기준:

- AC-REQ-051: 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다.
- 사례 1: 10개 저장 후 재조회 순서 일치
- 사례 2: 11번째 추가는 거부되어 기존10개가 유지된다.

### REQ-052 — 최근 화면 자동 갱신 (source_id: null)
<!-- speckit:section sec-e75ef1395bc44a94aaa70bf838ec154e -->

업무 목적: 최근 화면 자동 갱신을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 활성화순 RECENT를 중복 없이 최대5개로 저장하고 재방문을 맨 앞으로 옮긴다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 활성화순 RECENT를 중복 없이 최대5개로 저장하고 재방문을 맨 앞으로 옮긴다. 원문 근거: SPEC-REQUEST.md:150 — |RECENT / *|최근 활성화순 화면ID 배열·기본[]·최대5개. 같은 화면 재방문은 맨 앞으로 이동·중복 제거·초과 뒤에서 제거|

검증 기준:

- AC-REQ-052: 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다.

### REQ-053 — 메뉴 접힘 기억 (source_id: null)
<!-- speckit:section sec-be8d44d4df2f4bfb85ccf85eb0706401 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: MENU_FOLD boolean을 저장하고 미저장 PC=false·태블릿=true, 모바일 슬라이드는 임시 상태다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: MENU_FOLD boolean을 저장하고 미저장 PC=false·태블릿=true, 모바일 슬라이드는 임시 상태다. 원문 근거: SPEC-REQUEST.md:151 — |MENU_FOLD / *|boolean. 저장값 없으면 PC=false·태블릿=true. 모바일 슬라이드 열림은 임시 상태이며 이 값을 변경하지 않음|

검증 기준:

- AC-REQ-053: PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다.
- 사례 1: PC 접힘 저장 후 재조회 true
- 사례 2: 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다.

### REQ-054 — 목록 크기 설정 (source_id: null)
<!-- speckit:section sec-d3d96891b98f49c7a1e0b5f9873f8a91 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: PAGE_SIZE는 JSON 정수20/50/100만 저장하며 기본50이다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: PAGE_SIZE는 JSON 정수20/50/100만 저장하며 기본50이다. 원문 근거: SPEC-REQUEST.md:152 — |PAGE_SIZE / *|JSON 정수20/50/100·기본50. 문자열‘50’ 거부|

검증 기준:

- AC-REQ-054: 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다.
- 사례 1: 정수100 허용
- 사례 2: 문자열 ‘50’은400이고 미저장 목록은50행 기준이다.

### REQ-055 — 밀도 설정 동기화 (source_id: null)
<!-- speckit:section sec-df7de824a2224bbb9e82ded490960bd8 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: DENSE 넓게/좁게를 기본 넓게로 서버 저장하고 열린 목록과 사용자 메뉴에 동일 적용한다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: DENSE 넓게/좁게를 기본 넓게로 서버 저장하고 열린 목록과 사용자 메뉴에 동일 적용한다. 원문 근거: SPEC-REQUEST.md:153 — |DENSE / *|‘넓게’/‘좁게’·기본넓게. 모든 열린 목록과 사용자 메뉴에 같은값 적용|

검증 기준:

- AC-REQ-055: 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다.
- 사례 1: 좁게 저장 후 열린 목록 모두 좁게
- 사례 2: 새로고침 재조회도 좁게다.

### REQ-056 — 표 열 설정 저장 (source_id: null)
<!-- speckit:section sec-bdc81656b8434191941f527f8872965c -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: COLS는 표별 전체 순서와 선택 숨김을 저장하고 필수열 숨김·중복·미등록열을 거부한다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: COLS는 표별 전체 순서와 선택 숨김을 저장하고 필수열 숨김·중복·미등록열을 거부한다. 원문 근거: SPEC-REQUEST.md:154 — |COLS / screenId|표별 `{viewId:{order:[columnId],hidden:[columnId]}}`·기본 전체열/기본순서. 필수열 숨김·중복·미등록열 거부|

검증 기준:

- AC-REQ-056: 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다.
- 사례 1: 유효 order/hidden은 재조회 일치
- 사례 2: 필수열 숨김 또는 중복 열은400·기존 설정 보존이다.

### REQ-057 — 금액 단위 선택 기억 (source_id: null)
<!-- speckit:section sec-4ea925ef7e534dc192ec8a7a1fdcb7e7 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: AMT_UNIT 원/천원/백만원을 기본 천원으로 저장/복원하되 업무 금액 변환은 하지 않는다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: AMT_UNIT 원/천원/백만원을 기본 천원으로 저장/복원하되 업무 금액 변환은 하지 않는다. 원문 근거: SPEC-REQUEST.md:155 — |AMT_UNIT / *|‘원’/‘천원’/‘백만원’·기본천원. 선택/저장/복원만, 실제 업무 금액 변환은 이번 범위에서 제외|

검증 기준:

- AC-REQ-057: 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다.
- 사례 1: 백만원 선택 후 재조회 백만원
- 사례 2: 업무 테이블 숫자 변경0건이다.
<!-- decision-answer:1ddedc2589cba9d4:start -->
- 확정 처리 규칙: 금액 단위는 원·천원·백만원을 저장·복원하고 미지정 시 천원을 기본값으로 쓰며, 단위 변경에 따른 업무 금액 변경 건수가 0건인 것을 합격 기준으로 제안합니다.
<!-- decision-answer:1ddedc2589cba9d4:end -->

### REQ-058 — 설정 화면ID·표시 보호 (source_id: null)
<!-- speckit:section sec-1f6685882e5249ad82fe3371383529f6 -->

업무 목적: 자주 쓰는 화면을 순서대로 다시 연다.

관찰 가능한 동작: 즐겨찾기/최근은 등록 screenId만 허용하고 PC 숨김은 배열 삭제가 아닌 표시만 숨긴다.

입력 항목: 등록된 screenId 배열·기존 FAV value/revision·목록 순서·현재 화면폭.

처리 규칙: 즐겨찾기/최근은 등록 screenId만 허용하고 PC 숨김은 배열 삭제가 아닌 표시만 숨긴다. 원문 근거: SPEC-REQUEST.md:158 — - 즐겨찾기/최근 항목은 등록된 화면ID만 허용한다. 숨겨진 PC 전용 항목은 배열에서 삭제하지 않고 표시만 숨긴다. 메뉴 접힘 상태에서는 두 목록을 숨긴다. 빈 안내는 각각 “화면 제목 옆 ☆를 누르면 추가됩니다”·“아직 없습니다”다.

검증 기준:

- AC-REQ-058: 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다.
- 사례 1: 미등록 screenId 저장 거부
- 사례 2: 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다.

### REQ-059 — UI-27 열 정의 제한 (source_id: null)
<!-- speckit:section sec-034fb56943ec4bc69e1c8d21bc620b63 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: UI-27 viewId와 필수열을 고정하고 체크/작업열·아직 표 없는 다른 화면의 COLS를 받지 않는다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: UI-27 viewId와 필수열을 고정하고 체크/작업열·아직 표 없는 다른 화면의 COLS를 받지 않는다. 원문 근거: SPEC-REQUEST.md:159 — - UI-27의 viewId는 codes/valueCodes/mappings/standards/history다. order는 해당 표 정의의 전체 열 순열, hidden은 선택 열 부분집합이다. 코드/값코드의 code/name, 대응의 sourceText/groupId/code, 기준의 key/name/value, 이력의 occurredAt/target는 필수열이다. 선택 체크·작업 버튼은 열 설정에서 제외한다. 다른 화면의 COLS는 해당 화면에 표 정의가 생긴 뒤 허용한다.

검증 기준:

- AC-REQ-059: codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다.
- 사례 1: codes의 code/name 숨김400
- 사례 2: 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다.

### REQ-060 — 설정 적용과 실패 보존 (source_id: null)
<!-- speckit:section sec-f3e3e7e1ce6f45c499e7e60114cabce7 -->

업무 목적: 개인 목록 표시를 서버에 저장하여 다시 사용한다.

관찰 가능한 동작: PAGE_SIZE·DENSE는 서버 성공 후 열린 목록에 반영하고 실패 시 이전값/편집값을 유지한다.

입력 항목: admin의 type/key, value, expectedRevision; FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD 타입별 JSON.

처리 규칙: PAGE_SIZE·DENSE는 서버 성공 후 열린 목록에 반영하고 실패 시 이전값/편집값을 유지한다. 원문 근거: SPEC-REQUEST.md:161 — - PAGE_SIZE·DENSE는 목록과 사용자 메뉴 어디서 바꿔도 서버 저장 후 열린 목록에 반영한다. 필터/페이지/열 설정·밀도는 미저장 편집값을 삭제하지 않는다. 설정 저장 실패는 이전 적용값으로 유지·오류 안내다. localStorage만으로 합격하지 않는다.

검증 기준:

- AC-REQ-060: 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다.
- 사례 1: 설정 실패 시 이전 밀도 유지·오류 안내
- 사례 2: 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다.

### REQ-061 — 기간 설정 타입·범위 검사 (source_id: null)
<!-- speckit:section sec-b57e807acab441ad91f720877e30699f -->

업무 목적: 허용된 연도/월/누계 기간을 선택하고 기억한다.

관찰 가능한 동작: PERIOD는 화면 유형·연월 일치·허용범위를 검사하고 NONE 쓰기를 거부하며 범위 밖 저장값을 조용히 바꾸지 않는다.

입력 항목: 화면 등록표의 YEAR/YEAR_MONTH/YEAR_CUM/NONE, 서버 현재일·CASH_BASE_YM·TB_CL_CLOSE·기간 설정.

처리 규칙: PERIOD는 화면 유형·연월 일치·허용범위를 검사하고 NONE 쓰기를 거부하며 범위 밖 저장값을 조용히 바꾸지 않는다. 원문 근거: SPEC-REQUEST.md:162 — - PERIOD는 등록표 유형·연도와 월 일치·4.4의 범위를 검사한다. NONE 화면의 PERIOD 쓰기는400이다. 저장된 기간이 새 허용범위 밖이면 DB를 조용히 바꾸지 않고 안내와 기본기간 표시, 사용자가 다시 고를 때 저장한다.

검증 기준:

- AC-REQ-061: NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다.
- 사례 1: NONE PERIOD PUT은400
- 사례 2: 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다.

### REQ-062 — controlled 기간 부품 (source_id: null)
<!-- speckit:section sec-bed083d0819e4040b49f3292d2563122 -->

업무 목적: controlled 기간 부품을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: PeriodSelector는 부모의 type/value/min/max/disabled를 쓰고 DB 조회나 설정 저장을 내부에서 하지 않는다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: PeriodSelector는 부모의 type/value/min/max/disabled를 쓰고 DB 조회나 설정 저장을 내부에서 하지 않는다. 원문 근거: SPEC-REQUEST.md:166 — PeriodSelector는 React·TypeScript **controlled** 부품이다. Props는 `type:'YEAR'|'YEAR_MONTH', value:string, onChange(nextValue), disabled?, min?, max?, ariaLabel?`다. YEAR는 YYYY, YEAR_MONTH는 YYYY-MM만 전달한다. 값/범위/disabled는 부모 소유, 내부 DB 조회·설정 저장0회다. 범위 밖 화살표·disabled 입력은 비활성이고 변경 이벤트0회, 유효 선택1회는 이벤트1회다.

검증 기준:

- AC-REQ-062: YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다.
- 사례 1: YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회
- 사례 2: 범위 밖/disabled 이벤트0회·내부 DB호출0회다.

### REQ-063 — 화면 기간 선택·기억 (source_id: null)
<!-- speckit:section sec-4e7155a60bb845ff8ad2b978cc378ec3 -->

업무 목적: 허용된 연도/월/누계 기간을 선택하고 기억한다.

관찰 가능한 동작: ScreenPeriodBar는 서버 현재일/설정과 연결하고 누계 연도·기준월을 범위로 제한하여 기간을 저장한다.

입력 항목: 화면 등록표의 YEAR/YEAR_MONTH/YEAR_CUM/NONE, 서버 현재일·CASH_BASE_YM·TB_CL_CLOSE·기간 설정.

처리 규칙: ScreenPeriodBar는 서버 현재일/설정과 연결하고 누계 연도·기준월을 범위로 제한하여 기간을 저장한다. 원문 근거: SPEC-REQUEST.md:168 — ScreenPeriodBar가 서버 현재일·설정과 연결한다. 누계형은 연도+기준월·‘1~n월 누계’를 조합하며 연도 변경 시 기존 월을 새 범위로 제한한다. 기본기간은 현재 연월을 범위 안으로 제한한 값이다. 화면별 유형은 아래와 같다.

검증 기준:

- AC-REQ-063: 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다.; api_endpoint:GET /api/period-context
- 사례 1: 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다.
- 사례 2: api_endpoint:GET /api/period-context

연결 연산: GET /api/period-context (getPeriodContext); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-064 — 기간 범위와 운영 전 상태 (source_id: null)
<!-- speckit:section sec-b0f19320447541719200be873bc86706 -->

업무 목적: 허용된 연도/월/누계 기간을 선택하고 기억한다.

관찰 가능한 동작: CASH_BASE_YM와 현재일로 유형별 범위를 산정하고 범위 역전은 운영 시작 전·비활성·저장 금지다.

입력 항목: 화면 등록표의 YEAR/YEAR_MONTH/YEAR_CUM/NONE, 서버 현재일·CASH_BASE_YM·TB_CL_CLOSE·기간 설정.

처리 규칙: CASH_BASE_YM와 현재일로 유형별 범위를 산정하고 범위 역전은 운영 시작 전·비활성·저장 금지다. 원문 근거: SPEC-REQUEST.md:177 — - 현재 빈 업무화면의 YEAR 범위는 CASH_BASE_YM의 연도~현재연도+1, YEAR_MONTH는 CASH_BASE_YM~현재월이다. 누계형은 시작연도~현재연도, UI-07/08/09만 시작연도-1부터 허용한다. 누계 기준월은 선택연도의1월~12월, 현재연도는 현재월까지만이다. 범위 역전은 ‘운영 시작 전’·선택 비활성·PERIOD 저장0회다.

검증 기준:

- AC-REQ-064: YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다.
- 사례 1: YEAR는 시작연도~현재연도+1
- 사례 2: 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다.

### REQ-065 — 연도 배지 (source_id: null)
<!-- speckit:section sec-7c62232ab39f4ef9bd741302fe10b3cc -->

업무 목적: 허용된 연도/월/누계 기간을 선택하고 기억한다.

관찰 가능한 동작: 날짜 비교로 올해/지난 해·읽기 전용/내년·계획 배지를 표시하고 업무 계산/재계산은 하지 않는다.

입력 항목: 화면 등록표의 YEAR/YEAR_MONTH/YEAR_CUM/NONE, 서버 현재일·CASH_BASE_YM·TB_CL_CLOSE·기간 설정.

처리 규칙: 날짜 비교로 올해/지난 해·읽기 전용/내년·계획 배지를 표시하고 업무 계산/재계산은 하지 않는다. 원문 근거: SPEC-REQUEST.md:178 — - 연도 배지는 올해/지난 해·읽기 전용/내년·계획을 날짜 비교로 표시한다. 월 배지는 아래 상태 조회를 사용한다. 이번에는 기간 선택·표시·기억까지만 구현하고 업무 데이터의 계산/쓰기/재계산은 없다.

검증 기준:

- AC-REQ-065: 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다.
<!-- decision-answer:8b01623305e1ac76:start -->
- 확정 처리 규칙: 비교 기준일은 서버의 Asia/Seoul 현재일이다. 선택 연도가 현재 연도와 같으면 ‘올해’, 이전이면 ‘지난 해·읽기 전용’, 다음 연도이면 ‘내년·계획’을 표시한다. - 기준일 2026-12-31: 2026년은 ‘올해’, 2027년은 ‘내년·계획’ - 기준일 2027-01-01: 2026년은 ‘지난 해·읽기 전용’, 2027년은 ‘올해’ 이번에는 배지만 표시하며 업무 계산·재계산 실행은 0회다.
<!-- decision-answer:8b01623305e1ac76:end -->

### REQ-066 — 실제 월 마감 조회 (source_id: null)
<!-- speckit:section sec-8d9fec64f43f4cbfbcca0c01efcb1f91 -->

업무 목적: 허용된 연도/월/누계 기간을 선택하고 기억한다.

관찰 가능한 동작: isClosed는 시작월 이전 이관·결산완료, CLOSE는 마감, WORK/행 없음은 작업중으로 판단한다.

입력 항목: 화면 등록표의 YEAR/YEAR_MONTH/YEAR_CUM/NONE, 서버 현재일·CASH_BASE_YM·TB_CL_CLOSE·기간 설정.

처리 규칙: isClosed는 시작월 이전 이관·결산완료, CLOSE는 마감, WORK/행 없음은 작업중으로 판단한다. 원문 근거: SPEC-REQUEST.md:179 — - isClosed(ym)는 CASH_BASE_YM 이전이면 이관·결산완료, 이후 TB_CL_CLOSE.CLOSE_STAT_CD=CLOSE면 마감, WORK/행 없음이면 작업중이다. 서버 현재일과 DB로 판단하고9개월 마감 같은 가상 기록은0건이다. 마감 쓰기 API는0개다.

검증 기준:

- AC-REQ-066: CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다.; api_endpoint:GET /api/month-close
- 사례 1: CLOSE fixture는 마감
- 사례 2: 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다.; api_endpoint:GET /api/month-close

연결 연산: GET /api/month-close (getMonthClose); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-067 — UI-27 네 탭과 조회 상태 (source_id: null)
<!-- speckit:section sec-35b101cd82354283a1f0ca8ec30b2f72 -->

업무 목적: UI-27 네 탭과 조회 상태을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 코드/엑셀 표기 대응/운영 기준값/변경 이력 4탭과 loading/empty/error를 구분한다.

입력 항목: 해당 부품의 controlled Props·이벤트·화면폭·loading/empty/error/permission/success·기존 편집값. 상세 인터페이스는 원문 상세 계약 참조.

처리 규칙: 코드/엑셀 표기 대응/운영 기준값/변경 이력 4탭과 loading/empty/error를 구분한다. 원문 근거: SPEC-REQUEST.md:185 — - [코드] [엑셀 표기 대응] [운영 기준값] [변경 이력]4탭이다. 로딩·빈 결과·API 오류를 구분한다. 코드/대응/변경 이력은 페이지20/50/100·기본50, page≥1, 잘못된 size/page는400이다. 운영 기준값은43키를 묶음 줄로 모두 표시하고 페이지를 나누지 않는다. 공통 표의 페이지 기능은 선택 옵션이다. 기본 정렬은 각 절에 고정하며 페이지 이동도 편집값을 유지한다.

검증 기준:

- AC-REQ-067: 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다.

### REQ-068 — 표 페이지 계약 (source_id: null)
<!-- speckit:section sec-a1135c02d5064546b9d058dc76525a80 -->

업무 목적: 표 페이지 계약을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 코드/대응/이력은 page≥1·size20/50/100·기본50, 기준값은 전체43키 비페이지 목록이다.

입력 항목: page/size와 목록 종류; 운영 기준 목록은 비페이지 전체 조회.

처리 규칙: 코드/대응/이력은 page≥1·size20/50/100·기본50, 기준값은 전체43키 비페이지 목록이다. 원문 근거: SPEC-REQUEST.md:185 — - [코드] [엑셀 표기 대응] [운영 기준값] [변경 이력]4탭이다. 로딩·빈 결과·API 오류를 구분한다. 코드/대응/변경 이력은 페이지20/50/100·기본50, page≥1, 잘못된 size/page는400이다. 운영 기준값은43키를 묶음 줄로 모두 표시하고 페이지를 나누지 않는다. 공통 표의 페이지 기능은 선택 옵션이다. 기본 정렬은 각 절에 고정하며 페이지 이동도 편집값을 유지한다.

검증 기준:

- AC-REQ-068: page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다.
- 사례 1: page0 또는 size30은400
- 사례 2: 기준값43키는 페이지 분리 없이 모두 보인다.

### REQ-069 — 그룹 목록과 선택 (source_id: null)
<!-- speckit:section sec-77e36ea4259f46a8ac245839b24f8cb2 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 전체 그룹을 SORT_ORD/CODE_GRP_ID순으로 표시하고 첫 선택·코드 수·시스템 잠금·스크롤 위치를 제공한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 전체 그룹을 SORT_ORD/CODE_GRP_ID순으로 표시하고 첫 선택·코드 수·시스템 잠금·스크롤 위치를 제공한다. 원문 근거: SPEC-REQUEST.md:186 — - 그룹은 DB 전체를 SORT_ORD/CODE_GRP_ID순으로 표시한다. 초기64개(시스템44), 첫 그룹 자동 선택·선택 강조·업무코드 수·시스템 자물쇠다. 목록 높이 안 별도 스크롤·선택 후 위치 유지. 그룹 이름/사용 여부 변경·그룹 삭제 기능은0개다.

검증 기준:

- AC-REQ-069: 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다.; api_endpoint:GET /api/code-groups
- 사례 1: 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다.
- 사례 2: api_endpoint:GET /api/code-groups

연결 연산: GET /api/code-groups (getCodeGroups); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-070 — 일반 코드 조회 (source_id: null)
<!-- speckit:section sec-2f93f852014f4f309954a31307de7c08 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 일반 표는 지정 열과 저장된 SORT_ORD/CODE순이며 미삭제 중지 코드도 흐리게 표시한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 일반 표는 지정 열과 저장된 SORT_ORD/CODE순이며 미삭제 중지 코드도 흐리게 표시한다. 원문 근거: SPEC-REQUEST.md:187 — - 일반 코드 표는 code/name/addVal1~2(적용 그룹만)/sortOrder/useYn/deleteAction, 이름·사용·허용 추가값만 편집한다. 순서는 저장된 SORT_ORD·CODE순·읽기 전용이다. DEL_YN=N의 중지 코드도 흐리게 표시한다. 코드 표의 선택 체크/일괄 중지 버튼은0개, 여러 행을 수정 후 [저장]1회로 반영한다.

검증 기준:

- AC-REQ-070: GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다.; api_endpoint:GET /api/code-groups/{group}/codes
- 사례 1: GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다.
- 사례 2: api_endpoint:GET /api/code-groups/{group}/codes

연결 연산: GET /api/code-groups/{group}/codes (getGroupCodes); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-071 — 일반 코드 일괄 변경 저장 (source_id: null)
<!-- speckit:section sec-102b03792e594e26abac3ca5cc953a27 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 이름·사용·허용 추가값을 여러 행 편집하여 그룹 단위 저장1회로 반영한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 이름·사용·허용 추가값을 여러 행 편집하여 그룹 단위 저장1회로 반영한다. 원문 근거: SPEC-REQUEST.md:187 — - 일반 코드 표는 code/name/addVal1~2(적용 그룹만)/sortOrder/useYn/deleteAction, 이름·사용·허용 추가값만 편집한다. 순서는 저장된 SORT_ORD·CODE순·읽기 전용이다. DEL_YN=N의 중지 코드도 흐리게 표시한다. 코드 표의 선택 체크/일괄 중지 버튼은0개, 여러 행을 수정 후 [저장]1회로 반영한다.

검증 기준:

- AC-REQ-071: 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다.; api_endpoint:PUT /api/code-groups/{group}/codes
- 사례 1: 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다.
- 사례 2: api_endpoint:PUT /api/code-groups/{group}/codes

연결 연산: PUT /api/code-groups/{group}/codes (saveGroupCodes); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-072 — 편집 취소 복원 (source_id: null)
<!-- speckit:section sec-5711b77ef9914a6da395f1e8a64bb238 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 취소는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 취소는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. 원문 근거: SPEC-REQUEST.md:188 — - [취소]는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. [저장]은 변경분만 전송하고 현재 페이지에 없는 행을 삭제하지 않는다. 동일 code를 한 요청에2회 보내면400이다.

검증 기준:

- AC-REQ-072: 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다.
- 추가 경계 사례: 변경 요청의 revision이 최신값과 다르면 409 STALE_WRITE·후행 데이터/이력 변경0건이다.

### REQ-073 — 부분 변경·중복 코드 검사 (source_id: null)
<!-- speckit:section sec-48be4963405143b28104f4ff4337d43f -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 저장은 변경분만 받고 다른 페이지 행은 삭제하지 않으며 동일 code 요청2회는 거부한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 저장은 변경분만 받고 다른 페이지 행은 삭제하지 않으며 동일 code 요청2회는 거부한다. 원문 근거: SPEC-REQUEST.md:188 — - [취소]는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. [저장]은 변경분만 전송하고 현재 페이지에 없는 행을 삭제하지 않는다. 동일 code를 한 요청에2회 보내면400이다.

검증 기준:

- AC-REQ-073: 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다.
- 사례 1: 변경 한 행 저장 후 다른 페이지 보존
- 사례 2: 동일 code 두 건 요청은400·전체변경0건이다.

### REQ-074 — 시스템 코드 변경 제한 (source_id: null)
<!-- speckit:section sec-47c86e8b6e1447bd9aeb3faf3f9c9b56 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: SYS_CODE_YN=Y는 이름만 변경하고 다른 변경이 섞이면 전체409 SYSTEM_CODE_LOCKED다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: SYS_CODE_YN=Y는 이름만 변경하고 다른 변경이 섞이면 전체409 SYSTEM_CODE_LOCKED다. 원문 근거: SPEC-REQUEST.md:189 — - SYS_CODE_YN=Y는 이름만 변경한다. 신규·삭제·순서·사용·추가값·값/단위 변경은409 SYSTEM_CODE_LOCKED이고 같은 요청의 이름 변경도 롤백한다. “이름만 바꿀 수 있습니다.”를 표시한다.

검증 기준:

- AC-REQ-074: CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다.
- 사례 1: CTRT_TYPE TK 이름만 변경 성공
- 사례 2: 이름+useYn 요청은409·이름과 이력도 변경0건이다.

### REQ-075 — 그룹 중복과 채번 경합 (source_id: null)
<!-- speckit:section sec-58b0b53510ae47529f47ef040dd8fa8f -->

업무 목적: 새 일반 업무 코드 그룹을 만든다.

관찰 가능한 동작: groupId/name 필수·중복ID/trim후 동일명 거부하며 그룹 테이블 직렬화 잠금으로 중복/채번 경합을 막는다.

입력 항목: groupId/name 필수·그룹 추가 전 미저장 편집 상태. SYS/VAL/USE/SORT_ORD는 서버가 정한다.

처리 규칙: groupId/name 필수·중복ID/trim후 동일명 거부하며 그룹 테이블 직렬화 잠금으로 중복/채번 경합을 막는다. 원문 근거: SPEC-REQUEST.md:193 — - [그룹 추가]: groupId/name 필수·3.2 규칙, ID 중복409 DUPLICATE_GROUP, 이름도 trim 후 같은 문자열이면409 DUPLICATE_GROUP_NAME이다. 그룹 추가 중 그룹 테이블을 직렬화 잠금해 이름 중복과 SORT_ORD 채번 경합을 막는다.

검증 기준:

- AC-REQ-075: 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다.
- 사례 1: 동일ID는409 DUPLICATE_GROUP
- 사례 2: 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다.

### REQ-076 — 업무 그룹 생성 (source_id: null)
<!-- speckit:section sec-15857d40a5ca4238bc48296e841b33a6 -->

업무 목적: 새 일반 업무 코드 그룹을 만든다.

관찰 가능한 동작: 새 그룹은 SYS=N·VAL=N·USE=Y·SORT=max+1로 저장하고 NEW 이력 후 선택/재조회한다.

입력 항목: groupId/name 필수·그룹 추가 전 미저장 편집 상태. SYS/VAL/USE/SORT_ORD는 서버가 정한다.

처리 규칙: 새 그룹은 SYS=N·VAL=N·USE=Y·SORT=max+1로 저장하고 NEW 이력 후 선택/재조회한다. 원문 근거: SPEC-REQUEST.md:194 — - 새 그룹은 SYS_CODE_YN=N·VAL_CODE_YN=N·USE_YN=Y·SORT_ORD=max+1이다. DB 저장과 NEW 이력 후 선택·재조회한다. 미저장 코드가 있으면 먼저 저장/취소한다. 임의 추가값 컬럼 정의는 제공하지 않는다.

검증 기준:

- AC-REQ-076: POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다.; api_endpoint:POST /api/code-groups
- 사례 1: POST 후201·DB에 일반 업무 그룹·NEW 이력1개
- 사례 2: UI에서 새 그룹이 선택된다.; api_endpoint:POST /api/code-groups

연결 연산: POST /api/code-groups (createCodeGroup); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-077 — 그룹 추가 전 편집 처리 (source_id: null)
<!-- speckit:section sec-1321ca4453834f5d9a9c7407633fa34c -->

업무 목적: 새 일반 업무 코드 그룹을 만든다.

관찰 가능한 동작: 미저장 코드가 있으면 저장/취소 후 그룹을 추가하며 임의 추가값 컬럼 정의는 제공하지 않는다.

입력 항목: groupId/name 필수·그룹 추가 전 미저장 편집 상태. SYS/VAL/USE/SORT_ORD는 서버가 정한다.

처리 규칙: 미저장 코드가 있으면 저장/취소 후 그룹을 추가하며 임의 추가값 컬럼 정의는 제공하지 않는다. 원문 근거: SPEC-REQUEST.md:194 — - 새 그룹은 SYS_CODE_YN=N·VAL_CODE_YN=N·USE_YN=Y·SORT_ORD=max+1이다. DB 저장과 NEW 이력 후 선택·재조회한다. 미저장 코드가 있으면 먼저 저장/취소한다. 임의 추가값 컬럼 정의는 제공하지 않는다.

검증 기준:

- AC-REQ-077: 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다.
- 사례 1: 계속 작성이면 그룹 추가0건·편집 유지
- 사례 2: 저장/취소 처리 후에만 그룹 추가가 진행된다.

### REQ-078 — 일반 신규 코드 편집 행 (source_id: null)
<!-- speckit:section sec-3667b4765bb142839e48989a02b936da -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 코드 추가창은 code/name/해당 추가값을 검증하고 DB가 아닌 신규 편집 행에만 추가한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 코드 추가창은 code/name/해당 추가값을 검증하고 DB가 아닌 신규 편집 행에만 추가한다. 원문 근거: SPEC-REQUEST.md:195 — - 일반 [코드 추가] 창은 code/name·해당 추가값 필수다. 소문자는 대문자 정규화, 같은 그룹 중복은 DEL_YN=Y도409 DUPLICATE_CODE다. 창 [추가]는 편집 목록의 신규 행으로만 넣고 하단 [저장] 때 DB에 저장한다. 신규 취소는 DB 변경0건이다.

검증 기준:

- AC-REQ-078: 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다.
- 사례 1: 창 추가 뒤 DB0건
- 사례 2: 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다.

### REQ-079 — 그룹 내 코드 PK 재사용 금지 (source_id: null)
<!-- speckit:section sec-7555eadfa0d84d6ea1752e4fb3018ef5 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 같은 그룹 code는 삭제된 행을 포함하여 중복409 DUPLICATE_CODE다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 같은 그룹 code는 삭제된 행을 포함하여 중복409 DUPLICATE_CODE다. 원문 근거: SPEC-REQUEST.md:195 — - 일반 [코드 추가] 창은 code/name·해당 추가값 필수다. 소문자는 대문자 정규화, 같은 그룹 중복은 DEL_YN=Y도409 DUPLICATE_CODE다. 창 [추가]는 편집 목록의 신규 행으로만 넣고 하단 [저장] 때 DB에 저장한다. 신규 취소는 DB 변경0건이다.

검증 기준:

- AC-REQ-079: DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다.
- 사례 1: DEL_YN=Y 기존 PK 재등록은409
- 사례 2: 다른 신규 code는 그룹 저장 시 생성된다.

### REQ-080 — 신규 코드 기본값·정렬 채번 (source_id: null)
<!-- speckit:section sec-3d32077e00824179becc3c77e3d8ae8c -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 새 코드는 USE=Y·DEL=N·그룹 max+1이고 같은 요청 신규는 전달 순서로 채번한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 새 코드는 USE=Y·DEL=N·그룹 max+1이고 같은 요청 신규는 전달 순서로 채번한다. 원문 근거: SPEC-REQUEST.md:196 — - 새 코드는 USE_YN=Y·DEL_YN=N, SORT_ORD는 그룹 내 max+1이며 같은 요청의 신규는 전달 순서다. SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 전체 롤백한다. 저장된 PK는 변경하지 않는다.

검증 기준:

- AC-REQ-080: 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다.
- 추가 경계 사례: 변경 요청의 revision이 최신값과 다르면 409 STALE_WRITE·후행 데이터/이력 변경0건이다.

### REQ-081 — 정렬 순번 한계 (source_id: null)
<!-- speckit:section sec-cf5b7d4351de41e687a5ff9fccf45740 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 그룹 전체 저장을 롤백한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 그룹 전체 저장을 롤백한다. 원문 근거: SPEC-REQUEST.md:196 — - 새 코드는 USE_YN=Y·DEL_YN=N, SORT_ORD는 그룹 내 max+1이며 같은 요청의 신규는 전달 순서다. SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 전체 롤백한다. 저장된 PK는 변경하지 않는다.

검증 기준:

- AC-REQ-081: max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다.
- 추가 경계 사례: 변경 요청의 revision이 최신값과 다르면 409 STALE_WRITE·후행 데이터/이력 변경0건이다.

### REQ-082 — 코드명 중복 허용 (source_id: null)
<!-- speckit:section sec-453889bb057444c8a9dfd0cf5b5d6cb2 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 동일 코드명은 허용하고 그룹ID/코드로 식별한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 동일 코드명은 허용하고 그룹ID/코드로 식별한다. 원문 근거: SPEC-REQUEST.md:197 — - 코드명 중복은 허용하며 그룹ID/코드로 구분한다.

검증 기준:

- AC-REQ-082: 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다.
- 사례 1: 다른 code에 같은 name 저장 성공
- 사례 2: 조회에서 각각의 code로 구별된다.

### REQ-083 — 그룹별 추가값 필수·길이 (source_id: null)
<!-- speckit:section sec-5552e5c3995c4ad0a26ad017aad6ed51 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 적용 그룹 추가값은 빈칸 불가·정규화 후100자 이하이고 다른 그룹 ADD_VAL은 NULL·ADD_VAL3은 미사용이다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 적용 그룹 추가값은 빈칸 불가·정규화 후100자 이하이고 다른 그룹 ADD_VAL은 NULL·ADD_VAL3은 미사용이다. 원문 근거: SPEC-REQUEST.md:201 — 추가·수정 시 아래 필드는 빈칸 불가, 정규화 후 각각100자 이하다. 다른 그룹의 ADD_VAL은 NULL, ADD_VAL3은 이번에 사용하지 않는다. 알려진 다른 그룹 필드를 보내면400이다.

검증 기준:

- AC-REQ-083: 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다.
- 사례 1: 필수 추가값 공백/101자는400
- 사례 2: 다른 그룹 필드 주입은400이다.

### REQ-084 — PROJ_STEP 진척 정수 (source_id: null)
<!-- speckit:section sec-5da75640aff54700a45ba5f7e3aae3d5 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: PROJ_STEP.ADD_VAL1은 0~100 포함 정수이고 ADD_VAL2는 CTRT_TYPE CSV다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: PROJ_STEP.ADD_VAL1은 0~100 포함 정수이고 ADD_VAL2는 CTRT_TYPE CSV다. 원문 근거: SPEC-REQUEST.md:207 — |PROJ_STEP|0~100 포함 정수·소수 거부|CTRT_TYPE 코드 CSV|

검증 기준:

- AC-REQ-084: 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다.
- 사례 1: 0과100은 허용
- 사례 2: 소수 또는101은400이며 전체 요청이 롤백된다.

### REQ-085 — BANK 코드와 RSM 라벨 (source_id: null)
<!-- speckit:section sec-060d99b445384ae4b23c1a57e0a64f7a -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: BANK.ADD_VAL1은 정확한3자리 숫자로 앞0을 보존한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: BANK.ADD_VAL1은 정확한3자리 숫자로 앞0을 보존한다. 원문 근거: SPEC-REQUEST.md:208 — |BANK|정확한 숫자3자리·앞0 보존|없음|

검증 기준:

- AC-REQ-085: 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다.
- 사례 1: 3자리 앞0 값은 그대로 재조회
- 사례 2: 2자리 또는 비숫자는400이다.

### REQ-086 — RSM_LABEL 허용값 (source_id: null)
<!-- speckit:section sec-c5158acb1d714d88b4b058dfd57a0d2d -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: RSM_LABEL.ADD_VAL1은 명시8개 값 중 하나를 대문자 정규화해 저장한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: RSM_LABEL.ADD_VAL1은 명시8개 값 중 하나를 대문자 정규화해 저장한다. 원문 근거: SPEC-REQUEST.md:209 — |RSM_LABEL|NAME/TEL/MAIL/ADDR/SKILL/WANT/FROM/VNDR 중1개·대문자 정규화|없음|

검증 기준:

- AC-REQ-086: name 입력은 NAME으로 저장; 목록 외 문자열은400이다.
- 사례 1: name 입력은 NAME으로 저장
- 사례 2: 목록 외 문자열은400이다.

### REQ-087 — CSV 정규화·참조 (source_id: null)
<!-- speckit:section sec-d36695e3a33746329895dcb415a26c87 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: CSV trim/대문자/빈항목 거부/첫 순서 중복제거 후 실제 미삭제·신규 active 참조를 최종 상태로 검사한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: CSV trim/대문자/빈항목 거부/첫 순서 중복제거 후 실제 미삭제·신규 active 참조를 최종 상태로 검사한다. 원문 근거: SPEC-REQUEST.md:212 — CSV는 쉼표 분리→항목trim/대문자→빈 항목 거부→첫 등장 순서로 중복 제거→쉼표 결합이다. 최소1개, 실제 코드 존재·미삭제·신규 참조는 USE_YN=Y를 검사한다. JOB 자기 자신 포함은 허용하며 필수는 아니다. 같은 저장 요청에서 새로 추가되는 JOB 참조도 최종 상태로 검사한다. 기존 중지 참조를 그대로 유지하는 것은 허용한다.

검증 기준:

- AC-REQ-087: 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다.
- 사례 1: 중복 CSV는 첫 등장 순서만 남음
- 사례 2: 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다.

### REQ-088 — 수당 코드 조회·편집 형태 (source_id: null)
<!-- speckit:section sec-353a23aecd624cc7b52f978e648477e5 -->

업무 목적: 수당 기준 코드를 정밀하게 추가·변경한다.

관찰 가능한 동작: 값 코드 그룹은 코드/이름/값/단위4열이며 기존 값만 편집한다.

입력 항목: VAL_CODE_YN 그룹, code/name/codeVal/unitNm·expectedRevision; 기존 일반 값 코드는 codeVal만 변경한다.

처리 규칙: 값 코드 그룹은 코드/이름/값/단위4열이며 기존 값만 편집한다. 원문 근거: SPEC-REQUEST.md:216 — - VAL_CODE_YN=Y 그룹은 **코드/이름/값/단위**4열이다. 기존은 값만 편집, 코드·이름·단위는 읽기 전용이다. 사용/삭제/순서 작업은 없다. API의 기존 값 이외 필드 변경·삭제는409 VALUE_CODE_FIELD_LOCKED다.

검증 기준:

- AC-REQ-088: 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다.

### REQ-089 — 값 코드 잠금 (source_id: null)
<!-- speckit:section sec-f8954da3396d4b00ae93d68cec897e93 -->

업무 목적: 수당 기준 코드를 정밀하게 추가·변경한다.

관찰 가능한 동작: 기존 값 이외 변경/삭제는409 VALUE_CODE_FIELD_LOCKED이며 시스템 값 그룹은 시스템 규칙이 우선이다.

입력 항목: VAL_CODE_YN 그룹, code/name/codeVal/unitNm·expectedRevision; 기존 일반 값 코드는 codeVal만 변경한다.

처리 규칙: 기존 값 이외 변경/삭제는409 VALUE_CODE_FIELD_LOCKED이며 시스템 값 그룹은 시스템 규칙이 우선이다. 원문 근거: SPEC-REQUEST.md:216 — - VAL_CODE_YN=Y 그룹은 **코드/이름/값/단위**4열이다. 기존은 값만 편집, 코드·이름·단위는 읽기 전용이다. 사용/삭제/순서 작업은 없다. API의 기존 값 이외 필드 변경·삭제는409 VALUE_CODE_FIELD_LOCKED다.

검증 기준:

- AC-REQ-089: 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다.
- 사례 1: 일반 값 코드 단위 변경은409
- 사례 2: 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다.

### REQ-090 — 수당 신규 코드 저장 (source_id: null)
<!-- speckit:section sec-28c8858f8d234a0088c8c4fe8d273be8 -->

업무 목적: 수당 기준 코드를 정밀하게 추가·변경한다.

관찰 가능한 동작: 수당코드 추가는 code/name 필수·값 기본/빈0·unitNm 원/% 기본원이며 그룹 저장 때 반영한다.

입력 항목: VAL_CODE_YN 그룹, code/name/codeVal/unitNm·expectedRevision; 기존 일반 값 코드는 codeVal만 변경한다.

처리 규칙: 수당코드 추가는 code/name 필수·값 기본/빈0·unitNm 원/% 기본원이며 그룹 저장 때 반영한다. 원문 근거: SPEC-REQUEST.md:217 — - [수당코드 추가]는 코드·이름 필수, 값 기본0·비우면0, 단위는 원/% 중 선택·기본원이다. 3.2의 CODE_VAL 정밀도·코드 규칙을 적용한다. 추가 창은 신규 편집 행, 하단 저장은 그룹 단위 트랜잭션이다.

검증 기준:

- AC-REQ-090: 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다.
- 사례 1: 추가창 완료만으로 DB0건
- 사례 2: 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다.

### REQ-091 — 수당 값 변경과 업무 불변 (source_id: null)
<!-- speckit:section sec-de9739ae9b524bc49f1be0f39b5b0c09 -->

업무 목적: 수당 기준 코드를 정밀하게 추가·변경한다.

관찰 가능한 동작: 초기 수당은 SQL 값을 읽고 변경 후 공통 조회 값/단위만 갱신하며 업무 테이블은 바꾸지 않는다.

입력 항목: VAL_CODE_YN 그룹, code/name/codeVal/unitNm·expectedRevision; 기존 일반 값 코드는 codeVal만 변경한다.

처리 규칙: 초기 수당은 SQL 값을 읽고 변경 후 공통 조회 값/단위만 갱신하며 업무 테이블은 바꾸지 않는다. 원문 근거: SPEC-REQUEST.md:218 — - 초기 ALWNC6개는 ALLOW_TEAM=200000원, ALLOW_GROUP=400000원, INS_RATE=9.4%, SEV_RATE=8.33%, TEAM_FEE/PEXP_FEE=50000원이며 실제 값은 SQL에서 읽는다. 저장 후 공통 조회의 값/단위가 바뀌고 업무 테이블 값은 그대로다. 급여·보험·원가 계산 엔진은 생성하지 않는다.

검증 기준:

- AC-REQ-091: ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다.
- 사례 1: ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치
- 사례 2: 값 변경 후 프로젝트 팀비/경비 변경0건이다.

### REQ-092 — 일반 코드 중지·재개 (source_id: null)
<!-- speckit:section sec-d358d882f81d4a3bb6e29eb3d55d1612 -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: USE_YN N/Y 저장은 STOP/RESUME이며 active 선택 제외와 기존 label 조회를 구분한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: USE_YN N/Y 저장은 STOP/RESUME이며 active 선택 제외와 기존 label 조회를 구분한다. 원문 근거: SPEC-REQUEST.md:222 — - 일반 업무 코드의 USE_YN 체크를 N/Y로 바꿔 저장하면 STOP/RESUME 이력이다. 중지 코드는 신규 옵션에서 제외하고 기존 이름 조회는 가능하다. 다른 테이블의 저장 코드를 수정한 건수는0건이다.

검증 기준:

- AC-REQ-092: 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다.
- 사례 1: 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능
- 사례 2: 재개 후 active 반환·다른 업무 저장코드 변경0건이다.

### REQ-093 — 사용 상태 변경 이력 (source_id: null)
<!-- speckit:section sec-43f396da4cb44f4da336fc321d9b94aa -->

업무 목적: 선택 그룹의 허용 코드 속성을 정확히 저장한다.

관찰 가능한 동작: 일반 업무 코드 중지·재개 시 STOP/RESUME 이력을 기록한다.

입력 항목: group, groupRevision→expectedRevision, changes의 code/isNew와 모드별 name/useYn/addVal1/addVal2/codeVal/unitNm; 등록 PK는 불변.

처리 규칙: 일반 업무 코드 중지·재개 시 STOP/RESUME 이력을 기록한다. 원문 근거: SPEC-REQUEST.md:222 — - 일반 업무 코드의 USE_YN 체크를 N/Y로 바꿔 저장하면 STOP/RESUME 이력이다. 중지 코드는 신규 옵션에서 제외하고 기존 이름 조회는 가능하다. 다른 테이블의 저장 코드를 수정한 건수는0건이다.

검증 기준:

- AC-REQ-093: USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다.
- 추가 경계 사례: 변경 요청의 revision이 최신값과 다르면 409 STALE_WRITE·후행 데이터/이력 변경0건이다.

### REQ-094 — 삭제 참조 차단 (source_id: null)
<!-- speckit:section sec-637334bfd87746a0972febbe20129d22 -->

업무 목적: 사용되지 않는 일반 코드만 목록에서 제거한다.

관찰 가능한 동작: 삭제 전 편집 처리·확인 후 참조가1건이라도 있으면409 CODE_IN_USE와 유형/건수/안내로 거부한다.

입력 항목: group/code·expectedRevision·고정 매핑과 TB_SY_CODE_MAP 및 CSV/과거명 참조·삭제 확인.

처리 규칙: 삭제 전 편집 처리·확인 후 참조가1건이라도 있으면409 CODE_IN_USE와 유형/건수/안내로 거부한다. 원문 근거: SPEC-REQUEST.md:223 — - 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다.

검증 기준:

- AC-REQ-094: 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다.
- 사례 1: 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지
- 사례 2: 화면은 사용을 끄세요 안내를 보여준다.

### REQ-095 — 일반 코드 논리 삭제 (source_id: null)
<!-- speckit:section sec-471c4c5f25ca4e419d27845484913d6e -->

업무 목적: 사용되지 않는 일반 코드만 목록에서 제거한다.

관찰 가능한 동작: 미참조 일반 업무 코드는 DEL_YN=Y로 논리 삭제하고 물리행/PK를 보존한다.

입력 항목: group/code·expectedRevision·고정 매핑과 TB_SY_CODE_MAP 및 CSV/과거명 참조·삭제 확인.

처리 규칙: 미참조 일반 업무 코드는 DEL_YN=Y로 논리 삭제하고 물리행/PK를 보존한다. 원문 근거: SPEC-REQUEST.md:223 — - 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다.

검증 기준:

- AC-REQ-095: 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다.; api_endpoint:DELETE /api/code-groups/{group}/codes/{code}
- 사례 1: 미참조 삭제 후 DEL_YN=Y·물리행 존속
- 사례 2: 같은 PK 추가는409 DUPLICATE_CODE다.; api_endpoint:DELETE /api/code-groups/{group}/codes/{code}

연결 연산: DELETE /api/code-groups/{group}/codes/{code} (deleteGroupCode); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-096 — 코드 삭제 이력 (source_id: null)
<!-- speckit:section sec-6fec3326914749438a07534ceb5f5cc2 -->

업무 목적: 사용되지 않는 일반 코드만 목록에서 제거한다.

관찰 가능한 동작: 일반 코드 논리 삭제 시 DEL 이력을 남긴다.

입력 항목: group/code·expectedRevision·고정 매핑과 TB_SY_CODE_MAP 및 CSV/과거명 참조·삭제 확인.

처리 규칙: 일반 코드 논리 삭제 시 DEL 이력을 남긴다. 원문 근거: SPEC-REQUEST.md:223 — - 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다.

검증 기준:

- AC-REQ-096: 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다.
- 추가 경계 사례: 변경 요청의 revision이 최신값과 다르면 409 STALE_WRITE·후행 데이터/이력 변경0건이다.

### REQ-097 — 고정 참조 가드 (source_id: null)
<!-- speckit:section sec-0ac942ee40ce40e6801585dc24325ec2 -->

업무 목적: 사용되지 않는 일반 코드만 목록에서 제거한다.

관찰 가능한 동작: CodeReferenceGuard는 고정 매핑과 공통 대응을 검사하며 취소·중지·마감 참조도 세고 자기 NEW 이력은 제외한다.

입력 항목: group/code·expectedRevision·고정 매핑과 TB_SY_CODE_MAP 및 CSV/과거명 참조·삭제 확인.

처리 규칙: CodeReferenceGuard는 고정 매핑과 공통 대응을 검사하며 취소·중지·마감 참조도 세고 자기 NEW 이력은 제외한다. 원문 근거: SPEC-REQUEST.md:224 — - CodeReferenceGuard는 다음 고정 매핑을 사용한다. 취소·중지·마감 자료도 검사하며 코드 자신의 NEW 이력은 사용으로 세지 않는다. 모든 그룹은 추가로 TB_SY_CODE_MAP의 group/code 참조를 검사한다. 요청의 테이블명으로 SQL을 만들지 않는다.

검증 기준:

- AC-REQ-097: 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다.
- 사례 1: 각 매핑 fixture의 참조1건은 삭제 차단
- 사례 2: 코드 자체 NEW 이력만 있으면 삭제 허용이다.

### REQ-098 — 삭제 직전 재검사 (source_id: null)
<!-- speckit:section sec-4e2dba661c3f493298b9f7be02c6c0da -->

업무 목적: 사용되지 않는 일반 코드만 목록에서 제거한다.

관찰 가능한 동작: 삭제 직전 대상 그룹/행을 잠그고 실제 참조를 재검사하며 ALWNC는 삭제 대상에서 제외한다.

입력 항목: group/code·expectedRevision·고정 매핑과 TB_SY_CODE_MAP 및 CSV/과거명 참조·삭제 확인.

처리 규칙: 삭제 직전 대상 그룹/행을 잠그고 실제 참조를 재검사하며 ALWNC는 삭제 대상에서 제외한다. 원문 근거: SPEC-REQUEST.md:239 — ALWNC는5.4의 값 코드라 삭제 대상이 아니다. 참조 목록은 서버 코드에 명시하고 실제 사용을 fixture로 확인한다. 삭제 직전 대상 그룹/행을 잠그고 재검사한다.

검증 기준:

- AC-REQ-098: 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다.
- 사례 1: 잠금 후 생긴 참조 fixture는409로 보호
- 사례 2: ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다.

### REQ-099 — 운영 기준 전체 조회 (source_id: null)
<!-- speckit:section sec-717a9b75b0a6457ea5d22ab2ffc458a9 -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: SQL43키를 STD_CD순으로 읽어 이름/값/단위/적용월/설명/값변경/이력을 표시한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: SQL43키를 STD_CD순으로 읽어 이름/값/단위/적용월/설명/값변경/이력을 표시한다. 원문 근거: SPEC-REQUEST.md:245 — - [운영 기준값]은 SQL의43키 전체를 읽어 이름·값·단위·적용 시작월·STD_DESC·[값 변경]/[이력]을 표시한다. 키·이름·단위·설명은 읽기 전용이고 신규 키/삭제 기능은 없다. 목록은 STD_CD순이다.

검증 기준:

- AC-REQ-099: 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다.; api_endpoint:GET /api/operating-standards
- 사례 1: 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다.
- 사례 2: api_endpoint:GET /api/operating-standards

연결 연산: GET /api/operating-standards (getOperatingStandards); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-100 — 기준 현재·과거·미래 이력 (source_id: null)
<!-- speckit:section sec-db38e83d35d64e0db33e4b56fc7322bb -->

업무 목적: 현재·예정·지난 적용값을 구분한다.

관찰 가능한 동작: 현재값은 현재월 이하 최신행이고 이력은 적용월 DESC·현재/예정/지난 상태로 보여준다.

입력 항목: key, 현재월, 해당 key 전체 적용월 행과 revision.

처리 규칙: 현재값은 현재월 이하 최신행이고 이력은 적용월 DESC·현재/예정/지난 상태로 보여준다. 원문 근거: SPEC-REQUEST.md:246 — - 현재값은 APLY_BGN_YM≤현재월 중 가장 최근 행, 이력은 모든 적용월 DESC·현재/예정/지난 상태다. 미래 값은 현재값으로 사용하지 않는다. 해당월 기준이 없으면 ‘기준값 없음’으로 표시하며 임의 기본값 대입은0회다.

검증 기준:

- AC-REQ-100: 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다.; api_endpoint:GET /api/operating-standards/{key}/history
- 사례 1: 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다.
- 사례 2: api_endpoint:GET /api/operating-standards/{key}/history

연결 연산: GET /api/operating-standards/{key}/history (getOperatingStandardHistory); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-101 — 일반 기준 적용 최소월 (source_id: null)
<!-- speckit:section sec-74c10fb50bbe4ea295116dee9f98022f -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: value/effectiveMonth를 필수로 받아 최소월=max(시작월,마지막 CLOSE+1) 전은409 CLOSED_MONTH다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: value/effectiveMonth를 필수로 받아 최소월=max(시작월,마지막 CLOSE+1) 전은409 CLOSED_MONTH다. 원문 근거: SPEC-REQUEST.md:247 — - 일반 값 변경은 value/effectiveMonth 필수다. 최소월은 max(CASH_BASE_YM, 마지막 CLOSE월+1), CLOSE가 없으면 CASH_BASE_YM이다. 기본월은 max(현재월,최소월)이다. 최소월 전은409 CLOSED_MONTH다.

검증 기준:

- AC-REQ-101: 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다.
- 사례 1: 최소월 저장은 허용
- 사례 2: 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다.

### REQ-102 — 기준 월별 추가·덮어쓰기 (source_id: null)
<!-- speckit:section sec-520e25402b5242e5baea093e2b007e8e -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: 같은 적용월은 전후 확인 후 UPDATE·없으면 INSERT하며 다른월은 보존하고 키 전체 이력 revision을 비교한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: 같은 적용월은 전후 확인 후 UPDATE·없으면 INSERT하며 다른월은 보존하고 키 전체 이력 revision을 비교한다. 원문 근거: SPEC-REQUEST.md:248 — - 같은 적용월 행이 있으면 덮어쓸 월/전후를 확인한 뒤 expectedRevision으로 UPDATE, 없으면 INSERT한다. 다른 적용월 행은 유지한다. 한 요청의 변경 키는 중복 불가이며 미변경 칸을 재저장하지 않는다. 키별 전체 이력 revision을 잠금 후 비교한다.

검증 기준:

- AC-REQ-102: 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다.; api_endpoint:PUT /api/operating-standards
- 사례 1: 덮어쓰기 후 해당월 값만 변경
- 사례 2: 신규월 저장은 새행이며 다른 적용월 행은 동일하다.; api_endpoint:PUT /api/operating-standards

연결 연산: PUT /api/operating-standards (saveOperatingStandards); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-103 — 기준 변경 키 중복 거부 (source_id: null)
<!-- speckit:section sec-390fa08de37740dcaf3855a484ffa168 -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: 한 요청 키 중복을 거부하고 미변경 칸은 재저장하지 않는다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: 한 요청 키 중복을 거부하고 미변경 칸은 재저장하지 않는다. 원문 근거: SPEC-REQUEST.md:248 — - 같은 적용월 행이 있으면 덮어쓸 월/전후를 확인한 뒤 expectedRevision으로 UPDATE, 없으면 INSERT한다. 다른 적용월 행은 유지한다. 한 요청의 변경 키는 중복 불가이며 미변경 칸을 재저장하지 않는다. 키별 전체 이력 revision을 잠금 후 비교한다.

검증 기준:

- AC-REQ-103: 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다.
- 사례 1: 같은 key 중복 요청은400·전체 변경0건
- 사례 2: 미변경 키의 시각/이력도 변하지 않는다.

### REQ-104 — 묶음 기준 표시 (source_id: null)
<!-- speckit:section sec-b2d7644c8bc44c5da45afca59239dd0f -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: DUN/EXT_PRBL/STEP_PRBL/BILL_MISS/AI_SCORE를 지정 묶음·최대4입력/줄로 표시한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: DUN/EXT_PRBL/STEP_PRBL/BILL_MISS/AI_SCORE를 지정 묶음·최대4입력/줄로 표시한다. 원문 근거: SPEC-REQUEST.md:249 — - 묶음 줄은 DUN_D1/2/3, EXT_PRBL_H/M/L/NULL, STEP_PRBL_1/2/3/4, BILL_MISS_RT/MIN, AI_SCORE_JOB/SKILL/CUST/GRADE/PERF/COST/AVAIL/LOC다. 최대4입력/줄·AI8개는2줄이며 저장은 각 STD_CD 행이다. 변경 칸만 같은 적용월로 한 요청에 보낸다.

검증 기준:

- AC-REQ-104: AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다.

### REQ-105 — DUN 순서 검증 (source_id: null)
<!-- speckit:section sec-2905e160c70d49aa9d06d9e2dd7c3110 -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: DUN_D1≥1 및 D1<D2<D3를 BigDecimal 일반 정밀도 안에서 검사한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: DUN_D1≥1 및 D1<D2<D3를 BigDecimal 일반 정밀도 안에서 검사한다. 원문 근거: SPEC-REQUEST.md:256 — |DUN_D1/2/3|D1≥1, D1<D2<D3. 일반 최대4소수 범위 적용|

검증 기준:

- AC-REQ-105: D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다.
- 사례 1: D1이1 미만이면400
- 사례 2: D1≥D2 또는 D2≥D3이면400·전체 롤백이다.

### REQ-106 — 확률 범위 검증 (source_id: null)
<!-- speckit:section sec-c2218228d3e94d3a8d9ed4900fb02cba -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: EXT_PRBL·STEP_PRBL은 각각0~100 포함만 허용한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: EXT_PRBL·STEP_PRBL은 각각0~100 포함만 허용한다. 원문 근거: SPEC-REQUEST.md:257 — |EXT_PRBL·STEP_PRBL|각0~100 포함|

검증 기준:

- AC-REQ-106: 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다.
- 사례 1: 0/100 허용
- 사례 2: 100 초과는400이며 키 묶음 전체가 변경되지 않는다.

### REQ-107 — AI 점수 합계 검증 (source_id: null)
<!-- speckit:section sec-1f334b871ba444c6822dca871c46bf7a -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: AI_SCORE8개는 각0~100·합계 정확히100을 BigDecimal로 비교한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: AI_SCORE8개는 각0~100·합계 정확히100을 BigDecimal로 비교한다. 원문 근거: SPEC-REQUEST.md:258 — |AI_SCORE8개|각0~100·합계정확히100. BigDecimal 합으로 비교|

검증 기준:

- AC-REQ-107: 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다.
- 사례 1: 합99/101은400·전체롤백
- 사례 2: 합100은 저장되고 재조회 합100이다.

### REQ-108 — 철수 통보일 정수 (source_id: null)
<!-- speckit:section sec-39a6e7b2a8194a079d1cd54e908dbb02 -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: WTHDR_NOTI_D는1 이상·최대99,999,999,999 정수다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: WTHDR_NOTI_D는1 이상·최대99,999,999,999 정수다. 원문 근거: SPEC-REQUEST.md:259 — |WTHDR_NOTI_D|1 이상 정수·최대99,999,999,999|

검증 기준:

- AC-REQ-108: 1과99,999,999,999 허용; 0 또는 소수는400이다.
- 사례 1: 1과99,999,999,999 허용
- 사례 2: 0 또는 소수는400이다.

### REQ-109 — 자금 시작 연월 표현 (source_id: null)
<!-- speckit:section sec-da6cf57ab49e4312a6e50ea4672eb811 -->

업무 목적: 미마감 초기 자금 기준만 즉시 수정한다.

관찰 가능한 동작: CASH_BASE_YM 화면은 YYYY-MM·API value는 YYYYMM6자리·DB는 정수로 보존한다.

입력 항목: CASH_BASE_YM/CASH_BASE_AMT의 value/expectedRevision·기존 시작월·TB_CL_CLOSE; effectiveMonth는 보내지 않는다.

처리 규칙: CASH_BASE_YM 화면은 YYYY-MM·API value는 YYYYMM6자리·DB는 정수로 보존한다. 원문 근거: SPEC-REQUEST.md:260 — |CASH_BASE_YM|화면 YYYY-MM, API value는 YYYYMM6자리 문자열. 실제 연월·소수 없음. DB는 정수, 읽을 때6자리로 복원|

검증 기준:

- AC-REQ-109: 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다.
- 사례 1: 2026-01 화면은 API 202601·DB 정수로 저장
- 사례 2: 월00/13 또는 소수는400이다.

### REQ-110 — 기준 전체 영향구간 검사 (source_id: null)
<!-- speckit:section sec-e2243fd24dd9486bbe4ab22702e03a4c -->

업무 목적: 유효 적용월의 운영 기준값을 일관되게 변경한다.

관찰 가능한 동작: 묶음 미변경키도 잠그고 해당월·영향구간·이후 예정 경계에서 합/순서/범위를 검사한다.

입력 항목: changes:[{key,value,expectedRevision}], 일반 기준 effectiveMonth와 전체 묶음/예정 경계값.

처리 규칙: 묶음 미변경키도 잠그고 해당월·영향구간·이후 예정 경계에서 합/순서/범위를 검사한다. 원문 근거: SPEC-REQUEST.md:263 — 묶음은 미변경 키까지 같은 트랜잭션에서 잠그고 변경값과 해당 적용월의 다른 유효값을 합쳐 검사한다. 새 이력으로 영향받는 구간과 이후 예정 기준의 경계월에서도 합100/순서/범위를 검사하고 한 경계라도 위반하면400·전체 롤백이다. 단위나 기준명으로 추가 업무 상한을 추정하지 않는다.

검증 기준:

- AC-REQ-110: 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다.
- 사례 1: 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백
- 사례 2: 전 경계 유효이면 commit된다.

### REQ-111 — 자금 두 키 즉시 갱신 (source_id: null)
<!-- speckit:section sec-581e9ef348534e89b4874ce45d5bd1c5 -->

업무 목적: 미마감 초기 자금 기준만 즉시 수정한다.

관찰 가능한 동작: CASH_BASE_YM/AMT는 키당1행·별도요청·effectiveMonth 없이 즉시 UPDATE하고 원래 적용월을 유지한다.

입력 항목: CASH_BASE_YM/CASH_BASE_AMT의 value/expectedRevision·기존 시작월·TB_CL_CLOSE; effectiveMonth는 보내지 않는다.

처리 규칙: CASH_BASE_YM/AMT는 키당1행·별도요청·effectiveMonth 없이 즉시 UPDATE하고 원래 적용월을 유지한다. 원문 근거: SPEC-REQUEST.md:267 — - 두 키는 키마다1행을 유지한다. 적용 시작월 입력 없이 별도 저장 요청으로 즉시 UPDATE, 원래 APLY_BGN_YM은 보존하고 UPD 이력을 남긴다. 일반 이력 요청과 섞으면400이다.

검증 기준:

- AC-REQ-111: 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다.
- 사례 1: 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일
- 사례 2: 일반 이력 요청과 혼합은400이다.

### REQ-112 — 자금 기준 변경 이력 (source_id: null)
<!-- speckit:section sec-67beadf076534e8cb8c4a6772af0556d -->

업무 목적: 미마감 초기 자금 기준만 즉시 수정한다.

관찰 가능한 동작: 자금 두 키 변경은 UPD 이력을 기록한다.

입력 항목: CASH_BASE_YM/CASH_BASE_AMT의 value/expectedRevision·기존 시작월·TB_CL_CLOSE; effectiveMonth는 보내지 않는다.

처리 규칙: 자금 두 키 변경은 UPD 이력을 기록한다. 원문 근거: SPEC-REQUEST.md:267 — - 두 키는 키마다1행을 유지한다. 적용 시작월 입력 없이 별도 저장 요청으로 즉시 UPDATE, 원래 APLY_BGN_YM은 보존하고 UPD 이력을 남긴다. 일반 이력 요청과 섞으면400이다.

검증 기준:

- AC-REQ-112: 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다.
- 사례 1: 성공한 실제 값 변경은 UPD 전후값으로 조회
- 사례 2: 미변경값은 이력0건이다.

### REQ-113 — 기존 시작월 마감 잠금 (source_id: null)
<!-- speckit:section sec-93d80aa3cc774117bbc0f56c49183ab1 -->

업무 목적: 미마감 초기 자금 기준만 즉시 수정한다.

관찰 가능한 동작: 기존 CASH_BASE_YM이 CLOSE이면 두 키 모두 읽기 전용·409 CLOSED_MONTH로 변경을 막는다.

입력 항목: CASH_BASE_YM/CASH_BASE_AMT의 value/expectedRevision·기존 시작월·TB_CL_CLOSE; effectiveMonth는 보내지 않는다.

처리 규칙: 기존 CASH_BASE_YM이 CLOSE이면 두 키 모두 읽기 전용·409 CLOSED_MONTH로 변경을 막는다. 원문 근거: SPEC-REQUEST.md:268 — - 저장 전 **기존 CASH_BASE_YM**이 TB_CL_CLOSE에서 CLOSE인지 검사한다. 마감됐으면 두 키 모두 읽기 전용·설명·API409 CLOSED_MONTH다. 시작월을 먼저 바꿔 잠금을 우회하지 못한다.

검증 기준:

- AC-REQ-113: 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다.
- 사례 1: 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변
- 사례 2: 기존 미마감이면 저장 허용이다.

### REQ-114 — 자금 동시 잠금·후속 갱신 (source_id: null)
<!-- speckit:section sec-2a4815a4bad641d0972373e8fb3911af -->

업무 목적: 미마감 초기 자금 기준만 즉시 수정한다.

관찰 가능한 동작: 두행 동시 잠금 후 기존 시작월 재검사하고 저장 후 period-context/기준조회만 갱신한다.

입력 항목: CASH_BASE_YM/CASH_BASE_AMT의 value/expectedRevision·기존 시작월·TB_CL_CLOSE; effectiveMonth는 보내지 않는다.

처리 규칙: 두행 동시 잠금 후 기존 시작월 재검사하고 저장 후 period-context/기준조회만 갱신한다. 원문 근거: SPEC-REQUEST.md:269 — - 두 행을 함께 잠그고 기존 시작월로 재검사한다. 미마감일 때만 새 연월/잔액을 저장한다. 저장 후 period-context·기준 조회를 갱신하며 자금수지 계산·마감 자료 변경은0건이다.

검증 기준:

- AC-REQ-114: 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다.
- 사례 1: 정상 저장 후 기간 범위와 기준 재조회가 최신
- 사례 2: 자금수지 계산·마감 자료 변경0건이다.

### REQ-115 — 표기 대응 목록·검색 (source_id: null)
<!-- speckit:section sec-3c3c158cd47647e4932a9f510cffa9f8 -->

업무 목적: 등록 표기를 그룹과 문자열로 찾아 확인한다.

관찰 가능한 동작: 대응을 sourceText 부분일치/groupId로 필터하고 원문 순서·페이지로 조회한다.

입력 항목: sourceText 부분일치/groupId/page/size.

처리 규칙: 대응을 sourceText 부분일치/groupId로 필터하고 원문 순서·페이지로 조회한다. 원문 근거: SPEC-REQUEST.md:275 — - 표 열은 선택·엑셀 표기·그룹·바꿀 코드(코드/이름), 정렬SRC_TEXT/CODE_GRP_ID/MAP_ID순, 기본50·필터sourceText부분일치/groupId다. 빈 상태는 “등록된 표기가 없습니다.”다.

검증 기준:

- AC-REQ-115: SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다.; api_endpoint:GET /api/code-mappings
- 사례 1: SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다.
- 사례 2: api_endpoint:GET /api/code-mappings

연결 연산: GET /api/code-mappings (getCodeMappings); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-116 — 대응 입력·중복 검사 (source_id: null)
<!-- speckit:section sec-4e978880166144078637f8a899487f87 -->

업무 목적: 원문 표기를 active 코드와 연결한다.

관찰 가능한 동작: trim 1~100자 sourceText·실재 groupId/active code를 검사하고 UK(sourceText,groupId)를 보호한다.

입력 항목: trim sourceText 1~100자·실제 groupId/active code; 등록 시 처리자는 서버 admin.

처리 규칙: trim 1~100자 sourceText·실재 groupId/active code를 검사하고 UK(sourceText,groupId)를 보호한다. 원문 근거: SPEC-REQUEST.md:276 — - [추가]의 sourceText는trim 후1~100자, groupId/code 필수·실재 그룹과 active 코드만 허용한다. UK(sourceText,groupId) 중복은409 DUPLICATE_MAPPING이고 다른 코드여도 중복이다. 다른 그룹의 같은 표기는 허용한다. 주/보조·메모·사용 여부·적용일 컬럼은 추가하지 않는다.

검증 기준:

- AC-REQ-116: 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다.
- 사례 1: 동일표기+동일그룹은409 DUPLICATE_MAPPING
- 사례 2: 같은표기+다른그룹은 허용된다.

### REQ-117 — 대응 즉시 등록 (source_id: null)
<!-- speckit:section sec-92c29cb2ddf44b5589ff72858a23d7d1 -->

업무 목적: 원문 표기를 active 코드와 연결한다.

관찰 가능한 동작: 대응 추가는 즉시 DB에 저장하고 수정 대신 삭제 후 재등록한다.

입력 항목: trim sourceText 1~100자·실제 groupId/active code; 등록 시 처리자는 서버 admin.

처리 규칙: 대응 추가는 즉시 DB에 저장하고 수정 대신 삭제 후 재등록한다. 원문 근거: SPEC-REQUEST.md:277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다.

검증 기준:

- AC-REQ-117: POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다.; api_endpoint:POST /api/code-mappings
- 사례 1: POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다.
- 사례 2: api_endpoint:POST /api/code-mappings

연결 연산: POST /api/code-mappings (createCodeMapping); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-118 — 대응 등록·삭제 이력 (source_id: null)
<!-- speckit:section sec-907ff48b7f76493ebbf0a217a03ef4e5 -->

업무 목적: 원문 표기를 active 코드와 연결한다.

관찰 가능한 동작: 대응 NEW/DEL은 삭제 전후 식별내용을 이력에 남겨 물리삭제 후에도 식별한다.

입력 항목: trim sourceText 1~100자·실제 groupId/active code; 등록 시 처리자는 서버 admin.

처리 규칙: 대응 NEW/DEL은 삭제 전후 식별내용을 이력에 남겨 물리삭제 후에도 식별한다. 원문 근거: SPEC-REQUEST.md:277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다.

검증 기준:

- AC-REQ-118: NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-119 — 대응 다중 물리 삭제 (source_id: null)
<!-- speckit:section sec-62ccc7f67a784b2ea9a7132659f943da -->

업무 목적: 선택한 표기 대응을 이력을 남겨 제거한다.

관찰 가능한 동작: 선택 확인 후 여러 대응을 한 트랜잭션으로 DELETE한다.

입력 항목: items:[{mapId,expectedRevision}]·다중 선택 확인.

처리 규칙: 선택 확인 후 여러 대응을 한 트랜잭션으로 DELETE한다. 원문 근거: SPEC-REQUEST.md:277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다.

검증 기준:

- AC-REQ-119: 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다.; api_endpoint:POST /api/code-mappings/delete
- 사례 1: 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다.
- 사례 2: api_endpoint:POST /api/code-mappings/delete

연결 연산: POST /api/code-mappings/delete (deleteCodeMappings); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-120 — 대응 삭제 충돌 (source_id: null)
<!-- speckit:section sec-413b51fca930410f870c2c1e0cfdb2aa -->

업무 목적: 선택한 표기 대응을 이력을 남겨 제거한다.

관찰 가능한 동작: 삭제 목록에 누락/변경 행이 하나라도 있으면409로 전체 삭제를 롤백한다.

입력 항목: items:[{mapId,expectedRevision}]·다중 선택 확인.

처리 규칙: 삭제 목록에 누락/변경 행이 하나라도 있으면409로 전체 삭제를 롤백한다. 원문 근거: SPEC-REQUEST.md:277 — - 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다.

검증 기준:

- AC-REQ-120: 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다.
- 추가 경계 사례: 변경 요청의 revision이 최신값과 다르면 409 STALE_WRITE·후행 데이터/이력 변경0건이다.

### REQ-121 — 대응 변경의 업무 불변 (source_id: null)
<!-- speckit:section sec-66501e93db8b4b1191cd753c528c5956 -->

업무 목적: 원문 표기를 active 코드와 연결한다.

관찰 가능한 동작: 대응 변경으로 기존 업무 자료는 수정하지 않는다.

입력 항목: trim sourceText 1~100자·실제 groupId/active code; 등록 시 처리자는 서버 admin.

처리 규칙: 대응 변경으로 기존 업무 자료는 수정하지 않는다. 원문 근거: SPEC-REQUEST.md:278 — - 대응 변경으로 기존 업무 자료를 고친 건수는0건이다. 이번에는 엑셀 파서·파일 업로드/다운로드를 구현하지 않는다.

검증 기준:

- AC-REQ-121: 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-122 — 변경 이력 조회 (source_id: null)
<!-- speckit:section sec-d83ef466cee6407f9c0b9a85244665ac -->

업무 목적: 코드와 기준/대응의 변경 사실을 추적한다.

관찰 가능한 동작: 허용5종 대상만 원문 열·필터·정렬·페이지로 조회하고 긴 값 펼침/처리자 표시를 제공한다.

입력 항목: targetType/groupId/page/size·허용 대상의 TB_SY_CHG_HIST 행.

처리 규칙: 허용5종 대상만 원문 열·필터·정렬·페이지로 조회하고 긴 값 펼침/처리자 표시를 제공한다. 원문 근거: SPEC-REQUEST.md:282 — [변경 이력]은 코드 그룹·코드·값코드·기준값·표기 대응만 조회한다. 필터targetType/groupId·기본전체, 정렬REG_DTTM DESC/HIST_ID DESC, 페이지20/50/100·기본50이다. 열은 일시·대상·그룹/코드/키·유형·항목·이전·이후·처리자이며 긴 값은 펼쳐 읽는다. 신규/값변경/중지/재개/삭제는 NEW/UPD/STOP/RESUME/DEL로 기록한다. codeName 이력의 CHG_ITEM_NM은 CODE_NM으로 고정해 과거 이름을 추적한다. SYSTEM은‘시스템’, admin은‘임시 관리자’로 표시한다. 이력 수정·삭제 API는0개다.

검증 기준:

- AC-REQ-122: REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다.; api_endpoint:GET /api/common-code-history
- 사례 1: REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다.
- 사례 2: api_endpoint:GET /api/common-code-history

연결 연산: GET /api/common-code-history (getCommonCodeHistory); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-123 — 변경 유형과 과거 이름 추적 (source_id: null)
<!-- speckit:section sec-f1d71dd25ed24bacb1e0396aaf7ff736 -->

업무 목적: 변경 유형과 과거 이름 추적을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: NEW/UPD/STOP/RESUME/DEL을 기록하고 codeName의 CHG_ITEM_NM은 CODE_NM으로 고정한다.

입력 항목: 대상 테이블/키·변경 컬럼·유형·전후값·admin·REG_DTTM.

처리 규칙: NEW/UPD/STOP/RESUME/DEL을 기록하고 codeName의 CHG_ITEM_NM은 CODE_NM으로 고정한다. 원문 근거: SPEC-REQUEST.md:282 — [변경 이력]은 코드 그룹·코드·값코드·기준값·표기 대응만 조회한다. 필터targetType/groupId·기본전체, 정렬REG_DTTM DESC/HIST_ID DESC, 페이지20/50/100·기본50이다. 열은 일시·대상·그룹/코드/키·유형·항목·이전·이후·처리자이며 긴 값은 펼쳐 읽는다. 신규/값변경/중지/재개/삭제는 NEW/UPD/STOP/RESUME/DEL로 기록한다. codeName 이력의 CHG_ITEM_NM은 CODE_NM으로 고정해 과거 이름을 추적한다. SYSTEM은‘시스템’, admin은‘임시 관리자’로 표시한다. 이력 수정·삭제 API는0개다.

검증 기준:

- AC-REQ-123: 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-124 — 이력 필터 도메인 (source_id: null)
<!-- speckit:section sec-8436f862484048bfaadc49a391e2ad27 -->

업무 목적: 코드와 기준/대응의 변경 사실을 추적한다.

관찰 가능한 동작: targetType은 명시5값·생략전체이며 groupId는 해당 그룹/코드/대응 이력만 반환한다.

입력 항목: targetType/groupId/page/size·허용 대상의 TB_SY_CHG_HIST 행.

처리 규칙: targetType은 명시5값·생략전체이며 groupId는 해당 그룹/코드/대응 이력만 반환한다. 원문 근거: SPEC-REQUEST.md:284 — targetType은 GROUP/CODE/VALUE_CODE/OPER_STD/CODE_MAP이며 생략하면 전체, 잘못된 값은400이다. CODE/VALUE_CODE는 그룹의 VAL_CODE_YN으로 구분한다. groupId가 있으면 그 그룹의 그룹/코드/대응 이력만 조회하고 운영 기준값은 제외한다.

검증 기준:

- AC-REQ-124: 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다.
- 사례 1: 잘못된 targetType은400
- 사례 2: groupId 지정 결과에 운영기준 이력이 없다.

### REQ-125 — 이력 키·전후값 무손실 (source_id: null)
<!-- speckit:section sec-1047d79658b14e6e92b1c6d74b806c04 -->

업무 목적: 이력 키·전후값 무손실을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 실제 테이블명과 대상별 TRGT_KEY·컬럼별 전후값·NEW/DEL JSON을500자 이내로 저장하고 잘라내지 않는다.

입력 항목: 대상 테이블/키·변경 컬럼·유형·전후값·admin·REG_DTTM.

처리 규칙: 실제 테이블명과 대상별 TRGT_KEY·컬럼별 전후값·NEW/DEL JSON을500자 이내로 저장하고 잘라내지 않는다. 원문 근거: SPEC-REQUEST.md:286 — TRGT_TBL_NM은 실제 테이블명, TRGT_KEY는 그룹=`groupId`, 코드=`groupId:code`, 대응=`groupId:mapId`, 기준=`key:YYYYMM`이다. UPD는 변경 컬럼별 전후 값, STOP/RESUME는 USE_YN, 코드 DEL은 DEL_YN의 N→Y다. NEW는 항목‘등록’·이전NULL·이후 식별자/이름/값을 남긴다. 대응 NEW/DEL은 sourceText/groupId/code/codeName의 JSON을 이후/이전 값에 각각 남기고 반대쪽은NULL이다. 삭제 후에도 groupId 필터가 동작하도록 키의 정확한 접두 구분자를 사용한다. 이력 값은500자 이내며 조용히 잘라 저장하지 않는다.

검증 기준:

- AC-REQ-125: 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다.
- 사례 1: 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회
- 사례 2: 500자 초과는 조용한 절단 없이 거부된다.

### REQ-126 — 공통 active 코드 조회 (source_id: null)
<!-- speckit:section sec-4ffcce1f58de4e14b36a7c313b8ccc1e -->

업무 목적: 공통 active 코드 조회을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: CodeQueryService/useCodes는 active 미삭제 코드를 정렬하여 원문 공개필드를 반환하고 includeInactive label을 구분한다.

입력 항목: groupId·includeInactive와 DB의 USE_YN/DEL_YN/SORT_ORD/CODE.

처리 규칙: CodeQueryService/useCodes는 active 미삭제 코드를 정렬하여 원문 공개필드를 반환하고 includeInactive label을 구분한다. 원문 근거: SPEC-REQUEST.md:290 — - CodeQueryService/useCodes(groupId)는 USE_YN=Y·DEL_YN=N 코드를 SORT_ORD/CODE순으로 반환한다. code/name/addVal1~3/codeVal/unitNm을 제공한다. 저장된 중지 코드 label 조회는 includeInactive로 구분하고 신규 선택에 끼워 넣지 않는다.

검증 기준:

- AC-REQ-126: GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다.; api_endpoint:GET /api/codes/{group}
- 사례 1: GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm
- 사례 2: 중지코드는 기본 제외·includeInactive에서 조회된다.; api_endpoint:GET /api/codes/{group}

연결 연산: GET /api/codes/{group} (getCodes); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-127 — 월별 단일 기준 조회 (source_id: null)
<!-- speckit:section sec-7caf6cdc2bf54492a25d1718cc65ab23 -->

업무 목적: 월별 단일 기준 조회을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: OperatingStandardService.get은 요청월 이하 최신 값/단위/적용월을 DB에서 반환한다.

입력 항목: key와 month(생략 현재월)·DB의 적용월 이력.

처리 규칙: OperatingStandardService.get은 요청월 이하 최신 값/단위/적용월을 DB에서 반환한다. 원문 근거: SPEC-REQUEST.md:291 — - OperatingStandardService.get(key,month)는 요청월 이하 최신 적용월의 정확한 값·단위·적용월을 반환한다. 키/해당월 값 없음은404 STANDARD_NOT_FOUND,0이나 하드코딩 값으로 대체하지 않는다. 조회는 DB 값을 읽는 기능이며 계산 엔진을 실행하지 않는다.

검증 기준:

- AC-REQ-127: GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다.; api_endpoint:GET /api/params/{key}
- 사례 1: GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다.
- 사례 2: api_endpoint:GET /api/params/{key}

연결 연산: GET /api/params/{key} (getParameter); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

### REQ-128 — 기준 미존재 거부 (source_id: null)
<!-- speckit:section sec-5d257b5ba09746bead8c0994ae5bcb33 -->

업무 목적: 기준 미존재 거부을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 키 또는 해당월 값이 없으면404 STANDARD_NOT_FOUND이며0/하드코딩 값으로 대체하지 않는다.

입력 항목: key와 month(생략 현재월)·DB의 적용월 이력.

처리 규칙: 키 또는 해당월 값이 없으면404 STANDARD_NOT_FOUND이며0/하드코딩 값으로 대체하지 않는다. 원문 근거: SPEC-REQUEST.md:291 — - OperatingStandardService.get(key,month)는 요청월 이하 최신 적용월의 정확한 값·단위·적용월을 반환한다. 키/해당월 값 없음은404 STANDARD_NOT_FOUND,0이나 하드코딩 값으로 대체하지 않는다. 조회는 DB 값을 읽는 기능이며 계산 엔진을 실행하지 않는다.

검증 기준:

- AC-REQ-128: 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다.
- 추가 경계 사례: 이 요구의 추가 기대값·정책은 원문에 명시된 경우만 적용한다. 미정 경계는 OQ-004에서 확인하며 임의 값을 확정하지 않는다.

### REQ-129 — API 필드와 서버 필드 보호 (source_id: null)
<!-- speckit:section sec-6a39fdc6b5a94113a39960c7c50c28ff -->

업무 목적: API 필드와 서버 필드 보호을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: camelCase·십진문자열·목록 봉투·expectedRevision을 따르고 허용외/주입 필드는400·모드 금지 필드는409다.

입력 항목: 원문 API별 허용 필드·camelCase JSON·숫자 문자열·expectedRevision·requestId.

처리 규칙: camelCase·십진문자열·목록 봉투·expectedRevision을 따르고 허용외/주입 필드는400·모드 금지 필드는409다. 원문 근거: SPEC-REQUEST.md:295 — 공통 접두사는 `/api`, JSON camelCase다. 목록은 `{items,total,page,size}`·page1부터, 전체 건수는현재 페이지 크기와 구분한다. codeVal/value는 십진 문자열, 기존 값 변경은 expectedRevision 필수다. 허용되지 않은 필드400, 모드상 금지 필드409다. 처리자·SYS/VAL/DEL·등록시각 주입을 거부한다.

검증 기준:

- AC-REQ-129: SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다.
- 사례 1: SYS/VAL/DEL/처리자/등록시각 주입400
- 사례 2: 시스템 잠금 필드409이며 DB 변경0건이다.

### REQ-130 — 공통 오류 반환과 비밀 보호 (source_id: null)
<!-- speckit:section sec-6d9c15366b3046009aff35e1198b7235 -->

업무 목적: 공통 오류 반환과 비밀 보호을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: 원문 상태별 오류코드·한국어 사유·필드·참조건수를 반환하고 SQL/스택/비밀값을 노출하지 않는다.

입력 항목: 원문 API별 허용 필드·camelCase JSON·숫자 문자열·expectedRevision·requestId.

처리 규칙: 원문 상태별 오류코드·한국어 사유·필드·참조건수를 반환하고 SQL/스택/비밀값을 노출하지 않는다. 원문 근거: SPEC-REQUEST.md:324 — 오류는400 VALIDATION_ERROR/INVALID_PERIOD/UNKNOWN_FIELD,404 NOT_FOUND/STANDARD_NOT_FOUND,409 STALE_WRITE/DUPLICATE_GROUP/DUPLICATE_GROUP_NAME/DUPLICATE_CODE/DUPLICATE_MAPPING/SYSTEM_CODE_LOCKED/VALUE_CODE_FIELD_LOCKED/CODE_IN_USE/CLOSED_MONTH/ORDER_LIMIT,500 INTERNAL_ERROR,503 DB_UNAVAILABLE다. 한국어 사유·필드·참조건수를 제공하고 SQL/스택/비밀값은 노출하지 않는다.

검증 기준:

- AC-REQ-130: 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다.
- 사례 1: 참조삭제409는 CODE_IN_USE·참조건수
- 사례 2: DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다.

### REQ-156 — 실제 DB health 조회 (source_id: null)
<!-- speckit:section sec-ab35c729fa9a41a0905318a33af8ff16 -->

업무 목적: 실제 DB health 조회을 통해 공통 저장·조회와 일관된 오류/데이터 보호를 보장한다.

관찰 가능한 동작: /api/health가 실제 DB 연결 SELECT를 검사하고 정상200·실패503을 반환한다.

입력 항목: 실제 DB 연결/SELECT 결과; 인증은 이번 단계에 적용하지 않는다.

처리 규칙: /api/health가 실제 DB 연결 SELECT를 검사하고 정상200·실패503을 반환한다. 원문 근거: SPEC-REQUEST.md:470 — /api/health는 실제 DB 연결을 검사하고 정상 200, 연결 실패 503을 반환한다.

검증 기준:

- AC-REQ-156: 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다.; api_endpoint:GET /api/health
- 사례 1: 정상 DB에서200
- 사례 2: DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다.; api_endpoint:GET /api/health

연결 연산: GET /api/health (getHealth); 아래 API 계약이 입력/결과를 소유하며 후속 OpenAPI는 이 이름을 보존한다.

## Technology Stack Contract
<!-- speckit:section sec-b221c2772c244516a6fb202d434a0bf1 -->

기술 계약은 추정으로 덮어쓰지 않는다. 다음 고정 값은 원문 369~408행의 authoritative contract를 그대로 보존한다. 30행의 Java21/Spring Boot3.5.x와의 충돌은 OQ-001이며 이 후보를 build-ready로 넘기지 않는다. 나머지 MVC/JPA/Flyway·Gradle·PostgreSQL16 요구는 고정 계약과 합치한다.

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

추가 기술 제약: React Router, Gradle, Spring MVC/JPA/Flyway, ddl-auto=validate, PostgreSQL16, imperative/jpa/blocking, BigDecimal, Asia/Seoul, 3서비스 compose, nginx /api 프록시·SPA fallback. frontend/는 공통 UI/설정/기간/UI-27와 hook/client를 구현하고 backend/는 실제 DB API/검증/트랜잭션/revision/이력·CurrentUserProvider를 구현한다. infra/docker-compose.yml과 .env.example은 환경변수·healthcheck·실행/종료 안내를 제공하며 공개 배포는 금지한다. 각 build context에 .dockerignore를 제공한다.

연결 요구: REQ-132, REQ-133, REQ-134, REQ-135, REQ-136, REQ-137, REQ-138, REQ-139, REQ-140, REQ-141, REQ-142, REQ-143, REQ-144, REQ-145, REQ-146

### 실행·영속성 결정의 보존
<!-- speckit:section sec-1db2512e6e564fc38d2081a6dd0b78ee -->

원문의 resolved/imperative/jpa/blocking/explicit/conflicts=[] 결정을 그대로 따른다. MyBatis 우선순위 목록은 자동 기술 선택 지시가 아니며 이미 명시 선택된 jpa를 대체하지 않는다. 이번 단계에는 plan.md를 만들지 않는다. 후속 plan.md에는 아래 원문 블록 전체를 값 변경 없이 복사하고 그 문서가 이 결정의 semantic SSOT가 된다. 별도 열 번째 실행·영속성 문서는 만들지 않는다. Java/Boot 버전 OQ와 실행/영속성 resolved 상태는 서로 다른 판단이다.

<!-- aiops:execution-persistence-contract:start -->
```json
{
  "decision_status": "resolved",
  "execution_model": "imperative",
  "persistence_technology": "jpa",
  "access_mode": "blocking",
  "selection_source": "explicit",
  "blocking_priority": [
    "mybatis",
    "jpa",
    "spring-jdbc"
  ],
  "conflicts": []
}
```
<!-- aiops:execution-persistence-contract:end -->

## 핵심 데이터 계약
<!-- speckit:section sec-1ace0064771a4a2e8f63292ff35a9c05 -->

설치 전체 스키마의 정본은 원문 지정 SQL 전체다. 현재 DB 원본 파일은 이 작업 폴더에 없으므로 68개 테이블·836개 컬럼 이름을 만들어 적지 않는다. 실제 원본 revision/해시·구조 검증은 후속 구현 전 조회해야 한다. 아래 6테이블만 설치 후 업무 쓰기 대상이며 나머지는 조회용이다. data-model.md/data-model.json은 이번 단계의 출력이 아니다.

|테이블|핵심 정의|
|---|---|
|TB_SY_CODE_GRP|CODE_GRP_ID(30) PK, CODE_GRP_NM(50), SYS_CODE_YN, VAL_CODE_YN, SORT_ORD NUMERIC(5), USE_YN|
|TB_SY_CODE|PK(CODE_GRP_ID,CODE(20)), CODE_NM(100), SORT_ORD(5), USE_YN, DEL_YN, CODE_VAL NUMERIC(15,4), UNIT_NM(10), ADD_VAL1~3(100)|
|TB_SY_CODE_MAP|MAP_ID 시퀀스, SRC_TEXT(100), CODE_GRP_ID(30), CODE(20), UK(SRC_TEXT,CODE_GRP_ID)|
|TB_SY_OPER_STD|PK(STD_CD(30),APLY_BGN_YM CHAR(6)), STD_NM(100), STD_VAL NUMERIC(15,4), UNIT_NM(10), STD_DESC(300)|
|TB_SY_USER_PREF|PK(USER_ID,PREF_TYPE_CD,PREF_KEY), PREF_VAL VARCHAR(2000)의 JSON|
|TB_SY_CHG_HIST|HIST_ID 시퀀스, TRGT_TBL_NM(30), TRGT_KEY(100), CHG_TYPE_CD, CHG_ITEM_NM(50), BFR_VAL/AFT_VAL/CHG_RSN(500), REG_USER_ID/REG_DTTM|

REG_USER_ID는 VARCHAR(30) NOT NULL, REG_DTTM은 기본 CURRENT_TIMESTAMP, MOD_USER_ID/MOD_DTTM은 nullable이며 서버가 기록한다. 이력 테이블에는 DDL의 등록 필드만 쓴다. 화면/API 연월은 YYYY-MM, DB는 YYYYMM이다.

핵심 연결 요구: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-131

## Canonical Naming Ledger
<!-- speckit:section sec-9f958c439c444bb993adedab8ce02a06 -->

원문 경로에 공통 /api를 적용한다. 같은 자원을 여러 동사로 다루면 하나의 path 행에 묶는다. API 전용 경로는 화면 route/screen_id를 새로 만들지 않는다. N/A의 사유는 해당 경로마다 적으며, 이미 이름을 선언한 공유 물리 테이블은 해당 첫 행의 명명을 참조한다. 공유 UI-27에 자식 요구마다 가짜 화면ID/테이블을 부여하지 않는다. operationId는 이 명세에서 선택한 고정 명칭이며 후속 contracts/openapi.yaml의 실제 연산으로 생성해야 한다. 이번 stage에서 OpenAPI 파일 존재나 검증 완료를 뜻하지 않는다.

| 개념 | route | API path | operationId | screen_id | primary entity or table | canonical_id | source_id | 명명 확인 사항 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| API 자원 /api/health | N/A — 비화면 API /api/health | /api/health | getHealth | N/A — /api/health의 API만 명명 | N/A — 실제 DB SELECT 연결검사 | REQ-156 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/current-user | N/A — 비화면 API /api/current-user | /api/current-user | getCurrentUser | N/A — /api/current-user의 API만 명명 | N/A — CurrentUserProvider의 DB admin; 물리 사용자 테이블명 OQ-002 | REQ-025 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/menus | N/A — 비화면 API /api/menus | /api/menus | getMenus | N/A — /api/menus의 API만 명명 | TB_SY_MENU | REQ-035 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/period-context | N/A — 비화면 API /api/period-context | /api/period-context | getPeriodContext | N/A — /api/period-context의 API만 명명 | TB_SY_OPER_STD | REQ-063 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/month-close | N/A — 비화면 API /api/month-close | /api/month-close | getMonthClose | N/A — /api/month-close의 API만 명명 | TB_CL_CLOSE | REQ-066 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/me/preferences | N/A — 비화면 API /api/me/preferences | /api/me/preferences | getPreferences | N/A — /api/me/preferences의 API만 명명 | TB_SY_USER_PREF | REQ-047 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/me/preferences/{type}/{key} | N/A — 비화면 API /api/me/preferences/{type}/{key} | /api/me/preferences/{type}/{key} | putPreference, deletePreference | N/A — /api/me/preferences/{type}/{key}의 API만 명명 | 동일 물리명 참조: TB_SY_USER_PREF / /api/me/preferences/{type}/{key} | REQ-048, REQ-050 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/code-groups | N/A — 비화면 API /api/code-groups | /api/code-groups | getCodeGroups, createCodeGroup | N/A — /api/code-groups의 API만 명명 | TB_SY_CODE_GRP | REQ-069, REQ-076 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/code-groups/{group}/codes | N/A — 비화면 API /api/code-groups/{group}/codes | /api/code-groups/{group}/codes | getGroupCodes, saveGroupCodes | N/A — /api/code-groups/{group}/codes의 API만 명명 | TB_SY_CODE | REQ-070, REQ-071 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/code-groups/{group}/codes/{code} | N/A — 비화면 API /api/code-groups/{group}/codes/{code} | /api/code-groups/{group}/codes/{code} | deleteGroupCode | N/A — /api/code-groups/{group}/codes/{code}의 API만 명명 | 동일 물리명 참조: TB_SY_CODE / /api/code-groups/{group}/codes/{code} | REQ-095 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/codes/{group} | N/A — 비화면 API /api/codes/{group} | /api/codes/{group} | getCodes | N/A — /api/codes/{group}의 API만 명명 | 동일 물리명 참조: TB_SY_CODE / /api/codes/{group} | REQ-126 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/code-mappings | N/A — 비화면 API /api/code-mappings | /api/code-mappings | getCodeMappings, createCodeMapping | N/A — /api/code-mappings의 API만 명명 | TB_SY_CODE_MAP | REQ-115, REQ-117 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/code-mappings/delete | N/A — 비화면 API /api/code-mappings/delete | /api/code-mappings/delete | deleteCodeMappings | N/A — /api/code-mappings/delete의 API만 명명 | 동일 물리명 참조: TB_SY_CODE_MAP / /api/code-mappings/delete | REQ-119 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/operating-standards | N/A — 비화면 API /api/operating-standards | /api/operating-standards | getOperatingStandards, saveOperatingStandards | N/A — /api/operating-standards의 API만 명명 | 동일 물리명 참조: TB_SY_OPER_STD / /api/operating-standards | REQ-099, REQ-102 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/operating-standards/{key}/history | N/A — 비화면 API /api/operating-standards/{key}/history | /api/operating-standards/{key}/history | getOperatingStandardHistory | N/A — /api/operating-standards/{key}/history의 API만 명명 | 동일 물리명 참조: TB_SY_OPER_STD / /api/operating-standards/{key}/history | REQ-100 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/params/{key} | N/A — 비화면 API /api/params/{key} | /api/params/{key} | getParameter | N/A — /api/params/{key}의 API만 명명 | 동일 물리명 참조: TB_SY_OPER_STD / /api/params/{key} | REQ-127 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |
| API 자원 /api/common-code-history | N/A — 비화면 API /api/common-code-history | /api/common-code-history | getCommonCodeHistory | N/A — /api/common-code-history의 API만 명명 | TB_SY_CHG_HIST | REQ-122 | null | 메뉴/상태 생성 없음; UI 연결은 아래 화면 등록 계약 |

화면 등록 계약: 공식 route는 /{screenId}, root /는 /UI-16, legacy #UI-xx는 replace한다. UI-27은 시스템 → 공통코드이며 하위 네 탭을 별도 screenId나 pathname으로 발명하지 않는다. 나머지 UI-ID와 menu_path는 아래 DB 초기 메뉴 목록을 보존한다. 역할 ADMIN·처리자 admin·DataScope=ALL, SYS/VAL/USE/DEL의 Y/N, CLOSE/WORK와 변경 유형 NEW/UPD/STOP/RESUME/DEL도 그대로 유지한다.

| 화면ID | route | menu_path | 기간 유형 | canonical_id | source_id |
| --- | --- | --- | --- | --- | --- |
| UI-27 | /UI-27 | 시스템 → 공통코드 | NONE | REQ-069, REQ-070, REQ-071, REQ-072, REQ-073, REQ-074, REQ-078, REQ-079, REQ-080, REQ-081, REQ-082, REQ-083, REQ-084, REQ-085, REQ-086, REQ-087, REQ-092, REQ-093 | null |
| UI-16 | /UI-16 | 대시보드 | YEAR_MONTH | REQ-035, REQ-036, REQ-037, REQ-038 | null |

## API 동작 계약
<!-- speckit:section sec-ce6cb7b8565a47bd9b5b096d3ad32c95 -->

공통 접두사 /api, JSON camelCase. 목록은 items/total/page/size이며 page=1부터이고 total은 현재 페이지 행수가 아니다. codeVal/value는 십진 문자열, 기존 값 변경에는 expectedRevision이 필수다. 숨겨진 서버 필드와 미변경 필드를 통째로 되돌려 보내지 않는다. 본문이 있는 POST/PUT은 표의 입력과 모드별 필수값만 받는다. 운영 기준 전체43키처럼 비페이지 자원은 일반 페이지 API로 바꾸지 않는다.

후속 OpenAPI 공통 봉투: components.securitySchemes를 선언하되 이번 operation에는 인증·401/역할403을 요구하지 않는다. ApiResponse는 required success/meta·success=true, ApiError는 required success/meta·success=false이며 성공 data가 약속된 연산은 allOf(ApiResponse, 연산별 data 필수 스키마)를 쓴다. 2xx/4xx JSON은 각각 그 봉투를 $ref로 참조한다. 원문 오류 code/message/field?/details?/requestId와 X-Request-Id를 보존하고 완전한 ApiError를 ApiResponse.error 아래 중첩하지 않는다. 오류 필드의 봉투 내부 위치는 OQ-003에서 확정한다.

쓰기 연산은 아래 연관 요구의 규칙/이력/관찰 가능한 테스트를 x-business-rules/x-side-effects/x-required-tests에 각각 투영한다. 상태 변경 연산은 x-state-transitions도 쓴다. auth 테스트는 임의401/403을 만들지 말고 임시 관리자에서 인증차단0건·처리자 주입 거부를 검증한다. 이력이 없는 사용자 설정에 audit 이력을 만들어 다섯 범주를 채우지 않는다. health는 실제 DB 점검의 200/503이며 호출자가 고칠 입력/인증/업무 오류가 없음을 명시하는 경우에만 두 infrastructure 예외 사유를 같이 쓴다. 비즈니스 GET에는 해당 원문400/404를 보존한다. 원문이 정하지 않은 오류는 검사 통과용으로 발명하지 않는다.

| METHOD path | operationId | 원문 입력·결과 | 대표 canonical_id |
| --- | --- | --- | --- |
| GET /api/health | getHealth | 실제DB SELECT·200/실패503 | REQ-156 |
| GET /api/current-user | getCurrentUser | admin/name/roleIds/developmentMode | REQ-025 |
| GET /api/menus | getMenus | DB 메뉴·상위ID·이름·screenId·순서·icon·pcOnly·children·진단 | REQ-035 |
| GET /api/period-context | getPeriodContext | 현재일/연월·timezone·CASH_BASE_YM·마감상태 | REQ-063 |
| GET /api/month-close | getMonthClose | YYYY-MM 범위·from≤to·이관/마감/작업중 조회 | REQ-066 |
| GET /api/me/preferences | getPreferences | 설정 value/revision/stored·기본값 | REQ-047 |
| PUT /api/me/preferences/{type}/{key} | putPreference | value/expectedRevision → 검증·upsert | REQ-048 |
| DELETE /api/me/preferences/{type}/{key} | deletePreference | expectedRevision → 해당 설정만 초기화 | REQ-050 |
| GET /api/code-groups | getCodeGroups | 그룹·SYS/VAL·sortOrder·미삭제 코드 수 | REQ-069 |
| POST /api/code-groups | createCodeGroup | groupId/name →201·일반 업무 그룹 | REQ-076 |
| GET /api/code-groups/{group}/codes | getGroupCodes | page/size → 미삭제 코드(중지 포함)·추가값/값/단위·groupRevision | REQ-070 |
| PUT /api/code-groups/{group}/codes | saveGroupCodes | expectedRevision/changes:[{code,isNew,...변경필드}] → 그룹 단위 저장. 일반 name/useYn/addVal1/2, 시스템 name, 기존 값코드 codeVal만 | REQ-071 |
| DELETE /api/code-groups/{group}/codes/{code} | deleteGroupCode | expectedRevision → 참조 검사·논리 삭제 | REQ-095 |
| GET /api/codes/{group} | getCodes | includeInactive=false 기본·조회용 정확한 값/단위 | REQ-126 |
| GET /api/code-mappings | getCodeMappings | sourceText/groupId/page/size → 목록·행revision | REQ-115 |
| POST /api/code-mappings | createCodeMapping | sourceText/groupId/code →201 | REQ-117 |
| POST /api/code-mappings/delete | deleteCodeMappings | items:[{mapId,expectedRevision}] → 원자적 실제삭제 | REQ-119 |
| GET /api/operating-standards | getOperatingStandards | 생략현재월·43키의 유효값/단위/설명/적용월·keyRevision | REQ-099 |
| GET /api/operating-standards/{key}/history | getOperatingStandardHistory | 모든 적용월·현재/예정·revision | REQ-100 |
| PUT /api/operating-standards | saveOperatingStandards | effectiveMonth?/changes:[{key,value,expectedRevision}] → 검증·이력·저장.6.3 두키는effectiveMonth없이 별도요청 | REQ-102 |
| GET /api/params/{key} | getParameter | 생략현재월·단일 유효값 | REQ-127 |
| GET /api/common-code-history | getCommonCodeHistory | targetType/groupId/page/size → 허용 대상 이력 | REQ-122 |

상태/오류 보존: 400 VALIDATION_ERROR/INVALID_PERIOD/UNKNOWN_FIELD; 404 NOT_FOUND/STANDARD_NOT_FOUND; 409 STALE_WRITE/DUPLICATE_GROUP/DUPLICATE_GROUP_NAME/DUPLICATE_CODE/DUPLICATE_MAPPING/SYSTEM_CODE_LOCKED/VALUE_CODE_FIELD_LOCKED/CODE_IN_USE/CLOSED_MONTH/ORDER_LIMIT; 500 INTERNAL_ERROR; 503 DB_UNAVAILABLE. 모드상 금지409와 허용외 필드400을 혼동하지 않는다. 실제 성공 결과·이력 행·무변경·충돌 롤백을 연산별로 구분한다.

## 원문 상세 계약
<!-- speckit:section sec-5ed6f471744546c485b2b475f4882172 -->

아래는 원문 요구 절의 상세 필드·화면·필터·Props·고정 참조 목록을 생략 없이 보존한 계약이다. 새로운 요구 ID를 발행하는 부록이 아니다. 앞의 canonical_id/소유/합격 기준에 해당하는 정확한 원문을 적용하며 명확화의 시스템 규칙 우선/삭제 방식/무기한 보존은 이 절보다 우선한다. 실제 SQL/레퍼런스 HTML의 내용은 이번 문서 작성에서 조회 검증하지 않았으며 이 인용은 그 내용을 보았다는 주장이 아니다.

### 2. 기술·DB 구축
<!-- speckit:section sec-a8979f24eb5f4ae2b96b9064f60fa3d9 -->


#### 2.1 실행 구성
<!-- speckit:section sec-2cd6caf84ea644328cbc14a91d9c26b1 -->


- React·TypeScript·Vite·React Router, Java21·Spring Boot3.5.x·Gradle·Spring MVC/JPA/Flyway, PostgreSQL16을 사용한다. 기본 경로는 `frontend/`, `backend/`, `infra/docker-compose.yml`이다.
- 프론트/nginx·백엔드·DB의3서비스를 compose로 실행한다. nginx는 `/api`를 프록시하고 화면 직접 접근/새로고침을 SPA fallback으로 처리한다. DB 접속값은 환경설정, `.env.example`은 자리표시자와 실행 안내로 제공한다.
- 서버·DB 세션 시간대는 Asia/Seoul로 통일한다. 현재일·연월·등록/수정시각과 화면 일시를 이 기준으로 처리한다.
- JPA `ddl-auto=validate`, 테이블 생성은 Flyway만 수행한다. 업무 SQL 식별자는 따옴표 없이 사용한다. 이번 숫자 입력·검증은 Java BigDecimal을 사용하며 float/double 변환을 하지 않는다. 실제 급여·원가 계산은 만들지 않는다.

#### 2.2 마이그레이션과 초기값
<!-- speckit:section sec-c05b177d4da340b89b66d43cdfbae49f -->


|순서|실행 파일·위치|내용|
|---|---|---|
|1|`backend/src/main/resources/db/migration/V1__full_schema.sql`|project_ddl 전체·마지막 HQ 예약 파트 포함|
|2|같은 경로 `V2__base_data.sql`|project_data 전체|

- 빈 개발 DB에서 **68테이블·836컬럼·73 FK·78 명시 인덱스·13 시퀀스·46 CHECK·5 UK**를 생성한다. TB_SY_CALC_RULE 11컬럼도 포함하며 컬럼·타입·NULL·DEFAULT·PK·UK·CHECK·FK·인덱스·시퀀스·코멘트를 보존한다.
- 초기값은 그룹64(시스템44), 코드391, 운영기준43, 역할5, 메뉴40(10묶음+30화면), 역할메뉴150, 관리자/역할 연결 각1행, 계산규칙94행이다. HQ1행은 DDL에서만 적재한다. TB_SY_CODE_MAP·변경 이력·사용자 설정은 빈 상태로 시작한다.
- 계산규칙 테이블·데이터는 그대로 적재하고 편집 화면·계산 실행 엔진은 생성하지 않는다. 초기 CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 월 마감 기록을 임의로 만들지 않는다.
- data의 최상위 BEGIN2개/COMMIT1개는 Flyway 트랜잭션 래퍼와 중복되지 않게 실행용 사본에서만 제거한다. 계산규칙 TEXT 안의 문자열은 치환하지 않는다. 원본/실행본 차이·사용한 revision/해시를 기록한다.
- 적용된 마이그레이션은 수정·재실행하지 않는다. 재시작 시 중복 적재0건이어야 한다. 미적용 파일은 원본과 대조해 완성한다. 설치 실패는 해당 트랜잭션을 롤백하고 지정 개발 DB에서 수정·검증하며 임의 DROP/TRUNCATE·자동 baseline으로 구조 불일치를 숨기지 않는다.
- 초기 구조와 이미 존재하는 구조가 다르면 이름·타입을 추정해 바꾸지 않고 차이와 실패 항목을 보고한다. 초기 관리자 비밀번호·메일 주소를 프론트에 노출하지 않는다.

#### 2.3 이번 쓰기 대상과 핵심 컬럼
<!-- speckit:section sec-5e68d227ca4a40faba9ba538fb70d50b -->


설치 이후 업무 쓰기는 아래6테이블만 사용한다. 사용자·역할·메뉴·마감·프로젝트 등은 조회용이다. 수당/기준값 변경으로 프로젝트 팀비·경비나 정산금액을 변경한 건수는0건이다.

|테이블|핵심 정의|
|---|---|
|TB_SY_CODE_GRP|CODE_GRP_ID(30) PK, CODE_GRP_NM(50), SYS_CODE_YN, VAL_CODE_YN, SORT_ORD NUMERIC(5), USE_YN|
|TB_SY_CODE|PK(CODE_GRP_ID,CODE(20)), CODE_NM(100), SORT_ORD(5), USE_YN, DEL_YN, CODE_VAL NUMERIC(15,4), UNIT_NM(10), ADD_VAL1~3(100)|
|TB_SY_CODE_MAP|MAP_ID 시퀀스, SRC_TEXT(100), CODE_GRP_ID(30), CODE(20), UK(SRC_TEXT,CODE_GRP_ID)|
|TB_SY_OPER_STD|PK(STD_CD(30),APLY_BGN_YM CHAR(6)), STD_NM(100), STD_VAL NUMERIC(15,4), UNIT_NM(10), STD_DESC(300)|
|TB_SY_USER_PREF|PK(USER_ID,PREF_TYPE_CD,PREF_KEY), PREF_VAL VARCHAR(2000)의 JSON|
|TB_SY_CHG_HIST|HIST_ID 시퀀스, TRGT_TBL_NM(30), TRGT_KEY(100), CHG_TYPE_CD, CHG_ITEM_NM(50), BFR_VAL/AFT_VAL/CHG_RSN(500), REG_USER_ID/REG_DTTM|

REG_USER_ID는 VARCHAR(30) NOT NULL, REG_DTTM은 기본 CURRENT_TIMESTAMP, MOD_USER_ID/MOD_DTTM은 nullable이며 서버가 기록한다. 이력 테이블에는 DDL의 등록 필드만 쓴다. 화면/API 연월은 YYYY-MM, DB는 YYYYMM이다.

### 3. 공통 부품·입력·저장
<!-- speckit:section sec-f09fc165945d45a69e6a5f0fa1fac9b8 -->


#### 3.1 컴포넌트와 책임
<!-- speckit:section sec-35b5fe484ac54cfea23d0c475664c20e -->


레이아웃·표·검색·페이지·입력·모달·배지·이력을 재사용 가능한 React·TypeScript 컴포넌트로 구성한다. 현재 UI-27에서 실제 사용하고, 후속 화면도 동일 경로/인터페이스를 재사용하도록 Props·이벤트·사용처를 기록한다. 컬럼·렌더러·행 작업·필드 정의를 화면에서 전달하고 공통 부품 안에 개별 업무 분기를 넣지 않는다.

|부품|필수 계약|
|---|---|
|AppLayout/Header/Sidebar|메뉴·화면폭·접힘·활성ID·탐색/접기 이벤트. 메뉴 조회는 부모 hook|
|ScreenTabs/TitleBar|tabs/activeId/dirtyIds·활성화/닫기·제목/작업/즐겨찾기. 탭 store가 개수/LRU 소유|
|`DataTable<T>`|rows/columns/rowKey/loading/empty/page/size/total·페이지/크기 변경·선택/행 작업/열 설정 옵션. 코드/대응/기준값/이력에서 재사용|
|Text/Decimal/CodeSelect|value/onChange/required/maxLength/error/disabled. 숫자 표시와 저장 문자열 분리|
|Dialog/ConfirmDialog|open/title/children/pending/onConfirm/onCancel·focus trap·닫힌 후 포커스 복원|
|ErrorBanner/Toast/StatusBadge|상태·한국어 문구·재시도 이벤트. 부모가 실제 처리 결과 전달|
|PeriodSelector/ScreenPeriodBar|4.4의 controlled Props와 부모 연결. DB 조회·설정 저장은 부모 책임|

프론트 기본 경로는 `src/components/{layout,common}/`, `src/screens/common-codes/`, `src/hooks/`, `src/lib/format.ts`다. API 호출은 hook/client, 검증·조회·저장은 서버 서비스가 담당한다. HTML 전체 iframe·DOM 문자열 복제로 구현하지 않는다.

#### 3.2 입력·정밀도
<!-- speckit:section sec-d62d33fdd2d44fb986b4958ef7019077 -->


|입력|허용값·기본값·오류|
|---|---|
|문자열|trim 후 길이 검사, 필수 빈칸400. 초과값을 잘라 저장하지 않음|
|그룹ID|소문자→대문자 후 `^[A-Z][A-Z0-9_]{0,29}$`,1~30자|
|코드|소문자→대문자 후 `^[A-Z0-9_]{1,20}$`,1~20자|
|그룹명/코드명|각1~50/1~100자. 등록된 ID/코드는 불변|
|CODE_VAL·STD_VAL|0~99,999,999,999.9999 포함, 정수부 최대11·소수 최대4자리. 숫자 문자열로 주고받고 BigDecimal로 검사/저장|
|숫자 입력 처리|공백·쉼표·끝의 원/% 표시는 제거 후 검사. 음수·지수표기·NaN·Infinity·그 밖의 문자는400. 소수5자리 또는 정수12자리 입력도400|
|반올림|CODE_VAL/STD_VAL은 입력 원값 보존, 자동 반올림/버림0회. 허용 소수 이내는 정확히 저장하며 화면 뒤0만 생략|
|JOB.ADD_VAL2 월단가|0~999,999,999,999,999원 포함·소수0자리. 쉼표/공백/끝의 ‘원’ 정리 후 정수 문자열로 ADD_VAL2 저장. 음수/소수/상한 초과400|
|연월|0001-01~9999-12의 실제 연월. 월00/13·형식 오류400, 월 가감은 날짜 라이브러리 사용|
|Y/N·JSON|Y/N만, boolean은 JSON true/false만. PREF_VAL JSON 전체 최대2000자. 타입 오류400|

기준값 빈칸은400, 수당 CODE_VAL의 신규 기본0·비운 값0은 허용한다. 단위가 %라는 이유만으로 임의100 상한을 추가하지 않으며 별도 제한은6.2의 표만 적용한다. 금액 원 단위 콤마, 음수 표시 함수는 ‘−’,0은‘0’,NULL은‘–’, 일자YYYY-MM-DD·일시YYYY-MM-DD HH:mm, 숫자 오른쪽/tabular-nums·문자 왼쪽·상태 가운데다. 표시 함수는 저장값을 바꾸지 않는다.

#### 3.3 임시 사용자·트랜잭션·동시 수정
<!-- speckit:section sec-bce5e84eb1ba44c2b93701c5ed25fb3b -->


- CurrentUserProvider는 DB의 admin·roleIds=[ADMIN]·표시명‘임시 관리자’·developmentMode=true를 제공한다. 헤더에 개발용임을 표시한다. 등록자·수정자·설정 USER_ID는 admin이며 요청으로 다른 처리자를 지정하지 못한다.
- 인증 리다이렉트/401·역할별403·민감칸 가림은0건이다. 권한 서비스의 현재 결과는 허용·DataScope=ALL이며 공급자는 한곳에서 교체 가능하게 둔다. 관리자 ID를 각 업무 로직에 복제하지 않는다.
- 코드 한 그룹 저장, 운영기준 한 요청, 대응 추가/다중 삭제는 각각 단일 트랜잭션이다. 한 항목 실패 시 데이터와 이력 모두0건 변경이다. 잠금은 키 오름차순이다.
- 조회는 불투명 revision, 변경은 expectedRevision을 사용한다. 비교값은 기존 식별자·저장값·REG_DTTM/MOD_DTTM으로 계산한다. 그룹은 그룹과 소속 전체 코드 행, 기준키는 전체 적용월 이력을 포함한다. 시각 양쪽NULL은 시각만 같고 한쪽NULL은 다르다. 양쪽NULL이어도 값이 바뀌면 다른 revision이다.
- 대상 그룹/기준키 잠금 후 최신값과 비교하고 불일치면409 STALE_WRITE다. 두 클라이언트가 같은 revision으로 다른 값을 저장하면 선행1건 성공·후행1건409, 후행 DB/이력 변경0건이다. 스키마에 version 컬럼은 추가하지 않는다.
- revision 일치 후 실제 변경0이면200 changed=false·“바뀐 내용이 없습니다.”, 수정시각/이력 갱신0건이다. 변경이 있으면 commit 후 changed=true와 재조회 결과를 반영한다.
- 저장 성공 응답 수신 후2초 이내에 저장 브라우저의 관련 캐시·열린 선택 목록·설정을 최신값으로 갱신한다. 서버 캐시는 commit 이후 무효화한다. 다른 브라우저는 다음 조회에서 최신값을 받는다. 통신 오류는 재시도 안내이며 오래된 값을 성공 결과로 표시하지 않는다.
- 오류는 `{code,message,field?,details?,requestId}`·X-Request-Id다. 필드 오류·첫 오류 포커스, 연결/500의 오류 띠·[다시 시도]를 제공한다. commit 후 재조회만 실패하면 “저장됐지만 다시 불러오지 못했습니다.”로 구분한다. 자동 저장/재시도0회·처리 중 중복 클릭 차단·입력 보존이다.

### 4. 메뉴·화면 틀·설정
<!-- speckit:section sec-0912259e9a3646ec8f55d9758daea421 -->


#### 4.1 DB 메뉴와 라우트
<!-- speckit:section sec-6047dc9ea2e243b093c07d01187bce6e -->


TB_SY_MENU를 API로 읽어 USE_YN=Y의 정상 부모/자식 관계를 구성한다. 형제는 SORT_ORD/MENU_ID순, 이름·아이콘·PC 전용 여부는 DB 원천이다. 아래는 초기값 검증 목록이며 프론트 하드코딩 메뉴 원천으로 사용하지 않는다.

|묶음|화면|
|---|---|
|대시보드|UI-16 대시보드|
|기준정보|UI-15 거래처·영업 파트, UI-02 개발자 풀|
|프로젝트·투입|UI-01 프로젝트, UI-31 프로젝트 현황판, UI-12 투입 배정, UI-05 철수 리스트, UI-11 인원 계획 대비 투입|
|인력 계획|UI-28 인력 운영 보드, UI-17 인력 수요 예측, UI-29 영업 파이프라인, UI-33 외부 인력 후보|
|매출|UI-13 청구·입금|
|원가·수익|UI-03 개인 원가·수익표, UI-04 프로젝트 원가 분석, UI-19 영업 파트 이익금, UI-32 단가 재협상 대상|
|재무·자금|UI-10 재무 항목, UI-07 전체 수익 구조, UI-08 영업이익표, UI-09 순이익표, UI-18 사업계획 대비 실적, UI-21 자금수지표|
|마감|UI-14 월 마감|
|AI|UI-26 AI 분석 도우미, UI-30 AI 인력 추천|
|시스템|UI-23 사용자, UI-24 권한, UI-25 메뉴, UI-27 공통코드|

- 현재 묶음을 자동 펼치고 기본은 한 묶음만 펼친다. 대시보드는 그룹+자식 데이터여도 한 항목으로 바로 이동한다. 화면ID가 있는 부모의 이름은 이동·펼침 아이콘은 토글이다. 접힌 메뉴는 hover/focus 하위 목록을 제공한다.
- 없는/중지 부모·자기참조·순환·MENU_LVL 깊이 불일치·미등록 화면ID는 해당 노드와 자손을 제외하고 진단을 남긴다. 중복 SCRN_ID는 정상 계층→SORT_ORD→MENU_ID순 대표1개, 탈락 트리도 제외한다. 다른 부모로 자동 이동하거나 DML을 수정하지 않는다. 정상 부분은 표시하되 초기 메뉴 오류가 남으면 합격하지 않는다.
- React 등록표는 screenId/컴포넌트/기간 유형만 관리한다. 공식 경로는 `/{screenId}`, `/`는 `/UI-16`, `#UI-xx`는 공식 경로로 replace한다. 유효 메뉴가 없는 주소는 대시보드, 대시보드도 없으면 정상 첫 화면, 정상 화면0개면 메뉴 오류다. 리다이렉트 반복은0회다.
- PC_ONLY_YN=Y 초기 화면은 UI-10·14·23·24·25·27이다. 폭768px 미만에서 메뉴·즐겨찾기·최근에서 숨긴다. 직접 접근/창 축소는 본문 대신 PC 사용 안내·이전/대시보드 이동을 보여 준다. 미저장 상태는 유지하고 폭을 넓히면 복원한다.

#### 4.2 레이아웃·탭·헤더
<!-- speckit:section sec-1d8124c254744ac3be5003d1dbb4e18c -->


- AppLayout이 Header/Sidebar/ScreenTabs/TitleBar/본문을 조합한다. 헤더56px, PC≥1280 메뉴 펼침240/접힘64px·여백24px, 태블릿768~1279 기본접힘·여백16px, 모바일≤767 메뉴 슬라이드·화면탭 없음이다. 최소360px에서 본문 전체 가로 넘침0, 넓은 표만 내부 스크롤한다.
- 헤더는 메뉴 토글·로고/시스템명(대시보드 이동)·AI/알림·임시 관리자 메뉴다. AI/알림 클릭은 “준비 중” 안내, 가짜 배지/대화는0개다. 사용자 메뉴는 화면 설정만 제공한다. 비밀번호·로그아웃·타이머는 없다.
- 제목은 묶음›화면명·즐겨찾기·UI-ID·해당 화면 작업이다. 업무 기간은 헤더가 아닌 제목 아래 본문 줄이다. UI-27은 업무 기간 줄이 없고 자체 적용월 입력을 사용한다.
- 메뉴 클릭은 탭 생성, 열린 화면이면 재활성화다. 최대8개(대시보드 포함), 대시보드는 맨 앞·닫기 불가다.9번째는 가장 오래 안 본 닫을 수 있는 탭을 제시해 확인한다. 취소하면 새 탭0개·기존 탭 유지다.
- 일반 탭 전환은 편집·필터·기간·스크롤 보존이다. UI-27의 그룹/하위 탭 변경·탭 닫기·상태를 버리는 이동은 “저장하지 않은 내용이 있습니다.”와 [계속 작성]/[나가기], 취소 시 상태를 유지한다. dirty 탭은 ●, 새로고침/창 닫기는 beforeunload다. 모달은 focus trap·닫기 후 포커스 복원, 입력은 label·Tab·오류 포커스를 제공한다.
- 새로고침은 URL 화면+고정 대시보드로 시작한다. 열린 탭 목록 자체를 서버 복원하지 않는다.

#### 4.3 서버 저장 설정
<!-- speckit:section sec-a5abfba4e49e43449e574ad4c7de7b22 -->


TB_SY_USER_PREF의 USER_ID는 admin이다. GET은 value/revision/stored를 반환하고 행 없음은 stored=false·기본값이다. MENU_FOLD만 미저장 value=null로 주고 화면폭별 기본값을 적용한다. 최초 PUT은 expectedRevision=null, 기존 행은 조회한 토큰을 보낸다. 최초 명시 저장은 기본값과 같아도 INSERT한다. 동시 최초 등록의 PK 충돌은409 STALE_WRITE다. 잘못된 JSON은 해당 항목의 오류/revision을 반환하고 화면은 기본값으로 임시 표시한다. [이 설정 초기화]로 해당 행만 삭제하며 자동 삭제하지 않는다. 다른 설정 조회·값은 유지한다. 설정 변경은 설정 행의 등록/수정 필드만 기록하고 UI-27 변경 이력에는 넣지 않는다.

|PREF_TYPE_CD / PREF_KEY|값·기본값·검사|
|---|---|
|FAV / *|화면ID 배열·기본[]·최대10개·중복 불가. 별 추가/해제, 목록 편집▲▼로 순서 변경.11번째는 거부|
|RECENT / *|최근 활성화순 화면ID 배열·기본[]·최대5개. 같은 화면 재방문은 맨 앞으로 이동·중복 제거·초과 뒤에서 제거|
|MENU_FOLD / *|boolean. 저장값 없으면 PC=false·태블릿=true. 모바일 슬라이드 열림은 임시 상태이며 이 값을 변경하지 않음|
|PAGE_SIZE / *|JSON 정수20/50/100·기본50. 문자열‘50’ 거부|
|DENSE / *|‘넓게’/‘좁게’·기본넓게. 모든 열린 목록과 사용자 메뉴에 같은값 적용|
|COLS / screenId|표별 `{viewId:{order:[columnId],hidden:[columnId]}}`·기본 전체열/기본순서. 필수열 숨김·중복·미등록열 거부|
|AMT_UNIT / *|‘원’/‘천원’/‘백만원’·기본천원. 선택/저장/복원만, 실제 업무 금액 변환은 이번 범위에서 제외|
|PERIOD / screenId|YEAR는 `{type:"YEAR",value:"2026"}`, YEAR_MONTH는 `{type:"YEAR_MONTH",value:"2026-11"}`, YEAR_CUM은 `{type:"YEAR_CUM",year:"2026",month:"2026-11"}`. 실제 기본값은 서버 현재일/허용범위로 결정|

- 즐겨찾기/최근 항목은 등록된 화면ID만 허용한다. 숨겨진 PC 전용 항목은 배열에서 삭제하지 않고 표시만 숨긴다. 메뉴 접힘 상태에서는 두 목록을 숨긴다. 빈 안내는 각각 “화면 제목 옆 ☆를 누르면 추가됩니다”·“아직 없습니다”다.
- UI-27의 viewId는 codes/valueCodes/mappings/standards/history다. order는 해당 표 정의의 전체 열 순열, hidden은 선택 열 부분집합이다. 코드/값코드의 code/name, 대응의 sourceText/groupId/code, 기준의 key/name/value, 이력의 occurredAt/target는 필수열이다. 선택 체크·작업 버튼은 열 설정에서 제외한다. 다른 화면의 COLS는 해당 화면에 표 정의가 생긴 뒤 허용한다.
- 기본 columnId 순서는 codes=`code,name,addVal1,addVal2,sortOrder,useYn`, valueCodes=`code,name,codeVal,unitNm`, mappings=`sourceText,groupId,code`, standards=`key,name,value,unitNm,effectiveMonth,description`, history=`occurredAt,target,groupId,entityKey,type,item,before,after,actor`다. codes는 전체 순서를 저장하되 현재 모드/그룹에 적용되는 열만 표시한다.
- PAGE_SIZE·DENSE는 목록과 사용자 메뉴 어디서 바꿔도 서버 저장 후 열린 목록에 반영한다. 필터/페이지/열 설정·밀도는 미저장 편집값을 삭제하지 않는다. 설정 저장 실패는 이전 적용값으로 유지·오류 안내다. localStorage만으로 합격하지 않는다.
- PERIOD는 등록표 유형·연도와 월 일치·4.4의 범위를 검사한다. NONE 화면의 PERIOD 쓰기는400이다. 저장된 기간이 새 허용범위 밖이면 DB를 조용히 바꾸지 않고 안내와 기본기간 표시, 사용자가 다시 고를 때 저장한다.

#### 4.4 기간 선택·마감 조회
<!-- speckit:section sec-c02ebe2a3ea841f499097ed2e8699f8d -->


PeriodSelector는 React·TypeScript **controlled** 부품이다. Props는 `type:'YEAR'|'YEAR_MONTH', value:string, onChange(nextValue), disabled?, min?, max?, ariaLabel?`다. YEAR는 YYYY, YEAR_MONTH는 YYYY-MM만 전달한다. 값/범위/disabled는 부모 소유, 내부 DB 조회·설정 저장0회다. 범위 밖 화살표·disabled 입력은 비활성이고 변경 이벤트0회, 유효 선택1회는 이벤트1회다.

ScreenPeriodBar가 서버 현재일·설정과 연결한다. 누계형은 연도+기준월·‘1~n월 누계’를 조합하며 연도 변경 시 기존 월을 새 범위로 제한한다. 기본기간은 현재 연월을 범위 안으로 제한한 값이다. 화면별 유형은 아래와 같다.

|유형|화면|
|---|---|
|YEAR|UI-01·03·05·11·12·13·17·21·28|
|YEAR_MONTH|UI-04·10·14·16·19|
|YEAR_CUM|UI-07·08·09·18|
|NONE|나머지 전체·UI-27 포함|

- 현재 빈 업무화면의 YEAR 범위는 CASH_BASE_YM의 연도~현재연도+1, YEAR_MONTH는 CASH_BASE_YM~현재월이다. 누계형은 시작연도~현재연도, UI-07/08/09만 시작연도-1부터 허용한다. 누계 기준월은 선택연도의1월~12월, 현재연도는 현재월까지만이다. 범위 역전은 ‘운영 시작 전’·선택 비활성·PERIOD 저장0회다.
- 연도 배지는 올해/지난 해·읽기 전용/내년·계획을 날짜 비교로 표시한다. 월 배지는 아래 상태 조회를 사용한다. 이번에는 기간 선택·표시·기억까지만 구현하고 업무 데이터의 계산/쓰기/재계산은 없다.
- isClosed(ym)는 CASH_BASE_YM 이전이면 이관·결산완료, 이후 TB_CL_CLOSE.CLOSE_STAT_CD=CLOSE면 마감, WORK/행 없음이면 작업중이다. 서버 현재일과 DB로 판단하고9개월 마감 같은 가상 기록은0건이다. 마감 쓰기 API는0개다.

### 5. 공통코드 UI-27
<!-- speckit:section sec-1c1aee7a0ab04bd6971d7ee3b7dd8cf9 -->


#### 5.1 구성·목록·편집
<!-- speckit:section sec-0d66f9bbe7be4f7bb0be758e5a435c60 -->


- [코드] [엑셀 표기 대응] [운영 기준값] [변경 이력]4탭이다. 로딩·빈 결과·API 오류를 구분한다. 코드/대응/변경 이력은 페이지20/50/100·기본50, page≥1, 잘못된 size/page는400이다. 운영 기준값은43키를 묶음 줄로 모두 표시하고 페이지를 나누지 않는다. 공통 표의 페이지 기능은 선택 옵션이다. 기본 정렬은 각 절에 고정하며 페이지 이동도 편집값을 유지한다.
- 그룹은 DB 전체를 SORT_ORD/CODE_GRP_ID순으로 표시한다. 초기64개(시스템44), 첫 그룹 자동 선택·선택 강조·업무코드 수·시스템 자물쇠다. 목록 높이 안 별도 스크롤·선택 후 위치 유지. 그룹 이름/사용 여부 변경·그룹 삭제 기능은0개다.
- 일반 코드 표는 code/name/addVal1~2(적용 그룹만)/sortOrder/useYn/deleteAction, 이름·사용·허용 추가값만 편집한다. 순서는 저장된 SORT_ORD·CODE순·읽기 전용이다. DEL_YN=N의 중지 코드도 흐리게 표시한다. 코드 표의 선택 체크/일괄 중지 버튼은0개, 여러 행을 수정 후 [저장]1회로 반영한다.
- [취소]는 미저장 신규/편집을 폐기하고 마지막 조회값을 복원한다. [저장]은 변경분만 전송하고 현재 페이지에 없는 행을 삭제하지 않는다. 동일 code를 한 요청에2회 보내면400이다.
- SYS_CODE_YN=Y는 이름만 변경한다. 신규·삭제·순서·사용·추가값·값/단위 변경은409 SYSTEM_CODE_LOCKED이고 같은 요청의 이름 변경도 롤백한다. “이름만 바꿀 수 있습니다.”를 표시한다.

#### 5.2 그룹·일반 코드 추가
<!-- speckit:section sec-34adb7d869474eccad16af7ef071d252 -->


- [그룹 추가]: groupId/name 필수·3.2 규칙, ID 중복409 DUPLICATE_GROUP, 이름도 trim 후 같은 문자열이면409 DUPLICATE_GROUP_NAME이다. 그룹 추가 중 그룹 테이블을 직렬화 잠금해 이름 중복과 SORT_ORD 채번 경합을 막는다.
- 새 그룹은 SYS_CODE_YN=N·VAL_CODE_YN=N·USE_YN=Y·SORT_ORD=max+1이다. DB 저장과 NEW 이력 후 선택·재조회한다. 미저장 코드가 있으면 먼저 저장/취소한다. 임의 추가값 컬럼 정의는 제공하지 않는다.
- 일반 [코드 추가] 창은 code/name·해당 추가값 필수다. 소문자는 대문자 정규화, 같은 그룹 중복은 DEL_YN=Y도409 DUPLICATE_CODE다. 창 [추가]는 편집 목록의 신규 행으로만 넣고 하단 [저장] 때 DB에 저장한다. 신규 취소는 DB 변경0건이다.
- 새 코드는 USE_YN=Y·DEL_YN=N, SORT_ORD는 그룹 내 max+1이며 같은 요청의 신규는 전달 순서다. SORT_ORD가 NUMERIC(5)를 넘으면409 ORDER_LIMIT로 전체 롤백한다. 저장된 PK는 변경하지 않는다.
- 코드명 중복은 허용하며 그룹ID/코드로 구분한다.

#### 5.3 그룹별 추가값
<!-- speckit:section sec-2901249d87dd429eac0cdfdbe5045f6e -->


추가·수정 시 아래 필드는 빈칸 불가, 정규화 후 각각100자 이하다. 다른 그룹의 ADD_VAL은 NULL, ADD_VAL3은 이번에 사용하지 않는다. 알려진 다른 그룹 필드를 보내면400이다.

|그룹|ADD_VAL1|ADD_VAL2|
|---|---|---|
|JOB|관련 JOB 코드 CSV|3.2의 월단가 정수|
|SKILL|필요한 JOB 코드 CSV|없음|
|PROJ_STEP|0~100 포함 정수·소수 거부|CTRT_TYPE 코드 CSV|
|BANK|정확한 숫자3자리·앞0 보존|없음|
|RSM_LABEL|NAME/TEL/MAIL/ADDR/SKILL/WANT/FROM/VNDR 중1개·대문자 정규화|없음|
|RSM_JOB_KW|JOB 코드 CSV|없음|

CSV는 쉼표 분리→항목trim/대문자→빈 항목 거부→첫 등장 순서로 중복 제거→쉼표 결합이다. 최소1개, 실제 코드 존재·미삭제·신규 참조는 USE_YN=Y를 검사한다. JOB 자기 자신 포함은 허용하며 필수는 아니다. 같은 저장 요청에서 새로 추가되는 JOB 참조도 최종 상태로 검사한다. 기존 중지 참조를 그대로 유지하는 것은 허용한다.

#### 5.4 값 코드(수당)
<!-- speckit:section sec-3d08985a68e149d395f1e77bd832f311 -->


- VAL_CODE_YN=Y 그룹은 **코드/이름/값/단위**4열이다. 기존은 값만 편집, 코드·이름·단위는 읽기 전용이다. 사용/삭제/순서 작업은 없다. API의 기존 값 이외 필드 변경·삭제는409 VALUE_CODE_FIELD_LOCKED다.
- [수당코드 추가]는 코드·이름 필수, 값 기본0·비우면0, 단위는 원/% 중 선택·기본원이다. 3.2의 CODE_VAL 정밀도·코드 규칙을 적용한다. 추가 창은 신규 편집 행, 하단 저장은 그룹 단위 트랜잭션이다.
- 초기 ALWNC6개는 ALLOW_TEAM=200000원, ALLOW_GROUP=400000원, INS_RATE=9.4%, SEV_RATE=8.33%, TEAM_FEE/PEXP_FEE=50000원이며 실제 값은 SQL에서 읽는다. 저장 후 공통 조회의 값/단위가 바뀌고 업무 테이블 값은 그대로다. 급여·보험·원가 계산 엔진은 생성하지 않는다.

#### 5.5 중지·삭제·참조 보호
<!-- speckit:section sec-a1c663b95f9340c194b2d07ffb0d342d -->


- 일반 업무 코드의 USE_YN 체크를 N/Y로 바꿔 저장하면 STOP/RESUME 이력이다. 중지 코드는 신규 옵션에서 제외하고 기존 이름 조회는 가능하다. 다른 테이블의 저장 코드를 수정한 건수는0건이다.
- 일반 업무 코드 [삭제]는 미저장 변경을 먼저 저장/취소하고 확인 후 별도 요청한다. 아래 참조가1건이라도 있으면409 CODE_IN_USE·참조 유형/건수·“사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.”다. 없으면 DEL_YN=Y·DEL 이력·물리 행 보존, 같은 PK 재사용은0회다.
- CodeReferenceGuard는 다음 고정 매핑을 사용한다. 취소·중지·마감 자료도 검사하며 코드 자신의 NEW 이력은 사용으로 세지 않는다. 모든 그룹은 추가로 TB_SY_CODE_MAP의 group/code 참조를 검사한다. 요청의 테이블명으로 SQL을 만들지 않는다.

|그룹|확인할 컬럼|
|---|---|
|JOB|TB_HR_DEV·TB_HR_CAND의 JOB_CD/SUB_JOB_CD, TB_PJ_MNPW_PLAN·TB_PJ_ASGN·TB_SL_OPP_NEED·TB_SL_RCMD_LOG의 JOB_CD. 다른 미삭제 JOB/SKILL/RSM_JOB_KW 행의 JOB CSV 참조|
|GRADE|TB_HR_DEV·TB_HR_CAND·TB_SL_OPP_NEED의 GRADE_CD, TB_SL_RCMD_LOG.REQ_GRADE_CD|
|JBPS / EXT_PSBL|TB_HR_DEV.JBPS_CD / TB_PJ_ASGN.EXT_PSBL_CD. EXT_PSBL은 ASGN_CHG_TYPE_CD=EXTP인 TB_PJ_ASGN_HIST.BFR_VAL/AFT_VAL의 코드/이름도 검사|
|SGA_ITEM / DRCT_EXPS_ITEM|TB_FI_ITEM.SGA_ITEM_CD / TB_PJ_DRCT_EXPS.DRCT_EXPS_ITEM_CD|
|CHG_ITEM / IMPR / PROJ_STEP|TB_PJ_PROJ_CHG.CHG_ITEM_CD / TB_PJ_ISSUE.IMPR_CD / TB_PJ_STEP.PROJ_STEP_CD|
|CNTC_STAT / CAND_SRC|TB_HR_CAND·TB_HR_CAND_CNTC의 CNTC_STAT_CD / TB_HR_CAND.CAND_SRC_CD|
|ASGN_CHG_RSN / LOSE_RSN|TB_PJ_ASGN_HIST.ASGN_CHG_RSN_CD / TB_PJ_PROJ·TB_SL_OPP의 LOSE_RSN_CD|
|RCMD_EXCL / BANK / INDUTY|TB_SL_RCMD_LOG.RCMD_EXCL_CD / TB_BS_CLNT.BANK_CD / TB_BS_CLNT.INDUTY_CD|
|SKILL|TB_HR_DEV_SKILL·TB_HR_CAND_SKILL의 SKILL_NM. 현재 이름과 해당 코드명 변경 이력의 과거 이름을 함께 검사|
|RSM_LABEL / RSM_JOB_KW / 신규 업무 그룹|공통 표기 대응 참조. 별도 업무 컬럼 참조는 없음|

ALWNC는5.4의 값 코드라 삭제 대상이 아니다. 참조 목록은 서버 코드에 명시하고 실제 사용을 fixture로 확인한다. 삭제 직전 대상 그룹/행을 잠그고 재검사한다.

### 6. 운영 기준값
<!-- speckit:section sec-a76844d74f554f82adfd59dd5e7d4326 -->


#### 6.1 조회·적용월·저장
<!-- speckit:section sec-45ab19636ff74b28a71a52e9bd07de57 -->


- [운영 기준값]은 SQL의43키 전체를 읽어 이름·값·단위·적용 시작월·STD_DESC·[값 변경]/[이력]을 표시한다. 키·이름·단위·설명은 읽기 전용이고 신규 키/삭제 기능은 없다. 목록은 STD_CD순이다.
- 현재값은 APLY_BGN_YM≤현재월 중 가장 최근 행, 이력은 모든 적용월 DESC·현재/예정/지난 상태다. 미래 값은 현재값으로 사용하지 않는다. 해당월 기준이 없으면 ‘기준값 없음’으로 표시하며 임의 기본값 대입은0회다.
- 일반 값 변경은 value/effectiveMonth 필수다. 최소월은 max(CASH_BASE_YM, 마지막 CLOSE월+1), CLOSE가 없으면 CASH_BASE_YM이다. 기본월은 max(현재월,최소월)이다. 최소월 전은409 CLOSED_MONTH다.
- 같은 적용월 행이 있으면 덮어쓸 월/전후를 확인한 뒤 expectedRevision으로 UPDATE, 없으면 INSERT한다. 다른 적용월 행은 유지한다. 한 요청의 변경 키는 중복 불가이며 미변경 칸을 재저장하지 않는다. 키별 전체 이력 revision을 잠금 후 비교한다.
- 묶음 줄은 DUN_D1/2/3, EXT_PRBL_H/M/L/NULL, STEP_PRBL_1/2/3/4, BILL_MISS_RT/MIN, AI_SCORE_JOB/SKILL/CUST/GRADE/PERF/COST/AVAIL/LOC다. 최대4입력/줄·AI8개는2줄이며 저장은 각 STD_CD 행이다. 변경 칸만 같은 적용월로 한 요청에 보낸다.

#### 6.2 값 검사
<!-- speckit:section sec-497e380208604dfa90c62ef2582f6f6b -->


|대상|이번 관리 화면의 검사|
|---|---|
|모든 일반 값|3.2의0~11자리 정수부·최대4소수. 아래 추가조건 없는 키는 이 규칙만 적용|
|DUN_D1/2/3|D1≥1, D1<D2<D3. 일반 최대4소수 범위 적용|
|EXT_PRBL·STEP_PRBL|각0~100 포함|
|AI_SCORE8개|각0~100·합계정확히100. BigDecimal 합으로 비교|
|WTHDR_NOTI_D|1 이상 정수·최대99,999,999,999|
|CASH_BASE_YM|화면 YYYY-MM, API value는 YYYYMM6자리 문자열. 실제 연월·소수 없음. DB는 정수, 읽을 때6자리로 복원|
|CASH_BASE_AMT|3.2의 일반 STD_VAL 정밀도|

묶음은 미변경 키까지 같은 트랜잭션에서 잠그고 변경값과 해당 적용월의 다른 유효값을 합쳐 검사한다. 새 이력으로 영향받는 구간과 이후 예정 기준의 경계월에서도 합100/순서/범위를 검사하고 한 경계라도 위반하면400·전체 롤백이다. 단위나 기준명으로 추가 업무 상한을 추정하지 않는다.

#### 6.3 자금수지 시작 월·기초 잔액
<!-- speckit:section sec-4465e412b15345eb8ef45edbb10259f4 -->


- 두 키는 키마다1행을 유지한다. 적용 시작월 입력 없이 별도 저장 요청으로 즉시 UPDATE, 원래 APLY_BGN_YM은 보존하고 UPD 이력을 남긴다. 일반 이력 요청과 섞으면400이다.
- 저장 전 **기존 CASH_BASE_YM**이 TB_CL_CLOSE에서 CLOSE인지 검사한다. 마감됐으면 두 키 모두 읽기 전용·설명·API409 CLOSED_MONTH다. 시작월을 먼저 바꿔 잠금을 우회하지 못한다.
- 두 행을 함께 잠그고 기존 시작월로 재검사한다. 미마감일 때만 새 연월/잔액을 저장한다. 저장 후 period-context·기준 조회를 갱신하며 자금수지 계산·마감 자료 변경은0건이다.

### 7. 엑셀 표기 대응·변경 이력·공통 조회
<!-- speckit:section sec-870a14193a2f4f6bb527c8f4f005880a -->


#### 7.1 표기 대응
<!-- speckit:section sec-c716ba49005c4f17aa7f2f44f5f07224 -->


- 표 열은 선택·엑셀 표기·그룹·바꿀 코드(코드/이름), 정렬SRC_TEXT/CODE_GRP_ID/MAP_ID순, 기본50·필터sourceText부분일치/groupId다. 빈 상태는 “등록된 표기가 없습니다.”다.
- [추가]의 sourceText는trim 후1~100자, groupId/code 필수·실재 그룹과 active 코드만 허용한다. UK(sourceText,groupId) 중복은409 DUPLICATE_MAPPING이고 다른 코드여도 중복이다. 다른 그룹의 같은 표기는 허용한다. 주/보조·메모·사용 여부·적용일 컬럼은 추가하지 않는다.
- 추가는 즉시 DB/NEW 이력 저장이다. 수정은 삭제 후 재등록한다. 선택 삭제는 확인 후 실제 DELETE·DEL 이력이며 여러 행을 한 트랜잭션으로 처리한다. 누락/변경된 행이 있으면409·전체 삭제0건이다. 삭제된 대응은 이력의 이전 문자열로 식별 가능해야 한다.
- 대응 변경으로 기존 업무 자료를 고친 건수는0건이다. 이번에는 엑셀 파서·파일 업로드/다운로드를 구현하지 않는다.

#### 7.2 변경 이력
<!-- speckit:section sec-615fcab649274fcbbaee420dd3fbb1f6 -->


[변경 이력]은 코드 그룹·코드·값코드·기준값·표기 대응만 조회한다. 필터targetType/groupId·기본전체, 정렬REG_DTTM DESC/HIST_ID DESC, 페이지20/50/100·기본50이다. 열은 일시·대상·그룹/코드/키·유형·항목·이전·이후·처리자이며 긴 값은 펼쳐 읽는다. 신규/값변경/중지/재개/삭제는 NEW/UPD/STOP/RESUME/DEL로 기록한다. codeName 이력의 CHG_ITEM_NM은 CODE_NM으로 고정해 과거 이름을 추적한다. SYSTEM은‘시스템’, admin은‘임시 관리자’로 표시한다. 이력 수정·삭제 API는0개다.

targetType은 GROUP/CODE/VALUE_CODE/OPER_STD/CODE_MAP이며 생략하면 전체, 잘못된 값은400이다. CODE/VALUE_CODE는 그룹의 VAL_CODE_YN으로 구분한다. groupId가 있으면 그 그룹의 그룹/코드/대응 이력만 조회하고 운영 기준값은 제외한다.

TRGT_TBL_NM은 실제 테이블명, TRGT_KEY는 그룹=`groupId`, 코드=`groupId:code`, 대응=`groupId:mapId`, 기준=`key:YYYYMM`이다. UPD는 변경 컬럼별 전후 값, STOP/RESUME는 USE_YN, 코드 DEL은 DEL_YN의 N→Y다. NEW는 항목‘등록’·이전NULL·이후 식별자/이름/값을 남긴다. 대응 NEW/DEL은 sourceText/groupId/code/codeName의 JSON을 이후/이전 값에 각각 남기고 반대쪽은NULL이다. 삭제 후에도 groupId 필터가 동작하도록 키의 정확한 접두 구분자를 사용한다. 이력 값은500자 이내며 조용히 잘라 저장하지 않는다.

#### 7.3 조회 서비스
<!-- speckit:section sec-400a6496a82b4c96a4490862fcb5f574 -->


- CodeQueryService/useCodes(groupId)는 USE_YN=Y·DEL_YN=N 코드를 SORT_ORD/CODE순으로 반환한다. code/name/addVal1~3/codeVal/unitNm을 제공한다. 저장된 중지 코드 label 조회는 includeInactive로 구분하고 신규 선택에 끼워 넣지 않는다.
- OperatingStandardService.get(key,month)는 요청월 이하 최신 적용월의 정확한 값·단위·적용월을 반환한다. 키/해당월 값 없음은404 STANDARD_NOT_FOUND,0이나 하드코딩 값으로 대체하지 않는다. 조회는 DB 값을 읽는 기능이며 계산 엔진을 실행하지 않는다.

### 8. API
<!-- speckit:section sec-fa66457f0a2d498cb8772df126327be8 -->


공통 접두사는 `/api`, JSON camelCase다. 목록은 `{items,total,page,size}`·page1부터, 전체 건수는현재 페이지 크기와 구분한다. codeVal/value는 십진 문자열, 기존 값 변경은 expectedRevision 필수다. 허용되지 않은 필드400, 모드상 금지 필드409다. 처리자·SYS/VAL/DEL·등록시각 주입을 거부한다.

|메서드·경로|입력·결과|
|---|---|
|GET /health|실제DB SELECT·200/실패503|
|GET /current-user|admin/name/roleIds/developmentMode|
|GET /menus|DB 메뉴·상위ID·이름·screenId·순서·icon·pcOnly·children·진단|
|GET /period-context|현재일/연월·timezone·CASH_BASE_YM·마감상태|
|GET /month-close?from=&to=|YYYY-MM 범위·from≤to·이관/마감/작업중 조회|
|GET /me/preferences|설정 value/revision/stored·기본값|
|PUT /me/preferences/{type}/{key}|value/expectedRevision → 검증·upsert|
|DELETE /me/preferences/{type}/{key}|expectedRevision → 해당 설정만 초기화|
|GET /code-groups|그룹·SYS/VAL·sortOrder·미삭제 코드 수|
|POST /code-groups|groupId/name →201·일반 업무 그룹|
|GET /code-groups/{group}/codes|page/size → 미삭제 코드(중지 포함)·추가값/값/단위·groupRevision|
|PUT /code-groups/{group}/codes|expectedRevision/changes:[{code,isNew,...변경필드}] → 그룹 단위 저장. 일반 name/useYn/addVal1/2, 시스템 name, 기존 값코드 codeVal만|
|DELETE /code-groups/{group}/codes/{code}|expectedRevision → 참조 검사·논리 삭제|
|GET /codes/{group}|includeInactive=false 기본·조회용 정확한 값/단위|
|GET /code-mappings|sourceText/groupId/page/size → 목록·행revision|
|POST /code-mappings|sourceText/groupId/code →201|
|POST /code-mappings/delete|items:[{mapId,expectedRevision}] → 원자적 실제삭제|
|GET /operating-standards?month=|생략현재월·43키의 유효값/단위/설명/적용월·keyRevision|
|GET /operating-standards/{key}/history|모든 적용월·현재/예정·revision|
|PUT /operating-standards|effectiveMonth?/changes:[{key,value,expectedRevision}] → 검증·이력·저장.6.3 두키는effectiveMonth없이 별도요청|
|GET /params/{key}?month=|생략현재월·단일 유효값|
|GET /common-code-history|targetType/groupId/page/size → 허용 대상 이력|

새 행 changes는 모드별 필수값을 포함한다. 일반은 code/name/추가값, 값코드는 code/name/codeVal/unitNm이다. 시스템 그룹 신규는 거부한다. 프론트는 서버 필드·미수정 값을 통째로 되돌려 보내지 않는다.

오류는400 VALIDATION_ERROR/INVALID_PERIOD/UNKNOWN_FIELD,404 NOT_FOUND/STANDARD_NOT_FOUND,409 STALE_WRITE/DUPLICATE_GROUP/DUPLICATE_GROUP_NAME/DUPLICATE_CODE/DUPLICATE_MAPPING/SYSTEM_CODE_LOCKED/VALUE_CODE_FIELD_LOCKED/CODE_IN_USE/CLOSED_MONTH/ORDER_LIMIT,500 INTERNAL_ERROR,503 DB_UNAVAILABLE다. 한국어 사유·필드·참조건수를 제공하고 SQL/스택/비밀값은 노출하지 않는다.

## 경계 조건
<!-- speckit:section sec-ea7d9d44148c4a399ea266951d7d6622 -->

| 경계 | 관찰 가능한 처리 | canonical_id |
| --- | --- | --- |
| 시스템/값 플래그 중첩 | SYS_CODE_YN=Y가 우선이다. 기존 이름만 변경 가능하며 값/단위 등 혼합 변경은409·전체롤백이다. | REQ-074 |
| 삭제된 코드 재등록 | DEL_YN=Y라도 같은 PK는409 DUPLICATE_CODE이며 물리행/이력을 보존한다. | REQ-079 |
| 참조/중지/마감 자료 | 현재 active 자료뿐 아니라 중지·취소·마감·CSV·SKILL 과거명·표기 대응의 참조1건도 삭제409다. | REQ-097 |
| 정밀도 상한 | 0~99,999,999,999.9999 허용·소수5자리/정수12자리400·자동반올림0회, JOB 월단가는15자리 정수다. | REQ-017 |
| 빈값·초과문자 | 수당 빈값0과 기준값 빈칸400을 구분하고 초과값을 잘라 저장하지 않는다. | REQ-023 |
| 기간 밖·운영 전 | 실제 연월만 허용하고 범위 역전 시 운영 시작 전 안내·disabled·저장0회다. | REQ-064 |
| 마감된 자금 기준 | 기존 시작월 CLOSE이면 두 키409이며 새 시작월로 변경해 우회할 수 없다. 월 마감 자체 쓰기/확정 후 수정 기능은 없다. | REQ-113 |
| 이미 변경된 revision | 잠금 후 최신값으로 비교해 후행409이며 양쪽NULL 시각만으로 같은 revision이라 하지 않는다. | REQ-030 |
| 같은 값 저장 | 일치 revision+실제변경0이면200 changed=false·수정시각/이력0건이다. | REQ-031 |
| 9번째 탭·축소 | LRU 교체 취소는 새탭0개. UI-27 모바일 직접접근은 권한403이 아니라 PC 안내이며 확대 후 미저장 편집이 복원된다. | REQ-043 |
| 권한 없는 상태의 해석 | 이번 단계는 임시 관리자 전체 허용이므로 인증/역할403을 만들지 않는다. 다만 시스템·참조·마감 검증409는 실제 수행하고 공개 배포를 금지한다. | REQ-027 |
| 손상 설정 | 오류/revision을 내보내고 기본값을 임시 표시한다. 자동DELETE/다른설정변경은 하지 않는다. | REQ-049 |
| 미래 예정 경계 | 현재 적용월뿐 아니라 영향 구간의 예정 경계 하나라도 AI합/순서/범위를 위반하면400·전체 롤백이다. | REQ-110 |
| 기준 없음 | 0이나 하드코딩 값이 아닌 목록 기준값 없음, 단일조회404 STANDARD_NOT_FOUND다. | REQ-128 |
| 저장 후 조회 실패 | 이미 commit되었음을 명시하고 입력을 유지한다. 자동 재저장 없이 다시 불러오기 실패를 별도 안내한다. | REQ-033 |

## Executable Acceptance Contract
<!-- speckit:section sec-6f90d09c8a854f549d3fa8b47a98e618 -->

여기 명령은 후속 구현의 실행 가능한 인수 계약이지 이번 문서 단계의 실행 결과가 아니다. 설치·fixture는 독립 테스트 DB만 사용한다. 실행 전에 OQ-001을 해소하고 원문 SQL/revision을 확보해야 한다. 서비스명을 임의 선택해 compose 개별 실행 명령을 만들지 않는다.

| 사용자 가시 증거 | 명령 또는 HTTP/사용자 행동 | 기대 상태/결과 | 필수 seed/fixture | canonical_id |
| --- | --- | --- | --- | --- |
| 실제 DB health | curl -i http://localhost/api/health | DB 정상200, DB 중단503 DB_UNAVAILABLE | 독립 PostgreSQL/3서비스 compose | REQ-156 |
| 임시 컨텍스트 | curl -i http://localhost/api/current-user | 200·admin/roleIds=[ADMIN]/developmentMode=true | 초기 admin/역할 연결 | REQ-025 |
| DB 메뉴 | curl -i http://localhost/api/menus | 초기10묶음30화면·정상트리·진단 | 메뉴40/역할메뉴150 | REQ-035 |
| 전체 그룹 | curl -i http://localhost/api/code-groups | 64그룹·시스템44·순서/코드수 | V1/V2 초기값 | REQ-069 |
| 수당 정밀값 | curl -i http://localhost/api/codes/ALWNC | ALLOW_TEAM200000/ALLOW_GROUP400000/INS_RATE9.4/SEV_RATE8.33/TEAM_FEE50000/PEXP_FEE50000의 숫자 문자열 | SQL ALWNC6개 | REQ-091 |
| 단일 기준 | curl -i "http://localhost/api/params/CASH_BASE_AMT?month=2026-01" | 해당월 이하 최신 value/단위/적용월, 초기1150000000 | 초기 CASH_BASE 기준행 | REQ-127 |
| 설정 복원 | curl -i http://localhost/api/me/preferences | value/revision/stored·PAGE_SIZE 기본50·MENU_FOLD null | 설정0행 | REQ-047 |
| 빈 대응 | curl -i "http://localhost/api/code-mappings?page=1&size=50" | items=[]·total=0·page=1·size=50 | 대응0행 | REQ-115 |
| 페이지 거부 | curl -i "http://localhost/api/common-code-history?page=0&size=30" | 400，필드와 한국어 사유·requestId | 독립 테스트 DB | REQ-068 |
| 임시 관리자 UI | 브라우저 /UI-27 직접접근·그룹 코드 편집·저장·새로고침 | 실제 DB 변경 및 이력·인증리다이렉트/401/역할403은0 | 일반/시스템/수당 그룹·참조 fixture | REQ-071 |

3서비스 실행/종료 계약: docker compose -f infra/docker-compose.yml up --build -d ; docker compose -f infra/docker-compose.yml ps ; docker compose -f infra/docker-compose.yml down . 기대:3서비스 healthy, volume 삭제 없이 종료. 테스트 명령과 테스트 파일명은 아직 구현되지 않았으므로 지어내지 않고 후속 quickstart.md/tasks.md에서 실제 runner와 대조해 추가한다.

## 성공 기준과 전체 검증 범위
<!-- speckit:section sec-c9078b514f8d433c85975050256df91d -->

원문 326~355행은 합격 판정/시험 근거이지 독립 요구 원장 항목이 아니다. 아래 모든 관찰 결과를 실제 PostgreSQL·API·브라우저로 확인해야 하며 mock/토스트/스크린샷만으로 DB 저장·원자성·공통부품 재사용을 합격 처리하지 않는다.

독립 테스트 DB에서 fixture를 만들고 실제 PostgreSQL·API·브라우저로 확인한다. 수행 명령·관측 결과를 보고하며 미실행은 미검증으로 표시한다. UI-27에 필요한 변경 외 업무 화면을 테스트용으로 추가 생성하지 않는다.

|검사|합격 결과|
|---|---|
|새 DB 구축|V1/V2 적용·JPA validate·68/836/FK73/인덱스78/시퀀스13/CHECK46/UK5 일치, 재시작 중복0건|
|초기값|그룹64/시스템44/코드391/기준43/역할5/메뉴40/권한150/계산규칙94·HQ/관리자 연결. 행정구역0행|
|인증 유예|/UI-27 직접 접근·조회/저장 가능, 인증리다이렉트/401/역할403은0건·처리자admin|
|범위|업무 가짜수치/무동작저장0개·제외기능생성0개. 공통코드4탭의 실제 조회/저장/이력 정상|
|메뉴·경로|10묶음30화면·UI20없음·직접/새로고침/hash호환·잘못된트리 진단/제외·리다이렉트루프0회|
|탭·dirty|대시보드닫기0회·9번째LRU확인·취소보존·단순전환편집유지·그룹변경미저장보호|
|반응형|1280/1024/375/360px에서메뉴/여백/표스크롤·UI27모바일차단·넓히면편집복원|
|설정|즐겨찾기11번째거부·최근중복없이5·PAGE_SIZE50기본/최대100·DENSE/COLS/단위/기간 서버복원·열린목록동기화|
|기간부품|YEAR/YEAR_MONTH 두테스트부모에서controlled재사용·min/max/disabled 이벤트0회·유효선택1회·누계연월일치|
|시스템보호|CTRT_TYPE TK 이름만 저장. 이름+사용변조 혼합요청은409·데이터/이력변경0건|
|코드입력|코드20/그룹30/이름100/그룹명50경계·소문자정규화·중복/삭제PK재사용거부·그룹명경합검사|
|추가값|JOB CSV중복제거·참조/100자·월단가0/15자리허용·소수/음수거부·단계0/100정수·BANK앞0·RSM허용값|
|정밀도|CODE_VAL/STD_VAL 소수4자리정확보존·5자리/정수12자리거부·반올림/버림0회·수당빈값0|
|중지·삭제|중지시active제외/기존label유지·미사용논리삭제·참조1건이면409·일반/값/시스템모드제한|
|대응|동일표기+동일그룹409·다른그룹허용·다중삭제중충돌시전체0건·DEL이력으로삭제대상식별|
|기준이력|현재/미래/과거조회·적용월덮어쓰기확인·다른월유지·최소월이전409·변경칸만저장|
|묶음검증|AI합99/101거부·100허용·DUN역전거부·확률범위검사·예정경계위반시전체롤백|
|CASH두키|기존시작월마감시둘다409·새시작월로우회0회·미마감은같은행갱신·APLY_BGN_YM보존|
|동시수정|같은revision2요청중선행1성공·후행1건409·후행데이터/이력0건·양쪽NULL시각만으로같다고판정하지않음|
|캐시갱신|정상저장성공응답후2초내관련현재브라우저값갱신·프로젝트팀비/경비변경0건|
|오류·재조회|DB중단health503·오류띠/문의번호/재시도·입력보존·저장후조회실패구분·자동재저장0회|
|통합완료|공통컴포넌트실제사용·프론트빌드/백엔드/DB/API/UI검증통과·compose3서비스healthy·/api/health200|

완료 보고: 생성/확장 경로·공통 부품Props·SQL revision/래퍼 정리·실제 검증 결과·실행 방법·미검증 항목. mock·토스트·스크린샷만으로 DB 저장·트랜잭션·재사용을 합격 처리하지 않는다.

위 합격표는 해당 요구의 AC를 대체하지 않는다. 특히 정밀도·참조 가드의 모든 고정 매핑·그룹명 경합·두NULL시각 revision·설정 PK 경합·예정경계 검증을 fixture로 각각 수행한다. 수행 명령/관측 결과·SQL revision/해시·공통 Props/사용처·실행/종료법·미검증 항목을 최종 구현 보고에 남긴다.

## 가정·의존성·확인 필요
<!-- speckit:section sec-b17324db381c40f5b4616b3f0a994371 -->

검토 후보에 숨은 승인 가정은 없다. 이 문서 단계는 현재 폴더에 제품 소스/SQL이 없고 Git 저장소가 아닌 상태에서 제공된 원문으로 작성했다. 외부 SQL/HTML의 실제 내용, 실제 DB 수량/시각 토큰·UI 일치·빌드는 미검증이다. 제공된 SQL/레퍼런스를 확보할 때 원문과 다르면 추정 수정하지 않고 보고한다.

| 확인 항목 | 근거·질문 | 후속 영향 |
| --- | --- | --- |
| OQ-001 | L30은 Java21·Spring Boot3.5.x, L372/L398은 Java17·3.3.x다. 어느 두 버전을 최종 채택할지 명시 승인이 필요하다. 고정 계약은 변경하지 않는다. | build-ready 차단. 실행·영속성 jpa/blocking 결정은 유지한다. |
| OQ-002 | 원문 DDL/data의 현재 revision·해시와 실제 사용자/행정구역 물리 테이블명 전체는 미확보다. SQL 조회 후 원문 구조와 대조한다. | 전체68테이블 필드/제약을 추정 생성하지 않으며 후속 데이터 설계 전 확인한다. |
| OQ-003 | 원문 오류 {code,message,field?,details?,requestId}와 이 호출의 required success/meta 봉투를 함께 보존할 정확한 내부 위치를 후속 OpenAPI에서 명시한다. | ApiError 전체를 성공 봉투에 중첩하지 않으며 사용자에게 없는 정책/오류를 발명하지 않는다. |
| OQ-004 | 삭제/상태/금액 검증에서 원문이 주지 않은 추가 기대 숫자·실제 입력 외 경계 정책은 확인 필요다. 원문 수치와 확정409/400/404만 사용한다. | 추정 상한·역할·만료·재시도·이력 삭제 정책으로 바꾸지 않는다. |
| OQ-005 | 서로 다른 API가 UI-27와 물리테이블을 공유한다. naming ledger에는 API 경로마다 가짜 화면·테이블을 만들지 않고 N/A/공유명 참조를 썼다. | 후속 OpenAPI 생성 시 실제 path당1행·operationId 존재와 공유 대상 링크를 대조한다. |
| OQ-006 | 현재 request_key 원문 필드가 제공되지 않았다. BASIC-129는 요청 제목의 식별자이지 자동으로 request_key라고 추정하지 않는다. | 후속 data-model.json.meta.request_key는 호스트의 실제 current request_key를 확인한다. |

## 요구 회계
<!-- speckit:section sec-47c138f16eb24529a7b349e1b9e4e140 -->

| canonical_id | status | evidence |
| --- | --- | --- |
| REQ-001 | data-constraint | 전체 SQL 원천 보존 — HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |
| REQ-002 | data-constraint | 행정구역 빈 테이블 — 설치 후 행정구역 테이블 데이터 건수 0행이다. |
| REQ-003 | data-constraint | 전체 DB 구조 보존 — 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |
| REQ-004 | data-constraint | 기본 초기값 적재 — 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |
| REQ-005 | data-constraint | 초기 자금 기준과 계산규칙 — CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |
| REQ-006 | data-constraint | Flyway 실행본 래퍼 정리 — 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. |
| REQ-007 | data-constraint | 마이그레이션 불변·멱등성 — 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |
| REQ-008 | data-constraint | 설치 실패와 구조 은폐 금지 — 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |
| REQ-009 | data-constraint | 구조 차이 보고 — 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |
| REQ-010 | data-constraint | 설치 후 쓰기 경계 — 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |
| REQ-011 | data-constraint | 서버 감사 필드와 연월 저장 — REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |
| REQ-012 | screen-only | 공통 컴포넌트 실제 재사용은 공통 셸/화면 동작이다. 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. |
| REQ-013 | covered | saveGroupCodes / saveOperatingStandards — 문자열 입력 검증의 공통 적용 계약; REQ-013 |
| REQ-014 | covered | saveGroupCodes / saveOperatingStandards — 그룹ID 정규화의 공통 적용 계약; REQ-014 |
| REQ-015 | covered | saveGroupCodes / saveOperatingStandards — 코드 정규화의 공통 적용 계약; REQ-015 |
| REQ-016 | covered | saveGroupCodes / saveOperatingStandards — 명칭 경계와 식별자 불변의 공통 적용 계약; REQ-016 |
| REQ-017 | covered | saveGroupCodes / saveOperatingStandards — 정밀 십진값 범위의 공통 적용 계약; REQ-017 |
| REQ-018 | covered | saveGroupCodes / saveOperatingStandards — 숫자 입력 정리와 금지값의 공통 적용 계약; REQ-018 |
| REQ-019 | covered | saveGroupCodes / saveOperatingStandards — 소수 원값 보존의 공통 적용 계약; REQ-019 |
| REQ-020 | covered | saveGroupCodes / saveOperatingStandards — JOB 월단가 정수의 공통 적용 계약; REQ-020 |
| REQ-021 | covered | saveGroupCodes / saveOperatingStandards — 실제 연월 검증의 공통 적용 계약; REQ-021 |
| REQ-022 | covered | saveGroupCodes / saveOperatingStandards — Y/N·JSON 타입의 공통 적용 계약; REQ-022 |
| REQ-023 | covered | saveGroupCodes / saveOperatingStandards — 빈 수당·기준값 구분의 공통 적용 계약; REQ-023 |
| REQ-024 | screen-only | 공통 표시 함수은 공통 셸/화면 동작이다. 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’ |
| REQ-025 | covered | getCurrentUser (GET /api/current-user) — 임시 사용자 컨텍스트 |
| REQ-026 | covered | getCurrentUser — 처리자 주입 거부의 공통 적용 계약; REQ-026 |
| REQ-027 | covered | getCurrentUser — 인증·권한 유예의 공통 적용 계약; REQ-027 |
| REQ-028 | covered | saveGroupCodes / saveOperatingStandards / deleteCodeMappings — 원자적 업무 변경의 공통 적용 계약; REQ-028 |
| REQ-029 | covered | saveGroupCodes / saveOperatingStandards — 불투명 revision의 공통 적용 계약; REQ-029 |
| REQ-030 | covered | saveGroupCodes / saveOperatingStandards — 잠금 후 동시 수정 검출의 공통 적용 계약; REQ-030 |
| REQ-031 | covered | saveGroupCodes / saveOperatingStandards / deleteCodeMappings — 실제 변경 없는 저장의 공통 적용 계약; REQ-031 |
| REQ-032 | covered | saveGroupCodes / getCodes — commit 후 캐시 최신화의 공통 적용 계약; REQ-032 |
| REQ-033 | screen-only | 한국어 오류와 재조회 실패 구분은 공통 셸/화면 동작이다. 500/연결 실패는 오류 띠·재시도 |
| REQ-034 | screen-only | 중복 클릭·자동 재저장 금지은 공통 셸/화면 동작이다. 처리 중 반복 클릭의 추가 요청0건 |
| REQ-035 | covered | getMenus (GET /api/menus) — DB 메뉴 구성·정렬 |
| REQ-036 | screen-only | 메뉴 펼침·접힘 탐색은 공통 셸/화면 동작이다. 부모 이름은 화면 이동, 펼침 아이콘은 토글 |
| REQ-037 | screen-only | 잘못된 메뉴 트리 진단은 공통 셸/화면 동작이다. 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외 |
| REQ-038 | screen-only | 공식 라우트와 복구은 공통 셸/화면 동작이다. /UI-27 새로고침 및 #UI-27 replace 성공 |
| REQ-039 | screen-only | PC 전용 화면 보호은 공통 셸/화면 동작이다. 767px UI-27 본문 대신 안내·이동 수단 |
| REQ-040 | screen-only | 반응형 공통 레이아웃은 공통 셸/화면 동작이다. 1280px 메뉴240/64px·여백24px |
| REQ-041 | screen-only | 헤더의 실제 제공 항목은 공통 셸/화면 동작이다. AI/알림 클릭은 준비 중이고 가짜 배지/대화0개 |
| REQ-042 | screen-only | 화면 제목·기간 배치은 공통 셸/화면 동작이다. UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. |
| REQ-043 | screen-only | 화면 탭과 LRU은 공통 셸/화면 동작이다. 대시보드 닫기 불가 |
| REQ-044 | screen-only | 편집 보존과 이동 경고은 공통 셸/화면 동작이다. 계속 작성 시 입력/필터/기간/스크롤 유지 |
| REQ-045 | screen-only | 접근 가능한 모달·입력은 공통 셸/화면 동작이다. 모달 내 Tab 순환·닫기 후 원래 포커스 |
| REQ-046 | screen-only | 새로고침 탭 초기화은 공통 셸/화면 동작이다. 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. |
| REQ-047 | covered | getPreferences (GET /api/me/preferences) — 설정 조회와 기본값 |
| REQ-048 | covered | putPreference (PUT /api/me/preferences/{type}/{key}) — 설정 명시 저장 |
| REQ-049 | screen-only | 손상 설정 격리은 공통 셸/화면 동작이다. 손상 항목 조회는 오류와 revision |
| REQ-050 | covered | deletePreference (DELETE /api/me/preferences/{type}/{key}) — 개별 설정 초기화 |
| REQ-051 | screen-only | 즐겨찾기 편집은 공통 셸/화면 동작이다. 10개 저장 후 재조회 순서 일치 |
| REQ-052 | screen-only | 최근 화면 자동 갱신은 공통 셸/화면 동작이다. 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |
| REQ-053 | screen-only | 메뉴 접힘 기억은 공통 셸/화면 동작이다. PC 접힘 저장 후 재조회 true |
| REQ-054 | screen-only | 목록 크기 설정은 공통 셸/화면 동작이다. 정수100 허용 |
| REQ-055 | screen-only | 밀도 설정 동기화은 공통 셸/화면 동작이다. 좁게 저장 후 열린 목록 모두 좁게 |
| REQ-056 | screen-only | 표 열 설정 저장은 공통 셸/화면 동작이다. 유효 order/hidden은 재조회 일치 |
| REQ-057 | screen-only | 금액 단위 선택 기억은 공통 셸/화면 동작이다. 백만원 선택 후 재조회 백만원 |
| REQ-058 | screen-only | 설정 화면ID·표시 보호은 공통 셸/화면 동작이다. 미등록 screenId 저장 거부 |
| REQ-059 | screen-only | UI-27 열 정의 제한은 공통 셸/화면 동작이다. codes의 code/name 숨김400 |
| REQ-060 | screen-only | 설정 적용과 실패 보존은 공통 셸/화면 동작이다. 설정 실패 시 이전 밀도 유지·오류 안내 |
| REQ-061 | screen-only | 기간 설정 타입·범위 검사은 공통 셸/화면 동작이다. NONE PERIOD PUT은400 |
| REQ-062 | screen-only | controlled 기간 부품은 공통 셸/화면 동작이다. YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회 |
| REQ-063 | covered | getPeriodContext (GET /api/period-context) — 화면 기간 선택·기억 |
| REQ-064 | screen-only | 기간 범위와 운영 전 상태은 공통 셸/화면 동작이다. YEAR는 시작연도~현재연도+1 |
| REQ-065 | screen-only | 연도 배지은 공통 셸/화면 동작이다. 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다. |
| REQ-066 | covered | getMonthClose (GET /api/month-close) — 실제 월 마감 조회 |
| REQ-067 | screen-only | UI-27 네 탭과 조회 상태은 공통 셸/화면 동작이다. 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. |
| REQ-068 | covered | getGroupCodes / getCodeMappings / getCommonCodeHistory — 표 페이지 계약의 공통 적용 계약; REQ-068 |
| REQ-069 | covered | getCodeGroups (GET /api/code-groups) — 그룹 목록과 선택 |
| REQ-070 | covered | getGroupCodes (GET /api/code-groups/{group}/codes) — 일반 코드 조회 |
| REQ-071 | covered | saveGroupCodes (PUT /api/code-groups/{group}/codes) — 일반 코드 일괄 변경 저장 |
| REQ-072 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-072 — 편집 취소 복원 |
| REQ-073 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-073 — 부분 변경·중복 코드 검사 |
| REQ-074 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-074 — 시스템 코드 변경 제한 |
| REQ-075 | covered | UI-27 / 일반 업무 그룹 추가 / REQ-075 — 그룹 중복과 채번 경합 |
| REQ-076 | covered | createCodeGroup (POST /api/code-groups) — 업무 그룹 생성 |
| REQ-077 | covered | UI-27 / 일반 업무 그룹 추가 / REQ-077 — 그룹 추가 전 편집 처리 |
| REQ-078 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-078 — 일반 신규 코드 편집 행 |
| REQ-079 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-079 — 그룹 내 코드 PK 재사용 금지 |
| REQ-080 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-080 — 신규 코드 기본값·정렬 채번 |
| REQ-081 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-081 — 정렬 순번 한계 |
| REQ-082 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-082 — 코드명 중복 허용 |
| REQ-083 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-083 — 그룹별 추가값 필수·길이 |
| REQ-084 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-084 — PROJ_STEP 진척 정수 |
| REQ-085 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-085 — BANK 코드와 RSM 라벨 |
| REQ-086 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-086 — RSM_LABEL 허용값 |
| REQ-087 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-087 — CSV 정규화·참조 |
| REQ-088 | covered | UI-27 / 수당 값 코드 편집 / REQ-088 — 수당 코드 조회·편집 형태 |
| REQ-089 | covered | UI-27 / 수당 값 코드 편집 / REQ-089 — 값 코드 잠금 |
| REQ-090 | covered | UI-27 / 수당 값 코드 편집 / REQ-090 — 수당 신규 코드 저장 |
| REQ-091 | covered | UI-27 / 수당 값 코드 편집 / REQ-091 — 수당 값 변경과 업무 불변 |
| REQ-092 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-092 — 일반 코드 중지·재개 |
| REQ-093 | covered | UI-27 / 그룹의 일반·시스템 코드 편집 / REQ-093 — 사용 상태 변경 이력 |
| REQ-094 | covered | UI-27 / 미사용 일반 코드 삭제 / REQ-094 — 삭제 참조 차단 |
| REQ-095 | covered | deleteGroupCode (DELETE /api/code-groups/{group}/codes/{code}) — 일반 코드 논리 삭제 |
| REQ-096 | covered | UI-27 / 미사용 일반 코드 삭제 / REQ-096 — 코드 삭제 이력 |
| REQ-097 | covered | UI-27 / 미사용 일반 코드 삭제 / REQ-097 — 고정 참조 가드 |
| REQ-098 | covered | UI-27 / 미사용 일반 코드 삭제 / REQ-098 — 삭제 직전 재검사 |
| REQ-099 | covered | getOperatingStandards (GET /api/operating-standards) — 운영 기준 전체 조회 |
| REQ-100 | covered | getOperatingStandardHistory (GET /api/operating-standards/{key}/history) — 기준 현재·과거·미래 이력 |
| REQ-101 | covered | UI-27 / 운영 기준값 변경 / REQ-101 — 일반 기준 적용 최소월 |
| REQ-102 | covered | saveOperatingStandards (PUT /api/operating-standards) — 기준 월별 추가·덮어쓰기 |
| REQ-103 | covered | UI-27 / 운영 기준값 변경 / REQ-103 — 기준 변경 키 중복 거부 |
| REQ-104 | covered | UI-27 / 운영 기준값 변경 / REQ-104 — 묶음 기준 표시 |
| REQ-105 | covered | UI-27 / 운영 기준값 변경 / REQ-105 — DUN 순서 검증 |
| REQ-106 | covered | UI-27 / 운영 기준값 변경 / REQ-106 — 확률 범위 검증 |
| REQ-107 | covered | UI-27 / 운영 기준값 변경 / REQ-107 — AI 점수 합계 검증 |
| REQ-108 | covered | UI-27 / 운영 기준값 변경 / REQ-108 — 철수 통보일 정수 |
| REQ-109 | covered | UI-27 / 자금 시작월·기초잔액 변경 / REQ-109 — 자금 시작 연월 표현 |
| REQ-110 | covered | UI-27 / 운영 기준값 변경 / REQ-110 — 기준 전체 영향구간 검사 |
| REQ-111 | covered | UI-27 / 자금 시작월·기초잔액 변경 / REQ-111 — 자금 두 키 즉시 갱신 |
| REQ-112 | covered | UI-27 / 자금 시작월·기초잔액 변경 / REQ-112 — 자금 기준 변경 이력 |
| REQ-113 | covered | UI-27 / 자금 시작월·기초잔액 변경 / REQ-113 — 기존 시작월 마감 잠금 |
| REQ-114 | covered | UI-27 / 자금 시작월·기초잔액 변경 / REQ-114 — 자금 동시 잠금·후속 갱신 |
| REQ-115 | covered | getCodeMappings (GET /api/code-mappings) — 표기 대응 목록·검색 |
| REQ-116 | covered | UI-27 / 엑셀 표기 대응 등록 / REQ-116 — 대응 입력·중복 검사 |
| REQ-117 | covered | createCodeMapping (POST /api/code-mappings) — 대응 즉시 등록 |
| REQ-118 | covered | UI-27 / 엑셀 표기 대응 등록 / REQ-118 — 대응 등록·삭제 이력 |
| REQ-119 | covered | deleteCodeMappings (POST /api/code-mappings/delete) — 대응 다중 물리 삭제 |
| REQ-120 | covered | UI-27 / 엑셀 표기 대응 선택 삭제 / REQ-120 — 대응 삭제 충돌 |
| REQ-121 | covered | UI-27 / 엑셀 표기 대응 등록 / REQ-121 — 대응 변경의 업무 불변 |
| REQ-122 | covered | getCommonCodeHistory (GET /api/common-code-history) — 변경 이력 조회 |
| REQ-123 | covered | getCommonCodeHistory — 변경 유형과 과거 이름 추적의 공통 적용 계약; REQ-123 |
| REQ-124 | covered | UI-27 / 공통코드 변경 이력 열람 / REQ-124 — 이력 필터 도메인 |
| REQ-125 | covered | getCommonCodeHistory — 이력 키·전후값 무손실의 공통 적용 계약; REQ-125 |
| REQ-126 | covered | getCodes (GET /api/codes/{group}) — 공통 active 코드 조회 |
| REQ-127 | covered | getParameter (GET /api/params/{key}) — 월별 단일 기준 조회 |
| REQ-128 | covered | getParameter — 기준 미존재 거부의 공통 적용 계약; REQ-128 |
| REQ-129 | covered | getCurrentUser / saveGroupCodes / getHealth — API 필드와 서버 필드 보호의 공통 적용 계약; REQ-129 |
| REQ-130 | covered | getCurrentUser / saveGroupCodes / getHealth — 공통 오류 반환과 비밀 보호의 공통 적용 계약; REQ-130 |
| REQ-131 | data-constraint | 변경 이력 무기한 보존 — 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |
| REQ-132 | non-functional | 원문 구현 기술 요구 — 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |
| REQ-133 | non-functional | 3서비스 실행과 SPA — /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |
| REQ-134 | non-functional | 서울 시간대 일관성 — 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |
| REQ-135 | non-functional | Flyway 전용 스키마·정밀도 — JPA validate 통과·Hibernate DDL 생성0회·float/double 금액변환0회다. |
| REQ-136 | non-functional | 마이그레이션 파일 경로 — backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |
| REQ-137 | non-functional | 프론트 구조와 계층 책임 — UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |
| REQ-138 | non-functional | 고정 technology_stack — React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |
| REQ-139 | non-functional | 고정 Version BOM — BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |
| REQ-140 | non-functional | 고정 required_outputs — backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |
| REQ-141 | non-functional | 레퍼런스 시각 계약 — sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |
| REQ-142 | non-functional | Docker 빌드 컨텍스트 제외 — 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |
| REQ-143 | non-functional | 격리 환경만 배포 — 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |
| REQ-144 | non-functional | 실행·영속성 결정 보존 — resolved/imperative/jpa/blocking/explicit 및 blocking_priority/conflicts가 변경 없이 후속 plan.md로 복사된다. |
| REQ-145 | non-functional | 실제 백엔드 산출물 — backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |
| REQ-146 | non-functional | 프론트·API 함께 검증 — 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |
| REQ-147 | out-of-scope | 후속 업무 화면 경계 — UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. |
| REQ-148 | out-of-scope | 월 마감 쓰기 제외 — 월 마감/해제/스냅샷 생성은 구현하지 않는다. |
| REQ-149 | out-of-scope | 외부·출력·외형 확장 제외 — 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. |
| REQ-150 | out-of-scope | UI-20·단가·근태 제외 — UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. |
| REQ-151 | out-of-scope | 행정구역 파일 작업 제외 — 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. |
| REQ-152 | out-of-scope | 계산 엔진과 규칙 편집 제외 — 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. |
| REQ-153 | out-of-scope | 그룹 변경·삭제 제외 — 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. |
| REQ-154 | out-of-scope | 기준 키 CRUD 확장 제외 — 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. |
| REQ-155 | out-of-scope | 이력 변경·삭제 제외 — 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. |
| REQ-156 | covered | getHealth (GET /api/health) — 실제 DB health 조회 |

## 확정 처리 규칙

<!-- decision-answer:524666e4bdc5de47:start -->
- REQ-150 확정 처리 규칙: UI-20 지급관리 화면, 일/시간 단가 기능, 근태 업로드 기능의 생성 수가 각각 0개인 것을 합격 기준으로 제안합니다.
<!-- decision-answer:524666e4bdc5de47:end -->
<!-- decision-answer:759e93bc9ee97f19:start -->
- REQ-152 확정 처리 규칙: 계산규칙 테이블과 데이터는 원문 대비 누락·불일치가 각각 0개이고, 계산규칙 편집 화면과 계산 실행 엔진의 생성 수는 각각 0개인 것을 합격 기준으로 제안합니다.
<!-- decision-answer:759e93bc9ee97f19:end -->
<!-- decision-answer:ac16251802c91956:start -->
- REQ-149 확정 처리 규칙: 외부·출력·외형 확장 제외 처리 중 하나라도 실패하면 처리한 내역 전체를 되돌린다.
<!-- decision-answer:ac16251802c91956:end -->

