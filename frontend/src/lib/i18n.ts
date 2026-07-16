// frontend/src/lib/i18n.ts
import i18next from "i18next";
import { initReactI18next } from "react-i18next";
import { i18n as translations } from "@/constants/i18n";
import { useLanguageStore } from "@/store/languageStore";

i18next.use(initReactI18next).init({
  resources: {
    ko: { translation: translations.ko },
    en: { translation: translations.en },
    ja: { translation: translations.ja },
    zh: { translation: translations.zh },
    vi: { translation: translations.vi },
    th: { translation: translations.th },
  },
  lng: useLanguageStore.getState().language,
  fallbackLng: "ko",
  keySeparator: ".",
  interpolation: {
    escapeValue: false,
    prefix: "{",
    suffix: "}",
  },
});

export default i18next;
