"use client";

/**
 * _TimeCharts.tsx
 *
 * "시간 흐름"에 따른 데이터를 보여주는 차트 컴포넌트 모음입니다.
 * 일별·월별·시간대별 추이, 히트맵, 전년 대비 비교 등이 여기에 속합니다.
 *
 * 포함된 컴포넌트:
 *   - LineChart      : 일별 발생 건수 꺾은선(면적) 차트
 *   - DailyBar       : 일별 발생 건수 SVG 막대 차트
 *   - Heatmap        : 요일×시간대 히트맵
 *   - DayOfWeekBar   : 요일별 집계 막대
 *   - HourBar        : 시간대별 집계 막대
 *   - StackedArea    : 월별 유형별 누적 면적 차트
 *   - StackedBar     : 월별 유형별 누적 막대 차트
 *   - CompareBars    : 전년 동기 대비 막대 차트
 *   - CompareLines   : 전년 동기 대비 꺾은선 차트
 */

import { Fragment, useState } from "react";
import {
  BarChart, Bar, Cell,         // 막대 차트
  AreaChart, Area,             // 면적(영역) 차트
  XAxis, YAxis,                // 가로·세로 축
  CartesianGrid,               // 배경 격자선
  Tooltip,                     // 마우스 호버 팝업
  ReferenceLine,               // 평균선 같은 기준선
  Legend,                      // 범례
  ResponsiveContainer,         // 부모 크기에 맞춘 자동 리사이즈
} from "recharts";
import type { DailyStat, HourlyStat, MonthlyTypeStat } from "@/types/alerts";
import { STACKED_COLORS, CHART_COLORS, DOW_TO_IDX, WEEKDAYS } from "./_constants";
import { EmptyChart } from "./_charts";
import { VerticalBar } from "./_DistributionCharts";

// ─── 공통 툴팁 스타일 ─────────────────────────────────────────────────────────

const TT_BOX: React.CSSProperties = {
  background: "#1e293b",
  borderRadius: 6,
  padding: "6px 10px",
  border: "none",
};
const TT_LABEL: React.CSSProperties = { color: "#94a3b8", fontSize: 10, margin: "0 0 2px 0" };
const TT_VALUE: React.CSSProperties = { color: "#fff", fontSize: 12, fontWeight: 700, margin: 0 };

// Recharts가 content 컴포넌트에 넘겨주는 props 타입
type TTProps = {
  active?: boolean;
  payload?: Array<{
    value?: number;
    dataKey?: string;
    payload?: Record<string, number>;
  }>;
  label?: string;
};

// ─── 일별 꺾은선(면적) 차트 ─────────────────────────────────────────────────

/**
 * LineChart
 *
 * 날짜별 재난문자 발생 건수를 면적(AreaChart)으로 표현합니다.
 * 최고점을 빨간 점으로 강조하고, 평균값을 점선으로 보여줍니다.
 *
 * @param data - { date: "YYYY-MM-DD", count: number }[] 배열
 *
 * 참고: 이름은 LineChart이지만 내부적으로 Recharts의 AreaChart를 씁니다.
 *       (그라디언트 면적 채우기를 위해)
 */
