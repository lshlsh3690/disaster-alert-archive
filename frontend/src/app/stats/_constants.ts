import type { TFunction } from "i18next";
import type { I18nKey } from "@/constants/i18n";

type T = TFunction;

// ─── 공통 타입 ────────────────────────────────────────────────────────────────

export interface TypeStat   { type: string | null;  count: number }
export interface LevelStat  { level: string | null; count: number }
export interface RegionStat { region: string;        count: number }

export interface LibItem {
  id: string; kind: string; title: string; desc: string; help: string;
  defaultSpan: number; sample?: boolean;
  variants?: { key: string; label: string }[];
}

export interface WidgetItem { id: string; libId: string; span: number; variant?: string }

// ─── 색상 팔레트 ──────────────────────────────────────────────────────────────
// KRDS 표준형(design_handoff_krds_redesign/README.md "Design Tokens"). 강조색은 5% 이하로 쓰므로
// 막대는 한 가지 primary 색으로 칠하고, 여러 색이 꼭 필요한 차트(도넛·누적 막대·산점도)만
// primary 단계색(진함→옅음) 다음에 gray 를 잇는다.

// 막대 기본 채움(primary-50)
export const BAR_FILL       = "#256ef4";
// 강조가 아닌 막대(primary-30) — 일별 막대의 지난날, 세로 막대의 최댓값 외 막대
export const BAR_FILL_MUTED = "#86aff9";
// 차트 격자선·도넛 트랙(gray-10)
export const CHART_GRID     = "#e6e8ea";
// 축 눈금 글자(gray-50)
export const CHART_TICK     = "#6d7882";
// 막대 hover 커서 배경(gray-5)
export const CHART_CURSOR   = "#f4f5f6";
// 농도로 건수를 보여주는 차트(히트맵·요일별·시간대별)의 기준색 primary-50 의 RGB — rgba(…, 농도) 로 쓴다
export const HEAT_RGB       = "37,110,244";

// 도넛 차트·유형 범례·산점도에 쓰이는 6색 (primary 단계 → gray)
export const TYPE_COLORS   = ["#0b50d0","#256ef4","#86aff9","#d8e5fd","#58616a","#b1b8be"];
// 지역별 누적 막대에 쓰이는 8색 (primary 단계 → gray 단계)
export const BAR_COLORS    = ["#0b50d0","#256ef4","#86aff9","#d8e5fd","#464c53","#8a949e","#b1b8be","#cdd1d5"];
// 유형별 누적 막대(stacked) 4색
export const STACKED_COLORS = ["#0b50d0","#256ef4","#86aff9","#d8e5fd"];

// 경보 단계별 표시 텍스트·색상 (KRDS System 색: 안전안내 info / 긴급재난 warning / 위급재난 danger)
// bg·textCls 는 카드 칸, solid 는 비율 게이지에 쓴다. 상태를 색만으로 구분하지 않도록 항상 text 와 함께 표시한다.
export function getLevelMeta(t: T): Record<string, { text: string; bg: string; textCls: string; solid: string }> {
  return {
    LEVEL_1: { text: t("statsPage.levels.LEVEL_1"), bg: "bg-[#e7f4fe]", textCls: "text-[#085a98]", solid: "#085a98" },
    LEVEL_2: { text: t("statsPage.levels.LEVEL_2"), bg: "bg-[#fff3db]", textCls: "text-[#8a5c00]", solid: "#8a5c00" },
    LEVEL_3: { text: t("statsPage.levels.LEVEL_3"), bg: "bg-[#fdefec]", textCls: "text-[#bd2c0f]", solid: "#bd2c0f" },
  };
}

// ─── 히트맵 상수 ──────────────────────────────────────────────────────────────

// 히트맵 Y축 요일 레이블 (월~일 순서)
export function getWeekdays(t: T): string[] {
  const w = t("statsPage.weekdays", { returnObjects: true }) as I18nKey["ko"]["statsPage"]["weekdays"];
  return [w.mon, w.tue, w.wed, w.thu, w.fri, w.sat, w.sun];
}
// PostgreSQL EXTRACT(DOW): 0=Sun,1=Mon,...,6=Sat → 표시 순서 Mon=0..Sun=6
export const DOW_TO_IDX: Record<number, number> = { 1:0, 2:1, 3:2, 4:3, 5:4, 6:5, 0:6 };

