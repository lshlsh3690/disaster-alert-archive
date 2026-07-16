// frontend/src/components/providers/I18nSync.tsx
"use client";

import { useEffect } from "react";
import i18next from "@/lib/i18n";
import { useLanguageStore } from "@/store/languageStore";

export default function I18nSync() {
  const language = useLanguageStore((state) => state.language);

  useEffect(() => {
    i18next.changeLanguage(language);
  }, [language]);

  return null;
}