export function LineChart({ data }: { data: DailyStat[] }) {
  if (data.length === 0) return <EmptyChart />;

  // 평균 건수: 모든 값을 더해서 개수로 나눕니다
  const avg = Math.round(data.reduce((s, d) => s + d.count, 0) / data.length);

  // 최고값 인덱스: 빨간 점을 찍을 위치를 찾습니다
  const peakIdx = data.reduce((p, c, i) => (c.count > data[p].count ? i : p), 0);

  // X축 레이블이 너무 많으면 겹치므로, 최대 6개만 보여줍니다
  const labelStride = Math.max(1, Math.floor(data.length / 6));

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString("ko-KR")}건</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0" style={{ height: "100%" }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
          {/* SVG 그라디언트 정의: 면적 채우기에 사용 */}
          <defs>
            <linearGradient id="lineGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.25} />
              <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
            </linearGradient>
          </defs>

          <CartesianGrid vertical={false} stroke="#f3f4f6" />

          {/* tickFormatter: "2024-07-15" → "07-15" 로 짧게 표시 */}
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }}
            tickFormatter={(v: string) => v.slice(5)}
            interval={labelStride - 1} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />

          <Tooltip content={<TooltipContent />} />

          {/* 평균값 점선 기준선 */}
          <ReferenceLine y={avg} stroke="#f97316" strokeDasharray="3 2" />

          <Area
            type="monotone"
            dataKey="count"
            stroke="#2563eb"
            strokeWidth={2}
            fill="url(#lineGrad)"
            // dot prop: 최고점에만 빨간 원을 그립니다
            dot={(props: { cx?: number; cy?: number; index: number }) => {
              const { cx, cy, index } = props;
              if (index !== peakIdx || cx == null || cy == null) return <g key={index} />;
              return (
                <circle key={index} cx={cx} cy={cy} r={5}
                  fill="#ef4444" stroke="#fff" strokeWidth={2} />
              );
            }}
            activeDot={{ r: 4, fill: "#2563eb" }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── 일별 막대 차트 ──────────────────────────────────────────────────────────

/**
 * DailyBar
 *
 * 날짜별 건수를 Recharts BarChart로 표현합니다.
 * ResponsiveContainer를 사용해 LineChart와 동일한 높이 제약을 받습니다.
 *
 * @param data - { date: "YYYY-MM-DD", count: number }[] 배열
 */
export function DailyBar({ data }: { data: DailyStat[] }) {
  if (data.length === 0) return <EmptyChart />;

  const labelStride = Math.max(1, Math.floor(data.length / 6));

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString("ko-KR")}건</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0" style={{ height: "100%" }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
          <CartesianGrid vertical={false} stroke="#f3f4f6" />
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }}
            tickFormatter={(v: string) => v.slice(5)}
            interval={labelStride - 1} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} />
          <Bar dataKey="count" fill="#3b82f6" radius={[2, 2, 0, 0]} maxBarSize={40} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ─── 요일×시간대 히트맵 ──────────────────────────────────────────────────────

/**
 * Heatmap
 *
 * 요일(행) × 시간대(열) 교차점에 재난 발생 빈도를 색 농도로 표현합니다.
 * 파란색이 진할수록 발생 건수가 많습니다.
 *
 * @param data - { dayOfWeek: number, hour: number, count: number }[] 배열
 *              dayOfWeek은 PostgreSQL EXTRACT(DOW) 기준: 0=일요일, 1=월요일 ... 6=토요일
 */
