# UI-03 공통 표시·설정 인계

## 범위와 보존

이 변경은 공통 표 옵션, 표시 형식, UI-03 열설정과 기간 호환을 제공한다. 사람별 기간집계 API/필터/요약/원장/XLSX 및 최종 본문 조립은 후속 그룹 책임이다. 등록된 `shared/IndividualProfitabilityScreen.tsx`와 `/UI-03` 경로를 유지한다. `screenRegistry.ts`, `app/router.tsx`, 메뉴, 작업탭 호스트, 즐겨찾기 저장·이동은 변경하지 않았다.

선행 코드의 22열 메타데이터, UI-03 설정 등록, PreferenceService 기본값 열거 및 기존 원가/급여 계산 코드는 보존했다. 신규 DB 테이블/컬럼/마이그레이션/seed/원가 원천 쓰기는 없다. 설정은 명시적인 기존 API 호출만 사용한다.

## UI 계약 체크리스트

- `/UI-03`: 기존 SharedScreenFrame의 제목/즐겨찾기/YEAR/작업탭을 유지한다. entry의 선택 `children`으로 본문을 조립할 수 있다. 후속 화면을 연결하기 전에는 기존 안내와 설정 진입을 유지하며, 가짜 원가행을 표시하지 않는다.
- 열설정: 기존 ColumnPreferencesDialog, PreferencesProvider, useCommitRefresh, 상대경로 preference API를 재사용한다. UI-03 표식은 `SCR-UI-03-M1`, 기존 UI-27 표식은 `SCR-UI-27-M9`다. 전체 22열 순서, 선택 숨김, 기본 숨김 4열, 필수 name/revenue/totalCost/profit를 보존한다. 적용만 저장하고 취소는 초안을 폐기한다. 실패 시 Dialog가 초안과 오류를 유지한다.
- 기간: UI-03만 운영시작연도~올해다. 서버와 ScreenPeriodBar 모두 내년을 거부한다. 범위 밖 저장기간은 안내/임시 기본값만 표시하며 자동 수정하지 않는다. 다른 YEAR 화면의 내년 허용은 그대로다.
- 단위: formatAmount는 원 정수 문자열을 자릿수 이동만 하여 표시한다. 기본 천원, 원/천원/백만원의 최대 소수 0/3/6, 뒤 0 제거, NULL `–`, 유효 0 `0`, 음수 `−`를 구분한다. 부동소수점 환산·추가 반올림은 없다.
- MM/율/날짜: formatMm/formatRate/formatDate를 사용한다. MM 2자리와 율 1자리는 서버가 확정한 수치의 표시 자릿수만 유지한다. LocalDate는 브라우저 시간대로 이동시키지 않는다. ProfitabilityValue는 음수에 기존 `--bad` 토큰 색을 사용한다.

## 후속 화면용 공통 표 인터페이스

`shared/DataTable.tsx`의 기존 props는 유지된다. 새 props는 모두 선택이다.

- `expandableRow`: `expandedKeys`, `onExpandedChange(Key[])`, `render(row)`, 선택 `label(row)`, `canExpand(row)`. 화면이 상태를 소유하며 행키는 반드시 도메인 식별자다. 펼침 버튼은 행 클릭으로 전파하지 않는다. 조회/로딩/오류는 화면이 공급하는 renderer와 기존 Feedback을 사용한다. 상세 패널은 올바른 colspan, aria-expanded/controls/region을 갖는다.
- `footer`: `{label, render(column)}`. 필터 전체 서버 totals를 열별로 그리는 콜백이다. DataTable은 현재 페이지나 자식행을 합산하지 않는다. 이름 열의 ‘전체 합계’ 문구도 소비자가 공급한다. 로딩 중에는 이전 합계를 표시하지 않는다.
- `stickyColumns`: `{id,width}` 배열. width는 px이며 현재 보이는 열 순서대로 offset을 계산한다. 숨긴 열은 offset에 포함하지 않는다. 선택/펼침 작업열은 각각 44px로 별도 취급하며 설정 열에 넣지 않는다. 기존 표에는 지정하지 않으면 고정열을 추가하지 않는다.
- `testId`: 여러 표가 같은 화면에 있으면 고유 kebab-case 접두사를 지정한다.

`pages/profitability/ProfitabilityTable.tsx`는 위 표를 감싸 UI-03 COLS의 순서/숨김과 공통 DENSE/PAGE_SIZE를 연결한다. 실제 overview가 API에서 받은 `columns/rows/rowKey`, `expandableRow`, `footer`, `stickyColumns`, 정렬/페이지 콜백을 공급해야 한다. 원가 금액·MM·율을 이 부품에서 계산하지 않는다. 후속 overview가 모바일 크기나 페이지 리셋을 제어할 때는 size/onSizeChange를 전달한다. AMT_UNIT은 기존 useTablePreferences에서 읽어 ProfitabilityValue의 unit에 전달한다.

