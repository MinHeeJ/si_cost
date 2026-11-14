# 정산 공통 원천 및 연결 계약

## 발견 근거

실제 PRODUCT_ROOT는 /shared/syncops/artifacts/hermes/phase-worktrees/si-cost-13-scope-analysis-20261114T070759/parallel-node/slices/foundation 이다. 이 복사본에서 git status --short, git branch --show-current, git rev-parse HEAD는 모두 not a git repository로 실패했다. 따라서 이전 분석의 HEAD를 최신 확인으로 재사용하지 않는다.

backend/pom.xml은 Java 17, Spring Boot 3.3.13, MyBatis 3.0.4, Flyway PostgreSQL 모듈, POI 5.3.0을 선언한다. backend에 Maven wrapper는 발견되지 않았다. frontend/package.json 및 package-lock.json에는 React 18.3.1, React Router 6.30.1, Vite 5.4.21 및 npm 사용이 확인된다. 의존성/설치 파일은 변경하지 않는다. 실행 검증 진입점은 루트에서 python3 /app/infra/hermes/runner/code_agent_detail/node/self_check.py . 이다.

## 실제 선행 원천

- common/assignments/AssignmentMonthlyService.java: Source(id, projectId, developerId, jobCode, startDate, endDate, contractMm, price, inputType, contractDeveloperId, extensionPossibility, extensionCount, earlyWithdrawal, status); Month(month, projectId, developerId, inputDays, contractMm, monthlyMm, appliedUnitPrice, salesAmount, fixed). preview(Source,List<ValueHistory>,String,String)는 보호월을 저장값으로 유지한다. synchronize는 쓰기이며 GET/export에서 호출하지 않는다.
- apiops/PayrollService.java: getPayroll(PayrollDtos.Query)는 readOnly REPEATABLE_READ이며 repository.snapshot(month)로 revision을 만든다. 급여에 FIX_YN은 없고 BASE/월마감으로 보호한다.
- profitability/MonthlyCostRepository.java: loadMonth(int,int), loadRange(String), loadRange(YearMonth), loadYear(Integer)는 전체 원천을 읽는다. historicalPeople/prices/states, payroll, assignments/projectMonths, projects/projectParts/turnkey, storedCosts/standards/parts/closes/allowanceCodes를 한번의 읽기 스냅샷으로 연결한다.
- profitability/MonthlyCostService.java: calculate(MonthlyCostDtos.Sources)는 필터 없는 MonthlyCostsData를 반환한다. MonthlyPersonCost.laborSource/pay와 MonthlyCostRow.laborAllocated/personalRevenue/assignmentMm은 별개다. settlement의 비용을 totalCost로 대체하거나 costComplete 하나로 모든 산식의 유효성을 판단하지 않는다.
- profitability/MonthlyCostDtos.java: Person.personPartId는 TB_HR_DEV.PART_ID이고 StoredCost.projectSalesPartId는 프로젝트 영업파트다. 보호월의 StoredCost 및 Payroll은 현재 마스터로 복원하지 않는다.
- profitability/TurnkeyRevenueCalculation.java: selected/calculate를 그대로 재사용한다. 대체식 예산은 양수만 유효하게 변경했고 기존 TurnkeyRevenueCalculationTest의 null/0 사례에 음수 회귀를 추가했다.
- salespartsops/PartStandardService.java: save(String,JsonNode)는 part header, timeline, CASH_BASE_YM/close 이후 기존 CNFM/AUTO 율·관리비 보호를 검사한다. 파트장만 변경하는 예외를 보존했다. 이 서비스와 mapper는 수정하지 않았다.

읽기 물리 원천은 TB_BS_PART/STD, TB_HR_DEV/PRC/STAT/MON_PAY, TB_PJ_ASGN/MON/HIST/PROJ/TK_DSTRB, TB_CO_MON_COST, TB_CL_CLOSE, TB_SY_OPER_STD/CODE/USER/CHG_HIST/USER_PREF다. 기존 profitability/MonthlyCostMapper.xml과 common/AssignmentMonthlyMapper.xml, salespartsops/PartStandardMapper.xml이 실제 읽기/쓰기 연결의 근거다. 이 문서는 각 원천의 실DB 실행 또는 FX-B140-SETL/RATE 수치 수령을 대신하지 않는다.

## 설치와 제한된 쓰기

V1__full_schema.sql의 TB_CO_PART_SETL PK는 PART_ID,SETL_YM이며 카드액에는 SQL DEFAULT가 없다. TB_CO_PART_SETL_DTL PK는 PART_ID,SETL_YM,DEV_ID이고 PROJ_NM_LIST는 VARCHAR(1000), RMRK는 VARCHAR(100)이다. FK/인덱스 및 REG/MOD 열은 그대로다. 신규 migration/테이블/열/시퀀스/seed는 없다.

