"use client";

import Link from "next/link";
import { useLatestAlerts } from "@/lib/queries/useAlerts";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import { LatestAlert } from "@/types/alerts";

const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };

export default function LatestAlertsSection({ limit = 5 }: { limit?: number }) {
  const t = useI18n();
  const language = useLanguageStore((state) => state.language);
  const { data, isLoading, isError } = useLatestAlerts(limit, language);

  function formatKST(iso: string) {
    return new Date(iso).toLocaleString(LANG_LOCALE[language] ?? "ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  if (isLoading) return <p className="feed-state">{t("loading")}</p>;
  if (isError) return <p className="feed-state feed-state--error">{t("dashboard.loadError")}</p>;
  if (!data || data.length === 0) return <p className="feed-state">{t("dashboard.noAlerts")}</p>;

  return (
    <ul>
      {data.map((a: LatestAlert) => {
        const translated = language !== "ko";
        const region = (translated && a.translatedTopRegion) || a.topRegion || t("dashboard.unknownRegion");
        const message = (translated && a.translatedMessage) || a.message;
        const disasterType = (translated && a.translatedDisasterType) || a.disasterType;
        return (
          <li key={a.id}>
            <Link href={`/alerts/${a.id}`} className="feed-item">
              <div className="feed-item__head">
                <span className="feed-item__region">{region}</span>
                <time className="feed-item__time">{formatKST(a.createdAt)}</time>
              </div>
              <p className="feed-item__msg">
                {disasterType && <span className="feed-item__type">{disasterType}</span>}
                {message}
              </p>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
