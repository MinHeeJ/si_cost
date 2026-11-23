import { useRef } from "react";
import { useLocation } from "react-router-dom";
import { SharedScreenFrame } from "./FollowUpScreen";
import { BillsScreen, type BillsScreenProps } from "../bills/BillsScreen";
export function BillingReceiptsScreen(props: BillsScreenProps = {}) {
  const location = useLocation();
  const incoming = location.state as {
    month?: string;
    section?: string;
    filter?: string;
    fromMonthClose?: boolean;
    fromCashFlow?: boolean;
    fromDashboard?: boolean;
    fromActivity?: boolean;
    fromNotification?: boolean;
    billId?: string;
  } | null;
  const entry = useRef<{
    key: string;
    month?: string;
    section?: string;
    filter?: string;
  }>();
  if (
    location.pathname === "/UI-13" &&
    (incoming?.fromMonthClose ||
      incoming?.fromCashFlow ||
      incoming?.fromDashboard ||
      incoming?.fromActivity) &&
    entry.current?.key !== location.key
  )
    entry.current = {
      key: location.key,
      month: incoming.month,
      section: incoming.section,
      filter: incoming.filter,
    };
  const month = entry.current?.month;
  return (
    <SharedScreenFrame
      key={entry.current?.key}
      screenId="UI-13"
      initialMonth={month}
      screenGuide={
        <dl>
          <div>
            <dt>청구 목록과 요약</dt>
            <dd>
              상단 요약 카드는 선택한 연도와 이월 설정을 기준으로 집계합니다.
              청구 목록의 검색 조건을 바꿔도 요약 값은 바뀌지 않습니다.
            </dd>
          </div>
          <div>
            <dt>청구 누락 점검</dt>
            <dd>
              예상 공급가에서 취소되지 않은 청구의 공급가 합계를 빼서 차이를
              확인합니다. 예상 공급가는 유상 배정별 적용 단가에 월 MM을 곱한
              금액을 절사해 합산합니다. 예정 건도 포함되며 보호된 점검 기록은
              유지됩니다.
            </dd>
          </div>
          <div>
            <dt>허용오차와 판정</dt>
            <dd>
              허용오차는 예상 공급가와 실제 청구 공급가의 차이를 어디까지
              ‘맞음’으로 볼지 정하는 기준입니다. 청구가 있고 차이의 절댓값이 이
              기준 이하면 ‘맞음’으로 표시합니다. 허용오차는 최소 허용오차와
              예상 공급가에 허용률을 곱한 값 중 큰 값이며, 프로젝트별로
              계산합니다. 표의 ‘설명’ 열에서 T가 해당 행의 허용오차이며,
              표의 금액 표시 단위와 관계없이 원으로 표시하며 반올림하지 않습니다.
            </dd>
          </div>
          <div>
            <dt>미수 알림</dt>
            <dd>
              조회 기준일에 입금 예정일이 지난 청구는 알림 전 단계여도 표시합니다.
              현재 수신 대상과 과거 실제 수신자는 다를 수 있습니다. 다시 알림을
              요청하기 전에 수신자와 잔액을 확인하세요.
            </dd>
          </div>
        </dl>
      }
    >
      <BillsScreen
        {...props}
        initialMonth={month}
        initialSection={entry.current?.section}
        initialMissingInvoiceNo={
          entry.current?.filter === "BILL_NUMBER_MISSING"
        }
        initialBillId={
          location.pathname === "/UI-13" &&
          incoming?.fromNotification &&
          /^B-\d{6}-\d{3}$/.test(incoming.billId ?? "")
            ? incoming.billId
            : undefined
        }
      />
    </SharedScreenFrame>
  );
}