// ─── 위젯 라이브러리 정의 ─────────────────────────────────────────────────────

// 사용 가능한 모든 위젯의 정적 정의 목록 (id, 차트 종류, 기본 스팬, 변형 옵션 등)
export function getWidgetLibrary(t: T): LibItem[] {
  const w = t("statsPage.widgets", { returnObjects: true }) as I18nKey["ko"]["statsPage"]["widgets"];
  const v = t("statsPage.variants", { returnObjects: true }) as I18nKey["ko"]["statsPage"]["variants"];
  return [
    { id: "type-donut", kind: "donut", title: w.typeDistribution.title, desc: w.typeDistribution.desc, help: w.typeDistribution.help,
      defaultSpan: 6, variants: [{ key: "donut", label: v.donut }, { key: "hbar", label: v.horizontalBar }, { key: "vbar", label: v.verticalBar }] },
    { id: "sido-bar", kind: "hbar", title: w.regionTop.title, desc: w.regionTop.desc, help: w.regionTop.help,
      defaultSpan: 6, variants: [{ key: "hbar", label: v.horizontalBar }, { key: "vbar", label: v.verticalBar }, { key: "donut", label: v.donut }] },
    { id: "level-card", kind: "levels", title: w.levelDistribution.title, desc: w.levelDistribution.desc, help: w.levelDistribution.help,
      defaultSpan: 6, variants: [{ key: "card", label: v.card }, { key: "donut", label: v.donut }, { key: "hbar", label: v.bar }] },
    { id: "daily-line", kind: "line", title: w.dailyTrend.title, desc: w.dailyTrend.desc, help: w.dailyTrend.help,
      defaultSpan: 12, variants: [{ key: "line", label: v.line }, { key: "bar", label: v.bar }] },
    { id: "heatmap", kind: "heatmap", title: w.timeHeatmap.title, desc: w.timeHeatmap.desc, help: w.timeHeatmap.help,
      defaultSpan: 12, variants: [{ key: "heatmap", label: v.heatmap }, { key: "dow", label: v.weekday }, { key: "hour", label: v.hour }] },
    { id: "stacked", kind: "stacked", title: w.monthlyTypes.title, desc: w.monthlyTypes.desc, help: w.monthlyTypes.help,
      defaultSpan: 6, variants: [{ key: "donut", label: v.donut }, { key: "hbar", label: v.horizontalBar }, { key: "bar", label: v.verticalBar }] },
    { id: "compare", kind: "compare", title: w.yearComparison.title, desc: w.yearComparison.desc, help: w.yearComparison.help,
      defaultSpan: 12, variants: [{ key: "bar", label: v.bar }, { key: "line", label: v.line }] },
    { id: "weather-overlay", kind: "weather", title: w.weatherCorrelation.title, desc: w.weatherCorrelation.desc, help: w.weatherCorrelation.help,
      defaultSpan: 12, variants: [{ key: "type", label: v.byType }, { key: "region", label: v.byRegion }] },
    { id: "weather-scatter", kind: "weather2", title: w.weatherScatter.title, desc: w.weatherScatter.desc, help: w.weatherScatter.help,
      defaultSpan: 6, variants: [{ key: "scatter", label: v.scatter }, { key: "overlay", label: v.overlay }] },
  ];
}

// 초기 로드 시 표시하는 기본 위젯 배치 (5개 위젯)
export const DEFAULT_LAYOUT: WidgetItem[] = [
  { id: "w1", libId: "type-donut",       span: 6  },
  { id: "w2", libId: "sido-bar",         span: 6  },
  { id: "w3", libId: "level-card",       span: 6  },
  { id: "w4", libId: "daily-line",       span: 12 },
  { id: "w5", libId: "weather-overlay",  span: 12 },
];
