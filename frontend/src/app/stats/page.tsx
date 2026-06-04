"use client";

import { Suspense, useState, useEffect, useMemo, useCallback } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useAlertStats, useSidoStats, useSigunguStats, useDailyStats, useHourlyStats, useMonthlyTypeStats, useDailyTypeStats, useWeatherCorrelation, useWeatherByType, useWeatherByRegion, useWeatherHourlyCorrelation, useWeatherHourlyByType, useWeatherHourlyByRegion } from "@/lib/queries/useAlerts";
import type { DailyStat, WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import { levelTextToCode } from "@/ui/level";
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

  const sido      = searchParams.get("sido")      ?? undefined;
  const sigungu   = searchParams.get("sigungu")   ?? undefined;
  const startDate = searchParams.get("startDate") ?? undefined;
  const endDate   = searchParams.get("endDate")   ?? undefined;
  const type      = searchParams.get("type")      ?? undefined;
  const levelText = searchParams.get("levelText") ?? undefined;
  const keyword   = searchParams.get("keyword")   ?? undefined;
  const source    = searchParams.get("source")    ?? undefined;

  const statsParams = useMemo(() => {
    const levelCode = levelTextToCode(levelText);
    const region = sido && sigungu ? `${sido} ${sigungu}` : sido;
    return { region, startDate, endDate, type, level: levelCode, keyword, source: source as "ALL" | "OFFICIAL" | "USER" | undefined };
  }, [sido, sigungu, startDate, endDate, type, levelText, keyword, source]);

  const { data: stats,        isLoading: loadingStats }     = useAlertStats(statsParams);
  const { data: sidoStats,    isLoading: loadingSido }      = useSidoStats(statsParams);
  const { data: sigunguStats, isLoading: loadingSigungu }   = useSigunguStats({ ...statsParams, region: sido }, !!sido);

  const typeStats: TypeStat[] = useMemo(() =>
    (stats?.typeStats ?? []).filter(d => d.type).sort((a, b) => b.count - a.count),
    [stats]
  );

  const regionStats: RegionStat[] = useMemo(() => {
    if (sido) {
      if (!sigunguStats || sigunguStats.length === 0) return [];
      const prefix = sido + " ";
      return sigunguStats.map(d => ({ ...d, region: d.region.startsWith(prefix) ? d.region.slice(prefix.length) : d.region }));
    }
    return sidoStats ?? [];
  }, [sido, sidoStats, sigunguStats]);

  const levelStats: LevelStat[] = stats?.levelStats ?? [];

  const [activePreset, setActivePreset] = useState<1 | 2 | 3>(1);
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

  const hasCompare = layout.some(w => w.libId === "compare");
  const hasWeather = layout.some(w => w.libId === "weather-overlay" || w.libId === "weather-scatter");

  const { data: dailyStatsRaw,    isLoading: loadingDaily }       = useDailyStats(statsParams);
  const { data: hourlyStatsRaw,   isLoading: loadingHourly }      = useHourlyStats(statsParams);
  const { data: monthlyTypeRaw,   isLoading: loadingMonthlyType } = useMonthlyTypeStats(statsParams);
  const dailyStats       = dailyStatsRaw   ?? [];
  const hourlyStats      = hourlyStatsRaw  ?? [];
  const monthlyTypeStats = monthlyTypeRaw  ?? [];

  const currentYear = useMemo(() => new Date().getFullYear(), []);
  const compareBase = useMemo(() => ({ ...statsParams, startDate: undefined, endDate: undefined }), [statsParams]);
  const { data: thisYearRaw, isLoading: loadingThisYear } = useDailyStats({ ...compareBase, startDate: `${currentYear}-01-01`, endDate: `${currentYear}-12-31` }, hasCompare);
  const { data: lastYearRaw, isLoading: loadingLastYear } = useDailyStats({ ...compareBase, startDate: `${currentYear - 1}-01-01`, endDate: `${currentYear - 1}-12-31` }, hasCompare);
  const thisYearData: DailyStat[] = thisYearRaw ?? [];
  const lastYearData: DailyStat[] = lastYearRaw ?? [];

  const topType    = typeStats[0];
  const topRegion  = regionStats[0];
  const totalCount = stats?.totalCount ?? 0;
  const periodDays = useMemo(() => {
    if (startDate && endDate) {
      const diff = (new Date(endDate).getTime() - new Date(startDate).getTime()) / 86_400_000 + 1;
      return Math.max(1, Math.round(diff));
    }
    return 30;
  }, [startDate, endDate]);
  const dailyAvg = Math.round(totalCount / periodDays);

  const { data: weatherRaw, isLoading: loadingWeather } = useWeatherCorrelation(statsParams, hasWeather);
  const weatherStats = weatherRaw ?? [];

  const regionLevel = sido ? "sigungu" : "sido";
  const regionLabel = sido ? "시/군/구별" : "시/도별";
  const { data: weatherTypeRaw,   isLoading: loadingWeatherType   } = useWeatherByType(statsParams, hasWeather);
  const { data: weatherRegionRaw, isLoading: loadingWeatherRegion } = useWeatherByRegion(statsParams, regionLevel, hasWeather);
  const weatherTypeStats   = weatherTypeRaw   ?? [];
  const weatherRegionStats = weatherRegionRaw ?? [];

  const isSubMonth    = periodDays < 30;
  const isShortPeriod = periodDays <= 7;
  const { data: dailyTypeRaw, isLoading: loadingDailyType } = useDailyTypeStats(statsParams, isSubMonth);
  const dailyTypeStats = dailyTypeRaw ?? [];
  const { data: weatherHourlyRaw,       isLoading: loadingWeatherHourly       } = useWeatherHourlyCorrelation(statsParams, isShortPeriod && hasWeather);
  const { data: weatherHourlyTypeRaw,   isLoading: loadingWeatherHourlyType   } = useWeatherHourlyByType(statsParams, isShortPeriod && hasWeather);
  const { data: weatherHourlyRegionRaw, isLoading: loadingWeatherHourlyRegion } = useWeatherHourlyByRegion(statsParams, regionLevel, isShortPeriod && hasWeather);
  const weatherHourlyStats       = weatherHourlyRaw       ?? [];
  const weatherHourlyTypeStats   = weatherHourlyTypeRaw   ?? [];
  const weatherHourlyRegionStats = weatherHourlyRegionRaw ?? [];

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [newWidgetId, setNewWidgetId] = useState<string | null>(null);

  const updateLayout = useCallback((updater: (l: WidgetItem[]) => WidgetItem[]) => {
    setLayout(l => {
      const next = updater(l);
      try { localStorage.setItem(`stats-layout-${activePreset}`, JSON.stringify(next)); } catch {}
      return next;
    });
  }, [activePreset]);

  const handleRemove        = useCallback((id: string) => updateLayout(l => l.filter(w => w.id !== id)), [updateLayout]);
  const handleVariantChange = useCallback((id: string, variant: string) => updateLayout(l => l.map(w => w.id === id ? { ...w, variant } : w)), [updateLayout]);
  const handleAdd = useCallback((item: LibItem) => {
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
        <div className="flex gap-2 shrink-0 items-center">
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

      {/* 필터 배너 */}
      <FilterBanner sido={sido} sigungu={sigungu} startDate={startDate} endDate={endDate}
        type={type} levelText={levelText} keyword={keyword} source={source} />

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

      {/* 위젯 그리드 */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(12, 1fr)", gap: 12 }}>
        {layout.map(w => {
          const lib = WIDGET_LIBRARY.find(x => x.id === w.libId);
          if (!lib) return null;
          return (
            <WidgetCard key={w.id} widget={w} lib={lib}
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
                isLoading={
                  lib.kind === "line"    ? loadingDaily :
                  lib.kind === "hbar"    ? (sido ? loadingSigungu : loadingSido) :
                  lib.kind === "heatmap" ? loadingHourly :
                  lib.kind === "stacked" ? (isSubMonth ? loadingDailyType : loadingMonthlyType) :
                  lib.kind === "compare" ? (loadingThisYear || loadingLastYear) :
                  loadingStats
                } />
            </WidgetCard>
          );
        })}
      </div>

      {/* 안내 */}
      <div className="bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-xs text-blue-800 leading-relaxed">
        💡 <b>+ 위젯</b> 버튼으로 차트를 추가하거나 제거할 수 있어요. 각 위젯 헤더의 토글로 차트 유형을 전환할 수 있습니다.
        <Link href="/alerts" className="ml-2 underline font-semibold">재난문자 페이지에서 필터 설정 →</Link>
      </div>

      {/* 위젯 드로어 */}
      <WidgetLibrary open={drawerOpen} onClose={() => setDrawerOpen(false)} layout={layout} onAdd={handleAdd} onRemove={handleRemove} />
    </main>
  );
}
