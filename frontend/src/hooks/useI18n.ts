// frontend/src/hooks/useI18n.ts
import "@/lib/i18n";
import { useTranslation } from "react-i18next";

export function useI18n() {
  const { t } = useTranslation();
  return t;
}
