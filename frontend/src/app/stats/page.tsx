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
import { useSearchParams, useRouter } from "next/navigation";
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
import { WIDGET_LIBRARY, DEFAULT_LAYOUT } from "./_constants";
import { WidgetCard, WidgetContent } from "./_WidgetCard";
import { WidgetLibrary } from "./_WidgetLibrary";
import { KpiBox, FilterBanner } from "./_KpiCards";

// ─── CSV 다운로드 ─────────────────────────────────────────────────────────────

function buildCsv(opts: {
  filters: Record<string, string>;
  totalCount: number;
  dailyAvg: number;
  topType?: TypeStat;
  topRegion?: RegionStat;
  typeStats: TypeStat[];
  regionStats: RegionStat[];
  levelStats: LevelStat[];
  dailyStats: DailyStat[];
  weatherStats: WeatherCorrelationStat[];
  weatherTypeStats: WeatherTypeStat[];
  weatherRegionStats: WeatherRegionStat[];
  weatherHourlyStats: WeatherCorrelationStat[];
  weatherHourlyTypeStats: WeatherTypeStat[];
  weatherHourlyRegionStats: WeatherRegionStat[];
  isShortPeriod: boolean;
}): string {
  const { filters, totalCount, dailyAvg, topType, topRegion, typeStats, regionStats, levelStats, dailyStats,
    weatherStats, weatherTypeStats, weatherRegionStats,
    weatherHourlyStats, weatherHourlyTypeStats, weatherHourlyRegionStats, isShortPeriod } = opts;
  const rows: string[] = [];
  const row = (...cols: (string | number)[]) => rows.push(cols.map(c => `"${String(c).replace(/"/g, '""')}"`).join(","));
  const blank = () => rows.push("");
  const section = (title: string) => { blank(); row(title); };

  row("재난 통계 데이터");
  row(`다운로드 시각: ${new Date().toLocaleString("ko-KR")}`);

  section("## 필터 조건");
  row("항목", "값");
  Object.entries(filters).forEach(([k, v]) => row(k, v || "-"));

  section("## KPI 요약");
  row("항목", "값");
  row("총 발생 건수", `${totalCount}건`);
  row("일 평균 (최근 30일)", `${dailyAvg}건`);
  row("최다 유형", topType?.type ?? "-");
  row("최다 지역", topRegion?.region ?? "-");

  if (typeStats.length > 0) {
    section("## 재난 유형 분포");
    row("순위", "유형", "건수", "비율");
    typeStats.forEach((d, i) =>
      row(i + 1, d.type ?? "기타", d.count, `${Math.round((d.count / (totalCount || 1)) * 100)}%`)
    );
  }

  if (regionStats.length > 0) {
    section("## 지역별 발생 건수");
    row("순위", "지역", "건수");
    regionStats.forEach((d, i) => row(i + 1, d.region, d.count));
  }

  if (levelStats.length > 0) {
    const levelNames: Record<string, string> = { LEVEL_1: "안전안내", LEVEL_2: "긴급재난", LEVEL_3: "위급재난" };
    const levelTotal = levelStats.reduce((s, d) => s + d.count, 0) || 1;
    section("## 경보 단계 분포");
    row("단계", "건수", "비율");
    levelStats.forEach(d =>
      row(d.level ? (levelNames[d.level] ?? d.level) : "기타", d.count, `${Math.round((d.count / levelTotal) * 100)}%`)
    );
  }

  if (dailyStats.length > 0) {
    section("## 일별 발생 추이");
    row("날짜", "건수");
    dailyStats.forEach(d => row(d.date, d.count));
  }

  const fmt = (v: number | null | undefined) => v == null ? "-" : String(v);

  if (weatherStats.length > 0) {
    section("## 일별 날씨-재난 상관관계");
    row("날짜", "재난건수", "평균기온(℃)", "최저기온(℃)", "최고기온(℃)", "최대강수량(mm)", "평균풍속(m/s)", "주요재난유형");
    weatherStats.forEach(d =>
      row(d.date, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip), fmt(d.avgWindSpeed), d.primaryType ?? "-")
    );
  }

  if (weatherTypeStats.length > 0) {
    section("## 재난유형별 날씨");
    row("날짜", "재난유형", "건수", "평균기온(℃)", "최저기온(℃)", "최고기온(℃)", "최대강수량(mm)");
    weatherTypeStats.forEach(d =>
      row(d.date, d.type ?? "기타", d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  if (weatherRegionStats.length > 0) {
    section("## 지역별 날씨");
    row("날짜", "지역", "건수", "평균기온(℃)", "최저기온(℃)", "최고기온(℃)", "최대강수량(mm)");
    weatherRegionStats.forEach(d =>
      row(d.date, d.region, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  if (isShortPeriod && weatherHourlyStats.length > 0) {
    section("## 시간별 날씨-재난 상관관계");
    row("일시", "재난건수", "평균기온(℃)", "최저기온(℃)", "최고기온(℃)", "최대강수량(mm)", "평균풍속(m/s)", "주요재난유형");
    weatherHourlyStats.forEach(d =>
      row(d.date, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip), fmt(d.avgWindSpeed), d.primaryType ?? "-")
    );
  }

  if (isShortPeriod && weatherHourlyTypeStats.length > 0) {
    section("## 시간별 재난유형별 날씨");
    row("일시", "재난유형", "건수", "평균기온(℃)", "최저기온(℃)", "최고기온(℃)", "최대강수량(mm)");
    weatherHourlyTypeStats.forEach(d =>
      row(d.date, d.type ?? "기타", d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
    );
  }

  if (isShortPeriod && weatherHourlyRegionStats.length > 0) {
    section("## 시간별 지역별 날씨");
    row("일시", "지역", "건수", "평균기온(℃)", "최저기온(℃)", "최고기온(℃)", "최대강수량(mm)");
    weatherHourlyRegionStats.forEach(d =>
      row(d.date, d.region, d.count, fmt(d.avgTemp), fmt(d.minTemp), fmt(d.maxTemp), fmt(d.maxPrecip))
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
  return (
    <Suspense fallback={<main className="p-6">불러오는 중...</main>}>
      <StatsPageInner />
    </Suspense>
  );
}

function StatsPageInner() {
  const searchParams = useSearchParams();
  const router = useRouter();

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

  // 적용 버튼: 로컬 필터를 URL 파라미터로 push → useSearchParams가 자동 업데이트
  const applyFilter = () => {
    const qs = new URLSearchParams();
    if (localFilter.sido)      qs.set("sido",      localFilter.sido);
    if (localFilter.sigungu)   qs.set("sigungu",   localFilter.sigungu);
    if (localFilter.startDate) qs.set("startDate", localFilter.startDate);
    if (localFilter.endDate)   qs.set("endDate",   localFilter.endDate);
    if (localFilter.type)      qs.set("type",      localFilter.type);
    if (localFilter.levelText) qs.set("levelText", localFilter.levelText);
    router.push(`?${qs.toString()}`);
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
  useEffect(() => {
    try {
      const p = (Number(localStorage.getItem("stats-preset-active")) || 1) as 1 | 2 | 3;
      const saved = localStorage.getItem(`stats-layout-${p}`);
      setActivePreset(p);
      if (saved) setLayout(JSON.parse(saved) as WidgetItem[]);
    } catch {}
  }, []);

  const switchPreset = useCallback((p: 1 | 2 | 3) => {
    setActivePreset(p);
    try {
      localStorage.setItem("stats-preset-active", String(p));
      const saved = localStorage.getItem(`stats-layout-${p}`);
      setLayout(saved ? (JSON.parse(saved) as WidgetItem[]) : DEFAULT_LAYOUT);
    } catch {}
  }, []);

  // 비교 위젯 존재 여부 → 전년/금년 데이터 페칭 여부 결정 (enabled 플래그)
  const hasCompare = layout.some(w => w.libId === "compare");
  // 날씨 위젯 존재 여부 → 날씨 데이터 페칭 여부 결정 (enabled 플래그)
  const hasWeather = layout.some(w => w.libId === "weather-overlay" || w.libId === "weather-scatter");

  const { data: dailyStatsRaw,    isLoading: loadingDaily }       = useDailyStats(effectiveParams);
  const { data: hourlyStatsRaw,   isLoading: loadingHourly }      = useHourlyStats(effectiveParams);
  const { data: monthlyTypeRaw,   isLoading: loadingMonthlyType } = useMonthlyTypeStats(effectiveParams);
  // API raw 데이터의 null-coalesce (로딩 중엔 빈 배열)
  const dailyStats       = dailyStatsRaw   ?? [];
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
  const regionLabel = sido ? "시/군/구별" : "시/도별";
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
          <h1 className="text-xl font-semibold">재난 통계</h1>
          <p className="text-sm text-gray-500">재난문자 페이지에서 필터링한 데이터를 다양한 차트로 분석합니다.</p>
        </div>
        <div className="flex gap-2 shrink-0 items-center flex-wrap justify-end">
          {/* 필터 토글 버튼 */}
          <button onClick={openFilter}
            className={`px-3 py-2 text-sm font-semibold rounded-lg border transition-colors flex items-center gap-1 ${hasActiveFilter ? "border-blue-400 bg-blue-50 text-blue-700" : "border-gray-200 bg-white text-gray-700 hover:border-blue-300 hover:text-blue-600"}`}>
            🔍 필터{hasActiveFilter ? " ●" : ""}
          </button>
          {/* 프리셋 탭 */}
          <div className="flex border border-gray-200 rounded-lg overflow-hidden">
            {([1, 2, 3] as const).map(p => (
              <button key={p} onClick={() => switchPreset(p)}
                className={`px-3 py-2 text-sm font-semibold transition-colors ${activePreset === p ? "bg-blue-600 text-white" : "text-gray-500 hover:bg-gray-50"}`}>
                {p}
              </button>
            ))}
          </div>
          <button onClick={() => {
            const filters: Record<string, string> = {
              "시·도": sido ?? "전체",
              "시·군·구": sigungu ?? "전체",
              "시작일": startDate ?? "전체",
              "종료일": endDate ?? "전체",
              "재난 유형": type ?? "전체",
              "경보 단계": levelText ?? "전체",
              "키워드": keyword ?? "-",
              "출처": source ?? "ALL",
            };
            const csv = buildCsv({ filters, totalCount, dailyAvg, topType, topRegion, typeStats, regionStats, levelStats, dailyStats,
              weatherStats, weatherTypeStats, weatherRegionStats,
              weatherHourlyStats, weatherHourlyTypeStats, weatherHourlyRegionStats, isShortPeriod });
            downloadCsv(`재난통계_${new Date().toISOString().slice(0, 10)}.csv`, csv);
          }} className="px-3 py-2 text-sm font-semibold rounded-lg border border-gray-200 bg-white text-gray-700 hover:border-green-400 hover:text-green-600 transition-colors flex items-center gap-1">
            ⬇ CSV
          </button>
          <button onClick={() => setDrawerOpen(true)}
            className="px-3 py-2 text-sm font-semibold rounded-lg bg-blue-600 text-white flex items-center gap-1">
            <span className="text-base leading-none">+</span> 위젯
          </button>
        </div>
      </div>

      {/* 접히는 필터 패널 */}
      {filterOpen && (
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm px-4 py-4 space-y-3">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {/* 시·도 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-gray-500">시·도</label>
              <select value={localFilter.sido} onChange={e => setLocalSido(e.target.value)}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-800 focus:outline-none focus:border-blue-400">
                <option value="">전체</option>
                {METROS.map(m => <option key={m} value={m}>{m}</option>)}
              </select>
            </div>
            {/* 시·군·구 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-gray-500">시·군·구</label>
              <select value={localFilter.sigungu} onChange={e => setLocalFilter(f => ({ ...f, sigungu: e.target.value }))}
                disabled={!localFilter.sido}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-800 focus:outline-none focus:border-blue-400 disabled:bg-gray-50 disabled:text-gray-400">
                <option value="">전체</option>
                {sigunguList?.filter(s => s.name !== "전체").map(s => (
                  <option key={s.code} value={s.name}>{s.name}</option>
                ))}
              </select>
            </div>
            {/* 재난 유형 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-gray-500">재난 유형</label>
              <select value={localFilter.type} onChange={e => setLocalFilter(f => ({ ...f, type: e.target.value }))}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-800 focus:outline-none focus:border-blue-400">
                <option value="">전체</option>
                {DISASTER_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            {/* 경보 단계 */}
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-gray-500">경보 단계</label>
              <select value={localFilter.levelText} onChange={e => setLocalFilter(f => ({ ...f, levelText: e.target.value }))}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-800 focus:outline-none focus:border-blue-400">
                <option value="">전체</option>
                <option value="안전안내문자">안전안내</option>
                <option value="긴급재난문자">긴급재난</option>
                <option value="위급재난문자">위급재난</option>
              </select>
            </div>
          </div>

          {/* 기간 선택 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold text-gray-500">기간</label>
            <div className="flex flex-wrap items-center gap-2">
              <input type="date" value={localFilter.startDate} onChange={e => setLocalFilter(f => ({ ...f, startDate: e.target.value }))}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-800 focus:outline-none focus:border-blue-400" />
              <span className="text-gray-400 text-sm">~</span>
              <input type="date" value={localFilter.endDate} onChange={e => setLocalFilter(f => ({ ...f, endDate: e.target.value }))}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm text-gray-800 focus:outline-none focus:border-blue-400" />
              <div className="flex gap-1">
                {([7, 30, 90] as const).map(d => (
                  <button key={d} onClick={() => setQuickDate(d)}
                    className="px-2.5 py-1.5 text-xs font-semibold border border-gray-200 rounded-lg text-gray-600 hover:border-blue-400 hover:text-blue-600 transition-colors">
                    {d}일
                  </button>
                ))}
                <button onClick={() => setQuickDate("year")}
                  className="px-2.5 py-1.5 text-xs font-semibold border border-gray-200 rounded-lg text-gray-600 hover:border-blue-400 hover:text-blue-600 transition-colors">
                  올해
                </button>
              </div>
            </div>
          </div>

          {/* 액션 버튼 */}
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => setFilterOpen(false)}
              className="px-4 py-1.5 text-sm font-semibold border border-gray-200 rounded-lg text-gray-600 hover:bg-gray-50 transition-colors">
              취소
            </button>
            <button onClick={() => {
              setLocalFilter({ sido: "", sigungu: "", startDate: "", endDate: "", type: "", levelText: "" });
            }}
              className="px-4 py-1.5 text-sm font-semibold border border-gray-200 rounded-lg text-gray-500 hover:bg-gray-50 transition-colors">
              초기화
            </button>
            <button onClick={applyFilter}
              className="px-4 py-1.5 text-sm font-semibold rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors">
              적용
            </button>
          </div>
        </div>
      )}

      {/* 필터 배너 */}
      <FilterBanner sido={sido} sigungu={sigungu} startDate={startDate} endDate={endDate}
        type={type} levelText={levelText} keyword={keyword} source={source} onFilterOpen={openFilter} />

      {/* KPI */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <KpiBox icon="📨" label="총 발생 건수"  value={`${totalCount.toLocaleString("ko-KR")}건`} sub="필터 적용 결과" />
        <KpiBox icon="📅" label="일 평균"        value={`${dailyAvg}건`}
          sub={startDate && endDate ? `${periodDays}일 기준` : "최근 30일 기준"} />
        <KpiBox icon="🌧" label="최다 유형"       value={topType?.type ?? "-"}
          sub={topType ? `${topType.count.toLocaleString("ko-KR")}건 · ${Math.round((topType.count / (totalCount || 1)) * 100)}%` : "-"} />
        <KpiBox icon="📍" label="최다 지역"       value={topRegion?.region ?? "-"}
          sub={topRegion ? `${topRegion.count.toLocaleString("ko-KR")}건` : "-"} />
      </div>

      {/* 크로스 필터 배지: 차트 클릭으로 유형 필터 활성 시 표시 */}
      {crossFilter.type && (
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 bg-violet-50 border border-violet-200 rounded-lg px-3 py-1.5 text-sm text-violet-800">
            <span className="text-xs">차트 필터</span>
            <span className="font-bold">{crossFilter.type}</span>
            <button onClick={() => setCrossFilter({})}
              className="text-violet-400 hover:text-violet-700 font-bold leading-none ml-1">×</button>
          </div>
          <span className="text-xs text-gray-400">다른 유형 클릭 시 전환 · × 로 해제</span>
        </div>
      )}

      {/* 위젯 그리드 */}
      <DndContext id="stats-widget-dnd" sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={layout.map(w => w.id)} strategy={rectSortingStrategy}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(12, 1fr)", gap: 12 }}>
            {layout.map(w => {
              const lib = WIDGET_LIBRARY.find(x => x.id === w.libId);
              if (!lib) return null;
              return (
                <SortableWidgetCard key={w.id} id={w.id} span={w.span} widget={w} lib={lib}
                  onVariantChange={handleVariantChange}
                  onRemove={handleRemove}
                  titleOverride={lib.id === "sido-bar" && sido ? `${sido} 시·군·구별 발생 건수` : undefined}
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
      <div className="bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-xs text-blue-800 leading-relaxed">
        💡 이 통계는 <b>재난문자 검색 결과</b>와 연동됩니다. 지역·기간·유형을 검색하면 그 결과가 모든 차트에 반영돼요.
        <b> + 위젯</b> 버튼으로 원하는 차트를 추가·제거하고, 헤더 토글로 차트 유형도 전환할 수 있습니다.
        <Link href="/alerts" className="ml-2 underline font-semibold">재난문자 검색 페이지 →</Link>
      </div>

      {/* 위젯 드로어 */}
      <WidgetLibrary open={drawerOpen} onClose={() => setDrawerOpen(false)} layout={layout} onAdd={handleAdd} onRemove={handleRemove} />
    </main>
  );
}
