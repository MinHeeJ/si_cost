# UI-03 공통부품·최종 조립 인계

상태: awaiting runner verification. 기존 React18/React Router6/Vite5와 공통 CSS 토큰을 사용한다. 시안 재분석/새 스타일 시스템/라우터/메뉴 재생성은 없다.

## 실제 진입점과 화면 계약

`frontend/src/pages/shared/screenRegistry.ts`의 UI-03 → 기존 `shared/IndividualProfitabilityScreen.tsx` → `SharedScreenFrame`(제목/즐겨찾기/기간) → `profitability/IndividualProfitabilityScreen.tsx` 본문이다. /UI-03 및 기존 작업 탭의 keyed mount를 유지한다. router.tsx에 두 번째 경로나 등록을 추가할 필요가 없다.

- 목적: 개인 기간 원가·개인 단가·이익을 서버 결과로 조회한다. 기본 YEAR 범위는 서버 운영시작~올해다. 단가는 실제 회사 청구매출이 아닌 개인 배분 참고액이다.
- 주요 행동: 조회/Enter, 초기화, 누계·월평균, 상세조건/TK·적자·동시투입, 행 펼침, 개발자 이름 상세, 열/단위 설정, 전체 XLSX, 하단 계산 방법.
- 필터는 초안만 수정하며 조회 때 적용한다. 금액·MM·공제·분모는 편집 불가다. 업무 저장/마감/급여 업로드/새 개발자 편집 버튼을 만들지 않는다.
- 로딩/조건0건/자료없음/페이지초과/HTTP오류/예정/부분미확인을 구별한다. 임시 ADMIN 전체열·기능 제공이며 실제 역할별403/금액 가림은 이번 승인 계약에서 유예다.
- 보기/필터/페이지/펼침 상태는 마운트된 작업 탭에 속한다. 조건 변경은 page1/펼침해제, 평균 부모의 월원장은 항상 평균 전 원본이다. PC 엑셀은 현재 적용 조건, 모바일은 카드20건/더보기·다운로드 미호출이다.

## 재사용 경로와 Props

아래 경로는 별도 표시가 없으면 `frontend/src/pages/profitability/` 상대경로다.

