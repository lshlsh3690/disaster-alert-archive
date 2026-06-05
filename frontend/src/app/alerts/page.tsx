"use client";

import ReportButton from "@/components/alerts/ReportButton";
import KakaoPolygonMap from "@/components/map/KakaoPolygonMap";
import { useSearchCombinedAlerts, useSigungu, useSidoStats, useAlertStats, useSigunguStats } from "@/lib/queries/useAlerts";
import { Alert } from "@/types/alerts";
import { LEVEL_OPTIONS, levelTextToCode } from "@/ui/level";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useRouter, useSearchParams } from "next/navigation";
import { DISASTER_TYPES } from "@/ui/disasterType";
import { METROS } from "@/ui/metros";
import { useI18n } from "@/hooks/useI18n";


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



  const { register, handleSubmit, reset, watch, setValue } = useForm<SearchForm>({
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

  const { data, isLoading, isFetching } = useSearchCombinedAlerts(params);

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
    <main className="p-3 sm:p-6 space-y-4 sm:space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold">{t.alertList.title}</h1>
          <p className="text-sm text-gray-500">
            {t.alertList.description}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            href={`/stats${searchParams.toString() ? `?${searchParams.toString()}` : ""}`}
            className="px-3 py-2 text-sm font-semibold rounded-lg border border-gray-200 bg-white text-gray-700 hover:border-blue-400 hover:text-blue-600 transition-colors"
          >
            📊 통계 보기
          </Link>
          <ReportButton />
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_340px] gap-4 sm:gap-6">
        {/* 왼쪽: 검색 폼 + 목록 */}
        <div className="flex flex-col min-w-0 gap-4 sm:gap-6">
          <form
            onSubmit={handleSubmit(onSubmit)}
            className="bg-white rounded-xl shadow p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3"
          >
            <select {...register("sido")} className="input">
              <option value="">{t.alertList.filter.sido}</option>
              {METROS.map((m) => (
                <option key={m} value={m}>{t.metros[m]}</option>
              ))}
            </select>
            <select {...register("sigungu")} className="input" disabled={!watchedSido}>
              <option value="">{t.alertList.filter.sigungu}</option>
              {/* 드롭다운 - value는 한국어 이름(검색용), 표시는 번역된 이름 */}
              {sigunguList?.filter((s) => s.name !== "전체").map((s) => (
                <option key={s.code} value={s.name}>
                  {s.translatedName ?? s.name}
                </option>
              ))}
            </select>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-gray-500">{t.alertList.filter.startDate}</label>
              <input {...register("startDate")} type="date" className="input" />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-gray-500">{t.alertList.filter.endDate}</label>
              <input {...register("endDate")} type="date" className="input" />
            </div>
            <select {...register("type")} className="input">
              <option value="">{t.alertList.filter.type}</option>
              {DISASTER_TYPES.map((type) => (
                <option key={type} value={type}>{t.disasterTypes[type]}</option>
              ))}
            </select>
            <select {...register("levelText")} className="input">
              <option value="">{t.alertList.filter.level}</option>
              {LEVEL_OPTIONS.map((o) => (
                <option key={o.code} value={o.text}>{t.levels[o.text]}</option>
              ))}
            </select>
            <select {...register("source")} className="input">
              <option value="ALL">{t.alertList.filter.sourceAll}</option>
              <option value="OFFICIAL">{t.alertList.filter.sourceOfficial}</option>
              <option value="USER">{t.alertList.filter.sourceUser}</option>
            </select>
            <input {...register("keyword")} placeholder={t.alertList.filter.keyword} className="input col-span-full sm:col-span-2" />
            <div className="col-span-full sm:col-span-2 md:col-span-4 flex justify-end gap-2">
              <button type="button" onClick={onReset} className="px-3 py-2 rounded bg-gray-100">
                {t.alertList.reset}
              </button>
              <button
                type="submit"
                className="px-3 py-2 rounded bg-blue-600 text-white disabled:opacity-60"
                disabled={isFetching}
              >
                {isFetching ? t.alertList.searching : t.alertList.search}
              </button>
            </div>
          </form>

          {/* 목록 */}
          <div id="list" className="bg-white rounded-xl shadow p-4 flex flex-col flex-1">
            {isLoading ? (
              <div className="text-sm text-gray-500">{t.loading}</div>
            ) : (
              <div className="flex flex-col flex-1">
                <ul className="space-y-2">
                  {(data?.content ?? []).map((a: Alert & { source?: "OFFICIAL" | "USER" }) => {
                    const regionLabel = a.regionNames && a.regionNames.length > 0 ? a.regionNames.join(", ") : "-";
                    const href = a.source === "USER" ? `/alerts/${a.id}?source=USER` : `/alerts/${a.id}?source=OFFICIAL`;
                    return (
                      <li key={a.id} className="border-b last:border-0 pb-2">
                        <Link href={href} className="hover:underline block">
                          <span className="text-gray-800 block truncate">
                            [{regionLabel}] {new Date(a.createdAt).toLocaleString()} - {a.message}
                          </span>
                        </Link>
                        <div className="text-xs text-gray-500">
                          {a.disasterType ?? "-"} · {a.emergencyLevelText ?? "-"}
                        </div>
                      </li>
                    );
                  })}
                </ul>
                <div className="mt-auto pt-2 border-t flex items-center justify-between">
                  <button
                    className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                  >
                    {t.alertList.prev}
                  </button>
                  <div className="text-sm text-gray-500">{data ? `${data.number + 1} / ${data.totalPages}` : "-"}</div>
                  <button
                    className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
                    onClick={() => setPage((p) => (data ? Math.min(data.totalPages - 1, p + 1) : p))}
                    disabled={!data || data.number + 1 >= data.totalPages}
                  >
                    {t.alertList.next}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 오른쪽: 폴리곤 지도 + 통계 */}
        <div className="w-full min-w-0 flex flex-col gap-4">
          <KakaoPolygonMap params={mapParams} mapHeight="520px" showSidebar={false} externalSido={formState.sido || undefined} onSidoSelect={onMapSidoSelect} />

          {/* 재난 통계 요약 */}
          <div className="bg-white rounded-xl shadow p-4 space-y-3">
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">총 발생 건수</span>
                <span className="font-semibold">
                  {filteredStats ? filteredStats.totalCount.toLocaleString("ko-KR") + "건" : "-"}
                </span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">최다 발생 지역</span>
                <span className="font-semibold">
                  {maxRegion ? `${maxRegion.region} (${maxRegion.count.toLocaleString("ko-KR")}건)` : "-"}
                </span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">최다 재난 유형</span>
                <span className="font-semibold">
                  {topType ? `${topType.type} (${topType.count.toLocaleString("ko-KR")}건)` : "-"}
                </span>
              </div>
            </div>

            {/* 재난 유형 분포 */}
            <div className="pt-2 border-t">
              <h3 className="text-sm font-semibold text-gray-700 mb-2">
                재난 유형 분포{formState.sido ? ` · ${formState.sido}` : " · 전국"}
              </h3>
              {typeDistribution.length === 0 ? (
                <p className="text-xs text-gray-400 text-center py-2">데이터 없음</p>
              ) : (
                <ul className="space-y-1.5">
                  {typeDistribution.map(({ type, count, pct }, i) => (
                    <li key={type}>
                      <div className="flex justify-between text-xs mb-0.5">
                        <span className="text-gray-600 truncate">{type}</span>
                        <span className="text-gray-500 shrink-0 ml-2">{pct}% ({count.toLocaleString("ko-KR")}건)</span>
                      </div>
                      <div className="h-1.5 w-full bg-gray-100 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${["bg-red-500", "bg-orange-400", "bg-yellow-400", "bg-blue-500", "bg-purple-500"][i]}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* 재난 경보 단계별 건수 */}
            <div className="pt-2 border-t">
              <h3 className="text-sm font-semibold text-gray-700 mb-2">
                재난 경보 단계{formState.sido ? ` · ${formState.sido}` : " · 전국"}
              </h3>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { code: "LEVEL_1", text: "안전안내", color: "bg-blue-50 text-blue-700 border-blue-200" },
                  { code: "LEVEL_2", text: "긴급재난", color: "bg-orange-50 text-orange-700 border-orange-200" },
                  { code: "LEVEL_3", text: "위급재난", color: "bg-red-50 text-red-700 border-red-200" },
                ].map(({ code, text, color }) => {
                  const count = filteredStats?.levelStats
                    ?.find((l) => l.level === code)?.count ?? 0;
                  return (
                    <div key={text} className={`flex flex-col items-center rounded border py-1.5 gap-0.5 ${color}`}>
                      <span className="text-xs font-medium">{text}</span>
                      <span className="text-sm font-bold">{count.toLocaleString("ko-KR")}건</span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}