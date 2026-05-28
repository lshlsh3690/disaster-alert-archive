"use client";

import { Suspense, Fragment, useState, useEffect, useMemo, useCallback, useRef } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useAlertStats, useSidoStats, useSigunguStats, useDailyStats, useHourlyStats, useMonthlyTypeStats } from "@/lib/queries/useAlerts";
import type { DailyStat, HourlyStat, MonthlyTypeStat } from "@/types/alerts";
import { levelTextToCode } from "@/ui/level";

// ─── 타입 ────────────────────────────────────────────────────────────────────

interface TypeStat  { type: string | null;  count: number }
interface LevelStat { level: string | null; count: number }
interface RegionStat { region: string; count: number }

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
}): string {
  const { filters, totalCount, dailyAvg, topType, topRegion, typeStats, regionStats, levelStats, dailyStats } = opts;
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

// ─── 색상 팔레트 ──────────────────────────────────────────────────────────────

const TYPE_COLORS = ["#3b82f6", "#f59e0b", "#f97316", "#a855f7", "#06b6d4", "#9ca3af"];
const BAR_COLORS  = ["#ef4444", "#f97316", "#f59e0b", "#3b82f6", "#06b6d4", "#a855f7", "#6b7280", "#9ca3af"];

const LEVEL_META: Record<string, { text: string; bg: string; textCls: string; border: string; solid: string }> = {
  LEVEL_1: { text: "안전안내", bg: "bg-blue-50",   textCls: "text-blue-700",   border: "border-blue-200",   solid: "#3b82f6" },
  LEVEL_2: { text: "긴급재난", bg: "bg-orange-50", textCls: "text-orange-700", border: "border-orange-200", solid: "#f97316" },
  LEVEL_3: { text: "위급재난", bg: "bg-red-50",    textCls: "text-red-700",    border: "border-red-200",    solid: "#ef4444" },
};

// ─── 상수 ────────────────────────────────────────────────────────────────────

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];
// MySQL DAYOFWEEK: 1=Sun, 2=Mon, ..., 7=Sat → 표시 순서(Mon=0..Sun=6)
// PostgreSQL EXTRACT(DOW): 0=Sun,1=Mon,...,6=Sat → display Mon=0..Sun=6
const DOW_TO_IDX: Record<number, number> = { 1: 0, 2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 0: 6 };
const STACKED_COLORS = ["#3b82f6", "#f59e0b", "#f97316", "#a855f7"];

// ─── 위젯 라이브러리 ──────────────────────────────────────────────────────────

interface LibItem {
  id: string; kind: string; title: string; desc: string; defaultSpan: number; icon: string; sample?: boolean;
}
const WIDGET_LIBRARY: LibItem[] = [
  { id: "type-donut", kind: "donut",   title: "재난 유형 분포",      desc: "유형별 점유율 도넛 차트",        defaultSpan: 4, icon: "🍩" },
  { id: "sido-bar",   kind: "hbar",    title: "시·도 TOP 8",         desc: "지역별 발생 건수 가로 막대",      defaultSpan: 6, icon: "📊" },
  { id: "level-card", kind: "levels",  title: "재난 경보 단계 분포",  desc: "안전/긴급/위급 비율",             defaultSpan: 6, icon: "🚨" },
  { id: "daily-line", kind: "line",    title: "일별 발생 추이",       desc: "날짜별 발생 건수 시계열",        defaultSpan: 8, icon: "📈" },
  { id: "heatmap",    kind: "heatmap", title: "요일·시간대 히트맵",   desc: "발생 빈도 요일×시간대 분석",     defaultSpan: 8, icon: "🔥" },
  { id: "stacked",    kind: "stacked", title: "월별 유형별 발생",     desc: "유형별 월간 누적 발생 추이",     defaultSpan: 4, icon: "🪜" },
  { id: "compare",    kind: "compare", title: "전년 동기 대비",       desc: "월별 발생 건수 YoY 비교",        defaultSpan: 8, icon: "⚖️" },
];

const DEFAULT_LAYOUT: WidgetItem[] = [
  { id: "w1", libId: "type-donut", span: 4 },
  { id: "w2", libId: "sido-bar",   span: 8 },
  { id: "w3", libId: "level-card", span: 6 },
  { id: "w4", libId: "daily-line", span: 6 },
];

interface WidgetItem { id: string; libId: string; span: number }

// ─── KPI 카드 ─────────────────────────────────────────────────────────────────

