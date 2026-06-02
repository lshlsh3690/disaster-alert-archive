"use client";

import { useState, useRef } from "react";
import type { DailyStat, HourlyStat, MonthlyTypeStat, WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import type { TypeStat, LevelStat, RegionStat, LibItem, WidgetItem } from "./_constants";
import { EmptyChart, LoadingChart } from "./_charts";
import { DonutChart, HorizontalBar, VerticalBar, LevelsCard } from "./_DistributionCharts";
import { LineChart, DailyBar, Heatmap, DayOfWeekBar, HourBar, StackedArea, StackedBar, CompareBars, CompareLines } from "./_TimeCharts";
import { WeatherCorrelationScatter, WeatherByTypeChart, WeatherByRegionChart } from "./_WeatherCharts";

// ─── 위젯 콘텐츠 디스패처 ────────────────────────────────────────────────────

export function WidgetContent({ kind, variant, typeStats, regionStats, levelStats, dailyStats, hourlyStats, monthlyTypeStats, thisYearData, lastYearData, currentYear, weatherStats, weatherTypeStats, weatherRegionStats, regionLabel, loadingWeather, scrollableRegion, isLoading }: {
  kind: string;
  variant: string;
  typeStats: TypeStat[];
  regionStats: RegionStat[];
  levelStats: LevelStat[];
  dailyStats: DailyStat[];
  hourlyStats: HourlyStat[];
  monthlyTypeStats: MonthlyTypeStat[];
  thisYearData: DailyStat[];
  lastYearData: DailyStat[];
  currentYear: number;
  weatherStats: WeatherCorrelationStat[];
  weatherTypeStats: WeatherTypeStat[];
  weatherRegionStats: WeatherRegionStat[];
  regionLabel: string;
  loadingWeather: boolean;
  scrollableRegion?: boolean;
  isLoading?: boolean;
}) {
  if (isLoading) return <LoadingChart />;
  const typeTotal = typeStats.reduce((s, d) => s + d.count, 0);
  const typeAsBar = typeStats.slice(0, 8).map(d => ({ label: d.type ?? "기타", count: d.count }));
  const regionAsVBar = (scrollableRegion ? regionStats : regionStats.slice(0, 8)).map(d => ({ label: d.region, count: d.count }));
  const LEVEL_NAMES: Record<string, string> = { LEVEL_1: "안전안내", LEVEL_2: "긴급재난", LEVEL_3: "위급재난" };
  const levelAsBar = levelStats.map(d => ({ label: d.level ? (LEVEL_NAMES[d.level] ?? d.level) : "기타", count: d.count }));
  const levelForDonut = levelStats.map(d => ({ type: d.level ? (LEVEL_NAMES[d.level] ?? d.level) : "기타", count: d.count }));
  const levelTotal = levelStats.reduce((s, d) => s + d.count, 0);
  const topTypes = typeStats.slice(0, 4).map(d => d.type ?? "기타");

  switch (kind) {
    case "donut":
      if (typeTotal === 0) return <EmptyChart />;
      if (variant === "hbar") return <HorizontalBar data={typeAsBar.map(d => ({ region: d.label, count: d.count }))} />;
      if (variant === "vbar") return <VerticalBar data={typeAsBar} />;
      return <DonutChart data={typeStats.slice(0, 6)} total={typeTotal} />;

    case "hbar": {
      if (regionStats.length === 0) return <EmptyChart />;
      if (variant === "vbar") return <VerticalBar data={regionAsVBar} />;
      if (variant === "donut") {
        const top8 = regionStats.slice(0, 8);
        const total = top8.reduce((s, d) => s + d.count, 0);
        return <DonutChart data={top8.map(d => ({ type: d.region, count: d.count }))} total={total} />;
      }
      if (scrollableRegion) return <div className="overflow-y-auto flex-1" style={{ maxHeight: 320 }}><HorizontalBar data={regionStats} /></div>;
      return <HorizontalBar data={regionStats.slice(0, 8)} />;
    }

    case "levels":
      if (levelStats.length === 0) return <EmptyChart />;
      if (variant === "donut") return <DonutChart data={levelForDonut} total={levelTotal} />;
      if (variant === "hbar") return <HorizontalBar data={levelAsBar.map(d => ({ region: d.label, count: d.count }))} />;
      return <LevelsCard data={levelStats} />;

    case "line":
      if (variant === "bar") return <DailyBar data={dailyStats} />;
      return <LineChart data={dailyStats} />;

    case "heatmap":
      if (hourlyStats.length === 0) return <EmptyChart />;
      if (variant === "dow")  return <DayOfWeekBar data={hourlyStats} />;
      if (variant === "hour") return <HourBar data={hourlyStats} />;
      return <Heatmap data={hourlyStats} />;

    case "stacked":
      if (variant === "bar") return <StackedBar data={monthlyTypeStats} types={topTypes} />;
      return <StackedArea data={monthlyTypeStats} types={topTypes} />;

    case "compare":
      if (variant === "line") return <CompareLines thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear} />;
      return <CompareBars thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear} />;

    case "weather":
      if (loadingWeather) return <LoadingChart />;
      if (variant === "region") return <WeatherByRegionChart data={weatherRegionStats} regionLabel={regionLabel} />;
      return <WeatherByTypeChart data={weatherTypeStats} />;

    case "weather2":
      if (loadingWeather) return <LoadingChart />;
      return <WeatherCorrelationScatter data={weatherStats} />;

    default: return <EmptyChart />;
  }
}

