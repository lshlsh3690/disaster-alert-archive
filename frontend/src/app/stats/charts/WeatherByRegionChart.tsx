"use client";

import { useState, useRef, useEffect } from "react";
import {
  ComposedChart, Bar, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer,
} from "recharts";
import type { WeatherRegionStat } from "@/types/alerts";
import { BAR_COLORS } from "../_constants";
import { EmptyChart } from "../_charts";
import { LoadingDonut } from "@/components/ui/LoadingDonut";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TTProps } from "./common";
import { getAggMode, getAggKey, fmtKey, aggModeLabel } from "./weatherHelpers";

/**
 * WeatherByRegionChart
 *
 * 지역별 일별 누적 막대 + 평균기온 꺾은선.
 * WeatherByTypeChart와 동일한 자동 집계·툴팁 고정(pin) 인터랙션을 가집니다.
 */
export function WeatherByRegionChart({ data, regionLabel }: { data: WeatherRegionStat[]; regionLabel: string }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const translateRegion = (region: string) => t(`metros.${region}`, { defaultValue: region });
  // 툴팁 핀 로딩 진행률 (0~100)
  const [progress, setProgress] = useState(0);
  // 툴팁 고정 여부
  const [pinned, setPinned] = useState(false);
  // 고정된 시점의 툴팁 데이터 스냅샷
  const [pinnedSnap, setPinnedSnap] = useState<{ label: string; payload: TTProps["payload"] } | null>(null);
  // 1.2초 카운트다운 인터벌 ref
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 마지막으로 호버된 툴팁 데이터
  const lastPayloadRef = useRef<{ label: string; payload: TTProps["payload"] } | null>(null);
  // 마우스가 차트 컨테이너 안에 있는지 여부
  const isHoveringRef = useRef(false);
  useEffect(() => () => { if (intervalRef.current) clearInterval(intervalRef.current); }, []);

  // 1.2초 타이머 실행
  const runTimer = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    let elapsed = 0;
    intervalRef.current = setInterval(() => {
      elapsed += 50;
      const pct = Math.min(100, (elapsed / 1200) * 100);
      setProgress(pct);
      if (pct >= 100) {
        clearInterval(intervalRef.current!);
        setPinnedSnap(lastPayloadRef.current);
        setPinned(true);
      }
    }, 50);
  };
  const startTimer = () => { if (!pinned) runTimer(); };
  const stopTimer = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (!pinned) setProgress(0);
  };
  const handleClose = () => {
    setPinned(false);
    setPinnedSnap(null);
    setProgress(0);
    if (isHoveringRef.current) runTimer();
  };

  if (data.length === 0) return <EmptyChart />;

  // 지역별 총 건수 합산 → 상위 10개 지역만 차트에 표시
  const regionTotals = new Map<string, number>();
  data.forEach(d => regionTotals.set(d.region, (regionTotals.get(d.region) ?? 0) + d.count));
  const topRegions = [...regionTotals.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([r]) => r);

  // 중복 제거한 날짜 목록 → 집계 단위 결정
  const allDates = [...new Set(data.map(d => d.date))].sort();
  const mode = getAggMode(allDates.length);

  // 날짜별 평균 기온
  const dateTemp = new Map<string, number | null>();
  data.forEach(d => { if (!dateTemp.has(d.date)) dateTemp.set(d.date, d.avgTemp ?? null); });

  // 집계 키별 지역 건수 (key → region → count), 상위 10개 지역만 집계
  const keyRegionCount = new Map<string, Map<string, number>>();
  // 집계 키에 속한 원본 날짜 목록
  const keyDates = new Map<string, Set<string>>();
  data.forEach(d => {
    if (!topRegions.includes(d.region)) return;
    const key = getAggKey(d.date, mode);
    if (!keyRegionCount.has(key)) keyRegionCount.set(key, new Map());
    keyRegionCount.get(key)!.set(d.region, (keyRegionCount.get(key)!.get(d.region) ?? 0) + d.count);
    if (!keyDates.has(key)) keyDates.set(key, new Set());
    keyDates.get(key)!.add(d.date);
  });

  // 정렬된 집계 키 목록
  const keys = [...keyRegionCount.keys()].sort();
  // X축 레이블 표시 간격
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  // Recharts 피벗 데이터: 각 행이 하나의 집계 기간, 열이 지역별 건수 + 평균기온
  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = keys.map(key => {
    const regionMap = keyRegionCount.get(key)!;
    // 해당 집계 기간에 속한 날짜들의 기온 평균
    const temps = [...(keyDates.get(key) ?? [])].map(d => dateTemp.get(d) ?? null).filter((v): v is number => v != null);
    const obj: PivotRow = { date: fmtKey(key, mode, t("statsPage.monthSuffix")) };
    topRegions.forEach(r => { obj[r] = regionMap.get(r) ?? 0; });
    obj._avgTemp = temps.length > 0 ? Math.round(temps.reduce((a, b) => a + b, 0) / temps.length * 10) / 10 : null;
    return obj;
  });

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (active && payload?.length && label) lastPayloadRef.current = { label, payload };
    if (!active || !payload?.length || pinned) return null;
    const tempEntry = payload.find(p => p.dataKey === "_avgTemp");
    const bars = payload.filter(p => p.dataKey !== "_avgTemp" && (p.value ?? 0) > 0);
    return (
      <div style={{ ...TT_BOX, position: "relative", paddingRight: 28, minWidth: 160 }}>
        <div style={{ position: "absolute", top: 6, right: 6 }}><LoadingDonut progress={progress} /></div>
        <p style={TT_LABEL}>{label}</p>
        <div style={{ overflowY: "hidden" }}>
          {bars.map((p, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 2 }}>
              <span style={{ width: 8, height: 8, borderRadius: 2, background: BAR_COLORS[topRegions.indexOf(String(p.dataKey)) % BAR_COLORS.length], display: "inline-block", flexShrink: 0 }} />
              <span style={{ color: "#e2e8f0", fontSize: 11 }}>{translateRegion(String(p.dataKey))}: {(p.value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</span>
            </div>
          ))}
        </div>
        {tempEntry?.value != null && <p style={{ ...TT_LABEL, marginTop: 4 }}>{t("statsPage.weatherChart.temperatureLabel")} {tempEntry.value}°C</p>}
      </div>
    );
  };

  return (
    <div className="flex flex-col flex-1 min-h-0 gap-1 relative"
      onMouseEnter={() => { isHoveringRef.current = true; startTimer(); }}
      onMouseLeave={() => { isHoveringRef.current = false; stopTimer(); }}>
      <p className="text-[10px] text-gray-400 text-right pr-1">
        {regionLabel} · {t("statsPage.weatherChart.topRegionsSuffix")}{mode !== "daily" ? ` · ${aggModeLabel(mode, t)}` : ""}
      </p>

      {pinned && pinnedSnap && (
        <div style={{ position: "absolute", top: 8, right: 8, zIndex: 20, ...TT_BOX, minWidth: 180, maxWidth: 220, maxHeight: 260, display: "flex", flexDirection: "column" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4, flexShrink: 0 }}>
            <p style={{ ...TT_LABEL, margin: 0 }}>{pinnedSnap.label}</p>
            <button onClick={handleClose}
              style={{ background: "none", border: "none", color: "#94a3b8", cursor: "pointer", fontSize: 14, lineHeight: 1, padding: 0, marginLeft: 12 }}>
              ✕
            </button>
          </div>
          <div style={{ overflowY: "hidden", flex: 1 }}>
            {pinnedSnap.payload?.filter(p => p.dataKey !== "_avgTemp" && (p.value ?? 0) > 0).map((p, i) => (
              <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 2 }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: BAR_COLORS[topRegions.indexOf(String(p.dataKey)) % BAR_COLORS.length], display: "inline-block", flexShrink: 0 }} />
                <span style={{ color: "#e2e8f0", fontSize: 11 }}>{translateRegion(String(p.dataKey))}: {(p.value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</span>
              </div>
            ))}
          </div>
          {pinnedSnap.payload?.find(p => p.dataKey === "_avgTemp")?.value != null && (
            <p style={{ ...TT_LABEL, marginTop: 4, flexShrink: 0 }}>{t("statsPage.weatherChart.temperatureLabel")} {pinnedSnap.payload.find(p => p.dataKey === "_avgTemp")?.value}°C</p>
          )}
        </div>
      )}

      <div className="flex-1 min-h-0 relative">
        <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
          <ComposedChart data={pivoted} margin={{ top: 4, right: 36, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="date" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#9ca3af" }} interval={labelStride - 1} />
            <YAxis yAxisId="cnt" axisLine={false} tickLine={false} tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
            <YAxis yAxisId="temp" orientation="right" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#f97316" }} width={36} tickFormatter={(v: number) => `${v}°`} />
            <Tooltip content={<TooltipContent />} wrapperStyle={{ zIndex: 30 }} />
            <Legend wrapperStyle={{ fontSize: 10 }}
              formatter={(v: string) => v === "_avgTemp" ? t("statsPage.weatherChart.averageTempLegend") : translateRegion(v)} />
            {topRegions.map((r, i) => (
              <Bar key={r} yAxisId="cnt" dataKey={r} stackId="s"
                fill={BAR_COLORS[i % BAR_COLORS.length]} maxBarSize={24}
                radius={i === topRegions.length - 1 ? [2, 2, 0, 0] : [0, 0, 0, 0]} isAnimationActive={false} />
            ))}
            <Line yAxisId="temp" type="monotone" dataKey="_avgTemp" stroke="#f97316"
              strokeWidth={2} dot={false} name="_avgTemp" isAnimationActive={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
