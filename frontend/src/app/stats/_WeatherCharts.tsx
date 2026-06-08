"use client";

/**
 * _WeatherCharts.tsx
 *
 * 날씨와 재난 발생의 상관관계를 시각화하는 차트 컴포넌트 모음입니다.
 *
 * 포함된 컴포넌트:
 *   - WeatherByTypeChart   : 재난 유형별 일별 누적 막대 + 평균기온 꺾은선
 *   - WeatherByRegionChart : 지역별 일별 누적 막대 + 평균기온 꺾은선
 *   - WeatherOverlayChart  : 총 발생건수·기온범위·강수량 복합 차트
 *   - WeatherCorrelationScatter : 기온(X) vs 발생건수(Y) 버블 산점도
 *
 * 공통 특징:
 *   - 날짜 수가 많으면 일별→주별→월별로 자동 집계해 막대 수를 제한 (getAggMode)
 *   - 1.2초 이상 호버 시 툴팁이 고정(pin)되는 인터랙션 (LoadingDonut → 고정 팝업)
 */

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
import { LoadingDonut } from "@/components/ui/LoadingDonut";

// ─── 날짜 집계 헬퍼 ──────────────────────────────────────────────────────────

type AggMode = "daily" | "weekly" | "monthly";

// 날짜 수에 따라 집계 단위를 결정 (60일 이하: 일별, 181일 이상: 월별)
function getAggMode(dateCount: number): AggMode {
  if (dateCount <= 60) return "daily";
  if (dateCount <= 180) return "weekly";
  return "monthly";
}

// 날짜 문자열을 집계 단위의 대표 키로 변환
// weekly: 해당 날짜가 속한 주의 월요일 날짜를 키로 사용
function getAggKey(date: string, mode: AggMode): string {
  if (mode === "daily") return date;
  if (mode === "monthly") return date.slice(0, 7);
  const d = new Date(date);
  const dow = d.getDay();
  d.setDate(d.getDate() - (dow === 0 ? 6 : dow - 1));
  return d.toISOString().slice(0, 10);
}

// 집계 키를 차트 X축 레이블로 변환 (월별: "7월", 나머지: "MM-DD")
function fmtKey(key: string, mode: AggMode): string {
  if (mode === "monthly") return `${parseInt(key.slice(5))}월`;
  return key.slice(5);
}

// 집계 단위 안내 텍스트 (차트 우상단에 표시)
function aggModeLabel(mode: AggMode): string {
  if (mode === "weekly") return "주별 집계";
  if (mode === "monthly") return "월별 집계";
  return "";
}

// ─── 공통 툴팁 타입 · 스타일 ─────────────────────────────────────────────────

type TTProps = {
  active?: boolean;
  payload?: Array<{ value?: number; dataKey?: string; payload?: Record<string, number> }>;
  label?: string;
};

// 툴팁 박스 공통 스타일
const TT_BOX: React.CSSProperties = { background: "#1e293b", borderRadius: 6, padding: "6px 10px", border: "none" };
// 툴팁 내 레이블(날짜, 지역명 등) 스타일
const TT_LABEL: React.CSSProperties = { color: "#94a3b8", fontSize: 10, margin: "0 0 2px 0" };
// 툴팁 내 강조 수치 스타일
const TT_VALUE: React.CSSProperties = { color: "#fff", fontSize: 12, fontWeight: 700, margin: 0 };

// ─── 날씨·유형별 누적 막대 ───────────────────────────────────────────────────

