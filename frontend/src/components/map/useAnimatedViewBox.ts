"use client";

import { useEffect, useRef, useState } from "react";
import type { ViewBox } from "@/lib/mapViewBox";

const easeOutCubic = (p: number) => 1 - Math.pow(1 - p, 3);

/**
 * target viewBox 로 부드럽게 이동한 현재 값을 돌려준다(시군구를 눌러 확대/복귀할 때 쓴다).
 * 이동 도중 target 이 바뀌면 그 시점의 값에서 새 target 으로 다시 출발한다.
 *
 * 효과의 의존성은 target 의 숫자 값만 쓴다 — 객체 자체를 넣으면 호출 쪽이 렌더마다 새 객체를 만들 때
 * 애니메이션 도중 효과가 다시 시작돼 끝나지 않는다.
 */
export function useAnimatedViewBox(target: ViewBox, durationMs = 320): ViewBox {
  const [current, setCurrent] = useState<ViewBox>(target);
  const currentRef = useRef<ViewBox>(target);
  const targetRef = useRef<ViewBox>(target);
  targetRef.current = target;

  const { x, y, w, h } = target;

  useEffect(() => {
    const to = targetRef.current;
    const from = currentRef.current;
    if (from.x === to.x && from.y === to.y && from.w === to.w && from.h === to.h) return;

    // 모션 줄이기를 켠 사용자는 애니메이션 없이 바로 목표로 이동한다(다음 프레임에 한 번 갱신).
    const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const duration = reduceMotion ? 0 : durationMs;

    let raf = 0;
    const t0 = performance.now();
    const step = (now: number) => {
      // rAF 가 넘겨 주는 프레임 시각이 t0(performance.now())보다 조금 앞설 수 있어 진행률이 음수가 되면
      // viewBox 의 폭/높이가 음수가 되므로 하한을 0 으로 고정한다.
      const p = duration === 0 ? 1 : Math.min(1, Math.max(0, (now - t0) / duration));
      const e = easeOutCubic(p);
      const next: ViewBox = {
        x: from.x + (to.x - from.x) * e,
        y: from.y + (to.y - from.y) * e,
        w: from.w + (to.w - from.w) * e,
        h: from.h + (to.h - from.h) * e,
      };
      currentRef.current = next;
      setCurrent(next);
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [x, y, w, h, durationMs]);

  return current;
}
