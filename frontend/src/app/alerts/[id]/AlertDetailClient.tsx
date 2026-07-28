"use client";

import Link from "next/link";
import { useAlert, useUserAlert } from "@/lib/queries/useAlerts";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useTranslation } from "react-i18next";
import { useDeleteUserAlert } from "@/lib/mutations/useDeleteUserAlert";
import { useAuthStore } from "@/store/authStore";
import { useLanguageStore } from "@/store/languageStore";
import { useComments, useCreateComment, useDeleteComment, useInfiniteComments, useUpdateComment } from "@/lib/queries/useComments";
import { useEffect, useRef, useState } from "react";
import AlertRiskSection from "@/components/alerts/AlertRiskSection";
import { disasterTypeChipStyle } from "@/ui/disasterTypeColor";

const LEVEL_BADGE: Record<string, { backgroundColor: string; color: string }> = {
  안전안내: { backgroundColor: "var(--blue-soft)", color: "var(--blue)" },
  긴급재난: { backgroundColor: "#fff1e3", color: "#c1670c" },
  위급재난: { backgroundColor: "var(--coral-soft)", color: "#c0473b" },
};
function levelBadgeStyle(text?: string | null) {
  return LEVEL_BADGE[text ?? ""] ?? { backgroundColor: "#eef1f5", color: "var(--text-muted)" };
}

