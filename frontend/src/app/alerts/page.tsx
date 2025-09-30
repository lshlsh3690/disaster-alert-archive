"use client";

import ReportButton from "@/components/alerts/ReportButton";
import { useSearchAlerts } from "@/lib/queries/useAlerts";
import { Alert } from "@/types/alerts";
import { LEVEL_OPTIONS, levelTextToCode } from "@/ui/level";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

const ZSearch = z.object({
  region: z.string().optional(),
  districtCode: z.string().optional(),
  startDate: z.string().optional(), // YYYY-MM-DD
  endDate: z.string().optional(),
  type: z.string().optional(),
  levelText: z.string().optional(), // "안전안내" ...
  keyword: z.string().optional(),
});
type SearchForm = z.infer<typeof ZSearch>;

export default function DisasterListPage() {
  const sp = useSearchParams();

  const [page, setPage] = useState<number>(0);
  const [size, setSize] = useState<number>(10);
  const [formState, setFormState] = useState<SearchForm>({});

  const { register, handleSubmit, reset } = useForm<SearchForm>({
    resolver: zodResolver(ZSearch),
    defaultValues: {},
  });

  const buildParams = (f: SearchForm) => {
    const levelCode = levelTextToCode(f.levelText);
    return {
      region: f.region || undefined,
      districtCode: f.districtCode || undefined,
      startDate: f.startDate || undefined,
      endDate: f.endDate || undefined,
      type: f.type || undefined,
      level: levelCode, // 백엔드 enum 코드로 전송
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
    setFormState(v);
  };
  const onReset = () => {
    reset({});
    setFormState({});
    setPage(0);
  };

  return (
    <main className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">🗂 재난 문자 아카이브</h1>
          <p className="text-sm text-gray-500">
            과거 수신된 모든 재난 문자 목록입니다. 지역/날짜/키워드로 검색할 수 있어요.
          </p>
        </div>
        <ReportButton />
      </div>

      {/* 검색 필터 바 */}
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="bg-white rounded-xl shadow p-4 grid grid-cols-2 md:grid-cols-4 gap-3"
      >
        <input {...register("region")} placeholder="지역명 (예: 서울특별시)" className="input" />
        <input {...register("districtCode")} placeholder="법정동 코드" className="input" />
        <input {...register("startDate")} type="date" className="input" />
        <input {...register("endDate")} type="date" className="input" />
        <input {...register("type")} placeholder="유형(예: 호우)" className="input" />

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

      {/* 목록 */}
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

            {/* 페이지네이션 */}
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
