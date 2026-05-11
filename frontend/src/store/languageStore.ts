// frontend/src/store/languageStore.ts
import { create } from "zustand";
import { persist } from "zustand/middleware";
import { LangCode } from "@/constants/language";

type LanguageStore = {
  language: LangCode;
  setLanguage: (lang: LangCode) => void;
};

export const useLanguageStore = create<LanguageStore>()(
  persist(
    (set) => ({
      language: "ko",
      setLanguage: (lang) => set({ language: lang }),
    }),
    {
      name: "disaster-alert-language",
    },
  ),
);
