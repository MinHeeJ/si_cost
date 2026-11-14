# 정산 공통 기반 검증 기록

## 실제 실행

작업트리의 git status --short / git branch --show-current / git rev-parse HEAD는 Git 메타데이터가 없어 실패했다. 이전 분석의 HEAD는 최신 실행 근거로 사용하지 않았다.

루트 실행 명령: python3 /app/infra/hermes/runner/code_agent_detail/node/self_check.py .

첫 실행: 종료코드 1, FAIL. Backend Maven 선택 시험에서 SettlementBoundaryTest.detailInsertionAndExactPartMonthReplacementAreAllowed가 실패했다. SQL parser의 operation은 DELETE가 아니라 DELETE FROM이며, 새 상세 삭제 분기가 이를 정확히 판별하도록 수정했다. 시험의 허용 범위나 assertion은 낮추지 않았다.

최종 재실행: 종료코드 0, PASS. runner는 backend Maven 선택 시험을 64초에 완료하고 위 공통 시험과 기존 WriteCoordinatorTest/ExcelExportServiceTest/AccessLogWriterTest/ChangeHistoryWriterTest/PersistenceBoundaryTest/TurnkeyRevenueCalculationTest를 PASS로 보고했다. frontend/e2e는 변경 시험이 없어 실행하지 않았다고 보고했다. 출력에는 개별 PostgreSQL 시험의 실행/skip 세부 영수증이 없으므로 실DB 실행까지 승격하지 않는다. 이 문서의 검사 항목/설계 설명 자체는 제품 실행 PASS가 아니다.

## 추가한 실행 대상과 경계

- SettlementBoundaryTest: 부모 전체키 쓰기/상세 전체집합 교체, 키·REG 수정/부모삭제/상세수정/upsert/범위없는 SQL 거부.
- SettlementHistoryWriterTest: compact YYYYMM 감사키·정확한 원문·서버 actor/서울시각, 삭제 상세 감사, Unicode code point 500자 한계, 501자/1000자 감사 충돌, 명령 전체 사전검사, no-op, 필수 INSERT 실패.
- SettlementAccessLogTest: UI-19/32 DNLD의 종류/파일명/건수 연결, 다른 이벤트·잘못된 종류·제어문자/NULL 건수/음수 거부, 필수 기록 실패, 기존 생성자 호환.
- SettlementExportTest: 이번달/추이/재협상 정확한 파일명, callback 내 repeatable-read, DNLD 별도 커밋 후 반환, XLSX 실제 파싱으로 ID 앞0/수식유사 텍스트/숫자/음수/NULL/0 확인, 로그 실패 시 반환 없음, 기존 workbook 생성자/파일명 유지.
- SettlementSourceLocksTest: part/header/standard 물리 잠금 후 정렬 advisory, 실제 원천 namespace, 소속 재검사 충돌, 이전/이후 파트 전달, WriteCoordinator stale 우선 및 롤백.
- SettlementSourceMapperTest: 실제 mapper XML 파싱 및 동적 BoundSql, nullable SQL predicate 없음, 정렬 행 잠금/SHARE table lock 없음.
- SettlementSchemaDatabaseTest: 독립 PostgreSQL 설치 시 PK/FK/NULL/DEFAULT/너비/인덱스 확인 및 재기동 설치 경로 전후 migration history와 업무행 전필드 비교. SICOST_TEST_DB_URL이 없으면 skip; skip은 PostgreSQL 통과가 아니다.
- 기존 TurnkeyRevenueCalculationTest: null/0 예산과 함께 음수 예산도 미확인 처리하며 비용은 유지한다.

## 미실행/미수령 경계

FX-B140-SETL/RATE 수치와 두 실제 업무 API의 조회 전후 baseline은 수령/실행하지 않았다. 실제 PostgreSQL schema/restart/rollback/두 트랜잭션 경합은 DB 시험의 실행 영수증 없이는 합격으로 보고하지 않는다. UI-19/32 브라우저 진입/모달/반응형/시각대조 및 출력 HTTP dispatch도 foundation만으로 합격 처리하지 않는다.

직접 source writer의 공유 잠금 연결은 settlement-source-map.md의 후속 호출 계약을 따른다. 새 helper/WriteCoordinator 단위시험은 기존 모든 직접 writer가 같은 경로를 사용함을 증명하지 않는다. confirmForClose 구현 및 호출자 마감 트랜잭션도 이 공통 클래스의 존재만으로 완료 판정하지 않는다.

## 허용 폴더 밖 변경 사유

backend/src/main/resources/mappers/common/SettlementSourceLockMapper.xml은 기존 MyBatis mapper-locations가 읽는 실제 SQL 리소스다. 별도 ORM/저장소를 만들지 않고 공통 잠금 클래스가 기존 물리 계약을 사용하도록 필요하다.

