"""复算科技发明制作类研究报告图 9、表 5 和两卷重题数；仅依赖 Python 标准库。"""
import csv
import json
import random
import sys
from math import ceil
from pathlib import Path
from statistics import mean


def percentiles(values):
    ordered = sorted(values)
    return [ordered[ceil(len(ordered) * p) - 1] for p in (0.05, 0.95)]


def main():
    manifest = Path(__file__).resolve().parents[1] / "datasets/formal-exams/manifest.json"
    counts = json.loads(manifest.read_text(encoding="utf-8"))["counts"]["MOCK"]
    sizes = [counts[k] for k in ("SINGLE", "MULTIPLE", "INDEFINITE")]
    if sizes != [153, 145, 45]:
        sys.exit("模拟客观题池已变化；请使用报告对应的 1.3.0 版题库清单复算。")
    quotas = (50, 35, 15)
    trials = 5_000
    strata = [range(sum(sizes[:i]), sum(sizes[: i + 1])) for i in range(len(sizes))]

    def paper(rng):
        return set().union(*(rng.sample(pool, k) for pool, k in zip(strata, quotas)))

    rng = random.Random(20261109)
    overlap = [len(paper(rng) & paper(rng)) for _ in range(trials)]
    writer = csv.writer(sys.stdout)
    writer.writerow(["指标", "卷数", "序列数", "理论期望", "抽样均值", "经验P05", "经验P95"])
    writer.writerow(["两卷重复题数", 2, trials,
                     f"{sum(q*q/n for q, n in zip(quotas, sizes)):.6f}",
                     f"{mean(overlap):.6f}", *percentiles(overlap)])

    rng = random.Random(20261110)
    observations = [[] for _ in range(10)]
    for _ in range(trials):
        seen = set()
        for observed in observations:
            seen.update(paper(rng))
            observed.append(len(seen))
    for k, observed in enumerate(observations, 1):
        expected = sum(n * (1 - (1 - q/n)**k) for q, n in zip(quotas, sizes))
        writer.writerow(["累计不同题数", k, trials, f"{expected:.6f}",
                         f"{mean(observed):.6f}", *percentiles(observed)])


if __name__ == "__main__":
    main()
