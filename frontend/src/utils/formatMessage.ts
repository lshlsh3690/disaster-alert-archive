// i18n 문자열의 "{key}" 플레이스홀더를 값으로 치환한다.
// 예: formatMessage("최근 {days}일", { days: 7 }) → "최근 7일"
export function formatMessage(template: string, params: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key) => (key in params ? String(params[key]) : match));
}
