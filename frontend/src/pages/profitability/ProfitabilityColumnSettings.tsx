import { useState } from "react";
import { ColumnPreferencesDialog } from "../me/ColumnPreferencesDialog";
import { profitabilityColumns } from "./profitabilityColumns";

/** No role gating in temporary-admin mode; optional salary references are user-selectable. */
export function ProfitabilityColumnSettings() {
  const [open, setOpen] = useState(false);
  return (
    <div data-testid="profitability-column-settings">
      <button
        type="button"
        data-testid="profitability-columns-button"
        onClick={() => setOpen(true)}
      >
        열 설정
      </button>
      {open && (
        <ColumnPreferencesDialog
          open
          onClose={() => setOpen(false)}
          screenId="UI-03"
          viewId="individualProfitability"
          columns={profitabilityColumns}
        />
      )}
    </div>
  );
}
