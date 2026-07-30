import type { TFunction } from "i18next";
import type { DailyStat } from "@/types/alerts";
import { TT_BOX, TT_LABEL, TTProps } from "./common";

/**
 * useYearComparison.tsx
 *
 * CompareBars/CompareLines가 공통으로 쓰는 "일별 데이터 → 월별 올해/작년 비교"
 * 집계 로직과 툴팁입니다. 두 컴포넌트가 이 로직을 그대로 복붙해 갖고 있었는데,
 * Bar-vs-Line 렌더링 차이만 남기고 여기로 뽑아냈습니다.
 */

export type ComparisonMonth = { label: string; ty: number; ly: number; monthNum: number };

export function useYearComparison(thisYearData: DailyStat[], lastYearData: DailyStat[], t: TFunction) {
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
  const months: ComparisonMonth[] = Array.from({ length: 12 }, (_, i) => {
    const key = String(i + 1).padStart(2, "0"); // "01" ~ "12"
    return { label: `${i + 1}${t("statsPage.monthSuffix")}`, ty: ty[key] ?? 0, ly: ly[key] ?? 0, monthNum: i + 1 };
  }).filter(m => m.ty > 0 || m.ly > 0);

  // 전체 YoY 증감률 계산 (미래 달 제외)
  const pastMonths = months.filter(m => !(m.ty === 0 && m.monthNum > currentMonth));
  const totalTy = pastMonths.reduce((s, m) => s + m.ty, 0);
  const totalLy = pastMonths.reduce((s, m) => s + m.ly, 0);
  const yoy = totalLy > 0 ? Math.round(((totalTy - totalLy) / totalLy) * 100) : null;

  return { months, currentMonth, yoy };
}

export function CompareTooltipContent({
  active, payload, label,
  months, currentMonth, currentYear, locale, t,
}: TTProps & {
  months: ComparisonMonth[];
  currentMonth: number;
  currentYear: number;
  locale: string;
  t: TFunction;
}) {
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
}

export function YoyBadge({ yoy }: { yoy: number | null }) {
  if (yoy === null) return null;
  return (
    <div className="flex justify-end text-xs">
      <span className={`font-bold ${yoy >= 0 ? "text-red-600" : "text-blue-600"}`}>
        {yoy >= 0 ? `↑ +${yoy}%` : `↓ ${yoy}%`} (YoY)
      </span>
    </div>
  );
}
