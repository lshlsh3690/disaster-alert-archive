"use client";

import {
  BarChart, Bar, Cell,
  XAxis, YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { BAR_COLORS } from "../_constants";
import type { RegionStat } from "../_constants";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";

/**
 * HorizontalBar
 *
 * 지역별 재난 건수를 가로 막대로 표현합니다.
 * Recharts의 BarChart에 layout="vertical"을 사용하면 가로 막대가 됩니다.
 *
 * @param data - { region: 지역명, count: 건수 }[] 배열
 */
export function HorizontalBar({ data, onBarClick, labelFormatter }: { data: RegionStat[]; onBarClick?: (label: string) => void; labelFormatter?: (label: string) => string }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  if (data.length === 0) return <EmptyChart />;

  // 커스텀 툴팁: 마우스를 올렸을 때 지역명과 건수를 보여줍니다
  const TooltipContent = ({ active, payload }: TTProps) => {
    if (!active || !payload?.length) return null;
    const region = payload[0].payload?.region as unknown as string | undefined;
    return (
      <div style={TT_BOX}>
        {/* payload[0].payload는 해당 막대의 원본 데이터 객체입니다 */}
        <p style={TT_LABEL}>{region ? (labelFormatter ? labelFormatter(region) : region) : ""}</p>
        <p style={TT_VALUE}>{(payload[0].value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0 relative">
      <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
        {/* layout="vertical": 막대가 가로 방향으로 그려집니다 */}
        <BarChart layout="vertical" data={data} margin={{ top: 4, right: 16, bottom: 4, left: 4 }}>
          {/* type="number": 가로 축이 숫자 눈금 */}
          <XAxis type="number" hide />
          {/* type="category": 세로 축이 텍스트(지역명) */}
          {/* interval={0}: 번역된 라벨이 길어져도 Recharts가 겹침 방지를 위해 임의로 건너뛰지 않고 전부 표시 */}
          <YAxis type="category" dataKey="region" width={80} axisLine={false} tickLine={false} interval={0}
            tick={{ fontSize: 11, fill: "#374151" }} tickFormatter={labelFormatter} />
          <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} />
          <Bar dataKey="count" radius={[0, 3, 3, 0]} barSize={14} isAnimationActive={false}
            cursor={onBarClick ? "pointer" : "default"}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            onClick={onBarClick ? (d: any) => onBarClick(d.region ?? "") : undefined}>
            {data.map((_, i) => (
              <Cell key={i} fill={BAR_COLORS[i % BAR_COLORS.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
