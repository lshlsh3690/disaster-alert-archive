"use client";

import Link from "next/link";
import { useI18n } from "@/hooks/useI18n";

export default function Home() {
  const t = useI18n();

  return (
    <main className="min-h-screen bg-gray-50 px-6 py-12">
      <section className="text-center py-16">
        <h1 className="text-4xl font-bold mb-4 text-gray-900">{t.home.title}</h1>
        <p className="text-gray-600 text-center mt-2 mb-6">
          {t.home.subtitle.split("\n").map((line, i) => (
            <span key={i}>{line}{i === 0 && <br />}</span>
          ))}
        </p>
      </section>

      <section className="mt-16 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {Object.values(t.home.features).map(({ title, desc }) => (
          <FeatureCard key={title} title={title} desc={desc} />
        ))}
      </section>

      <section className="mt-20 text-center">
        <h2 className="text-2xl font-semibold mb-4">{t.home.ctaTitle}</h2>
        <Link href="/dashboard">
          <button className="bg-blue-500 hover:bg-blue-600 text-white px-5 py-3 rounded-lg">
            {t.home.ctaButton}
          </button>
        </Link>
      </section>
    </main>
  );
}

function FeatureCard({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="bg-white p-6 rounded-xl shadow hover:shadow-md transition">
      <h3 className="text-xl font-bold text-gray-800 mb-2">{title}</h3>
      <p className="text-gray-600 text-sm">{desc}</p>
    </div>
  );
}