function KpiBox({ icon, label, value, sub }: { icon: string; label: string; value: string; sub: string }) {
  return (
    <div className="bg-white rounded-xl shadow p-4">
      <div className="flex items-center gap-2 mb-1.5">
        <span className="text-lg">{icon}</span>
        <span className="text-xs text-gray-500 font-medium">{label}</span>
      </div>
      <div className="text-2xl font-bold text-gray-900 tracking-tight leading-tight">{value}</div>
      <div className="text-xs text-gray-400 mt-1">{sub}</div>
    </div>
  );
}

// ─── 차트: 꺾은선 (tooltip 포함) ─────────────────────────────────────────────

function LineChart({ data }: { data: DailyStat[] }) {
  const [hovered, setHovered] = useState<{ idx: number; x: number; y: number } | null>(null);

  const w = 600, h = 200, padL = 32, padR = 12, padT = 16, padB = 24;
  const n = data.length;
  if (n === 0) return <EmptyChart />;

  const max = Math.max(...data.map(d => d.count)) || 1;
  const xs = (i: number) => padL + (i / Math.max(n - 1, 1)) * (w - padL - padR);
  const ys = (v: number) => padT + (1 - v / max) * (h - padT - padB);
  const pts = data.map((d, i) => `${xs(i)},${ys(d.count)}`).join(" ");
  const area = `${xs(0)},${h - padB} ${pts} ${xs(n - 1)},${h - padB}`;
  const peakIdx = data.reduce((p, c, i) => (c.count > data[p].count ? i : p), 0);

  const labelStride = Math.max(1, Math.floor(n / 6));

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const svgX = ((e.clientX - rect.left) / rect.width) * w;
    const idx = Math.min(n - 1, Math.max(0, Math.round((svgX - padL) / ((w - padL - padR) / Math.max(n - 1, 1)))));
    setHovered({ idx, x: xs(idx), y: ys(data[idx].count) });
  };

  const hd = hovered !== null ? data[hovered.idx] : null;
  const tooltipX = hovered ? Math.min(hovered.x + 8, w - 80) : 0;
  const tooltipY = hovered ? Math.max(hovered.y - 28, 4) : 0;

  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none"
      style={{ width: "100%", height: "100%", cursor: "crosshair" }}
      onMouseMove={handleMouseMove} onMouseLeave={() => setHovered(null)}>
      {[0, 0.25, 0.5, 0.75, 1].map(p => (
        <line key={p} x1={padL} x2={w - padR}
          y1={padT + p * (h - padT - padB)} y2={padT + p * (h - padT - padB)}
          stroke="#f3f4f6" strokeWidth="1" />
      ))}
      {[0, 0.5, 1].map(p => (
        <text key={p} x={padL - 4} y={padT + (1 - p) * (h - padT - padB) + 3}
          textAnchor="end" fontSize="9" fill="#9ca3af">{Math.round(max * p)}</text>
      ))}
      <defs>
        <linearGradient id="lineFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.25" />
          <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={area} fill="url(#lineFill)" />
      <polyline points={pts} fill="none" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      {/* 피크 마커 */}
      <circle cx={xs(peakIdx)} cy={ys(data[peakIdx].count)} r="4" fill="#ef4444" stroke="#fff" strokeWidth="1.5" />
      <text x={xs(peakIdx)} y={ys(data[peakIdx].count) - 8} textAnchor="middle" fontSize="9" fontWeight="700" fill="#dc2626">{data[peakIdx].count}</text>
      {/* X 축 날짜 레이블 */}
      {data.map((d, i) => ((i % labelStride === 0 || i === n - 1) ? (
        <text key={i} x={xs(i)} y={h - padB + 14} textAnchor="middle" fontSize="9" fill="#9ca3af">{d.date.slice(5)}</text>
      ) : null))}
      {/* 호버 수직선 + 점 */}
      {hovered && (
        <>
          <line x1={hovered.x} x2={hovered.x} y1={padT} y2={h - padB} stroke="#2563eb" strokeWidth="1" strokeDasharray="3 2" />
          <circle cx={hovered.x} cy={hovered.y} r="5" fill="#2563eb" stroke="#fff" strokeWidth="2" />
          {/* 툴팁 */}
          <rect x={tooltipX} y={tooltipY} width="76" height="26" rx="4" fill="#1e293b" opacity="0.9" />
          <text x={tooltipX + 38} y={tooltipY + 10} textAnchor="middle" fontSize="8" fill="#94a3b8">{hd?.date}</text>
          <text x={tooltipX + 38} y={tooltipY + 21} textAnchor="middle" fontSize="10" fontWeight="700" fill="#fff">{hd?.count.toLocaleString("ko-KR")}건</text>
        </>
      )}
    </svg>
  );
}

// ─── 차트: 도넛 ───────────────────────────────────────────────────────────────

