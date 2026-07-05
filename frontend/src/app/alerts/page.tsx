"use client";

import ReportButton from "@/components/alerts/ReportButton";
import KakaoPolygonMap from "@/components/map/KakaoPolygonMap";
import { useSearchCombinedAlerts, useSigungu, useSidoStats, useAlertStats, useSigunguStats } from "@/lib/queries/useAlerts";
import { Alert } from "@/types/alerts";
import { LEVEL_OPTIONS, levelTextToCode } from "@/ui/level";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import DatePicker from "@/components/form/DatePicker";
import { z } from "zod";
import { useRouter, useSearchParams } from "next/navigation";
import { DISASTER_TYPES } from "@/ui/disasterType";
import { METROS } from "@/ui/metros";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import { disasterTypeChipStyle, disasterTypePalette } from "@/ui/disasterTypeColor";

// <input type="date">의 표시 포맷은 JS 텍스트가 아니라 브라우저가 lang 속성을 보고 렌더링하므로
// 언어 전환 시 실제로 반영되도록 명시적으로 넘겨준다.
const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };

const LEVEL_BADGE: Record<string, { backgroundColor: string; color: string }> = {
  안전안내: { backgroundColor: "var(--blue-soft)", color: "var(--blue)" },
  긴급재난: { backgroundColor: "#fff1e3", color: "#c1670c" },
  위급재난: { backgroundColor: "var(--coral-soft)", color: "#c0473b" },
};
function levelBadgeStyle(text?: string | null) {
  return LEVEL_BADGE[text ?? ""] ?? { backgroundColor: "#eef1f5", color: "var(--text-muted)" };
}


const ZSearch = z.object({
  sido: z.string().optional(),
  sigungu: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  type: z.string().optional(),
  levelText: z.string().optional(),
  keyword: z.string().optional(),
  source: z.enum(["ALL", "OFFICIAL", "USER"]).optional(),
});
type SearchForm = z.infer<typeof ZSearch>;

export default function DisasterListPage() {
  return (
    <Suspense fallback={<main className="p-6">불러오는 중...</main>}>
      <DisasterListPageInner />
    </Suspense>
  );
}

