import { request } from "../../shared/api";

export type ProjectExpense = {
  expenseMonth: string;
  expenseSn: number;
  itemCode: string;
  itemName: string;
  amount: string;
  memo: string | null;
  registeredAt: string;
  revision: string;
};
export type ProjectExpensesData = {
  items: ProjectExpense[];
  totalAmount: string;
  revision: string;
};
export type ExpenseResult = {
  value: ProjectExpensesData;
  revision: string;
  changed: boolean;
};
export type CreateProjectExpenseRequest = {
  year: number;
  expectedRevision: string;
  expenseMonth: string;
  itemCode: string;
  amount: string;
  memo: string | null;
};
export type DeleteProjectExpenseRequest = {
  year: number;
  expectedRevision: string;
};
const path = (id: string) => `/api/projects/${encodeURIComponent(id)}/expenses`;
export const getProjectExpenses = (id: string) =>
  request<ProjectExpensesData>(path(id));
export const createProjectExpense = (
  id: string,
  body: CreateProjectExpenseRequest,
) =>
  request<ExpenseResult>(path(id), {
    method: "POST",
    body: JSON.stringify(body),
  });
export const deleteProjectExpense = (
  id: string,
  row: Pick<ProjectExpense, "expenseMonth" | "expenseSn">,
  body: DeleteProjectExpenseRequest,
) =>
  request<ExpenseResult>(
    `${path(id)}/${encodeURIComponent(row.expenseMonth)}/${row.expenseSn}`,
    { method: "DELETE", body: JSON.stringify(body) },
  );