export function WeatherByTypeChart({ data }: { data: WeatherTypeStat[] }) {
  // 툴팁 핀 로딩 진행률 (0~100)
  const [progress, setProgress] = useState(0);
  // 툴팁 고정 여부
  const [pinned, setPinned] = useState(false);
  // 고정된 시점의 툴팁 데이터 스냅샷
  const [pinnedSnap, setPinnedSnap] = useState<{ label: string; payload: TTProps["payload"] } | null>(null);
  // 1.2초 카운트다운 인터벌 ref
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 마지막으로 호버된 툴팁 데이터 (핀 시점에 스냅샷으로 복사됨)
  const lastPayloadRef = useRef<{ label: string; payload: TTProps["payload"] } | null>(null);
  // 마우스가 차트 컨테이너 안에 있는지 여부 (X 버튼 클릭 후 재시작 판단용)
  const isHoveringRef = useRef(false);
  useEffect(() => () => { if (intervalRef.current) clearInterval(intervalRef.current); }, []);

  // 1.2초 타이머 실행 (50ms 간격으로 progress 업데이트, 완료 시 핀 고정)
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
  // 아직 고정되지 않은 경우에만 타이머 시작
  const startTimer = () => { if (!pinned) runTimer(); };
  // 마우스가 나가면 타이머 중단, 미고정 상태면 progress 초기화
  const stopTimer = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (!pinned) setProgress(0);
  };
  // X 버튼: 고정 해제 후 마우스가 안에 있으면 타이머 즉시 재시작
  const handleClose = () => {
    setPinned(false);
    setPinnedSnap(null);
    setProgress(0);
    if (isHoveringRef.current) runTimer();
  };

  if (data.length === 0) return <EmptyChart />;

  // 날짜별로 쪼개진 data를 유형 단위로 합산 → 상위 6개만 차트에 표시
  const typeTotals = new Map<string, number>();
  data.forEach(d => typeTotals.set(d.type ?? "기타", (typeTotals.get(d.type ?? "기타") ?? 0) + d.count));
  const types = [...typeTotals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6).map(([t]) => t);

  // 중복 제거한 날짜 목록 → 집계 단위 결정
  const allDates = [...new Set(data.map(d => d.date))].sort();
  const mode = getAggMode(allDates.length);

  // 날짜별 평균 기온 (날짜 중복 방지: 첫 번째 값만 기록)
  const dateTemp = new Map<string, number | null>();
  data.forEach(d => { if (!dateTemp.has(d.date)) dateTemp.set(d.date, d.avgTemp ?? null); });

  // 집계 키별 유형 건수 (key → type → count)
  const keyTypeCount = new Map<string, Map<string, number>>();
  // 집계 키에 속한 원본 날짜 목록 (기온 평균 계산에 사용)
  const keyDates = new Map<string, Set<string>>();
  data.forEach(d => {
    const key = getAggKey(d.date, mode);
    const type = d.type ?? "기타";
    if (!keyTypeCount.has(key)) keyTypeCount.set(key, new Map());
    keyTypeCount.get(key)!.set(type, (keyTypeCount.get(key)!.get(type) ?? 0) + d.count);
    if (!keyDates.has(key)) keyDates.set(key, new Set());
    keyDates.get(key)!.add(d.date);
  });

  // 정렬된 집계 키 목록
  const keys = [...keyTypeCount.keys()].sort();
  // X축 레이블이 겹치지 않도록 표시 간격 (최대 6개 레이블 기준)
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  // Recharts에 넘길 피벗 데이터: 각 행이 하나의 집계 기간, 열이 유형별 건수 + 평균기온
  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = keys.map(key => {
    const typeMap = keyTypeCount.get(key)!;
    // 해당 집계 기간에 속한 날짜들의 기온 평균
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

  // 집계 키별 통계 누산 버퍼
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

  // 정렬된 집계 키 목록
  const keys = [...aggMap.keys()].sort();
  // X축 레이블 표시 간격
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  // Recharts에 넘길 최종 데이터: 기간별 건수·평균기온·기온범위·강수·주요유형
  const chartData = keys.map(key => {
    const e = aggMap.get(key)!;
    // 해당 기간의 평균 기온 (소수점 1자리)
    const avgTemp = e.tempN > 0 ? Math.round(e.tempSum / e.tempN * 10) / 10 : null;
    // 해당 기간에서 가장 많이 발생한 재난 유형
    const primaryType = e.typeCount.size > 0 ? [...e.typeCount.entries()].sort((a, b) => b[1] - a[1])[0][0] : null;
    return {
      date: fmtKey(key, mode),
      count: e.count,
      avgTemp,
      minTemp: e.minTemp,
      maxTemp: e.maxTemp,
      maxPrecip: e.maxPrecip,
      primaryType,
      // tempRange: Area 차트로 기온 범위(최저~최고)를 띠 형태로 표현하기 위한 배열값
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
  // 범례 클릭으로 숨긴 유형 집합 (겹침이 심할 때 유형을 켜고 끄며 분리해 보기)
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const toggleType = (name: string) => setHidden(prev => {
    const next = new Set(prev);
    if (next.has(name)) next.delete(name); else next.add(name);
    return next;
  });

  // 기온 데이터가 없는 항목은 산점도에서 제외
  const filtered = data.filter(d => d.avgTemp != null);
  if (filtered.length === 0) return <EmptyChart />;

  // 등장하는 재난 유형 목록 (중복 제거)
  const types = [...new Set(filtered.map(d => d.primaryType ?? "기타"))];
  // 유형별 색상 매핑
  const colorMap: Record<string, string> = {};
  types.forEach((t, i) => { colorMap[t] = TYPE_COLORS[i % TYPE_COLORS.length]; });

  // 유형별로 그룹핑된 산점도 데이터
  // x: 평균기온, y: 발생건수, z: 강수량(버블 크기, 0 방지를 위해 +1)
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
          {/* 버블 크기 범위를 줄여 점들이 서로 덜 겹치도록 함 */}
          <ZAxis type="number" dataKey="z" range={[20, 120]} />
          <Tooltip content={<TooltipContent />} cursor={{ strokeDasharray: "3 3" }} />
          {/* 범례 클릭 시 해당 유형 표시/숨김 토글 (숨긴 유형은 회색 처리) */}
          <Legend wrapperStyle={{ fontSize: 11, cursor: "pointer" }}
            onClick={(e: { value?: string }) => toggleType(String(e.value ?? ""))}
            formatter={(value: string) => (
              <span style={{ color: hidden.has(value) ? "#cbd5e1" : "#6b7280" }}>{value}</span>
            )} />
          {grouped.map(g => (
            // 투명도를 낮추고 흰 테두리를 더해 겹쳐도 개별 점 윤곽이 구분되게 함
            <Scatter key={g.name} name={g.name} data={g.points} fill={g.color}
              fillOpacity={0.45} stroke="#fff" strokeWidth={0.5} hide={hidden.has(g.name)} />
          ))}
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
}
