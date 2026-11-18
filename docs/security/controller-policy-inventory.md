# BASIC-147 Controller 정책 인벤토리

## 운영 원천과 확인 범위

`backend/src/main/resources/permission-operations.tsv`가 **HTTP method / 전체 Spring mapping / 정책 / 작업 / Controller.method**의 운영 원천이다. 별도 문서용 허용표를 운영 설정에 덮어쓰지 않는다. 현재 파일을 정적으로 읽은 결과는 **297개 method/path, 중복 0개**다. 이는 현재 실행 앱의 매핑 일치나 전수 기능 통과를 뜻하지 않는다.

`AuthenticationIntegrationTest.everyActualControllerMappingHasAnExactServerPolicy`와 `ControllerPolicyInventoryTest`는 실제 Spring mapping과 TSV를 양방향 대조하는 검증자 실행 대상이다. 이번 재개에서는 실행하지 않고 compile만 확인한다. 런타임 인터셉터는 미등록 method/path나 다른 handler를 거부한다. Referer, screen, userId, ADMIN/SYSTEM 헤더는 권한 증명이 아니다. 기존 업무 URL은 유지한다.

## 분류

| policy | 인증·동작 및 데이터 경계 |
|---|---|
| PUBLIC | 명시 `/api/health`; 업무 데이터 없음 |
| AUTH | 로그인/찾기/CSRF/세션/단계/비밀번호 명령. 인증 필터와 서비스가 현재 USE·해시·ADMIN MFA·단계·만료를 검사. 변경 요청은 CSRF 필요. 중간 응답에는 최소 단계 정보만 포함 |
| OWN | 본인 설정·알림·명시한 자기 서비스. 서버 주체 키만 사용. 자동작업 로그는 ADMIN+ALL을 별도 요구 |
| DYNAMIC | 서버가 검증한 대상 종류/업무 목적을 아래 실제 메뉴 정책으로 변환. 알 수 없는 목적은 거부 |
| INTERNAL | HTTP 수동 실행과 고정 내부 SYSTEM 명령을 구별. 수동 실행의 활성 플래그·ADMIN·해당 메뉴동작·ALL을 기존 controller/service에서 재검사. 스케줄 작업에 임시 admin을 주입하지 않음 |
| UI-* | 활성 역할의 INQ 및 해당 REG/UPD/DEL/UPLD/DNLD/CNFM 합집합, 활성 메뉴 계층·실제 화면 대응. ADMIN도 저장 메뉴를 우회하지 않음 |

## 동적 대상

| 실제 경로 | 서버가 결정하는 정책 |
|---|---|
| `/api/current-user`, `/api/menus`, `/api/period-context` | 정상 인증, 실제 내 정보/허용 메뉴/최소 공통 기간. `developmentMode=false`, `accessEnforcement=true` |
| `/api/business-users/role-options` | 사용자 화면에 필요한 활성 역할 ID/이름만. 사용자 등록의 부수 조회로 전체 `/api/roles` 권한표를 열지 않음 |
| `/api/master-history` | USER/ROLE/MENU/CLIENT/PART → UI-23/24/25/15 INQ. 역할/메뉴는 ADMIN 추가 검사. 비밀 인증열은 count/page 이전 제외 |
| `/api/people-history` | DEVELOPER/CANDIDATE → UI-02/UI-33 INQ. 실제 객체 범위 및 허용 이력 필드만 조회 |
| `/api/financial-history` | MONTH/CARDS → UI-10, TAX → UI-09, BUSINESS_PLAN → UI-18, HISTORICAL_PROFIT_LOSS → UI-08, PART_SETTLEMENT → UI-19. 해당 INQ·회사 자료 ALL 또는 실제 정산 PART·업로드 소유/자격을 검사. 제한 정산 이력의 민감 금액/자유 사유는 제외 |
| `/api/business-users/options`, `/api/clients/options`, `/api/sales-parts/options`, `/api/projects/options`, `/api/assignment-people/options` | 고정 목적 → 허용 입력업무 INQ+REG/UPD 또는 원래 관리화면 INQ. 최소 선택 정보만 반환하며 타 화면 상세·급여·역할표 권한을 주지 않음 |
| `/api/projects/{id}/pm` | UI-23 담당관리 또는 UI-01/UI-31 INQ+실제 프로젝트 범위. 최소 담당 식별/이름·연결 상태 |
| `/api/uploads/{id}/*` | 업로드 원천 종류 → 해당 업무 메뉴/동작 및 소유·추가 자격·범위. 오류/결과/파일도 같은 경계 |
| `/api/codes/*`, `/api/params/*`, `/api/month-close` | 허용 업무 화면이 있는 사용자의 공통 참조 및 월/마감 상태만. 회사 마감 원장·점검·행위를 여는 경로가 아님 |
| `/api/resume-extractions` | 검증한 DEVELOPER/CANDIDATE → UI-02/UI-33 REG 및 저장과 동일 필드 정책 |
| `/api/exports/master-data` | 서버 소유 view → 목적 메뉴 INQ+DNLD, 원천 범위·열 투영. 요청 screen은 권한 증명이 아님 |
| `/api/common-code-history` | UI-27 INQ |

