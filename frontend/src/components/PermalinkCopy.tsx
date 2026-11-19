"use client";

import { useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * "링크 복사" 버튼. path 는 "/archive/events/123" 처럼 사이트 내부 경로로 받고, 누른 순간의 접속 주소(origin)를 붙여 복사한다.
 * 사건 ID 는 백필 때 바뀌므로 복사되는 주소는 대표 알림 sn 기반 고정 주소다(FR-004) — 화면에는 주소를 보이지 않는다.
 */
export default function PermalinkCopy({ path }: { path: string }) {
  const { t } = useTranslation();
  const [status, setStatus] = useState<"idle" | "copied" | "failed">("idle");
  const [url, setUrl] = useState("");

  const copy = async () => {
    const full = `${window.location.origin}${path}`;
    setUrl(full);
    try {
      await navigator.clipboard.writeText(full);
      setStatus("copied");
      window.setTimeout(() => setStatus("idle"), 2000);
    } catch {
      // http 접속·권한 거부 등으로 클립보드를 못 쓰면 주소를 보여 주고 직접 복사하도록 안내한다(이때는 자동으로 숨기지 않는다)
      setStatus("failed");
    }
  };

  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <button type="button" onClick={copy} className="krds-btn krds-btn--secondary krds-btn--sm">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M10 13a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1.5 1.5" />
            <path d="M14 11a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1.5-1.5" />
          </svg>
          {t("archive.copyLink")}
        </button>
        <span role="status" aria-live="polite" className="text-[15px] text-[var(--text-muted)]">
          {status === "copied" ? t("archive.copied") : status === "failed" ? t("archive.copyFailed") : ""}
        </span>
      </div>
      {status === "failed" && (
        <input
          readOnly
          value={url}
          onFocus={(e) => e.currentTarget.select()}
          aria-label={t("archive.copyLink")}
          className="krds-control text-[15px]"
        />
      )}
    </div>
  );
}