export function Heatmap({ data }: { data: HourlyStat[] }) {
  // tooltip: 현재 마우스가 올라간 셀의 위치·값 정보
  const [tooltip, setTooltip] = useState<{
    di: number; hi: number; v: number; x: number; y: number
  } | null>(null);

  // 7×24 배열 초기화: matrix[요일인덱스][시간] = 건수
  const matrix = Array.from({ length: 7 }, () => Array(24).fill(0) as number[]);
  data.forEach(({ dayOfWeek, hour, count }) => {
    // DOW_TO_IDX: PostgreSQL 요일번호 → 화면 표시 순서(월=0 ... 일=6)로 변환
    const idx = DOW_TO_IDX[dayOfWeek];
    if (idx !== undefined) matrix[idx][hour] = count;
  });

  const max = Math.max(...matrix.flat()) || 1;

  return (
    <div className="flex flex-col gap-2 flex-1 relative">
      {/* CSS Grid: 첫 열은 요일 레이블(20px), 나머지 24열은 균등 분배 */}
      <div style={{ display: "grid", gridTemplateColumns: "20px repeat(24, 1fr)", gap: 2 }}>
        {/* 시간대 헤더 (0, 3, 6, ... 21시만 표시) */}
        <div />
        {Array.from({ length: 24 }).map((_, h) => (
          <div key={h} style={{ fontSize: 8, color: "#9ca3af", textAlign: "center" }}>
            {h % 3 === 0 ? h : ""}
          </div>
        ))}

        {/* 요일별 행 */}
        {matrix.map((row, di) => (
          <Fragment key={di}>
            {/* 요일 레이블 */}
            <div style={{
              fontSize: 10, color: "#6b7280",
              display: "flex", alignItems: "center",
              justifyContent: "flex-end", paddingRight: 4,
            }}>
              {WEEKDAYS[di]}
            </div>

            {/* 시간대별 셀 */}
            {row.map((v, hi) => (
              <div
                key={hi}
                style={{
                  height: 22,
                  // 건수가 0이면 회색, 있으면 파란색(농도는 비율에 비례)
                  background: v === 0
                    ? "#f3f4f6"
                    : `rgba(37,99,235,${0.15 + (v / max) * 0.75})`,
                  borderRadius: 2,
                  cursor: "pointer",
                }}
                onMouseEnter={e => {
                  // 셀 위치를 부모(.relative) 기준 좌표로 계산합니다
                  const rect = (e.target as HTMLElement).getBoundingClientRect();
                  const parent = (e.target as HTMLElement)
                    .closest(".relative")!.getBoundingClientRect();
                  setTooltip({
                    di, hi, v,
                    x: rect.left - parent.left + rect.width / 2,
                    y: rect.top - parent.top,
                  });
                }}
                onMouseLeave={() => setTooltip(null)}
              />
            ))}
          </Fragment>
        ))}
      </div>

      {/* 툴팁 팝업 */}
      {tooltip && (
        <div
          className="absolute z-10 bg-gray-800 text-white text-xs rounded px-2 py-1.5 shadow-lg pointer-events-none whitespace-nowrap -translate-x-1/2 -translate-y-full"
          style={{ left: tooltip.x, top: tooltip.y - 6 }}
        >
          <div className="font-semibold">{WEEKDAYS[tooltip.di]} {tooltip.hi}시</div>
          <div className="text-gray-300">{tooltip.v.toLocaleString("ko-KR")}건</div>
        </div>
      )}

      {/* 색상 범례: 적음 → 많음 */}
      <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-1 flex-wrap">
        <span>적음</span>
        {[0.15, 0.3, 0.5, 0.7, 0.9].map(o => (
          <span key={o} className="w-4 h-2.5 rounded-sm inline-block"
            style={{ background: `rgba(37,99,235,${o})` }} />
        ))}
        <span>많음</span>
      </div>
    </div>
  );
}

// ─── 요일별 집계 막대 ────────────────────────────────────────────────────────

/**
 * DayOfWeekBar
 *
 * 히트맵 데이터를 요일별로 합산해 세로 막대 차트로 보여줍니다.
 * HourlyStat[] 원본 데이터를 받아 요일 차원으로 집계합니다.
 *
 * @param data - useHourlyStats()가 반환하는 HourlyStat[] 그대로 넘기면 됩니다
 */
export function DayOfWeekBar({ data }: { data: HourlyStat[] }) {
  // agg: { 요일번호: 총건수 } 형태로 집계
  const agg: Record<number, number> = {};
  data.forEach(({ dayOfWeek, count }) => {
    agg[dayOfWeek] = (agg[dayOfWeek] ?? 0) + count;
  });

  // WEEKDAYS 순서(월~일)에 맞게 배열로 변환
  const bars = WEEKDAYS.map((label, di) => {
    // DOW_TO_IDX의 역방향: 화면 인덱스(0=월) → PostgreSQL DOW 번호 찾기
    const dow = Number(
      Object.entries(DOW_TO_IDX).find(([, idx]) => idx === di)?.[0] ?? -1
    );
    return { label, count: agg[dow] ?? 0 };
  });

  if (bars.every(b => b.count === 0)) return <EmptyChart />;
  // VerticalBar는 _DistributionCharts.tsx에서 가져옵니다
  return <VerticalBar data={bars} />;
}

// ─── 시간대별 집계 막대 ──────────────────────────────────────────────────────

