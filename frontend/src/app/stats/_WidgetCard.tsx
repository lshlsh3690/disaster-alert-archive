"use client";

/**
 * _WidgetCard.tsx
 *
 * 통계 페이지 위젯의 두 가지 핵심 컴포넌트를 담고 있습니다.
 *
 *   - WidgetContent : lib.kind 값을 보고 알맞은 차트 컴포넌트로 분기하는 디스패처.
 *                    page.tsx에서 내려온 모든 데이터를 받아 각 차트에 필요한 것만 전달합니다.
 *
 *   - WidgetCard    : 헤더(제목·변형 토글·PNG 다운로드·삭제)와 콘텐츠 영역으로 이루어진 카드 래퍼.
 *                    isNew prop이 true이면 추가 직후 파란 glow 애니메이션을 1.6초간 재생합니다.
 */

import { useState, useRef, useEffect } from "react";
import type { DailyStat, HourlyStat, MonthlyTypeStat, WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import type { TypeStat, LevelStat, RegionStat, LibItem, WidgetItem } from "./_constants";
import { EmptyChart, LoadingChart } from "./_charts";
import { DonutChart, HorizontalBar, VerticalBar, LevelsCard } from "./_DistributionCharts";
import { LineChart, DailyBar, Heatmap, DayOfWeekBar, HourBar, CompareBars, CompareLines } from "./_TimeCharts";
import { WeatherCorrelationScatter, WeatherOverlayChart, WeatherByTypeChart, WeatherByRegionChart } from "./_WeatherCharts";

// ─── 일별/시간별 토글 ────────────────────────────────────────────────────────

function GranularityToggle({ value, onChange }: { value: "daily" | "hourly"; onChange: (v: "daily" | "hourly") => void }) {
  return (
    <div className="flex border border-gray-200 rounded overflow-hidden self-start">
      {(["daily", "hourly"] as const).map(g => (
        <button key={g} onClick={() => onChange(g)}
          className={`px-2 py-0.5 text-[11px] font-medium transition-colors ${value === g ? "bg-blue-600 text-white" : "text-gray-400 hover:bg-gray-50"}`}>
          {g === "daily" ? "일별" : "시간별"}
        </button>
      ))}
    </div>
  );
}

// ─── 위젯 콘텐츠 디스패처 ────────────────────────────────────────────────────

export function WidgetContent({ kind, variant, typeStats, regionStats, levelStats, dailyStats, hourlyStats, monthlyTypeStats, dailyTypeStats, isSubMonth, thisYearData, lastYearData, currentYear, weatherStats, weatherTypeStats, weatherRegionStats, weatherHourlyStats, weatherHourlyTypeStats, weatherHourlyRegionStats, isShortPeriod, regionLabel, loadingWeather, loadingWeatherHourly, scrollableRegion, isLoading, onTypeClick, selectedType }: {
  kind: string;
  variant: string;
  typeStats: TypeStat[];
  regionStats: RegionStat[];
  levelStats: LevelStat[];
  dailyStats: DailyStat[];
  hourlyStats: HourlyStat[];
  monthlyTypeStats: MonthlyTypeStat[];
  dailyTypeStats: MonthlyTypeStat[];
  isSubMonth: boolean;
  thisYearData: DailyStat[];
  lastYearData: DailyStat[];
  currentYear: number;
  weatherStats: WeatherCorrelationStat[];
  weatherTypeStats: WeatherTypeStat[];
  weatherRegionStats: WeatherRegionStat[];
  weatherHourlyStats: WeatherCorrelationStat[];
  weatherHourlyTypeStats: WeatherTypeStat[];
  weatherHourlyRegionStats: WeatherRegionStat[];
  isShortPeriod: boolean;
  regionLabel: string;
  loadingWeather: boolean;
  loadingWeatherHourly: boolean;
  scrollableRegion?: boolean;
  isLoading?: boolean;
  onTypeClick?: (type: string) => void;
  selectedType?: string;
}) {
  // 날씨 위젯의 일별/시간별 전환 상태 (isShortPeriod일 때만 토글 표시)
  const [granularity, setGranularity] = useState<"daily" | "hourly">("daily");

  if (isLoading) return <LoadingChart />;
  // 전체 유형 건수 합계 (도넛·비율 계산에 사용)
  const typeTotal = typeStats.reduce((s, d) => s + d.count, 0);
  // 가로/세로 막대에 넘길 형태로 변환한 상위 8개 유형 데이터
  const typeAsBar = typeStats.slice(0, 8).map(d => ({ label: d.type ?? "기타", count: d.count }));
  // 세로 막대/도넛에 넘길 형태로 변환한 지역 데이터 (scrollableRegion이면 전체, 아니면 상위 8개)
  const regionAsVBar = (scrollableRegion ? regionStats : regionStats.slice(0, 8)).map(d => ({ label: d.region, count: d.count }));
  // 경보 단계 코드를 한글 이름으로 변환하는 맵
  const LEVEL_NAMES: Record<string, string> = { LEVEL_1: "안전안내", LEVEL_2: "긴급재난", LEVEL_3: "위급재난" };
  // 막대 차트에 넘길 형태로 변환한 단계 데이터 (코드 → 한글 이름)
  const levelAsBar = levelStats.map(d => ({ label: d.level ? (LEVEL_NAMES[d.level] ?? d.level) : "기타", count: d.count }));
  // 도넛 차트에 넘길 형태로 변환한 단계 데이터
  const levelForDonut = levelStats.map(d => ({ type: d.level ? (LEVEL_NAMES[d.level] ?? d.level) : "기타", count: d.count }));
  // 전체 경보 단계 건수 합계
  const levelTotal = levelStats.reduce((s, d) => s + d.count, 0);
  // 누적 차트에 표시할 상위 4개 유형 이름
  const topTypes = typeStats.slice(0, 4).map(d => d.type ?? "기타");

  // 시간별 날씨 데이터를 사용할지 여부 (기간 ≤7일 && 사용자가 시간별 선택)
  const isHourly = isShortPeriod && granularity === "hourly";

  switch (kind) {
    case "donut":
      if (typeTotal === 0) return <EmptyChart />;
      if (variant === "hbar") return <HorizontalBar data={typeAsBar.map(d => ({ region: d.label, count: d.count }))} onBarClick={onTypeClick} />;
      if (variant === "vbar") return <VerticalBar data={typeAsBar} onBarClick={onTypeClick} />;
      return <DonutChart data={typeStats.slice(0, 6)} total={typeTotal} onTypeClick={onTypeClick} selectedType={selectedType} />;

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

    case "stacked": {
      const stackData = isSubMonth ? dailyTypeStats : monthlyTypeStats;
      const typeAgg = topTypes.map(type => ({
        type,
        count: stackData.filter(d => d.type === type).reduce((s, d) => s + d.count, 0),
      })).filter(d => d.count > 0);
      const typeAggTotal = typeAgg.reduce((s, d) => s + d.count, 0);
      if (typeAggTotal === 0) return <EmptyChart />;
      if (variant === "donut") return <DonutChart data={typeAgg} total={typeAggTotal} onTypeClick={onTypeClick} selectedType={selectedType} />;
      if (variant === "hbar") return <HorizontalBar data={typeAgg.map(d => ({ region: d.type, count: d.count }))} onBarClick={onTypeClick} />;
      return <VerticalBar data={typeAgg.map(d => ({ label: d.type, count: d.count }))} onBarClick={onTypeClick} />;
    }

    case "compare":
      if (variant === "line") return <CompareLines thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear} />;
      return <CompareBars thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear} />;

    case "weather": {
      const loading = isHourly ? loadingWeatherHourly : loadingWeather;
      if (loading) return <LoadingChart />;
      const typeData   = isHourly ? weatherHourlyTypeStats   : weatherTypeStats;
      const regionData = isHourly ? weatherHourlyRegionStats : weatherRegionStats;
      return (
        <div className="flex flex-col flex-1 min-h-0 gap-2">
          {isShortPeriod && <GranularityToggle value={granularity} onChange={setGranularity} />}
          {variant === "region"
            ? <WeatherByRegionChart data={regionData} regionLabel={regionLabel} />
            : <WeatherByTypeChart data={typeData} />}
        </div>
      );
    }

    case "weather2": {
      const loading = isHourly ? loadingWeatherHourly : loadingWeather;
      if (loading) return <LoadingChart />;
      const corrData = isHourly ? weatherHourlyStats : weatherStats;
      return (
        <div className="flex flex-col flex-1 min-h-0 gap-2">
          {isShortPeriod && <GranularityToggle value={granularity} onChange={setGranularity} />}
          {variant === "overlay"
            ? <WeatherOverlayChart data={corrData} />
            : <WeatherCorrelationScatter data={corrData} />}
        </div>
      );
    }

    default: return <EmptyChart />;
  }
}