export default function AlertDetailClient() {
  const params = useParams<{ id: string }>();
  const sp = useSearchParams();
  const router = useRouter();
  const id = Number(params.id);
  const source = (sp.get("source") || "OFFICIAL").toUpperCase();
  const isUser = source === "USER";
  // 헤더의 전역 언어 선택(useLanguageStore)을 그대로 따른다 — 예전엔 이 페이지만
  // "한국어/English" 버튼으로 별도 로컬 lang을 관리해서, 헤더에서 언어를 바꿔도
  // 본문 번역(useAlert의 lang 파라미터)엔 반영이 안 되던 버그가 있었다.
  const language = useLanguageStore((s) => s.language);
  const { t } = useTranslation();
  const { data: offData, isLoading: offLoading } = useAlert(isUser ? 0 : id, language);
  const { data: userData, isLoading: userLoading } = useUserAlert(isUser ? id : 0);
  const data = isUser ? userData : offData;
  const isLoading = isUser ? userLoading : offLoading;
  const { mutateAsync: deleteUser } = useDeleteUserAlert();
  const authUser = useAuthStore((s) => s.user);
  const canEdit = isUser && authUser && (
    authUser.role === "ADMIN" || authUser.memberId === (userData)?.createdById
  );

  const { data: comments } = useComments({ source: isUser ? "USER" : "OFFICIAL", targetId: id });
  const infinite = useInfiniteComments({ source: isUser ? "USER" : "OFFICIAL", targetId: id, size: 10 });
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = infinite;
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!loadMoreRef.current) return;
    const el = loadMoreRef.current;
    const io = new IntersectionObserver((entries) => {
      const first = entries[0];
      if (first.isIntersecting && hasNextPage && !isFetchingNextPage) {
        fetchNextPage();
      }
    }, { rootMargin: "0px 0px -200px 0px", threshold: 0 });
    io.observe(el);
    return () => io.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const { mutateAsync: addComment } = useCreateComment();
  const { mutateAsync: removeComment } = useDeleteComment(isUser ? "USER" : "OFFICIAL", id);
  const { mutateAsync: editComment } = useUpdateComment(isUser ? "USER" : "OFFICIAL", id);
  const [commentText, setCommentText] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingText, setEditingText] = useState("");

  if (isLoading) return <main className="p-6 text-[13px] text-[var(--text-muted)]">{t("loading")}</main>;
  if (!data) return <main className="p-6 text-[13px] text-[var(--text-muted)]">{t("alertDetail.notFound")}</main>;

  const translated = language !== "ko";
  const translatedRegions = translated && offData?.translatedRegionNames?.length
    ? offData.translatedRegionNames
    : null;
  const regionText = translatedRegions
    ? translatedRegions.join(", ")
    : Array.isArray(data?.regionNames) && data.regionNames.length > 0
      ? data.regionNames.join(", ")
      : (isUser ? "-" : (offData?.originalRegion ?? "-"));

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-6xl space-y-4 px-4 py-8 sm:px-6">
        <h1 className="text-xl font-bold tracking-tight text-[var(--ink)]">📨 {t("alertDetail.title")}</h1>
        {/* 좌측 문자 상세(가변) / 우측 위험도 분석(340px 고정) — /alerts 목록 페이지와 동일 비율 */}
        <div className={`grid grid-cols-1 gap-4 sm:gap-6 items-start ${!isUser ? "xl:grid-cols-[minmax(0,1fr)_340px]" : ""}`}>
        <div className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-5 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <div className="flex flex-wrap items-center gap-2">
            {data.disasterType && (
              <span style={disasterTypeChipStyle(data.disasterType)} className="rounded-[var(--radius-pill)] px-2 py-0.5 text-[11px] font-medium">
                {(translated && offData?.translatedDisasterType) ? offData.translatedDisasterType : (t(`disasterTypes.${data.disasterType}`, { defaultValue: data.disasterType }))}
              </span>
            )}
            {data.emergencyLevelText && (
              <span style={levelBadgeStyle(data.emergencyLevelText)} className="rounded-[var(--radius-pill)] px-2 py-0.5 text-[11px] font-medium">
                {t(`levels.${data.emergencyLevelText}`, { defaultValue: data.emergencyLevelText })}
              </span>
            )}
            {isUser && (
              <span className="rounded-[var(--radius-pill)] bg-[#eef1f5] px-2 py-0.5 text-[11px] font-medium text-[var(--text-muted)]">
                {t("alertList.filter.sourceUser")}
              </span>
            )}
            <time className="ml-auto text-xs text-[var(--text-subtle)]">{new Date(data.createdAt).toLocaleString()}</time>
          </div>

          <p className="text-[15px] leading-relaxed text-[var(--ink)]">
            {translated && offData?.translatedMessage
              ? offData.translatedMessage
              : data.message}
          </p>
          {translated && !offData?.translatedMessage && !isUser && (
            <p className="text-[13px] text-[var(--text-subtle)]">{t("alertDetail.translationPending")}</p>
          )}

          <dl className="space-y-1.5 border-t border-[var(--line)] pt-3 text-[13px]">
            <div className="flex gap-2">
              <dt className="w-20 shrink-0 text-[var(--text-muted)]">{t("alertDetail.region")}</dt>
              <dd className="min-w-0 text-[var(--text-body)]">{regionText}</dd>
            </div>
            {!isUser && (
              <div className="flex gap-2">
                <dt className="w-20 shrink-0 text-[var(--text-muted)]">SN</dt>
                <dd className="min-w-0 text-[var(--text-body)]">{offData?.sn ?? "-"}</dd>
              </div>
            )}
          </dl>

          {!isUser && offData?.eventId && (
            <Link
              href={`/events/${offData.eventId}`}
              className="inline-flex items-center gap-1 text-sm font-medium text-[var(--blue)] hover:underline"
            >
              {t("events.viewEvent")} →
            </Link>
          )}
          {canEdit && (
            <div className="pt-1 flex gap-2">
              <button
                className="rounded-[var(--radius-control)] bg-[var(--blue)] px-3 py-1.5 text-sm font-medium text-white transition hover:brightness-95"
                onClick={() => router.push(`/alerts/${id}/edit?source=USER`)}
              >
                {t("alertDetail.edit")}
              </button>
              <button
                className="rounded-[var(--radius-control)] border border-[#f3c7c1] px-3 py-1.5 text-sm font-medium text-[var(--coral)] transition-colors hover:bg-[var(--coral-soft)]"
                onClick={async () => {
                  if (!confirm(t("alertDetail.deleteConfirm"))) return;
                  await deleteUser(id);
                  router.push("/alerts?page=0");
                }}
              >
                {t("alertDetail.delete")}
              </button>
            </div>
          )}
        </div>
        {/* 위험도 분석 (공식 재난문자만 — 이벤트 클러스터링 기반) */}
        {!isUser && <AlertRiskSection alertId={id} alertCreatedAt={offData?.createdAt} />}
        </div>
        <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-5 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <h2 className="text-lg font-semibold text-[var(--ink)]">{t("alertDetail.comments")}</h2>
          {authUser ? (
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                placeholder={t("alertDetail.commentPlaceholder")}
                className="input flex-1"
              />
              <button
                className="w-full rounded-[var(--radius-control)] bg-[var(--blue)] px-4 py-2 text-sm font-semibold text-white transition hover:brightness-95 sm:w-auto"
                onClick={async () => {
                  const text = commentText.trim();
                  if (!text) return;
                  await addComment({ source: isUser ? "USER" : "OFFICIAL", targetId: id, content: text });
                  setCommentText("");
                }}
              >{t("alertDetail.submit")}</button>
            </div>
          ) : (
            <div className="flex items-center justify-between gap-3 rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--canvas)] px-3 py-2">
              <span className="text-sm text-[var(--text-muted)]">{t("alertDetail.loginToComment")}</span>
              <button
                className="rounded-[var(--radius-control)] bg-[var(--blue)] px-3 py-1.5 text-sm font-medium text-white transition hover:brightness-95"
                onClick={() => {
                  const redirect = encodeURIComponent(location.pathname + location.search);
                  router.push(`/login?redirect=${redirect}`);
                }}
              >{t("nav.login")}</button>
            </div>
          )}
          <ul className="divide-y divide-[var(--line)]">
            {(infinite.data?.pages.flatMap(p => p.content) ?? comments?.content ?? []).map((c) => {
              const canModify = authUser && (authUser.role === "ADMIN" || authUser.memberId === (c)?.authorId);
              const isEditing = editingId === c.id;
              return (
                <li key={c.id} className="py-3 flex items-start justify-between gap-3">
                  <div className="text-sm flex-1 min-w-0">
                    <div className="text-[var(--text-body)]">{c.content}</div>
                        <div className="mt-0.5 text-xs text-[var(--text-subtle)]">
                          {c.authorNickname ?? t("dashboard.anonymous")} · {new Date(c.createdAt).toLocaleString()}
                          {c.edited && (
                            <span className="ml-2 text-[11px]">({t("alertDetail.edited")} · {c.updatedAt ? new Date(c.updatedAt).toLocaleString() : ""})</span>
                          )}
                        </div>
                    {isEditing && (
                      <div className="mt-2 flex gap-2">
                        <input
                          value={editingText}
                          onChange={(e) => setEditingText(e.target.value)}
                          className="input flex-1"
                        />
                        <button
                          className="rounded-[var(--radius-control)] bg-[var(--blue)] px-3 py-1.5 text-sm font-medium text-white transition hover:brightness-95"
                          onClick={async () => {
                            const text = editingText.trim();
                            if (!text) return;
                            await editComment({ id: c.id, content: text });
                            setEditingId(null);
                            setEditingText("");
                          }}
                        >{t("alertDetail.save")}</button>
                        <button
                          className="rounded-[var(--radius-control)] border border-[var(--line)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)]"
                          onClick={() => {
                            setEditingId(null);
                            setEditingText("");
                          }}
                        >{t("alertDetail.cancel")}</button>
                      </div>
                    )}
                  </div>
                  {canModify && !isEditing && (
                    <div className="flex shrink-0 items-center gap-2">
                      <button
                        className="text-xs text-[var(--text-muted)] hover:text-[var(--blue)]"
                        onClick={() => {
                          setEditingId(c.id);
                          setEditingText(c.content);
                        }}
                      >{t("alertDetail.edit")}</button>
                      <button
                        className="text-xs text-[var(--coral)] hover:underline"
                        onClick={async () => {
                          if (!authUser) {
                            const redirect = encodeURIComponent(location.pathname + location.search);
                            router.push(`/login?redirect=${redirect}`);
                            return;
                          }
                          if (!confirm(t("alertDetail.deleteCommentConfirm"))) return;
                          await removeComment(c.id);
                        }}
                      >{t("alertDetail.delete")}</button>
                    </div>
                  )}
                </li>
              );
            })}
            {((infinite.data?.pages?.[0]?.content.length ?? 0) === 0) && (
              <li className="py-4 text-sm text-[var(--text-muted)]">첫 댓글을 작성해보세요.</li>
            )}
          </ul>
          <div ref={loadMoreRef} className="h-16" />
          {infinite.hasNextPage && !infinite.isFetchingNextPage && (
            <div className="text-center py-2">
              <button
                className="rounded-[var(--radius-control)] border border-[var(--line)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)]"
                onClick={() => infinite.fetchNextPage()}
              >더 보기</button>
            </div>
          )}
          {infinite.isFetchingNextPage && (
            <div className="text-center text-sm text-[var(--text-muted)] py-2">{t("loading")}</div>
          )}
          {!infinite.hasNextPage && (infinite.data?.pages?.[0]?.totalElements ?? 0) > 0 && (
            <div className="text-center text-xs text-[var(--text-subtle)] py-2">마지막 댓글입니다.</div>
          )}
        </section>
      </div>
    </main>
  );
}
