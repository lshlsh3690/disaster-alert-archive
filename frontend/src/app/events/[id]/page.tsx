"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEvent } from "@/lib/queries/useEvents";
import { useLanguageStore } from "@/store/languageStore";
import { useI18n } from "@/hooks/useI18n";
import { disasterTypeChipClass } from "@/ui/disasterTypeChip";
import { formatEventPeriod } from "@/utils/eventDate";
import { REPETITIVE_TYPES } from "@/constants/eventTypes";
import type { EventAlertItem } from "@/types/events";

export default function EventDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const lang = useLanguageStore((s) => s.language);
  const t = useI18n();
  const { data, isLoading } = useEvent(id, lang);
  const [allExpanded, setAllExpanded] = useState(false);
  const [expandedDates, setExpandedDates] = useState<Set<string>>(new Set());

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

  if (isLoading) return <main className="p-6">{t.loading}</main>;
  if (!data) return <main className="p-6">데이터가 없습니다.</main>;

  const title = data.translatedTitle ?? data.eventTitle;
  const period = formatEventPeriod(data.firstAlertAt, data.lastAlertAt);

  return (
    <main className="p-4 sm:p-6 space-y-5 max-w-3xl mx-auto">
      {/* 헤더 */}
      <div className="bg-white rounded-xl shadow p-5 space-y-3">
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-xl font-bold leading-snug flex-1">{title}</h1>
          <span
            className={`shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${
              data.active ? "bg-red-100 text-red-600" : "bg-gray-100 text-gray-500"
            }`}
          >
            {data.active ? t.events.badgeActive : t.events.badgePast}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600">
          {data.primaryDisasterType && (
            <span
              className={`px-2 py-0.5 rounded-full text-xs font-medium ${disasterTypeChipClass(data.primaryDisasterType)}`}
            >
              {data.primaryDisasterType}
            </span>
          )}
          <span>{period}</span>
          {data.primaryRegionName && <span>{data.primaryRegionName}</span>}
          <span className="text-gray-400">
            {t.events.relatedCount} {data.alertCount}건
          </span>
        </div>
        {data.primaryRegionName && (
          <p className="text-xs text-gray-400">{t.events.approxRegion}</p>
        )}
      </div>

      {/* 타임라인 */}
      <section className="bg-white rounded-xl shadow p-4 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold text-gray-800">{t.events.timelineTitle}</h2>
          {isRepetitive && (
            <button
              className="text-xs text-blue-600 hover:underline"
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
                <TimelineNode key={item.alertId} item={item} isLast={i === group.items.length - 1} lang={lang} />
              ));
            }

            const isOpen = expandedDates.has(group.date);
            const dateLabel = group.date.slice(2).replace(/-/g, "/");
            return (
              <div key={group.date} className="mb-2">
                <button
                  className="flex items-center gap-2 w-full text-left py-1 px-2 rounded hover:bg-gray-50"
                  onClick={() => toggleDate(group.date!)}
                >
                  <span
                    className={`text-xs transition-transform ${isOpen ? "rotate-90" : ""}`}
                  >
                    ▶
                  </span>
                  <span className="text-sm font-medium text-gray-700">
                    {dateLabel} ({group.items.length}건)
                  </span>
                </button>
                {isOpen && (
                  <div className="ml-4 border-l-2 border-gray-100 pl-3 mt-1 space-y-1">
                    {group.items.map((item, i) => (
                      <TimelineNode
                        key={item.alertId}
                        item={item}
                        isLast={gi === timelineGroups.length - 1 && i === group.items.length - 1}
                        lang={lang}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </section>
    </main>
  );
}

function TimelineNode({
  item,
  isLast,
  lang,
}: {
  item: EventAlertItem;
  isLast: boolean;
  lang: string;
}) {
  const dt = new Date(item.createdAt);
  const timeStr = `${dt.getMonth() + 1}/${dt.getDate()} ${String(dt.getHours()).padStart(2, "0")}:${String(dt.getMinutes()).padStart(2, "0")}`;

  const message =
    lang !== "ko" && item.translatedMessage ? item.translatedMessage : item.message;
  const type =
    lang !== "ko" && item.translatedType ? item.translatedType : item.disasterType;
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
        <div className="w-2.5 h-2.5 rounded-full bg-blue-400 mt-1 shrink-0 group-hover:bg-blue-600 transition-colors" />
        {!isLast && <div className="w-0.5 bg-gray-200 flex-1 mt-1" />}
      </div>
      <div className="flex-1 pb-3">
        <div className="text-xs text-gray-400 mb-0.5">{timeStr}</div>
        {(type || regions.length > 0) && (
          <div className="flex flex-wrap gap-1 mb-1 text-xs">
            {type && (
              <span className="text-gray-500">{type}</span>
            )}
            {regions.length > 0 && (
              <span className="text-gray-400">{regions.join(", ")}</span>
            )}
          </div>
        )}
        <p className="text-sm text-gray-800 leading-relaxed group-hover:text-blue-700 transition-colors line-clamp-3">
          {message}
        </p>
      </div>
    </Link>
  );
}