// ─── 위젯 카드 래퍼 ──────────────────────────────────────────────────────────

export function WidgetCard({ widget, lib, onVariantChange, onRemove, titleOverride, children }: {
  widget: WidgetItem; lib: LibItem;
  onVariantChange: (id: string, variant: string) => void;
  onRemove: (id: string) => void;
  titleOverride?: string;
  children: React.ReactNode;
}) {
  const cardRef = useRef<HTMLDivElement>(null);
  const [helpOpen, setHelpOpen] = useState(false);

  const handleDownloadPng = async () => {
    if (!cardRef.current) return;
    const { toPng } = await import("html-to-image");
    const dataUrl = await toPng(cardRef.current, { backgroundColor: "#ffffff", pixelRatio: 2 });
    const a = document.createElement("a");
    a.href = dataUrl;
    a.download = `${(titleOverride ?? lib.title).replace(/[·\s]/g, "_")}_${new Date().toISOString().slice(0, 10)}.png`;
    a.click();
  };

  return (
    <div ref={cardRef} className="bg-white rounded-xl shadow flex flex-col" style={{ gridColumn: `span ${widget.span}`, minHeight: 240 }}>
      <div className="flex items-center justify-between px-3.5 py-2.5 border-b border-gray-100">
        <div className="flex items-center gap-1.5">
          <span className="text-sm">{lib.icon}</span>
          <h3 className="text-sm font-bold text-gray-800">{titleOverride ?? lib.title}</h3>
          {lib.sample && (
            <span className="text-xs font-semibold text-amber-600 bg-amber-50 border border-amber-200 px-1.5 py-0.5 rounded">샘플</span>
          )}
          <div className="relative">
            <button
              onMouseEnter={() => setHelpOpen(true)}
              onMouseLeave={() => setHelpOpen(false)}
              className="w-4 h-4 rounded-full border border-gray-300 text-gray-400 text-[10px] font-bold leading-none flex items-center justify-center hover:border-blue-400 hover:text-blue-500 transition-colors"
            >
              ?
            </button>
            {helpOpen && (
              <div className="absolute left-0 top-full mt-1.5 z-20 w-64 bg-gray-800 text-white text-xs rounded-lg px-3 py-2.5 shadow-xl pointer-events-none leading-relaxed">
                {lib.help}
              </div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          {lib.variants && lib.variants.length > 1 && (() => {
            const cur = widget.variant ?? lib.variants![0].key;
            return (
              <div className="flex border border-gray-200 rounded overflow-hidden">
                {lib.variants!.map(v => (
                  <button key={v.key} onClick={() => onVariantChange(widget.id, v.key)}
                    className={`px-1.5 py-0.5 text-[11px] font-medium transition-colors ${cur === v.key ? "bg-blue-600 text-white" : "text-gray-400 hover:bg-gray-50"}`}>
                    {v.label}
                  </button>
                ))}
              </div>
            );
          })()}
          <button onClick={handleDownloadPng} title="PNG 다운로드"
            className="px-1.5 py-0.5 text-xs border border-gray-200 text-gray-400 rounded hover:border-green-400 hover:text-green-600 transition-colors">
            ⬇
          </button>
          <button onClick={() => onRemove(widget.id)} title="위젯 삭제"
            className="w-5 h-5 flex items-center justify-center text-gray-300 rounded hover:bg-red-50 hover:text-red-500 transition-colors">
            ✕
          </button>
        </div>
      </div>
      <div className="flex-1 p-3.5 flex flex-col min-h-0">{children}</div>
    </div>
  );
}