function DonutChart({ data, total }: { data: TypeStat[]; total: number }) {
  const r = 38, c = 2 * Math.PI * r;
  let acc = 0;
  return (
    <div className="flex items-center gap-3 flex-1">
      <svg viewBox="0 0 100 100" width="120" height="120" style={{ flexShrink: 0 }}>
        <circle cx="50" cy="50" r={r} fill="none" stroke="#f3f4f6" strokeWidth="14" />
        {data.map((d, i) => {
          const pct = d.count / total;
          const dash = pct * c;
          const offset = -((acc / total) * c);
          acc += d.count;
          return (
            <circle key={i} cx="50" cy="50" r={r} fill="none"
              stroke={TYPE_COLORS[i % TYPE_COLORS.length]} strokeWidth="14"
              strokeDasharray={`${dash} ${c - dash}`} strokeDashoffset={offset}
              transform="rotate(-90 50 50)" />
          );
        })}
        <text x="50" y="47" textAnchor="middle" fontSize="8" fill="#6b7280">총</text>
        <text x="50" y="60" textAnchor="middle" fontSize="13" fontWeight="700" fill="#111827">{total.toLocaleString("ko-KR")}</text>
      </svg>
      <ul className="flex-1 space-y-1.5">
        {data.map((d, i) => (
          <li key={d.type ?? i} className="flex items-center gap-2 text-xs">
            <span className="w-2 h-2 rounded-sm shrink-0" style={{ background: TYPE_COLORS[i % TYPE_COLORS.length] }} />
            <span className="flex-1 text-gray-700 truncate">{d.type ?? "기타"}</span>
            <span className="text-gray-400">{Math.round((d.count / total) * 100)}%</span>
            <span className="font-semibold text-gray-900 min-w-[36px] text-right">{d.count.toLocaleString("ko-KR")}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// ─── 차트: 가로 막대 ──────────────────────────────────────────────────────────

function HorizontalBar({ data }: { data: RegionStat[] }) {
  const max = Math.max(...data.map(d => d.count)) || 1;
  return (
    <ul className="flex flex-col gap-2 flex-1">
      {data.map((d, i) => (
        <li key={d.region}>
          <div className="flex justify-between text-xs mb-0.5">
            <span className="text-gray-700 font-medium">{i + 1}. {d.region}</span>
            <span className="text-gray-500">{d.count.toLocaleString("ko-KR")}건</span>
          </div>
          <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full rounded-full" style={{ width: `${(d.count / max) * 100}%`, background: BAR_COLORS[i % BAR_COLORS.length] }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

// ─── 차트: 경보 단계 카드 ────────────────────────────────────────────────────

function LevelsCard({ data }: { data: LevelStat[] }) {
  const levels = ["LEVEL_1", "LEVEL_2", "LEVEL_3"].map(code => {
    const meta = LEVEL_META[code];
    const count = data.find(d => d.level === code)?.count ?? 0;
    return { code, ...meta, count };
  });
  const total = levels.reduce((s, l) => s + l.count, 0) || 1;
  const urgentPct = Math.round((levels[2].count / total) * 100);
  return (
    <div className="flex flex-col gap-3 flex-1">
      <div className="flex gap-2">
        {levels.map(l => (
          <div key={l.code} className={`flex-1 text-center rounded-lg border py-2.5 ${l.bg} ${l.border}`}>
            <div className={`text-xs font-semibold mb-1 ${l.textCls}`}>{l.text}</div>
            <div className={`text-xl font-extrabold ${l.textCls}`}>{l.count.toLocaleString("ko-KR")}</div>
            <div className="text-xs text-gray-400">{Math.round((l.count / total) * 100)}%</div>
          </div>
        ))}
      </div>
      <div className="flex h-2.5 rounded-full overflow-hidden">
        {levels.map(l => (
          <div key={l.code} style={{ flex: l.count || 0, background: l.solid }} />
        ))}
      </div>
      <p className="text-xs text-gray-500 leading-relaxed">
        총 <b className="text-gray-900">{total.toLocaleString("ko-KR")}건</b> 중 위급재난이 <b className="text-red-600">{urgentPct}%</b>를 차지합니다.
      </p>
    </div>
  );
}

// ─── 차트: 히트맵 ────────────────────────────────────────────────────────────

function Heatmap({ data }: { data: HourlyStat[] }) {
  const matrix = Array.from({ length: 7 }, () => Array(24).fill(0) as number[]);
  data.forEach(({ dayOfWeek, hour, count }) => {
    const idx = DOW_TO_IDX[dayOfWeek];
    if (idx !== undefined) matrix[idx][hour] = count;
  });
  const max = Math.max(...matrix.flat()) || 1;
  return (
    <div className="flex flex-col gap-2 flex-1">
      <div style={{ display: "grid", gridTemplateColumns: "20px repeat(24, 1fr)", gap: 2 }}>
        <div />
        {Array.from({ length: 24 }).map((_, h) => (
          <div key={h} style={{ fontSize: 8, color: "#9ca3af", textAlign: "center" }}>{h % 3 === 0 ? h : ""}</div>
        ))}
        {matrix.map((row, di) => (
          <Fragment key={di}>
            <div style={{ fontSize: 10, color: "#6b7280", display: "flex", alignItems: "center", justifyContent: "flex-end", paddingRight: 4 }}>{WEEKDAYS[di]}</div>
            {row.map((v, hi) => (
              <div key={hi} style={{ aspectRatio: "1/1", background: v === 0 ? "#f3f4f6" : `rgba(37,99,235,${0.15 + (v / max) * 0.75})`, borderRadius: 2 }} title={`${WEEKDAYS[di]} ${hi}시: ${v}건`} />
            ))}
          </Fragment>
        ))}
      </div>
      <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-1 flex-wrap">
        <span>적음</span>
        {[0.15, 0.3, 0.5, 0.7, 0.9].map(o => (
          <span key={o} className="w-4 h-2.5 rounded-sm inline-block" style={{ background: `rgba(37,99,235,${o})` }} />
        ))}
        <span>많음</span>
      </div>
    </div>
  );
}

// ─── 차트: 월별 유형별 누적 ───────────────────────────────────────────────────

function StackedArea({ data, types }: { data: MonthlyTypeStat[]; types: string[] }) {
  if (data.length === 0 || types.length === 0) return <EmptyChart />;
  const months = [...new Set(data.map(d => d.month))].sort().slice(-12);
  if (months.length === 0) return <EmptyChart />;

  const byMonth = (month: string, type: string) =>
    data.find(d => d.month === month && d.type === type)?.count ?? 0;
  const totals = months.map(m => types.reduce((s, t) => s + byMonth(m, t), 0));
  const max = Math.max(...totals) || 1;

  const w = 400, h = 180, padL = 8, padR = 8, padT = 10, padB = 20;
  const n = months.length;
  const xs = (i: number) => padL + (i / Math.max(n - 1, 1)) * (w - padL - padR);
  const ys = (v: number) => padT + (1 - v / max) * (h - padT - padB);

  const bands = types.map((type, ki) => {
    const top = months.map(m => types.slice(0, ki + 1).reduce((s, t) => s + byMonth(m, t), 0));
    const bot = months.map(m => types.slice(0, ki).reduce((s, t) => s + byMonth(m, t), 0));
    const pts = [
      ...top.map((v, i) => `${xs(i)},${ys(v)}`),
      ...bot.map((v, i) => `${xs(i)},${ys(v)}`).reverse(),
    ].join(" ");
    return { type, color: STACKED_COLORS[ki % STACKED_COLORS.length], pts };
  });

  return (
    <div className="flex flex-col flex-1 gap-2">
      <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" style={{ width: "100%", flex: 1 }}>
        {bands.map(b => <polygon key={b.type} points={b.pts} fill={b.color} fillOpacity="0.85" />)}
        {months.map((m, i) => (i % Math.max(1, Math.floor(n / 5)) === 0 || i === n - 1) && (
          <text key={m} x={xs(i)} y={h - padB + 13} textAnchor="middle" fontSize="9" fill="#9ca3af">
            {`${parseInt(m.slice(5))}월`}
          </text>
        ))}
      </svg>
      <div className="flex gap-3 text-xs text-gray-500 flex-wrap">
        {bands.map(b => (
          <span key={b.type} className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-sm inline-block" style={{ background: b.color }} />{b.type}
          </span>
        ))}
      </div>
    </div>
  );
}

// ─── 차트: 전년 동기 대비 ─────────────────────────────────────────────────────

function CompareBars({ thisYearData, lastYearData, currentYear }: {
  thisYearData: DailyStat[]; lastYearData: DailyStat[]; currentYear: number;
}) {
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  const agg = (data: DailyStat[]) => {
    const m: Record<string, number> = {};
    data.forEach(d => { const k = d.date.slice(5, 7); m[k] = (m[k] ?? 0) + d.count; });
    return m;
  };
  const ty = agg(thisYearData), ly = agg(lastYearData);
  const months = Array.from({ length: 12 }, (_, i) => {
    const key = String(i + 1).padStart(2, "0");
    return { label: `${i + 1}월`, ty: ty[key] ?? 0, ly: ly[key] ?? 0 };
  }).filter(m => m.ty > 0 || m.ly > 0);

  if (months.length === 0) return <EmptyChart />;
  const max = Math.max(...months.map(m => Math.max(m.ty, m.ly))) || 1;
  const totalTy = months.reduce((s, m) => s + m.ty, 0);
  const totalLy = months.reduce((s, m) => s + m.ly, 0);
  const yoy = totalLy > 0 ? Math.round(((totalTy - totalLy) / totalLy) * 100) : null;

  return (
    <div className="flex flex-col gap-3 flex-1">
      <div className="flex-1 flex items-end gap-1" style={{ minHeight: 130 }}>
        {months.map((m, i) => (
          <div
            key={m.label}
            className="flex-1 flex flex-col items-center gap-1 relative"
            onMouseEnter={() => setHoveredIdx(i)}
            onMouseLeave={() => setHoveredIdx(null)}
          >
            {hoveredIdx === i && (
              <div className="absolute bottom-full mb-2 z-10 left-1/2 -translate-x-1/2 bg-gray-800 text-white text-xs rounded px-2 py-1.5 whitespace-nowrap shadow-lg pointer-events-none">
                <div className="flex items-center gap-1.5 mb-0.5">
                  <span className="w-2 h-2 rounded-sm bg-slate-300 inline-block flex-shrink-0" />
                  <span>{currentYear - 1}: {m.ly.toLocaleString("ko-KR")}건</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-sm bg-blue-500 inline-block flex-shrink-0" />
                  <span>{currentYear}: {m.ty.toLocaleString("ko-KR")}건</span>
                </div>
                {m.ly > 0 && (
                  <div className={`text-center mt-1 font-semibold ${m.ty >= m.ly ? "text-red-400" : "text-blue-400"}`}>
                    {m.ty >= m.ly ? `↑ +${Math.round(((m.ty - m.ly) / m.ly) * 100)}%` : `↓ ${Math.round(((m.ty - m.ly) / m.ly) * 100)}%`}
                  </div>
                )}
              </div>
            )}
            <div className="flex items-end gap-0.5 w-full justify-center" style={{ height: 130 }}>
              <div className="w-3 rounded-t-sm bg-slate-300" style={{ height: `${(m.ly / max) * 100}%` }} />
              <div className="w-3 rounded-t-sm bg-blue-600" style={{ height: `${(m.ty / max) * 100}%` }} />
            </div>
            <span className="text-xs text-gray-500">{m.label}</span>
          </div>
        ))}
      </div>
      <div className="flex justify-between items-center text-xs text-gray-500">
        <div className="flex gap-3">
          <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-sm bg-slate-300 inline-block" />{currentYear - 1}</span>
          <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-sm bg-blue-600 inline-block" />{currentYear}</span>
        </div>
        {yoy !== null && (
          <span className={`font-bold ${yoy >= 0 ? "text-red-600" : "text-blue-600"}`}>
            {yoy >= 0 ? `↑ +${yoy}%` : `↓ ${yoy}%`} (YoY)
          </span>
        )}
      </div>
    </div>
  );
}

// ─── 위젯 렌더러 ─────────────────────────────────────────────────────────────

function WidgetContent({ kind, typeStats, regionStats, levelStats, dailyStats, hourlyStats, monthlyTypeStats, thisYearData, lastYearData, currentYear, scrollableRegion, isLoading }: {
  kind: string;
  typeStats: TypeStat[];
  regionStats: RegionStat[];
  levelStats: LevelStat[];
  dailyStats: DailyStat[];
  hourlyStats: HourlyStat[];
  monthlyTypeStats: MonthlyTypeStat[];
  thisYearData: DailyStat[];
  lastYearData: DailyStat[];
  currentYear: number;
  scrollableRegion?: boolean;
  isLoading?: boolean;
}) {
  if (isLoading) return <LoadingChart />;
  const typeTotal = typeStats.reduce((s, d) => s + d.count, 0);
  switch (kind) {
    case "donut":   return typeTotal > 0 ? <DonutChart data={typeStats.slice(0, 6)} total={typeTotal} /> : <EmptyChart />;
    case "hbar":
      if (regionStats.length === 0) return <EmptyChart />;
      if (scrollableRegion) {
        return (
          <div className="overflow-y-auto flex-1" style={{ maxHeight: 320 }}>
            <HorizontalBar data={regionStats} />
          </div>
        );
      }
      return <HorizontalBar data={regionStats.slice(0, 8)} />;
    case "levels":  return <LevelsCard data={levelStats} />;
    case "line":    return <LineChart data={dailyStats} />;
    case "heatmap": return hourlyStats.length > 0 ? <Heatmap data={hourlyStats} /> : <EmptyChart />;
    case "stacked": return <StackedArea data={monthlyTypeStats} types={typeStats.slice(0, 4).map(d => d.type ?? "기타")} />;
    case "compare": return <CompareBars thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear} />;
    default:        return <EmptyChart />;
  }
}

function EmptyChart() {
  return <div className="flex-1 flex items-center justify-center text-sm text-gray-400">데이터 없음</div>;
}

function LoadingChart() {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="w-6 h-6 rounded-full border-2 border-gray-200 border-t-blue-500 animate-spin" />
    </div>
  );
}

// ─── 위젯 카드 래퍼 ──────────────────────────────────────────────────────────

function WidgetCard({ widget, lib, editMode, onResize, onRemove, titleOverride, children }: {
  widget: WidgetItem; lib: LibItem; editMode: boolean;
  onResize: (id: string, span: number) => void;
  onRemove: (id: string) => void;
  titleOverride?: string;
  children: React.ReactNode;
}) {
  const cardRef = useRef<HTMLDivElement>(null);

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
        </div>
        <div className="flex items-center gap-1">
          <button onClick={handleDownloadPng} title="PNG 다운로드"
            className="px-1.5 py-0.5 text-xs border border-gray-200 text-gray-400 rounded hover:border-green-400 hover:text-green-600 transition-colors">
            ⬇
          </button>
          {editMode && (
            <>
              {[4, 6, 8, 12].map(s => (
                <button key={s} onClick={() => onResize(widget.id, s)}
                  className={`px-1.5 py-0.5 text-xs font-semibold rounded border ${widget.span === s ? "border-blue-500 bg-blue-50 text-blue-700" : "border-gray-200 text-gray-400"}`}>
                  {s}
                </button>
              ))}
              <button onClick={() => onRemove(widget.id)}
                className="ml-1 px-1.5 py-0.5 text-xs border border-red-200 text-red-500 rounded">×</button>
            </>
          )}
        </div>
      </div>
      <div className="flex-1 p-3.5 flex flex-col min-h-0">{children}</div>
    </div>
  );
}

// ─── 위젯 라이브러리 드로어 ───────────────────────────────────────────────────

function WidgetLibrary({ open, onClose, layout, onAdd, onRemove }: {
  open: boolean; onClose: () => void; layout: WidgetItem[];
  onAdd: (item: LibItem) => void; onRemove: (id: string) => void;
}) {
  if (!open) return null;
  const usedMap = new Map(layout.map(w => [w.libId, w.id]));
  return (
    <>
      <div className="absolute inset-0 bg-black/30 z-10" onClick={onClose} />
      <aside className="absolute top-0 right-0 bottom-0 w-80 bg-white shadow-2xl z-20 flex flex-col">
        <div className="flex items-center gap-2 px-4 py-3.5 border-b border-gray-200">
          <h3 className="flex-1 text-sm font-bold text-gray-900">위젯 관리</h3>
          <button onClick={onClose} className="w-7 h-7 border border-gray-200 rounded-lg text-gray-400 text-sm">×</button>
        </div>
        <div className="flex-1 overflow-auto p-4 space-y-2">
          {WIDGET_LIBRARY.map(item => {
            const widgetId = usedMap.get(item.id);
            const added = !!widgetId;
            return (
              <div key={item.id} className={`flex items-start gap-2.5 p-3 border rounded-lg transition-colors ${added ? "border-blue-200 bg-blue-50" : "border-gray-200 bg-white"}`}>
                <span className="text-xl leading-none">{item.icon}</span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-bold text-gray-900">{item.title}</span>
                    {added && <span className="text-xs font-bold text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">표시 중</span>}
                    {item.sample && <span className="text-xs text-amber-500">샘플</span>}
                  </div>
                  <div className="text-xs text-gray-400 mt-0.5">{item.desc}</div>
                </div>
                {added ? (
                  <button onClick={() => onRemove(widgetId!)}
                    className="shrink-0 px-2 py-1 text-xs font-semibold border border-red-200 text-red-500 rounded hover:bg-red-50 transition-colors">
                    삭제
                  </button>
                ) : (
                  <button onClick={() => onAdd(item)}
                    className="shrink-0 px-2 py-1 text-xs font-semibold border border-blue-300 text-blue-600 rounded hover:bg-blue-50 transition-colors">
                    추가
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </aside>
    </>
  );
}

// ─── 필터 배너 ───────────────────────────────────────────────────────────────

function FilterBanner({ sido, sigungu, startDate, endDate, type, levelText, keyword, source }: {
  sido?: string; sigungu?: string; startDate?: string; endDate?: string;
  type?: string; levelText?: string; keyword?: string; source?: string;
}) {
  const tags = [
    sido && { label: sido },
    sigungu && { label: sigungu },
    (startDate || endDate) && { label: `${startDate ?? "~"} ~ ${endDate ?? "~"}` },
    type && { label: `유형: ${type}` },
    levelText && { label: `레벨: ${levelText}` },
    keyword && { label: `"${keyword}"` },
    source && source !== "ALL" && { label: source === "OFFICIAL" ? "공식만" : "제보만" },
  ].filter(Boolean) as { label: string }[];

  const alertsHref = `/alerts?${[
    sido && `sido=${encodeURIComponent(sido)}`,
    sigungu && `sigungu=${encodeURIComponent(sigungu)}`,
    startDate && `startDate=${startDate}`,
    endDate && `endDate=${endDate}`,
    type && `type=${encodeURIComponent(type)}`,
    levelText && `levelText=${encodeURIComponent(levelText)}`,
    keyword && `keyword=${encodeURIComponent(keyword)}`,
    source && `source=${source}`,
  ].filter(Boolean).join("&")}`;

  if (tags.length === 0) {
    return (
      <div className="bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm text-gray-500">
        전체 데이터를 보여주고 있어요.{" "}
        <Link href="/alerts" className="text-blue-600 font-semibold hover:underline">재난문자 페이지에서 필터링 →</Link>
      </div>
    );
  }

  return (
    <div className="bg-white border-l-4 border-blue-500 rounded-xl shadow px-4 py-2.5 flex items-center gap-2 flex-wrap">
      <span className="text-xs font-bold text-gray-800">
        <svg className="inline mr-1" style={{ verticalAlign: "-2px" }} width="13" height="13" viewBox="0 0 14 14" fill="none">
          <path d="M2 3h10l-3.5 4.5V12L5.5 10.5V7.5L2 3z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        </svg>
        재난문자 필터 적용 중
      </span>
      {tags.map((t, i) => (
        <span key={i} className="px-2 py-0.5 rounded-full bg-blue-100 text-blue-800 text-xs font-semibold">{t.label}</span>
      ))}
      <span className="flex-1" />
      <Link href={alertsHref} className="text-xs text-blue-600 font-semibold hover:underline">↗ 필터 변경</Link>
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

  const { data: stats,       isLoading: loadingStats }    = useAlertStats(statsParams);
  const { data: sidoStats,   isLoading: loadingSido }     = useSidoStats(statsParams);
  const { data: sigunguStats, isLoading: loadingSigungu } = useSigunguStats(
    { ...statsParams, region: sido },
    !!sido
  );

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

  const { data: dailyStatsRaw, isLoading: loadingDaily } = useDailyStats(statsParams);
  const dailyStats: DailyStat[] = dailyStatsRaw ?? [];

  const { data: hourlyStatsRaw, isLoading: loadingHourly }       = useHourlyStats(statsParams);
  const { data: monthlyTypeRaw, isLoading: loadingMonthlyType }   = useMonthlyTypeStats(statsParams);
  const hourlyStats: HourlyStat[]         = hourlyStatsRaw ?? [];
  const monthlyTypeStats: MonthlyTypeStat[] = monthlyTypeRaw ?? [];

  const currentYear = useMemo(() => new Date().getFullYear(), []);
  const compareBase = useMemo(() => ({ ...statsParams, startDate: undefined, endDate: undefined }), [statsParams]);
  const { data: thisYearRaw, isLoading: loadingThisYear } = useDailyStats({ ...compareBase, startDate: `${currentYear}-01-01`, endDate: `${currentYear}-12-31` });
  const { data: lastYearRaw, isLoading: loadingLastYear } = useDailyStats({ ...compareBase, startDate: `${currentYear - 1}-01-01`, endDate: `${currentYear - 1}-12-31` });
  const thisYearData: DailyStat[] = thisYearRaw ?? [];
  const lastYearData: DailyStat[] = lastYearRaw ?? [];

  const topType   = typeStats[0];
  const topRegion = regionStats[0];
  const totalCount = stats?.totalCount ?? 0;
  const periodDays = useMemo(() => {
    if (startDate && endDate) {
      const diff = (new Date(endDate).getTime() - new Date(startDate).getTime()) / 86_400_000 + 1;
      return Math.max(1, Math.round(diff));
    }
    return 30;
  }, [startDate, endDate]);
  const dailyAvg = Math.round(totalCount / periodDays);

  const [layout, setLayout] = useState<WidgetItem[]>(DEFAULT_LAYOUT);

  useEffect(() => {
    try {
      const saved = sessionStorage.getItem("stats-widget-layout");
      if (saved) setLayout(JSON.parse(saved) as WidgetItem[]);
    } catch {}
  }, []);
  const [editMode, setEditMode] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const updateLayout = useCallback((updater: (l: WidgetItem[]) => WidgetItem[]) => {
    setLayout(l => {
      const next = updater(l);
      try { sessionStorage.setItem("stats-widget-layout", JSON.stringify(next)); } catch {}
      return next;
    });
  }, []);

  const handleResize = useCallback((id: string, span: number) => {
    updateLayout(l => l.map(w => w.id === id ? { ...w, span } : w));
  }, [updateLayout]);
  const handleRemove = useCallback((id: string) => {
    updateLayout(l => l.filter(w => w.id !== id));
  }, [updateLayout]);
  const handleAdd = useCallback((item: LibItem) => {
    updateLayout(l => [...l, { id: `w${Date.now()}`, libId: item.id, span: item.defaultSpan }]);
    setDrawerOpen(false);
  }, [updateLayout]);


  return (
    <main className="p-3 sm:p-6 space-y-4 relative">
      {/* 페이지 헤더 */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold">재난 통계</h1>
          <p className="text-sm text-gray-500">재난문자 페이지에서 필터링한 데이터를 다양한 차트로 분석합니다.</p>
        </div>
        <div className="flex gap-2 shrink-0">
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
            const csv = buildCsv({ filters, totalCount, dailyAvg, topType, topRegion, typeStats, regionStats, levelStats, dailyStats });
            const datePart = new Date().toISOString().slice(0, 10);
            downloadCsv(`재난통계_${datePart}.csv`, csv);
          }} className="px-3 py-2 text-sm font-semibold rounded-lg border border-gray-200 bg-white text-gray-700 hover:border-green-400 hover:text-green-600 transition-colors flex items-center gap-1">
            ⬇ CSV
          </button>
          <button onClick={() => setEditMode(e => !e)}
            className={`px-3 py-2 text-sm font-semibold rounded-lg border ${editMode ? "bg-indigo-50 text-indigo-700 border-indigo-200" : "bg-white text-gray-700 border-gray-200"}`}>
            {editMode ? "✓ 편집 모드" : "✏ 편집"}
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
            <WidgetCard key={w.id} widget={w} lib={lib} editMode={editMode} onResize={handleResize} onRemove={handleRemove}
              titleOverride={lib.id === "sido-bar" && sido ? `${sido} 시·군·구별 발생 건수` : undefined}>
              <WidgetContent kind={lib.kind} typeStats={typeStats} regionStats={regionStats} levelStats={levelStats}
                dailyStats={dailyStats} hourlyStats={hourlyStats} monthlyTypeStats={monthlyTypeStats}
                thisYearData={thisYearData} lastYearData={lastYearData} currentYear={currentYear}
                scrollableRegion={lib.id === "sido-bar" && !!sido}
                isLoading={
                  lib.kind === "line"    ? loadingDaily :
                  lib.kind === "hbar"    ? (sido ? loadingSigungu : loadingSido) :
                  lib.kind === "heatmap" ? loadingHourly :
                  lib.kind === "stacked" ? loadingMonthlyType :
                  lib.kind === "compare" ? (loadingThisYear || loadingLastYear) :
                  loadingStats
                } />
            </WidgetCard>
          );
        })}
        {editMode && (
          <button onClick={() => setDrawerOpen(true)}
            className="col-span-12 min-h-14 border-2 border-dashed border-gray-300 rounded-xl text-sm font-semibold text-gray-400 flex items-center justify-center gap-1 hover:border-blue-400 hover:text-blue-500 transition-colors">
            <span className="text-lg">+</span> 위젯 추가하기
          </button>
        )}
      </div>

      {/* 안내 */}
      <div className="bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-xs text-blue-800 leading-relaxed">
        💡 <b>편집 모드</b>에서 위젯 크기(4·6·8·12열)를 조정하거나 삭제할 수 있어요.
        <b className="ml-1">샘플</b> 표시 위젯은 시각화 형태만 제공하며 실제 시계열 데이터는 준비 중입니다.
        <Link href="/alerts" className="ml-2 underline font-semibold">재난문자 페이지에서 필터 설정 →</Link>
      </div>

      {/* 위젯 드로어 */}
      <WidgetLibrary open={drawerOpen} onClose={() => setDrawerOpen(false)} layout={layout} onAdd={handleAdd} onRemove={handleRemove} />
    </main>
  );
}
