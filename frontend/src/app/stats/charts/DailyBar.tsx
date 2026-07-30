"use client";

import {
  BarChart, Bar,
  XAxis, YAxis,
  CartesianGrid,
  Tooltip,
  Brush,
  ResponsiveContainer,
} from "recharts";
import type { DailyStat } from "@/types/alerts";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";

/**
 * DailyBar
 *
 * 날짜별 건수를 Recharts BarChart로 표현합니다.
 * ResponsiveContainer를 사용해 LineChart와 동일한 높이 제약을 받습니다.
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
          <CartesianGrid vertical={false} stroke="#f3f4f6" />
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }}
            tickFormatter={(v: string) => v.slice(5)}
            interval={labelStride - 1} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} />
          <Bar dataKey="count" fill="#3b82f6" radius={[2, 2, 0, 0]} maxBarSize={40} isAnimationActive={false} />
          {data.length > 7 && (
            <Brush dataKey="date" height={22} stroke="#93c5fd" fill="#eff6ff" travellerWidth={8}
              tickFormatter={(v: string) => v.slice(5)}
              style={{ fontSize: 9, fill: "#6b7280" }} />
          )}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
