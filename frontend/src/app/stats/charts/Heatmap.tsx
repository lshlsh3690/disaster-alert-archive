"use client";

import { Fragment, useState } from "react";
import type { HourlyStat } from "@/types/alerts";
import { DOW_TO_IDX, getWeekdays } from "../_constants";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE } from "./common";

/**
 * Heatmap
 *
 * 요일(행) × 시간대(열) 교차점에 재난 발생 빈도를 색 농도로 표현합니다.
 * 파란색이 진할수록 발생 건수가 많습니다.
 *
 * @param data - { dayOfWeek: number, hour: number, count: number }[] 배열
 *              dayOfWeek은 PostgreSQL EXTRACT(DOW) 기준: 0=일요일, 1=월요일 ... 6=토요일
 */
export function Heatmap({ data }: { data: HourlyStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const weekdays = getWeekdays(t);
  // tooltip: 현재 마우스가 올라간 셀의 위치·값 정보
  const [tooltip, setTooltip] = useState<{
    di: number; hi: number; v: number; x: number; y: number
  } | null>(null);

  // 7×24 배열 초기화: matrix[요일인덱스][시간] = 건수
  const matrix = Array.from({ length: 7 }, () => Array(24).fill(0) as number[]);
  data.forEach(({ dayOfWeek, hour, count }) => {
    // DOW_TO_IDX: PostgreSQL 요일번호 → 화면 표시 순서(월=0 ... 일=6)로 변환
    const idx = DOW_TO_IDX[dayOfWeek];
    if (idx !== undefined) matrix[idx][hour] = count;
  });

  const max = Math.max(...matrix.flat()) || 1;

  return (
    <div className="flex flex-col gap-2 flex-1 relative">
      {/* CSS Grid: 첫 열은 요일 레이블(20px), 나머지 24열은 균등 분배 */}
      <div style={{ display: "grid", gridTemplateColumns: "20px repeat(24, 1fr)", gap: 2 }}>
        {/* 시간대 헤더 (0, 3, 6, ... 21시만 표시) */}
        <div />
        {Array.from({ length: 24 }).map((_, h) => (
          <div key={h} style={{ fontSize: 8, color: "#9ca3af", textAlign: "center" }}>
            {h % 3 === 0 ? h : ""}
          </div>
        ))}

        {/* 요일별 행 */}
        {matrix.map((row, di) => (
          <Fragment key={di}>
            {/* 요일 레이블 */}
            <div style={{
              fontSize: 10, color: "#6b7280",
              display: "flex", alignItems: "center",
              justifyContent: "flex-end", paddingRight: 4,
            }}>
              {weekdays[di]}
            </div>

            {/* 시간대별 셀 */}
            {row.map((v, hi) => (
              <div
                key={hi}
                style={{
                  height: 22,
                  // 건수가 0이면 회색, 있으면 파란색(농도는 비율에 비례)
                  background: v === 0
                    ? "#f3f4f6"
                    : `rgba(37,99,235,${0.15 + (v / max) * 0.75})`,
                  borderRadius: 2,
                  cursor: "pointer",
                }}
                onMouseEnter={e => {
                  // 셀 위치를 부모(.relative) 기준 좌표로 계산합니다
                  const rect = (e.target as HTMLElement).getBoundingClientRect();
                  const parent = (e.target as HTMLElement)
                    .closest(".relative")!.getBoundingClientRect();
                  setTooltip({
                    di, hi, v,
                    x: rect.left - parent.left + rect.width / 2,
                    y: rect.top - parent.top,
                  });
                }}
                onMouseLeave={() => setTooltip(null)}
              />
            ))}
          </Fragment>
        ))}
      </div>

      {/* 툴팁 팝업 */}
      {tooltip && (
        <div
          className="absolute z-10 bg-gray-800 text-white text-xs rounded px-2 py-1.5 shadow-lg pointer-events-none whitespace-nowrap -translate-x-1/2 -translate-y-full"
          style={{ left: tooltip.x, top: tooltip.y - 6 }}
        >
          <div className="font-semibold">{weekdays[tooltip.di]} {tooltip.hi}{t("statsPage.hourSuffix")}</div>
          <div className="text-gray-300">{tooltip.v.toLocaleString(locale)}{t("statsPage.countUnit")}</div>
        </div>
      )}

      {/* 색상 범례: 적음 → 많음 */}
      <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-1 flex-wrap">
        <span>{t("statsPage.low")}</span>
        {[0.15, 0.3, 0.5, 0.7, 0.9].map(o => (
          <span key={o} className="w-4 h-2.5 rounded-sm inline-block"
            style={{ background: `rgba(37,99,235,${o})` }} />
        ))}
        <span>{t("statsPage.high")}</span>
      </div>
    </div>
  );
}
