"use client";

import { useAlert, useUserAlert } from "@/lib/queries/useAlerts";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useDeleteUserAlert } from "@/lib/mutations/useDeleteUserAlert";
import { useAuthStore } from "@/store/authStore";
import { useComments, useCreateComment, useDeleteComment, useInfiniteComments, useUpdateComment } from "@/lib/queries/useComments";
import { useEffect, useRef, useState } from "react";

export default function AlertDetailPage() {
  const params = useParams<{ id: string }>();
  const sp = useSearchParams();
  const router = useRouter();
  const id = Number(params.id);
  const source = (sp.get("source") || "OFFICIAL").toUpperCase();
  const isUser = source === "USER";
  const { data: offData, isLoading: offLoading } = useAlert(isUser ? 0 : id);
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

  if (isLoading) return <main className="p-6">불러오는 중...</main>;
  if (!data) return <main className="p-6">데이터가 없습니다.</main>;

  const regionText = Array.isArray((data)?.regionNames) && (data)?.regionNames.length > 0
    ? (data)?.regionNames.join(", ")
    : (isUser ? "-" : (offData?.originalRegion ?? "-"));

  return (
    <main className="p-6 space-y-4">
      <h1 className="text-xl font-semibold">📨 재난 문자 상세</h1>
      <div className="bg-white rounded-xl shadow p-4 space-y-2">
        <div className="text-sm text-gray-500">{new Date(data.createdAt).toLocaleString()}</div>
        <div className="text-lg">{data.message}</div>
        <div className="text-sm text-gray-600">유형: {data.disasterType ?? "-"}</div>
        <div className="text-sm text-gray-600">레벨: {data.emergencyLevelText ?? "-"}</div>
        <div className="text-sm text-gray-600">지역: {regionText}</div>
        {!(isUser) && <div className="text-xs text-gray-400">SN: {offData?.sn ?? "-"}</div>}
        {canEdit && (
          <div className="pt-2 flex gap-2">
            <button
              className="px-3 py-1 rounded bg-blue-600 text-white"
              onClick={() => router.push(`/alerts/${id}/edit?source=USER`)}
            >
              수정
            </button>
            <button
              className="px-3 py-1 rounded bg-red-600 text-white"
              onClick={async () => {
                if (!confirm("정말 삭제하시겠습니까?")) return;
                await deleteUser(id);
                router.push("/alerts?page=0");
              }}
            >
              삭제
            </button>
          </div>
        )}
      </div>
      <section className="bg-white rounded-xl shadow p-4 space-y-3">
        <h2 className="text-lg font-semibold">댓글</h2>
        {authUser ? (
          <div className="flex gap-2">
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="댓글을 입력하세요"
              className="flex-1 border rounded px-3 py-2"
            />
            <button
              className="px-4 py-2 bg-blue-600 text-white rounded"
              onClick={async () => {
                const text = commentText.trim();
                if (!text) return;
                await addComment({ source: isUser ? "USER" : "OFFICIAL", targetId: id, content: text });
                setCommentText("");
              }}
            >등록</button>
          </div>
        ) : (
          <div className="flex items-center justify-between gap-3 border rounded px-3 py-2 bg-gray-50">
            <span className="text-sm text-gray-600">로그인 후 댓글을 작성할 수 있어요.</span>
            <button
              className="px-3 py-1 text-sm bg-gray-800 text-white rounded"
              onClick={() => {
                const redirect = encodeURIComponent(location.pathname + location.search);
                router.push(`/login?redirect=${redirect}`);
              }}
            >로그인</button>
          </div>
        )}
        <ul className="divide-y">
          {(infinite.data?.pages.flatMap(p => p.content) ?? comments?.content ?? []).map((c) => {
            const canModify = authUser && (authUser.role === "ADMIN" || authUser.memberId === (c)?.authorId);
            const isEditing = editingId === c.id;
            return (
              <li key={c.id} className="py-2 flex items-start justify-between gap-3">
                <div className="text-sm flex-1">
                  <div className="text-gray-800">{c.content}</div>
                      <div className="text-xs text-gray-500">
                        {c.authorNickname ?? "익명"} · {new Date(c.createdAt).toLocaleString()}
                        {c.edited && (
                          <span className="ml-2 text-[11px] text-gray-400">(수정됨 · {c.updatedAt ? new Date(c.updatedAt).toLocaleString() : ""})</span>
                        )}
                      </div>
                  {isEditing && (
                    <div className="mt-2 flex gap-2">
                      <input
                        value={editingText}
                        onChange={(e) => setEditingText(e.target.value)}
                        className="flex-1 border rounded px-3 py-2"
                      />
                      <button
                        className="px-3 py-1 text-sm bg-blue-600 text-white rounded"
                        onClick={async () => {
                          const text = editingText.trim();
                          if (!text) return;
                          await editComment({ id: c.id, content: text });
                          setEditingId(null);
                          setEditingText("");
                        }}
                      >저장</button>
                      <button
                        className="px-3 py-1 text-sm border rounded"
                        onClick={() => {
                          setEditingId(null);
                          setEditingText("");
                        }}
                      >취소</button>
                    </div>
                  )}
                </div>
                {canModify && !isEditing && (
                  <div className="flex items-center gap-2">
                    <button
                      className="text-xs text-gray-600"
                      onClick={() => {
                        setEditingId(c.id);
                        setEditingText(c.content);
                      }}
                    >수정</button>
                    <button
                      className="text-xs text-red-600"
                      onClick={async () => {
                        if (!authUser) {
                          const redirect = encodeURIComponent(location.pathname + location.search);
                          router.push(`/login?redirect=${redirect}`);
                          return;
                        }
                        if (!confirm("삭제하시겠습니까?")) return;
                        await removeComment(c.id);
                      }}
                    >삭제</button>
                  </div>
                )}
              </li>
            );
          })}
          {((infinite.data?.pages?.[0]?.content.length ?? 0) === 0) && (
            <li className="py-4 text-sm text-gray-500">첫 댓글을 작성해보세요.</li>
          )}
        </ul>
        {infinite.hasNextPage && !infinite.isFetchingNextPage && (
          <div className="text-center text-xs text-gray-400 py-2">조금 더 내려가면 댓글을 더 불러옵니다</div>
        )}
        <div ref={loadMoreRef} className="h-16" />
        {infinite.hasNextPage && !infinite.isFetchingNextPage && (
          <div className="text-center py-2">
            <button
              className="px-3 py-1 text-sm border rounded"
              onClick={() => infinite.fetchNextPage()}
            >더 보기</button>
          </div>
        )}
        {infinite.isFetchingNextPage && (
          <div className="text-center text-sm text-gray-500 py-2">불러오는 중...</div>
        )}
        {!infinite.hasNextPage && (infinite.data?.pages?.[0]?.totalElements ?? 0) > 0 && (
          <div className="text-center text-xs text-gray-400 py-2">마지막 댓글입니다.</div>
        )}
      </section>
    </main>
  );
}
