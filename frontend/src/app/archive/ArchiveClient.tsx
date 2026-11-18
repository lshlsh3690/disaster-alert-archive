"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslation } from "react-i18next";
import { useMajorEvents } from "@/lib/queries/useArchive";
import { disasterTypeChipStyle } from "@/ui/disasterTypeColor";
import { formatEventPeriod } from "@/utils/eventDate";

/**
 * 연도별 주요 사건 — 포함 알림 수가 많은 순서로 최대 10개.
 * 순서는 보여주되 "1위" 같은 순위 번호·점수는 붙이지 않는다(FR-013, 위험도 삭제 원칙).
 */
export default function ArchiveClient({ year }: { year: number | null }) {
  const { t } = useTranslation();
  const router = useRouter();
  const { data, isLoading, isError } = useMajorEvents(year);

  // 응답 year 는 요청에 year 가 없을 때 서버가 고른 최근 연도
  const selectedYear = data?.year ?? year;

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-3xl space-y-5 px-4 py-8 sm:px-6">
        <header className="space-y-2">
          <h1 className="text-xl font-bold tracking-tight text-[var(--ink)]">{t("archive.title")}</h1>
          <p className="text-sm text-[var(--text-muted)]">{t("archive.description")}</p>
        </header>

        {data && data.years.length > 0 && (
          <div className="flex items-center gap-2">
            <label htmlFor="archive-year" className="text-sm font-medium text-[var(--text-body)]">
              {t("archive.year")}
            </label>
            <select
              id="archive-year"
              value={selectedYear ?? ""}
              onChange={(e) => router.push(`/archive?year=${e.target.value}`)}
              className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-2 py-1 text-sm text-[var(--text-body)]"
            >
              {/* 목록에 없는 연도를 주소로 직접 연 경우에도 선택 상태가 비지 않게 */}
              {selectedYear != null && !data.years.includes(selectedYear) && (
                <option value={selectedYear}>{selectedYear}</option>
              )}
              {data.years.map((y) => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
          </div>
        )}

        {isLoading && <p className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t("loading")}</p>}
        {isError && <p className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t("archive.loadFailed")}</p>}
        {data && data.events.length === 0 && (
          <p className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t("archive.empty")}</p>
        )}

        {data && data.events.length > 0 && (
          <ul className="space-y-3">
            {data.events.map((e) => (
              <li key={e.representativeSn}>
                <Link
                  href={`/archive/events/${e.representativeSn}`}
                  prefetch={false}
                  className="block rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)] transition-shadow hover:shadow-[0_12px_28px_rgba(28,39,60,0.08)]"
                >
                  <h2 className="font-semibold leading-snug text-[var(--ink)]">{e.eventTitle}</h2>
                  <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                    {e.primaryDisasterType && (
                      <span style={disasterTypeChipStyle(e.primaryDisasterType)} className="rounded-[var(--radius-pill)] px-2 py-0.5 font-medium">
                        {t(`disasterTypes.${e.primaryDisasterType}`, { defaultValue: e.primaryDisasterType })}
                      </span>
                    )}
                    {e.primaryRegionName && (
                      <span className="rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)]">
                        {e.primaryRegionName}
                      </span>
                    )}
                    <span className="rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)]">
                      {t("events.relatedCount")} {e.alertCount}{t("alertList.countUnit")}
                    </span>
                    <span className="text-[var(--text-subtle)]">
                      {formatEventPeriod(e.firstAlertAt, e.lastAlertAt, { sameDay: t("events.sameDay"), daysSpan: t("events.daysSpan") })}
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {data && data.events.length > 0 && (
          <p className="text-xs text-[var(--text-subtle)]">{t("archive.selectionNote")}</p>
        )}
      </div>
    </main>
  );
}
