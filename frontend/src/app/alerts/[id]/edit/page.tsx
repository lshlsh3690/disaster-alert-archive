"use client";

import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useUserAlert } from "@/lib/queries/useAlerts";
import { LEVEL_OPTIONS } from "@/ui/level";

const DISASTER_TYPES = [
  "호우",
  "태풍",
  "지진",
  "화재",
  "폭염",
  "한파",
  "감염병",
  "대설",
  "강풍",
  "해일",
];
import { useUpdateUserAlert } from "@/lib/mutations/useUpdateUserAlert";

const ZEdit = z.object({
  title: z.string().min(1).max(120),
  message: z.string().min(1).max(2000),
  disasterType: z.string().optional(),
  disasterLevel: z.string().optional(),
  occurredAt: z.string().optional(),
});

type EditForm = z.infer<typeof ZEdit>;

export default function AlertEditPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const sp = useSearchParams();
  const id = Number(params.id);
  const { data, isLoading } = useUserAlert(id);
  const { mutateAsync: update } = useUpdateUserAlert();

  const { register, handleSubmit, reset } = useForm<EditForm>({
    resolver: zodResolver(ZEdit),
    defaultValues: {
      title: "",
      message: "",
      disasterType: undefined,
      disasterLevel: undefined,
      occurredAt: undefined,
    },
  });

  // 초기값 세팅: 렌더 중에 reset 호출 금지 → useEffect에서 1회 세팅
  useEffect(() => {
    if (!isLoading && data) {
      // occurredAt(ISO)를 input type=datetime-local 형식(YYYY-MM-DDTHH:mm)으로 변환
      const occurredAt = (data as any).occurredAt ? String((data as any).occurredAt).slice(0,16) : undefined;
      reset({
        title: (data as any).title ?? "",
        message: (data as any).message ?? "",
        disasterType: (data as any).disasterType ?? undefined,
        disasterLevel: (data as any).emergencyLevel ?? undefined,
        occurredAt,
      });
    }
  }, [isLoading, data, reset]);

  const onSubmit = async (v: EditForm) => {
    await update({ id, payload: v });
    router.push(`/alerts/${id}?source=USER`);
  };

  if (isLoading) return <main className="p-6">불러오는 중...</main>;

  return (
    <main className="p-6 space-y-4">
      <h1 className="text-xl font-semibold">✏️ 사용자 제보 수정</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="bg-white rounded-xl shadow p-4 space-y-3">
        <div>
          <label className="block text-sm font-medium mb-1">제목</label>
          <input {...register("title")} placeholder="제목" className="input w-full" />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">내용</label>
          <textarea {...register("message")} placeholder="내용" className="input w-full h-32" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium mb-1">유형</label>
            <select {...register("disasterType")} className="input" defaultValue={(data as any)?.disasterType ?? ""}>
              <option value="">선택</option>
              {DISASTER_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">레벨</label>
            <select {...register("disasterLevel")} className="input" defaultValue={(data as any)?.emergencyLevel ?? ""}>
              {LEVEL_OPTIONS.map((o) => (
                <option key={o.code} value={o.code}>{o.text}</option>
              ))}
            </select>
          </div>
          <div className="col-span-2">
            <label className="block text-sm font-medium mb-1">발생 시각</label>
            <input {...register("occurredAt")} type="datetime-local" className="input w-full" />
          </div>
        </div>
        <div className="flex gap-2 justify-end">
          <button type="button" className="px-3 py-2 rounded bg-gray-100" onClick={() => router.back()}>
            취소
          </button>
          <button type="submit" className="px-3 py-2 rounded bg-blue-600 text-white">
            저장
          </button>
        </div>
      </form>
    </main>
  );
}