/**
 * HourBar
 *
 * 히트맵 데이터를 시간대별로 합산해 세로 막대 차트로 보여줍니다.
 *
 * @param data - useHourlyStats()가 반환하는 HourlyStat[] 그대로 넘기면 됩니다
 */
export function HourBar({ data }: { data: HourlyStat[] }) {
  const agg: Record<number, number> = {};
  data.forEach(({ hour, count }) => {
    agg[hour] = (agg[hour] ?? 0) + count;
  });

  // 0시 ~ 23시 24개 막대
  const bars = Array.from({ length: 24 }, (_, h) => ({
    label: `${h}시`,
    count: agg[h] ?? 0,
  }));

  if (bars.every(b => b.count === 0)) return <EmptyChart />;
  return <VerticalBar data={bars} />;
}

// ─── 월별 유형별 누적 면적 차트 ─────────────────────────────────────────────

/**
 * StackedArea
 *
 * 최근 12개월의 재난 발생을 유형별 누적 면적으로 표현합니다.
 * 순수 SVG polygon으로 그립니다.
 *
 * @param data  - useMonthlyTypeStats()에서 반환하는 MonthlyTypeStat[] 배열
 * @param types - 표시할 유형 목록 (보통 상위 4개)
 *
 * 핵심 로직 (Stacked Area 원리):
 *   각 유형의 "상단 좌표"는 자신 + 아래 모든 유형의 합입니다.
 *   이 상단 좌표와 아래 유형의 상단 좌표를 연결하면 면적이 됩니다.
 */
export function StackedArea({
  data,
  types,
}: {
  data: MonthlyTypeStat[];
  types: string[];
}) {
  if (data.length === 0 || types.length === 0) return <EmptyChart />;

  // 최근 12개월만 사용
  const months = [...new Set(data.map(d => d.month))].sort().slice(-12);
  if (months.length === 0) return <EmptyChart />;

  // 특정 월·유형의 건수를 찾는 헬퍼 함수
  const byMonth = (month: string, type: string) =>
    data.find(d => d.month === month && d.type === type)?.count ?? 0;

  // 월별 전체 합계 (Y축 최댓값 계산용)
  const totals = months.map(m => types.reduce((s, t) => s + byMonth(m, t), 0));
  const max = Math.max(...totals) || 1;

  // SVG 좌표 설정
  const w = 400, h = 180, padL = 8, padR = 8, padT = 10, padB = 20;
  const n = months.length;
  // xs(i): i번째 월의 X좌표
  const xs = (i: number) => padL + (i / Math.max(n - 1, 1)) * (w - padL - padR);
  // ys(v): 값 v의 Y좌표 (위로 갈수록 Y가 작아지는 SVG 좌표계)
  const ys = (v: number) => padT + (1 - v / max) * (h - padT - padB);

  // 각 유형의 누적 면적 polygon 좌표 계산
  const bands = types.map((type, ki) => {
    // 이 유형의 상단: 자신 포함 0~ki번째 유형의 합
    const top = months.map(m =>
      types.slice(0, ki + 1).reduce((s, t) => s + byMonth(m, t), 0)
    );
    // 이 유형의 하단: 자신 제외 0~(ki-1)번째 유형의 합
    const bot = months.map(m =>
      types.slice(0, ki).reduce((s, t) => s + byMonth(m, t), 0)
    );
    // 위쪽 경계 좌→우, 아래쪽 경계 우→좌로 polygon 포인트 연결
    const pts = [
      ...top.map((v, i) => `${xs(i)},${ys(v)}`),
      ...bot.map((v, i) => `${xs(i)},${ys(v)}`).reverse(),
    ].join(" ");
    return { type, color: STACKED_COLORS[ki % STACKED_COLORS.length], pts };
  });

  return (
    <div className="flex flex-col flex-1 gap-2">
      <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none"
        style={{ width: "100%", flex: 1 }}>
        {bands.map(b => (
          <polygon key={b.type} points={b.pts} fill={b.color} fillOpacity="0.85" />
        ))}
        {/* X축 월 레이블 */}
        {months.map((m, i) =>
          (i % Math.max(1, Math.floor(n / 5)) === 0 || i === n - 1) && (
            <text key={m} x={xs(i)} y={h - padB + 13}
              textAnchor="middle" fontSize="9" fill="#9ca3af">
              {`${parseInt(m.slice(5))}월`}
            </text>
          )
        )}
      </svg>

      {/* 범례 */}
      <div className="flex gap-3 text-xs text-gray-500 flex-wrap">
        {bands.map(b => (
          <span key={b.type} className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-sm inline-block"
              style={{ background: b.color }} />
            {b.type}
          </span>
        ))}
      </div>
    </div>
  );
}

