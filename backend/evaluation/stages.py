# 사용법: python stages.py trace.json pairs.txt  (trace.json = parse_log.py 결과, pairs.txt = stage_pairs.sql 결과)
# 사건 셋 쌍을 판정 기록에 붙여 검색·생성 단계를 센다. 나중 알림 b 의 기록을 본다.
import json, sys
from collections import Counter
t = json.load(open(sys.argv[1], encoding="utf-8"))
rows = []
unassigned = Counter()  # 두 알림 중 하나라도 이벤트가 없는 쌍 — eval_rates.sql 처럼 단계 집계에서 빼고 따로 센다
for line in open(sys.argv[2], encoding="utf-8"):
    a, b, label, ca, cb, ea, eb, ta, tb = line.rstrip("\n").split("|")
    a, b = int(a), int(b)
    if not ea or not eb:
        unassigned[label] += 1
        continue
    later_is_b = (cb, b) > (ca, a)
    early, late = (a, b) if later_is_b else (b, a)
    e_early = int(ea if later_is_b else eb) if (ea and eb) else None
    e_late = int(eb if later_is_b else ea) if (ea and eb) else None
    rec = t.get(str(late), {})
    path = rec.get("path")
    cands = rec.get("cands", {})
    in_cands = str(e_early) in cands if e_early is not None else None
    rows.append(dict(a=a, b=b, label=label, path=path, in_cands=in_cands,
                     dist=cands.get(str(e_early)), same=(e_early == e_late) if e_early else None,
                     method=rec.get("method"), result=rec.get("result"), type=ta if ta == tb else f"{ta}/{tb}",
                     ncands=len(cands)))
print("제외(이벤트 미배정):", dict(unassigned) or 0)
for lab in ("SAME", "DIFF"):
    rs = [r for r in rows if r["label"] == lab]
    print(f"== {lab} {len(rs)}쌍, 나중 알림의 경로:", Counter(r["path"] for r in rs))
    emb = [r for r in rs if r["path"] == "EMBEDDING"]
    print(f"   임베딩 경로 {len(emb)}쌍: 먼저 알림의 이벤트가 후보에 있음 {sum(1 for r in emb if r['in_cands'])}, 없음 {sum(1 for r in emb if not r['in_cands'])}")
    inc = [r for r in emb if r["in_cands"]]
    print(f"   후보에 있던 {len(inc)}쌍: 같은 이벤트로 묶임 {sum(1 for r in inc if r['same'])}, 안 묶임 {sum(1 for r in inc if not r['same'])}")
    key = [r for r in rs if r["path"] != "EMBEDDING"]
    print(f"   키 경로 {len(key)}쌍: 같은 이벤트 {sum(1 for r in key if r['same'])}, 다른 이벤트 {sum(1 for r in key if not r['same'])}")
