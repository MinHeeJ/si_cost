import { useState } from "react";
import { DataTable, type DataTableProps } from "../../shared/DataTable";
import { useTablePreferences } from "../me/ColumnPreferencesDialog";
import { usePreferences } from "../me/usePreferences";
import { ErrorBanner } from "../../shared/Feedback";
import { profitabilityColumns } from "./profitabilityColumns";
import { columnHelp } from "../../shared/helpContent";

export type ProfitabilityTableProps<T> = Omit<
  DataTableProps<T>,
  "onColumnsChange" | "dense" | "testId"
>;

/** UI-03 table boundary. The overview supplies API rows, totals and detail callbacks;
 * this component owns only presentation preferences, never financial calculations. */
export function ProfitabilityTable<T>(props: ProfitabilityTableProps<T>) {
  const [saveError, setSaveError] = useState("");
  const preferences = usePreferences();
  const table = useTablePreferences(
    "UI-03",
    "individualProfitability",
    profitabilityColumns,
  );
  const columns = table.columns.flatMap((definition) => {
    const column = props.columns.find((item) => item.id === definition.id);
    return column
      ? [
          {
            ...column,
            help: columnHelp.profitability[
              column.id === "profit" ? "operatingProfit" : column.id
            ],
          },
        ]
      : [];
  });
  const size =
    table.pageSize === 20 || table.pageSize === 100 ? table.pageSize : 50;
  return (
    <div data-testid="profitability-table-region">
      <DataTable
        {...props}
        columns={columns}
        dense={table.dense}
        size={props.size ?? size}
        testId="profitability-table"
        onSizeChange={
          props.onSizeChange ??
          ((next) => {
            setSaveError("");
            void preferences
              .put("PAGE_SIZE", "*", next)
              .catch((error: unknown) => {
                setSaveError(
                  error instanceof Error
                    ? error.message
                    : "설정을 저장하지 못했습니다.",
                );
              });
          })
        }
      />
      {saveError && <ErrorBanner message={saveError} />}
    </div>
  );
}