backend/src/main/java/com/sicost/profitability/TurnkeyRevenueCalculation.java 및 해당 기존 시험은 결정 D11의 재사용 TK 예산 <=0 보호를 원래 엔진에서 수정/회귀하기 위해 최소 변경했다. TK 엔진을 복제하지 않았다.

docs/implementation/settlement-source-map.md 및 이 문서는 T001/T034/T036의 실제 원천·심볼·실행과 미실행 증거 인계다. 작업키가 든 제안 파일명 대신 승인 D1의 업무명을 쓴다. .aiops-runtime/slice-notes.json은 필수 병합 인계 파일이다.

## 병합 작업트리의 정산 명령·추이 보완

이번 작업은 이전 foundation 실행 기록과 별도다. 현재 작업트리에서 git status/branch는 정상 실행되었으며 main의 기존 수정·미추적 파일을 보존했다. 네 연산은 이미 SettlementCommandController에 등록되어 있었으므로 중복 컨트롤러·라우트·마이그레이션을 만들지 않았다.

경로 원장:

- GET /api/sales-part-settlements/{partId}/trend → SettlementCommandController.getSalesPartSettlementTrend → SettlementTrendService.get → SalesPartSettlementService.previewSettlement → SalesPartSettlementRepository.load. readOnly REPEATABLE_READ 및 월별 사본/계산 선택은 보존한다. 다른 사람의 blocking issue가 해당 사람의 무투입월을 UNKNOWN으로 바꾸지 않도록 했다. 파트월 전체 결측과 해당 사람의 결측은 계속 UNKNOWN이다.
- PUT /api/sales-part-settlements/{partId}/card → SettlementCommandController.saveSalesPartSettlementCard → SettlementCommandService.saveCard → execute → SettlementCommandMapper.insertParent/updateParent. WORK 응답의 확정 이익금·확정자·확정시각을 명시 null로 반환한다. GET의 예상 정산 합계와 mutation의 확정금액은 서로 다른 의미다.
- POST /api/sales-part-settlements/{partId}/confirm → SettlementCommandController.createSalesPartSettlementConfirmation → SettlementCommandService.confirm → execute → insertParent/updateParent/deleteDetails/insertDetail. 전체 인력 사본·동일 트랜잭션 카드·감사 처리 및 최신 중복확정 no-op은 보존한다.
- POST /api/sales-part-settlements/{partId}/cancel → SettlementCommandController.createSalesPartSettlementCancellation → SettlementCommandService.cancel → execute → updateParent/deleteDetails. 카드 보존 및 정확한 파트월 상세 삭제를 유지하고 WORK 응답에서 재계산 예상합을 확정합으로 반환하지 않는다.

네 연산의 기존 공개 접근/임시 관리자 컨텍스트는 변경하지 않는다. 새 로그인·역할403은 없다. 변경 actor는 CurrentUserProvider.userId이며 잠금은 SettlementSourceLocks.lock, revision 검사는 RevisionService.requireExpected, 감사는 ChangeHistoryWriter.preflightSettlement/append다. 물리 쓰기는 기존 TB_CO_PART_SETL(PART_ID,SETL_YM), TB_CO_PART_SETL_DTL(PART_ID,SETL_YM,DEV_ID)와 mappers/salespartsettlementsops/SettlementCommandMapper.xml을 그대로 사용한다.

UI-19는 기존 shared 호스트 → salespartsettlementsops/SalesPartnerProfitScreen → salespartsettlements/SalesPartnerProfitScreen과 SettlementCardInput/SettlementTrendPanel을 유지한다. 실제 본문에 SCR-UI-19 표식이 있다. 카드 초안의 저장 전/예상합계는 BigInt 차감이며 페이지·건수·정렬 변경에도 기존 저장/버리기/계속편집 guard를 적용한다. 추이 이름열은 기존 CSS 토큰으로 고정한다.

회귀 실행 대상:

- backend: SettlementCommandTest, SettlementTrendTest. 실제 소유 컨트롤러의 MockMvc HTTP 요청으로 WORK null 응답·취소 카드보존·404 무쓰기·추이 인력별 결측 격리를 추가했다. 기존 stale/마감/입력거부/감사실패 테스트는 유지한다.
- frontend: salespartsettlementsops/SettlementCommands.test.tsx, salespartsettlements/SalesPartnerProfitScreen.test.tsx. jsdom 렌더/입력/버튼 동작으로 정확정수·미확인 초안, 페이지/건수/정렬 이탈 보호를 추가했다. 소스 문자열 비교 시험은 없다.
- e2e/tests/sales-part-settlement-commands.spec.ts: 실제 runner 앱의 추이 API/화면 바인딩, 확정창 취소·초안 유지·업무 쓰기0, 세 쓰기 핸들러의 입력거부·revision 불변. 공유 seed를 수정하지 않으며 성공 명령/트랜잭션/DB 경합 증명으로 과장하지 않는다.

