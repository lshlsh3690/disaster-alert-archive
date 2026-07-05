"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReadonlyURLSearchParams } from "next/navigation";
import Link from "next/link";
import { Controller, useForm } from "react-hook-form";
import DatePicker from "@/components/form/DatePicker";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import { useSearchEvents } from "@/lib/queries/useEvents";
import { useSigungu } from "@/lib/queries/useAlerts";
import { fetchSigungu } from "@/api/alertApi";
import { DISASTER_TYPES } from "@/ui/disasterType";
import { METROS } from "@/ui/metros";
import { disasterTypeChipStyle } from "@/ui/disasterTypeColor";
import { formatEventPeriod } from "@/utils/eventDate";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import type { Event } from "@/types/events";

type ActiveTab = "active" | "past" | "all";

// <input type="date">의 표시 포맷은 JS 텍스트가 아니라 브라우저가 lang 속성을 보고 렌더링하므로
// 언어 전환 시 실제로 반영되도록 명시적으로 넘겨준다.
const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };

const ZSearch = z.object({
  sido: z.string().optional(),
  sigungu: z.string().optional(),
  type: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  keyword: z.string().optional(),
});
type SearchForm = z.infer<typeof ZSearch>;

const EMPTY_FORM: SearchForm = {
  sido: "",
  sigungu: "",
  type: "",
  startDate: "",
  endDate: "",
  keyword: "",
};

/** URL 쿼리에서 적용된 검색 필터를 복원 — 상세 진입 후 뒤로 가기 시 상태 유지용. */
function readFormFromParams(sp: ReadonlyURLSearchParams): SearchForm {
  return {
    sido: sp.get("sido") ?? "",
    sigungu: sp.get("sigungu") ?? "",
    type: sp.get("type") ?? "",
    startDate: sp.get("startDate") ?? "",
    endDate: sp.get("endDate") ?? "",
    keyword: sp.get("keyword") ?? "",
  };
}

export default function EventsPage() {
  return (
    <Suspense fallback={<main className="p-6">불러오는 중...</main>}>
      <EventsPageInner />
    </Suspense>
  );
}

