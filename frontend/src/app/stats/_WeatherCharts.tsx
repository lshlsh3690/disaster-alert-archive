"use client";

import { useState, useRef, useEffect } from "react";
import {
  ComposedChart, Bar, Line, Area,
  ScatterChart, Scatter, ZAxis,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer,
} from "recharts";
import type { WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import { TYPE_COLORS, STACKED_COLORS, BAR_COLORS } from "./_constants";
import { EmptyChart } from "./_charts";

// ─── 날짜 집계 헬퍼 ──────────────────────────────────────────────────────────

type AggMode = "daily" | "weekly" | "monthly";

function getAggMode(dateCount: number): AggMode {
  if (dateCount <= 60) return "daily";
  if (dateCount <= 180) return "weekly";
  return "monthly";
}

function getAggKey(date: string, mode: AggMode): string {
  if (mode === "daily") return date;
  if (mode === "monthly") return date.slice(0, 7);
  const d = new Date(date);
  const dow = d.getDay();
  d.setDate(d.getDate() - (dow === 0 ? 6 : dow - 1));
  return d.toISOString().slice(0, 10);
}

function fmtKey(key: string, mode: AggMode): string {
  if (mode === "monthly") return `${parseInt(key.slice(5))}월`;
  return key.slice(5);
}

function aggModeLabel(mode: AggMode): string {
  if (mode === "weekly") return "주별 집계";
  if (mode === "monthly") return "월별 집계";
  return "";
}

// ─── 로딩 도넛 ───────────────────────────────────────────────────────────────

function LoadingDonut({ progress }: { progress: number }) {
  const r = 7, c = 2 * Math.PI * r;
  const dash = (progress / 100) * c;
  return (
    <svg width={18} height={18} viewBox="0 0 18 18" style={{ display: "block" }}>
      <circle cx={9} cy={9} r={r} fill="none" stroke="#334155" strokeWidth={2.5} />
      <circle cx={9} cy={9} r={r} fill="none" stroke="#60a5fa" strokeWidth={2.5}
        strokeDasharray={`${dash} ${c - dash}`}
        transform="rotate(-90 9 9)" />
    </svg>
  );
}

// ─── 공통 툴팁 타입 · 스타일 ─────────────────────────────────────────────────

type TTProps = {
  active?: boolean;
  payload?: Array<{ value?: number; dataKey?: string; payload?: Record<string, number> }>;
  label?: string;
};

const TT_BOX: React.CSSProperties = { background: "#1e293b", borderRadius: 6, padding: "6px 10px", border: "none" };
const TT_LABEL: React.CSSProperties = { color: "#94a3b8", fontSize: 10, margin: "0 0 2px 0" };
const TT_VALUE: React.CSSProperties = { color: "#fff", fontSize: 12, fontWeight: 700, margin: 0 };

// ─── 날씨·유형별 누적 막대 ───────────────────────────────────────────────────

export function WeatherByTypeChart({ data }: { data: WeatherTypeStat[] }) {
  const [progress, setProgress] = useState(0);
  const [pinned, setPinned] = useState(false);
  const [pinnedSnap, setPinnedSnap] = useState<{ label: string; payload: TTProps["payload"] } | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastPayloadRef = useRef<{ label: string; payload: TTProps["payload"] } | null>(null);
  const isHoveringRef = useRef(false);
  useEffect(() => () => { if (intervalRef.current) clearInterval(intervalRef.current); }, []);

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

  const typeTotals = new Map<string, number>();
  data.forEach(d => typeTotals.set(d.type ?? "기타", (typeTotals.get(d.type ?? "기타") ?? 0) + d.count));
  const types = [...typeTotals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6).map(([t]) => t);
  const allDates = [...new Set(data.map(d => d.date))].sort();
  const mode = getAggMode(allDates.length);

  // (key → date → avgTemp) 로 날짜별 기온 한 번만 집계
  const dateTemp = new Map<string, number | null>();
  data.forEach(d => { if (!dateTemp.has(d.date)) dateTemp.set(d.date, d.avgTemp ?? null); });

  // (key → type → count) 집계
  const keyTypeCount = new Map<string, Map<string, number>>();
  const keyDates = new Map<string, Set<string>>();
  data.forEach(d => {
    const key = getAggKey(d.date, mode);
    const type = d.type ?? "기타";
    if (!keyTypeCount.has(key)) keyTypeCount.set(key, new Map());
    keyTypeCount.get(key)!.set(type, (keyTypeCount.get(key)!.get(type) ?? 0) + d.count);
    if (!keyDates.has(key)) keyDates.set(key, new Set());
    keyDates.get(key)!.add(d.date);
  });

  const keys = [...keyTypeCount.keys()].sort();
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = keys.map(key => {
    const typeMap = keyTypeCount.get(key)!;
    const temps = [...keyDates.get(key)!].map(d => dateTemp.get(d) ?? null).filter((t): t is number => t != null);
    const obj: PivotRow = { date: fmtKey(key, mode) };
    types.forEach(t => { obj[t] = typeMap.get(t) ?? 0; });
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
              <span style={{ width: 8, height: 8, borderRadius: 2, background: STACKED_COLORS[types.indexOf(String(p.dataKey)) % STACKED_COLORS.length], display: "inline-block", flexShrink: 0 }} />
              <span style={{ color: "#e2e8f0", fontSize: 11 }}>{p.dataKey}: {(p.value ?? 0).toLocaleString("ko-KR")}건</span>
            </div>
          ))}
        </div>
        {tempEntry?.value != null && <p style={{ ...TT_LABEL, marginTop: 4 }}>기온 {tempEntry.value}°C</p>}
      </div>
    );
  };

  return (
    <div className="flex flex-col flex-1 min-h-0 gap-1 relative"
      onMouseEnter={() => { isHoveringRef.current = true; startTimer(); }}
      onMouseLeave={() => { isHoveringRef.current = false; stopTimer(); }}>
      {mode !== "daily" && <p className="text-[10px] text-gray-400 text-right pr-1">{aggModeLabel(mode)}</p>}

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
                <span style={{ width: 8, height: 8, borderRadius: 2, background: STACKED_COLORS[types.indexOf(String(p.dataKey)) % STACKED_COLORS.length], display: "inline-block", flexShrink: 0 }} />
                <span style={{ color: "#e2e8f0", fontSize: 11 }}>{p.dataKey}: {(p.value ?? 0).toLocaleString("ko-KR")}건</span>
              </div>
            ))}
          </div>
          {pinnedSnap.payload?.find(p => p.dataKey === "_avgTemp")?.value != null && (
            <p style={{ ...TT_LABEL, marginTop: 4, flexShrink: 0 }}>기온 {pinnedSnap.payload.find(p => p.dataKey === "_avgTemp")?.value}°C</p>
          )}
        </div>
      )}

      <div className="flex-1 min-h-0" style={{ height: "100%" }}>
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={pivoted} margin={{ top: 8, right: 36, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="date" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#9ca3af" }} interval={labelStride - 1} />
            <YAxis yAxisId="cnt" axisLine={false} tickLine={false} tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
            <YAxis yAxisId="temp" orientation="right" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#f97316" }} width={36} tickFormatter={(v: number) => `${v}°`} />
            <Tooltip content={<TooltipContent />} />
            <Legend wrapperStyle={{ fontSize: 10 }}
              formatter={(v: string) => v === "_avgTemp" ? "평균기온" : v} />
            {types.map((t, i) => (
              <Bar key={t} yAxisId="cnt" dataKey={t} stackId="s"
                fill={STACKED_COLORS[i % STACKED_COLORS.length]} maxBarSize={24}
                radius={i === types.length - 1 ? [2, 2, 0, 0] : [0, 0, 0, 0]} isAnimationActive={false} />
            ))}
            <Line yAxisId="temp" type="monotone" dataKey="_avgTemp" stroke="#f97316"
              strokeWidth={2} dot={false} name="_avgTemp" isAnimationActive={false} />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

// ─── 날씨·지역별 누적 막대 ───────────────────────────────────────────────────

export function WeatherByRegionChart({ data, regionLabel }: { data: WeatherRegionStat[]; regionLabel: string }) {
  const [progress, setProgress] = useState(0);
  const [pinned, setPinned] = useState(false);
  const [pinnedSnap, setPinnedSnap] = useState<{ label: string; payload: TTProps["payload"] } | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastPayloadRef = useRef<{ label: string; payload: TTProps["payload"] } | null>(null);
  const isHoveringRef = useRef(false);
  useEffect(() => () => { if (intervalRef.current) clearInterval(intervalRef.current); }, []);

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

  const regionTotals = new Map<string, number>();
  data.forEach(d => regionTotals.set(d.region, (regionTotals.get(d.region) ?? 0) + d.count));
  const topRegions = [...regionTotals.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([r]) => r);

  const allDates = [...new Set(data.map(d => d.date))].sort();
  const mode = getAggMode(allDates.length);

  const dateTemp = new Map<string, number | null>();
  data.forEach(d => { if (!dateTemp.has(d.date)) dateTemp.set(d.date, d.avgTemp ?? null); });

  const keyRegionCount = new Map<string, Map<string, number>>();
  const keyDates = new Map<string, Set<string>>();
  data.forEach(d => {
    if (!topRegions.includes(d.region)) return;
    const key = getAggKey(d.date, mode);
    if (!keyRegionCount.has(key)) keyRegionCount.set(key, new Map());
    keyRegionCount.get(key)!.set(d.region, (keyRegionCount.get(key)!.get(d.region) ?? 0) + d.count);
    if (!keyDates.has(key)) keyDates.set(key, new Set());
    keyDates.get(key)!.add(d.date);
  });

  const keys = [...keyRegionCount.keys()].sort();
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = keys.map(key => {
    const regionMap = keyRegionCount.get(key)!;
    const temps = [...(keyDates.get(key) ?? [])].map(d => dateTemp.get(d) ?? null).filter((t): t is number => t != null);
    const obj: PivotRow = { date: fmtKey(key, mode) };
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
              <span style={{ color: "#e2e8f0", fontSize: 11 }}>{p.dataKey}: {(p.value ?? 0).toLocaleString("ko-KR")}건</span>
            </div>
          ))}
        </div>
        {tempEntry?.value != null && <p style={{ ...TT_LABEL, marginTop: 4 }}>기온 {tempEntry.value}°C</p>}
      </div>
    );
  };

  return (
    <div className="flex flex-col flex-1 min-h-0 gap-1 relative"
      onMouseEnter={() => { isHoveringRef.current = true; startTimer(); }}
      onMouseLeave={() => { isHoveringRef.current = false; stopTimer(); }}>
      <p className="text-[10px] text-gray-400 text-right pr-1">
        {regionLabel} · 상위 10개 지역{mode !== "daily" ? ` · ${aggModeLabel(mode)}` : ""}
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
                <span style={{ color: "#e2e8f0", fontSize: 11 }}>{p.dataKey}: {(p.value ?? 0).toLocaleString("ko-KR")}건</span>
              </div>
            ))}
          </div>
          {pinnedSnap.payload?.find(p => p.dataKey === "_avgTemp")?.value != null && (
            <p style={{ ...TT_LABEL, marginTop: 4, flexShrink: 0 }}>기온 {pinnedSnap.payload.find(p => p.dataKey === "_avgTemp")?.value}°C</p>
          )}
        </div>
      )}

      <div className="flex-1 min-h-0" style={{ height: "100%" }}>
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={pivoted} margin={{ top: 4, right: 36, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="date" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#9ca3af" }} interval={labelStride - 1} />
            <YAxis yAxisId="cnt" axisLine={false} tickLine={false} tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
            <YAxis yAxisId="temp" orientation="right" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#f97316" }} width={36} tickFormatter={(v: number) => `${v}°`} />
            <Tooltip content={<TooltipContent />} />
            <Legend wrapperStyle={{ fontSize: 10 }}
              formatter={(v: string) => v === "_avgTemp" ? "평균기온" : v} />
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

// ─── 날씨 오버레이 (총 건수 + 기온 범위·강수 복합) ──────────────────────────

export function WeatherOverlayChart({ data }: { data: WeatherCorrelationStat[] }) {
  if (data.length === 0) return <EmptyChart />;

  const mode = getAggMode(data.length);

  // 집계
  type AggRow = { count: number; tempSum: number; tempN: number; minTemp: number | null; maxTemp: number | null; maxPrecip: number | null; typeCount: Map<string, number> };
  const aggMap = new Map<string, AggRow>();
  data.forEach(d => {
    const key = getAggKey(d.date, mode);
    if (!aggMap.has(key)) aggMap.set(key, { count: 0, tempSum: 0, tempN: 0, minTemp: null, maxTemp: null, maxPrecip: null, typeCount: new Map() });
    const e = aggMap.get(key)!;
    e.count += d.count;
    if (d.avgTemp != null) { e.tempSum += d.avgTemp; e.tempN++; }
    if (d.minTemp != null) e.minTemp = e.minTemp == null ? d.minTemp : Math.min(e.minTemp, d.minTemp);
    if (d.maxTemp != null) e.maxTemp = e.maxTemp == null ? d.maxTemp : Math.max(e.maxTemp, d.maxTemp);
    if (d.maxPrecip != null) e.maxPrecip = e.maxPrecip == null ? d.maxPrecip : Math.max(e.maxPrecip, d.maxPrecip);
    if (d.primaryType) e.typeCount.set(d.primaryType, (e.typeCount.get(d.primaryType) ?? 0) + d.count);
  });

  const keys = [...aggMap.keys()].sort();
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  const chartData = keys.map(key => {
    const e = aggMap.get(key)!;
    const avgTemp = e.tempN > 0 ? Math.round(e.tempSum / e.tempN * 10) / 10 : null;
    const primaryType = e.typeCount.size > 0 ? [...e.typeCount.entries()].sort((a, b) => b[1] - a[1])[0][0] : null;
    return {
      date: fmtKey(key, mode),
      count: e.count,
      avgTemp,
      minTemp: e.minTemp,
      maxTemp: e.maxTemp,
      maxPrecip: e.maxPrecip,
      primaryType,
      tempRange: e.minTemp != null && e.maxTemp != null ? [e.minTemp, e.maxTemp] as [number, number] : null,
    };
  });

  const TooltipContent = ({ active, payload }: TTProps) => {
    if (!active || !payload?.length) return null;
    const get = (key: string) => payload.find(p => p.dataKey === key)?.value;
    const row = payload[0].payload as unknown as typeof chartData[number];
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{row.date}</p>
        <p style={{ ...TT_VALUE, color: "#60a5fa" }}>{(get("count") ?? 0).toLocaleString("ko-KR")}건</p>
        {row?.primaryType && (
          <p style={{ ...TT_LABEL, marginTop: 4 }}>주요 유형: <span style={{ color: "#e2e8f0" }}>{row.primaryType}</span></p>
        )}
        {row.avgTemp != null && (
          <p style={TT_LABEL}>
            기온 {row.avgTemp.toFixed(1)}°C
            {row.minTemp != null && row.maxTemp != null && (
              <span style={{ color: "#94a3b8" }}> ({row.minTemp.toFixed(1)}~{row.maxTemp.toFixed(1)})</span>
            )}
          </p>
        )}
        {get("maxPrecip") != null && <p style={TT_LABEL}>강수 {get("maxPrecip")}mm</p>}
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0" style={{ height: "100%" }}>
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={chartData} margin={{ top: 8, right: 24, bottom: 4, left: 4 }}>
          <CartesianGrid vertical={false} stroke="#f3f4f6" />
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }}
            interval={labelStride - 1} />
          <YAxis yAxisId="cnt" axisLine={false} tickLine={false} tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
          <YAxis yAxisId="temp" orientation="right" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#f97316" }} width={36} tickFormatter={(v: number) => `${v}°`} />
          <Tooltip content={<TooltipContent />} />
          <Legend wrapperStyle={{ fontSize: 11 }}
            formatter={(v: string) => ({ count: "발생건수", avgTemp: "평균기온", tempRange: "기온범위", maxPrecip: "최대강수(mm)" }[v] ?? v)} />
          <Bar yAxisId="cnt" dataKey="count" fill="#3b82f6" fillOpacity={0.7} radius={[2, 2, 0, 0]} maxBarSize={20} isAnimationActive={false} />
          <Area yAxisId="temp" type="monotone" dataKey="tempRange" stroke="none"
            fill="#f97316" fillOpacity={0.12} activeDot={false} legendType="none" isAnimationActive={false} />
          <Line yAxisId="temp" type="monotone" dataKey="avgTemp" stroke="#f97316" strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line yAxisId="temp" type="monotone" dataKey="maxPrecip" stroke="#06b6d4" strokeWidth={1.5} dot={false} strokeDasharray="4 2" isAnimationActive={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── 날씨 상관 산점도 ────────────────────────────────────────────────────────

export function WeatherCorrelationScatter({ data }: { data: WeatherCorrelationStat[] }) {
  const filtered = data.filter(d => d.avgTemp != null);
  if (filtered.length === 0) return <EmptyChart />;

  const types = [...new Set(filtered.map(d => d.primaryType ?? "기타"))];
  const colorMap: Record<string, string> = {};
  types.forEach((t, i) => { colorMap[t] = TYPE_COLORS[i % TYPE_COLORS.length]; });

  const grouped = types.map(t => ({
    name: t,
    color: colorMap[t],
    points: filtered
      .filter(d => (d.primaryType ?? "기타") === t)
      .map(d => ({ x: d.avgTemp!, y: d.count, z: Math.max((d.maxPrecip ?? 0) + 1, 1), date: d.date, minTemp: d.minTemp, maxTemp: d.maxTemp })),
  }));

  const TooltipContent = ({ active, payload }: TTProps) => {
    if (!active || !payload?.length) return null;
    const p = payload[0].payload as unknown as { x: number; y: number; z: number; date: string; minTemp: number | null; maxTemp: number | null };
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{p.date}</p>
        <p style={TT_VALUE}>{p.y.toLocaleString("ko-KR")}건</p>
        <p style={TT_LABEL}>
          평균기온 {p.x.toFixed(1)}°C
          {p.minTemp != null && p.maxTemp != null && (
            <span style={{ color: "#64748b" }}> ({p.minTemp.toFixed(1)}~{p.maxTemp.toFixed(1)})</span>
          )}
        </p>
        <p style={TT_LABEL}>강수 {(p.z - 1).toFixed(1)}mm</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0" style={{ height: "100%" }}>
      <ResponsiveContainer width="100%" height="100%">
        <ScatterChart margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
          <CartesianGrid stroke="#f3f4f6" />
          <XAxis type="number" dataKey="x" name="기온" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} tickFormatter={(v: number) => `${v}°`} />
          <YAxis type="number" dataKey="y" name="건수" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
          <ZAxis type="number" dataKey="z" range={[30, 200]} />
          <Tooltip content={<TooltipContent />} cursor={{ strokeDasharray: "3 3" }} />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          {grouped.map(g => (
            <Scatter key={g.name} name={g.name} data={g.points} fill={g.color} fillOpacity={0.7} />
          ))}
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
}
