# UI Contract 느슨한 점검 결과

## 범위와 판단

`.aiops-spec/ui-design.md`의 route inventory, 사용자 흐름, UI Contract, Data Binding, 상태/권한 계약을 현재 frontend 구현과 비교했다. ASCII 위치·간격·열 순서는 수정 기준에서 제외했다. backend/API/schema/seed는 변경하지 않았다. 전체 계약의 합격 판정이나 pipeline 실패용 검증이 아니다.

## 화면별 체크리스트와 확인 범위

| 화면/region | 사용자 기능 계약 | 현재 구현과 점검 결과 |
| --- | --- | --- |
| 전체 공식 route와 공통 틀 | DB 메뉴/공식 주소/직접 진입·복구, 제목·별, 열린 화면, 일반 화면 이동 시 편집 유지, PC_ONLY 안내 | SharedApplication/SharedScreenFrame 재사용. router의 모든 screenRegistry route 렌더 시험 통과. 임시 ADMIN 메뉴·CTA와 닫기 폐기 후 재진입 시험 보강 |
| 후속 업무 안내 화면 | 업무 CRUD를 만들지 않음, 지정 YEAR/YEAR_MONTH/YEAR_CUM/NONE 기간만 제공, 범위/disabled, 실패 이전값 유지 | FollowUpScreen/ScreenPeriodBar/PeriodSelector 사용. 기존 기간·화면 시험 유지. 업무 필드나 저장 버튼을 신설하지 않음 |
| UI-27 코드 | 그룹 선택, 일반 추가는 로컬→하단 저장, 취소 복원, 기존 PK/순서 불변, SYS 이름만·VAL 기존 값만·SYS 우선, 변경분/revision만 payload, 삭제 확인·성공 후 같은 그룹 재조회 | 기존 구현 유지. 현재 탭 재클릭 시 불필요한 폐기 확인 방지. 사용 체크박스의 ApiError field 연결·첫 오류 포커스 보강. 닫기 폐기 후 상위 dirty 해제 |
| UI-27 대응 | 표기/그룹 필터, active 코드 API 선택, 신규 즉시 저장, 선택삭제 확인과 mapId/revision, 실패 입력 유지 | CodeMappingsScreen/MappingCreateDialog/MappingDeleteDialog 기존 구현 및 렌더 시험 유지. 수정 폼/임의 editable 필드 신설 없음 |
| UI-27 기준 | 읽기 전용 메타데이터, 일반 값·적용월만 편집, 월/전후값 확인, 변경칸/keyRevision, CASH 별도 요청·effectiveMonth 제외, 기존 시작월 마감이면 두 입력 잠금 | 기존 구현/시험 유지. 최초 조회 실패를 정상 빈 목록으로 표시하지 않도록 보강 |
| UI-27 변경 이력·기준 이력 | 필터/페이지·긴 값 펼침, 수정·삭제 없음, 기준 적용월 상태 | 기존 ChangeHistoryPanel/StandardHistoryDialog 구현 및 시험 유지 |
| 설정/FAV/열 설정 모달 | 명시 저장, 항목별 revision/초기화, 필수열 숨김 금지, 실패 기존값 유지, 포커스 복원 | 기존 구현/렌더 시험 유지. 신규 권한 정책·인증 화면 없음 |

현재 승인된 화면 역할은 임시 ADMIN·DataScope=ALL이며 역할별 가림/401/403이 없다(ui-design.md 36, 1238~1253행). ADMIN 이외 역할별 정책을 추정하거나 시험용 접근 제한을 만들지 않았다. 시험의 API 대역은 테스트 파일 안에만 있으며 운영 화면의 데이터는 기존 API 조회를 유지한다.

## 적용한 작은 보강

- CommonCodesScreen: 선택된 코드 하위 탭을 다시 눌러도 이동/폐기 확인을 띄우지 않고 입력을 유지한다.
- CommonCodesScreen: 화면 unmount 시 상위 dirty 상태를 해제하여 실제 편집 폐기 후에도 닫기 확인이 계속 남는 문제를 방지한다.
- CommonCodesScreen: changes[index].useYn 오류를 체크박스의 aria-invalid/aria-describedby와 필드 오류 문구에 연결하고 기존 첫 오류 포커스 처리를 적용한다.
- CommonCodesScreen/OperatingStandardsScreen: 최초 조회 실패 시 그룹/코드/운영 기준의 정상 빈 결과 문구를 동시에 표시하지 않는다. 이미 조회된 편집 데이터는 오류가 나도 유지한다.

## 남은 gap / 보류 관찰

1. OQ-UI-001: 승인된 중간 메뉴명이 없다. 현재 DB의 2단계 메뉴를 유지하며 임의의 3단계 메뉴나 OQ 표기를 만들지 않았다(ui-design.md 30행).
2. 운영 기준 목록의 전체 43키 미도착을 별도로 진단하는 화면 처리는 아직 없다. 이번 변경은 연결 오류/빈 결과 혼동만 보강했다. 목록 completeness 처리는 정상 응답의 완전성 기준과 함께 후속 보강할 수 있다(ui-design.md 1217행).
3. 대응/이력 하위 탭은 조건부 렌더링으로 unmount되므로 하위 탭을 왕복하면 필터·페이지·선택이 초기화될 수 있다. 일반 열린 화면 탭 이동의 상태 보존과 하위 탭 폐기 이동은 명세에서 구분하므로, 이를 전면 캐시 구조 변경으로 확장하지 않았다. 하위 탭 복원 범위 확인 후 보강할 항목이다(CommonCodesScreen의 panels 조건부 렌더링).
4. 9번째 열린 화면/LRU 및 닫기 확인은 native window.confirm을 사용한다. 확인·취소/dirty 보호는 있으나 도면 M3/M10의 공통 Dialog 기반 화면과 완전히 같지는 않다. 시각 배치 차이만으로 재작성하지 않았다(SharedApplication.go/close).
5. ui-design.md의 OQ-003/004 등 미확정 API 제안과 기존 실제 API 호출을 임의로 재정의하지 않았다. 이번 수정에서 새 API/schema 필드가 필요한 기능을 추가하지 않았다. DB의 원자성·이력 무변경·실제 동시성은 jsdom 시험으로 증명하지 않는다.

## 실제 실행 결과

실행 위치: frontend/

- `npm test -- --run --maxWorkers=2 --minWorkers=1`: 16 test files passed, 241 tests passed.
- `npm run build`: tsc --noEmit 성공, Vite production build 성공.
- Vitest 환경은 기존 vitest.config.ts의 jsdom이다. 보강 시험은 React 컴포넌트를 실제 렌더링하고 role/label/화면 상태와 요청 payload를 단언한다. 소스 문자열 읽기/비교 시험은 추가하지 않았다.
- React Router future flag 및 기존 SharedPreferencesDialogs 시험의 act 경고가 출력되었으나 시험 실패는 없다. 실제 브라우저 시각 검증이나 실제 backend/DB 통합 검증은 이번 실행에 포함하지 않았다.

## 이번 변경 파일

- frontend/src/pages/codegroups/CommonCodesScreen.tsx
- frontend/src/pages/codegroups/CommonCodesScreen.test.tsx
- frontend/src/pages/operatingstandards/OperatingStandardsScreen.tsx
- frontend/src/pages/operatingstandards/OperatingStandardsScreen.test.tsx
- frontend/src/app/router.test.tsx
- frontend/UI-CONTRACT-REVIEW.md
