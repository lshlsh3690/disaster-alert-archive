"use client";

import { useEffect, useMemo, useRef, useState } from "react";

/**
 * 네이티브 <input type="date">는 표시 포맷(연/월/일 순서, 구분자, 요일명 등)을
 * 페이지의 lang 속성이 아니라 브라우저/OS 자체 언어 설정으로 렌더링한다(특히 Chrome).
 * 그래서 사이트 언어를 영어로 바꿔도 날짜 입력만 한글 포맷으로 남는 문제가 있었음.
 * → 네이티브 위젯 대신 직접 그려서 Intl.DateTimeFormat(locale, ...)로 항상 현재
 *   사이트 언어에 맞는 포맷/월/요일명을 강제한다.
 */

function toDateOnly(value: string): Date | null {
  if (!value) return null;
  const [y, m, d] = value.split("-").map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
}

function toValue(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

interface DatePickerProps {
  value: string; // "YYYY-MM-DD" | ""
  onChange: (value: string) => void;
  locale?: string; // 예: "ko-KR", "en-US"
  placeholder?: string;
  className?: string;
  id?: string;
  clearLabel?: string;
  prevMonthLabel?: string;
  nextMonthLabel?: string;
}

export default function DatePicker({
  value,
  onChange,
  locale = "ko-KR",
  placeholder,
  className,
  id,
  clearLabel = "지우기",
  prevMonthLabel = "이전 달",
  nextMonthLabel = "다음 달",
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = useMemo(() => toDateOnly(value), [value]);
  const [viewMonth, setViewMonth] = useState(() => selected ?? new Date());
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (selected) setViewMonth(selected);
  }, [value]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  const displayText = selected
    ? new Intl.DateTimeFormat(locale, { year: "numeric", month: "2-digit", day: "2-digit" }).format(selected)
    : "";

  const monthLabel = new Intl.DateTimeFormat(locale, { year: "numeric", month: "long" }).format(viewMonth);

  // 일요일부터 시작하는 한 주 기준 요일 짧은 이름 (2024-01-07은 일요일)
  const weekdayLabels = useMemo(() => {
    const base = new Date(2024, 0, 7);
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(base);
      d.setDate(base.getDate() + i);
      return new Intl.DateTimeFormat(locale, { weekday: "narrow" }).format(d);
    });
  }, [locale]);

  const days = useMemo(() => {
    const year = viewMonth.getFullYear();
    const month = viewMonth.getMonth();
    const firstDay = new Date(year, month, 1);
    const startOffset = firstDay.getDay(); // 0 (일) ~ 6 (토)
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const cells: { date: Date; inMonth: boolean }[] = [];
    for (let i = 0; i < startOffset; i++) {
      cells.push({ date: new Date(year, month, i - startOffset + 1), inMonth: false });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ date: new Date(year, month, d), inMonth: true });
    }
    while (cells.length % 7 !== 0 || cells.length < 42) {
      const last = cells[cells.length - 1].date;
      const next = new Date(last);
      next.setDate(last.getDate() + 1);
      cells.push({ date: next, inMonth: false });
      if (cells.length >= 42) break;
    }
    return cells;
  }, [viewMonth]);

  const today = new Date();

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        id={id}
        onClick={() => setOpen((prev) => !prev)}
        className={`${className ?? "input"} flex items-center justify-between gap-2 text-left`}
      >
        <span className={displayText ? "" : "text-[var(--text-subtle)]"}>{displayText || placeholder}</span>
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="shrink-0 text-[var(--text-subtle)]" aria-hidden="true">
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 10h18" />
        </svg>
      </button>

      {open && (
        <div className="absolute z-50 mt-1 w-64 rounded-[var(--radius-compact)] border border-[var(--line)] bg-[var(--surface)] p-3 shadow-[0_10px_30px_rgba(28,39,60,0.12)]">
          <div className="mb-2 flex items-center justify-between">
            <button
              type="button"
              onClick={() => setViewMonth((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1))}
              className="rounded-[var(--radius-control)] p-1 text-[var(--text-muted)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]"
              aria-label={prevMonthLabel}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
            </button>
            <span className="text-sm font-semibold text-[var(--ink)]">{monthLabel}</span>
            <button
              type="button"
              onClick={() => setViewMonth((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1))}
              className="rounded-[var(--radius-control)] p-1 text-[var(--text-muted)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]"
              aria-label={nextMonthLabel}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 18l6-6-6-6" /></svg>
            </button>
          </div>
          <div className="grid grid-cols-7 gap-0.5 text-center">
            {weekdayLabels.map((w, i) => (
              <span key={i} className="py-1 text-[11px] font-semibold text-[var(--text-subtle)]">{w}</span>
            ))}
            {days.map(({ date, inMonth }, i) => {
              const isSelected = selected && sameDay(date, selected);
              const isToday = sameDay(date, today);
              return (
                <button
                  key={i}
                  type="button"
                  onClick={() => {
                    onChange(toValue(date));
                    setOpen(false);
                  }}
                  className={`rounded-[var(--radius-control)] py-1 text-xs transition-colors ${
                    isSelected
                      ? "bg-[var(--blue)] font-semibold text-white"
                      : inMonth
                      ? "text-[var(--text-body)] hover:bg-[var(--blue-soft)]"
                      : "text-[var(--text-subtle)] hover:bg-[var(--blue-soft)]"
                  } ${isToday && !isSelected ? "ring-1 ring-inset ring-[var(--blue)]" : ""}`}
                >
                  {date.getDate()}
                </button>
              );
            })}
          </div>
          {value && (
            <button
              type="button"
              onClick={() => { onChange(""); setOpen(false); }}
              className="mt-2 w-full rounded-[var(--radius-control)] border border-[var(--line)] py-1 text-xs text-[var(--text-muted)] hover:bg-[var(--blue-soft)]"
            >
              {clearLabel}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
