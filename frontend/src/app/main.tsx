import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { ApplicationRouter } from "./router";
import { request } from "../shared/api";
import { ErrorBanner, StatusBadge } from "../shared/Feedback";
import "../shared/common.css";

export function ApplicationStatus() {
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [message, setMessage] = useState("");
  const check = () => {
    setState("loading");
    void request<void>("/api/health")
      .then(() => setState("ready"))
      .catch((error: unknown) => {
        setMessage(
          error instanceof Error
            ? error.message
            : "연결을 확인하지 못했습니다.",
        );
        setState("error");
      });
  };
  useEffect(check, []);
  return (
    <main data-testid="application-status" style={{ padding: 24 }}>
      <h1>프로젝트 · 원가 관리</h1>
      {state === "loading" && (
        <p role="status">데이터베이스 연결을 확인하고 있습니다.</p>
      )}
      {state === "ready" && (
        <StatusBadge tone="ok">데이터베이스 연결 정상</StatusBadge>
      )}
      {state === "error" && <ErrorBanner message={message} onRetry={check} />}
    </main>
  );
}

const element = document.getElementById("root");
if (element)
  createRoot(element).render(
    <StrictMode>
      <ApplicationRouter />
    </StrictMode>,
  );
