"use client";

import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * 기록관 영구 링크 표시 + 복사. path 는 "/archive/events/123" 처럼 사이트 내부 경로로 받고,
 * 화면에 보일 전체 주소는 마운트 뒤 현재 접속 주소(origin)로 만든다 — 서버 렌더와 어긋나지 않게.
 */
export default function PermalinkCopy({ path }: { path: string }) {
  const { t } = useTranslation();
  const [url, setUrl] = useState(path);
  const [status, setStatus] = useState<"idle" | "copied" | "failed">("idle");

  useEffect(() => {
    setUrl(`${window.location.origin}${path}`);
  }, [path]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setStatus("copied");
    } catch {
      // http 접속·권한 거부 등으로 클립보드를 못 쓰면 주소를 직접 선택해 복사하도록 안내
      setStatus("failed");
    }
    window.setTimeout(() => setStatus("idle"), 2000);
  };

  return (
    <div className="space-y-1">
      {/* 복사 결과는 라벨 옆에 붙인다 — 아래 줄로 두면 평소에도 빈 줄 높이만큼 카드 하단이 비어 보인다 */}
      <div className="flex flex-wrap items-baseline gap-x-2 text-xs">
        <span className="font-medium text-[var(--text-muted)]">{t("archive.permalink")}</span>
        <span role="status" aria-live="polite" className="text-[var(--text-subtle)]">
          {status === "copied" ? t("archive.copied") : status === "failed" ? t("archive.copyFailed") : ""}
        </span>
      </div>
      <div className="flex items-center gap-2">
        <input
          readOnly
          value={url}
          onFocus={(e) => e.currentTarget.select()}
          aria-label={t("archive.permalink")}
          className="min-h-11 min-w-0 flex-1 rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--canvas)] px-2 py-1 text-xs text-[var(--text-body)] sm:min-h-0"
        />
        {/* 모바일 터치 타겟 44px 확보, 데스크톱은 원래 크기 */}
        <button
          type="button"
          onClick={copy}
          className="min-h-11 min-w-11 shrink-0 rounded-[var(--radius-control)] border border-[var(--line)] px-3 py-1 text-xs font-medium text-[var(--blue)] hover:bg-[var(--blue-soft)] sm:min-h-0 sm:min-w-0 sm:px-2.5"
        >
          {t("archive.copy")}
        </button>
      </div>
    </div>
  );
}