`ProfitabilityTable.test.tsx`는 등록된 실제 entry의 children 안에 이 부품을 렌더하여 위 콜백과 설정, 탭 왕복의 마운트/펼침 보존을 검사한다. 이는 테스트 API 대역을 사용하는 컴포넌트 시험이며 실제 원가 API 또는 실제 브라우저 수락 영수증이 아니다. 필터·보기의 업무 동작은 해당 본문을 구현하는 후속 그룹의 시험에서도 검증해야 한다. UI 상태를 URL 변경이나 작업탭 활성화만으로 초기화하는 effect를 추가하지 말 것.

## 설정 변경 경로 원장

| 경계 | 확인한 실제 경로 |
| --- | --- |
| 계약 | getPreferences GET `/api/me/preferences`, putPreference PUT `/api/me/preferences/{type}/{key}`; 기존 success/data/meta 봉투와 400/409/500 오류 유지 |
| 진입점 | PreferenceController.getPreferences(Map), putPreference(String,String,JsonNode,Map) |
| 인증/권한 | caller 인증 유예 유지. CurrentUserProvider.userId()의 DB 임시 관리자 문맥으로 행 scope를 결정. 새 역할 guard/로그인/계정 안내 생성 없음 |
| 서비스 | PreferenceService.getPreferences() readOnly; putPreference(String,String,PutPreferenceRequest) transactional; validator 검증 후 기존 composite id lock/revision 비교 |
| 저장소 | PreferenceMapper.list/lock/insert/update/delete 및 mappers/me/PreferenceMapper.xml 그대로 재사용 |
| 스키마 | V1__full_schema.sql의 TB_SY_USER_PREF: PK(USER_ID,PREF_TYPE_CD,PREF_KEY), PREF_VAL VARCHAR(2000), REG/MOD 감사열. 별도 revision/order/hidden 컬럼 없음 |
| 구체화 | StoredPreference 8개 record 필드에 quoted camelCase alias 대응; revision은 기존 RevisionService 계산 |
| 부수효과 | 유효한 명시 저장만 한 설정 행 변경 및 기존 afterCommit 이벤트. 조회·잘못된 열설정은 쓰기 없음. 다른 화면 키/공통 키 불변 |
| 시험 | PreferencesContractTest는 실제 PreferenceController/Service/Validator + mapper 대역. ProfitabilityPreferencesDatabaseTest는 실제 기존 mapper/PostgreSQL/MockMvc |

## Runner 검증 대상

시험/빌드/설치/컨테이너/서버/네트워크 명령은 실행하지 않았다. `git diff --check`의 정적 공백 검사만 수행했으며 컴파일/실행 통과를 의미하지 않는다. 상태는 awaiting runner verification이다.

- 백엔드: PreferencesContractTest, ProfitabilityPreferencesDatabaseTest, 기존 PreferencesDatabaseTest 회귀.
- 프런트: shared/CommonComponents.test.tsx, shared/format.test.ts, pages/me/usePreferences.test.tsx, pages/profitability/ProfitabilityColumnSettings.test.tsx, pages/profitability/ProfitabilityTable.test.tsx, pages/shared/SharedScreens.test.tsx, app/router.test.tsx.
- 프로젝트 기존 npm typecheck/test/build와 Maven 시험은 runner가 수행한다. 새 의존성/lockfile은 만들거나 설치하지 않았다.

새 PostgreSQL 시험은 기존 독립 baseline의 `SICOST_COST_TEST_*` 연결을 사용하되 `SICOST_COST_TEST_BASELINE_CONFIRMED=true`와 별도의 `SICOST_PREF_TEST_WRITES_CONFIRMED=true`가 모두 있어야 실행한다. 두 번째 표시는 해당 독립 복사본의 시험 계정에 fixture 사용자 및 설정 쓰기가 허용됨을 runner가 명시 확인하는 gate다. 읽기 전용 원가 시험 연결에서는 이 gate를 주지 않는다. 자동 설치/DDL/seed 재실행은 없다.

시험은 매번 새로운 userId/email을 소유하고 기존 사용자에서 유효 필수 필드만 DB 내부 복사한다. 공유 seed 사용자나 설정을 변경하지 않으며 자신이 저장한 UI-03 설정만 삭제한다. 모든 쓰기는 단일 rollback-only 트랜잭션에 격리하고 종료 후 사용자/설정 잔존 0행을 확인한다. 실제 PostgreSQL PK와 PREF_VAL 길이도 catalog로 검사한다. 환경 gate가 없어서 건너뛴 시험은 성공으로 보고하지 않는다.
