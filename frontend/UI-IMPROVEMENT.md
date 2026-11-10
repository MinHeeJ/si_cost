# UI 개선 체크리스트와 검증 기록

## 코딩 전 계약 확인

실제 레퍼런스 URL을 Chromium으로 열어 UI-27과 그룹 추가 모달을 확인했다. 1280×800에서 헤더 56px, 메뉴 240px, 화면 탭 41px, 콘텐츠 좌우 24px, 파랑 #1D4ED8, 회색 #F9FAFB, 4개 밑줄 탭과 좌우 코드 편집기를 확인했다. 인물·알림 배지·업무 수치는 복제하지 않는다. 기존 React Router, 수기 CSS, 공통 Dialog/DataTable을 유지하며 UI 라이브러리를 추가하지 않는다.

공통 계약: DB 메뉴 이름·아이콘·계층, 공식 URL, 즐겨찾기/최근 화면, 열린 화면 보존, 설정 revision 저장, 로딩/오류/재시도, PC_ONLY의 모바일 메뉴 숨김과 직접 진입 안내. 현재 API는 임시 ADMIN이며 역할 기반 인증을 신설하지 않는다.

| route | 목적 / archetype | CTA·필드·payload·성공·제약 |
| --- | --- | --- |
| /UI-16 | 대시보드 안내 / viewer | 별·월 선택; PERIOD YEAR_MONTH 저장 후 같은 화면; 가짜 지표 없음 |
| /UI-15 | 거래처·영업 파트 안내 / viewer | 별; NONE 기간 없음; 업무 CRUD 없음 |
| /UI-02 | 개발자 풀 안내 / viewer | 별; NONE 기간 없음; 업무 CRUD 없음 |
| /UI-01 | 프로젝트 안내 / viewer | 별·YEAR 기간; 명시 선택만 저장 |
| /UI-31 | 프로젝트 현황판 안내 / viewer | 별; NONE |
| /UI-12 | 투입 배정 안내 / viewer | 별·YEAR 기간 |
| /UI-05 | 철수 리스트 안내 / viewer | 별·YEAR 기간 |
| /UI-11 | 계획 대비 투입 안내 / viewer | 별·YEAR 기간 |
| /UI-28 | 인력 운영 보드 안내 / viewer | 별·YEAR 기간 |
| /UI-17 | 수요 예측 안내 / viewer | 별·YEAR 기간 |
| /UI-29 | 영업 파이프라인 안내 / viewer | 별; NONE |
| /UI-33 | 외부 인력 후보 안내 / viewer | 별; NONE |
| /UI-13 | 청구·입금 안내 / viewer | 별·YEAR 기간 |
| /UI-03 | 개인 원가·수익 안내 / viewer | 별·YEAR 기간 |
| /UI-04 | 프로젝트 원가 안내 / viewer | 별·YEAR_MONTH 기간 |
| /UI-19 | 영업 파트 이익 안내 / viewer | 별·YEAR_MONTH 기간 |
| /UI-32 | 재협상 대상 안내 / viewer | 별; NONE |
| /UI-10 | 재무 항목 안내 / viewer | 별·YEAR_MONTH; PC_ONLY |
| /UI-07 | 전체 수익 구조 안내 / viewer | 별·YEAR_CUM; 연도/월 일치 |
| /UI-08 | 영업이익 안내 / viewer | 별·YEAR_CUM; 연도/월 일치 |
| /UI-09 | 순이익 안내 / viewer | 별·YEAR_CUM; 연도/월 일치 |
| /UI-18 | 사업계획 실적 안내 / viewer | 별·YEAR_CUM |
| /UI-21 | 자금수지 안내 / viewer | 별·YEAR |
| /UI-14 | 월 마감 안내 / viewer | 별·YEAR_MONTH; PC_ONLY; 마감 변경 없음 |
| /UI-26 | AI 분석 안내 / viewer | 별; NONE; AI 연산 없음 |
| /UI-30 | AI 추천 안내 / viewer | 별; NONE |
| /UI-23 | 사용자 안내 / viewer | 별; NONE; PC_ONLY; 사용자 쓰기 없음 |
| /UI-24 | 권한 안내 / viewer | 별; NONE; PC_ONLY; 권한 쓰기 없음 |
| /UI-25 | 메뉴 안내 / viewer | 별; NONE; PC_ONLY; 메뉴 쓰기 없음 |
| /UI-27 | 공통코드 / editor | 아래 네 탭, 같은 URL; PC_ONLY; 기간 없음 |