## 공통 실제 연결

- `PermissionScopeSql`은 실제 행 소속을 count/sum/order/limit 이전 SQL 원천에 적용한다. 투입/청구의 소속은 프로젝트이며 조인한 개발자 소속 PART로 허용된 프로젝트 행을 다시 누락시키지 않는다. 개발자-root 조회에는 사람 범위를 유지한다.
- `PermissionRowPolicy`는 요청 대상 및 연결 ID 전량, `PermissionMutationScope`는 실제 DML write-set 전량을 검사한다. 원래 마감/정산 lock 및 timeout 문장은 행 변경이 아니며 이후 DML 검사는 생략하지 않는다. 미허용 일부 행만 빼는 부분 성공으로 우회하지 않는다.
- `PermissionWriteGuard`와 작업 checkpoint는 독립적으로 커밋된 현재 인증/역할/메뉴/범위를 읽어 오래된 MyBatis/업무 transaction snapshot을 재사용하지 않는다.
- 정산/재협상/프로젝트 계산은 허용 ID 선확정 → 기존 완전 원천 계산 → 허용 응답 투영으로 구분한다. `PermissionCalculationContext`는 SELECT 계산 원천용이지 인증/쓰기 우회가 아니다.
- JSON/UI/workbook은 민감값을 0/NULL로 대체하지 않고 금지 키·열·원천 설명·메타데이터에서 제외한다. UI-19 비금액 상태, UI-32 허용 파생값, UI-31 원가사용률 예외를 다른 화면의 급여 권한으로 확대하지 않는다. 허용 PART 청구단가는 급여 조회로 오인해 전체 detail을 차단하지 않는다.
- 대시보드의 회사 원장·자금·마감 원천은 ALL에만 계산/제공한다. 제한 범위 화면/엑셀에는 회사 카드·숫자·링크를 넣지 않는다.
- AI/사용자 worker는 실제 세션 proof와 접수 capabilities 상한을 보존하며 현재 권한과 교집합을 적용한다. 비HTTP AI SELECT도 요청 기간의 SQL 원천 범위를 적용한다. 완료 응답/저장 전 재검사한다.
- 최종 알림 수신자의 현재 상태·메뉴·범위 외 DUN/철수/자금의 기존 업무 수신 자격을 다시 계산한다. 탈락은 기존 delivery task에 제외 사유로 남긴다. 본인 완료 알림은 일반 상태만 유지하고 새 QNA의 동일 transaction 소유 확인을 보존한다.
- 성공 응답의 서버 시각/세션 만료 헤더를 JSON/파일/SSE 첫 바이트 전에 제공한다. 사용자 활동은 한 번만 반영하고 상태 GET/허용된 배경 폴링은 연장하지 않는다. 탭 간 만료 전파는 업무 DOM/초안을 폐기하며 원요청을 재전송하지 않는다.

## 남는 제약과 검증

등록된 정책 또는 보수적인 503 거부만으로 정상 업무 수락을 입증하지 않는다. 특히 단가 권한 없는 신규 후보는 기존 필수 단가의 권한 없는 입력/가짜 0 대신 원천 정책 미확정 오류로 차단되어 있으며 미완료 제약이다. 실제 SMTP·지연 AI 원천·참고 시안·선행 업무가 준비되지 않은 통합은 미검증으로 구분한다.

현재 소스의 동작 통과는 독립 검증자 결과가 필요하다. 시험 suite/전체 build/browser는 구현자가 이번 재개에서 실행하지 않았다. compile/typecheck와 실제 시험 실행을 구별하며 `basic147-validation-report.md`에 기록한다. 기존 계산·마감·정산·취소 assertion은 완화하지 않는다.
