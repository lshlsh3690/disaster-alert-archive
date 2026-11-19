"use client";

import type { LibItem, WidgetItem } from "./_constants";
import { getWidgetLibrary } from "./_constants";
import { useTranslation } from "react-i18next";

export function WidgetLibrary({ open, onClose, layout, onAdd, onRemove }: {
  open: boolean; onClose: () => void; layout: WidgetItem[];
  onAdd: (item: LibItem) => void; onRemove: (id: string) => void;
}) {
  const { t } = useTranslation();
  if (!open) return null;
  const widgetLibrary = getWidgetLibrary(t);
  // 현재 레이아웃에서 사용 중인 위젯의 libId → 인스턴스 id 맵 (추가/삭제 버튼 분기에 사용)
  const usedMap = new Map(layout.map(w => [w.libId, w.id]));
  return (
    <>
      <div className="fixed inset-0 bg-black/30 z-10" onClick={onClose} />
      <aside className="fixed top-0 right-0 bottom-0 w-[360px] max-w-full bg-[var(--krds-gray-0)] border-l border-[var(--krds-gray-20)] z-20 flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-start gap-3 px-5 py-4 border-b border-[var(--krds-gray-20)]">
          <div className="flex-1 min-w-0">
            <h3 className="text-[19px] font-bold text-[var(--krds-gray-90)]">{t("statsPage.widgetLibrary")}</h3>
            <p className="mt-1 text-[15px] text-[var(--krds-gray-50)]">{t("statsPage.widgetLibraryDescription")}</p>
          </div>
          <button type="button" onClick={onClose} className="stats-icon-btn" aria-label={t("statsPage.close")}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12" /><path d="M18 6 6 18" />
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-auto p-5 space-y-3">
          {widgetLibrary.map(item => {
            const widgetId = usedMap.get(item.id);
            const added = !!widgetId;
            return (
              <div key={item.id} className="krds-card flex items-start gap-3 p-4">
                <div className="flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="text-[16px] font-bold text-[var(--krds-gray-90)]">{item.title}</span>
                    {added && <span className="krds-badge krds-badge--info">{t("statsPage.visible")}</span>}
                  </div>
                  <div className="text-[15px] text-[var(--krds-gray-50)] mt-1">{item.desc}</div>
                </div>
                {added ? (
                  <button type="button" onClick={() => onRemove(widgetId!)}
                    className="krds-btn krds-btn--secondary krds-btn--sm shrink-0">
                    {t("statsPage.remove")}
                  </button>
                ) : (
                  <button type="button" onClick={() => onAdd(item)}
                    className="krds-btn krds-btn--secondary krds-btn--sm shrink-0">
                    {t("statsPage.add")}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </aside>
    </>
  );
}
