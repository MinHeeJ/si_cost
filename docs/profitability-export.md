# 개인 원가·수익 XLSX 인계

## 구현 경로

GET /api/individual-profitability/export → IndividualProfitabilityExportController → IndividualProfitabilityExportService → ExcelExportService.generateWorkbook.

새 업무 코드는 기존 공통 월별/개인 원가 코드와 같은 com.sicost.profitability 패키지에 둔다. exportsops의 기존 출력은 변경하지 않으며, 파일 생성/분할/임시파일 정리/전송은 공통 ExcelExportService를 사용한다. 기존 POST /api/exports/master-data와 기존 출력의 큰 수 TEXT 보존 정책은 유지한다.

## 데이터 및 부수효과 경계

- 공통 ExcelExportService의 readOnly REPEATABLE_READ 트랜잭션 안에서 설정을 읽고 IndividualProfitabilityService.snapshot을 한 번 호출한다.
- 같은 Snapshot에서 선택된 TK 보기 및 TK 제외 보기를 각각 project(..., true)로 집계한다. LOSS는 각 보기에서 독립 적용되며 페이지 제한이 없다.
- 개인원가수익/턴키단가미포함 두 부모 시트에는 현재 열순서/숨김/단위 및 전체 합계를 적용한다. 빈 결과에 가짜 합계 행은 없다.
- 월별배분상세는 선택 보기의 부모 인원만 대상으로 모든 월의 MONTH 행 및 ALLOCATION 원본행을 포함한다. 상세는 월평균 전 원본 금액/MM이며, 원가 사본 복합키의 월/사람/프로젝트/투입 ID를 TEXT로 보존한다. detailMonths는 기존 상세 API와 공유하는 무조회 투영이다.
- 다운로드정보에는 서버 처리자/서울시각/기간/조건/단위/보기/TK 포함 여부/인원/미확인/예정 인원/열설정 및 정밀도 주의가 있다.
- UI-03 금액/MM/건수는 숫자 셀, 사용자 문자열 및 ID는 문자열 셀, NULL은 빈 셀, 0은 숫자 0이다. 수식처럼 시작하는 문자열도 FORMULA가 되지 않는다. 금액 단위 변환은 BigDecimal 소수점 이동이며 반올림하지 않는다.
- Excel의 15자리 정밀도 한계를 정보 시트에 고지한다. 이 출력에만 큰 합계도 숫자 셀을 유지하며 API의 정확한 문자열 금액을 변경하지 않는다.
- 파일을 완성하고 조회 트랜잭션을 끝낸 후, 별도 REQUIRES_NEW 트랜잭션으로 UI-03/DNLD/개인원가수익/전체 인원 접근기록을 저장하고 commit한다. 이후에만 응답 헤더와 스트림에 접근한다. 생성/감사/commit 실패는 파일을 지우고 일반화된 500 JSON을 반환한다. 민감 원천 금액이나 내부 예외를 로그로 출력하지 않는다.
- 접근기록은 기존 AccessLogMapper의 실제 열 USER_ID, ACT_TYPE_CD, SCRN_ID, TRGT_DESC, CLIENT_IP, ACT_DTTM, REG_USER_ID, REG_DTTM을 사용한다. LOG_ID는 기존 시퀀스/PK이고 새 열이나 스키마 변경이 없다. 업무 원천/설정 쓰기는 없다.

## 다음 그룹의 화면 조립

기존 IndividualProfitabilityScreen의 renderExport(query, data) 슬롯에 ProfitabilityExportButton을 제공한다. 버튼은 PreferencesProvider의 현재 COLS/UI-03 및 AMT_UNIT을 읽어 상대경로 GET을 호출한다. 조회 query를 그대로 전달하되 API 클라이언트가 page/size를 제외한다. 버튼의 처리중/오류/재시도/성공/취소 처리는 구현되어 있다. 기존 라우터/메뉴/작업 탭은 이 그룹에서 바꾸지 않았다.

## Runner 검증 대상

이 단계에서는 시험/빌드/패키지 설치/서버/컨테이너를 실행하지 않았다. 모든 아래 시험은 awaiting runner verification이다.

- backend: IndividualProfitabilityExportContractTest — 실제 MockMvc 컨트롤러/서비스/계산/POI/접근기록 경로, 전체 인원, 독립 LOSS, 평균/원본 차이, 설정/단위, 셀 타입, 주입 방지, 빈 파일, strict 입력, 감사 실패 및 commit 실패/파일 미전송/임시파일 정리, 기존 출력 정밀도 회귀.
- backend: ProfitabilitySheetRowsTest — 기존 시트 최대행 경계/반복 헤더/시트명/숫자 및 빈 셀.
- backend: IndividualProfitabilityExportDatabaseTest — 실제 PostgreSQL의 기존 독립자료만 사용하여 XLSX와 조회 결과, 실제 로그 열/PK, 업무/설정/감사 원천 불변을 대조한다. 기존 CostDatabaseSupport의 SICOST_COST_TEST_DB_* 및 SICOST_COST_TEST_BASELINE_CONFIRMED를 사용하고, DNLD 시험 쓰기는 SICOST_COST_TEST_AUDIT_WRITE_CONFIRMED=true도 필요하다. 시험이 생성한 LOG_ID만 추적하여 정리하며 기존 행은 변경하지 않는다. 기본 시험은 DB 없이 이 통합시험을 건너뛴다.
- frontend: profitabilityExportApi.test.ts — 상대경로/필터/정렬/열/단위/페이지 제외, 파일명/형식/오류/빈 파일 검증.
- frontend: ProfitabilityExportButton.test.tsx — jsdom 렌더링과 클릭으로 현재 설정 전달, 중복방지, 성공, 오류 후 재시도, 언마운트 취소를 검증한다.
- 기존 MasterDataExportContractTest 및 관련 출력/접근기록 회귀도 함께 실행한다.

독립 기존자료의 FX-B137-EXPORT 실데이터와 브라우저 최종 조립 확인은 runner가 제공/실행한다. 시험 미실행을 합격으로 간주하지 않는다.
