import { render, screen, waitFor } from "@testing-library/react";
import { expect, it } from "vitest";
import { useScreenMotion } from "./useScreenMotion";
import { DashboardSkeleton } from "../pages/dashboardops/DashboardSkeleton";

function Screen() {
  const ref = useScreenMotion("UI-16");
  return (
    <section ref={ref} className="sicost-screen">
      <div className="sicost-screen-content">
        <DashboardSkeleton />
        <section className="sicost-section-card" data-testid="loaded-card">
          조회 결과
        </section>
      </div>
    </section>
  );
}
it("decorative skeletons keep their own pulse while real cards retain the common entrance motion", async () => {
  render(<Screen />);
  await waitFor(() =>
    expect(screen.getByTestId("loaded-card")).toHaveClass("sicost-enter-stage"),
  );
  expect(
    screen
      .getByTestId("dashboard-skeleton")
      .querySelector(".sicost-enter-stage"),
  ).toBeNull();
});
