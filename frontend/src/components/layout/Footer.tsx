// frontend/src/components/Footer.tsx
"use client";
import { useI18n } from "@/hooks/useI18n";

export default function Footer() {
  const t = useI18n();
  return (
    <footer className="bg-[var(--surface)] border-t border-[var(--line)] px-4 py-5 text-center text-[13px] leading-relaxed text-[var(--text-muted)]">
      {t("footer")}
    </footer>
  );
}