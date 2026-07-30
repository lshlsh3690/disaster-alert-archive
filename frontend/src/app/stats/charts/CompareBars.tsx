"use client";

import {
  BarChart, Bar,
  XAxis, YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import type { DailyStat } from "@/types/alerts";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE } from "./common";
import { useYearComparison, CompareTooltipContent, YoyBadge } from "./useYearComparison";

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
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const { months, currentMonth, yoy } = useYearComparison(thisYearData, lastYearData, t);

  if (months.length === 0) return <EmptyChart />;

  return (
    <div className="flex flex-col gap-2 flex-1 min-h-0">
      <div className="flex-1 min-h-0 relative">
        <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
          <BarChart data={months} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="label" axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} />
            <YAxis axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} width={32} />
            <Tooltip
              content={<CompareTooltipContent months={months} currentMonth={currentMonth} currentYear={currentYear} locale={locale} t={t} />}
              cursor={{ fill: "#f3f4f6" }} wrapperStyle={{ zIndex: 30 }} />
            {/* formatter: Recharts 범례의 "ly"/"ty" 키를 연도로 변환 */}
            <Legend wrapperStyle={{ fontSize: 12 }}
              formatter={(value: string) =>
                value === "ly" ? String(currentYear - 1) : String(currentYear)
              } />
            {/* ly: 작년 (회색), ty: 올해 (파란색) */}
            <Bar dataKey="ly" fill="#cbd5e1" radius={[3, 3, 0, 0]} maxBarSize={16} isAnimationActive={false} />
            <Bar dataKey="ty" fill="#2563eb" radius={[3, 3, 0, 0]} maxBarSize={16} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* 전체 YoY 증감률 표시 */}
      <YoyBadge yoy={yoy} />
    </div>
  );
}
