"use client";

import { getLevelMeta } from "../_constants";
import type { LevelStat } from "../_constants";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE } from "./common";

/**
 * LevelsCard
 *
 * 재난 경보 단계(안전안내·긴급재난·위급재난)의 건수와 비율을
 * 색상 카드 + 게이지 바 형태로 보여줍니다.
 *
 * @param data - { level: "LEVEL_1"|"LEVEL_2"|"LEVEL_3"|null, count: number }[]
 *              LEVEL_META(_constants.ts)에서 각 단계의 색상·텍스트를 가져옵니다.
 */
export function LevelsCard({ data }: { data: LevelStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const levelMeta = getLevelMeta(t);
  // LEVEL_1, LEVEL_2, LEVEL_3 순서를 고정하고 데이터가 없으면 count=0으로 채웁니다
  const levels = ["LEVEL_1", "LEVEL_2", "LEVEL_3"].map(code => {
    const meta = levelMeta[code];
    const count = data.find(d => d.level === code)?.count ?? 0;
    return { code, ...meta, count };
  });

  const total = levels.reduce((s, l) => s + l.count, 0) || 1; // 0으로 나누기 방지
  const urgentPct = Math.round((levels[2].count / total) * 100); // LEVEL_3(위급) 비율

  return (
    <div className="flex flex-col gap-3 flex-1">
      {/* 단계별 카드 3개 */}
      <div className="flex gap-2">
        {levels.map(l => (
          <div key={l.code}
            className={`flex-1 text-center rounded-lg border py-2.5 ${l.bg} ${l.border}`}>
            <div className={`text-xs font-semibold mb-1 ${l.textCls}`}>{l.text}</div>
            <div className={`text-xl font-extrabold ${l.textCls}`}>
              {l.count.toLocaleString(locale)}
            </div>
            <div className="text-xs text-gray-400">
              {Math.round((l.count / total) * 100)}%
            </div>
          </div>
        ))}
      </div>

      {/* 비율 게이지 바: flex의 flex 값을 건수로 지정하면 비율대로 나눠집니다 */}
      <div className="flex h-2.5 rounded-full overflow-hidden">
        {levels.map(l => (
          <div key={l.code} style={{ flex: l.count || 0, background: l.solid }} />
        ))}
      </div>

      <p className="text-xs text-gray-500 leading-relaxed">
        {t("statsPage.urgentSummary", { total: total.toLocaleString(locale), percent: urgentPct })}
      </p>
    </div>
  );
}
