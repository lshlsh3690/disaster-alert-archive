"use client";

import {
  BarChart, Bar, Cell,
  XAxis, YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { BAR_FILL, BAR_FILL_MUTED, CHART_CURSOR, CHART_TICK } from "../_constants";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";

/**
 * VerticalBar
 *
 * 유형별·지역별 건수를 세로 막대로 표현합니다.
 * 가장 많은 막대만 primary-50 으로 강조하고 나머지는 primary-30 으로 칠합니다 (peakIdx).
 *
 * @param data - { label: 라벨명, count: 건수, color?: 개별 색상 }[] 배열
 *              color를 지정하면 해당 색상을, 없으면 위 강조 규칙을 씁니다.
 */
export function VerticalBar({
  data,
  onBarClick,
  labelFormatter,
}: {
  data: { label: string; count: number; color?: string }[];
  onBarClick?: (label: string) => void;
  labelFormatter?: (label: string) => string;
}) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  if (data.length === 0) return <EmptyChart />;

  // 가장 값이 큰 인덱스를 찾아서 진한 primary 로 강조합니다
  const peakIdx = data.reduce((p, c, i) => (c.count > data[p].count ? i : p), 0);

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label ? (labelFormatter ? labelFormatter(label) : label) : ""}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0 relative">
      <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
        <BarChart data={data} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
          <XAxis dataKey="label" axisLine={false} tickLine={false} interval={0}
            tick={{ fontSize: 10, fill: CHART_TICK }} tickFormatter={labelFormatter} />
          <YAxis axisLine={false} tickLine={false}
            tick={{ fontSize: 10, fill: CHART_TICK }} width={32} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: CHART_CURSOR }} />
          <Bar dataKey="count" radius={[3, 3, 0, 0]} maxBarSize={40} isAnimationActive={false}
            cursor={onBarClick ? "pointer" : "default"}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            onClick={onBarClick ? (d: any) => onBarClick(d.label ?? "") : undefined}>
            {data.map((d, i) => (
              <Cell key={i}
                fill={d.color ?? (i === peakIdx ? BAR_FILL : BAR_FILL_MUTED)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
