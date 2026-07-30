"use client";

import {
  ComposedChart, Bar, Line, Area,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer,
} from "recharts";
import type { WeatherCorrelationStat } from "@/types/alerts";
import { EmptyChart } from "../_charts";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TT_VALUE, TTProps } from "./common";
import { getAggMode, getAggKey, fmtKey } from "./weatherHelpers";

/**
 * WeatherOverlayChart
 *
 * 총 발생건수(막대) + 기온범위·평균기온·강수량(꺾은선/영역) 복합 차트.
 */
export function WeatherOverlayChart({ data }: { data: WeatherCorrelationStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const translateType = (type: string) => t(`disasterTypes.${type}`, { defaultValue: type });
  if (data.length === 0) return <EmptyChart />;

  const mode = getAggMode(data.length);

  // 집계 키별 통계 누산 버퍼
  type AggRow = { count: number; tempSum: number; tempN: number; minTemp: number | null; maxTemp: number | null; maxPrecip: number | null; typeCount: Map<string, number> };
  const aggMap = new Map<string, AggRow>();
  data.forEach(d => {
    const key = getAggKey(d.date, mode);
    if (!aggMap.has(key)) aggMap.set(key, { count: 0, tempSum: 0, tempN: 0, minTemp: null, maxTemp: null, maxPrecip: null, typeCount: new Map() });
    const e = aggMap.get(key)!;
    e.count += d.count;
    if (d.avgTemp != null) { e.tempSum += d.avgTemp; e.tempN++; }
    if (d.minTemp != null) e.minTemp = e.minTemp == null ? d.minTemp : Math.min(e.minTemp, d.minTemp);
    if (d.maxTemp != null) e.maxTemp = e.maxTemp == null ? d.maxTemp : Math.max(e.maxTemp, d.maxTemp);
    if (d.maxPrecip != null) e.maxPrecip = e.maxPrecip == null ? d.maxPrecip : Math.max(e.maxPrecip, d.maxPrecip);
    if (d.primaryType) e.typeCount.set(d.primaryType, (e.typeCount.get(d.primaryType) ?? 0) + d.count);
  });

  // 정렬된 집계 키 목록
  const keys = [...aggMap.keys()].sort();
  // X축 레이블 표시 간격
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  // Recharts에 넘길 최종 데이터: 기간별 건수·평균기온·기온범위·강수·주요유형
  const chartData = keys.map(key => {
    const e = aggMap.get(key)!;
    // 해당 기간의 평균 기온 (소수점 1자리)
    const avgTemp = e.tempN > 0 ? Math.round(e.tempSum / e.tempN * 10) / 10 : null;
    // 해당 기간에서 가장 많이 발생한 재난 유형
    const primaryType = e.typeCount.size > 0 ? [...e.typeCount.entries()].sort((a, b) => b[1] - a[1])[0][0] : null;
    return {
      date: fmtKey(key, mode, t("statsPage.monthSuffix")),
      count: e.count,
      avgTemp,
      minTemp: e.minTemp,
      maxTemp: e.maxTemp,
      maxPrecip: e.maxPrecip,
      primaryType,
      // tempRange: Area 차트로 기온 범위(최저~최고)를 띠 형태로 표현하기 위한 배열값
      tempRange: e.minTemp != null && e.maxTemp != null ? [e.minTemp, e.maxTemp] as [number, number] : null,
    };
  });

  const TooltipContent = ({ active, payload }: TTProps) => {
    if (!active || !payload?.length) return null;
    const get = (key: string) => payload.find(p => p.dataKey === key)?.value;
    const row = payload[0].payload as unknown as typeof chartData[number];
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{row.date}</p>
        <p style={{ ...TT_VALUE, color: "#60a5fa" }}>{(get("count") ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</p>
        {row?.primaryType && (
          <p style={{ ...TT_LABEL, marginTop: 4 }}>{t("statsPage.weatherChart.primaryTypeLabel")}: <span style={{ color: "#e2e8f0" }}>{translateType(row.primaryType)}</span></p>
        )}
        {row.avgTemp != null && (
          <p style={TT_LABEL}>
            {t("statsPage.weatherChart.temperatureLabel")} {row.avgTemp.toFixed(1)}°C
            {row.minTemp != null && row.maxTemp != null && (
              <span style={{ color: "#94a3b8" }}> ({row.minTemp.toFixed(1)}~{row.maxTemp.toFixed(1)})</span>
            )}
          </p>
        )}
        {get("maxPrecip") != null && <p style={TT_LABEL}>{t("statsPage.weatherChart.precipitationLabel")} {get("maxPrecip")}mm</p>}
      </div>
    );
  };

  return (
    <div className="flex-1 min-h-0 relative">
      <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
        <ComposedChart data={chartData} margin={{ top: 8, right: 24, bottom: 4, left: 4 }}>
          <CartesianGrid vertical={false} stroke="#f3f4f6" />
          <XAxis dataKey="date" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#9ca3af" }}
            interval={labelStride - 1} />
          <YAxis yAxisId="cnt" axisLine={false} tickLine={false} tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
          <YAxis yAxisId="temp" orientation="right" axisLine={false} tickLine={false}
            tick={{ fontSize: 9, fill: "#f97316" }} width={36} tickFormatter={(v: number) => `${v}°`} />
          <Tooltip content={<TooltipContent />} wrapperStyle={{ zIndex: 30 }} />
          <Legend wrapperStyle={{ fontSize: 11 }}
            formatter={(v: string) => ({
              count: t("statsPage.weatherChart.occurrenceCountLegend"),
              avgTemp: t("statsPage.weatherChart.averageTempLegend"),
              tempRange: t("statsPage.weatherChart.tempRangeLegend"),
              maxPrecip: t("statsPage.weatherChart.maxPrecipLegend"),
            }[v] ?? v)} />
          <Bar yAxisId="cnt" dataKey="count" fill="#3b82f6" fillOpacity={0.7} radius={[2, 2, 0, 0]} maxBarSize={20} isAnimationActive={false} />
          <Area yAxisId="temp" type="monotone" dataKey="tempRange" stroke="none"
            fill="#f97316" fillOpacity={0.12} activeDot={false} legendType="none" isAnimationActive={false} />
          <Line yAxisId="temp" type="monotone" dataKey="avgTemp" stroke="#f97316" strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line yAxisId="temp" type="monotone" dataKey="maxPrecip" stroke="#06b6d4" strokeWidth={1.5} dot={false} strokeDasharray="4 2" isAnimationActive={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