실행 상태: awaiting runner verification. 사용자 지시에 따라 이번 작업에서는 Maven/npm/Playwright/서버/컨테이너를 실행하지 않았다. git diff --check는 종료코드 0이었다. 미추적 파일을 포함한 변경범위 공백 검사도 종료코드 0, trailing whitespace 없음이다. 정확정수 회귀 예상값은 Python 정수 연산으로 확인했다. 이 정적 확인은 컴파일·단위시험·브라우저·PostgreSQL 합격이 아니다. 앞 절의 과거 foundation PASS를 이번 수정의 PASS로 재사용하지 않는다.

## 최종 통합 정합성 점검 — 실행 검증과 별도

현재 main의 기존 변경 및 미추적 산출물을 보존하고, Git에 알려진 비무시 UTF-8 작업 파일과 staged specification을 정적으로 대조했다. 환경·자격 증명 파일 및 바이너리는 읽기 대상에서 제외했다. 충돌 표식은 발견하지 않았고 backend/src/test/resources/contracts/openapi.yaml은 staged effective OpenAPI와 바이트 단위로 같았다. 기존 /UI-19·/UI-32 route, shared 재노출 경로와 V2__base_data.sql의 M016/M017 메뉴 연결은 유지하며 마이그레이션·시드를 수정하지 않았다.

수정한 교차 단계 불일치:

- 기존 UI-32는 usePreferences.get/put의 COLS/UI-32를 사용하지만 PreferenceService.getPreferences의 기본 항목과 PreferenceValidator의 허용 화면·표·필수열에는 빠져 있었다. 기존 getPreferences/putPreference/deletePreference 경로에 rateRenegotiations의 명세상 11열과 name/projectName 필수열을 연결했다. UI-19 COLS와 UI-32 PERIOD는 계속 거부한다.
- frontend/src/pages/me/preferencesApi.ts의 공유 ScreenColumnPreferences에도 같은 표 이름을 등록하고 RateRenegotiationsScreen의 로컬 교차 타입 보충을 제거했다. 새로운 endpoint나 설정 저장소는 없다.
- 경로 원장: PreferenceController → PreferenceService의 기존 트랜잭션 → PreferenceValidator → PreferenceMapper/XML → TB_SY_USER_PREF(USER_ID,PREF_TYPE_CD,PREF_KEY; PREF_VAL VARCHAR(2000)). actor는 기존 CurrentUserProvider의 DB 임시 관리자이며 새 인증/권한 검사는 없다. revision 검사, 단건 잠금, 저장 후 invalidation과 응답 봉투는 그대로다.
- PreferencesContractTest에 기본값·순서/숨김 저장·no-op·초기화·다른 설정 보존, 잘못된 순열/필수열 숨김 거부, 두 화면의 기간/열 설정 경계를 추가했다. 테스트는 작성만 했으며 실행하지 않았다.

정적 조사로 확인한 미해결 구현 경계(완료나 통과로 간주하지 않음):

- MasterDataExportController/createMasterDataExport와 MasterDataExportInput에는 staged SalesPartSettlementExportRequest 및 RateRenegotiationsExportRequest 처리 분기가 없다. UI-32 downloadRenegotiations는 RATE_RENEGOTIATIONS를 전송하지만 기존 서버 dispatcher로 처리되지 않는다. UI-19의 다운로드 연결도 현재 정산 화면에 없다. 공통 WorkbookData/파일명 지원만으로 업무별 출력 구현 완료를 주장할 수 없다.
- staged getFinancialHistory는 PART_SETTLEMENT와 해당 partId를 정의하지만 FinancialReadInput은 둘 다 허용하지 않으며 UI-19 변경 이력 연결도 없다. 기존 MONTH/TAX 등의 조회를 정산 이력으로 바꾸어 대체하지 않았다.
- confirmForClose는 현재 production source에 없다. SettlementSourceLocks를 사용하는 WriteCoordinator와 별개로 AssignmentRepository.lock 등 직접 source writer의 기존 advisory 잠금 경로도 남아 있다. helper 존재만으로 전체 원천 직렬화나 마감 호출자 트랜잭션이 완성되었다고 보지 않는다.

위 미해결 항목은 새로운 업무별 처리/여정을 구현해야 하므로 이번 제한된 통합 정합성 수정에서 임의로 확장하지 않았다. 요구·공개 명세를 삭제하거나 현재 UI를 숨겨 차이를 덮지 않았다. 빌드, Docker, API 테스트 및 그 밖의 제품 실행 검증은 Quality Gate 소유로 남긴다. 상태: awaiting runner verification.
