"""disaster_alert_eval_v5(운영 분류값을 가져온 사본)에서 새 평가 쌍 301개를 뽑는다.
틀은 2026-11 루브릭 그대로: 둘 다 INCIDENT, 임베딩 있음, 같은 유형, 168h 이내, 같은 시군구 또는 region_adjacency 이웃,
기존 사건 셋·홀드아웃·삭제된 개발용 150쌍의 알림 제외, 한 알림은 한 번만, 작은 층부터, 시드 20261109.

일회성 기록용이다 — 그대로 다시 돌리는 도구가 아니다. 작업 디렉터리 S 는 작성 당시 세션 임시 폴더이고,
제외 입력인 예전 셋(cluster-eval-2026-10-incident·-test 등)은 2026-11-09 에 지워 git 이력에만 있다."""
import csv, io, subprocess, random, bisect, sys
from datetime import datetime, timedelta
from collections import defaultdict, Counter

S = "C:/Users/SSAFY/AppData/Local/Temp/claude/88444fe597a5b714/0ef86f27-e054-4b15-b477-dac28baa8bef/scratchpad"
D = "backend/evaluation/datasets/"
DB = sys.argv[1] if len(sys.argv) > 1 else "disaster_alert_eval_v5"

MAIN = {'fire': '화재', 'area': '호우', 'wild': '산불'}
MAIN_QUOTA = {('fire', 'same'): 56, ('fire', 'adj'): 22, ('area', 'same'): 44, ('area', 'adj'): 19,
              ('wild', 'same'): 44, ('wild', 'adj'): 19}
SPOT_QUOTA = {'기타': 47, '교통통제': 16, '교통사고': 13, '정전': 9, '수도': 9, '교통': 3}
SPOT_ADJ = 3
W = timedelta(hours=168)


def q(sql):
    out = subprocess.run(['docker', 'exec', '-i', 'postgres', 'psql', '-U', 'postgres', '-d', DB, '-X', '-q'],
                         input=f"COPY ({sql}) TO STDOUT WITH CSV HEADER;", capture_output=True, text=True, encoding='utf-8')
    assert out.returncode == 0, out.stderr
    return list(csv.DictReader(io.StringIO(out.stdout)))


excl = set()
for f in [D + 'cluster-eval-2026-10-incident.csv', D + 'cluster-eval-2026-10-test.csv', S + '/dev150.csv']:
    for r in csv.DictReader(io.open(f, encoding='utf-8')):
        excl |= {int(r['alert_a_id']), int(r['alert_b_id'])}

types = list(MAIN.values()) + list(SPOT_QUOTA)
tsql = ",".join("'%s'" % t for t in types)
base = f"a.alert_kind='INCIDENT' AND a.embedding IS NOT NULL AND a.disaster_type IN ({tsql})"
alerts = q(f"SELECT a.disaster_alert_id AS id, a.created_at, a.disaster_type AS t FROM disaster_alert a WHERE {base}")
regs = q(f"SELECT r.disaster_alert_id AS id, left(r.legal_district_code,5) AS sgg FROM disaster_alert_region r "
         f"JOIN disaster_alert a ON a.disaster_alert_id=r.disaster_alert_id WHERE {base}")
adj = {(r['region_code'], r['neighbor_code']) for r in q("SELECT region_code, neighbor_code FROM region_adjacency")}
sgg = defaultdict(set)
for r in regs:
    if r['sgg'][2:5] != '000':
        sgg[int(r['id'])].add(r['sgg'])

bytype = defaultdict(list)
for r in alerts:
    i = int(r['id'])
    if i in excl or not sgg[i]:
        continue
    bytype[r['t']].append((datetime.fromisoformat(r['created_at'][:19]), i))

cands = defaultdict(list)  # (type, rel) -> [(a, b)]
for t, xs in bytype.items():
    xs.sort()
    times = [x[0] for x in xs]
    for k, (ta, ia) in enumerate(xs):
        hi = bisect.bisect_right(times, ta + W)
        for tb, ib in xs[k + 1:hi]:
            sa, sb = sgg[ia], sgg[ib]
            if sa & sb:
                rel = 'same'
            elif any((x, y) in adj for x in sa for y in sb):
                rel = 'adj'
            else:
                continue
            cands[(t, rel)].append((ia, ib))
print("cands", {f"{t}/{r}": len(v) for (t, r), v in sorted(cands.items())})

rng = random.Random(20261109)
used, picked = set(), []


def take(pool, n, t, grp, rel):
    pool = pool[:]
    rng.shuffle(pool)
    got = 0
    for ia, ib in pool:
        if got == n:
            break
        if ia in used or ib in used:
            continue
        used.update((ia, ib))
        picked.append((ia, ib, t, grp, rel))
        got += 1
    return got


for (grp, rel), n in sorted(MAIN_QUOTA.items(), key=lambda x: x[1]):
    got = take(cands[(MAIN[grp], rel)], n, MAIN[grp], grp, rel)
    print(f"  {grp}/{rel}: {got}/{n}")

adj_left, short = SPOT_ADJ, 0
for t in sorted(SPOT_QUOTA, key=lambda x: SPOT_QUOTA[x]):
    n, short = SPOT_QUOTA[t] + short, 0
    got = 0
    if adj_left:
        g = take(cands[(t, 'adj')], min(adj_left, n), t, 'spot', 'adj')
        adj_left -= g
        got += g
    got += take(cands[(t, 'same')], n - got, t, 'spot', 'same')
    short = n - got
    print(f"  spot {t}: {got}/{n}")

io.open(S + '/picked_v5.csv', 'w', encoding='utf-8').write(
    "a,b,type,grp,rel\n" + "".join(f"{a},{b},{t},{g},{r}\n" for a, b, t, g, r in picked))
print("total", len(picked), dict(Counter(p[3] + '/' + p[4] for p in picked)))
