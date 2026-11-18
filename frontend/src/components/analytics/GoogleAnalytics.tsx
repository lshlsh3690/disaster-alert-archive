import Script from "next/script";

const GA_ID = process.env.NEXT_PUBLIC_GA_ID;

/**
 * GA4 태그. NEXT_PUBLIC_GA_ID 가 비어 있으면 아무것도 넣지 않는다(로컬·미설정 배포).
 * App Router 의 클라이언트 라우팅 페이지뷰는 GA4 향상된 측정의 "브라우저 기록 이벤트"가
 * 자동으로 잡으므로 라우트 변경마다 page_view 를 직접 보내지 않는다.
 */
export default function GoogleAnalytics() {
  if (!GA_ID) return null;

  return (
    <>
      <Script src={`https://www.googletagmanager.com/gtag/js?id=${GA_ID}`} strategy="afterInteractive" />
      <Script id="ga4-init" strategy="afterInteractive">
        {`window.dataLayer = window.dataLayer || [];
function gtag(){dataLayer.push(arguments);}
gtag('js', new Date());
gtag('config', '${GA_ID}');`}
      </Script>
    </>
  );
}
