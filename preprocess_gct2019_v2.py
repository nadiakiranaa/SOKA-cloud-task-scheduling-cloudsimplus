import gzip, json, csv, os, math

INPUT = "instance_usage-000000000000.json.gz"
OUTDIR = "gct_cloudsim_v2"
TARGETS = [100, 500, 1000]

# We keep the GCT trace's relative arrival pattern, but scale the
# total arrival span to 1 hour so the CloudSim demo remains compact.
TARGET_ARRIVAL_SPAN_SEC = 3600.0

os.makedirs(OUTDIR, exist_ok=True)

instances = {}
total = valid = 0
min_start = None
max_start = None

print("Reading GCT 2019 line-by-line...")

with gzip.open(INPUT, "rt", encoding="utf-8") as f:
    for line in f:
        total += 1
        try:
            r = json.loads(line)
            start = int(r["start_time"])
            end = int(r["end_time"])
            cpu = float(r.get("average_usage", {}).get("cpus", 0.0))
            cid = str(r["collection_id"])
            iid = str(r["instance_index"])
        except (json.JSONDecodeError, KeyError, TypeError, ValueError):
            continue

        if end <= start or cpu <= 0:
            continue

        valid += 1
        min_start = start if min_start is None else min(min_start, start)
        max_start = start if max_start is None else max(max_start, start)

        key = (cid, iid)
        duration = (end - start) / 1_000_000.0

        if key not in instances:
            instances[key] = {
                "collection_id": cid,
                "instance_index": iid,
                "start": start,
                "cpu_time": 0.0,
                "duration": 0.0,
                "samples": 0,
            }

        x = instances[key]
        x["start"] = min(x["start"], start)
        x["cpu_time"] += cpu * duration
        x["duration"] += duration
        x["samples"] += 1

        if total % 1_000_000 == 0:
            print(f"Processed {total:,} lines | instances {len(instances):,}")

print("\nRead complete.")
print(f"Total lines      : {total:,}")
print(f"Valid records    : {valid:,}")
print(f"Unique instances : {len(instances):,}")

raw = []

for x in instances.values():
    if x["duration"] <= 0:
        continue

    avg_cpu = x["cpu_time"] / x["duration"]

    # Raw workload proxy derived from GCT CPU demand and observed duration.
    # This is NOT an original GCT MI field.
    workload = avg_cpu * x["duration"]

    if workload > 0:
        raw.append({
            **x,
            "avg_cpu": avg_cpu,
            "workload": workload,
        })

raw.sort(key=lambda x: (x["start"], x["collection_id"], x["instance_index"]))

# Percentile mapping keeps GCT workload variation instead of clipping
# almost everything at 100,000 MI.
workloads = sorted(x["workload"] for x in raw)

def percentile_value(values, p):
    if not values:
        return 0.0
    pos = (len(values) - 1) * p
    lo = int(math.floor(pos))
    hi = int(math.ceil(pos))
    if lo == hi:
        return values[lo]
    return values[lo] + (values[hi] - values[lo]) * (pos - lo)

p01 = percentile_value(workloads, 0.01)
p99 = percentile_value(workloads, 0.99)

if p99 <= p01:
    raise RuntimeError("GCT workload distribution is degenerate.")

def to_mi(w):
    # Winsorize only the extreme 1% tails, then linearly map to
    # 1,000..100,000 MI.
    w = max(p01, min(p99, w))
    ratio = (w - p01) / (p99 - p01)
    return int(round(1000 + ratio * 99000))

trace_span = max_start - min_start if max_start is not None else 0

converted = []

for x in raw:
    if trace_span > 0:
        arrival = ((x["start"] - min_start) / trace_span) * TARGET_ARRIVAL_SPAN_SEC
    else:
        arrival = 0.0

    converted.append({
        "collection_id": x["collection_id"],
        "instance_index": x["instance_index"],
        "length": to_mi(x["workload"]),
        "file_size": 500,
        "output_size": 500,
        "pes": 1,
        "arrival": round(arrival, 3),
        "avg_cpu": round(x["avg_cpu"], 6),
        "duration_sec": round(x["duration"], 3),
        "raw_workload": x["workload"],
        "samples": x["samples"],
    })

# Select tasks spread across the complete GCT timeline.
# This avoids taking only the first 100/500/1000 tasks.
def evenly_spaced(items, n):
    if len(items) < n:
        raise RuntimeError(f"Need {n} tasks but only {len(items)} available.")
    if n == 1:
        return [items[0]]
    idxs = [round(i * (len(items)-1) / (n-1)) for i in range(n)]
    return [items[i] for i in idxs]

for target in TARGETS:
    selected = evenly_spaced(converted, target)

    path = os.path.join(OUTDIR, f"gct_cloudsim_{target}.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["length", "file_size", "output_size", "pes", "arrival"])
        for r in selected:
            w.writerow([
                r["length"], r["file_size"], r["output_size"],
                r["pes"], r["arrival"]
            ])

    print(f"Created {path} -> {len(selected)} tasks")

# Audit the exact selected 1000-task dataset.
selected1000 = evenly_spaced(converted, 1000)
audit = os.path.join(OUTDIR, "gct_conversion_audit.csv")

with open(audit, "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(
        f,
        fieldnames=[
            "collection_id", "instance_index", "length",
            "file_size", "output_size", "pes", "arrival",
            "avg_cpu", "duration_sec", "raw_workload", "samples"
        ],
    )
    w.writeheader()
    w.writerows(selected1000)

print(f"Created {audit}")
print("\nPreprocessing V2 complete.")
print(f"Workload p01 : {p01:.6f}")
print(f"Workload p99 : {p99:.6f}")
print(f"Arrival span : {TARGET_ARRIVAL_SPAN_SEC:.1f} seconds")
