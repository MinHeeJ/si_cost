import "./screenLoading.css";

type ScreenLoadingProps = {
  message: string;
  inline?: boolean;
  testId?: string;
};

export function ScreenLoading({ message, inline = false, testId }: ScreenLoadingProps) {
  return (
    <div
      className={inline ? "sicost-screen-loading is-inline" : "sicost-screen-loading"}
      role="status"
      data-testid={testId}
    >
      <span className="sicost-screen-loading-spinner" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}
