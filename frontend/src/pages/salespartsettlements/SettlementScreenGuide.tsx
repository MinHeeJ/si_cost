import { useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { formatAmount, formatRate } from "../../shared/format";
import { Icon } from "../../shared/Icon";
import type { SettlementResult } from "./salesPartSettlementsApi";

export function SettlementScreenGuide({
  showFinancial = false,
}: {
  showFinancial?: boolean;
}) {
  return (
    <dl>
      <div>
        <dt>조회와 확정 이력</dt>
        <dd>
          기준월과 영업 파트를 고르면 이번 달 정산을 확인할 수 있습니다. 기간
          추이는 선택한 해 1월부터 기준월까지입니다. 자동 확정이나 수동 확정이
          표시된 정산은 해당 월의 확정 결과입니다.
        </dd>
      </div>
      {showFinancial && (
        <div>
          <dt>표시 단위와 법인카드</dt>
          <dd>
            상단의 단위 선택은 요약과 정산표에 표시할 금액 단위를 바꿉니다.
            법인카드 사용액은 원 단위로 기록하며 해당 파트의 이익금에서
            차감합니다.
          </dd>
        </div>
      )}
    </dl>
  );
}

export function SettlementCalculationHelp({
  standard,
}: {
  standard: SettlementResult["standard"];
}) {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  return (
    <>
      <button
        type="button"
        className="sicost-help-button sicost-help-icon settlement-calculation-help"
        aria-label="계산 방법 도움말"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <Icon name="info" size={14} />
      </button>
      <Dialog
        open={open}
        title="계산 방법"
        testId="settlement-calculation-guide"
        onCancel={close}
        onConfirm={close}
        confirmLabel="닫기"
        hideCancel
        wide
      >
        <div className="sicost-table-viewport">
          <table className="settlement-guide-table" aria-label="계산 방법">
            <thead>
              <tr>
                <th scope="col">항목</th>
                <th scope="col">계산식</th>
                <th scope="col">참고</th>
              </tr>
            </thead>
            <tbody>
              <tr data-testid="settlement-guide-mm">
                <th scope="row">MM</th>
                <td>프로젝트별 MM을 모두 더함</td>
                <td>
                  투입을 시작·끝낸 달은 날짜만큼 (예: 15일 시작 → 17/31) · 내부
                  프로젝트 제외
                </td>
              </tr>
              <tr data-testid="settlement-guide-price">
                <th scope="row">단가</th>
                <td>프로젝트별 (월 단가 × MM)을 모두 더함</td>
                <td>
                  턴키는 배분표의 그 사람 그 달 금액, 배분표가 없으면 그 달 원가
                  × (계약금액 ÷ 예산) · 무상지원 0
                </td>
              </tr>
              <tr data-testid="settlement-guide-pay">
                <th scope="row">급여</th>
                <td>그 달 실제 지급액</td>
                <td>
                  MM과 관계없이 그 달 받은 급여 전부. 투입을 시작한 달은
                  날짜만큼: 월 급여 × (그 달 일수 − 시작일 + 1) ÷ 그 달 일수
                  (엑셀 「원가표」 방식)
                </td>
              </tr>
              <tr data-testid="settlement-guide-expense">
                <th scope="row">공통관리비</th>
                <td>
                  파트 기준값(
                  {formatAmount(standard.salesExpenseAmount, "원")}
                  원)
                </td>
                <td>외주(업체 인력)만 제외 — 정규·계약직·프리랜서에 붙음</td>
              </tr>
              <tr data-testid="settlement-guide-retirement">
                <th scope="row">퇴직금</th>
                <td>급여 ÷ 12</td>
                <td>정규직에만</td>
              </tr>
              <tr data-testid="settlement-guide-cost">
                <th scope="row">비용</th>
                <td>급여 + 공통관리비 + 퇴직금</td>
                <td />
              </tr>
              <tr data-testid="settlement-guide-profit">
                <th scope="row">이익</th>
                <td>단가 − 비용</td>
                <td>적자면 음수</td>
              </tr>
              <tr data-testid="settlement-guide-share">
                <th scope="row">이익금</th>
                <td>
                  이익 × 이익 배분율(
                  {formatRate(standard.distributionRate)})
                </td>
                <td>
                  원 단위 버림 (음수는 0 쪽으로 자름: −933,333.2 → −933,333) ·
                  배분율은 그 달에 적용되는 파트 기준 이력 값
                </td>
              </tr>
              <tr data-testid="settlement-guide-part-profit">
                <th scope="row">파트 이익금</th>
                <td>인력별 이익금을 모두 더함(적자 포함) − 법인카드 사용액</td>
                <td />
              </tr>
              <tr data-testid="settlement-guide-period">
                <th scope="row">기간 추이 합계</th>
                <td>투입한 달(MM &gt; 0)의 값만 더함</td>
                <td>
                  투입 안 한 달은 – · 맨 아래 「파트 이익금」 줄은 달마다 인력별
                  이익금 합 − 그 달 법인카드 사용액
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>
          인력은 그 사람의 소속 영업 파트로 모읍니다. 미확인은 –이며 0과
          다릅니다. 확정 사본의 저장되지 않은 당시 근거는 현재 자료로 복원하지
          않습니다.
        </p>
      </Dialog>
    </>
  );
}
