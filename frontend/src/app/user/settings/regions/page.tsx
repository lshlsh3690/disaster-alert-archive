"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useFavoriteRegions } from "@/lib/queries/useFavoriteRegions";
import { useAddFavoriteRegion } from "@/lib/mutations/useAddFavoriteRegion";
import { useDeleteFavoriteRegion } from "@/lib/mutations/useDeleteFavoriteRegion";

const MAX_REGIONS = 5;

type JusoItem = {
  jibunAddr?: string;
  roadAddr?: string;
  siNm?: string;
  sggNm?: string;
  emdNm?: string;
  admCd: string;
};

export default function FavoriteRegionsPage() {
  const router = useRouter();
  const isLoggedIn = useAuthStore((s) => s.user !== null);

  useEffect(() => {
    if (!isLoggedIn) router.push("/login");
  }, [isLoggedIn, router]);

  const { data: regions = [], isLoading } = useFavoriteRegions();

  const [isAddrModalOpen, setIsAddrModalOpen] = useState(false);
  const [addrQuery, setAddrQuery] = useState("");
  const [addrResults, setAddrResults] = useState<Array<{ name: string; code: string }>>([]);
  const [addrLoading, setAddrLoading] = useState(false);
  const [addrError, setAddrError] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string>("");

  const addMutation = useAddFavoriteRegion({
    onSuccessCallback: () => {
      setIsAddrModalOpen(false);
      setAddrQuery("");
      setAddrResults([]);
      setErrorMsg("");
    },
    onErrorCallback: (msg) => setErrorMsg(msg),
  });

  const deleteMutation = useDeleteFavoriteRegion({
    onErrorCallback: (msg) => setErrorMsg(msg),
  });

  const searchJuso = async () => {
    const key = process.env.NEXT_PUBLIC_JUSO_API_KEY;
    if (!key) {
      setAddrError("주소 API 키가 설정되지 않았습니다. (NEXT_PUBLIC_JUSO_API_KEY)");
      return;
    }
    if (!addrQuery || addrQuery.trim().length < 2) {
      setAddrError("주소를 두 글자 이상 입력하세요.");
      return;
    }
    try {
      setAddrError(null);
      setAddrLoading(true);

      const params = new URLSearchParams({
        confmKey: key,
        currentPage: "1",
        countPerPage: "20",
        keyword: addrQuery.trim(),
        resultType: "json",
      });
      const res = await fetch(`https://www.juso.go.kr/addrlink/addrLinkApi.do?${params.toString()}`);
      const json = await res.json().catch(() => null);
      const juso = json?.results?.juso || [];
      const items = juso.map((it: JusoItem) => ({
        name: it.jibunAddr || it.roadAddr || `${it.siNm ?? ""} ${it.sggNm ?? ""} ${it.emdNm ?? ""}`.trim(),
        code: it.admCd,
      }));
      setAddrResults(items);
    } catch (e) {
      setAddrError("주소 검색 중 오류가 발생했습니다. " + String(e));
    } finally {
      setAddrLoading(false);
    }
  };

  const handleSelect = (code: string) => {
    if (regions.length >= MAX_REGIONS) {
      setErrorMsg(`관심지역은 최대 ${MAX_REGIONS}개까지 등록할 수 있습니다.`);
      return;
    }
    addMutation.mutate(code);
  };

  const handleDelete = (code: string) => {
    if (!confirm("이 관심지역을 삭제하시겠습니까?")) return;
    deleteMutation.mutate(code);
  };

  return (
    <main className="p-6 space-y-6 max-w-6xl mx-auto">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">⭐ 관심지역 설정</h1>
        <p className="text-sm text-gray-500 mt-1">
          최대 {MAX_REGIONS}개의 관심지역을 등록하고 재난문자 알림을 받을 수 있습니다.
        </p>
      </header>

      {errorMsg && (
        <div className="bg-red-50 border border-red-200 text-red-600 text-sm rounded-lg p-3">{errorMsg}</div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 왼쪽: 등록된 관심지역 */}
        <section className="bg-white rounded-xl shadow p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-800">등록된 관심지역</h2>
            <span className="text-sm text-gray-500">
              {regions.length} / {MAX_REGIONS}
            </span>
          </div>

          {isLoading ? (
            <div className="text-sm text-gray-500">불러오는 중...</div>
          ) : regions.length === 0 ? (
            <div className="text-sm text-gray-500 py-8 text-center">아직 등록된 관심지역이 없습니다.</div>
          ) : (
            <ul className="space-y-2">
              {regions.map((r) => (
                <li
                  key={r.legalDistrictCode}
                  className="flex items-center justify-between p-3 border border-gray-200 rounded-lg hover:bg-gray-50"
                >
                  <div>
                    <div className="font-medium text-gray-800">{r.regionName}</div>
                    <div className="text-xs text-gray-500">법정동코드: {r.legalDistrictCode}</div>
                  </div>
                  <button
                    onClick={() => handleDelete(r.legalDistrictCode)}
                    disabled={deleteMutation.isPending}
                    className="px-3 py-1 text-sm border border-red-300 text-red-500 rounded hover:bg-red-50 transition disabled:opacity-50"
                  >
                    삭제
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button
            onClick={() => {
              setErrorMsg("");
              setIsAddrModalOpen(true);
            }}
            disabled={regions.length >= MAX_REGIONS}
            className="mt-4 w-full py-2 border-2 border-dashed border-gray-300 text-gray-600 rounded-lg hover:border-blue-400 hover:text-blue-500 transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            + 관심지역 추가
          </button>
        </section>

        {/* 오른쪽: 안내 */}
        <section className="bg-white rounded-xl shadow p-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">🔍 지역 추가 방법</h2>
          <ol className="text-sm text-gray-600 space-y-2 list-decimal list-inside">
            <li>
              왼쪽의 <span className="font-medium">+ 관심지역 추가</span> 버튼을 클릭하세요.
            </li>
            <li>주소 검색 창에서 시/도, 시/군/구, 동 이름으로 검색하세요.</li>
            <li>원하는 지역을 선택하면 등록됩니다.</li>
            <li>등록된 지역에 재난문자가 발령되면 알림을 받을 수 있습니다.</li>
          </ol>
          <div className="mt-4 p-3 bg-blue-50 text-sm text-blue-700 rounded-lg">
            💡 동(洞) 단위까지 세부적으로 등록할 수 있어요.
          </div>
        </section>
      </div>

      {/* 주소 검색 모달 */}
      <Modal open={isAddrModalOpen} onClose={() => setIsAddrModalOpen(false)}>
        <div className="space-y-3">
          <h3 className="text-lg font-semibold">주소 검색</h3>
          <div className="flex gap-2">
            <input
              className="input w-full"
              value={addrQuery}
              onChange={(e) => setAddrQuery(e.target.value)}
              placeholder="예: 서울특별시 종로구 신교동"
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  searchJuso();
                }
              }}
            />
            <button type="button" className="px-3 py-2 rounded bg-blue-600 text-white" onClick={searchJuso}>
              검색
            </button>
          </div>

          {addrLoading && <div className="text-sm text-gray-500">검색 중...</div>}
          {addrError && <div className="text-sm text-red-600">{addrError}</div>}

          {!addrLoading && addrResults.length > 0 && (
            <ul className="max-h-72 overflow-auto divide-y">
              {addrResults.map((r) => (
                <li key={`${r.code}-${r.name}`} className="py-2 flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm text-gray-800">{r.name}</div>
                    <div className="text-xs text-gray-500">법정동코드: {r.code}</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleSelect(r.code)}
                    disabled={addMutation.isPending}
                    className="px-3 py-1 rounded bg-gray-100 hover:bg-gray-200 disabled:opacity-50"
                  >
                    선택
                  </button>
                </li>
              ))}
            </ul>
          )}

          {!addrLoading && addrResults.length === 0 && !addrError && (
            <div className="text-sm text-gray-500">검색어를 입력하고 검색을 눌러주세요.</div>
          )}
        </div>
      </Modal>
    </main>
  );
}

function Modal({ open, onClose, children }: { open: boolean; onClose: () => void; children: React.ReactNode }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-xl shadow w-full max-w-2xl p-4" onClick={(e) => e.stopPropagation()}>
        <div className="flex justify-end">
          <button className="px-2 py-1 text-sm" onClick={onClose}>
            닫기
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
