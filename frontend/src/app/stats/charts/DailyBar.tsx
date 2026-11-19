"use client";

import {
  BarChart, Bar, Cell,
  XAxis, YAxis,
  CartesianGrid,
  Tooltip,
  Brush,
  ResponsiveContainer,
} from "recharts";
import type { DailyStat } from "@/types/alerts";
import { EmptyChart } from "../_charts";
import { BAR_FILL, BAR_FILL_MUTED, CHART_CURSOR, CHART_GRID, CHART_TICK } from "../_constants";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";

/**
 * DailyBar
 *
 * 날짜별 건수를 Recharts BarChart로 표현합니다.
 * ResponsiveContainer를 사용해 LineChart와 동일한 높이 제약을 받습니다.
 * 막대는 primary-30, 마지막 날(가장 최근)만 primary-50 으로 칠합니다(KRDS 통계 시안).
 *
 * @param data - { date: "YYYY-MM-DD", count: number }[] 배열
 */
export function DailyBar({ data }: { data: DailyStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  if (data.length === 0) return <EmptyChart />;

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
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
          <CartesianGrid vertical={false} stroke={CHART_GRID} />
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: CHART_TICK }}
            tickFormatter={(v: string) => v.slice(5)}
            interval={labelStride - 1} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: CHART_TICK }} width={32} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: CHART_CURSOR }} />
          <Bar dataKey="count" radius={[3, 3, 0, 0]} maxBarSize={40} isAnimationActive={false}>
            {data.map((_, i) => (
              <Cell key={i} fill={i === data.length - 1 ? BAR_FILL : BAR_FILL_MUTED} />
            ))}
          </Bar>
          {data.length > 7 && (
            <Brush dataKey="date" height={22} stroke="#86aff9" fill="#ecf2fe" travellerWidth={8}
              tickFormatter={(v: string) => v.slice(5)}
              style={{ fontSize: 9, fill: CHART_TICK }} />
          )}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
