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
import { LANG_LOCALE, TT_BOX, TT_LABEL, TTProps } from "./common";

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
  const agg = (data: DailyStat[]) => {
    const m: Record<string, number> = {};
    data.forEach(d => {
      const k = d.date.slice(5, 7);
      m[k] = (m[k] ?? 0) + d.count;
    });
    return m;
  };
  const ty = agg(thisYearData), ly = agg(lastYearData);

  // 1~12월 배열 생성 (둘 다 0인 달만 제외)
  const currentMonth = new Date().getMonth() + 1;
  const months = Array.from({ length: 12 }, (_, i) => {
    const key = String(i + 1).padStart(2, "0");
    return { label: `${i + 1}${t("statsPage.monthSuffix")}`, ty: ty[key] ?? 0, ly: ly[key] ?? 0, monthNum: i + 1 };
  }).filter(m => m.ty > 0 || m.ly > 0);

  if (months.length === 0) return <EmptyChart />;

  // 전체 YoY 증감률 계산 (미래 달 제외)
  const pastMonths = months.filter(m => !(m.ty === 0 && m.monthNum > currentMonth));
  const totalTy = pastMonths.reduce((s, m) => s + m.ty, 0);
  const totalLy = pastMonths.reduce((s, m) => s + m.ly, 0);
  const yoy = totalLy > 0 ? Math.round(((totalTy - totalLy) / totalLy) * 100) : null;

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (!active || !payload?.length) return null;
    const lyVal = payload.find(p => p.dataKey === "ly")?.value ?? 0;
    const tyVal = payload.find(p => p.dataKey === "ty")?.value ?? 0;
    const monthNum = months.find(m => m.label === label)?.monthNum ?? 0;
    const isFuture = tyVal === 0 && monthNum > currentMonth;
    const diff = !isFuture && lyVal > 0 ? Math.round(((tyVal - lyVal) / lyVal) * 100) : null;
    return (
      <div style={TT_BOX}>
        <p style={TT_LABEL}>{label}</p>
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 2 }}>
          <span style={{ width: 8, height: 8, borderRadius: 2, background: "#cbd5e1", display: "inline-block", flexShrink: 0 }} />
          <span style={{ color: "#fff", fontSize: 11 }}>
            {currentYear - 1}: {lyVal.toLocaleString(locale)}{t("statsPage.countUnit")}
          </span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: diff !== null ? 4 : 0 }}>
          <span style={{ width: 8, height: 8, borderRadius: 2, background: "#2563eb", display: "inline-block", flexShrink: 0 }} />
          <span style={{ color: "#fff", fontSize: 11 }}>
            {currentYear}: {tyVal.toLocaleString(locale)}{t("statsPage.countUnit")}
          </span>
        </div>
        {diff !== null && (
          <p style={{
            color: tyVal >= lyVal ? "#f87171" : "#60a5fa",
            fontWeight: 700, fontSize: 11, margin: 0, textAlign: "center",
          }}>
            {tyVal >= lyVal ? `↑ +${diff}%` : `↓ ${diff}%`}
          </p>
        )}
      </div>
    );
  };

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
            <Tooltip content={<TooltipContent />} wrapperStyle={{ zIndex: 30 }} />
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
      {yoy !== null && (
        <div className="flex justify-end text-xs">
          <span className={`font-bold ${yoy >= 0 ? "text-red-600" : "text-blue-600"}`}>
            {yoy >= 0 ? `↑ +${yoy}%` : `↓ ${yoy}%`} (YoY)
          </span>
        </div>
      )}
    </div>
  );
}
