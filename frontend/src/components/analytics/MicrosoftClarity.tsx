import Script from "next/script";

const CLARITY_PROJECT_ID = process.env.NEXT_PUBLIC_CLARITY_PROJECT_ID;

/**
 * Microsoft Clarity(히트맵·세션 재생) 태그. NEXT_PUBLIC_CLARITY_PROJECT_ID 가 비어 있으면 넣지 않는다.
 * 값이 인라인 스크립트에 그대로 들어가므로 영숫자 ID 가 아니면 태그를 넣지 않는다.
 * 클라이언트 라우팅 이동은 Clarity 가 history 변경을 감지해 자동으로 따라간다.
 */
export default function MicrosoftClarity() {
  if (!CLARITY_PROJECT_ID || !/^[a-z0-9]+$/i.test(CLARITY_PROJECT_ID)) return null;

  return (
    <Script id="ms-clarity" strategy="afterInteractive">
      {`(function(c,l,a,r,i,t,y){c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
})(window, document, "clarity", "script", "${CLARITY_PROJECT_ID}");`}
    </Script>
  );
}
