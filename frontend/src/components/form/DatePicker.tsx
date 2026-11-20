"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

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
  /** 고를 수 있는 가장 이른 날짜("YYYY-MM-DD"). 이보다 앞선 날은 누를 수 없고, 그 달보다 앞으로는 넘어가지 않는다. */
  min?: string;
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
  min,
}: DatePickerProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  // 달력 화면: 일(기본) / 월 고르기 / 연 고르기
  const [view, setView] = useState<"day" | "month" | "year">("day");
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

  // 연·월 고르기 화면에 쓰는 이름(사이트 언어 기준)
  const monthShort = useMemo(
    () => Array.from({ length: 12 }, (_, m) => new Intl.DateTimeFormat(locale, { month: "short" }).format(new Date(2024, m, 1))),
    [locale]
  );
  const yearName = (y: number) => new Intl.DateTimeFormat(locale, { year: "numeric" }).format(new Date(y, 0, 1));
  const monthTitle = new Intl.DateTimeFormat(locale, { year: "numeric", month: "long" }).format(viewMonth);

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
  const minDate = useMemo(() => (min ? toDateOnly(min) : null), [min]);
  const isBeforeMin = (date: Date) => minDate !== null && date < minDate;
  // 보고 있는 달이 최소 날짜가 든 달이면 이전 달로 넘어가지 않는다
  const atMinMonth =
    minDate !== null &&
    (viewMonth.getFullYear() < minDate.getFullYear() ||
      (viewMonth.getFullYear() === minDate.getFullYear() && viewMonth.getMonth() <= minDate.getMonth()));

  // 먼 과거로 한 번에 가도록 제목을 누르면 월 → 연 고르기 화면으로 올라간다(일 ← 월 ← 연).
  // 연도는 최소 날짜(없으면 10년 전)부터 올해(보고 있거나 고른 날짜가 더 뒤면 그 해)까지
  const firstYear = minDate ? minDate.getFullYear() : today.getFullYear() - 10;
  const lastYear = Math.max(today.getFullYear(), viewMonth.getFullYear(), selected?.getFullYear() ?? 0);
  const years = Array.from({ length: lastYear - firstYear + 1 }, (_, i) => firstYear + i);
  const viewYear = viewMonth.getFullYear();
  const goTo = (year: number, month: number) => {
    const target = new Date(year, month, 1);
    // 최소 날짜가 든 달보다 앞이면 그 달로 맞춘다(예: 2023년 1월을 보고 있다가 연도만 바꾼 경우 9월로)
    setViewMonth(minDate && target < new Date(minDate.getFullYear(), minDate.getMonth(), 1)
      ? new Date(minDate.getFullYear(), minDate.getMonth(), 1)
      : target);
  };
  const isMonthBeforeMin = (m: number) =>
    minDate !== null && (viewYear < minDate.getFullYear() || (viewYear === minDate.getFullYear() && m < minDate.getMonth()));

  const navBtn =
    "rounded-[var(--radius-control)] p-1 text-[var(--text-muted)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)] disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent";
  const prevIcon = <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 18l-6-6 6-6" /></svg>;
  const nextIcon = <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 18l6-6-6-6" /></svg>;
  const titleBtn =
    "rounded-[var(--radius-control)] px-2 py-1 text-sm font-semibold text-[var(--ink)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]";
  const cellBtn = (active: boolean) =>
    `rounded-[var(--radius-control)] py-2 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-30 ${
      active ? "bg-[var(--blue)] font-semibold text-white" : "text-[var(--text-body)] hover:bg-[var(--blue-soft)] disabled:hover:bg-transparent"
    }`;

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        id={id}
        onClick={() => {
          setView("day");
          setOpen((prev) => !prev);
        }}
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
            {view === "day" && (
              <>
                <button type="button" onClick={() => goTo(viewYear, viewMonth.getMonth() - 1)} disabled={atMinMonth} className={navBtn} aria-label={prevMonthLabel}>
                  {prevIcon}
                </button>
                <button type="button" onClick={() => setView("month")} className={titleBtn} aria-label={`${monthTitle} — ${t("datePicker.month")}`}>
                  {monthTitle}
                </button>
                <button type="button" onClick={() => goTo(viewYear, viewMonth.getMonth() + 1)} className={navBtn} aria-label={nextMonthLabel}>
                  {nextIcon}
                </button>
              </>
            )}
            {view === "month" && (
              <>
                <button type="button" onClick={() => goTo(viewYear - 1, viewMonth.getMonth())} disabled={viewYear <= firstYear} className={navBtn} aria-label={t("datePicker.prevYear")}>
                  {prevIcon}
                </button>
                <button type="button" onClick={() => setView("year")} className={titleBtn} aria-label={`${yearName(viewYear)} — ${t("datePicker.year")}`}>
                  {yearName(viewYear)}
                </button>
                <button type="button" onClick={() => goTo(viewYear + 1, viewMonth.getMonth())} disabled={viewYear >= lastYear} className={navBtn} aria-label={t("datePicker.nextYear")}>
                  {nextIcon}
                </button>
              </>
            )}
            {view === "year" && (
              <span className="w-full py-1 text-center text-sm font-semibold text-[var(--ink)]">
                {yearName(firstYear)} – {yearName(lastYear)}
              </span>
            )}
          </div>

          {view === "year" && (
            <div className="grid grid-cols-3 gap-1">
              {years.map((y) => (
                <button key={y} type="button" onClick={() => { goTo(y, viewMonth.getMonth()); setView("month"); }} className={cellBtn(y === viewYear)}>
                  {yearName(y)}
                </button>
              ))}
            </div>
          )}

          {view === "month" && (
            <div className="grid grid-cols-3 gap-1">
              {monthShort.map((name, m) => (
                <button
                  key={m}
                  type="button"
                  disabled={isMonthBeforeMin(m)}
                  onClick={() => { goTo(viewYear, m); setView("day"); }}
                  className={cellBtn(m === viewMonth.getMonth())}
                >
                  {name}
                </button>
              ))}
            </div>
          )}

          {view === "day" && (
          <div className="grid grid-cols-7 gap-0.5 text-center">
            {weekdayLabels.map((w, i) => (
              <span key={i} className="py-1 text-[11px] font-semibold text-[var(--text-subtle)]">{w}</span>
            ))}
            {days.map(({ date, inMonth }, i) => {
              const isSelected = selected && sameDay(date, selected);
              const isToday = sameDay(date, today);
              const disabled = isBeforeMin(date);
              return (
                <button
                  key={i}
                  type="button"
                  disabled={disabled}
                  onClick={() => {
                    onChange(toValue(date));
                    setOpen(false);
                  }}
                  className={`rounded-[var(--radius-control)] py-1 text-xs transition-colors ${
                    disabled
                      ? "cursor-not-allowed text-[var(--text-subtle)] opacity-40"
                      : isSelected
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
          )}
          {value && view === "day" && (
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
