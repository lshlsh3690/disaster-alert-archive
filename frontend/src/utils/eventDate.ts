export function formatEventPeriod(
  firstAlertAt: string,
  lastAlertAt: string,
  labels: { sameDay: string; daysSpan: string }
): string {
  const first = new Date(firstAlertAt);
  const last = new Date(lastAlertAt);

  const fmtMD = (d: Date) =>
    `${String(d.getFullYear()).slice(2)}/${String(d.getMonth() + 1).padStart(2, "0")}/${String(d.getDate()).padStart(2, "0")}`;
  const diffMs = last.setHours(0, 0, 0, 0) - first.setHours(0, 0, 0, 0);
  const days = Math.round(diffMs / 86_400_000);

  if (days === 0) return `${fmtMD(new Date(firstAlertAt))} · ${labels.sameDay}`;
  return `${fmtMD(new Date(firstAlertAt))} ~ ${fmtMD(new Date(lastAlertAt))} · ${days + 1}${labels.daysSpan}`;
}
