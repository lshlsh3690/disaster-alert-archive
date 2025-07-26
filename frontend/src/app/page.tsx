"use client";

import Link from "next/link";

export default function Home() {
  return (
    <main className="min-h-screen bg-gray-50 px-6 py-12">
      {/* Hero Section */}
      <section className="text-center py-16">
        <h1 className="text-4xl font-bold mb-4 text-gray-900">
          재난 안전문자 아카이브
        </h1>
        {/* <p className="text-lg text-gray-600 mb-6">
          전국 재난 문자 및 실종자 정보를 한눈에 확인하고{" "}
          <br className="hidden sm:block" /> 빠르게 대응하세요.
        </p> */}
        <p className="text-gray-600 text-center mt-2 mb-6">
          이 플랫폼은 과거 재난문자를 누구나 쉽게 확인하고,
          <br />
          그에 대한 의견을 나눌 수 있도록 만들었습니다.
        </p>
      </section>

      {/* Feature Cards */}
      <section className="mt-16 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <FeatureCard
          title="최신 재난 문자"
          desc="전국에서 수신된 재난 문자를 지역별로 확인"
        />
        <FeatureCard
          title="실종자 정보"
          desc="경찰청 공개 실종자 데이터를 통합 조회"
        />
        <FeatureCard
          title="통계 및 그래프"
          desc="일별/지역별 재난 알림 통계를 시각적으로 확인"
        />
        <FeatureCard
          title="커뮤니티 제보"
          desc="지역별 피해 제보와 실시간 댓글 공유"
        />
      </section>

      {/* CTA Section */}
      <section className="mt-20 text-center">
        <h2 className="text-2xl font-semibold mb-4">
          지금 대시보드를 확인해보세요
        </h2>
        <Link href="/dashboard">
          <button className="bg-blue-500 hover:bg-blue-600 text-white px-5 py-3 rounded-lg">
            재난 문자 확인하러 가기
          </button>
        </Link>
      </section>
    </main>
  );
}

// 🔹 컴포넌트 분리해도 좋지만 여기선 인라인 정의
function FeatureCard({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="bg-white p-6 rounded-xl shadow hover:shadow-md transition">
      <h3 className="text-xl font-bold text-gray-800 mb-2">{title}</h3>
      <p className="text-gray-600 text-sm">{desc}</p>
    </div>
  );
}
