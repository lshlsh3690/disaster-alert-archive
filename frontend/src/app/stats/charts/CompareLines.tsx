"use client";

import {
  LineChart, Line,
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
 * CompareLines
 *
 * 올해(파란 실선)와 작년(회색 점선)을 같은 축에 그려 추세를 비교합니다.
 * CompareBars와 동일하게 Recharts ResponsiveContainer를 사용해
 * 부모 카드 높이에 정확히 맞춥니다. (이전 순수 SVG 구현은 viewBox 비율 때문에
 * 카드 높이를 초과해 넘치는 문제가 있었음)
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
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const { months, currentMonth, yoy } = useYearComparison(thisYearData, lastYearData, t);

  if (months.length === 0) return <EmptyChart />;

  return (
    <div className="flex flex-col gap-2 flex-1 min-h-0">
      <div className="flex-1 min-h-0 relative">
        <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
          <LineChart data={months} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="label" axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} />
            <YAxis axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} width={32} />
            <Tooltip
              content={<CompareTooltipContent months={months} currentMonth={currentMonth} currentYear={currentYear} locale={locale} t={t} />}
              wrapperStyle={{ zIndex: 30 }} />
            {/* formatter: Recharts 범례의 "ly"/"ty" 키를 연도로 변환 */}
            <Legend wrapperStyle={{ fontSize: 12 }}
              formatter={(value: string) =>
                value === "ly" ? String(currentYear - 1) : String(currentYear)
              } />
            {/* ly: 작년 (회색 점선), ty: 올해 (파란 실선) */}
            <Line dataKey="ly" stroke="#cbd5e1" strokeWidth={2} strokeDasharray="5 3"
              dot={{ r: 3, fill: "#cbd5e1" }} isAnimationActive={false} />
            <Line dataKey="ty" stroke="#2563eb" strokeWidth={2}
              dot={{ r: 3, fill: "#2563eb" }} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* 전체 YoY 증감률 표시 */}
      <YoyBadge yoy={yoy} />
    </div>
  );
}
