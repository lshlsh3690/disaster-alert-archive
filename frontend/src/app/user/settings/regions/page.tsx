"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useAuthStore } from "@/store/authStore";
import { useLanguageStore } from "@/store/languageStore";
import { useFavoriteRegions } from "@/lib/queries/useFavoriteRegions";
import { useAddFavoriteRegion } from "@/lib/mutations/useAddFavoriteRegion";
import { useDeleteFavoriteRegion } from "@/lib/mutations/useDeleteFavoriteRegion";
import { useGuestFavoriteSync } from "@/hooks/useGuestFavoriteSync";
import { useSigungu } from "@/lib/queries/useAlerts";
import { useI18n } from "@/hooks/useI18n";
import { METROS } from "@/ui/metros";

const MAX_REGIONS = 5;

export default function FavoriteRegionsPage() {
  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const language = useLanguageStore((s) => s.language);
  const t = useI18n();

  // 로그인 시 게스트 데이터를 서버에 자동 병합
  useGuestFavoriteSync();

  const { data: regions = [], isLoading } = useFavoriteRegions();

  const [selectedSido, setSelectedSido] = useState<string>("");
  const [selectedSigunguCode, setSelectedSigunguCode] = useState<string>("");
  const [errorMsg, setErrorMsg] = useState<string>("");

  const { data: sigunguList = [], isLoading: sigunguLoading } = useSigungu(
    selectedSido || undefined,
    language
  );

  const getRegionName = (code: string) => {
    const found = sigunguList.find((s) => (s.code ?? "") === code);
    if (!found) return code;
    return found.name === "전체"
      ? `${selectedSido} 전체`
      : (found.translatedName ?? found.name);
  };

  const addMutation = useAddFavoriteRegion({
    onSuccessCallback: () => {
      setSelectedSido("");
      setSelectedSigunguCode("");
      setErrorMsg("");
    },
    onErrorCallback: (msg) => setErrorMsg(msg),
    getRegionName,
  });

  const deleteMutation = useDeleteFavoriteRegion({
    onErrorCallback: (msg) => setErrorMsg(msg),
  });

  const handleAdd = () => {
    if (!selectedSigunguCode) {
      setErrorMsg("지역을 선택해주세요.");
      return;
    }
    if (regions.length >= MAX_REGIONS) {
      setErrorMsg(`관심지역은 최대 ${MAX_REGIONS}개까지 등록할 수 있습니다.`);
      return;
    }
    addMutation.mutate(selectedSigunguCode);
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

      {/* 비로그인 안내 배너 */}
      {!isLoggedIn && (
        <div className="bg-amber-50 border border-amber-200 text-amber-800 text-sm rounded-lg p-4 flex items-start gap-3">
          <span className="text-lg leading-none">🔔</span>
          <div>
            <p className="font-medium">비로그인 상태입니다.</p>
            <p className="mt-0.5 text-amber-700">
              관심지역은 이 기기의 브라우저에 임시 저장됩니다.{" "}
              <Link href="/login" className="underline font-semibold hover:text-amber-900">
                로그인
              </Link>
              하시면 서버에 영구 저장되며 다른 기기에서도 사용할 수 있습니다.
            </p>
          </div>
        </div>
      )}

      {errorMsg && (
        <div className="bg-red-50 border border-red-200 text-red-600 text-sm rounded-lg p-3">
          {errorMsg}
        </div>
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
            <div className="text-sm text-gray-500 py-8 text-center">
              아직 등록된 관심지역이 없습니다.
            </div>
          ) : (
            <ul className="space-y-2">
              {regions.map((r) => (
                <li
                  key={r.legalDistrictCode}
                  className="flex items-center justify-between p-3 border border-gray-200 rounded-lg hover:bg-gray-50"
                >
                  <div className="font-medium text-gray-800">{r.regionName}</div>
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
        </section>

        {/* 오른쪽: 지역 추가 */}
        <section className="bg-white rounded-xl shadow p-6 space-y-4">
          <h2 className="text-lg font-semibold text-gray-800">🔍 지역 추가</h2>

          <div className="space-y-3">
            <div>
              <label className="block text-sm text-gray-600 mb-1">시/도</label>
              <select
                value={selectedSido}
                onChange={(e) => {
                  setSelectedSido(e.target.value);
                  setSelectedSigunguCode("");
                }}
                className="input w-full"
              >
                <option value="">시/도를 선택하세요</option>
                {METROS.map((m) => (
                  <option key={m} value={m}>
                    {t.metros[m]}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm text-gray-600 mb-1">시/군/구</label>
              <select
                value={selectedSigunguCode}
                onChange={(e) => setSelectedSigunguCode(e.target.value)}
                className="input w-full"
                disabled={!selectedSido || sigunguLoading}
              >
                <option value="">
                  {!selectedSido
                    ? "먼저 시/도를 선택하세요"
                    : sigunguLoading
                      ? "불러오는 중..."
                      : "시/군/구 또는 전체를 선택하세요"}
                </option>
                {sigunguList.map((s) => (
                  <option key={s.code ?? s.name} value={s.code ?? ""}>
                    {s.name === "전체"
                      ? `${selectedSido} 전체`
                      : s.translatedName ?? s.name}
                  </option>
                ))}
              </select>
            </div>

            <button
              onClick={handleAdd}
              disabled={
                !selectedSigunguCode ||
                addMutation.isPending ||
                regions.length >= MAX_REGIONS
              }
              className="w-full py-2 bg-blue-500 hover:bg-blue-600 disabled:bg-gray-300 text-white rounded-lg transition"
            >
              {addMutation.isPending ? "추가 중..." : "+ 관심지역 추가"}
            </button>
          </div>

          <div className="p-3 bg-blue-50 text-sm text-blue-700 rounded-lg">
            💡 시/도 전체 또는 시/군/구 단위로 등록할 수 있습니다. 시/군/구 목록에서 &quot;{selectedSido || "시/도"} 전체&quot;를 선택하면 해당 시/도 전체 알림을 받게 됩니다.
          </div>
        </section>
      </div>
    </main>
  );
}
