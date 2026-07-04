"use client";

import type { LibItem, WidgetItem } from "./_constants";
import { WIDGET_LIBRARY } from "./_constants";

export function WidgetLibrary({ open, onClose, layout, onAdd, onRemove }: {
  open: boolean; onClose: () => void; layout: WidgetItem[];
  onAdd: (item: LibItem) => void; onRemove: (id: string) => void;
}) {
  if (!open) return null;
  // 현재 레이아웃에서 사용 중인 위젯의 libId → 인스턴스 id 맵 (추가/삭제 버튼 분기에 사용)
  const usedMap = new Map(layout.map(w => [w.libId, w.id]));
  return (
    <>
      <div className="fixed inset-0 bg-black/30 z-10" onClick={onClose} />
      <aside className="fixed top-0 right-0 bottom-0 w-80 bg-[var(--surface)] shadow-2xl z-20 flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-center gap-2 px-4 py-3.5 border-b border-[var(--line)]">
          <h3 className="flex-1 text-sm font-bold text-[var(--ink)]">위젯 관리</h3>
          <button onClick={onClose} className="w-7 h-7 border border-[var(--line)] rounded-lg text-[var(--text-subtle)] text-sm">×</button>
        </div>
        <div className="flex-1 overflow-auto p-4 space-y-2">
          {WIDGET_LIBRARY.map(item => {
            const widgetId = usedMap.get(item.id);
            const added = !!widgetId;
            return (
              <div key={item.id} className={`flex items-start gap-2.5 p-3 border rounded-lg transition-colors ${added ? "border-blue-200 bg-[var(--blue-soft)]" : "border-[var(--line)] bg-[var(--surface)]"}`}>
                <span className="text-xl leading-none">{item.icon}</span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-bold text-[var(--ink)]">{item.title}</span>
                    {added && <span className="text-xs font-bold text-[var(--blue)] bg-blue-100 px-1.5 py-0.5 rounded">표시 중</span>}
                    {item.sample && <span className="text-xs text-amber-500">샘플</span>}
                  </div>
                  <div className="text-xs text-[var(--text-subtle)] mt-0.5">{item.desc}</div>
                </div>
                {added ? (
                  <button onClick={() => onRemove(widgetId!)}
                    className="shrink-0 px-2 py-1 text-xs font-semibold border border-red-200 text-red-500 rounded hover:bg-red-50 transition-colors">
                    삭제
                  </button>
                ) : (
                  <button onClick={() => onAdd(item)}
                    className="shrink-0 px-2 py-1 text-xs font-semibold border border-blue-300 text-[var(--blue)] rounded hover:bg-[var(--blue-soft)] transition-colors">
                    추가
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
