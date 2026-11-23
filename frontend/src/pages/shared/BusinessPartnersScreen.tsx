import type { ReactNode } from "react";
import { FollowUpScreen, SharedScreenFrame } from "./FollowUpScreen";
export function BusinessPartnersScreen({
  children,
  showSalesPartsGuide = false,
}: {
  children?: ReactNode;
  showSalesPartsGuide?: boolean;
}) {
  return children ? (
    <SharedScreenFrame
      screenId="UI-15"
      screenGuide={
        showSalesPartsGuide ? (
          <dl>
            <div>
              <dt>영업 파트 기준 적용</dt>
              <dd>기준은 적용 시작월부터 사용하며 지난 정산은 유지됩니다.</dd>
            </div>
          </dl>
        ) : undefined
      }
    >
      {children}
    </SharedScreenFrame>
  ) : (
    <FollowUpScreen screenId="UI-15" />
  );
}
