import type { ReactNode } from "react";
import { FollowUpScreen, SharedScreenFrame } from "./FollowUpScreen";

const businessPartnersGuide = (
  <dl>
    <div>
      <dt>거래처 엑셀 업로드 · 필수 입력</dt>
      <dd>
        모든 행에 구분·상호·사업자등록번호를 입력하세요. 구분은 ‘고객사’,
        ‘협력사’, ‘고객·협력’ 중 하나입니다. 고객사는 세금계산서 이메일이,
        협력사는 은행·계좌번호·예금주가 필수입니다. ‘고객·협력’은 이메일과
        계좌 정보가 모두 필요합니다.
      </dd>
    </div>
    <div>
      <dt>엑셀 작성과 반영</dt>
      <dd>
        거래처 탭에서 내려받은 XLSX 양식의 첫 시트에 입력하세요. 사업자등록번호는
        숫자 10자리 또는 3-2-5 하이픈 형식이며, 기존 거래처나 파일 안에서
        중복되면 등록할 수 없습니다. 사업자등록번호·연락처·계좌번호는
        텍스트 형식으로 입력하세요. 계좌번호는 숫자와 하이픈으로 8~20자,
        업종과 은행은 공통코드에 표시된 이름을 사용하세요.
        검사에서 오류가 없으면 같은 파일을 반영합니다.
      </dd>
    </div>
    <div>
      <dt>영업 파트 기준 적용</dt>
      <dd>기준은 적용 시작월부터 사용하며 지난 정산은 유지됩니다.</dd>
    </div>
  </dl>
);

export function BusinessPartnersScreen({ children }: { children?: ReactNode }) {
  return children ? (
    <SharedScreenFrame screenId="UI-15" screenGuide={businessPartnersGuide}>
      {children}
    </SharedScreenFrame>
  ) : (
    <FollowUpScreen screenId="UI-15" />
  );
}
