export function formatEventPeriod(firstAlertAt: string, lastAlertAt: string): string {
  const first = new Date(firstAlertAt);
  const last = new Date(lastAlertAt);

  const fmtMD = (d: Date) => `${d.getMonth() + 1}/${d.getDate()}`;
  const diffMs = last.setHours(0, 0, 0, 0) - first.setHours(0, 0, 0, 0);
  const days = Math.round(diffMs / 86_400_000);

  if (days === 0) return `${fmtMD(new Date(firstAlertAt))} · 당일`;
  return `${fmtMD(new Date(firstAlertAt))} ~ ${fmtMD(new Date(lastAlertAt))} · ${days + 1}일간`;
}