// ─── 월별 유형별 누적 막대 차트 ─────────────────────────────────────────────

/**
 * StackedBar
 *
 * 최근 12개월의 재난 발생을 유형별 누적 막대로 표현합니다.
 * CSS flex를 이용해 막대 안에 유형별 세그먼트를 쌓습니다.
 *
 * @param data  - useMonthlyTypeStats()에서 반환하는 MonthlyTypeStat[] 배열
 * @param types - 표시할 유형 목록
 */
export function StackedBar({
  data,
  types,
}: {
  data: MonthlyTypeStat[];
  types: string[];
}) {
  const months = [...new Set(data.map(d => d.month))].sort().slice(-12);
  if (months.length === 0) return <EmptyChart />;

  const monthData = months.map(m => {
    const segments = types.map((t, i) => ({
      type: t,
      color: STACKED_COLORS[i % STACKED_COLORS.length],
      count: data.find(d => d.month === m && d.type === t)?.count ?? 0,
    }));
    return {
      label: `${parseInt(m.slice(5))}월`,
      total: segments.reduce((s, g) => s + g.count, 0),
      segments,
    };
  });

  const maxTotal = Math.max(...monthData.map(d => d.total)) || 1;
  const BAR_H = 150; // 막대 최대 높이 (px)

  return (
    <div className="flex flex-col flex-1 gap-1 min-h-0">
      {/* 막대 영역 */}
      <div className="flex items-end gap-0.5" style={{ height: BAR_H }}>
        {monthData.map((m, i) => (
          <div key={i} className="flex-1 flex flex-col justify-end" style={{ height: "100%" }}>
            {/* 막대 높이를 total/maxTotal 비율로 계산 */}
            <div
              className="w-full flex flex-col-reverse overflow-hidden rounded-t-sm"
              style={{ height: `${Math.max((m.total / maxTotal) * BAR_H, 2)}px` }}
            >
              {/* flex-col-reverse: 아래 유형부터 쌓입니다 */}
              {m.segments.map((seg, j) => seg.count > 0 && (
                <div key={j}
                  title={`${seg.type}: ${seg.count}건`}
                  style={{
                    // 이 세그먼트 높이 = 이 유형 건수 / 이 달 전체 건수 비율
                    height: `${(seg.count / (m.total || 1)) * 100}%`,
                    background: seg.color,
                    minHeight: 1,
                  }}
                />
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* 월 레이블 */}
      <div className="flex gap-0.5">
        {monthData.map((m, i) => (
          <div key={i} className="flex-1 text-center">
            <span className="text-[9px] text-gray-500">{m.label}</span>
          </div>
        ))}
      </div>

      {/* 유형 범례 */}
      <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-1">
        {types.map((t, i) => (
          <span key={i} className="flex items-center gap-1 text-xs text-gray-500">
            <span className="w-2 h-2 rounded-sm inline-block flex-shrink-0"
              style={{ background: STACKED_COLORS[i % STACKED_COLORS.length] }} />
            {t}
          </span>
        ))}
      </div>
    </div>
  );
}

// ─── 전년 동기 대비 막대 차트 ────────────────────────────────────────────────

/**
 * CompareBars
 *
 * 올해와 작년의 월별 발생 건수를 나란한 막대로 비교합니다.
 * 전체 YoY(Year-over-Year) 증감률도 우측 하단에 표시합니다.
 *
 * @param thisYearData - 올해 일별 데이터 (월별로 내부에서 집계합니다)
 * @param lastYearData - 작년 일별 데이터
 * @param currentYear  - 올해 연도 숫자 (예: 2025)
 */
export function CompareBars({
  thisYearData,
  lastYearData,
  currentYear,
}: {
  thisYearData: DailyStat[];
  lastYearData: DailyStat[];
  currentYear: number;
}) {
  // 일별 데이터를 월별로 합산합니다: "2024-07-15" → "07" 키로 그룹핑
  const agg = (data: DailyStat[]) => {
    const m: Record<string, number> = {};
    data.forEach(d => {
      const k = d.date.slice(5, 7); // "07"
      m[k] = (m[k] ?? 0) + d.count;
    });
    return m;
  };
  const ty = agg(thisYearData), ly = agg(lastYearData);

  // 1~12월 배열 생성 (데이터가 전혀 없는 달은 제외)
  const months = Array.from({ length: 12 }, (_, i) => {
    const key = String(i + 1).padStart(2, "0"); // "01" ~ "12"
    return { label: `${i + 1}월`, ty: ty[key] ?? 0, ly: ly[key] ?? 0 };
  }).filter(m => m.ty > 0 || m.ly > 0);

  if (months.length === 0) return <EmptyChart />;

  // 전체 YoY 증감률 계산
  const totalTy = months.reduce((s, m) => s + m.ty, 0);
  const totalLy = months.reduce((s, m) => s + m.ly, 0);
  const yoy = totalLy > 0 ? Math.round(((totalTy - totalLy) / totalLy) * 100) : null;

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    const lyVal = payload.find(p => p.dataKey === "ly")?.value ?? 0;
    const tyVal = payload.find(p => p.dataKey === "ty")?.value ?? 0;
    const diff = lyVal > 0 ? Math.round(((tyVal - lyVal) / lyVal) * 100) : null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 2 }}>
          <span style={{ width: 8, height: 8, borderRadius: 2, background: "#cbd5e1", display: "inline-block", flexShrink: 0 }} />
          <span style={{ color: "#fff", fontSize: 11 }}>
            {currentYear - 1}: {lyVal.toLocaleString("ko-KR")}건
          </span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: diff !== null ? 4 : 0 }}>
          <span style={{ width: 8, height: 8, borderRadius: 2, background: "#2563eb", display: "inline-block", flexShrink: 0 }} />
          <span style={{ color: "#fff", fontSize: 11 }}>
            {currentYear}: {tyVal.toLocaleString("ko-KR")}건
          </span>
        </div>
        {diff !== null && (
          <p style={{
            color: tyVal >= lyVal ? "#f87171" : "#60a5fa",
            fontWeight: 700, fontSize: 11, margin: 0, textAlign: "center",
          }}>
            {tyVal >= lyVal ? `↑ +${diff}%` : `↓ ${diff}%`}
          </p>
        )}
      </div>
    );
  };

  return (
    <div className="flex flex-col gap-2 flex-1 min-h-0">
      <div className="flex-1 min-h-0" style={{ height: "100%" }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={months} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="label" axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} />
            <YAxis axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} width={32} />
            <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} />
            {/* formatter: Recharts 범례의 "ly"/"ty" 키를 연도로 변환 */}
            <Legend wrapperStyle={{ fontSize: 12 }}
              formatter={(value: string) =>
                value === "ly" ? String(currentYear - 1) : String(currentYear)
              } />
            {/* ly: 작년 (회색), ty: 올해 (파란색) */}
            <Bar dataKey="ly" fill="#cbd5e1" radius={[3, 3, 0, 0]} maxBarSize={16} />
            <Bar dataKey="ty" fill="#2563eb" radius={[3, 3, 0, 0]} maxBarSize={16} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* 전체 YoY 증감률 표시 */}
      {yoy !== null && (
        <div className="flex justify-end text-xs">
          <span className={`font-bold ${yoy >= 0 ? "text-red-600" : "text-blue-600"}`}>
            {yoy >= 0 ? `↑ +${yoy}%` : `↓ ${yoy}%`} (YoY)
          </span>
        </div>
      )}
    </div>
  );
}

