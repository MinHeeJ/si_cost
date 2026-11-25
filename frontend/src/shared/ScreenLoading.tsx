import "./screenLoading.css";

type ScreenLoadingProps = {
  message?: string;
  inline?: boolean;
  testId?: string;
};

export function ScreenLoading({
  message = "불러오는 중입니다.",
  inline = false,
  testId,
}: ScreenLoadingProps) {
  const Container = inline ? "span" : "div";
  return (
    <Container
      className={inline ? "sicost-screen-loading is-inline" : "sicost-screen-loading"}
      role="status"
      data-testid={testId}
    >
      <span className="sicost-screen-loading-spinner" aria-hidden="true" />
      {message}
    </Container>
  );
}