function DisasterListPageInner() {
  const [page, setPage] = useState<number>(0);
  const size = 10;
  const [formState, setFormState] = useState<SearchForm>({});
  const router = useRouter();
  const searchParams = useSearchParams();
  const t = useI18n();
  const language = useLanguageStore((state) => state.language);



  const { register, handleSubmit, reset, watch, setValue, control } = useForm<SearchForm>({
    resolver: zodResolver(ZSearch),
    defaultValues: {},
  });

  const watchedSido = watch("sido");
  const { data: sigunguList } = useSigungu(watchedSido || undefined);

  useEffect(() => {
    setValue("sigungu", "");
  }, [watchedSido, setValue]);

  const buildParams = useCallback((f: SearchForm) => {
    const levelCode = levelTextToCode(f.levelText);
    const region = f.sido && f.sigungu ? `${f.sido} ${f.sigungu}` : f.sido || undefined;
    return {
      region,
      startDate: f.startDate || undefined,
      endDate: f.endDate || undefined,
      type: f.type || undefined,
      level: levelCode,
      keyword: f.keyword || undefined,
      source: f.source || "ALL",
      page,
      size,
      sort: "createdAt,desc",
    };
  }, [page, size]);
  const params = useMemo(() => buildParams(formState), [buildParams, formState]);

  const { data, isLoading, isFetching } = useSearchCombinedAlerts(params, language);

  const filteredStatsParams = useMemo(() => {
    const levelCode = levelTextToCode(formState.levelText);
    const region =
      formState.sido && formState.sigungu
        ? `${formState.sido} ${formState.sigungu}`
        : formState.sido || undefined;
    return {
      region,
      startDate: formState.startDate || undefined,
      endDate: formState.endDate || undefined,
      type: formState.type || undefined,
      level: levelCode,
      keyword: formState.keyword || undefined,
      source: formState.source || undefined,
    };
  }, [formState]);

  const { data: sidoStats } = useSidoStats(filteredStatsParams);
  const { data: filteredStats } = useAlertStats(filteredStatsParams);

  const topType = useMemo(() => {
    if (!filteredStats?.typeStats) return null;
    return filteredStats.typeStats
      .filter((t) => t.type)
      .sort((a, b) => b.count - a.count)[0] ?? null;
  }, [filteredStats]);

  const typeDistribution = useMemo(() => {
    if (!filteredStats?.typeStats) return [];
    const sorted = filteredStats.typeStats
      .filter((t) => t.type)
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);
    const total = sorted.reduce((sum, t) => sum + t.count, 0);
    return sorted.map((t) => ({
      type: t.type!,
      count: t.count,
      pct: total > 0 ? Math.round((t.count / total) * 100) : 0,
    }));
  }, [filteredStats]);

  const mapParams = useMemo(() => {
    const levelCode = levelTextToCode(formState.levelText);
    return {
      startDate: formState.startDate || undefined,
      endDate: formState.endDate || undefined,
      type: formState.type || undefined,
      level: levelCode,
      keyword: formState.keyword || undefined,
      source: formState.source || undefined,
    };
  }, [formState]);

  const { data: sigunguStats } = useSigunguStats(
    { ...mapParams, region: formState.sido },
    !!formState.sido
  );

  const maxRegion = useMemo(() => {
    if (formState.sido) {
      if (!sigunguStats || sigunguStats.length === 0) return null;
      const max = sigunguStats.reduce((a, b) => (a.count > b.count ? a : b));
      const prefix = formState.sido + " ";
      const name = max.region.startsWith(prefix) ? max.region.slice(prefix.length) : max.region;
      return { region: name, count: max.count };
    }
    if (!sidoStats || sidoStats.length === 0) return null;
    return sidoStats.reduce((a, b) => (a.count > b.count ? a : b));
  }, [formState.sido, sigunguStats, sidoStats]);

  useEffect(() => {
    const sido = searchParams.get("sido") || undefined;
    const sigungu = searchParams.get("sigungu") || undefined;
    const startDate = searchParams.get("startDate") || undefined;
    const endDate = searchParams.get("endDate") || undefined;
    const type = searchParams.get("type") || undefined;
    const levelText = searchParams.get("levelText") || undefined;
    const keyword = searchParams.get("keyword") || undefined;
    const source = (searchParams.get("source") as "ALL" | "OFFICIAL" | "USER" | null) || undefined;

    setPage(0);
    setFormState({ sido, sigungu, startDate, endDate, type, levelText, keyword, source });
    reset({
      sido: sido ?? "",
      sigungu: sigungu ?? "",
      startDate: startDate ?? "",
      endDate: endDate ?? "",
      type: type ?? "",
      levelText: levelText ?? "",
      keyword: keyword ?? "",
      source: source ?? "ALL",
    });

    if (typeof window !== "undefined" && window.location.hash === "#list") {
      setTimeout(() => {
        const el = document.getElementById("list");
        if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 0);
    }
  }, [searchParams, reset]);

  const onSubmit = (v: SearchForm) => {
    setPage(0);
    const qs = new URLSearchParams();
    if (v.sido) qs.set("sido", v.sido);
    if (v.sigungu) qs.set("sigungu", v.sigungu);
    if (v.startDate) qs.set("startDate", v.startDate);
    if (v.endDate) qs.set("endDate", v.endDate);
    if (v.type) qs.set("type", v.type);
    if (v.levelText) qs.set("levelText", v.levelText);
    if (v.keyword) qs.set("keyword", v.keyword);
    if (v.source) qs.set("source", v.source);
    router.push(`/alerts?${qs.toString()}`);
  };

  const onReset = () => {
    reset({});
    setFormState({});
    setPage(0);
    router.push("/alerts");
  };

  const onMapSidoSelect = useCallback((sido: string | null) => {
    setPage(0);
    const qs = new URLSearchParams(searchParams.toString());
    if (sido) {
      qs.set("sido", sido);
      qs.delete("sigungu");
    } else {
      qs.delete("sido");
      qs.delete("sigungu");
    }
    router.push(`/alerts?${qs.toString()}`);
  }, [searchParams, router]);

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-7xl space-y-4 px-4 py-8 sm:px-6 sm:space-y-6">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-[var(--ink)]">{t.alertList.title}</h1>
            <p className="mt-1 text-[13px] text-[var(--text-muted)]">{t.alertList.description}</p>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href={`/stats${searchParams.toString() ? `?${searchParams.toString()}` : ""}`}
              className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-2 text-sm font-semibold text-[var(--text-body)] transition-colors hover:border-[var(--blue)] hover:text-[var(--blue)]"
            >
              📊 {t.alertList.viewStats}
            </Link>
            <ReportButton />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
          {/* 왼쪽: 검색 폼 + 목록 */}
          <div className="flex min-w-0 flex-col gap-4 sm:gap-6">
            <form
              onSubmit={handleSubmit(onSubmit)}
              className="grid grid-cols-1 gap-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)] sm:grid-cols-2 md:grid-cols-4"
            >
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.sido}</label>
                <select {...register("sido")} className="input">
                  <option value="">{t.alertList.filter.sido}</option>
                  {METROS.map((m) => (
                    <option key={m} value={m}>{t.metros[m]}</option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.sigungu}</label>
                <select {...register("sigungu")} className="input disabled:opacity-60" disabled={!watchedSido}>
                  <option value="">{t.alertList.filter.sigungu}</option>
                  {/* 드롭다운 - value는 한국어 이름(검색용), 표시는 번역된 이름 */}
                  {sigunguList?.filter((s) => s.name !== "전체").map((s) => (
                    <option key={s.code} value={s.name}>
                      {s.translatedName ?? s.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.startDate}</label>
                <Controller
                  name="startDate"
                  control={control}
                  render={({ field }) => (
                    <DatePicker value={field.value ?? ""} onChange={field.onChange} locale={LANG_LOCALE[language] ?? "ko-KR"} />
                  )}
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.endDate}</label>
                <Controller
                  name="endDate"
                  control={control}
                  render={({ field }) => (
                    <DatePicker value={field.value ?? ""} onChange={field.onChange} locale={LANG_LOCALE[language] ?? "ko-KR"} />
                  )}
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.type}</label>
                <select {...register("type")} className="input">
                  <option value="">{t.alertList.filter.type}</option>
                  {DISASTER_TYPES.map((type) => (
                    <option key={type} value={type}>{t.disasterTypes[type]}</option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.level}</label>
                <select {...register("levelText")} className="input">
                  <option value="">{t.alertList.filter.level}</option>
                  {LEVEL_OPTIONS.map((o) => (
                    <option key={o.code} value={o.text}>{t.levels[o.text]}</option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.sourceAll}</label>
                <select {...register("source")} className="input">
                  <option value="ALL">{t.alertList.filter.sourceAll}</option>
                  <option value="OFFICIAL">{t.alertList.filter.sourceOfficial}</option>
                  <option value="USER">{t.alertList.filter.sourceUser}</option>
                </select>
              </div>
              <div className="flex flex-col gap-1 col-span-full sm:col-span-2">
                <label className="text-xs text-[var(--text-muted)]">{t.alertList.filter.keyword}</label>
                <input {...register("keyword")} placeholder={t.alertList.filter.keyword} className="input" />
              </div>
              <div className="col-span-full flex justify-end gap-2">
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
            <div id="list" className="flex flex-1 flex-col rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-2 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
              {isLoading ? (
                <div className="p-4 text-[13px] text-[var(--text-muted)]">{t.loading}</div>
              ) : !(data?.content ?? []).length ? (
                <div className="p-8 text-center text-[13px] text-[var(--text-muted)]">{t.alertList.noData}</div>
              ) : (
                <>
                  <ul>
                    {(data?.content ?? []).map((a: Alert & { source?: "OFFICIAL" | "USER" }) => {
                      const translated = language !== "ko";
                      const translatedRegionNames = translated ? a.translatedRegionNames : null;
                      const regionLabel = translatedRegionNames && translatedRegionNames.length > 0
                        ? translatedRegionNames.join(", ")
                        : a.regionNames && a.regionNames.length > 0 ? a.regionNames.join(", ") : "-";
                      const message = (translated && a.translatedMessage) || a.message;
                      const disasterTypeLabel = (translated && a.translatedDisasterType)
                        || (t.disasterTypes[a.disasterType as keyof typeof t.disasterTypes] ?? a.disasterType);
                      const href = a.source === "USER" ? `/alerts/${a.id}?source=USER` : `/alerts/${a.id}?source=OFFICIAL`;
                      return (
                        <li key={a.id} className="border-b border-[var(--line)] last:border-0">
                          <Link href={href} className="group relative block py-3 pl-5 pr-2 transition-colors hover:bg-[var(--blue-soft)]">
                            <span className="absolute left-2 top-[18px] h-1.5 w-1.5 rounded-sm bg-[var(--coral)]" aria-hidden="true" />
                            <div className="flex items-baseline justify-between gap-3">
                              <span className="truncate text-[13px] font-semibold text-[var(--ink)]">{regionLabel}</span>
                              <time className="shrink-0 text-[11px] text-[var(--text-subtle)]">{new Date(a.createdAt).toLocaleString()}</time>
                            </div>
                            <p className="mt-1 line-clamp-2 text-[13px] leading-relaxed text-[var(--text-body)] transition-colors group-hover:text-[var(--blue)]">
                              {message}
                            </p>
                            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                              {a.disasterType && (
                                <span style={disasterTypeChipStyle(a.disasterType)} className="rounded-[var(--radius-pill)] px-2 py-0.5 text-[11px] font-medium">
                                  {disasterTypeLabel}
                                </span>
                              )}
                              {a.emergencyLevelText && (
                                <span style={levelBadgeStyle(a.emergencyLevelText)} className="rounded-[var(--radius-pill)] px-2 py-0.5 text-[11px] font-medium">
                                  {t.levels[a.emergencyLevelText as keyof typeof t.levels] ?? a.emergencyLevelText}
                                </span>
                              )}
                              {a.source === "USER" && (
                                <span className="rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 text-[11px] font-medium text-[var(--text-muted)]">
                                  {t.alertList.filter.sourceUser}
                                </span>
                              )}
                            </div>
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                  <div className="mt-auto flex items-center justify-between border-t border-[var(--line)] px-2 pt-3">
                    <button
                      className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)] disabled:opacity-50 disabled:hover:bg-[var(--surface)]"
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                    >
                      {t.alertList.prev}
                    </button>
                    <div className="text-sm text-[var(--text-muted)]">{data ? `${data.number + 1} / ${data.totalPages}` : "-"}</div>
                    <button
                      className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)] disabled:opacity-50 disabled:hover:bg-[var(--surface)]"
                      onClick={() => setPage((p) => (data ? Math.min(data.totalPages - 1, p + 1) : p))}
                      disabled={!data || data.number + 1 >= data.totalPages}
                    >
                      {t.alertList.next}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>

          {/* 오른쪽: 폴리곤 지도 + 통계 */}
          <div className="flex w-full min-w-0 flex-col gap-4">
            <div className="overflow-hidden rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
              <KakaoPolygonMap params={mapParams} mapHeight="520px" showSidebar={false} externalSido={formState.sido || undefined} onSidoSelect={onMapSidoSelect} />
            </div>

            {/* 재난 통계 요약 */}
            <div className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-[var(--text-muted)]">{t.alertList.totalCount}</span>
                  <span className="font-semibold text-[var(--ink)]">
                    {filteredStats ? filteredStats.totalCount.toLocaleString("ko-KR") + t.alertList.countUnit : "-"}
                  </span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-[var(--text-muted)]">{t.alertList.topRegion}</span>
                  <span className="font-semibold text-[var(--ink)]">
                    {maxRegion ? `${t.metros[maxRegion.region as keyof typeof t.metros] ?? maxRegion.region} (${maxRegion.count.toLocaleString("ko-KR")}${t.alertList.countUnit})` : "-"}
                  </span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-[var(--text-muted)]">{t.alertList.topType}</span>
                  <span className="font-semibold text-[var(--ink)]">
                    {topType ? `${t.disasterTypes[topType.type as keyof typeof t.disasterTypes] ?? topType.type} (${topType.count.toLocaleString("ko-KR")}${t.alertList.countUnit})` : "-"}
                  </span>
                </div>
              </div>

              {/* 재난 유형 분포 */}
              <div className="border-t border-[var(--line)] pt-2">
                <h3 className="mb-2 text-sm font-semibold text-[var(--text-body)]">
                  {t.alertList.typeDistribution}{formState.sido ? ` · ${t.metros[formState.sido as keyof typeof t.metros] ?? formState.sido}` : ` · ${t.alertList.nationwide}`}
                </h3>
                {typeDistribution.length === 0 ? (
                  <p className="py-2 text-center text-xs text-[var(--text-subtle)]">{t.alertList.noData}</p>
                ) : (
                  <ul className="space-y-1.5">
                    {typeDistribution.map(({ type, count, pct }) => (
                      <li key={type}>
                        <div className="mb-0.5 flex justify-between text-xs">
                          <span className="truncate text-[var(--text-body)]">{t.disasterTypes[type as keyof typeof t.disasterTypes] ?? type}</span>
                          <span className="ml-2 shrink-0 text-[var(--text-muted)]">{pct}% ({count.toLocaleString("ko-KR")}{t.alertList.countUnit})</span>
                        </div>
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-[#eef1f5]">
                          <div
                            className="h-full rounded-full"
                            style={{ width: `${pct}%`, backgroundColor: disasterTypePalette(type).text }}
                          />
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {/* 재난 경보 단계별 건수 */}
              <div className="border-t border-[var(--line)] pt-2">
                <h3 className="mb-2 text-sm font-semibold text-[var(--text-body)]">
                  {t.alertList.alertLevel}{formState.sido ? ` · ${t.metros[formState.sido as keyof typeof t.metros] ?? formState.sido}` : ` · ${t.alertList.nationwide}`}
                </h3>
                <div className="grid grid-cols-3 gap-2">
                  {[
                    { code: "LEVEL_1", key: "안전안내" },
                    { code: "LEVEL_2", key: "긴급재난" },
                    { code: "LEVEL_3", key: "위급재난" },
                  ].map((lvl) => {
                    const count = filteredStats?.levelStats?.find((l) => l.level === lvl.code)?.count ?? 0;
                    return (
                      <div
                        key={lvl.code}
                        style={levelBadgeStyle(lvl.key)}
                        className="flex flex-col items-center gap-0.5 rounded-[var(--radius-compact)] py-1.5"
                      >
                        <span className="flex min-h-[2rem] items-center justify-center text-center text-xs font-medium leading-tight">
                          {t.levels[lvl.key as keyof typeof t.levels]}
                        </span>
                        <span className="text-sm font-bold">{count.toLocaleString("ko-KR")}{t.alertList.countUnit}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}