function EventsPageInner() {
  const t = useI18n();
  const lang = useLanguageStore((s) => s.language);
  const router = useRouter();
  const searchParams = useSearchParams();

  const [tab, setTab] = useState<ActiveTab>(() => {
    const a = searchParams.get("active");
    if (a === "true") return "active";
    if (a === "false") return "past";
    return "all";
  });
  const [page, setPage] = useState(() => {
    const p = Number.parseInt(searchParams.get("page") ?? "", 10);
    return Number.isNaN(p) || p < 0 ? 0 : p;
  });
  const [formState, setFormState] = useState<SearchForm>(() =>
    readFormFromParams(searchParams)
  );
  const size = 10;

  const { register, handleSubmit, reset, watch, setValue, control } = useForm<SearchForm>({
    resolver: zodResolver(ZSearch),
    defaultValues: readFormFromParams(searchParams),
  });

  const watchedSido = watch("sido");
  const { data: sigunguList } = useSigungu(watchedSido || undefined, lang);
  // 시도 변경 시 시군구 초기화. 단 마운트 첫 렌더는 건너뛴다 — URL에서 복원한 시군구를 지우지 않기 위함.
  const sidoFirstRender = useRef(true);
  useEffect(() => {
    if (sidoFirstRender.current) {
      sidoFirstRender.current = false;
      return;
    }
    setValue("sigungu", "");
  }, [watchedSido, setValue]);

  const activeParam: boolean | undefined =
    tab === "active" ? true : tab === "past" ? false : undefined;

  const params = useMemo(() => {
    const region =
      formState.sido && formState.sigungu
        ? `${formState.sido} ${formState.sigungu}`
        : formState.sido || undefined;
    return {
      active: activeParam,
      type: formState.type || undefined,
      region,
      startDate: formState.startDate || undefined,
      endDate: formState.endDate || undefined,
      keyword: formState.keyword || undefined,
      lang,
      page,
      size,
    };
  }, [activeParam, formState, lang, page]);

  const { data, isLoading, isFetching } = useSearchEvents(params);

  // 탭/페이지/필터 상태를 URL 쿼리에 동기화 — 상세 진입 후 뒤로 가기 시 같은 페이지·검색결과로 복원된다.
  useEffect(() => {
    const url = new URLSearchParams();
    if (tab === "active") url.set("active", "true");
    else if (tab === "past") url.set("active", "false");
    if (page > 0) url.set("page", String(page));
    if (formState.sido) url.set("sido", formState.sido);
    if (formState.sigungu) url.set("sigungu", formState.sigungu);
    if (formState.type) url.set("type", formState.type);
    if (formState.startDate) url.set("startDate", formState.startDate);
    if (formState.endDate) url.set("endDate", formState.endDate);
    if (formState.keyword) url.set("keyword", formState.keyword);
    const qs = url.toString();
    router.replace(qs ? `/events?${qs}` : "/events", { scroll: false });
  }, [tab, page, formState, router]);

  const handleTabChange = (next: ActiveTab) => {
    setTab(next);
    setPage(0);
  };

  const onSubmit = useCallback((f: SearchForm) => {
    setFormState(f);
    setPage(0);
  }, []);

  const onReset = () => {
    reset(EMPTY_FORM);
    setFormState(EMPTY_FORM);
    setPage(0);
  };

  const splitRegion = (name?: string | null) => {
    const parts = (name ?? "").trim().split(/\s+/);
    return { sido: parts[0] || "", sigungu: parts.slice(1).join(" ") };
  };

  // 칩 필터는 누적하지 않고 해당 조건만 단독 적용
  const filterByType = (type?: string | null) => {
    if (!type) return;
    const next = { ...EMPTY_FORM, type };
    reset(next);
    setFormState(next);
    setPage(0);
  };
  const filterByRegion = (e: Event) => {
    const { sido, sigungu } = splitRegion(e.primaryRegionName);
    if (!sido) return;
    sidoFirstRender.current = true; // 시도 변경 시 시군구 자동 초기화 1회 건너뜀
    const next = { ...EMPTY_FORM, sido, sigungu };
    reset(next);
    setFormState(next);
    setPage(0);
  };

  // 지역명 번역: 시도는 t.metros, 시군구는 /districts/sigungu 응답으로 매핑
  const [sigunguTransBySido, setSigunguTransBySido] = useState<Record<string, Map<string, string>>>({});
  const ensureSigunguTrans = useCallback(
    async (sido: string) => {
      if (lang === "ko" || !sido || sigunguTransBySido[sido]) return;
      try {
        const list = await fetchSigungu(sido, lang);
        const map = new Map<string, string>();
        (list ?? []).forEach((s) => { if (s.translatedName) map.set(s.name, s.translatedName); });
        setSigunguTransBySido((prev) => ({ ...prev, [sido]: map }));
      } catch { /* 번역 실패 시 원문 유지 */ }
    },
    [lang, sigunguTransBySido]
  );
  useEffect(() => {
    if (lang === "ko") return;
    const sidos = new Set(
      (data?.content ?? []).map((e) => splitRegion(e.primaryRegionName).sido).filter(Boolean)
    );
    sidos.forEach(ensureSigunguTrans);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, lang]);
  const regionDisplay = (e: Event) => {
    const name = e.primaryRegionName;
    if (!name) return "";
    if (lang === "ko") return name;
    const { sido, sigungu } = splitRegion(name);
    const tSido = t.metros?.[sido as keyof typeof t.metros] ?? sido;
    if (!sigungu) return tSido;
    const tSigungu = sigunguTransBySido[sido]?.get(sigungu) ?? sigungu;
    return `${tSido} ${tSigungu}`;
  };

  const totalPages = data?.totalPages ?? 0;

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-4xl space-y-4 px-4 py-8 sm:px-6">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-[var(--ink)]">{t.events.title}</h1>
          <p className="mt-1 text-[13px] text-[var(--text-muted)]">{t.events.description}</p>
        </div>

        {/* 탭 */}
        <div className="flex gap-1 border-b border-[var(--line)]">
          {(["active", "past", "all"] as ActiveTab[]).map((tabKey) => {
            const label =
              tabKey === "active"
                ? t.events.tabActive
                : tabKey === "past"
                ? t.events.tabPast
                : t.events.tabAll;
            return (
              <button
                key={tabKey}
                onClick={() => handleTabChange(tabKey)}
                className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
                  tab === tabKey
                    ? "border-[var(--blue)] text-[var(--blue)]"
                    : "border-transparent text-[var(--text-muted)] hover:text-[var(--text-body)]"
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>

        {/* 필터 */}
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="grid grid-cols-2 gap-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)] sm:grid-cols-3"
        >
          <select {...register("sido")} className="input">
            <option value="">{t.events.filter.sido}</option>
            {METROS.map((m) => (
              <option key={m} value={m}>{t.metros[m as keyof typeof t.metros] ?? m}</option>
            ))}
          </select>

          <select {...register("sigungu")} className="input disabled:opacity-60" disabled={!watchedSido}>
            <option value="">{t.events.filter.sigungu}</option>
            {sigunguList?.map((s) => (
              <option key={s.code} value={s.name}>
                {s.translatedName ?? s.name}
              </option>
            ))}
          </select>

          <select {...register("type")} className="input">
            <option value="">{t.events.filter.type}</option>
            {DISASTER_TYPES.map((dt) => (
              <option key={dt} value={dt}>{t.disasterTypes[dt as keyof typeof t.disasterTypes] ?? dt}</option>
            ))}
          </select>

          <div className="flex flex-col gap-1">
            <span className="text-xs text-[var(--text-muted)]">{t.events.filter.startDate}</span>
            <Controller
              name="startDate"
              control={control}
              render={({ field }) => (
                <DatePicker value={field.value ?? ""} onChange={field.onChange} locale={LANG_LOCALE[lang] ?? "ko-KR"} clearLabel={t.datePicker.clear} prevMonthLabel={t.datePicker.prevMonth} nextMonthLabel={t.datePicker.nextMonth} />
              )}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-[var(--text-muted)]">{t.events.filter.endDate}</span>
            <Controller
              name="endDate"
              control={control}
              render={({ field }) => (
                <DatePicker value={field.value ?? ""} onChange={field.onChange} locale={LANG_LOCALE[lang] ?? "ko-KR"} clearLabel={t.datePicker.clear} prevMonthLabel={t.datePicker.prevMonth} nextMonthLabel={t.datePicker.nextMonth} />
              )}
            />
          </div>
          <input type="text" {...register("keyword")} className="input self-end" placeholder={t.events.filter.keyword} />

          <div className="col-span-2 flex justify-end gap-2 sm:col-span-3">
            <button
              type="button"
              onClick={onReset}
              className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)]"
            >
              {t.alertList.reset}
            </button>
            <button
              type="submit"
              className="rounded-[var(--radius-control)] bg-[var(--blue)] px-4 py-2 text-sm font-semibold text-white transition hover:brightness-95 disabled:opacity-60"
              disabled={isFetching}
            >
              {isFetching ? t.alertList.searching : t.alertList.search}
            </button>
          </div>
        </form>

        {/* 목록 */}
        {isLoading ? (
          <div className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t.loading}</div>
        ) : !data?.content.length ? (
          <div className="py-8 text-center text-[13px] text-[var(--text-muted)]">{t.events.empty}</div>
        ) : (
          <ul className="space-y-3">
            {data.content.map((e: Event) => (
              <li key={e.id}>
                <Link
                  href={`/events/${e.id}`}
                  className="block rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)] transition-shadow hover:shadow-[0_12px_28px_rgba(28,39,60,0.08)]"
                >
                  <div className="flex items-start justify-between gap-2">
                    <h2 className="flex-1 font-semibold leading-snug text-[var(--ink)]">
                      {e.translatedTitle ?? e.eventTitle}
                    </h2>
                    <span
                      className={`shrink-0 rounded-[var(--radius-pill)] px-2 py-0.5 text-xs font-medium ${
                        e.active ? "bg-[var(--coral-soft)] text-[#c0473b]" : "bg-[#eef1f5] text-[var(--text-muted)]"
                      }`}
                    >
                      {e.active ? t.events.badgeActive : t.events.badgePast}
                    </span>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                    {e.primaryDisasterType && (
                      <button
                        type="button"
                        onClick={(evt) => { evt.preventDefault(); evt.stopPropagation(); filterByType(e.primaryDisasterType); }}
                        title={t.events.filterByType}
                        style={disasterTypeChipStyle(e.primaryDisasterType)}
                        className="cursor-pointer rounded-[var(--radius-pill)] px-2 py-0.5 font-medium transition hover:brightness-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue)]"
                      >
                        {t.disasterTypes[e.primaryDisasterType as keyof typeof t.disasterTypes] ?? e.primaryDisasterType}
                      </button>
                    )}
                    {e.primaryRegionName && (
                      <button
                        type="button"
                        onClick={(evt) => { evt.preventDefault(); evt.stopPropagation(); filterByRegion(e); }}
                        title={t.events.filterByRegion}
                        className="inline-flex cursor-pointer items-center gap-1 rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)] transition hover:bg-[#e4e8ee] hover:text-[var(--text-body)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue-soft)]"
                      >
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                          <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z" />
                          <circle cx="12" cy="10" r="3" />
                        </svg>
                        {regionDisplay(e)}
                      </button>
                    )}
                    <span className="inline-flex items-center gap-1 rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 font-medium text-[var(--text-muted)]">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M4 4h16v12H7l-3 3V4z" />
                      </svg>
                      {t.events.relatedCount} {e.alertCount}{t.alertList.countUnit}
                    </span>
                    <span className="text-[var(--text-subtle)]">{formatEventPeriod(e.firstAlertAt, e.lastAlertAt)}</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 pt-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)] disabled:opacity-40 disabled:hover:bg-[var(--surface)]"
            >
              {t.alertList.prev}
            </button>
            <span className="px-3 py-1 text-sm text-[var(--text-muted)]">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)] disabled:opacity-40 disabled:hover:bg-[var(--surface)]"
            >
              {t.alertList.next}
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
