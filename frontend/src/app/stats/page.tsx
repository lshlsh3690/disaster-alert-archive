"use client";

/**
 * stats/page.tsx — 재난 통계 페이지
 *
 * ── 개요 ──────────────────────────────────────────────────────────────────────
 * 재난문자 페이지(/alerts)에서 URL 파라미터로 넘어온 필터 조건을 받아
 * 다양한 차트 위젯으로 분석 결과를 보여주는 대시보드입니다.
 *
 * ── 데이터 흐름 ───────────────────────────────────────────────────────────────
 * 1. URL 파라미터 파싱
 *    useSearchParams()로 sido·sigungu·startDate·endDate·type·level·keyword·source를 읽어
 *    statsParams 객체로 통합합니다. 이 객체가 모든 React Query 훅의 공통 인자입니다.
 *
 * 2. 데이터 페칭 (React Query)
 *    - useAlertStats   : 유형별·경보단계별 집계 (총 건수, TypeStat[], LevelStat[])
 *    - useSidoStats    : 시·도별 건수 (RegionStat[])
 *    - useSigunguStats : 시·군·구별 건수, sido 필터가 있을 때만 enabled
 *    - useDailyStats   : 일별 건수 시계열 (DailyStat[])
 *    - useHourlyStats  : 요일×시간대 히트맵용 집계
 *    - useMonthlyTypeStats / useDailyTypeStats : 월별·일별 유형 누적 (stacked 위젯)
 *    - useDailyStats ×2 : 전년/금년 비교용, hasCompare 위젯이 있을 때만 enabled
 *    - useWeatherCorrelation / useWeatherByType / useWeatherByRegion :
 *        날씨·재난 상관 데이터, hasWeather 위젯이 있을 때만 enabled
 *    - useWeatherHourly* : 위 날씨 데이터의 시간별 버전, 기간 ≤7일일 때만 enabled
 *
 * 3. 데이터 가공 (useMemo)
 *    - typeStats  : 건수 내림차순 정렬, null 타입 제거
 *    - regionStats: sido 선택 시 시·군·구 데이터로 교체, 시·도 접두어 제거
 *    - periodDays : startDate~endDate 일수 계산 (기본 30일)
 *    - dailyAvg   : totalCount / periodDays
 *    - isSubMonth / isShortPeriod : 기간에 따라 차트 세분성(일별↔시간별) 자동 전환
 *
 * 4. 위젯 레이아웃 & 프리셋
 *    - 3개의 독립 프리셋을 localStorage에 저장 (stats-layout-1~3, stats-preset-active)
 *    - 각 프리셋은 WidgetItem[] (id, libId, span, variant)의 배열
 *    - LibItem(_constants.ts)가 위젯의 정적 정의, WidgetItem이 사용자 인스턴스
 *    - 위젯 추가 시 12컬럼 그리드에서 빈 칸을 먼저 채우는 bin-packing 삽입
 *
 * 5. 렌더링
 *    - KPI 4개 → 필터 배너 → 위젯 그리드 (12컬럼)
 *    - 각 WidgetCard는 lib.kind를 보고 WidgetContent 디스패처가 알맞은 차트를 선택
 *    - WidgetLibrary 드로어로 위젯 추가·삭제
 */

import { Suspense, useState, useEffect, useMemo, useCallback } from "react";
import Link from "next/link";
import { useSearchParams, useRouter, usePathname } from "next/navigation";
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors } from "@dnd-kit/core";
import type { DragEndEvent } from "@dnd-kit/core";
import { SortableContext, rectSortingStrategy, arrayMove, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useAlertStats, useSidoStats, useSigunguStats, useDailyStats, useHourlyStats, useMonthlyTypeStats, useDailyTypeStats, useWeatherCorrelation, useWeatherByType, useWeatherByRegion, useWeatherHourlyCorrelation, useWeatherHourlyByType, useWeatherHourlyByRegion, useSigungu } from "@/lib/queries/useAlerts";
import type { DailyStat, WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import { levelTextToCode } from "@/ui/level";
import { METROS } from "@/ui/metros";
import { DISASTER_TYPES } from "@/ui/disasterType";
import type { TypeStat, LevelStat, RegionStat, LibItem, WidgetItem } from "./_constants";
import { getWidgetLibrary, DEFAULT_LAYOUT } from "./_constants";
import { WidgetCard, WidgetContent } from "./_WidgetCard";
import { WidgetLibrary } from "./_WidgetLibrary";
import { KpiBox, FilterBanner } from "./_KpiCards";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import { formatMessage } from "@/utils/formatMessage";
import DatePicker from "@/components/form/DatePicker";
import "./stats.css";

const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };
type T = ReturnType<typeof useI18n>;

// ─── CSV 다운로드 ─────────────────────────────────────────────────────────────