common/persistence/PersistenceBoundary.requireAllowed는 부모의 명시 INSERT/전체키 UPDATE와 상세의 명시 INSERT/동일파트월 전체 DELETE만 추가 허용한다. 부모 DELETE, 상세 UPDATE, 정산 upsert, REG/키 수정, 범위 없는 SQL은 거부한다. 이는 상태·BASE·CLOSE·revision 검사를 대체하지 않는다.

common/history/ChangeHistoryWriter.preflightSettlement(List<Change>)는 쓰기 전에 전체 명령의 감사 필드를 검사하는 진입점이다. append는 다시 같은 검사를 수행하고 필수 INSERT 건수를 확인한다. 감사키는 partId:YYYYMM 또는 partId:YYYYMM:devId이며 원문 before/after 500 code point 초과는 developerId/field/auditLimit을 가진 409 STATE_CONFLICT다. 잘라쓰기/해시/청크/숨은 RMRK 메타데이터는 없다. PROJ_NM_LIST 1000자 계약 자체를 500자로 변경하지 않는다.

## 공유 잠금과 후속 호출 계약

common/concurrent/SettlementSourceLocks.lock(Collection<String>)는 호출자 트랜잭션 MANDATORY다. 전체 이전/이후 part를 PART: 키로, 실제 참여자를 DEV:, 배정을 ASGN:, 프로젝트를 PROJECT:, 급여월을 PAY:YYYYMM으로 전달한다. mapper는 사람 소속에서 영향 part를 구하고, PART_ID 순 part → part/month header → part/standard 순으로 행 잠금을 잡은 뒤 기존 advisory key를 정렬해서 획득한다. 급여 교체는 인력이 있는 전체 part를 보수적으로 포함한다. 소속을 다시 읽어 바뀌었으면 409로 중단한다. 새 정산 SHARE 테이블 잠금은 도입하지 않는다.

Spring이 생성하는 WriteCoordinator는 5인자 생성자로 이 공유 경로를 사용한다. 기존 4인자 생성자는 기존 직접 구성 시험의 호환성을 유지한다. 직접 source writer의 기존 잠금 루프는 이 경로로 자동 전환되지 않는다. AssignmentRepository.lock, AssignmentActionsService.lock, B1Service/B5Service, B6Repository/B9Repository 및 DeveloperService/DeveloperStateService/DeveloperActionsRepository의 쓰기 진입점은 병합 단계에서 동일 경로를 첫 잠금으로 연결해야 한다. 이 연결과 실제 두 DB 트랜잭션의 경합 증거 전에는 전체 직렬화 완료를 주장하지 않는다.

confirmForClose는 이 공통 모듈에 존재하는 서비스가 아니다. 이를 구현하는 정산 서비스는 MANDATORY 호출자 트랜잭션에서 위 잠금·완전한 원천 재조회·revision·감사와 본체를 함께 처리해야 하며 REQUIRES_NEW 또는 독립 커밋을 만들면 안 된다.

## XLSX 소비 계약

ExcelExportService.generateWorkbook의 WorkbookSource.read 안에서 모든 원천/설정/metadata를 읽는다. UI-19 Specification.kind=영업파트이익금, UI-32 kind=단가재협상이며 uploadId=null이다. WorkbookData의 기존 4인자 생성자는 보존하고 5번째 filename을 추가했다. UI-19 year=YYYYMM, filename=영업파트이익금_<partId>_<YYYYMM>_이번달.xlsx 또는 _추이.xlsx; UI-32 year=YYYYMMDD, filename=단가재협상_<서울날짜>.xlsx이다. filename은 경로/제어문자를 받지 않는 allowlist로 검사한다.

숫자는 NUMBER, ID/관리번호/사용자 문자열은 TEXT, 미확인은 null로 전달한다. 임시파일 생성 → readOnly REPEATABLE_READ workbook 생성/종료 → 독립 DNLD 커밋 → Download 반환/transferTo 순서다. AccessLogWriter.Event의 기존 5인자 생성자는 보존하고 6번째 filename을 추가하여 UI-19/32 기록과 실제 파일명을 연결한다. 실패/전송종료 시 임시파일을 삭제한다.

## 시험과 증거 경계

가까운 기존 설정은 common/concurrent/WriteCoordinatorTest, common/excel/ExcelExportServiceTest, common/history/ChangeHistoryWriterTest 및 common/concurrent/ProjectProgressDatabaseTest를 재사용했다. 새 common 시험은 SettlementSourceLocksTest/SettlementSourceMapperTest, SettlementBoundaryTest/SettlementSchemaDatabaseTest, SettlementHistoryWriterTest/SettlementAccessLogTest, SettlementExportTest다. 실DB 시험은 SICOST_TEST_DB_URL이 없으면 명시적으로 skip하며 독립 스키마만 설치/정리한다.

FX-B140-SETL/RATE 및 두 업무 HTTP 조회의 baseline, 실제 GET 이후 원천 전필드 대조, 전체 브라우저 여정/시각 동등성, close 통합/실제 경합은 이 foundation 단독 산출물로 통과 처리하지 않는다. 이 단계는 UI/route/권한/설정 registry를 수정하지 않는다.
