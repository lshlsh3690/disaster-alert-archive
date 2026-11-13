// frontend/src/components/Footer.tsx
"use client";
import { useTranslation } from "react-i18next";

export default function Footer() {
  const { t } = useTranslation();
  return (
    <footer className="bg-[var(--surface)] border-t border-[var(--line)] px-4 py-5 text-center text-[13px] leading-relaxed text-[var(--text-muted)]">
      {t("footer")}
      {" | GitHub: "}
      <a
        href="https://github.com/lshlsh3690/disaster-alert-archive"
        target="_blank"
        rel="noopener noreferrer"
        className="break-all underline underline-offset-2 hover:text-[var(--text-body)]"
      >
        github.com/lshlsh3690/disaster-alert-archive
      </a>
    </footer>
  );
}