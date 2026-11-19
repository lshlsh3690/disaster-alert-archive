"use client";
import { useState } from "react";
import Link from "next/link";
import { useTranslation } from "react-i18next";
import { useCommunityPosts, useCreateCommunityPost } from "@/lib/queries/useCommunity";
import { useAuthStore } from "@/store/authStore";

// 번호 | 제목 | 작성자 | 작성일. 좁은 화면(sm 미만)에서는 표 머리를 숨기고 작성자·작성일을 제목 아래로 내린다.
const BOARD_COLS = "sm:grid-cols-[72px_minmax(0,1fr)_120px_120px]";

export default function CommunityPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<"NOTICE" | "FREE">("NOTICE");
  const { data, isLoading } = useCommunityPosts(tab, { page: 0, size: 10 });
  const authUser = useAuthStore((s) => s.user);
  const canWriteNotice = authUser?.role === "ADMIN";
  const canWriteFree = !!authUser;
  const createNotice = useCreateCommunityPost("NOTICE");
  const createFree = useCreateCommunityPost("FREE");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [showForm, setShowForm] = useState(false);

  const canWrite = tab === "NOTICE" ? canWriteNotice : canWriteFree;

  // 탭 전환 시 작성 폼을 닫는다
  const changeTab = (next: "NOTICE" | "FREE") => {
    setTab(next);
    setShowForm(false);
  };

  const closeForm = () => {
    setShowForm(false);
    setTitle("");
    setContent("");
  };

  const onSubmit = async () => {
    if (!canWrite) return alert("작성 권한이 없습니다.");
    const trimmedTitle = title.trim();
    const trimmedContent = content.trim();
    if (!trimmedTitle || !trimmedContent) return;
    const body = { title: trimmedTitle, content: trimmedContent };
    if (tab === "NOTICE") await createNotice.mutateAsync(body);
    else await createFree.mutateAsync(body);
    closeForm();
  };

  const posts = data?.content ?? [];
  const total = data?.totalElements ?? posts.length;

  return (
    <main className="krds-page krds-page--narrow">
      <div className="krds-page-head">
        <div>
          <p className="krds-breadcrumb">
            <Link href="/">{t("community.breadcrumbHome")}</Link>
            <span aria-hidden="true"> › </span>
            {t("community.title")}
          </p>
          <h1 className="krds-title">{t("community.title")}</h1>
        </div>
        {/* 권한 규칙: 공지는 ADMIN 만, 자유게시판은 로그인 사용자 */}
        {canWrite && (
          <button type="button" className="krds-btn krds-btn--primary" onClick={() => setShowForm((v) => !v)} aria-expanded={showForm}>
            {t("community.write")}
          </button>
        )}
      </div>

      <div className="flex flex-col gap-4">
        <div role="tablist" className="krds-tabs">
          <button type="button" role="tab" aria-selected={tab === "NOTICE"} className="krds-tab" onClick={() => changeTab("NOTICE")}>공지사항</button>
          <button type="button" role="tab" aria-selected={tab === "FREE"} className="krds-tab" onClick={() => changeTab("FREE")}>자유게시판</button>
        </div>

        {!canWrite && (
          <p className="m-0 text-[16px] text-[var(--krds-gray-50)]">
            {tab === "NOTICE" ? "공지 작성은 관리자만 가능합니다." : "로그인 후 자유 게시글을 작성할 수 있어요."}
          </p>
        )}

        {canWrite && showForm && (
          <section className="krds-card flex flex-col gap-3 px-5 py-4">
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" className="krds-control" />
            <textarea value={content} onChange={(e) => setContent(e.target.value)} placeholder="내용" className="krds-control h-28 py-[10px]" />
            <div className="flex justify-end gap-[10px]">
              <button type="button" className="krds-btn krds-btn--secondary krds-btn--sm" onClick={closeForm}>취소</button>
              <button type="button" className="krds-btn krds-btn--primary krds-btn--sm" onClick={onSubmit}>등록</button>
            </div>
          </section>
        )}

        {/* 게시판 표 */}
        <div className="border-t-2 border-[var(--krds-gray-90)] bg-[var(--krds-gray-0)]">
          <div
            className={`hidden border-b border-[var(--krds-gray-20)] bg-[var(--krds-gray-5)] px-4 py-3 text-[15px] font-bold text-[var(--krds-gray-70)] sm:grid ${BOARD_COLS}`}
            aria-hidden="true"
          >
            <span>{t("community.colNo")}</span>
            <span>{t("community.colTitle")}</span>
            <span>{t("community.colAuthor")}</span>
            <span>{t("community.colDate")}</span>
          </div>

          {isLoading ? (
            <p className="krds-empty">{t("loading")}</p>
          ) : data && posts.length === 0 ? (
            <p className="krds-empty">등록된 게시글이 없습니다.</p>
          ) : (
            <ul>
              {posts.map((post, idx) => (
                <li
                  key={post.id}
                  className={`krds-row grid grid-cols-1 items-center gap-x-0 gap-y-1 px-4 py-[14px] text-[16px] ${BOARD_COLS}`}
                >
                  <span className="hidden text-[var(--krds-gray-50)] sm:block">{total - idx}</span>
                  <span className="flex min-w-0 flex-col gap-[3px]">
                    <b className="truncate font-bold text-[var(--krds-gray-90)]">{post.title}</b>
                    <span className="truncate text-[15px] text-[var(--krds-gray-50)]">{post.content}</span>
                  </span>
                  {/* 좁은 화면: 작성자 · 작성일을 한 줄로 */}
                  <span className="flex min-w-0 gap-2 text-[15px] sm:contents sm:text-[16px]">
                    <span className="truncate text-[var(--krds-gray-70)]">{post.authorNickname ?? "익명"}</span>
                    <span className="text-[var(--krds-gray-40)] sm:hidden" aria-hidden="true">·</span>
                    <span className="whitespace-nowrap text-[var(--krds-gray-50)]">
                      {new Date(post.createdAt).toLocaleDateString()}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </main>
  );
}
