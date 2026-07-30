"use client";

import { TYPE_COLORS } from "../_constants";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE } from "./common";

/**
 * DonutChart
 *
 * 재난 유형 또는 경보 단계별 점유율을 SVG 도넛으로 표현합니다.
 * Recharts가 아닌 순수 SVG로 직접 그립니다 (더 세밀한 제어를 위해).
 *
 * @param data  - 도넛 조각 데이터 배열. type: 라벨명, count: 건수
 * @param total - 전체 건수 합계 (중앙에 표시 + 퍼센트 계산에 사용)
 *
 * 원리:
 *   SVG circle의 stroke-dasharray를 이용해 원의 일부만 색칠합니다.
 *   c = 2πr (원의 둘레), dash = pct * c (해당 조각이 차지하는 호의 길이)
 */
export function DonutChart({
  data,
  total,
  onTypeClick,
  selectedType,
  labelFormatter,
}: {
  data: { type: string | null; count: number }[];
  total: number;
  onTypeClick?: (type: string) => void;
  selectedType?: string;
  labelFormatter?: (label: string) => string;
}) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const r = 38;              // 원 반지름 (px 기준, SVG viewBox 100x100 내)
  const c = 2 * Math.PI * r; // 원의 전체 둘레 길이
  let acc = 0;               // 누적 각도 (이전 조각들의 합)

  return (
    <div className="flex items-center gap-3 flex-1">
      {/* SVG 도넛 원 */}
      <svg viewBox="0 0 100 100" width="120" height="120" style={{ flexShrink: 0 }}>
        {/* 회색 배경 원 (전체 원) */}
        <circle cx="50" cy="50" r={r} fill="none" stroke="#f3f4f6" strokeWidth="14" />

        {data.map((d, i) => {
          // total이 0이면(모든 count가 0인 경우) 0/0 = NaN이 되어 dash·offset이 깨지므로 방어
          const pct = total > 0 ? d.count / total : 0; // 이 조각의 비율 (0~1)
          const dash = pct * c;               // 색칠할 호의 길이
          // strokeDashoffset: 시작 위치를 조정합니다 (음수 = 시계방향으로 이동)
          const offset = total > 0 ? -((acc / total) * c) : 0;
          acc += d.count;
          return (
            <circle
              key={i}
              cx="50" cy="50" r={r}
              fill="none"
              stroke={TYPE_COLORS[i % TYPE_COLORS.length]}
              strokeWidth="14"
              // dash: 색칠할 부분 길이 / (c - dash): 투명한 나머지 길이
              strokeDasharray={`${dash} ${c - dash}`}
              strokeDashoffset={offset}
              // rotate(-90): SVG는 기본적으로 3시 방향에서 시작하므로
              //              12시 방향에서 시작하도록 -90도 회전합니다
              transform="rotate(-90 50 50)"
            />
          );
        })}

        {/* 중앙 텍스트: 총 건수 */}
        <text x="50" y="47" textAnchor="middle" fontSize="8" fill="#6b7280">{t("statsPage.total")}</text>
        <text x="50" y="60" textAnchor="middle" fontSize="13" fontWeight="700" fill="#111827">
          {total.toLocaleString(locale)}
        </text>
      </svg>

      {/* 범례 목록 — onTypeClick이 있으면 클릭 가능, selectedType이 있으면 해당 항목 외 흐리게 */}
      <ul className="flex-1 space-y-1.5">
        {data.map((d, i) => {
          const key = d.type ?? t("statsPage.other");
          const label = labelFormatter ? labelFormatter(key) : key;
          const isSelected = selectedType === key;
          const isDimmed = !!selectedType && !isSelected;
          return (
            <li key={d.type ?? i}
              onClick={() => onTypeClick?.(key)}
              className={`flex items-center gap-2 text-xs rounded px-1 -mx-1 transition-opacity
                ${onTypeClick ? "cursor-pointer hover:bg-gray-50" : ""}
                ${isDimmed ? "opacity-30" : ""}`}>
              <span className="w-2 h-2 rounded-sm shrink-0"
                style={{ background: TYPE_COLORS[i % TYPE_COLORS.length] }} />
              <span className="flex-1 text-gray-700 truncate">{label}</span>
              <span className="text-gray-400">{total > 0 ? Math.round((d.count / total) * 100) : 0}%</span>
              <span className="font-semibold text-gray-900 min-w-[36px] text-right">
                {d.count.toLocaleString(locale)}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
