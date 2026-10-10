import gzip
import json
import csv
import os

INPUT = "instance_usage-000000000000.json.gz"
OUTPUT_DIR = "gct_cloudsim"

TARGETS = [100, 500, 1000]
REFERENCE_MIPS = 1000.0

os.makedirs(OUTPUT_DIR, exist_ok=True)

instances = {}
total_lines = 0
valid_lines = 0

print("Membaca GCT 2019...")
print("Jangan tutup PowerShell.\n")

with gzip.open(INPUT, "rt", encoding="utf-8") as f:
    for line in f:
        total_lines += 1

        try:
            r = json.loads(line)
        except json.JSONDecodeError:
            continue

        try:
            start = int(r["start_time"])
            end = int(r["end_time"])
            avg_cpu = float(
                r.get("average_usage", {}).get("cpus", 0.0)
            )
        except (KeyError, TypeError, ValueError):
            continue

        if end <= start or avg_cpu <= 0:
            continue

        collection_id = r.get("collection_id")
        instance_index = r.get("instance_index")

        if collection_id is None or instance_index is None:
            continue

        valid_lines += 1

        key = (str(collection_id), str(instance_index))

        duration_sec = (end - start) / 1_000_000.0

        if key not in instances:
            instances[key] = {
                "collection_id": str(collection_id),
                "instance_index": str(instance_index),
                "start_time": start,
                "end_time": end,
                "cpu_time": 0.0,
                "duration": 0.0,
                "samples": 0
            }

        x = instances[key]

        x["start_time"] = min(x["start_time"], start)
        x["end_time"] = max(x["end_time"], end)

        x["cpu_time"] += avg_cpu * duration_sec
        x["duration"] += duration_sec
        x["samples"] += 1

        if total_lines % 1_000_000 == 0:
            print(
                f"Processed {total_lines:,} lines | "
                f"instances: {len(instances):,}"
            )

print("\nPembacaan selesai.")
print(f"Total lines       : {total_lines:,}")
print(f"Valid records     : {valid_lines:,}")
print(f"Unique instances  : {len(instances):,}")

# -------------------------------------------------
# Convert GCT records → CloudSim tasks
# -------------------------------------------------

rows = []

for x in instances.values():

    if x["duration"] <= 0:
        continue

    avg_cpu = x["cpu_time"] / x["duration"]

    # CPU fraction × reference MIPS × duration
    length_mi = avg_cpu * REFERENCE_MIPS * x["duration"]

    # Batas praktis untuk dataset CloudSim
    length_mi = max(
        1000,
        min(100000, int(round(length_mi)))
    )

    # Relative arrival time
    arrival = (
        x["start_time"] - 1_839_000_000_000
    ) / 1_000_000.0

    arrival = max(0.0, arrival)

    rows.append({
        "collection_id": x["collection_id"],
        "instance_index": x["instance_index"],
        "length": length_mi,
        "file_size": 500,
        "output_size": 500,
        "pes": 1,
        "arrival": round(arrival, 3),
        "avg_cpu": round(avg_cpu, 6),
        "duration_sec": round(x["duration"], 3),
        "samples": x["samples"]
    })

rows.sort(
    key=lambda r: (
        r["arrival"],
        r["collection_id"],
        r["instance_index"]
    )
)

print(f"Usable CloudSim tasks: {len(rows):,}")

if len(rows) < 1000:
    raise RuntimeError(
        "Task yang berhasil dibuat kurang dari 1000."
    )

# -------------------------------------------------
# Create 100 / 500 / 1000 datasets
# -------------------------------------------------

for target in TARGETS:

    selected = rows[:target]

    path = os.path.join(
        OUTPUT_DIR,
        f"gct_cloudsim_{target}.csv"
    )

    with open(
        path,
        "w",
        newline="",
        encoding="utf-8"
    ) as out:

        writer = csv.writer(out)

        writer.writerow([
            "length",
            "file_size",
            "output_size",
            "pes",
            "arrival"
        ])

        for r in selected:
            writer.writerow([
                r["length"],
                r["file_size"],
                r["output_size"],
                r["pes"],
                r["arrival"]
            ])

    print(
        f"Created {path} -> {len(selected)} tasks"
    )

# -------------------------------------------------
# Audit file
# -------------------------------------------------

audit_path = os.path.join(
    OUTPUT_DIR,
    "gct_conversion_audit.csv"
)

with open(
    audit_path,
    "w",
    newline="",
    encoding="utf-8"
) as out:

    writer = csv.DictWriter(
        out,
        fieldnames=[
            "collection_id",
            "instance_index",
            "length",
            "file_size",
            "output_size",
            "pes",
            "arrival",
            "avg_cpu",
            "duration_sec",
            "samples"
        ]
    )

    writer.writeheader()

    for r in rows[:1000]:
        writer.writerow(r)

print(f"Created {audit_path}")

print("\n================================")
print("PREPROCESSING SELESAI")
print("================================")