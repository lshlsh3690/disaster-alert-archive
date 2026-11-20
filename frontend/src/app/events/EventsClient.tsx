"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Controller, useForm } from "react-hook-form";
import DatePicker from "@/components/form/DatePicker";
import { FILTER_MIN_DATE } from "@/constants/dateRange";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import { useSearchEvents } from "@/lib/queries/useEvents";
import { useSigungu } from "@/lib/queries/useAlerts";
import { fetchSigungu } from "@/api/alertApi";
import {
  EVENTS_EMPTY_FORM,
  buildEventsListParams,
  buildEventsQuery,
  parseEventsPage,
  parseEventsSearchForm,
  parseEventsTab,
  type EventsActiveTab,
} from "@/lib/eventsSearchParams";
import { DISASTER_TYPES } from "@/ui/disasterType";
import { METROS } from "@/ui/metros";
import { formatEventPeriod } from "@/utils/eventDate";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import type { Event } from "@/types/events";

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
type ZSearchForm = z.infer<typeof ZSearch>;

export default function EventsClient() {
  return (
    <Suspense fallback={<main className="krds-page krds-page--narrow"><p className="krds-empty">불러오는 중...</p></main>}>
      <EventsClientInner />
    </Suspense>
  );
}

function EventsClientInner() {
  const { t } = useTranslation();
  const lang = useLanguageStore((s) => s.language);
  const router = useRouter();
  const searchParams = useSearchParams();

  const [tab, setTab] = useState<EventsActiveTab>(() => parseEventsTab(searchParams));
  const [page, setPage] = useState(() => parseEventsPage(searchParams));
  const [formState, setFormState] = useState(() => parseEventsSearchForm(searchParams));
  const size = 10;

  const { register, handleSubmit, reset, watch, setValue, control } = useForm({
    resolver: zodResolver(ZSearch),
    defaultValues: formState,
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

  const params = useMemo(
    () => buildEventsListParams(formState, tab, page, size, lang),
    [formState, tab, page, lang]
  );

  const { data, isLoading, isFetching } = useSearchEvents(params);

  // 탭/페이지/필터 상태를 URL 쿼리에 동기화 — 상세 진입 후 뒤로 가기 시 같은 페이지·검색결과로 복원된다.
  // 마지막으로 쓴 쿼리를 기억해, 아래 effect 가 "내가 바꾼 주소"와 "밖에서 바뀐 주소"를 구분한다.
  const lastQueryRef = useRef<string | null>(null);
  useEffect(() => {
    const qs = buildEventsQuery(formState, tab, page);
    lastQueryRef.current = qs;
    router.replace(qs ? `/events?${qs}` : "/events", { scroll: false });
  }, [tab, page, formState, router]);

  // 밖에서 주소가 바뀌면(헤더의 "사건 목록"을 다시 눌러 /events 로 오거나 브라우저 앞·뒤로 가기) 상태를 그 주소로 맞춘다.
  // 같은 경로라 컴포넌트가 다시 마운트되지 않아, 이게 없으면 필터가 남은 채 위 effect 가 옛 쿼리를 주소에 다시 써 버린다.
  useEffect(() => {
    const nextTab = parseEventsTab(searchParams);
    const nextPage = parseEventsPage(searchParams);
    const nextForm = parseEventsSearchForm(searchParams);
    if (lastQueryRef.current === null || buildEventsQuery(nextForm, nextTab, nextPage) === lastQueryRef.current) return;
    // 시도가 바뀌어도 주소에서 받은 시군구를 "시도 변경 → 시군구 비우기" effect 가 지우지 않게 1회 건너뛴다
    if (nextForm.sido !== watchedSido) sidoFirstRender.current = true;
    reset(nextForm);
    setFormState(nextForm);
    setTab(nextTab);
    setPage(nextPage);
    // watchedSido 는 비교용으로만 읽는다 — 폼 입력 중 시도를 바꿀 때마다 주소와 다시 맞추면 입력이 되돌아간다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, reset]);

  const handleTabChange = (next: EventsActiveTab) => {
    setTab(next);
    setPage(0);
  };

  const onSubmit = useCallback((f: ZSearchForm) => {
    setFormState({ ...EVENTS_EMPTY_FORM, ...f });
    setPage(0);
  }, []);

  const onReset = () => {
    reset(EVENTS_EMPTY_FORM);
    setFormState(EVENTS_EMPTY_FORM);
    setPage(0);
  };

  const splitRegion = (name?: string | null) => {
    const parts = (name ?? "").trim().split(/\s+/);
    return { sido: parts[0] || "", sigungu: parts.slice(1).join(" ") };
  };

  // 칩 필터는 누적하지 않고 해당 조건만 단독 적용
  const filterByType = (type?: string | null) => {
    if (!type) return;
    const next = { ...EVENTS_EMPTY_FORM, type };
    reset(next);
    setFormState(next);
    setPage(0);
  };
  const filterByRegion = (e: Event) => {
    const { sido, sigungu } = splitRegion(e.primaryRegionName);
    if (!sido) return;
    sidoFirstRender.current = true; // 시도 변경 시 시군구 자동 초기화 1회 건너뜀
    const next = { ...EVENTS_EMPTY_FORM, sido, sigungu };
    reset(next);
    setFormState(next);
    setPage(0);
  };

  // 지역명 번역: 시도는 t("metros"), 시군구는 /districts/sigungu 응답으로 매핑
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
    const tSido = t(`metros.${sido}`, { defaultValue: sido });
    if (!sigungu) return tSido;
    const tSigungu = sigunguTransBySido[sido]?.get(sigungu) ?? sigungu;
    return `${tSido} ${tSigungu}`;
  };

  const totalPages = data?.totalPages ?? 0;

  return (
    <main className="krds-page krds-page--narrow">
      <div className="krds-page-head">
        <div>
          <p className="krds-breadcrumb">
            <Link href="/">{t("events.breadcrumbHome")}</Link>
            <span aria-hidden="true"> › </span>
            {t("events.title")}
          </p>
          <h1 className="krds-title">{t("events.title")}</h1>
          <p className="krds-desc">{t("events.description")}</p>
        </div>
      </div>

      <div className="flex flex-col gap-4">
        {/* 탭 */}
        <div role="tablist" className="krds-tabs">
          {(["active", "past", "all"] as EventsActiveTab[]).map((tabKey) => {
            const label =
              tabKey === "active"
                ? t("events.tabActive")
                : tabKey === "past"
                ? t("events.tabPast")
                : t("events.tabAll");
            return (
              <button
                key={tabKey}
                type="button"
                role="tab"
                aria-selected={tab === tabKey}
                onClick={() => handleTabChange(tabKey)}
                className="krds-tab"
              >
                {label}
              </button>
            );
          })}
        </div>

        {/* 필터 바: 기존 필터(시도·시군구·유형·기간·키워드)를 한 줄 카드에 감아 배치 */}
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="krds-card flex flex-wrap items-center gap-[10px] px-5 py-4"
        >
          <select
            {...register("sido")}
            aria-label={t("events.filter.sido")}
            className="krds-control min-w-[150px] flex-1 sm:w-auto sm:flex-none"
          >
            <option value="">{t("events.filter.sido")}</option>
            {METROS.map((m) => (
              <option key={m} value={m}>{t(`metros.${m}`, { defaultValue: m })}</option>
            ))}
          </select>

          <select
            {...register("sigungu")}
            aria-label={t("events.filter.sigungu")}
            className="krds-control min-w-[150px] flex-1 sm:w-auto sm:flex-none"
            disabled={!watchedSido}
          >
            <option value="">{t("events.filter.sigungu")}</option>
            {sigunguList?.map((s) => (
              <option key={s.code} value={s.name}>
                {s.translatedName ?? s.name}
              </option>
            ))}
          </select>

          <select
            {...register("type")}
            aria-label={t("events.filter.type")}
            className="krds-control min-w-[150px] flex-1 sm:w-auto sm:flex-none"
          >
            <option value="">{t("events.filter.type")}</option>
            {DISASTER_TYPES.map((dt) => (
              <option key={dt} value={dt}>{t(`disasterTypes.${dt}`, { defaultValue: dt })}</option>
            ))}
          </select>

          <div className="min-w-[150px] flex-1 sm:flex-none">
            <Controller
              name="startDate"
              control={control}
              render={({ field }) => (
                <DatePicker min={FILTER_MIN_DATE} value={field.value ?? ""} onChange={field.onChange} className="krds-control" placeholder={t("events.filter.startDate")} locale={LANG_LOCALE[lang] ?? "ko-KR"} clearLabel={t("datePicker.clear")} prevMonthLabel={t("datePicker.prevMonth")} nextMonthLabel={t("datePicker.nextMonth")} />
              )}
            />
          </div>
          <div className="min-w-[150px] flex-1 sm:flex-none">
            <Controller
              name="endDate"
              control={control}
              render={({ field }) => (
                <DatePicker min={FILTER_MIN_DATE} value={field.value ?? ""} onChange={field.onChange} className="krds-control" placeholder={t("events.filter.endDate")} locale={LANG_LOCALE[lang] ?? "ko-KR"} clearLabel={t("datePicker.clear")} prevMonthLabel={t("datePicker.prevMonth")} nextMonthLabel={t("datePicker.nextMonth")} />
              )}
            />
          </div>

          <input
            type="text"
            {...register("keyword")}
            aria-label={t("events.filter.keyword")}
            className="krds-control min-w-[220px] flex-1"
            placeholder={t("events.filter.keyword")}
          />

          <div className="flex gap-[10px] max-sm:w-full">
            <button type="button" onClick={onReset} className="krds-btn krds-btn--secondary max-sm:flex-1">
              {t("alertList.reset")}
            </button>
            <button type="submit" className="krds-btn krds-btn--primary px-[22px] max-sm:flex-1" disabled={isFetching}>
              {isFetching ? t("alertList.searching") : t("alertList.search")}
            </button>
          </div>
        </form>

        {/* 목록 */}
        {isLoading ? (
          <p className="krds-empty">{t("loading")}</p>
        ) : !data?.content.length ? (
          <p className="krds-empty">{t("events.empty")}</p>
        ) : (
          <ul className="flex flex-col gap-4">
            {data.content.map((e: Event) => (
              <li key={e.id}>
                {/* prefetch=false: alerts 목록과 동일한 이유 — 상세(force-dynamic) 프리페치가
                    목록에 보이는 항목만큼 백그라운드 서버 데이터 페칭을 일으키는 걸 방지 */}
                <Link
                  href={`/events/${e.id}`}
                  className="krds-card flex flex-col gap-[10px] px-6 py-5 transition-colors hover:bg-[var(--krds-primary-5)] max-sm:px-5 max-sm:py-4"
                  prefetch={false}
                >
                  <div className="flex items-start justify-between gap-3">
                    <h2 className="m-0 min-w-0 flex-1 text-[19px] font-bold leading-[1.4] text-[var(--krds-gray-90)]">
                      {e.translatedTitle ?? e.eventTitle}
                    </h2>
                    <span className={`krds-badge shrink-0 ${e.active ? "krds-badge--danger" : "krds-badge--ended"}`}>
                      {e.active ? t("events.badgeActive") : t("events.badgePast")}
                    </span>
                  </div>
                  {/* 메타 칩: 유형·지역 칩은 누르면 그 조건 하나로 필터한다(카드 링크로 넘어가지 않게 막음) */}
                  <div className="flex flex-wrap items-center gap-2 text-[15px]">
                    {e.primaryDisasterType && (
                      <button
                        type="button"
                        onClick={(evt) => { evt.preventDefault(); evt.stopPropagation(); filterByType(e.primaryDisasterType); }}
                        title={t("events.filterByType")}
                        className="cursor-pointer rounded-[var(--radius-badge)] bg-[var(--krds-gray-5)] px-2 py-0.5 font-bold text-[var(--krds-gray-70)] transition-colors hover:text-[var(--krds-primary-50)] hover:underline"
                      >
                        {t(`disasterTypes.${e.primaryDisasterType}`, { defaultValue: e.primaryDisasterType })}
                      </button>
                    )}
                    {e.primaryRegionName && (
                      <button
                        type="button"
                        onClick={(evt) => { evt.preventDefault(); evt.stopPropagation(); filterByRegion(e); }}
                        title={t("events.filterByRegion")}
                        className="cursor-pointer rounded-[var(--radius-badge)] bg-[var(--krds-gray-5)] px-2 py-0.5 text-[var(--krds-gray-70)] transition-colors hover:text-[var(--krds-primary-50)] hover:underline"
                      >
                        {regionDisplay(e)}
                      </button>
                    )}
                    <span className="rounded-[var(--radius-badge)] bg-[var(--krds-gray-5)] px-2 py-0.5 text-[var(--krds-gray-70)]">
                      {t("events.relatedCount")} {e.alertCount}{t("alertList.countUnit")}
                    </span>
                    <span className="text-[var(--krds-gray-50)]">
                      {formatEventPeriod(e.firstAlertAt, e.lastAlertAt, { sameDay: t("events.sameDay"), daysSpan: t("events.daysSpan") })}
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <nav className="krds-pager" aria-label={t("events.title")}>
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="krds-pager__step"
            >
              {t("alertList.prev")}
            </button>
            <span className="px-3 text-[var(--krds-gray-70)]">
              <strong className="font-bold text-[var(--krds-gray-90)]">{page + 1}</strong> / {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="krds-pager__step"
            >
              {t("alertList.next")}
            </button>
          </nav>
        )}
      </div>
    </main>
  );
}
