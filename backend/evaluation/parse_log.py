# 재클러스터링 로그 → 알림별 판정 기록(경로, 후보 이벤트:거리, 결과 이벤트·방식)
import re, json, sys
log = sys.argv[1]
rec = {}
def r(aid): return rec.setdefault(aid, {"path": None, "cands": {}, "adj": False, "result": None, "event": None, "method": None})
pat_id = re.compile(r"clusterNewAlert(\([^)]*\))?: alertId=(\d+)")
for line in open(log, encoding="utf-8", errors="replace"):
    m = pat_id.search(line)
    if not m: continue
    tag, aid = m.group(1) or "", int(m.group(2))
    x = r(aid); rest = line[m.end():]
    if "비사건(ADVISORY" in rest: x["path"] = "SKIPPED"; x["result"] = "SKIPPED"; continue
    if tag == "(에피소드형)": x["path"] = x["path"] or "EPISODIC"
    elif tag in ("(지역 한정)", "(지역 한정·인접)"): x["path"] = x["path"] or "REGIONAL"
    elif tag == "(LLM폴백)": x["llm"] = True
    elif tag == "(인물)": x["path"] = x["path"] or "PERSON"
    elif tag in ("(전국통합)", "(전국 통합)"): x["path"] = x["path"] or "GLOBAL"
    if "지역 코드 없음" in rest: x["path"] = "NO_REGION"
    if re.search(r"(광역|전국)\(", rest): x["path"] = x["path"] or "BROADCAST"
    if "인접 시군구 후보" in rest: x["adj"] = True
    for e, d in re.findall(r"event=(\d+) dist=([\d.]+)", rest):
        x["path"] = x["path"] or "EMBEDDING"
        x["cands"].setdefault(e, float(d))
    if "후보 0건" in rest: x["path"] = x["path"] or "EMBEDDING"
    mm = re.search(r"→ event=(\d+) 머지 \(method=(\w+)", rest)
    if mm: x["result"] = "MERGED"; x["event"] = int(mm.group(1)); x["method"] = mm.group(2)
    mn = re.search(r"→ 신규 event=(\d+)", rest)
    if mn: x["result"] = "NEW"; x["event"] = int(mn.group(1))
for x in rec.values():
    if x["path"] is None and x["method"] in ("IDENTITY", "GLOBAL_TYPE", "REGIONAL_TYPE", "BROADCAST"):
        x["path"] = {"IDENTITY": "PERSON", "GLOBAL_TYPE": "GLOBAL", "REGIONAL_TYPE": "REGIONAL", "BROADCAST": "BROADCAST"}[x["method"]]
json.dump(rec, open(sys.argv[2], "w", encoding="utf-8"))
from collections import Counter
print(len(rec), Counter(v["path"] for v in rec.values()))
