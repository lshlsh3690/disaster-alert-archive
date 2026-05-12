// frontend/src/hooks/useI18n.ts
import { i18n } from "@/constants/i18n";
import { useLanguageStore } from "@/store/languageStore";

export function useI18n() {
    const language = useLanguageStore((state) => state.language);
    return i18n[language];
}