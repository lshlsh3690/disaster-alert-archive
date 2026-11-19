"use client";

import Link from "next/link";
import { useTranslation } from "react-i18next";
import type { ArchiveEventResolve } from "@/types/archive";

/** 사건 링크의 알림이 지금 어떤 사건에도 묶여 있지 않을 때 — 빈 화면 대신 안내와 원문 요약. */
export default function UnboundAlertClient({ alert }: { alert: ArchiveEventResolve["alert"] }) {
  const { t } = useTranslation();

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-3xl space-y-4 px-4 py-8 sm:px-6">
        <p className="rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 text-sm text-[var(--text-body)]">
          {t("archive.unbound")}
        </p>
        {alert && (
          <section className="space-y-2 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4">
            <div className="flex flex-wrap gap-2 text-xs text-[var(--text-muted)]">
              {alert.disasterType && (
                <span>{t(`disasterTypes.${alert.disasterType}`, { defaultValue: alert.disasterType })}</span>
              )}
              {alert.emergencyLevel && (
                <span>{t(`levels.${alert.emergencyLevel}`, { defaultValue: alert.emergencyLevel })}</span>
              )}
              <span className="text-[var(--text-subtle)]">{alert.createdAt.replace("T", " ").slice(0, 16)}</span>
            </div>
            <p className="whitespace-pre-line text-sm leading-relaxed text-[var(--text-body)]">{alert.message}</p>
            <Link
              href={`/alerts/${alert.alertId}?source=OFFICIAL`}
              className="inline-block text-xs font-medium text-[var(--blue)] hover:underline"
            >
              {t("archive.viewAlert")}
            </Link>
          </section>
        )}
      </div>
    </main>
  );
}
