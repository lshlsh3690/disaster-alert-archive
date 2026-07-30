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
import { LANG_LOCALE, TT_BOX, TT_LABEL, TTProps } from "./common";

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
  // 일별 데이터를 월별로 합산합니다: "2024-07-15" → "07" 키로 그룹핑
  const agg = (data: DailyStat[]) => {
    const m: Record<string, number> = {};
    data.forEach(d => {
      const k = d.date.slice(5, 7); // "07"
      m[k] = (m[k] ?? 0) + d.count;
    });
    return m;
  };
  const ty = agg(thisYearData), ly = agg(lastYearData);

  // 1~12월 배열 생성 (둘 다 0인 달만 제외)
  const currentMonth = new Date().getMonth() + 1;
  const months = Array.from({ length: 12 }, (_, i) => {
    const key = String(i + 1).padStart(2, "0"); // "01" ~ "12"
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
    // 올해 데이터가 없고 아직 지나지 않은 달은 증감률 표시 안 함
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
          <BarChart data={months} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="label" axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} />
            <YAxis axisLine={false} tickLine={false}
              tick={{ fontSize: 10, fill: "#9ca3af" }} width={32} />
            <Tooltip content={<TooltipContent />} cursor={{ fill: "#f3f4f6" }} wrapperStyle={{ zIndex: 30 }} />
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
