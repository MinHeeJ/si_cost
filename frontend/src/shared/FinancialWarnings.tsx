import { useState } from "react";
import { Dialog } from "./Dialog";
import { Icon } from "./Icon";
import "./financialWarnings.css";

export function FinancialWarnings({
  warnings,
  description,
}: {
  warnings: string[];
  description?: string;
}) {
  const [open, setOpen] = useState(false);
  const label = `확인 필요 ${warnings.length}건`;
  const groups = new Map<string, string[]>();
  for (const warning of warnings) {
    const prefix = warning.match(/^(\d{4}-(?:0[1-9]|1[0-2]))\s+/);
    const month = prefix?.[1] ?? "";
    const message = prefix ? warning.slice(prefix[0].length) : warning;
    const messages = groups.get(month);
    if (messages) messages.push(message);
    else groups.set(month, [message]);
  }
  const close = () => setOpen(false);

  return (
    <>
      <button
        type="button"
        className="sicost-financial-warning"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <Icon name="warning" size={16} />
        {label}
      </button>
      <Dialog
        open={open}
        title={label}
        onConfirm={close}
        onCancel={close}
        confirmLabel="닫기"
        hideCancel
        wide
      >
        {description && <p className="sicost-financial-warning-description">{description}</p>}
        <div className="sicost-financial-warning-groups">
          {[...groups].map(([month, messages]) => (
            <section className="sicost-financial-warning-group" key={month}>
              <h3>{month || "기타 확인 사항"}</h3>
              <ul>
                {messages.map((message) => <li key={message}>{message}</li>)}
              </ul>
            </section>
          ))}
        </div>
      </Dialog>
    </>
  );
}
