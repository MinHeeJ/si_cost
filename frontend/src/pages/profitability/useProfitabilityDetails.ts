import { useEffect, useState, type Key } from "react";
import {
  getIndividualProfitabilityPerson,
  type IndividualProfitabilityPersonData,
  type ProfitabilityQuery,
} from "./individualProfitabilityApi";

/** Local work-tab state: opening/closing the existing drawer never changes the query or expansion. */
export function useProfitabilityDetails(
  query: ProfitabilityQuery,
  mobile: boolean,
) {
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const [personId, setPersonId] = useState<string>();
  useEffect(() => {
    setExpandedKeys([]);
    setPersonId(undefined);
  }, [query, mobile]);
  return {
    expandedKeys,
    setExpandedKeys,
    personId,
    openPerson: setPersonId,
    closePerson: () => setPersonId(undefined),
  };
}

/** Fetch only for mounted expanded ledgers. A changed query/collapse aborts and ignores late responses. */
export function useProfitabilityLedger(
  personId: string,
  query: ProfitabilityQuery,
) {
  const [data, setData] = useState<IndividualProfitabilityPersonData>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setData(undefined);
    setError(undefined);
    void getIndividualProfitabilityPerson(personId, query, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [personId, query, revision]);
  return {
    data,
    error,
    loading,
    retry: () => setRevision((value) => value + 1),
  };
}
