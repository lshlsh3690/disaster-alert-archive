"use client";

import type { HourlyStat } from "@/types/alerts";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { VerticalBar } from "./VerticalBar";

/**
 * HourBar
 *
 * 히트맵 데이터를 시간대별로 합산해 세로 막대 차트로 보여줍니다.
 *
 * @param data - useHourlyStats()가 반환하는 HourlyStat[] 그대로 넘기면 됩니다
 */
export function HourBar({ data }: { data: HourlyStat[] }) {
  const { t } = useTranslation();
  const agg: Record<number, number> = {};
  data.forEach(({ hour, count }) => {
    agg[hour] = (agg[hour] ?? 0) + count;
  });

  // 0시 ~ 23시 24개 막대
  const bars = Array.from({ length: 24 }, (_, h) => ({
    label: `${h}${t("statsPage.hourSuffix")}`,
    count: agg[h] ?? 0,
  }));

  if (bars.every(b => b.count === 0)) return <EmptyChart />;
  // 0 기준 정규화 (새벽 등 값이 거의 없는 구간과 피크 구간 대비가 자연스러움)
  const max = Math.max(...bars.map(b => b.count)) || 1;
  const colored = bars.map(b => ({
    ...b,
    color: `rgba(37,99,235,${0.15 + (b.count / max) * 0.75})`,
  }));
  return <VerticalBar data={colored} />;
}