| UI-27 region | API 연결과 사용자 계약 |
| --- | --- |
| 코드 | GET /api/code-groups → GET /api/code-groups/{group}/codes. 그룹 추가 POST groupId/name. 저장 PUT expectedRevision/changes(변경칸만). 기존 code/sortOrder 불변; SYS 이름만; VAL 기존 값만; SYS 우선. 신규행 로컬 추가 후 하단 저장. 삭제 확인 후 DELETE code와 expectedRevision. 성공 같은 그룹 갱신, 충돌 입력 보존, 미저장 이동 확인. |
| 엑셀 표기 대응 | GET /api/code-mappings sourceText/groupId/page/size. 필터 명시 조회. 그룹과 active 코드 API 선택. POST sourceText/groupId/code. 선택 삭제 확인 후 POST /delete items(mapId/expectedRevision). 성공 목록 재조회, 실패 입력 보존. |
| 운영 기준값 | GET /api/operating-standards. 키·이름·단위·설명 불변. 값 변경 모달→적용월/전후 확인→PUT changes(key/value/expectedRevision), 일반만 effectiveMonth. CASH 별도 저장·기존 시작월 마감이면 읽기 전용. 이력 GET /{key}/history. 금액 표시만 포맷하고 저장 문자열은 보존. |
| 변경 이력 | GET /api/common-code-history targetType/groupId/page/size. 읽기 전용, 필터 입력과 명시 조회 분리, 긴 값 펼침, 서울 일시 표시. 저장/삭제 없음. |
| 설정 | GET/PUT/DELETE /api/me/preferences; PAGE_SIZE/DENSE/COLS 등 기존 API 유지. 실패 이전 적용값, 최초 expectedRevision=null. |

## 검증 원칙

Vitest + jsdom에서 실제 컴포넌트를 렌더하고 역할·버튼·입력·API 호출을 검증한다. 소스를 읽어 문자열 비교하는 시험은 추가하지 않는다. 브라우저 검증은 실 API를 사용하며 DB 변경 시험은 격리 또는 명시적 범위에서만 수행한다. 기존 운영 데이터는 브라우저 시각 검증을 위해 수정하지 않는다.

## 코딩 후 체크리스트 대조 결과

- 화면 목록: 기존 registry의 30개 공식 경로가 모두 연결되어 있어 신규 업무 route를 만들지 않았다. UI-27 네 탭도 동일 URL을 유지한다. 나머지 29개는 명세대로 후속 안내 화면이며 가짜 업무 CRUD/대시보드를 추가하지 않았다.
- 공통 프레임: DB 아이콘명을 SVG로 렌더링, 활성 메뉴 강조, 로고의 실제 대시보드 이동, AI/알림 준비 중 안내, 실제 developmentMode 조건, 모바일 PC_ONLY 메뉴/FAV/RECENT 숨김을 적용했다. 저장된 PC 목록은 삭제하지 않는다.
- 코드: 중첩 콘텐츠 패딩을 제거하고 제목줄 우측 그룹 추가·4탭·좌우 편집·main 영역 전체 너비의 sticky 저장바를 적용했다. 열 설정과 밀도 버튼을 연결했다. SYS/VAL 읽기 제한과 기존 변경분/revision 저장, 삭제 확인은 유지했다. 수당 신규 빈값은 0으로 정규화하고 정밀도 오류는 입력을 보존한다.
- 대응: 실제 필터/추가/선택 삭제 연결을 보존하고 필터 패널·toolbar를 정돈했다. 선택 행을 시각적으로 표시한다.
- 기준: 읽기 전용 메타·묶음 변경·적용월 확인·CASH 독립 저장을 보존한다. 숫자는 기존 문자열 기반 표시 함수를 사용하며 원본 저장 문자열을 변경하지 않는다. 긴 설명은 표 내부에서 줄바꿈한다.
- 이력: 필터 draft와 조회 조건을 분리하고 명시 조회·밀도 버튼을 연결했다. 서울 일시는 공통 포맷을 사용한다. 조회 실패를 빈 결과로 잘못 표시하지 않는다.
- 상태: 초기 메뉴 로딩, 표의 정적인 skeleton과 empty 아이콘, 그룹 조회중, 설정/그룹 조회 오류 및 재조회, 필드 오류 첫 포커스를 보강했다. 기존 commit 성공 후 재조회 실패 안내와 저장 자동 재전송 금지는 유지했다.
- 백엔드·스키마·persistence·패키지 의존성은 수정하지 않았다. 현재 임시 ADMIN 계약을 유지하며 역할 차단이나 로그인 화면을 신설하지 않았다.

