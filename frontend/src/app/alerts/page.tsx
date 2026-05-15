"use client";

import ReportButton from "@/components/alerts/ReportButton";
import { useSearchCombinedAlerts, useSigungu } from "@/lib/queries/useAlerts";
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
    <main className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">{t.alertList.title}</h1>
          <p className="text-sm text-gray-500">
            {t.alertList.description}
          </p>
        </div>
        <ReportButton />
      </div>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="bg-white rounded-xl shadow p-4 grid grid-cols-2 md:grid-cols-4 gap-3"
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
          {sigunguList?.map((s) => (
            <option key={s.code} value={s.name}>
              {s.translatedName}
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
        <input {...register("keyword")} placeholder={t.alertList.filter.keyword} className="input col-span-2" />
        <div className="col-span-2 md:col-span-4 flex justify-end gap-2">
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
    </main>
  );
}