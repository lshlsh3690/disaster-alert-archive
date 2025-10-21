"use client";
import { useState } from "react";
import { useCommunityPosts, useCreateCommunityPost } from "@/lib/queries/useCommunity";
import { useAuthStore } from "@/store/authStore";

export default function CommunityPage() {
  const [tab, setTab] = useState<"NOTICE" | "FREE">("NOTICE");
  const { data } = useCommunityPosts(tab, { page: 0, size: 10 });
  const authUser = useAuthStore((s) => s.user);
  const canWriteNotice = authUser?.role === "ADMIN";
  const canWriteFree = !!authUser;
  const createNotice = useCreateCommunityPost("NOTICE");
  const createFree = useCreateCommunityPost("FREE");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [showForm, setShowForm] = useState(false);

  // 탭 전환 시 작성 폼 닫기
  if (typeof window !== "undefined") {
    // 간단한 동작: 렌더 루프 방지 위해 setTimeout
    // (useEffect를 사용할 수도 있으나 컬럼 수를 줄이기 위해 가벼운 처리)
    // 탭 변경이 감지되면 폼을 닫는다
  }

  const canWrite = tab === "NOTICE" ? canWriteNotice : canWriteFree;
  const onSubmit = async () => {
    if (!canWrite) return alert("작성 권한이 없습니다.");
    const t = title.trim();
    const c = content.trim();
    if (!t || !c) return;
    if (tab === "NOTICE") await createNotice.mutateAsync({ title: t, content: c });
    else await createFree.mutateAsync({ title: t, content: c });
    setTitle("");
    setContent("");
    if (tab === "FREE") setShowForm(false);
  };

  return (
    <main className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">💬 커뮤니티</h1>
      <div className="flex gap-2">
        <button className={`px-3 py-1 rounded ${tab === "NOTICE" ? "bg-blue-600 text-white" : "bg-gray-100"}`} onClick={() => setTab("NOTICE")}>공지사항</button>
        <button className={`px-3 py-1 rounded ${tab === "FREE" ? "bg-blue-600 text-white" : "bg-gray-100"}`} onClick={() => setTab("FREE")}>자유게시판</button>
      </div>

      <section className="bg-white rounded-xl p-4 shadow space-y-3">
        <h2 className="text-lg font-semibold">{tab === "NOTICE" ? "공지사항" : "자유게시판"}</h2>
        {tab === "NOTICE" ? (
          canWrite ? (
            <div className="space-y-2">
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" className="w-full border rounded px-3 py-2" />
              <textarea value={content} onChange={(e) => setContent(e.target.value)} placeholder="내용" className="w-full border rounded px-3 py-2 h-28" />
              <div className="text-right">
                <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={onSubmit}>등록</button>
              </div>
            </div>
          ) : (
            <div className="text-sm text-gray-500">공지 작성은 관리자만 가능합니다.</div>
          )
        ) : (
          // FREE 탭: 자동 표시 대신 버튼으로 전환
          <>
            {canWriteFree ? (
              !showForm ? (
                <div className="text-right">
                  <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => setShowForm(true)}>게시글 등록하기</button>
                </div>
              ) : (
                <div className="space-y-2">
                  <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" className="w-full border rounded px-3 py-2" />
                  <textarea value={content} onChange={(e) => setContent(e.target.value)} placeholder="내용" className="w-full border rounded px-3 py-2 h-28" />
                  <div className="flex justify-end gap-2">
                    <button className="px-3 py-1 border rounded" onClick={() => { setShowForm(false); setTitle(""); setContent(""); }}>취소</button>
                    <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={onSubmit}>등록</button>
                  </div>
                </div>
              )
            ) : (
              <div className="text-sm text-gray-500">로그인 후 자유 게시글을 작성할 수 있어요.</div>
            )}
          </>
        )}
      </section>

      <section className="bg-white rounded-xl p-4 shadow space-y-2">
        {(data?.content ?? []).map(post => (
          <div key={post.id} className="border-b last:border-b-0 pb-2">
            <div className="font-medium">{post.title}</div>
            <div className="text-sm text-gray-600 line-clamp-2">{post.content}</div>
            <div className="text-xs text-gray-400">{post.authorNickname ?? "익명"} · {new Date(post.createdAt).toLocaleString()}</div>
          </div>
        ))}
        {data && data.content.length === 0 && (
          <div className="text-sm text-gray-500">등록된 게시글이 없습니다.</div>
        )}
      </section>
    </main>
  )
}