// ─── 위젯 카드 래퍼 ──────────────────────────────────────────────────────────

export function WidgetCard({ widget, lib, onVariantChange, onRemove, titleOverride, isNew, dragHandleListeners, children }: {
  widget: WidgetItem; lib: LibItem;
  onVariantChange: (id: string, variant: string) => void;
  onRemove: (id: string) => void;
  titleOverride?: string;
  isNew?: boolean;
  dragHandleListeners?: Record<string, unknown>;
  children: React.ReactNode;
}) {
  // PNG 다운로드 시 html-to-image로 캡처할 카드 DOM 참조
  const cardRef = useRef<HTMLDivElement>(null);
  // ? 버튼 hover 시 도움말 툴팁 표시 여부
  const [helpOpen, setHelpOpen] = useState(false);
  // 추가 직후 glow 애니메이션 활성 상태 (requestAnimationFrame으로 지연 적용)
  const [highlighted, setHighlighted] = useState(false);
  useEffect(() => {
    if (!isNew) return;
    const raf = requestAnimationFrame(() => setHighlighted(true));
    const t = setTimeout(() => setHighlighted(false), 1600);
    return () => { cancelAnimationFrame(raf); clearTimeout(t); };
  }, [isNew]);

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
    <div ref={cardRef} className={`bg-white rounded-xl shadow flex flex-col${highlighted ? " widget-added" : ""}`} style={{ gridColumn: `span ${widget.span}`, minHeight: 240 }}>
      <div className="flex items-center justify-between px-3.5 py-2.5 border-b border-gray-100">
        <div className="flex items-center gap-1.5">
          {dragHandleListeners && (
            <span {...dragHandleListeners}
              className="cursor-grab active:cursor-grabbing text-gray-300 hover:text-gray-400 select-none px-0.5"
              title="드래그하여 순서 변경">
              ⠿
            </span>
          )}
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
            // 현재 선택된 변형 키 (없으면 라이브러리 첫 번째 변형)
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
