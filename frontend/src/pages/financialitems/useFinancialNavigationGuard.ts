import { useEffect, useRef, useState } from "react";

export type FinancialNavigation = {
  screenId: string;
  proceed(): void;
  label?: string;
};
export function useFinancialNavigationGuard(
  dirty: boolean,
  save: () => Promise<boolean>,
  discard: () => void,
) {
  const [request, setRequest] = useState<{
    label: string;
    immediate: boolean;
    done(value: boolean): void;
  }>();
  const resolver = useRef<(value: boolean) => void>();
  const ask = (label = "기간 변경", immediate = false): Promise<boolean> => {
    if (!dirty) return Promise.resolve(true);
    resolver.current?.(false);
    return new Promise((done) => {
      resolver.current = done;
      setRequest({ label, immediate, done });
    });
  };
  useEffect(() => {
    const changing = (event: Event) => {
      const detail = (event as CustomEvent<FinancialNavigation>).detail;
      if (detail?.screenId !== "UI-10" || !dirty) return;
      event.preventDefault();
      void ask(detail.label ?? "다른 화면으로 이동").then((allowed) => {
        if (allowed) detail.proceed();
      });
    };
    window.addEventListener("sicost:screen-navigation-request", changing);
    window.addEventListener("sicost:screen-period-changing", changing);
    return () => {
      window.removeEventListener("sicost:screen-navigation-request", changing);
      window.removeEventListener("sicost:screen-period-changing", changing);
    };
  }, [dirty, save, discard]);
  useEffect(() => () => resolver.current?.(false), []);
  const finish = (value: boolean) => {
    resolver.current?.(value);
    resolver.current = undefined;
    setRequest(undefined);
  };
  return {
    request,
    ask,
    stay: () => finish(false),
    discardAndGo: () => {
      discard();
      finish(true);
    },
    saveAndGo: async () => {
      if (await save()) finish(true);
    },
  };
}