function buildCsv(opts: {
  t: T;
  locale: string;
  filters: Record<string, string>;
  totalCount: number;
  dailyAvg: number;
  topType?: TypeStat;
  topRegion?: RegionStat;
  typeStats: TypeStat[];
  regionStats: RegionStat[];
  levelStats: LevelStat[];
  dailyStats: DailyStat[];
  quarterStats: { quarter: string; count: number }[];
  weatherStats: WeatherCorrelationStat[];
  weatherTypeStats: WeatherTypeStat[];
  weatherRegionStats: WeatherRegionStat[];
  weatherHourlyStats: WeatherCorrelationStat[];
  weatherHourlyTypeStats: WeatherTypeStat[];
  weatherHourlyRegionStats: WeatherRegionStat[];
  isShortPeriod: boolean;
}): string {
  const { t, locale, filters, totalCount, dailyAvg, topType, topRegion, typeStats, regionStats, levelStats, dailyStats, quarterStats,
    weatherStats, weatherTypeStats, weatherRegionStats,
    weatherHourlyStats, weatherHourlyTypeStats, weatherHourlyRegionStats, isShortPeriod } = opts;
  const csv = t.statsPage.csv;
  const translateType = (type: string) => t.disasterTypes[type as keyof typeof t.disasterTypes] ?? type;
  const translateRegion = (region: string) => t.metros[region as keyof typeof t.metros] ?? region;
  const rows: string[] = [];
  const row = (...cols: (string | number)[]) => rows.push(cols.map(c => `"${String(c).replace(/"/g, '""')}"`).join(","));
  const blank = () => rows.push("");
  const section = (title: string) => { blank(); row(title); };

  row(csv.title);
  row(`${csv.downloadedAt}: ${new Date().toLocaleString(locale)}`);

  section(`## ${csv.filterConditions}`);
  row(csv.item, csv.value);
  Object.entries(filters).forEach(([k, v]) => row(k, v || "-"));

  section(`## ${csv.kpiSummary}`);
  row(csv.item, csv.value);
  row(t.statsPage.totalAlerts, `${totalCount}${t.statsPage.countUnit}`);
  row(`${t.statsPage.dailyAverage} (${t.statsPage.recentThirtyDays})`, `${dailyAvg}${t.statsPage.countUnit}`);
  row(t.statsPage.topType, topType?.type ? translateType(topType.type) : "-");
  row(t.statsPage.topRegion, topRegion?.region ? translateRegion(topRegion.region) : "-");

  if (typeStats.length > 0) {
    section(`## ${csv.typeDistribution}`);
    row(t.statsPage.rank, t.statsPage.type, t.statsPage.count, t.statsPage.share);
    typeStats.forEach((d, i) =>
      row(i + 1, d.type ? translateType(d.type) : t.statsPage.other, d.count, `${Math.round((d.count / (totalCount || 1)) * 100)}%`)
    );
  }

  if (regionStats.length > 0) {
    section(`## ${csv.regionDistribution}`);
    row(t.statsPage.rank, t.statsPage.region, t.statsPage.count);
    regionStats.forEach((d, i) => row(i + 1, translateRegion(d.region), d.count));
  }

  if (levelStats.length > 0) {
    const levelTotal = levelStats.reduce((s, d) => s + d.count, 0) || 1;
    section(`## ${csv.levelDistribution}`);
    row(t.statsPage.level, t.statsPage.count, t.statsPage.share);
    levelStats.forEach(d =>
      row(d.level ? (t.statsPage.levels[d.level as keyof typeof t.statsPage.levels] ?? d.level) : t.statsPage.other, d.count, `${Math.round((d.count / levelTotal) * 100)}%`)
    );
  }

  if (quarterStats.length > 0) {
    const quarterTotal = quarterStats.reduce((s, d) => s + d.count, 0) || 1;
    section(`## ${t.statsPage.tableTabs.quarter}`);
    row(t.statsPage.quarter, t.statsPage.count, t.statsPage.share);
    quarterStats.forEach(d => row(d.quarter, d.count, `${Math.round((d.count / quarterTotal) * 100)}%`));
  }

  if (dailyStats.length > 0) {
    section(`## ${csv.dailyTrend}`);
    row(t.statsPage.date, t.statsPage.count);
    dailyStats.forEach(d => row(d.date, d.count));
  }

  const fmt = (v: number | null | undefined) => v == null ? "-" : String(v);
  const weatherCols = [t.statsPage.date, t.statsPage.totalAlerts, `${t.statsPage.averageTemperature}`, `min(℃)`, `max(℃)`, `precip(mm)`, `wind(m/s)`, t.statsPage.topType];
  const weatherTypeCols = [t.statsPage.date, t.statsPage.type, t.statsPage.count, t.statsPage.averageTemperature, `min(℃)`, `max(℃)`, `precip(mm)`];
  const weatherRegionCols = [t.statsPage.date, t.statsPage.region, t.statsPage.count, t.statsPage.averageTemperature, `min(℃)`, `max(℃)`, `precip(mm)`];

  if (weatherStats.length > 0) {
    section(`## ${t.statsPage.widgets.weatherCorrelation.title}`);
    row(...weatherCols);
    weatherStats.forEach(d =>
      row(d.date, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip), fmt(d.avgWindSpeed), d.primaryType ? translateType(d.primaryType) : "-")
    );
  }

  if (weatherTypeStats.length > 0) {
    section(`## ${t.statsPage.widgets.weatherCorrelation.title} · ${t.statsPage.variants.byType}`);
    row(...weatherTypeCols);
    weatherTypeStats.forEach(d =>
      row(d.date, d.type ? translateType(d.type) : t.statsPage.other, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  if (weatherRegionStats.length > 0) {
    section(`## ${t.statsPage.widgets.weatherCorrelation.title} · ${t.statsPage.variants.byRegion}`);
    row(...weatherRegionCols);
    weatherRegionStats.forEach(d =>
      row(d.date, translateRegion(d.region), d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  if (isShortPeriod && weatherHourlyStats.length > 0) {
    section(`## ${t.statsPage.widgets.weatherCorrelation.title} (${t.statsPage.variants.hour})`);
    row(...weatherCols);
    weatherHourlyStats.forEach(d =>
      row(d.date, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip), fmt(d.avgWindSpeed), d.primaryType ? translateType(d.primaryType) : "-")
    );
  }

  if (isShortPeriod && weatherHourlyTypeStats.length > 0) {
    section(`## ${t.statsPage.widgets.weatherCorrelation.title} · ${t.statsPage.variants.byType} (${t.statsPage.variants.hour})`);
    row(...weatherTypeCols);
    weatherHourlyTypeStats.forEach(d =>
      row(d.date, d.type ? translateType(d.type) : t.statsPage.other, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  if (isShortPeriod && weatherHourlyRegionStats.length > 0) {
    section(`## ${t.statsPage.widgets.weatherCorrelation.title} · ${t.statsPage.variants.byRegion} (${t.statsPage.variants.hour})`);
    row(...weatherRegionCols);
    weatherHourlyRegionStats.forEach(d =>
      row(d.date, translateRegion(d.region), d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  return rows.join("\n");
}

function downloadCsv(filename: string, csv: string) {
  const bom = "﻿";
  const blob = new Blob([bom + csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}


// ─── 정렬 가능한 위젯 카드 래퍼 ──────────────────────────────────────────────

function SortableWidgetCard({ id, span, children, ...cardProps }: {
  id: string;
  span: number;
  children: React.ReactNode;
} & Omit<React.ComponentProps<typeof WidgetCard>, "dragHandleListeners">) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  return (
    <div ref={setNodeRef}
      style={{
        gridColumn: `span ${span}`,
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
        zIndex: isDragging ? 10 : undefined,
        display: "flex",
        flexDirection: "column",
        minHeight: 240,
      }}
      {...attributes}>
      <WidgetCard {...cardProps} dragHandleListeners={listeners as Record<string, unknown>}>
        {children}
      </WidgetCard>
    </div>
  );
}

// ─── 메인 ────────────────────────────────────────────────────────────────────

export default function StatsPage() {
  const t = useI18n();
  return (
    <Suspense fallback={<main className="p-6">{t.statsPage.loading}</main>}>
      <StatsPageInner />
    </Suspense>
  );
}

function StatsPageInner() {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const widgetLibrary = useMemo(() => getWidgetLibrary(t), [t]);
  const searchParams = useSearchParams();
  const router = useRouter();
  // 현재 경로(/stats). router.push에 절대 경로를 넘기기 위함
  // (쿼리스트링만 넘기는 상대 push는 App Router에서 URL이 갱신되지 않아 API가 호출되지 않음)
  const pathname = usePathname();

  const sido      = searchParams.get("sido")      ?? undefined;
  const sigungu   = searchParams.get("sigungu")   ?? undefined;
  const startDate = searchParams.get("startDate") ?? undefined;
  const endDate   = searchParams.get("endDate")   ?? undefined;
  const type      = searchParams.get("type")      ?? undefined;
  const levelText = searchParams.get("levelText") ?? undefined;
  const keyword   = searchParams.get("keyword")   ?? undefined;
  const source    = searchParams.get("source")    ?? undefined;

  // ─── 필터 패널 ──────────────────────────────────────────────────────────────

  // 필터 패널 열림 상태
  const [filterOpen, setFilterOpen] = useState(false);
  // 패널 내부에서 편집 중인 로컬 필터 값 (적용 버튼을 눌러야 URL에 반영됨)
  const [localFilter, setLocalFilter] = useState({
    sido: sido ?? "", sigungu: sigungu ?? "",
    startDate: startDate ?? "", endDate: endDate ?? "",
    type: type ?? "", levelText: levelText ?? "",
  });

  // 필터 패널을 열 때 현재 URL 파라미터로 로컬 상태 동기화
  const openFilter = () => {
    if (filterOpen) { setFilterOpen(false); return; }
    setLocalFilter({
      sido: sido ?? "", sigungu: sigungu ?? "",
      startDate: startDate ?? "", endDate: endDate ?? "",
      type: type ?? "", levelText: levelText ?? "",
    });
    setFilterOpen(true);
  };

  // 시·도 변경 시 시·군·구 초기화
  const setLocalSido = (v: string) =>
    setLocalFilter(f => ({ ...f, sido: v, sigungu: "" }));

  // 빠른 날짜 선택: 오늘 기준 N일 전 ~ 오늘
  const setQuickDate = (days: number | "year") => {
    const today = new Date();
    const fmt = (d: Date) => d.toISOString().slice(0, 10);
    if (days === "year") {
      setLocalFilter(f => ({ ...f, startDate: `${today.getFullYear()}-01-01`, endDate: fmt(today) }));
    } else {
      const from = new Date(today);
      from.setDate(from.getDate() - (days - 1));
      setLocalFilter(f => ({ ...f, startDate: fmt(from), endDate: fmt(today) }));
    }
  };

  // 프리셋에 저장하는 필터 형태 (localFilter와 동일한 편집 가능 필드)
  type StatsFilter = { sido: string; sigungu: string; startDate: string; endDate: string; type: string; levelText: string };

  // 필터 객체를 URL 파라미터로 push (keyword·source는 현재 값 유지)
  // → useSearchParams가 자동 업데이트되어 모든 차트에 반영됨
  const pushFilter = useCallback((f: StatsFilter) => {
    const qs = new URLSearchParams();
    if (f.sido)      qs.set("sido",      f.sido);
    if (f.sigungu)   qs.set("sigungu",   f.sigungu);
    if (f.startDate) qs.set("startDate", f.startDate);
    if (f.endDate)   qs.set("endDate",   f.endDate);
    if (f.type)      qs.set("type",      f.type);
    if (f.levelText) qs.set("levelText", f.levelText);
    if (keyword)     qs.set("keyword",   keyword);
    if (source)      qs.set("source",    source);
    const query = qs.toString();
    router.push(query ? `${pathname}?${query}` : pathname);
  }, [router, pathname, keyword, source]);

  // 적용 버튼: 현재 필터를 활성 프리셋에 저장하고 URL에 반영
  const applyFilter = () => {
    try { localStorage.setItem(`stats-filter-${activePreset}`, JSON.stringify(localFilter)); } catch {}
    pushFilter(localFilter);
    setFilterOpen(false);
  };

  // 시·도가 선택됐을 때 시·군·구 목록 로드
  const { data: sigunguList } = useSigungu(localFilter.sido || undefined);

  // 현재 URL에 적용된 필터가 하나라도 있는지 (필터 버튼 강조용)
  const hasActiveFilter = !!(sido || sigungu || startDate || endDate || type || levelText);

  // URL 파라미터를 모든 React Query 훅의 공통 인자로 통합한 객체
  const statsParams = useMemo(() => {
    const levelCode = levelTextToCode(levelText);
    const region = sido && sigungu ? `${sido} ${sigungu}` : sido;
    return { region, startDate, endDate, type, level: levelCode, keyword, source: source as "ALL" | "OFFICIAL" | "USER" | undefined };
  }, [sido, sigungu, startDate, endDate, type, levelText, keyword, source]);

  // 차트 클릭으로 설정되는 인터랙티브 유형 필터 (URL 필터와 별개로 동작)
  const [crossFilter, setCrossFilter] = useState<{ type?: string }>({});
  const onTypeClick = useCallback((t: string) =>
    setCrossFilter(f => f.type === t ? {} : { type: t }), []);

  // crossFilter가 있으면 statsParams의 type을 덮어씀 → 모든 차트에 반영
  const effectiveParams = useMemo(() => ({
    ...statsParams,
    ...(crossFilter.type ? { type: crossFilter.type } : {}),
  }), [statsParams, crossFilter]);

  const { data: stats,        isLoading: loadingStats }     = useAlertStats(effectiveParams);
  const { data: sidoStats,    isLoading: loadingSido }      = useSidoStats(effectiveParams);
  const { data: sigunguStats, isLoading: loadingSigungu }   = useSigunguStats({ ...effectiveParams, region: sido }, !!sido);

  // 건수 내림차순 정렬, null 타입 제거한 유형별 집계
  const typeStats: TypeStat[] = useMemo(() =>
    (stats?.typeStats ?? []).filter(d => d.type).sort((a, b) => b.count - a.count),
    [stats]
  );

  // sido 선택 시 시·군·구 데이터로 교체, 시·도 접두어 제거
  const regionStats: RegionStat[] = useMemo(() => {
    if (sido) {
      if (!sigunguStats || sigunguStats.length === 0) return [];
      const prefix = sido + " ";
      return sigunguStats.map(d => ({ ...d, region: d.region.startsWith(prefix) ? d.region.slice(prefix.length) : d.region }));
    }
    return sidoStats ?? [];
  }, [sido, sidoStats, sigunguStats]);

  // 경보 단계별 집계 (가공 없이 API 결과 그대로)
  const levelStats: LevelStat[] = stats?.levelStats ?? [];

  // 현재 활성화된 프리셋 번호 (1~3)
  const [activePreset, setActivePreset] = useState<1 | 2 | 3>(1);
  // 현재 프리셋의 위젯 배치 배열 (localStorage에서 복원)
  const [layout, setLayout] = useState<WidgetItem[]>(DEFAULT_LAYOUT);
  // 프리셋별 사용자 지정 이름 (기본값 "프리셋 1~3")
  const [presetNames, setPresetNames] = useState<Record<1 | 2 | 3, string>>({
    1: formatMessage(t.statsPage.presetName, { number: 1 }),
    2: formatMessage(t.statsPage.presetName, { number: 2 }),
    3: formatMessage(t.statsPage.presetName, { number: 3 }),
  });
  // 현재 이름 편집 중인 프리셋 번호 (null이면 편집 안 함)
  const [editingPreset, setEditingPreset] = useState<1 | 2 | 3 | null>(null);
  const [editingName, setEditingName] = useState("");
  useEffect(() => {
    try {
      const p = (Number(localStorage.getItem("stats-preset-active")) || 1) as 1 | 2 | 3;
      const saved = localStorage.getItem(`stats-layout-${p}`);
      const savedNames = localStorage.getItem("stats-preset-names");
      setActivePreset(p);
      if (saved) setLayout(JSON.parse(saved) as WidgetItem[]);
      if (savedNames) setPresetNames(JSON.parse(savedNames));
      // URL에 필터가 없을 때만 저장된 필터 복원 (URL 우선)
      const hasUrlFilter = ["sido", "sigungu", "startDate", "endDate", "type", "levelText"]
        .some(k => new URLSearchParams(window.location.search).get(k));
      if (!hasUrlFilter) {
        const savedFilter = localStorage.getItem(`stats-filter-${p}`);
        if (savedFilter) {
          const f = JSON.parse(savedFilter) as { sido: string; sigungu: string; startDate: string; endDate: string; type: string; levelText: string };
          const qs = new URLSearchParams();
          if (f.sido)      qs.set("sido",      f.sido);
          if (f.sigungu)   qs.set("sigungu",   f.sigungu);
          if (f.startDate) qs.set("startDate", f.startDate);
          if (f.endDate)   qs.set("endDate",   f.endDate);
          if (f.type)      qs.set("type",      f.type);
          if (f.levelText) qs.set("levelText", f.levelText);
          router.replace(`?${qs.toString()}`);
        }
      }
    } catch {}
  }, [router]);

  const commitPresetName = useCallback(() => {
    if (editingPreset === null) return;
    const name = editingName.trim() || formatMessage(t.statsPage.presetName, { number: editingPreset });
    setPresetNames(prev => {
      const next = { ...prev, [editingPreset]: name };
      try { localStorage.setItem("stats-preset-names", JSON.stringify(next)); } catch {}
      return next;
    });
    setEditingPreset(null);
  }, [editingPreset, editingName, t]);

  const switchPreset = useCallback((p: 1 | 2 | 3) => {
    if (p === activePreset) return;
    try {
      // 1) 떠나는 프리셋에 현재 필터 조합을 저장 (위젯 배치처럼 자동 보존)
      const currentFilter: StatsFilter = {
        sido: sido ?? "", sigungu: sigungu ?? "",
        startDate: startDate ?? "", endDate: endDate ?? "",
        type: type ?? "", levelText: levelText ?? "",
      };
      localStorage.setItem(`stats-filter-${activePreset}`, JSON.stringify(currentFilter));

      // 2) 새 프리셋으로 전환 + 배치 복원
      setActivePreset(p);
      localStorage.setItem("stats-preset-active", String(p));
      const saved = localStorage.getItem(`stats-layout-${p}`);
      setLayout(saved ? (JSON.parse(saved) as WidgetItem[]) : DEFAULT_LAYOUT);

      // 3) 새 프리셋의 필터 복원 (저장된 게 없으면 빈 필터로 초기화 → 다른 프리셋 필터가 새어 나오지 않음)
      const savedFilter = localStorage.getItem(`stats-filter-${p}`);
      const emptyFilter: StatsFilter = { sido: "", sigungu: "", startDate: "", endDate: "", type: "", levelText: "" };
      pushFilter(savedFilter ? (JSON.parse(savedFilter) as StatsFilter) : emptyFilter);
    } catch {}
  }, [activePreset, sido, sigungu, startDate, endDate, type, levelText, pushFilter]);

  // 비교 위젯 존재 여부 → 전년/금년 데이터 페칭 여부 결정 (enabled 플래그)
  const hasCompare = layout.some(w => w.libId === "compare");
  // 날씨 위젯 존재 여부 → 날씨 데이터 페칭 여부 결정 (enabled 플래그)
  const hasWeather = layout.some(w => w.libId === "weather-overlay" || w.libId === "weather-scatter");

  const { data: dailyStatsRaw,    isLoading: loadingDaily }       = useDailyStats(effectiveParams);
  const { data: hourlyStatsRaw,   isLoading: loadingHourly }      = useHourlyStats(effectiveParams);
  const { data: monthlyTypeRaw,   isLoading: loadingMonthlyType } = useMonthlyTypeStats(effectiveParams);
  // API raw 데이터의 null-coalesce (로딩 중엔 빈 배열)
  const dailyStats       = useMemo(() => dailyStatsRaw   ?? [], [dailyStatsRaw]);
  const hourlyStats      = hourlyStatsRaw  ?? [];
  const monthlyTypeStats = monthlyTypeRaw  ?? [];

  // 올해 연도 (전년 비교 차트에서 currentYear-1 계산에 사용)
  const currentYear = useMemo(() => new Date().getFullYear(), []);
  // 전년/금년 비교 페칭 시 날짜 범위를 뺀 공통 파라미터 베이스
  const compareBase = useMemo(() => ({ ...effectiveParams, startDate: undefined, endDate: undefined }), [effectiveParams]);
  const { data: thisYearRaw, isLoading: loadingThisYear } = useDailyStats({ ...compareBase, startDate: `${currentYear}-01-01`, endDate: `${currentYear}-12-31` }, hasCompare);
  const { data: lastYearRaw, isLoading: loadingLastYear } = useDailyStats({ ...compareBase, startDate: `${currentYear - 1}-01-01`, endDate: `${currentYear - 1}-12-31` }, hasCompare);
  // 전년/금년 일별 데이터 null-coalesce
  const thisYearData: DailyStat[] = thisYearRaw ?? [];
  const lastYearData: DailyStat[] = lastYearRaw ?? [];

  // KPI 카드에 표시하는 최다 유형 (typeStats 내림차순 첫 번째)
  const topType    = typeStats[0];
  // KPI 카드에 표시하는 최다 지역 (regionStats 내림차순 첫 번째)
  const topRegion  = regionStats[0];
  // KPI 카드·비율 계산용 전체 건수
  const totalCount = stats?.totalCount ?? 0;
  // 필터 기간 일수 (startDate~endDate 없으면 기본 30일)
  const periodDays = useMemo(() => {
    if (startDate && endDate) {
      const diff = (new Date(endDate).getTime() - new Date(startDate).getTime()) / 86_400_000 + 1;
      return Math.max(1, Math.round(diff));
    }
    return 30;
  }, [startDate, endDate]);
  // 전체 건수를 기간 일수로 나눈 일 평균
  const dailyAvg = Math.round(totalCount / periodDays);

  const { data: weatherRaw, isLoading: loadingWeather } = useWeatherCorrelation(effectiveParams, hasWeather);
  // 일별 날씨·재난 상관관계 데이터 null-coalesce
  const weatherStats = weatherRaw ?? [];

  // sido 선택 여부에 따라 시군구/시도 레벨 결정 (WeatherByRegion API 파라미터)
  const regionLevel = sido ? "sigungu" : "sido";
  // 날씨 지역 차트 제목용 레이블
  const regionLabel = sido ? t.statsPage.byDistrict : t.statsPage.byProvince;
  const { data: weatherTypeRaw,   isLoading: loadingWeatherType   } = useWeatherByType(effectiveParams, hasWeather);
  const { data: weatherRegionRaw, isLoading: loadingWeatherRegion } = useWeatherByRegion(effectiveParams, regionLevel, hasWeather);
  // 재난 유형별/지역별 날씨 데이터 null-coalesce
  const weatherTypeStats   = weatherTypeRaw   ?? [];
  const weatherRegionStats = weatherRegionRaw ?? [];

  // 기간 < 30일이면 true → 월별 대신 일별 누적 차트로 자동 전환
  const isSubMonth    = periodDays < 30;
  // 기간 ≤ 7일이면 true → 시간별 날씨 데이터 페칭 활성화, 일별↔시간별 토글 표시
  const isShortPeriod = periodDays <= 7;
  const { data: dailyTypeRaw, isLoading: loadingDailyType } = useDailyTypeStats(effectiveParams, isSubMonth);
  // isSubMonth일 때 stacked 위젯에서 월별 대신 사용하는 일별 유형 누적 데이터
  const dailyTypeStats = dailyTypeRaw ?? [];
  const { data: weatherHourlyRaw,       isLoading: loadingWeatherHourly       } = useWeatherHourlyCorrelation(effectiveParams, isShortPeriod && hasWeather);
  const { data: weatherHourlyTypeRaw,   isLoading: loadingWeatherHourlyType   } = useWeatherHourlyByType(effectiveParams, isShortPeriod && hasWeather);
  const { data: weatherHourlyRegionRaw, isLoading: loadingWeatherHourlyRegion } = useWeatherHourlyByRegion(effectiveParams, regionLevel, isShortPeriod && hasWeather);
  // 시간별 날씨 데이터 null-coalesce (isShortPeriod일 때만 실제 데이터 채워짐)
  const weatherHourlyStats       = weatherHourlyRaw       ?? [];
  const weatherHourlyTypeStats   = weatherHourlyTypeRaw   ?? [];
  const weatherHourlyRegionStats = weatherHourlyRegionRaw ?? [];

  // 데이터 테이블 열림 상태 및 현재 탭
  const [tableOpen, setTableOpen] = useState(false);
  const [tableTab, setTableTab] = useState<"type" | "region" | "level" | "daily" | "quarter">("type");
  // dailyStats를 분기별로 집계 (YYYY-Qn 키 순서 보장)
  const quarterStats = useMemo(() => {
    const map = new Map<string, number>();
    dailyStats.forEach(d => {
      const [year, month] = d.date.split("-").map(Number);
      const q = Math.ceil(month / 3);
      const key = `${year}-Q${q}`;
      map.set(key, (map.get(key) ?? 0) + d.count);
    });
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([quarter, count]) => ({ quarter, count }));
  }, [dailyStats]);

  // 위젯 드로어 열림 상태
  const [drawerOpen, setDrawerOpen] = useState(false);
  // 방금 추가된 위젯의 id (1.6초 후 null → glow 애니메이션 종료)
  const [newWidgetId, setNewWidgetId] = useState<string | null>(null);

  // 레이아웃을 변경하고 현재 프리셋의 localStorage 항목을 동시에 갱신하는 공통 업데이터
  const updateLayout = useCallback((updater: (l: WidgetItem[]) => WidgetItem[]) => {
    setLayout(l => {
      const next = updater(l);
      try { localStorage.setItem(`stats-layout-${activePreset}`, JSON.stringify(next)); } catch {}
      return next;
    });
  }, [activePreset]);

  // 8px 이상 움직여야 드래그 시작 (클릭과 구분)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }));
  const handleDragEnd = useCallback(({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    updateLayout(l => {
      const oldIdx = l.findIndex(w => w.id === active.id);
      const newIdx = l.findIndex(w => w.id === over.id);
      return arrayMove(l, oldIdx, newIdx);
    });
  }, [updateLayout]);

  const handleRemove        = useCallback((id: string) => updateLayout(l => l.filter(w => w.id !== id)), [updateLayout]);
  const handleVariantChange = useCallback((id: string, variant: string) => updateLayout(l => l.map(w => w.id === id ? { ...w, variant } : w)), [updateLayout]);
  const handleAdd = useCallback((item: LibItem) => {
    // 타임스탬프 기반 고유 id → 같은 위젯 종류를 여러 번 추가해도 충돌 없음
    const newId = `w${Date.now()}`;
    updateLayout(l => {
      // 12컬럼 그리드에서 행별로 시뮬레이션해 빈 공간이 있는 첫 번째 행 뒤에 삽입
      let col = 0;
      let insertIdx = l.length;
      for (let i = 0; i < l.length; i++) {
        if (col + l[i].span > 12) col = 0;
        col += l[i].span;
        if (col === 12) {
          // 이 행이 꽉 찼으므로 다음 행 시작
          col = 0;
        } else if (12 - col >= item.defaultSpan) {
          // 이 행에 새 위젯이 들어갈 공간이 있음 → 행의 마지막 위젯 다음에 삽입
          insertIdx = i + 1;
          break;
        }
      }
      const next = [...l];
      next.splice(insertIdx, 0, { id: newId, libId: item.id, span: item.defaultSpan });
      return next;
    });
    setNewWidgetId(newId);
    setTimeout(() => setNewWidgetId(null), 1600);
  }, [updateLayout]);

  return (
    <main className="p-3 sm:p-6 space-y-4 relative">
      {/* 페이지 헤더 */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold">{t.statsPage.title}</h1>
          <p className="text-sm text-[var(--text-muted)]">{t.statsPage.description}</p>
        </div>
        <div className="flex gap-2 shrink-0 items-center flex-wrap justify-end">
          {/* 필터 토글 버튼 */}
          <button onClick={openFilter}
            className={`px-3 py-2 text-sm font-semibold rounded-lg border transition-colors flex items-center gap-1 ${hasActiveFilter ? "border-[var(--blue)] bg-[var(--blue-soft)] text-[var(--blue)]" : "border-[var(--line)] bg-[var(--surface)] text-[var(--text-body)] hover:border-blue-300 hover:text-[var(--blue)]"}`}>
            🔍 {t.statsPage.filter}{hasActiveFilter ? " ●" : ""}
          </button>
          {/* 프리셋 탭 */}
          <div className="flex border border-[var(--line)] rounded-lg overflow-hidden">
            {([1, 2, 3] as const).map(p => (
              <div key={p}
                className={`flex items-center gap-1.5 px-3 py-2 text-sm font-semibold transition-colors cursor-pointer
                  ${activePreset === p ? "bg-[var(--blue)] text-white" : "text-[var(--text-muted)] hover:bg-[var(--canvas)]"}`}
                onClick={() => switchPreset(p)}>
                {editingPreset === p ? (
                  <input
                    autoFocus
                    value={editingName}
                    onChange={e => setEditingName(e.target.value)}
                    onBlur={commitPresetName}
                    onKeyDown={e => { if (e.key === "Enter") commitPresetName(); if (e.key === "Escape") setEditingPreset(null); }}
                    onClick={e => e.stopPropagation()}
                    className="bg-transparent outline-none w-20 text-white placeholder-blue-200"
                    maxLength={10}
                  />
                ) : (
                  <>
                    {presetNames[p]}
                    {activePreset === p && (
                      <span
                        onClick={e => { e.stopPropagation(); setEditingPreset(p); setEditingName(presetNames[p]); }}
                        className="text-blue-200 hover:text-white text-xs leading-none"
                        title={t.statsPage.editPresetName}>
                        ✏️
                      </span>
                    )}
                  </>
                )}
              </div>
            ))}
          </div>
          <button onClick={() => {
            const filters: Record<string, string> = {
              [t.statsPage.province]: sido ?? t.statsPage.all,
              [t.statsPage.district]: sigungu ?? t.statsPage.all,
              [t.statsPage.startDate]: startDate ?? t.statsPage.all,
              [t.statsPage.endDate]: endDate ?? t.statsPage.all,
              [t.statsPage.disasterType]: type ?? t.statsPage.all,
              [t.statsPage.alertLevel]: levelText ?? t.statsPage.all,
              키워드: keyword ?? "-",
              출처: source ?? "ALL",
            };
            const csv = buildCsv({ t, locale, filters, totalCount, dailyAvg, topType, topRegion, typeStats, regionStats, levelStats, dailyStats, quarterStats,
              weatherStats, weatherTypeStats, weatherRegionStats,
              weatherHourlyStats, weatherHourlyTypeStats, weatherHourlyRegionStats, isShortPeriod });
            downloadCsv(`${t.statsPage.csv.fileName}_${new Date().toISOString().slice(0, 10)}.csv`, csv);
          }} className="px-3 py-2 text-sm font-semibold rounded-lg border border-[var(--line)] bg-[var(--surface)] text-[var(--text-body)] hover:border-green-400 hover:text-green-600 transition-colors flex items-center gap-1">
            ⬇ {t.statsPage.exportCsv}
          </button>
          <button onClick={() => setDrawerOpen(true)}
            className="px-3 py-2 text-sm font-semibold rounded-lg bg-[var(--blue)] text-white flex items-center gap-1">
            <span className="text-base leading-none">+</span> {t.statsPage.manageWidgets}
          </button>
        </div>
      </div>

      {/* 접히는 필터 패널 */}
      {filterOpen && (
        <div className="bg-[var(--surface)] border border-[var(--line)] rounded-xl shadow-sm px-4 py-4 space-y-3">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {/* 시·도 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-[var(--text-muted)]">{t.statsPage.province}</label>
              <select value={localFilter.sido} onChange={e => setLocalSido(e.target.value)}
                className="border border-[var(--line)] rounded-lg px-2 py-1.5 text-sm text-[var(--ink)] focus:outline-none focus:border-[var(--blue)]">
                <option value="">{t.statsPage.all}</option>
                {METROS.map(m => <option key={m} value={m}>{t.metros[m as keyof typeof t.metros] ?? m}</option>)}
              </select>
            </div>
            {/* 시·군·구 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-[var(--text-muted)]">{t.statsPage.district}</label>
              <select value={localFilter.sigungu} onChange={e => setLocalFilter(f => ({ ...f, sigungu: e.target.value }))}
                disabled={!localFilter.sido}
                className="border border-[var(--line)] rounded-lg px-2 py-1.5 text-sm text-[var(--ink)] focus:outline-none focus:border-[var(--blue)] disabled:bg-[var(--canvas)] disabled:text-[var(--text-subtle)]">
                <option value="">{t.statsPage.all}</option>
                {sigunguList?.filter(s => s.name !== "전체").map(s => (
                  <option key={s.code} value={s.name}>{s.translatedName ?? s.name}</option>
                ))}
              </select>
            </div>
            {/* 재난 유형 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-[var(--text-muted)]">{t.statsPage.disasterType}</label>
              <select value={localFilter.type} onChange={e => setLocalFilter(f => ({ ...f, type: e.target.value }))}
                className="border border-[var(--line)] rounded-lg px-2 py-1.5 text-sm text-[var(--ink)] focus:outline-none focus:border-[var(--blue)]">
                <option value="">{t.statsPage.all}</option>
                {DISASTER_TYPES.map(dt => <option key={dt} value={dt}>{t.disasterTypes[dt as keyof typeof t.disasterTypes] ?? dt}</option>)}
              </select>
            </div>
            {/* 경보 단계 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-[var(--text-muted)]">{t.statsPage.alertLevel}</label>
              <select value={localFilter.levelText} onChange={e => setLocalFilter(f => ({ ...f, levelText: e.target.value }))}
                className="border border-[var(--line)] rounded-lg px-2 py-1.5 text-sm text-[var(--ink)] focus:outline-none focus:border-[var(--blue)]">
                <option value="">{t.statsPage.all}</option>
                <option value="안전안내문자">{t.statsPage.levels.LEVEL_1}</option>
                <option value="긴급재난문자">{t.statsPage.levels.LEVEL_2}</option>
                <option value="위급재난문자">{t.statsPage.levels.LEVEL_3}</option>
              </select>
            </div>
          </div>

          {/* 기간 선택 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold text-[var(--text-muted)]">{t.statsPage.period}</label>
            <div className="flex flex-wrap items-center gap-2">
              <DatePicker value={localFilter.startDate} onChange={v => setLocalFilter(f => ({ ...f, startDate: v }))} locale={locale} clearLabel={t.datePicker.clear} prevMonthLabel={t.datePicker.prevMonth} nextMonthLabel={t.datePicker.nextMonth} className="border border-[var(--line)] rounded-lg px-2 py-1.5 text-sm text-[var(--ink)] focus:outline-none focus:border-[var(--blue)]" />
              <span className="text-[var(--text-subtle)] text-sm">~</span>
              <DatePicker value={localFilter.endDate} onChange={v => setLocalFilter(f => ({ ...f, endDate: v }))} locale={locale} clearLabel={t.datePicker.clear} prevMonthLabel={t.datePicker.prevMonth} nextMonthLabel={t.datePicker.nextMonth} className="border border-[var(--line)] rounded-lg px-2 py-1.5 text-sm text-[var(--ink)] focus:outline-none focus:border-[var(--blue)]" />
              <div className="flex gap-1">
                {([7, 30, 90] as const).map(d => (
                  <button key={d} onClick={() => setQuickDate(d)}
                    className="px-2.5 py-1.5 text-xs font-semibold border border-[var(--line)] rounded-lg text-[var(--text-body)] hover:border-[var(--blue)] hover:text-[var(--blue)] transition-colors">
                    {formatMessage(t.statsPage.lastDays, { days: d })}
                  </button>
                ))}
                <button onClick={() => setQuickDate("year")}
                  className="px-2.5 py-1.5 text-xs font-semibold border border-[var(--line)] rounded-lg text-[var(--text-body)] hover:border-[var(--blue)] hover:text-[var(--blue)] transition-colors">
                  {t.statsPage.thisYear}
                </button>
              </div>
            </div>
          </div>

          {/* 액션 버튼 */}
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => setFilterOpen(false)}
              className="px-4 py-1.5 text-sm font-semibold border border-[var(--line)] rounded-lg text-[var(--text-body)] hover:bg-[var(--canvas)] transition-colors">
              {t.statsPage.cancel}
            </button>
            <button onClick={() => {
              setLocalFilter({ sido: "", sigungu: "", startDate: "", endDate: "", type: "", levelText: "" });
            }}
              className="px-4 py-1.5 text-sm font-semibold border border-[var(--line)] rounded-lg text-[var(--text-muted)] hover:bg-[var(--canvas)] transition-colors">
              {t.statsPage.reset}
            </button>
            <button onClick={applyFilter}
              className="px-4 py-1.5 text-sm font-semibold rounded-lg bg-[var(--blue)] text-white hover:brightness-95 transition-colors">
              {t.statsPage.apply}
            </button>
          </div>
        </div>
      )}

      {/* 필터 배너 */}
      <FilterBanner sido={sido} sigungu={sigungu} startDate={startDate} endDate={endDate}
        type={type} levelText={levelText} keyword={keyword} source={source} onFilterOpen={openFilter} />

      {/* KPI */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <KpiBox variant="coral" icon="📨" label={t.statsPage.totalAlerts} value={`${totalCount.toLocaleString(locale)}${t.statsPage.countUnit}`} sub={t.statsPage.filteredResult} />
        <KpiBox variant="blue" icon="📅" label={t.statsPage.dailyAverage} value={`${dailyAvg}${t.statsPage.countUnit}`}
          sub={startDate && endDate ? formatMessage(t.statsPage.dayBasis, { days: periodDays }) : t.statsPage.recentThirtyDays} />
        <KpiBox variant="purple" icon="🌧" label={t.statsPage.topType} value={topType?.type ? (t.disasterTypes[topType.type as keyof typeof t.disasterTypes] ?? topType.type) : "-"}
          sub={topType ? `${topType.count.toLocaleString(locale)}${t.statsPage.countUnit} · ${Math.round((topType.count / (totalCount || 1)) * 100)}%` : "-"} />
        <KpiBox variant="green" icon="📍" label={t.statsPage.topRegion} value={topRegion?.region ? (t.metros[topRegion.region as keyof typeof t.metros] ?? topRegion.region) : "-"}
          sub={topRegion ? `${topRegion.count.toLocaleString(locale)}${t.statsPage.countUnit}` : "-"} />
      </div>

      {/* 데이터 요약 테이블: 접기/펼치기 */}
      <div className="bg-[var(--surface)] border border-[var(--line)] rounded-xl shadow-sm overflow-hidden">
        <button
          onClick={() => setTableOpen(o => !o)}
          className="w-full flex items-center justify-between px-4 py-3 text-sm font-semibold text-[var(--text-body)] hover:bg-[var(--canvas)] transition-colors">
          <span>📋 {t.statsPage.dataSummary}</span>
          <span className="text-[var(--text-subtle)] text-xs">{tableOpen ? `▲ ${t.statsPage.collapse}` : `▼ ${t.statsPage.expand}`}</span>
        </button>

        {tableOpen && (
          <div className="border-t border-[var(--line)]">
            {/* 탭 */}
            <div className="flex border-b border-[var(--line)] px-4 gap-4">
              {([ ["type", t.statsPage.tableTabs.type], ["region", t.statsPage.tableTabs.region], ["level", t.statsPage.tableTabs.level], ["quarter", t.statsPage.tableTabs.quarter], ["daily", t.statsPage.tableTabs.daily] ] as const).map(([key, label]) => (
                <button key={key} onClick={() => setTableTab(key)}
                  className={`py-2 text-xs font-semibold border-b-2 transition-colors ${tableTab === key ? "border-[var(--blue)] text-[var(--blue)]" : "border-transparent text-[var(--text-subtle)] hover:text-[var(--text-body)]"}`}>
                  {label}
                </button>
              ))}
            </div>

            <div className="overflow-x-auto">
              {tableTab === "type" && (
                <table className="w-full text-xs">
                  <thead className="bg-[var(--canvas)]"><tr>
                    <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)] w-8">{t.statsPage.rank}</th>
                    <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)]">{t.statsPage.type}</th>
                    <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.count}</th>
                    <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.share}</th>
                  </tr></thead>
                  <tbody className="divide-y divide-gray-50">
                    {typeStats.length === 0
                      ? <tr><td colSpan={4} className="px-4 py-6 text-center text-[var(--text-subtle)]">{t.statsPage.noData}</td></tr>
                      : typeStats.map((d, i) => (
                        <tr key={i} className="hover:bg-[var(--canvas)]">
                          <td className="px-4 py-2 text-[var(--text-subtle)]">{i + 1}</td>
                          <td className="px-4 py-2 text-[var(--text-body)]">{t.disasterTypes[d.type as keyof typeof t.disasterTypes] ?? d.type ?? t.statsPage.other}</td>
                          <td className="px-4 py-2 text-right font-semibold text-[var(--ink)]">{d.count.toLocaleString(locale)}</td>
                          <td className="px-4 py-2 text-right text-[var(--text-muted)]">{Math.round((d.count / (totalCount || 1)) * 100)}%</td>
                        </tr>
                    ))}
                  </tbody>
                </table>
              )}

              {tableTab === "region" && (
                <table className="w-full text-xs">
                  <thead className="bg-[var(--canvas)]"><tr>
                    <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)] w-8">{t.statsPage.rank}</th>
                    <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)]">{t.statsPage.region}</th>
                    <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.count}</th>
                    <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.share}</th>
                  </tr></thead>
                  <tbody className="divide-y divide-gray-50">
                    {regionStats.length === 0
                      ? <tr><td colSpan={4} className="px-4 py-6 text-center text-[var(--text-subtle)]">{t.statsPage.noData}</td></tr>
                      : regionStats.map((d, i) => {
                        const regionTotal = regionStats.reduce((s, r) => s + r.count, 0) || 1;
                        return (
                          <tr key={i} className="hover:bg-[var(--canvas)]">
                            <td className="px-4 py-2 text-[var(--text-subtle)]">{i + 1}</td>
                            <td className="px-4 py-2 text-[var(--text-body)]">{t.metros[d.region as keyof typeof t.metros] ?? d.region}</td>
                            <td className="px-4 py-2 text-right font-semibold text-[var(--ink)]">{d.count.toLocaleString(locale)}</td>
                            <td className="px-4 py-2 text-right text-[var(--text-muted)]">{Math.round((d.count / regionTotal) * 100)}%</td>
                          </tr>
                        );
                      })}
                  </tbody>
                </table>
              )}

              {tableTab === "level" && (() => {
                const levelTotal = levelStats.reduce((s, d) => s + d.count, 0) || 1;
                return (
                  <table className="w-full text-xs">
                    <thead className="bg-[var(--canvas)]"><tr>
                      <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)]">{t.statsPage.tableTabs.level}</th>
                      <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.count}</th>
                      <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.share}</th>
                    </tr></thead>
                    <tbody className="divide-y divide-gray-50">
                      {levelStats.length === 0
                        ? <tr><td colSpan={3} className="px-4 py-6 text-center text-[var(--text-subtle)]">{t.statsPage.noData}</td></tr>
                        : levelStats.map((d, i) => (
                          <tr key={i} className="hover:bg-[var(--canvas)]">
                            <td className="px-4 py-2 text-[var(--text-body)]">{d.level ? (t.statsPage.levels[d.level as keyof typeof t.statsPage.levels] ?? d.level) : t.statsPage.other}</td>
                            <td className="px-4 py-2 text-right font-semibold text-[var(--ink)]">{d.count.toLocaleString(locale)}</td>
                            <td className="px-4 py-2 text-right text-[var(--text-muted)]">{Math.round((d.count / levelTotal) * 100)}%</td>
                          </tr>
                      ))}
                    </tbody>
                  </table>
                );
              })()}

              {tableTab === "quarter" && (() => {
                const quarterTotal = quarterStats.reduce((s, d) => s + d.count, 0) || 1;
                return (
                  <table className="w-full text-xs">
                    <thead className="bg-[var(--canvas)]"><tr>
                      <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)]">{t.statsPage.quarter}</th>
                      <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.count}</th>
                      <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.share}</th>
                    </tr></thead>
                    <tbody className="divide-y divide-gray-50">
                      {quarterStats.length === 0
                        ? <tr><td colSpan={3} className="px-4 py-6 text-center text-[var(--text-subtle)]">{t.statsPage.noData}</td></tr>
                        : quarterStats.map((d, i) => (
                          <tr key={i} className="hover:bg-[var(--canvas)]">
                            <td className="px-4 py-2 text-[var(--text-body)]">{d.quarter}</td>
                            <td className="px-4 py-2 text-right font-semibold text-[var(--ink)]">{d.count.toLocaleString(locale)}</td>
                            <td className="px-4 py-2 text-right text-[var(--text-muted)]">{Math.round((d.count / quarterTotal) * 100)}%</td>
                          </tr>
                      ))}
                    </tbody>
                  </table>
                );
              })()}

              {tableTab === "daily" && (
                <table className="w-full text-xs">
                  <thead className="bg-[var(--canvas)]"><tr>
                    <th className="px-4 py-2 text-left font-semibold text-[var(--text-muted)]">{t.statsPage.date}</th>
                    <th className="px-4 py-2 text-right font-semibold text-[var(--text-muted)]">{t.statsPage.count}</th>
                  </tr></thead>
                  <tbody className="divide-y divide-gray-50">
                    {dailyStats.length === 0
                      ? <tr><td colSpan={2} className="px-4 py-6 text-center text-[var(--text-subtle)]">{t.statsPage.noData}</td></tr>
                      : dailyStats.map((d, i) => (
                        <tr key={i} className="hover:bg-[var(--canvas)]">
                          <td className="px-4 py-2 text-[var(--text-body)]">{d.date}</td>
                          <td className="px-4 py-2 text-right font-semibold text-[var(--ink)]">{d.count.toLocaleString(locale)}</td>
                        </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 크로스 필터 배지: 차트 클릭으로 유형 필터 활성 시 표시 */}
      {crossFilter.type && (
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 bg-violet-50 border border-violet-200 rounded-lg px-3 py-1.5 text-sm text-violet-800">
            <span className="text-xs">{t.statsPage.chartFilter}</span>
            <span className="font-bold">{t.disasterTypes[crossFilter.type as keyof typeof t.disasterTypes] ?? crossFilter.type}</span>
            <button onClick={() => setCrossFilter({})}
              className="text-violet-400 hover:text-violet-700 font-bold leading-none ml-1">×</button>
          </div>
          <span className="text-xs text-[var(--text-subtle)]">{t.statsPage.chartFilterHint}</span>
        </div>
      )}

      {/* 위젯 그리드 */}
      <DndContext id="stats-widget-dnd" sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={layout.map(w => w.id)} strategy={rectSortingStrategy}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(12, 1fr)", gap: 12 }}>
            {layout.map(w => {
              const lib = widgetLibrary.find(x => x.id === w.libId);
              if (!lib) return null;
              return (
                <SortableWidgetCard key={w.id} id={w.id} span={w.span} widget={w} lib={lib}
                  onVariantChange={handleVariantChange}
                  onRemove={handleRemove}
                  titleOverride={lib.id === "sido-bar" && sido ? formatMessage(t.statsPage.districtCountTitle, { region: t.metros[sido as keyof typeof t.metros] ?? sido }) : undefined}
                  isNew={w.id === newWidgetId}>
                  <WidgetContent kind={lib.kind} variant={w.variant ?? lib.variants?.[0]?.key ?? ""}
                    typeStats={typeStats} regionStats={regionStats} levelStats={levelStats}
                    dailyStats={dailyStats} hourlyStats={hourlyStats} monthlyTypeStats={monthlyTypeStats}
                    dailyTypeStats={dailyTypeStats} isSubMonth={isSubMonth}
                    thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear}
                    weatherStats={weatherStats}
                    weatherTypeStats={weatherTypeStats}
                    weatherRegionStats={weatherRegionStats}
                    weatherHourlyStats={weatherHourlyStats}
                    weatherHourlyTypeStats={weatherHourlyTypeStats}
                    weatherHourlyRegionStats={weatherHourlyRegionStats}
                    isShortPeriod={isShortPeriod}
                    regionLabel={regionLabel}
                    loadingWeather={loadingWeather || loadingWeatherType || loadingWeatherRegion}
                    loadingWeatherHourly={loadingWeatherHourly || loadingWeatherHourlyType || loadingWeatherHourlyRegion}
                    scrollableRegion={lib.id === "sido-bar" && !!sido}
                    onTypeClick={onTypeClick}
                    selectedType={crossFilter.type}
                    isLoading={
                      lib.kind === "line"    ? loadingDaily :
                      lib.kind === "hbar"    ? (sido ? loadingSigungu : loadingSido) :
                      lib.kind === "heatmap" ? loadingHourly :
                      lib.kind === "stacked" ? (isSubMonth ? loadingDailyType : loadingMonthlyType) :
                      lib.kind === "compare" ? (loadingThisYear || loadingLastYear) :
                      loadingStats
                    } />
                </SortableWidgetCard>
              );
            })}
          </div>
        </SortableContext>
      </DndContext>

      {/* 안내 */}
      <div className="bg-[var(--blue-soft)] border border-blue-200 rounded-xl px-4 py-3 text-xs text-blue-800 leading-relaxed">
        💡 <b>{t.statsPage.connectedNotice}</b> {t.statsPage.connectedNoticeBody}
        <Link href="/alerts" className="ml-2 underline font-semibold">{t.statsPage.openAlerts} →</Link>
      </div>

      {/* 위젯 드로어 */}
      <WidgetLibrary open={drawerOpen} onClose={() => setDrawerOpen(false)} layout={layout} onAdd={handleAdd} onRemove={handleRemove} />
    </main>
  );
}
