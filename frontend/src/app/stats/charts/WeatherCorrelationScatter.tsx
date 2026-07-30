"use client";

import { useState } from "react";
import {
  ScatterChart, Scatter, ZAxis,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer,
} from "recharts";
import type { WeatherCorrelationStat } from "@/types/alerts";
import { TYPE_COLORS } from "../_constants";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";

/**
 * WeatherCorrelationScatter
 *
 * 기온(X) vs 발생건수(Y) 버블 산점도. 버블 크기는 강수량(Z)을 나타냅니다.
 * 범례를 클릭하면 해당 재난 유형을 켜고 끌 수 있습니다(겹침이 심할 때 분리해 보기 위함).
 */
export function WeatherCorrelationScatter({ data }: { data: WeatherCorrelationStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const translateType = (type: string) => t(`disasterTypes.${type}`, { defaultValue: type });
  // 범례 클릭으로 숨긴 유형 집합 (겹침이 심할 때 유형을 켜고 끄며 분리해 보기)
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const toggleType = (name: string) => setHidden(prev => {
    const next = new Set(prev);
    if (next.has(name)) next.delete(name); else next.add(name);
    return next;
  });

  // 기온 데이터가 없는 항목은 산점도에서 제외
  const filtered = data.filter(d => d.avgTemp != null);
  if (filtered.length === 0) return <EmptyChart />;

  // 등장하는 재난 유형 목록 (중복 제거)
  const types = [...new Set(filtered.map(d => d.primaryType ?? t("statsPage.other")))];
  // 유형별 색상 매핑
  const colorMap: Record<string, string> = {};
  types.forEach((type, i) => { colorMap[type] = TYPE_COLORS[i % TYPE_COLORS.length]; });

  // 유형별로 그룹핑된 산점도 데이터
  // x: 평균기온, y: 발생건수, z: 강수량(버블 크기, 0 방지를 위해 +1)
  const grouped = types.map(type => ({
    name: type,
    color: colorMap[type],
    points: filtered
      .filter(d => (d.primaryType ?? t("statsPage.other")) === type)
      .map(d => ({ x: d.avgTemp!, y: d.count, z: Math.max((d.maxPrecip ?? 0) + 1, 1), date: d.date, minTemp: d.minTemp, maxTemp: d.maxTemp })),
  }));

  const TooltipContent = ({ active, payload }: TTProps) => {
    if (!active || !payload?.length) return null;
    const p = payload[0].payload as unknown as { x: number; y: number; z: number; date: string; minTemp: number | null; maxTemp: number | null };
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{p.date}</p>
        <p style={TT_VALUE}>{p.y.toLocaleString(locale)}{t("statsPage.countUnit")}</p>
        <p style={TT_LABEL}>
          {t("statsPage.weatherChart.averageTempLegend")} {p.x.toFixed(1)}°C
          {p.minTemp != null && p.maxTemp != null && (
            <span style={{ color: "#64748b" }}> ({p.minTemp.toFixed(1)}~{p.maxTemp.toFixed(1)})</span>
          )}
        </p>
        <p style={TT_LABEL}>{t("statsPage.weatherChart.precipitationLabel")} {(p.z - 1).toFixed(1)}mm</p>
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0 relative">
      <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
        <ScatterChart margin={{ top: 8, right: 8, bottom: 4, left: 4 }}>
          <CartesianGrid stroke="#f3f4f6" />
          <XAxis type="number" dataKey="x" name={t("statsPage.weatherChart.temperatureLabel")} axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} tickFormatter={(v: number) => `${v}°`} />
          <YAxis type="number" dataKey="y" name={t("statsPage.weatherChart.countAxisLabel")} axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
          {/* 버블 크기 범위를 줄여 점들이 서로 덜 겹치도록 함 */}
          <ZAxis type="number" dataKey="z" range={[20, 120]} />
          <Tooltip content={<TooltipContent />} cursor={{ strokeDasharray: "3 3" }} wrapperStyle={{ zIndex: 30 }} />
          {/* 범례 클릭 시 해당 유형 표시/숨김 토글 (숨긴 유형은 회색 처리) */}
          <Legend wrapperStyle={{ fontSize: 11, cursor: "pointer" }}
            onClick={(e: { value?: string }) => toggleType(String(e.value ?? ""))}
            formatter={(value: string) => (
              <span style={{ color: hidden.has(value) ? "#cbd5e1" : "#6b7280" }}>{translateType(value)}</span>
            )} />
          {grouped.map(g => (
            // 투명도를 낮추고 흰 테두리를 더해 겹쳐도 개별 점 윤곽이 구분되게 함
            <Scatter key={g.name} name={g.name} data={g.points} fill={g.color}
              fillOpacity={0.45} stroke="#fff" strokeWidth={0.5} hide={hidden.has(g.name)} />
          ))}
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
}
