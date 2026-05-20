"use client";

import ReportButton from "@/components/alerts/ReportButton";
import KakaoMetroMap from "@/components/KakaoMetroMap";
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
import { useLanguageStore } from "@/store/languageStore";

type WeatherData = {
  temp: number;
  humidity: number;
  rainfall: number;
  windDir: string;
  windSpeed: number;
  dust: number;
  dustLevel: "좋음" | "보통" | "나쁨" | "매우나쁨";
};

const DUMMY_WEATHER: Record<string, WeatherData> = {
  "서울특별시":   { temp: 23, humidity: 62, rainfall: 0.0, windDir: "남서", windSpeed: 3.2, dust: 48, dustLevel: "보통" },
  "부산광역시":   { temp: 26, humidity: 75, rainfall: 1.2, windDir: "남",   windSpeed: 4.5, dust: 28, dustLevel: "좋음" },
  "대구광역시":   { temp: 27, humidity: 58, rainfall: 0.0, windDir: "북동", windSpeed: 2.1, dust: 62, dustLevel: "보통" },
  "인천광역시":   { temp: 22, humidity: 68, rainfall: 0.3, windDir: "서",   windSpeed: 5.0, dust: 41, dustLevel: "보통" },
  "광주광역시":   { temp: 25, humidity: 71, rainfall: 0.0, windDir: "남서", windSpeed: 2.8, dust: 35, dustLevel: "좋음" },
  "대전광역시":   { temp: 24, humidity: 63, rainfall: 0.0, windDir: "북",   windSpeed: 2.3, dust: 55, dustLevel: "보통" },
  "울산광역시":   { temp: 26, humidity: 70, rainfall: 0.5, windDir: "동",   windSpeed: 3.7, dust: 30, dustLevel: "좋음" },
  "세종특별자치시": { temp: 23, humidity: 60, rainfall: 0.0, windDir: "북서", windSpeed: 2.6, dust: 50, dustLevel: "보통" },
  "경기도":       { temp: 22, humidity: 66, rainfall: 0.1, windDir: "남",   windSpeed: 3.0, dust: 53, dustLevel: "보통" },
  "강원특별자치도": { temp: 18, humidity: 55, rainfall: 0.0, windDir: "북동", windSpeed: 4.2, dust: 20, dustLevel: "좋음" },
  "충청북도":     { temp: 23, humidity: 61, rainfall: 0.0, windDir: "북서", windSpeed: 2.0, dust: 47, dustLevel: "보통" },
  "충청남도":     { temp: 22, humidity: 67, rainfall: 0.2, windDir: "서",   windSpeed: 3.4, dust: 44, dustLevel: "보통" },
  "전북특별자치도": { temp: 24, humidity: 69, rainfall: 0.0, windDir: "남서", windSpeed: 2.5, dust: 38, dustLevel: "좋음" },
  "전라남도":     { temp: 25, humidity: 74, rainfall: 0.8, windDir: "남",   windSpeed: 3.9, dust: 25, dustLevel: "좋음" },
  "경상북도":     { temp: 25, humidity: 57, rainfall: 0.0, windDir: "북동", windSpeed: 2.2, dust: 60, dustLevel: "보통" },
  "경상남도":     { temp: 26, humidity: 72, rainfall: 0.4, windDir: "동남", windSpeed: 3.1, dust: 33, dustLevel: "좋음" },
  "제주특별자치도": { temp: 27, humidity: 80, rainfall: 3.5, windDir: "남",   windSpeed: 6.8, dust: 18, dustLevel: "좋음" },
};

