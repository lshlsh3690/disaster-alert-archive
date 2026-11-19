"use client";

import Link from "next/link";
import { useTranslation } from "react-i18next";
import PermalinkCopy from "@/components/PermalinkCopy";
import { disasterTypeChipStyle } from "@/ui/disasterTypeColor";
import type { ArchiveAlert } from "@/types/archive";

/** "2026-07-14T09:12:00" → "2026-07-14 09:12". 저장값이 한국 시간이라 시간대 변환을 하지 않는다. */
function formatDateTime(iso: string): string {
  return iso.replace("T", " ").slice(0, 16);
}

/**
 * 원문 기록 — 저장된 원문 값을 번역·법정동 정규화 없이 보여준다. 본문은 원 출처 그대로이고,
 * 수신 지역명은 수집 때 "전체"와 쉼표 앞뒤 공백만 정리된 값이다(DisasterAlertService.cleanRegionString).
 * 인용문 복사는 스토리 1 의 다음 단계(B5)에서 더한다.
 */
export default function AlertRecordClient({ record }: { record: ArchiveAlert }) {
  const { t } = useTranslation();

  const rows: Array<[string, string | null | undefined]> = [
    [t("archive.record.sender"), record.agency?.label],
    [t("archive.record.issuedAt"), formatDateTime(record.createdAt)],
    [t("archive.record.region"), record.originalRegion],
    [t("archive.record.sn"), String(record.sn)],
    [t("archive.record.modifiedAt"), record.modifiedDate ? formatDateTime(record.modifiedDate) : null],
  ];

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-3xl space-y-5 px-4 py-8 sm:px-6">
        <h1 className="text-xl font-bold tracking-tight text-[var(--ink)]">{t("archive.record.title")}</h1>

        <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-5 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <div className="flex flex-wrap items-center gap-2 text-xs">
            {record.disasterType && (
              <span style={disasterTypeChipStyle(record.disasterType)} className="rounded-[var(--radius-pill)] px-2 py-0.5 font-medium">
                {t(`disasterTypes.${record.disasterType}`, { defaultValue: record.disasterType })}
              </span>
            )}
            {record.emergencyLevel && (
              <span className="rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)]">
                {t(`levels.${record.emergencyLevel}`, { defaultValue: record.emergencyLevel })}
              </span>
            )}
          </div>

          <p className="whitespace-pre-line text-[15px] leading-relaxed text-[var(--ink)]">{record.message}</p>

          <dl className="space-y-1.5 border-t border-[var(--line)] pt-3 text-[13px]">
            {rows
              .filter(([, value]) => value)
              .map(([label, value]) => (
                <div key={label} className="flex gap-2">
                  <dt className="w-20 shrink-0 text-[var(--text-muted)]">{label}</dt>
                  <dd className="min-w-0 break-words text-[var(--text-body)]">{value}</dd>
                </div>
              ))}
          </dl>

          <PermalinkCopy path={`/archive/alerts/${record.sn}`} />

          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm font-medium">
            {/* 사건 링크도 이 sn 으로 만든다 — /archive/events/{sn} 이 지금 속한 사건으로 해석한다 */}
            {record.inEvent && (
              <Link href={`/archive/events/${record.sn}`} className="text-[var(--blue)] hover:underline">
                {t("events.viewEvent")} →
              </Link>
            )}
            <Link href={`/alerts/${record.alertId}?source=OFFICIAL`} className="text-[var(--blue)] hover:underline">
              {t("archive.record.viewDetail")} →
            </Link>
          </div>
        </section>

        <p className="text-xs text-[var(--text-subtle)]">{t("archive.record.sourceNote")}</p>
      </div>
    </main>
  );
}
