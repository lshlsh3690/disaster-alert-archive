"""LLM 판사와 사람 라벨의 일치율을 잰다 — 2026-11-11 측정 2의 2차(research.md 5절) 그대로.

cluster-eval-2026-11.json 에서 UNSURE 를 뺀 쌍마다 gpt-4o(온도 0)에 두 문자를 주고 SAME/DIFF/UNSURE 를 받아
사람 라벨과 교차표·일치율·비용을 출력하고, 쌍별 판정을 OUT(JSON)에 쓴다.
2026-11-11 결과: 227쌍, 같은 사건 145 중 127(87.6%), 다른 사건 82 중 78(95.1%), $0.319.

- 유료 호출이다(227회, 약 $0.32). 사용자가 명시적으로 승인했을 때만 돌린다.
- 프롬프트(SYS)는 2026-11-11 기준 원칙 1~3 + 경계 사례 A~C 다. 그 뒤 정한 D~I 는 들어 있지 않다.
  기준을 고치면 SYS 도 고치고 다시 잰다 — 그 전 수치와는 같은 범위 비교가 아니다.
- 일회성 측정 도구다. 운영 일일 채점의 구현이 아니며, 그것을 어디서 돌릴지는 006 plan 에서 정한다.

실행(저장소 루트에서): OPENAI_API_KEY=... python backend/evaluation/scripts/judge_agreement.py out.json
"""
import io, json, os, re, sys, time, urllib.error, urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

sys.stdout.reconfigure(encoding="utf-8")
OUT = sys.argv[1]
KEY = os.environ.get("OPENAI_API_KEY", "")
assert KEY, "no key"
MODEL = "gpt-4o"

L = [x for x in json.load(io.open("backend/evaluation/datasets/cluster-eval-2026-11.json", encoding="utf-8"))
     if x["label"] != "UNSURE"]

SYS = """너는 한국 재난문자 두 건이 같은 사건을 다루는지 판정한다. 아래 기준만 따른다.

[기본 기준]
1. 같은 지역에서 하나의 사고·사건이 이어지는 경우(발생 → 통제·대피 → 복구·해제 등 후속 문자) → SAME
2. 인접 지역이고 같은 개체·현상이 이어진 경우(사고 확산, 동물 이동 등, 문자에 연속성 단서가 있어야 함) → SAME
3. 별개의 사고·사건(각각 다른 장소·번지의 화재, 서로 다른 지점의 시설 파손, 같은 날 따로 발생한 사고 등) → DIFF

[경계 사례 — 위 기준보다 우선]
A. 호우: 같은 호우 동안 같은 시군구 안의 서로 다른 곳에 난 침수·범람·도로 통제·해제·대피 → SAME. 유형이 교통통제여도 원인이 같은 호우면 SAME. 이웃 시군구는 같은 호우라도 DIFF.
B. 산불 확산: 큰 산불이 번지며 차례로 나온 대피명령·통제 → 문자에 이어짐 표시가 없어도 SAME.
C. 산불 별개 발화: 같은 군·같은 날이어도 서로 다른 곳에서 각각 "발생"을 알리면 → DIFF.

정말 판단할 수 없을 때만 UNSURE. SAME, DIFF, UNSURE 중 한 단어만 출력한다."""


def side(s):
    return f"시각: {s['at']}\n유형: {s['type']}\n지역: {s['region']}\n본문: {s['message']}"


def user(x):
    return f"[문자 A]\n{side(x['a'])}\n\n[문자 B]\n{side(x['b'])}\n\n두 문자의 관계: {x['relation']}, 간격 {x['gap']}"


def call(x):
    body = json.dumps({"model": MODEL, "temperature": 0, "max_tokens": 5,
                       "messages": [{"role": "system", "content": SYS}, {"role": "user", "content": user(x)}]}).encode("utf-8")
    err = "?"
    for i in range(4):
        try:
            r = urllib.request.Request("https://api.openai.com/v1/chat/completions", data=body,
                                       headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"})
            d = json.load(urllib.request.urlopen(r, timeout=60))
            a = d["choices"][0]["message"]["content"].strip().upper()
            m = re.search(r"SAME|DIFF|UNSURE", a)
            return x, (m.group(0) if m else "BAD:" + a), d.get("usage", {}), d.get("model")
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                return x, f"HTTP{e.code}", {}, None
            err = f"HTTP{e.code}"
            time.sleep(2 * (i + 1))
        except Exception as e:
            err = type(e).__name__
            time.sleep(2 * (i + 1))
    return x, "ERR:" + err, {}, None


with ThreadPoolExecutor(6) as ex:
    R = list(ex.map(call, L))

rows = [{"q": x["q"], "type": x["a"]["type"], "relation": x["relation"], "human": x["label"], "judge": j,
         "a": x["a"]["message"], "b": x["b"]["message"]} for x, j, u, m in R]
io.open(OUT, "w", encoding="utf-8").write(json.dumps(rows, ensure_ascii=False, indent=1))

print("calls", len(R), "model", dict(Counter(m for _, _, _, m in R)))
pt = sum(u.get("prompt_tokens", 0) for _, _, u, _ in R)
ct = sum(u.get("completion_tokens", 0) for _, _, u, _ in R)
print("tokens in", pt, "out", ct, "cost_usd_at_2.5_10", round(pt * 2.5e-6 + ct * 10e-6, 3))
c = Counter((r["human"], r["judge"]) for r in rows)
print("matrix", sorted(c.items()))
for h in ("SAME", "DIFF"):
    n = sum(1 for r in rows if r["human"] == h)
    print(h, "agree", c[(h, h)], "/", n, round(100 * c[(h, h)] / n, 1))
print("by_type_all", sorted(Counter(r["type"] for r in rows).items()))
print("disagree_by_type", sorted(Counter((r["type"], r["human"], r["judge"]) for r in rows if r["human"] != r["judge"]).items()))