## 실제 검증 결과

실행 명령: frontend에서 `npm run build`, `npm run test -- --run`.

- TypeScript 검사 + Vite 프로덕션 빌드 성공: 102 modules transformed.
- Vitest/jsdom: 16 test files passed, 235 tests passed. 새 시험은 DOM 렌더링 기반이며 소스 문자열을 읽는 시험은 없다.
- Playwright 1.51.1의 Chromium으로 실제 레퍼런스에 직접 접속하여 화면과 그룹 추가 모달을 열었다.
- 빌드된 dist를 별도 정적 서버로 제공하고 실제 실행 중인 백엔드 API에 연결하여 30개 공식 주소의 DB 제목을 모두 확인했다. 업무 데이터 저장·삭제는 실행하지 않았으며, 화면 진입의 기존 RECENT 설정 저장만 발생했다.
- UI-27: 시스템 그룹의 신규 코드 버튼 없음, ALWNC의 4열과 이름 읽기 전용, 운영 기준 43행, AI_SCORE 변경 모달 8개 입력, 그룹 추가 모달 첫 입력 포커스/Escape 닫힘을 확인했다.
- 1280×800: 헤더 56px, 메뉴 240px, 하위 탭 x=264/y=181, split x=264/y=238, 저장바 x=240/y=739/width=1040. 레퍼런스에서 확인한 주요 프레임 좌표와 일치한다. 추가 계약 버튼/실제 API 데이터/글꼴에 따른 픽셀 차이까지 동일하다고 주장하지 않는다.
- 1024px와 390px에서 문서 가로 넘침 없음. 390px에서 PC 안내 표시·편집 본문 숨김·모바일 메뉴의 공통코드 링크 제외를 확인했다.
- 최종 브라우저 실행: `ROUTES 30 direct routes rendered`, `JS_ERRORS []`, `API_ERROR_RESPONSES_OBSERVED []`, `FINAL_ALERTS []`, exit 0.

환경 제약: sandbox에서 host.docker.internal 직접 DNS 연결이 안 되어 실 API 요청을 egress proxy로 전달하는 임시 relay를 사용했다. 중간 실행에는 proxy 502가 관측되었고 최종 실행은 오류 없이 완료했다. 초기 Vite 개발 서버의 연속 새로고침 검증이 불안정하여 최종 판정은 프로덕션 빌드로 수행했다. Node 22 실행 환경이 package.json의 Node 20 선언과 달라 npm engine 경고가 있으며, 기존 React Router future/일부 act 경고도 남아 있다. 이는 시험 실패와 구분한다.

## 화면 증거

- evidence/product-desktop.png
- evidence/product-dialog.png
- evidence/product-mappings.png
- evidence/product-standards.png
- evidence/product-history.png
- evidence/product-1024.png
- evidence/product-390.png

## 변경 파일

- frontend/UI-IMPROVEMENT.md
- frontend/vite.config.ts
- frontend/src/app/router.tsx
- frontend/src/app/router.test.tsx
- frontend/src/shared/Icon.tsx
- frontend/src/shared/DataTable.tsx
- frontend/src/shared/common.css
- frontend/src/pages/shared/HeaderActions.tsx
- frontend/src/pages/shared/SharedApplication.tsx
- frontend/src/pages/shared/FollowUpScreen.tsx
- frontend/src/pages/shared/ChangeHistoryPanel.tsx
- frontend/src/pages/shared/shared.css
- frontend/src/pages/shared/WorkspaceUx.test.tsx
- frontend/src/pages/shared/SharedPreferencesDialogs.test.tsx
- frontend/src/pages/codegroups/CommonCodesScreen.tsx
- frontend/src/pages/codegroups/CommonCodesScreen.test.tsx
- frontend/src/pages/codegroups/codegroups.css
- frontend/src/pages/codemappings/codeMappings.css
- frontend/src/pages/operatingstandards/OperatingStandardsScreen.tsx
- frontend/evidence/product-desktop.png
- frontend/evidence/product-dialog.png
- frontend/evidence/product-mappings.png
- frontend/evidence/product-standards.png
- frontend/evidence/product-history.png
- frontend/evidence/product-1024.png
- frontend/evidence/product-390.png
