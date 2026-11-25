import { render, screen, waitFor } from "@testing-library/react";
import { expect, it } from "vitest";
import { useScreenMotion } from "./useScreenMotion";
import { ScreenLoading } from "./ScreenLoading";

function Screen() {
  const ref = useScreenMotion("UI-16");
  return (
    <section ref={ref} className="sicost-screen">
      <div className="sicost-screen-content">
        <ScreenLoading />
        <section className="sicost-section-card" data-testid="loaded-card">
          조회 결과
        </section>
      </div>
    </section>
  );
}
it("loading indicators stay outside the common card entrance motion", async () => {
  render(<Screen />);
  await waitFor(() =>
    expect(screen.getByTestId("loaded-card")).toHaveClass("sicost-enter-stage"),
  );
  expect(screen.getByRole("status")).not.toHaveClass("sicost-enter-stage");
});
