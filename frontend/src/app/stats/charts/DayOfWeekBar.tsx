"use client";

import type { HourlyStat } from "@/types/alerts";
import { DOW_TO_IDX, getWeekdays } from "../_constants";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { VerticalBar } from "./VerticalBar";

/**
 * DayOfWeekBar
 *
 * 히트맵 데이터를 요일별로 합산해 세로 막대 차트로 보여줍니다.
 * HourlyStat[] 원본 데이터를 받아 요일 차원으로 집계합니다.
 *
 * @param data - useHourlyStats()가 반환하는 HourlyStat[] 그대로 넘기면 됩니다
 */
export function DayOfWeekBar({ data }: { data: HourlyStat[] }) {
  const { t } = useTranslation();
  const weekdays = getWeekdays(t);
  // agg: { 요일번호: 총건수 } 형태로 집계
  const agg: Record<number, number> = {};
  data.forEach(({ dayOfWeek, count }) => {
    agg[dayOfWeek] = (agg[dayOfWeek] ?? 0) + count;
  });

  // weekdays 순서(월~일)에 맞게 배열로 변환
  const bars = weekdays.map((label, di) => {
    const dow = Number(
      Object.entries(DOW_TO_IDX).find(([, idx]) => idx === di)?.[0] ?? -1
    );
    return { label, count: agg[dow] ?? 0 };
  });

  if (bars.every(b => b.count === 0)) return <EmptyChart />;
  // 최솟값~최댓값 범위로 정규화해 색 차이를 명확하게 표현
  const max = Math.max(...bars.map(b => b.count));
  const min = Math.min(...bars.map(b => b.count));
  const range = max - min || 1;
  const colored = bars.map(b => ({
    ...b,
    color: `rgba(37,99,235,${0.15 + ((b.count - min) / range) * 0.75})`,
  }));
  return <VerticalBar data={colored} />;
}