const DUST_COLOR: Record<WeatherData["dustLevel"], string> = {
  "좋음":   "text-blue-500",
  "보통":   "text-green-600",
  "나쁨":   "text-orange-500",
  "매우나쁨": "text-red-600",
};

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
  const language = useLanguageStore((s) => s.language);



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

  const { data: sidoStats } = useSidoStats({});
  const { data: alertStats } = useAlertStats({});
  const { data: filteredStats } = useAlertStats(
    formState.sido ? { region: formState.sido } : {}
  );

  const maxRegion = useMemo(() => {
    if (!sidoStats || sidoStats.length === 0) return null;
    return sidoStats.reduce((a, b) => (a.count > b.count ? a : b));
  }, [sidoStats]);

  const topType = useMemo(() => {
    if (!alertStats?.typeStats) return null;
    return alertStats.typeStats
      .filter((t) => t.type)
      .sort((a, b) => b.count - a.count)[0] ?? null;
  }, [alertStats]);

  const typeDistribution = useMemo(() => {
    const stats = filteredStats ?? alertStats;
    if (!stats?.typeStats) return [];
    const sorted = stats.typeStats
      .filter((t) => t.type)
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);
    const total = sorted.reduce((sum, t) => sum + t.count, 0);
    return sorted.map((t) => ({
      type: t.type!,
      count: t.count,
      pct: total > 0 ? Math.round((t.count / total) * 100) : 0,
    }));
  }, [filteredStats, alertStats]);

  const sigunguRanking = useMemo(() => {
    if (!formState.sido || !sidoStats) return [];
    const prefix = formState.sido + " ";
    return sidoStats
      .filter((s) => s.region.startsWith(prefix))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5)
      .map((s) => ({ name: s.region.slice(prefix.length), count: s.count }));
  }, [sidoStats, formState.sido]);

  const { data: sigunguRawData } = useSigunguStats(
    formState.sido ? { region: formState.sido } : {},
    !!formState.sido
  );

  const sigunguAllStats = useMemo(() => {
    if (!formState.sido || !sigunguRawData) return [];
    const prefix = formState.sido + " ";
    return sigunguRawData
      .filter((s) => s.count > 0)
      .map((s) => ({
        name: s.region.startsWith(prefix) ? s.region.slice(prefix.length) : s.region,
        count: s.count,
      }));
  }, [sigunguRawData, formState.sido]);

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
    reset({ sido, sigungu, startDate, endDate, type, levelText, keyword, source });

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

  return (
    <main className="p-3 sm:p-6 space-y-4 sm:space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold">{t.alertList.title}</h1>
          <p className="text-sm text-gray-500">
            {t.alertList.description}
          </p>
        </div>
        <ReportButton />
      </div>

      <div className="flex flex-col xl:flex-row gap-4 sm:gap-6 items-start">
        {/* 왼쪽: 검색 폼 + 목록 */}
        <div className="flex-1 min-w-0 space-y-4 sm:space-y-6">
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
          <div id="list" className="bg-white rounded-xl shadow p-4">
            {isLoading ? (
              <div className="text-sm text-gray-500">{t.loading}</div>
            ) : (
              <>
                <ul className="space-y-2">
                  {(data?.content ?? []).map((a: Alert & { source?: "OFFICIAL" | "USER" }) => {
                    const regionLabel = a.regionNames && a.regionNames.length > 0 ? a.regionNames.join(", ") : "-";
                    const href = a.source === "USER" ? `/alerts/${a.id}?source=USER` : `/alerts/${a.id}?source=OFFICIAL`;
                    return (
                      <li key={a.id} className="border-b last:border-0 pb-2">
                        <Link href={href} className="hover:underline">
                          <span className="text-gray-800">
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
                <div className="mt-4 flex items-center justify-between">
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
              </>
            )}
          </div>
        </div>

        {/* 오른쪽: 카카오 히트맵 */}
        <div className="w-full xl:w-[420px] shrink-0">
          <div className="bg-white rounded-xl shadow p-4 space-y-3">
            <h2 className="text-sm font-semibold text-gray-700">시도별 재난 현황</h2>
            <KakaoMetroMap
              todayOnly={false}
              zoomable={false}
              selectedSido={formState.sido}
              sigunguStats={sigunguAllStats}
            />

            {/* 재난 통계 요약 */}
            <div className="pt-2 border-t space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">총 발생 건수</span>
                <span className="font-semibold">
                  {alertStats ? alertStats.totalCount.toLocaleString("ko-KR") + "건" : "-"}
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

            {/* 날씨 정보 */}
            <div className="pt-2 border-t">
              <h3 className="text-sm font-semibold text-gray-700 mb-2">
                날씨 정보{formState.sido ? ` · ${formState.sido}` : ""}
              </h3>
              {(() => {
                const w = formState.sido ? DUMMY_WEATHER[formState.sido] : null;
                if (!w) {
                  return (
                    <p className="text-xs text-gray-400 text-center py-3">
                      시/도를 선택하면 해당 지역 날씨가 표시됩니다
                    </p>
                  );
                }
                return (
                  <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                    <div className="flex justify-between">
                      <span className="text-gray-500">온도</span>
                      <span className="font-medium">{w.temp}°C</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-500">습도</span>
                      <span className="font-medium">{w.humidity}%</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-500">강수량</span>
                      <span className="font-medium">{w.rainfall}mm</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-500">풍향</span>
                      <span className="font-medium">{w.windDir}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-500">풍속</span>
                      <span className="font-medium">{w.windSpeed}m/s</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-500">미세먼지</span>
                      <span className={`font-medium ${DUST_COLOR[w.dustLevel]}`}>
                        {w.dust}㎍/㎥ ({w.dustLevel})
                      </span>
                    </div>
                  </div>
                );
              })()}
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

            {/* 시/군/구 TOP N 랭킹 */}
            {sigunguRanking.length > 0 && (
              <div className="pt-2 border-t">
                <h3 className="text-sm font-semibold text-gray-700 mb-2">
                  시/군/구 TOP {sigunguRanking.length} · {formState.sido}
                </h3>
                <ol className="space-y-1">
                  {sigunguRanking.map(({ name, count }, i) => (
                    <li key={name} className="flex items-center gap-2 text-sm">
                      <span className="w-4 text-xs text-gray-400 font-medium shrink-0">{i + 1}</span>
                      <span className="flex-1 text-gray-700 truncate">{name}</span>
                      <span className="text-gray-500 text-xs shrink-0">{count.toLocaleString("ko-KR")}건</span>
                    </li>
                  ))}
                </ol>
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}