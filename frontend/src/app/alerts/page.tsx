"use client";

import { useSearchAlerts, useSigungu } from "@/lib/queries/useAlerts";
import { Alert } from "@/types/alerts";
import { LEVEL_OPTIONS, levelTextToCode } from "@/ui/level";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useRouter, useSearchParams } from "next/navigation";
import { DISASTER_TYPES } from "@/ui/disasterType";
import { METROS } from "@/ui/metros";

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

  const { register, handleSubmit, reset, watch, setValue } = useForm<SearchForm>({
    resolver: zodResolver(ZSearch),
    defaultValues: {},
  });

  const watchedSido = watch("sido");
  const { data: sigunguList } = useSigungu(watchedSido || undefined);

  useEffect(() => {
    setValue("sigungu", "");
  }, [watchedSido, setValue]);

  const buildParams = (f: SearchForm) => {
    const levelCode = levelTextToCode(f.levelText);
    const region = f.sido && f.sigungu ? `${f.sido} ${f.sigungu}` : f.sido || undefined;
    return {
      region,
      startDate: f.startDate || undefined,
      endDate: f.endDate || undefined,
      type: f.type || undefined,
      level: levelCode,
      keyword: f.keyword || undefined,
      page,
      size,
      sort: "createdAt,desc",
    };
  };
  const params = useMemo(() => buildParams(formState), [formState, page]);

  const { data, isLoading, isFetching } = useSearchAlerts(params);

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
  }, [searchParams]);

  const onReset = () => {
    reset({});
    setFormState({});
    setPage(0);
    router.push("/alerts");
  };

  return (
    <main className="p-6 space-y-6">
      <h1 className="text-xl font-semibold">🗂 재난 문자 아카이브</h1>
      <p className="text-sm text-gray-500">
        과거 수신된 모든 재난 문자 목록입니다. 지역/날짜/키워드로 검색할 수 있어요.
      </p>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="bg-white rounded-xl shadow p-4 grid grid-cols-2 md:grid-cols-4 gap-3"
      >
        <select {...register("sido")} className="input">
          <option value="">시/도(전체)</option>
          {METROS.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <select {...register("sigungu")} className="input" disabled={!watchedSido}>
          <option value="">시/군/구(전체)</option>
          {sigunguList?.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <input {...register("startDate")} type="date" className="input" />
        <input {...register("endDate")} type="date" className="input" />
        <select {...register("type")} className="input">
          <option value="">유형(전체)</option>
          {DISASTER_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <select {...register("levelText")} className="input">
          <option value="">레벨(전체)</option>
          {LEVEL_OPTIONS.map((o) => (
            <option key={o.code} value={o.text}>
              {o.text}
            </option>
          ))}
        </select>
        <input {...register("keyword")} placeholder="키워드(예: 경보)" className="input col-span-2" />
        <div className="col-span-2 md:col-span-4 flex justify-end gap-2">
          <button type="button" onClick={onReset} className="px-3 py-2 rounded bg-gray-100">
            초기화
          </button>
          <button
            type="submit"
            className="px-3 py-2 rounded bg-blue-600 text-white disabled:opacity-60"
            disabled={isFetching}
          >
            {isFetching ? "검색 중..." : "검색"}
          </button>
        </div>
      </form>

      <div className="bg-white rounded-xl shadow p-4">
        {isLoading ? (
          <div className="text-sm text-gray-500">불러오는 중...</div>
        ) : (
          <>
            <ul className="space-y-2">
              {data?.content.map((a: Alert) => {
                const regionLabel = a.regionNames && a.regionNames.length > 0 ? a.regionNames.join(", ") : "-";
                return (
                  <li key={a.id} className="border-b last:border-0 pb-2">
                    <Link href={`/alerts/${a.id}`} className="hover:underline">
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
                이전
              </button>
              <div className="text-sm text-gray-500">{data ? `${data.number + 1} / ${data.totalPages}` : "-"}</div>
              <button
                className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
                onClick={() => setPage((p) => (data ? Math.min(data.totalPages - 1, p + 1) : p))}
                disabled={!data || data.number + 1 >= data.totalPages}
              >
                다음
              </button>
            </div>
          </>
        )}
      </div>
    </main>
  );
}
