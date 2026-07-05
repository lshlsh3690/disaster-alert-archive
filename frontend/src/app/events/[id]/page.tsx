"use client";

import { useState, useMemo, useEffect } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEvent } from "@/lib/queries/useEvents";
import { useLanguageStore } from "@/store/languageStore";
import { useI18n } from "@/hooks/useI18n";
import { disasterTypeChipStyle } from "@/ui/disasterTypeColor";
import { fetchSigungu } from "@/api/alertApi";
import { formatEventPeriod } from "@/utils/eventDate";
import { REPETITIVE_TYPES } from "@/constants/eventTypes";
import type { EventAlertItem } from "@/types/events";

function splitRegion(name?: string | null) {
  const parts = (name ?? "").trim().split(/\s+/);
  return { sido: parts[0] || "", sigungu: parts.slice(1).join(" ") };
}

export default function EventDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const lang = useLanguageStore((s) => s.language);
  const t = useI18n();
  const { data, isLoading } = useEvent(id, lang);
  const [allExpanded, setAllExpanded] = useState(false);
  const [expandedDates, setExpandedDates] = useState<Set<string>>(new Set());

  // 대표 지역명 번역: 시도는 t.metros, 시군구는 /districts/sigungu 응답으로 매핑
  const [sigunguMap, setSigunguMap] = useState<Map<string, string> | null>(null);
  useEffect(() => {
    setSigunguMap(null);
    if (lang === "ko" || !data?.primaryRegionName) return;
    const { sido, sigungu } = splitRegion(data.primaryRegionName);
    if (!sido || !sigungu) return;
    let cancelled = false;
    fetchSigungu(sido, lang)
      .then((list) => {
        if (cancelled) return;
        const map = new Map<string, string>();
        (list ?? []).forEach((s) => { if (s.translatedName) map.set(s.name, s.translatedName); });
        setSigunguMap(map);
      })
      .catch(() => { /* 번역 실패 시 원문 유지 */ });
    return () => { cancelled = true; };
  }, [data, lang]);

  const regionDisplay = useMemo(() => {
    const name = data?.primaryRegionName;
    if (!name) return "";
    if (lang === "ko") return name;
    const { sido, sigungu } = splitRegion(name);
    const tSido = t.metros?.[sido as keyof typeof t.metros] ?? sido;
    if (!sigungu) return tSido;
    return `${tSido} ${sigunguMap?.get(sigungu) ?? sigungu}`;
  }, [data, lang, t, sigunguMap]);

  const isRepetitive = useMemo(
    () => !!data?.primaryDisasterType && REPETITIVE_TYPES.has(data.primaryDisasterType),
    [data]
  );

  const timelineGroups = useMemo(() => {
    if (!data?.timeline) return [];
    if (!isRepetitive) return [{ date: null, items: data.timeline }];

    const map = new Map<string, EventAlertItem[]>();
    for (const item of data.timeline) {
      const d = item.createdAt.slice(0, 10);
      if (!map.has(d)) map.set(d, []);
      map.get(d)!.push(item);
    }
    return Array.from(map.entries()).map(([date, items]) => ({ date, items }));
  }, [data, isRepetitive]);

  const toggleDate = (date: string) => {
    setExpandedDates((prev) => {
      const next = new Set(prev);
      if (next.has(date)) next.delete(date);
      else next.add(date);
      return next;
    });
  };

  if (isLoading) return <main className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t.loading}</main>;
  if (!data) return <main className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t.events.notFound}</main>;

  const title = data.translatedTitle ?? data.eventTitle;
  const period = formatEventPeriod(data.firstAlertAt, data.lastAlertAt, t.events);

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-3xl space-y-5 px-4 py-8 sm:px-6">
      {/* 헤더 */}
      <div className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-5 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
        <div className="flex items-start justify-between gap-3">
          <h1 className="flex-1 text-xl font-bold leading-snug tracking-tight text-[var(--ink)]">{title}</h1>
          <span
            className={`shrink-0 rounded-[var(--radius-pill)] px-2 py-0.5 text-xs font-medium ${
              data.active ? "bg-[var(--coral-soft)] text-[#c0473b]" : "bg-[#eef1f5] text-[var(--text-muted)]"
            }`}
          >
            {data.active ? t.events.badgeActive : t.events.badgePast}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs">
          {data.primaryDisasterType && (
            <span style={disasterTypeChipStyle(data.primaryDisasterType)} className="rounded-[var(--radius-pill)] px-2 py-0.5 font-medium">
              {t.disasterTypes[data.primaryDisasterType as keyof typeof t.disasterTypes] ?? data.primaryDisasterType}
            </span>
          )}
          {data.primaryRegionName && (
            <span className="inline-flex items-center gap-1 rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)]">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z" />
                <circle cx="12" cy="10" r="3" />
              </svg>
              {regionDisplay}
            </span>
          )}
          <span className="inline-flex items-center gap-1 rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)]">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M4 4h16v12H7l-3 3V4z" />
            </svg>
            {t.events.relatedCount} {data.alertCount}{t.alertList.countUnit}
          </span>
          <span className="text-[var(--text-subtle)]">{period}</span>
        </div>
        {data.primaryRegionName && (
          <p className="text-xs text-[var(--text-subtle)]">{t.events.approxRegion}</p>
        )}
      </div>

      {/* 타임라인 */}
      <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold text-[var(--ink)]">{t.events.timelineTitle}</h2>
          {isRepetitive && (
            <button
              className="text-xs font-medium text-[var(--blue)] hover:underline"
              onClick={() => {
                if (allExpanded) {
                  setExpandedDates(new Set());
                } else {
                  setExpandedDates(new Set(timelineGroups.map((g) => g.date!)));
                }
                setAllExpanded((v) => !v);
              }}
            >
              {allExpanded ? t.events.collapseAll : t.events.expandAll}
            </button>
          )}
        </div>

        <div className="relative">
          {timelineGroups.map((group, gi) => {
            if (group.date === null) {
              return group.items.map((item, i) => (
                <TimelineNode key={item.alertId} item={item} isLast={i === group.items.length - 1} lang={lang} t={t} />
              ));
            }

            const isOpen = expandedDates.has(group.date);
            const dateLabel = group.date.slice(2).replace(/-/g, "/");
            return (
              <div key={group.date} className="mb-2">
                <button
                  className="flex w-full items-center gap-2 rounded-[var(--radius-control)] px-2 py-1 text-left hover:bg-[var(--blue-soft)]"
                  onClick={() => toggleDate(group.date!)}
                >
                  <svg
                    className={`shrink-0 text-[var(--text-subtle)] transition-transform ${isOpen ? "rotate-90" : ""}`}
                    width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"
                  >
                    <path d="m9 6 6 6-6 6" />
                  </svg>
                  <span className="text-sm font-medium text-[var(--text-body)]">
                    {dateLabel} ({group.items.length}{t.alertList.countUnit})
                  </span>
                </button>
                {isOpen && (
                  <div className="ml-4 mt-1 space-y-1 border-l-2 border-[var(--line)] pl-3">
                    {group.items.map((item, i) => (
                      <TimelineNode
                        key={item.alertId}
                        item={item}
                        isLast={gi === timelineGroups.length - 1 && i === group.items.length - 1}
                        lang={lang}
                        t={t}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>
      </div>
    </main>
  );
}

function TimelineNode({
  item,
  isLast,
  lang,
  t,
}: {
  item: EventAlertItem;
  isLast: boolean;
  lang: string;
  t: ReturnType<typeof useI18n>;
}) {
  const dt = new Date(item.createdAt);
  const timeStr = `${String(dt.getFullYear()).slice(2)}/${String(dt.getMonth() + 1).padStart(2, "0")}/${String(dt.getDate()).padStart(2, "0")} ${String(dt.getHours()).padStart(2, "0")}:${String(dt.getMinutes()).padStart(2, "0")}`;

  const message =
    lang !== "ko" && item.translatedMessage ? item.translatedMessage : item.message;
  const type =
    lang !== "ko" && item.translatedType
      ? item.translatedType
      : t.disasterTypes[item.disasterType as keyof typeof t.disasterTypes] ?? item.disasterType;
  const regions =
    lang !== "ko" && item.translatedRegionNames?.length
      ? item.translatedRegionNames
      : item.regionNames;

  return (
    <Link
      href={`/alerts/${item.alertId}?source=OFFICIAL`}
      className={`flex gap-3 group ${isLast ? "" : "mb-3"}`}
    >
      <div className="flex flex-col items-center">
        <div className="w-2.5 h-2.5 rounded-full bg-[#9db4f5] mt-1 shrink-0 group-hover:bg-[var(--blue)] transition-colors" />
        {!isLast && <div className="w-0.5 bg-[var(--line)] flex-1 mt-1" />}
      </div>
      <div className="flex-1 pb-3">
        <div className="text-xs text-[var(--text-subtle)] mb-0.5">{timeStr}</div>
        {(type || regions.length > 0) && (
          <div className="flex flex-wrap gap-1 mb-1 text-xs">
            {type && (
              <span className="text-[var(--text-muted)]">{type}</span>
            )}
            {regions.length > 0 && (
              <span className="text-[var(--text-subtle)]">{regions.join(", ")}</span>
            )}
          </div>
        )}
        <p className="text-sm text-[var(--text-body)] leading-relaxed group-hover:text-[var(--blue)] transition-colors line-clamp-3">
          {message}
        </p>
      </div>
    </Link>
  );
}
