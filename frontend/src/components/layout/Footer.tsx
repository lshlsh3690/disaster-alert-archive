// frontend/src/components/Footer.tsx
"use client";
import { useI18n } from "@/hooks/useI18n";

export default function Footer() {
  const t = useI18n();
  return (
    <footer className="bg-gray-100 p-4 text-center text-sm">
      {t.footer}
    </footer>
  );
}