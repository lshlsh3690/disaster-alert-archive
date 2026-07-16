"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useUserAlert } from "@/lib/queries/useAlerts";
import { LEVEL_OPTIONS } from "@/ui/level";
import { useI18n } from "@/hooks/useI18n";

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
  const t = useI18n();
  const params = useParams<{ id: string }>();
  const router = useRouter();
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
      const occurredAt = (data)?.occurredAt ? String((data)?.occurredAt).slice(0,16) : undefined;
      reset({
        title: (data)?.title ?? "",
        message: (data)?.message ?? "",
        disasterType: (data)?.disasterType ?? undefined,
        disasterLevel: (data)?.emergencyLevel ?? undefined,
        occurredAt,
      });
    }
  }, [isLoading, data, reset]);

  const onSubmit = async (v: EditForm) => {
    await update({ id, payload: v });
    router.push(`/alerts/${id}?source=USER`);
  };

  if (isLoading) return <main className="p-6">{t("alertReport.loading")}</main>;

  return (
    <main className="p-6 space-y-4">
      <h1 className="text-xl font-semibold">{t("alertReport.editTitle")}</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="bg-white rounded-xl shadow p-4 space-y-3">
        <div>
          <label className="block text-sm font-medium mb-1">{t("alertReport.titleLabel")}</label>
          <input {...register("title")} placeholder={t("alertReport.titleLabel")} className="input w-full" />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">{t("alertReport.contentLabel")}</label>
          <textarea {...register("message")} placeholder={t("alertReport.contentLabel")} className="input w-full h-32" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium mb-1">{t("alertReport.typeLabel")}</label>
            <select {...register("disasterType")} className="input" defaultValue={(data)?.disasterType ?? ""}>
              <option value="">{t("alertReport.select")}</option>
              {DISASTER_TYPES.map((type) => (
                <option key={type} value={type}>{t(`disasterTypes.${type}`, { defaultValue: type })}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">{t("alertReport.levelLabel")}</label>
            <select {...register("disasterLevel")} className="input" defaultValue={(data)?.emergencyLevel ?? ""}>
              {LEVEL_OPTIONS.map((o) => (
                <option key={o.code} value={o.code}>{t(`levels.${o.text}`, { defaultValue: o.text })}</option>
              ))}
            </select>
          </div>
          <div className="col-span-2">
            <label className="block text-sm font-medium mb-1">{t("alertReport.occurredAtLabel")}</label>
            <input {...register("occurredAt")} type="datetime-local" className="input w-full" />
          </div>
        </div>
        <div className="flex gap-2 justify-end">
          <button type="button" className="px-3 py-2 rounded bg-gray-100" onClick={() => router.back()}>
            {t("alertReport.cancel")}
          </button>
          <button type="submit" className="px-3 py-2 rounded bg-blue-600 text-white">
            {t("alertReport.save")}
          </button>
        </div>
      </form>
    </main>
  );
}
