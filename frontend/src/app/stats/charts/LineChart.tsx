"use client";

import {
  AreaChart, Area,
  XAxis, YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  Brush,
  ResponsiveContainer,
} from "recharts";
import type { DailyStat } from "@/types/alerts";
import { EmptyChart } from "../_charts";
import { BAR_FILL, CHART_GRID, CHART_TICK } from "../_constants";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";

/**
 * LineChart
 *
 * 날짜별 재난문자 발생 건수를 면적(AreaChart)으로 표현합니다.
 * 최고점을 진한 primary 점으로 강조하고, 평균값을 회색 점선으로 보여줍니다.
 *
 * @param data - { date: "YYYY-MM-DD", count: number }[] 배열
 *
 * 참고: 이름은 LineChart이지만 내부적으로 Recharts의 AreaChart를 씁니다.
 *       (그라디언트 면적 채우기를 위해)
 */
export function LineChart({ data }: { data: DailyStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  if (data.length === 0) return <EmptyChart />;

  // 평균 건수: 모든 값을 더해서 개수로 나눕니다
  const avg = Math.round(data.reduce((s, d) => s + d.count, 0) / data.length);

  // 최고값 인덱스: 강조 점을 찍을 위치를 찾습니다
  const peakIdx = data.reduce((p, c, i) => (c.count > data[p].count ? i : p), 0);

  // X축 레이블이 너무 많으면 겹치므로, 최대 6개만 보여줍니다
  const labelStride = Math.max(1, Math.floor(data.length / 6));

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0 relative">
      <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
          {/* SVG 그라디언트 정의: 면적 채우기에 사용 */}
          <defs>
            <linearGradient id="lineGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={BAR_FILL} stopOpacity={0.2} />
              <stop offset="100%" stopColor={BAR_FILL} stopOpacity={0} />
            </linearGradient>
          </defs>

          <CartesianGrid vertical={false} stroke={CHART_GRID} />

          {/* tickFormatter: "2024-07-15" → "07-15" 로 짧게 표시 */}
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: CHART_TICK }}
            tickFormatter={(v: string) => v.slice(5)}
            interval={labelStride - 1} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: CHART_TICK }} width={32} />

          <Tooltip content={<TooltipContent />} />

          {/* 평균값 점선 기준선 */}
          <ReferenceLine y={avg} stroke="#8a949e" strokeDasharray="3 2" />

          {/* 7개 초과 시 줌/팬 범위 선택기 표시 (DailyBar와 동일한 기준) */}
          {data.length > 7 && (
            <Brush dataKey="date" height={22} stroke="#86aff9" fill="#ecf2fe" travellerWidth={8}
              tickFormatter={(v: string) => v.slice(5)}
              style={{ fontSize: 9, fill: CHART_TICK }} />
          )}

          <Area
            type="monotone"
            dataKey="count"
            stroke={BAR_FILL}
            strokeWidth={2}
            fill="url(#lineGrad)"
            isAnimationActive={false}
            // dot prop: 최고점에만 진한 primary 원을 그립니다
            dot={(props: { cx?: number; cy?: number; index: number }) => {
              const { cx, cy, index } = props;
              if (index !== peakIdx || cx == null || cy == null) return <g key={index} />;
              return (
                <circle key={index} cx={cx} cy={cy} r={5}
                  fill="#0b50d0" stroke="#fff" strokeWidth={2} />
              );
            }}
            activeDot={{ r: 4, fill: BAR_FILL }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
