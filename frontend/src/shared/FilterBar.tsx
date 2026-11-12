import type { ReactNode } from "react";

export type FilterBarProps = {
  /** 조회 버튼/Enter. 화면이 입력 중인 조건을 실제 조회 조건으로 확정한다. */
  onSubmit(): void;
  /** 초기화 버튼. 화면이 조건을 기본값으로 되돌려 다시 조회한다. */
  onReset(): void;
  /** 조건 칸들. 각 칸은 `<label className="sicost-field">이름표 <입력/></label>` 모양이다. */
  children: ReactNode;
  testId?: string;
  className?: string;
  resetTestId?: string;
  searchTestId?: string;
  /** 초기화·조회 왼쪽에 붙이는 추가 칸이나 버튼. */
  extra?: ReactNode;
};

/**
 * 검색 조건 영역의 공통 부품(요구서 3.1 `FilterBar`).
 * 이름표는 칸 왼쪽, [초기화]·[조회]는 오른쪽 끝에 둔다. 조건의 종류와 조회 방법은 화면이 정한다.
 */
export function FilterBar({
  onSubmit,
  onReset,
  children,
  testId,
  className = "",
  resetTestId,
  searchTestId,
  extra,
}: FilterBarProps) {
  return (
    <form
      className={`sicost-filterbar ${className}`.trim()}
      data-testid={testId}
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      {children}
      <div className="sicost-filterbar-actions">
        {extra}
        <button type="button" data-testid={resetTestId} onClick={onReset}>
          초기화
        </button>
        <button
          type="submit"
          className="sicost-primary"
          data-testid={searchTestId}
        >
          조회
        </button>
      </div>
    </form>
  );
}
