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
 *                    isNew prop이 true이면 추가 직후 primary 외곽선 애니메이션을 1.6초간 재생합니다.
 */

import { useState, useRef, useEffect } from "react";
import type { DailyStat, HourlyStat, MonthlyTypeStat, WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import type { TypeStat, LevelStat, RegionStat, LibItem, WidgetItem } from "./_constants";
import { EmptyChart, LoadingChart } from "./_charts";
import { DonutChart } from "./charts/DonutChart";
import { HorizontalBar } from "./charts/HorizontalBar";
import { VerticalBar } from "./charts/VerticalBar";
import { LevelsCard } from "./charts/LevelsCard";
import { LineChart } from "./charts/LineChart";
import { DailyBar } from "./charts/DailyBar";
import { Heatmap } from "./charts/Heatmap";
import { DayOfWeekBar } from "./charts/DayOfWeekBar";
import { HourBar } from "./charts/HourBar";
import { CompareBars } from "./charts/CompareBars";
import { CompareLines } from "./charts/CompareLines";
import { WeatherCorrelationScatter } from "./charts/WeatherCorrelationScatter";
import { WeatherOverlayChart } from "./charts/WeatherOverlayChart";
import { WeatherByTypeChart } from "./charts/WeatherByTypeChart";
import { WeatherByRegionChart } from "./charts/WeatherByRegionChart";
import { useTranslation } from "react-i18next";
import type { I18nKey } from "@/constants/i18n";

// ─── 일별/시간별 토글 ────────────────────────────────────────────────────────

function GranularityToggle({ value, onChange }: { value: "daily" | "hourly"; onChange: (v: "daily" | "hourly") => void }) {
  const { t } = useTranslation();
  return (
    <div className="krds-segment stats-segment-sm self-start">
      {(["daily", "hourly"] as const).map(g => (
        <button key={g} type="button" onClick={() => onChange(g)} aria-pressed={value === g}>
          {g === "daily" ? t("statsPage.granularityDaily") : t("statsPage.granularityHourly")}
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
  const { t } = useTranslation();
  // 날씨 위젯의 일별/시간별 전환 상태 (isShortPeriod일 때만 토글 표시)
  const [granularity, setGranularity] = useState<"daily" | "hourly">("daily");

  if (isLoading) return <LoadingChart />;
  // 전체 유형 건수 합계 (도넛·비율 계산에 사용)
  const typeTotal = typeStats.reduce((s, d) => s + d.count, 0);
  // 가로/세로 막대에 넘길 형태로 변환한 상위 8개 유형 데이터
  const typeAsBar = typeStats.slice(0, 8).map(d => ({ label: d.type ?? t("statsPage.other"), count: d.count }));
  // 세로 막대/도넛에 넘길 형태로 변환한 지역 데이터 (scrollableRegion이면 전체, 아니면 상위 8개)
  const regionAsVBar = (scrollableRegion ? regionStats : regionStats.slice(0, 8)).map(d => ({ label: d.region, count: d.count }));
  // 경보 단계 코드를 번역된 이름으로 변환하는 맵
  const LEVEL_NAMES = t("statsPage.levels", { returnObjects: true }) as I18nKey["ko"]["statsPage"]["levels"];
  // 막대 차트에 넘길 형태로 변환한 단계 데이터 (코드 → 번역된 이름)
  const levelAsBar = levelStats.map(d => ({ label: d.level ? (LEVEL_NAMES[d.level as keyof typeof LEVEL_NAMES] ?? d.level) : t("statsPage.other"), count: d.count }));
  // 도넛 차트에 넘길 형태로 변환한 단계 데이터
  const levelForDonut = levelStats.map(d => ({ type: d.level ? (LEVEL_NAMES[d.level as keyof typeof LEVEL_NAMES] ?? d.level) : t("statsPage.other"), count: d.count }));
  // 전체 경보 단계 건수 합계
  const levelTotal = levelStats.reduce((s, d) => s + d.count, 0);
  // 누적 차트에 표시할 상위 4개 유형 이름
  const topTypes = typeStats.slice(0, 4).map(d => d.type ?? t("statsPage.other"));

  // 시간별 날씨 데이터를 사용할지 여부 (기간 ≤7일 && 사용자가 시간별 선택)
  const isHourly = isShortPeriod && granularity === "hourly";
  // 재난 유형/지역 코드를 번역된 이름으로 변환 (매칭용 원본 값은 그대로 유지하고 표시만 번역)
  const translateType = (type: string) => t(`disasterTypes.${type}`, { defaultValue: type });
  const translateRegion = (region: string) => t(`metros.${region}`, { defaultValue: region });

  switch (kind) {
    case "donut":
      if (typeTotal === 0) return <EmptyChart />;
      if (variant === "hbar") return <HorizontalBar data={typeAsBar.map(d => ({ region: d.label, count: d.count }))} onBarClick={onTypeClick} labelFormatter={translateType} />;
      if (variant === "vbar") return <VerticalBar data={typeAsBar} onBarClick={onTypeClick} labelFormatter={translateType} />;
      return <DonutChart data={typeStats.slice(0, 6)} total={typeTotal} onTypeClick={onTypeClick} selectedType={selectedType} labelFormatter={translateType} />;

    case "hbar": {
      if (regionStats.length === 0) return <EmptyChart />;
      if (variant === "vbar") return <VerticalBar data={regionAsVBar} labelFormatter={translateRegion} />;
      if (variant === "donut") {
        const top8 = regionStats.slice(0, 8);
        const total = top8.reduce((s, d) => s + d.count, 0);
        return <DonutChart data={top8.map(d => ({ type: d.region, count: d.count }))} total={total} labelFormatter={translateRegion} />;
      }
      if (scrollableRegion) return <div className="overflow-y-auto flex-1" style={{ maxHeight: 320 }}><HorizontalBar data={regionStats} labelFormatter={translateRegion} /></div>;
      return <HorizontalBar data={regionStats.slice(0, 8)} labelFormatter={translateRegion} />;
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
      if (variant === "donut") return <DonutChart data={typeAgg} total={typeAggTotal} onTypeClick={onTypeClick} selectedType={selectedType} labelFormatter={translateType} />;
      if (variant === "hbar") return <HorizontalBar data={typeAgg.map(d => ({ region: d.type, count: d.count }))} onBarClick={onTypeClick} labelFormatter={translateType} />;
      return <VerticalBar data={typeAgg.map(d => ({ label: d.type, count: d.count }))} onBarClick={onTypeClick} labelFormatter={translateType} />;
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
  const { t } = useTranslation();
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
    <div ref={cardRef} className={`krds-card flex flex-col${highlighted ? " widget-added" : ""}`} style={{ gridColumn: `span ${widget.span}`, minHeight: 240 }}>
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 px-5 py-4 border-b border-[var(--krds-gray-10)]">
        <div className="flex items-center gap-2 min-w-0">
          {dragHandleListeners && (
            <span {...dragHandleListeners}
              className="cursor-grab active:cursor-grabbing text-[var(--krds-gray-40)] hover:text-[var(--krds-gray-70)] select-none"
              title={t("statsPage.dragWidget")}>
              {/* 드래그 손잡이: 점 6개 */}
              <svg width="12" height="18" viewBox="0 0 12 18" fill="currentColor" aria-hidden="true">
                <circle cx="3" cy="3" r="1.5" /><circle cx="9" cy="3" r="1.5" />
                <circle cx="3" cy="9" r="1.5" /><circle cx="9" cy="9" r="1.5" />
                <circle cx="3" cy="15" r="1.5" /><circle cx="9" cy="15" r="1.5" />
              </svg>
            </span>
          )}
          <h3 className="text-[19px] font-bold leading-snug text-[var(--krds-gray-90)]">{titleOverride ?? lib.title}</h3>
          <div className="relative">
            <button
              type="button"
              onMouseEnter={() => setHelpOpen(true)}
              onMouseLeave={() => setHelpOpen(false)}
              onFocus={() => setHelpOpen(true)}
              onBlur={() => setHelpOpen(false)}
              aria-label={t("statsPage.widgetHelp")}
              className="w-5 h-5 rounded-full border border-[var(--krds-gray-30)] text-[var(--krds-gray-60)] text-[12px] font-bold leading-none flex items-center justify-center hover:border-[var(--krds-primary-50)] hover:text-[var(--krds-primary-50)] transition-colors"
            >
              ?
            </button>
            {helpOpen && (
              <div className="absolute left-0 top-full mt-1.5 z-20 w-64 bg-[var(--krds-gray-90)] text-white text-[14px] rounded-[var(--radius-control)] px-3 py-2.5 pointer-events-none leading-relaxed">
                {lib.help}
              </div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {lib.variants && lib.variants.length > 1 && (() => {
            // 현재 선택된 변형 키 (없으면 라이브러리 첫 번째 변형)
            const cur = widget.variant ?? lib.variants![0].key;
            return (
              <div className="krds-segment stats-segment-sm">
                {lib.variants!.map(v => (
                  <button key={v.key} type="button" onClick={() => onVariantChange(widget.id, v.key)} aria-pressed={cur === v.key}>
                    {v.label}
                  </button>
                ))}
              </div>
            );
          })()}
          <button type="button" onClick={handleDownloadPng} title={t("statsPage.downloadPng")} aria-label={t("statsPage.downloadPng")}
            className="stats-icon-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 4v11" /><path d="m7 10 5 5 5-5" /><path d="M5 20h14" />
            </svg>
          </button>
          <button type="button" onClick={() => onRemove(widget.id)} title={t("statsPage.removeWidget")} aria-label={t("statsPage.removeWidget")}
            className="stats-icon-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12" /><path d="M18 6 6 18" />
            </svg>
          </button>
        </div>
      </div>
      <div className="flex-1 px-5 py-4 flex flex-col min-h-0">{children}</div>
    </div>
  );
}