// ─── 전년 동기 대비 꺾은선 차트 ─────────────────────────────────────────────

/**
 * CompareLines
 *
 * 올해(파란 실선)와 작년(회색 점선)을 같은 축에 그려 추세를 비교합니다.
 * Recharts 없이 순수 SVG path로 그립니다.
 *
 * @param thisYearData - 올해 일별 데이터
 * @param lastYearData - 작년 일별 데이터
 * @param currentYear  - 올해 연도 숫자
 */
export function CompareLines({
  thisYearData,
  lastYearData,
  currentYear,
}: {
  thisYearData: DailyStat[];
  lastYearData: DailyStat[];
  currentYear: number;
}) {
  const agg = (data: DailyStat[]) => {
    const m: Record<string, number> = {};
    data.forEach(d => {
      const k = d.date.slice(5, 7);
      m[k] = (m[k] ?? 0) + d.count;
    });
    return m;
  };
  const ty = agg(thisYearData), ly = agg(lastYearData);

  const months = Array.from({ length: 12 }, (_, i) => {
    const key = String(i + 1).padStart(2, "0");
    return { label: `${i + 1}월`, ty: ty[key] ?? 0, ly: ly[key] ?? 0 };
  }).filter(m => m.ty > 0 || m.ly > 0);

  if (months.length === 0) return <EmptyChart />;

  const max = Math.max(...months.flatMap(m => [m.ty, m.ly])) || 1;
  const W = 300, H = 130, PAD = 12;
  // 월 간격 (점과 점 사이 가로 거리)
  const xStep = months.length > 1 ? (W - PAD * 2) / (months.length - 1) : 0;
  // Y좌표: 값이 클수록 위(Y가 작아짐)
  const yScale = (v: number) => PAD + (1 - v / max) * (H - PAD * 2);
  // SVG path의 "M x,y L x,y L x,y ..." 형태 좌표 문자열 생성
  const mkPath = (vals: number[]) =>
    vals.map((v, i) => `${i === 0 ? "M" : "L"}${PAD + i * xStep},${yScale(v)}`).join(" ");

  return (
    <div className="flex flex-col flex-1 gap-2 min-h-0">
      <svg width="100%" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="flex-1">
        {/* 작년: 회색 점선 */}
        <path d={mkPath(months.map(m => m.ly))}
          fill="none" stroke="#cbd5e1" strokeWidth="2" strokeDasharray="5 3" />
        {/* 올해: 파란 실선 */}
        <path d={mkPath(months.map(m => m.ty))}
          fill="none" stroke="#2563eb" strokeWidth="2" />
        {/* 데이터 포인트 원 */}
        {months.map((m, i) => (
          <Fragment key={i}>
            <circle cx={PAD + i * xStep} cy={yScale(m.ly)} r={3} fill="#cbd5e1" />
            <circle cx={PAD + i * xStep} cy={yScale(m.ty)} r={3} fill="#2563eb" />
          </Fragment>
        ))}
      </svg>

      {/* X축 월 레이블 */}
      <div className="flex justify-between text-[9px] text-gray-400 px-1">
        {months.map((m, i) => <span key={i}>{m.label}</span>)}
      </div>

      {/* 범례 */}
      <div className="flex gap-4 text-xs text-gray-500">
        <span className="flex items-center gap-1.5">
          <svg width="16" height="8">
            <line x1="0" y1="4" x2="16" y2="4" stroke="#cbd5e1" strokeWidth="2" strokeDasharray="4 2" />
          </svg>
          {currentYear - 1}
        </span>
        <span className="flex items-center gap-1.5">
          <svg width="16" height="8">
            <line x1="0" y1="4" x2="16" y2="4" stroke="#2563eb" strokeWidth="2" />
          </svg>
          {currentYear}
        </span>
      </div>
    </div>
  );
}