| 경로/컴포넌트 | Props/결과 | 실제 사용처/책임 |
| --- | --- | --- |
| IndividualProfitabilityScreen.tsx | onPerson?(personId), renderLedger?(person,query), renderExport?(query,data), renderCalculationGuide?(data) | shared 진입점이 본문을 마운트. 기본 ledger/details는 본문이 재사용하고 export/guide 슬롯은 진입점이 공급한다. 외부 children/slot 주입 호환도 유지한다. |
| ProfitabilityFilters.tsx | query,onApply,onReset,loading,fieldMessages,onEmployeeTypes? | 본문에서 초안/DB 코드·마스터 옵션/Enter 검증을 표시. query에는 서버 API 요청만 담는다. |
| ProfitabilitySummary.tsx | summary:ProfitabilitySummaryData, unit:AmountUnit | 본문 요약5칸. 서버 평균/인원/율을 표시하고 필터독립 회사지표를 구별한다. |
| ProfitabilityContributionTable.tsx | cells:ProfitabilityContribution[], unit | 본문 공헌이익. 기본접힘, DB 직무/등급 표제와 서버 셀금액/인원. 클라이언트 평균 계산 없음. |
| ProfitabilityTable.tsx | ProfitabilityTableProps<T> (DataTableProps에서 설정/dense/testId 제외) | 본문 부모표. 공통 useTablePreferences와 ColumnPreferencesDialog, 기존 단위/밀도/열순서 유지. |
| ../../shared/DataTable.tsx | expandableRow:{expandedKeys,onExpandedChange,render,canExpand?,label?}, footer:{label,render}, stickyColumns:{id,width}[] | 부모표가 실제 사용한다. expandedKeys는 personId, footer는 응답 totals, name 고정220px. 기존 소비자는 옵션을 생략해 종전 동작을 유지한다. 월원장은 기본 DataTable을 사용한다. |
| ProfitabilityMonthlyLedger.tsx | personId,query,unit? | 부모표·모바일 펼침의 기본 렌더러. 동일 조건 상세 GET으로 네 칸 키 원본행/출처/문제를 표시. 행 정렬/분류 라벨만 수행한다. |
| useProfitabilityDetails.ts | useProfitabilityDetails(query,mobile) → expandedKeys,setExpandedKeys,personId,openPerson,closePerson | 본문 local state. 개발자 상세 열기/닫기로 부모문맥을 초기화하지 않는다. |
| useProfitabilityDetails.ts | useProfitabilityLedger(personId,query) → data,loading,error,retry | 월원장 로딩/abort/늦은 응답 무시. 새로운 조회 서비스나 급여 동기화 호출 없음. |
| ProfitabilityDetails.tsx | personId,onClose | 본문에서 기존 ../developers/DeveloperDrawer로 전달한다. onEdit 미전달이므로 UI-03에 편집 CTA를 신설하지 않는다. |
| ProfitabilityValue.tsx | value:string|null|undefined, kind?:amount/mm/rate/date, unit? | 본문·월원장 금액. ../../shared/format의 십진 문자열 단위이동/MM2자리/율1자리 표시만 수행한다. JS Number로 금융 합산하지 않는다. |
| ProfitabilityColumnSettings.tsx | 없음 | 모바일/미로드 본문에서 사용. PC 표는 자체 공통 dialog. COLS/UI-03 individualProfitability 저장, 필수4열 보호/참고4열 재표시. |
| ProfitabilityBadges.tsx | person:ProfitabilityPerson | PC 이름셀·모바일 카드에 실제 사용. 서버 estimated/completeness, 원본 monthBasis MM>1.00 표시만 판별한다. |
| ProfitabilityCalculationGuide.tsx | data:IndividualProfitabilityData | shared 진입점의 계산 안내 슬롯. 원문 회사손익 차이 문장은 항상 하단에 표시. 접힌 details 안에 응답 calculationBasis와 사람별 workedMonths/employmentMonths/utilizedMonths/monthBasis.explanation을 그대로 표시한다. |
| ProfitabilityExportButton.tsx | query:ProfitabilityQuery | shared 진입점의 export 슬롯. 현재 적용 query 및 공통 열·단위 preference로 상대경로 다운로드. 중복/실패/완료·취소는 기존 구현 사용. |
| useIndividualProfitability.ts | initialSize? → query/data/cards/loading/error/fieldMessages/apply/reset/refresh | 본문 데이터 수명/commit refresh/기간이벤트/모바일20건. API 금액을 재계산하지 않는다. |

## 서버와 표시의 분리

`individualProfitabilityApi.ts`는 상대 `/api/individual-profitability` 및 선택 personId 상세만 호출한다. 서버 반환 문자열을 표/합계/요약/공헌이익에 연결하며 프론트에 보험율/날짜/사람/월계수/금융 공식 구현이 없다. 타입·표제·상태 라벨·1.00MM 초과 비교·단위변환은 표시 메타데이터다. 계산 안내 산식은 백엔드 `IndividualProfitabilityCalculation.BASIS`에서 목록과 상세가 같이 내려주며 프론트에 복제하지 않는다. 급여별 수치는 월 상세 laborSource이고 사람별 분모는 응답 monthBasis다.

서버 응답이 바뀌면 해당 근거만 새로 표시한다. 없는 calculationBasis에 예시 산식을 대신 붙이지 않는다. source 파일 읽기/문자열 비교 시험은 사용하지 않는다.

## 회귀/주의

- IndividualProfitabilityScreen.test.tsx: 등록 wrapper의 실제 export/guide 조립, 원문 문장, 서버근거 교체/분모 직접 표시, 모바일 미노출, 빈근거, 기존 배지/전체합계·조건·오류 회귀.
- ProfitabilityMonthlyLedger.test.tsx: 실제 Drawer 선택ID, 같은 프로젝트 복수투입, 문맥/스크롤/펼침 보존, late response 및 독립 표식.
- SharedScreens.test.tsx: 기존 /UI-03 메뉴/작업탭/즐겨찾기와 YEAR 규칙.
- e2e/tests/individual-profitability.spec.ts: 실제 앱 응답 대조/월원장/출력/모바일 캡처. mock API 없음.
- 기존 DeveloperDrawer GET의 status refresh 부수효과는 레거시 동작이다. 계산·목록·월원장·출력의 원천불변 시험에서는 이름 Drawer 클릭을 분리한다. 이를 무쓰기 통과로 숨기거나 원가 계산에서 재사용하지 않는다.
- 테스트 실행·reference 시각 비교·접근성/반응형 최종 수락은 runner receipt가 필요하다.
