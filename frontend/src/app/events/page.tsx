"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReadonlyURLSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import { useSearchEvents } from "@/lib/queries/useEvents";
import { useSigungu } from "@/lib/queries/useAlerts";
import { DISASTER_TYPES } from "@/ui/disasterType";
import { METROS } from "@/ui/metros";
import { disasterTypeChipClass } from "@/ui/disasterTypeChip";
import { formatEventPeriod } from "@/utils/eventDate";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import type { Event } from "@/types/events";

type ActiveTab = "active" | "past" | "all";

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

  const { register, handleSubmit, reset, watch, setValue } = useForm<SearchForm>({
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

  const totalPages = data?.totalPages ?? 0;

  return (
    <main className="p-4 sm:p-6 space-y-4 max-w-4xl mx-auto">
      <div>
        <h1 className="text-xl font-bold">{t.events.title}</h1>
        <p className="text-sm text-gray-500 mt-1">{t.events.description}</p>
      </div>

      {/* 탭 */}
      <div className="flex gap-1 border-b">
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
              className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
                tab === tabKey
                  ? "border-blue-500 text-blue-600"
                  : "border-transparent text-gray-500 hover:text-gray-700"
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
        className="bg-white rounded-xl shadow p-4 grid grid-cols-2 sm:grid-cols-3 gap-3"
      >
        <select {...register("sido")} className="border rounded px-2 py-1 text-sm col-span-1">
          <option value="">{t.events.filter.sido}</option>
          {METROS.map((m) => (
            <option key={m} value={m}>{m}</option>
          ))}
        </select>

        <select
          {...register("sigungu")}
          className="border rounded px-2 py-1 text-sm col-span-1"
          disabled={!watchedSido}
        >
          <option value="">{t.events.filter.sigungu}</option>
          {sigunguList?.map((s) => (
            <option key={s.code} value={s.name}>
              {s.translatedName ?? s.name}
            </option>
          ))}
        </select>

        <select {...register("type")} className="border rounded px-2 py-1 text-sm col-span-1">
          <option value="">{t.events.filter.type}</option>
          {DISASTER_TYPES.map((dt) => (
            <option key={dt} value={dt}>{dt}</option>
          ))}
        </select>

        <input
          type="date"
          {...register("startDate")}
          className="border rounded px-2 py-1 text-sm col-span-1"
          placeholder={t.events.filter.startDate}
        />
        <input
          type="date"
          {...register("endDate")}
          className="border rounded px-2 py-1 text-sm col-span-1"
          placeholder={t.events.filter.endDate}
        />
        <input
          type="text"
          {...register("keyword")}
          className="border rounded px-2 py-1 text-sm col-span-1"
          placeholder={t.events.filter.keyword}
        />

        <div className="col-span-2 sm:col-span-3 flex gap-2 justify-end">
          <button
            type="button"
            onClick={onReset}
            className="px-3 py-1 text-sm border rounded text-gray-600"
          >
            {t.alertList.reset}
          </button>
          <button
            type="submit"
            className="px-4 py-1 text-sm bg-blue-600 text-white rounded"
            disabled={isFetching}
          >
            {isFetching ? t.alertList.searching : t.alertList.search}
          </button>
        </div>
      </form>

      {/* 목록 */}
      {isLoading ? (
        <div className="text-sm text-gray-500 py-8 text-center">{t.loading}</div>
      ) : !data?.content.length ? (
        <div className="text-sm text-gray-500 py-8 text-center">{t.events.empty}</div>
      ) : (
        <ul className="space-y-3">
          {data.content.map((e: Event) => (
            <li key={e.id}>
              <Link
                href={`/events/${e.id}`}
                className="block bg-white rounded-xl shadow p-4 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start justify-between gap-2">
                  <h2 className="font-semibold text-gray-900 leading-snug flex-1">
                    {e.translatedTitle ?? e.eventTitle}
                  </h2>
                  <span
                    className={`shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${
                      e.active
                        ? "bg-red-100 text-red-600"
                        : "bg-gray-100 text-gray-500"
                    }`}
                  >
                    {e.active ? t.events.badgeActive : t.events.badgePast}
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  {e.primaryDisasterType && (
                    <span
                      className={`px-2 py-0.5 rounded-full font-medium ${disasterTypeChipClass(e.primaryDisasterType)}`}
                    >
                      {e.primaryDisasterType}
                    </span>
                  )}
                  <span>{formatEventPeriod(e.firstAlertAt, e.lastAlertAt)}</span>
                  {e.primaryRegionName && <span>{e.primaryRegionName}</span>}
                  <span>
                    {t.events.relatedCount} {e.alertCount}건
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="flex justify-center items-center gap-2 pt-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 text-sm border rounded disabled:opacity-40"
          >
            {t.alertList.prev}
          </button>
          <span className="text-sm text-gray-600">
            {page + 1} / {totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="px-3 py-1 text-sm border rounded disabled:opacity-40"
          >
            {t.alertList.next}
          </button>
        </div>
      )}
    </main>
  );
}
