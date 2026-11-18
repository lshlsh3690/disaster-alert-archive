import type { Ratio } from "@/api/clusteringAdminApi";

// 관리자 클러스터링 화면의 표시 규칙 모음. 숫자는 서버가 계산한 값을 그대로 쓰고 여기서는 글자로만 바꾼다.

const KST_DATE = new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" });

/** 조회 범위 상한(양 끝 포함) — 서버 MAX_RANGE_DAYS 와 같다. */
export const MAX_RANGE_DAYS = 366;

/**
 * 한국 시간 기준 날짜(yyyy-MM-dd). 일일 채점·검사 날짜가 KST 날짜라 브라우저 시간대와 무관하게 맞춘다.
 * 로케일 출력 형식(예: en-CA 의 yyyy-MM-dd)에 기대지 않고 연·월·일을 꺼내 직접 조립한다.
 */
export function kstDate(date: Date): string {
  const part = (type: string) => KST_DATE.formatToParts(date).find((p) => p.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}

/** yyyy-MM-dd 에 days 일을 더한 날짜(음수면 뺀다). */
export function addDays(ymd: string, days: number): string {
  const d = new Date(`${ymd}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** 끝을 시작 이상, 시작 + 365일 이하로 맞춘다(시작을 바꿨을 때). */
export function clampEnd(from: string, to: string): string {
  const max = addDays(from, MAX_RANGE_DAYS - 1);
  return to < from ? from : to > max ? max : to;
}

/** 시작을 끝 이하, 끝 - 365일 이상으로 맞춘다(끝을 바꿨을 때). */
export function clampStart(from: string, to: string): string {
  const min = addDays(to, -(MAX_RANGE_DAYS - 1));
  return from > to ? to : from < min ? min : from;
}

/** 기본 범위: 오늘(아직 채점 전) 제외 최근 30일. */
export function defaultRange(now = new Date()): { from: string; to: string } {
  const to = addDays(kstDate(now), -1);
  return { from: addDays(to, -29), to };
}

/** 비율은 항상 분자/분모와 함께 — 분모가 0 이면 "계산할 수 없음"(0% 와 구별). */
export function formatRatio(ratio: Ratio | null | undefined): string {
  if (!ratio) return "-";
  if (ratio.percent === null) return `${ratio.numerator}/${ratio.denominator} (계산할 수 없음)`;
  return `${ratio.numerator}/${ratio.denominator} (${ratio.percent.toFixed(1)}%)`;
}

/** 서버 시각(yyyy-MM-ddTHH:mm:ss, KST)을 "MM-dd HH:mm" 으로. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  return value.replace("T", " ").slice(5, 16);
}

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? "-" : value.toLocaleString("ko-KR");
}
