import type { TFunction } from "i18next";
import type { I18nKey } from "@/constants/i18n";

type T = TFunction;

// ─── 공통 타입 ────────────────────────────────────────────────────────────────

export interface TypeStat   { type: string | null;  count: number }
export interface LevelStat  { level: string | null; count: number }
export interface RegionStat { region: string;        count: number }

export interface LibItem {
  id: string; kind: string; title: string; desc: string; help: string;
  defaultSpan: number; icon: string; sample?: boolean;
  variants?: { key: string; label: string }[];
}

export interface WidgetItem { id: string; libId: string; span: number; variant?: string }

// ─── 색상 팔레트 ──────────────────────────────────────────────────────────────

// 도넛 차트·유형 범례에 쓰이는 기본 6색 팔레트
export const TYPE_COLORS   = ["#3b82f6","#f59e0b","#f97316","#a855f7","#06b6d4","#9ca3af"];
// 가로/세로 막대 차트에 쓰이는 8색 팔레트
export const BAR_COLORS    = ["#ef4444","#f97316","#f59e0b","#3b82f6","#06b6d4","#a855f7","#6b7280","#9ca3af"];
// 누적 차트(stacked) 유형별 4색 팔레트
export const STACKED_COLORS = ["#3b82f6","#f59e0b","#f97316","#a855f7"];
// 범용 다색 팔레트 (유형·지역 등 10가지)
export const CHART_COLORS  = ["#3b82f6","#f97316","#22c55e","#a855f7","#ef4444","#eab308","#06b6d4","#ec4899","#84cc16","#f43f5e"];

// 경보 단계별 표시 텍스트·색상 정의 (안전안내/긴급재난/위급재난)
export function getLevelMeta(t: T): Record<string, { text: string; bg: string; textCls: string; border: string; solid: string }> {
  return {
    LEVEL_1: { text: t("statsPage.levels.LEVEL_1"), bg: "bg-blue-50",   textCls: "text-blue-700",   border: "border-blue-200",   solid: "#3b82f6" },
    LEVEL_2: { text: t("statsPage.levels.LEVEL_2"), bg: "bg-orange-50", textCls: "text-orange-700", border: "border-orange-200", solid: "#f97316" },
    LEVEL_3: { text: t("statsPage.levels.LEVEL_3"), bg: "bg-red-50",    textCls: "text-red-700",    border: "border-red-200",    solid: "#ef4444" },
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
      defaultSpan: 6, icon: "🍩", variants: [{ key: "donut", label: v.donut }, { key: "hbar", label: v.horizontalBar }, { key: "vbar", label: v.verticalBar }] },
    { id: "sido-bar", kind: "hbar", title: w.regionTop.title, desc: w.regionTop.desc, help: w.regionTop.help,
      defaultSpan: 6, icon: "📊", variants: [{ key: "hbar", label: v.horizontalBar }, { key: "vbar", label: v.verticalBar }, { key: "donut", label: v.donut }] },
    { id: "level-card", kind: "levels", title: w.levelDistribution.title, desc: w.levelDistribution.desc, help: w.levelDistribution.help,
      defaultSpan: 6, icon: "🚨", variants: [{ key: "card", label: v.card }, { key: "donut", label: v.donut }, { key: "hbar", label: v.bar }] },
    { id: "daily-line", kind: "line", title: w.dailyTrend.title, desc: w.dailyTrend.desc, help: w.dailyTrend.help,
      defaultSpan: 12, icon: "📈", variants: [{ key: "line", label: v.line }, { key: "bar", label: v.bar }] },
    { id: "heatmap", kind: "heatmap", title: w.timeHeatmap.title, desc: w.timeHeatmap.desc, help: w.timeHeatmap.help,
      defaultSpan: 12, icon: "🔥", variants: [{ key: "heatmap", label: v.heatmap }, { key: "dow", label: v.weekday }, { key: "hour", label: v.hour }] },
    { id: "stacked", kind: "stacked", title: w.monthlyTypes.title, desc: w.monthlyTypes.desc, help: w.monthlyTypes.help,
      defaultSpan: 6, icon: "🪜", variants: [{ key: "donut", label: v.donut }, { key: "hbar", label: v.horizontalBar }, { key: "bar", label: v.verticalBar }] },
    { id: "compare", kind: "compare", title: w.yearComparison.title, desc: w.yearComparison.desc, help: w.yearComparison.help,
      defaultSpan: 12, icon: "⚖️", variants: [{ key: "bar", label: v.bar }, { key: "line", label: v.line }] },
    { id: "weather-overlay", kind: "weather", title: w.weatherCorrelation.title, desc: w.weatherCorrelation.desc, help: w.weatherCorrelation.help,
      defaultSpan: 12, icon: "🌤️", variants: [{ key: "type", label: v.byType }, { key: "region", label: v.byRegion }] },
    { id: "weather-scatter", kind: "weather2", title: w.weatherScatter.title, desc: w.weatherScatter.desc, help: w.weatherScatter.help,
      defaultSpan: 6, icon: "🔵", variants: [{ key: "scatter", label: v.scatter }, { key: "overlay", label: v.overlay }] },
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
