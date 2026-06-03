"use client";

import {
  ComposedChart, Bar, Line, Area,
  ScatterChart, Scatter, ZAxis,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer,
} from "recharts";
import type { WeatherCorrelationStat, WeatherTypeStat, WeatherRegionStat } from "@/types/alerts";
import { TYPE_COLORS, STACKED_COLORS, BAR_COLORS } from "./_constants";
import { EmptyChart } from "./_charts";

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
  if (data.length === 0) return <EmptyChart />;

  const types = [...new Set(data.map(d => d.type ?? "기타"))];
  const dates = [...new Set(data.map(d => d.date))].sort();
  const labelStride = Math.max(1, Math.floor(dates.length / 6));

  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = dates.map(date => {
    const dayRows = data.filter(d => d.date === date);
    const obj: PivotRow = { date: date.slice(5) };
    types.forEach(t => { obj[t] = dayRows.find(d => (d.type ?? "기타") === t)?.count ?? 0; });
    obj._avgTemp = dayRows[0]?.avgTemp ?? null;
    return obj;
  });

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    const tempEntry = payload.find(p => p.dataKey === "_avgTemp");
    const bars = payload.filter(p => p.dataKey !== "_avgTemp" && (p.value ?? 0) > 0);
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        {bars.map((p, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 2 }}>
            <span style={{ width: 8, height: 8, borderRadius: 2, background: STACKED_COLORS[types.indexOf(String(p.dataKey)) % STACKED_COLORS.length], display: "inline-block", flexShrink: 0 }} />
            <span style={{ color: "#e2e8f0", fontSize: 11 }}>{p.dataKey}: {(p.value ?? 0).toLocaleString("ko-KR")}건</span>
          </div>
        ))}
        {tempEntry?.value != null && <p style={{ ...TT_LABEL, marginTop: 4 }}>기온 {tempEntry.value}°C</p>}
      </div>
    );
  };

  return (
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
  );
}

// ─── 날씨·지역별 누적 막대 ───────────────────────────────────────────────────

export function WeatherByRegionChart({ data, regionLabel }: { data: WeatherRegionStat[]; regionLabel: string }) {
  if (data.length === 0) return <EmptyChart />;

  const regionTotals = new Map<string, number>();
  data.forEach(d => regionTotals.set(d.region, (regionTotals.get(d.region) ?? 0) + d.count));
  const topRegions = [...regionTotals.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([r]) => r);

  const dates = [...new Set(data.map(d => d.date))].sort();
  const labelStride = Math.max(1, Math.floor(dates.length / 6));

  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = dates.map(date => {
    const dayRows = data.filter(d => d.date === date && topRegions.includes(d.region));
    const obj: PivotRow = { date: date.slice(5) };
    topRegions.forEach(r => { obj[r] = dayRows.find(d => d.region === r)?.count ?? 0; });
    obj._avgTemp = dayRows[0]?.avgTemp ?? null;
    return obj;
  });

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    const tempEntry = payload.find(p => p.dataKey === "_avgTemp");
    const bars = payload.filter(p => p.dataKey !== "_avgTemp" && (p.value ?? 0) > 0);
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        {bars.map((p, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 2 }}>
            <span style={{ width: 8, height: 8, borderRadius: 2, background: BAR_COLORS[topRegions.indexOf(String(p.dataKey)) % BAR_COLORS.length], display: "inline-block", flexShrink: 0 }} />
            <span style={{ color: "#e2e8f0", fontSize: 11 }}>{p.dataKey}: {(p.value ?? 0).toLocaleString("ko-KR")}건</span>
          </div>
        ))}
        {tempEntry?.value != null && <p style={{ ...TT_LABEL, marginTop: 4 }}>기온 {tempEntry.value}°C</p>}
      </div>
    );
  };

  return (
    <div className="flex flex-col flex-1 min-h-0 gap-1">
      <p className="text-[10px] text-gray-400 text-right pr-1">{regionLabel} · 상위 10개 지역</p>
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
  const labelStride = Math.max(1, Math.floor(data.length / 6));

  // minTemp/maxTemp 를 [min, max] 쌍으로 Area 에 넣기 위해 가공
  const chartData = data.map(d => ({
    ...d,
    date: d.date.slice(5),
    tempRange: d.minTemp != null && d.maxTemp != null ? [d.minTemp, d.maxTemp] as [number, number] : null,
  }));

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
