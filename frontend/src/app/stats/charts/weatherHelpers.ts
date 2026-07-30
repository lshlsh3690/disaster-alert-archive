import type { TFunction } from "i18next";

/**
 * weatherHelpers.ts
 *
 * WeatherByTypeChart/WeatherByRegionChart/WeatherOverlayChart 등 날씨 관련
 * 차트가 공통으로 쓰는 날짜 집계 헬퍼입니다. 날짜 수가 많으면 일별→주별→월별로
 * 자동 집계해 막대 수를 제한합니다.
 */

export type AggMode = "daily" | "weekly" | "monthly";

// 날짜 수에 따라 집계 단위를 결정 (60일 이하: 일별, 181일 이상: 월별)
export function getAggMode(dateCount: number): AggMode {
  if (dateCount <= 60) return "daily";
  if (dateCount <= 180) return "weekly";
  return "monthly";
}

// 날짜 문자열을 집계 단위의 대표 키로 변환
// weekly: 해당 날짜가 속한 주의 월요일 날짜를 키로 사용
export function getAggKey(date: string, mode: AggMode): string {
  if (mode === "daily") return date;
  if (mode === "monthly") return date.slice(0, 7);
  // new Date("YYYY-MM-DD")는 UTC 자정으로 파싱되지만 getDay/setDate는 로컬 타임존
  // 기준이라, UTC 음수 오프셋 환경에서는 하루 전날 요일로 계산돼 주 경계가 밀린다.
  // UTC API로 통일해 브라우저 타임존과 무관하게 동일한 키가 나오게 한다.
  const d = new Date(`${date}T00:00:00Z`);
  const dow = d.getUTCDay();
  d.setUTCDate(d.getUTCDate() - (dow === 0 ? 6 : dow - 1));
  return d.toISOString().slice(0, 10);
}

// 집계 키를 차트 X축 레이블로 변환 (월별: "7월", 나머지: "MM-DD")
export function fmtKey(key: string, mode: AggMode, monthSuffix: string): string {
  if (mode === "monthly") return `${parseInt(key.slice(5))}${monthSuffix}`;
  return key.slice(5);
}

// 집계 단위 안내 텍스트 (차트 우상단에 표시)
export function aggModeLabel(mode: AggMode, t: TFunction): string {
  if (mode === "weekly") return t("statsPage.weatherChart.weeklyAggregate");
  if (mode === "monthly") return t("statsPage.weatherChart.monthlyAggregate");
  return "";
}
