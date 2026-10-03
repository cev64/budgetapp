#!/usr/bin/env python3
"""Reference implementation of docs/DOMAIN_RULES.md. Used to (1) prove the rules reproduce
the original spreadsheet and (2) generate docs/fixtures/expected.json for the app unit tests.

Usage: python3 tools/reference_calc.py backup.json  -> prints computed results as JSON
"""
import json
import sys


def compute(data):
    cats = {c["id"]: c for c in data["categories"] if not c.get("deleted")}
    months = sorted((m["year"], m["month"]) for m in data["months"] if not m.get("deleted"))
    closed = {(m["year"], m["month"]): m["closed"] for m in data["months"]}
    bud = {(b["year"], b["month"], b["category_id"]): b for b in data["budgets"] if not b.get("deleted")}
    ledger = {}
    for t in data["transactions"]:
        if t.get("deleted"):
            continue
        k = (t["year"], t["month"], t["category_id"])
        ledger[k] = ledger.get(k, 0) + t["amount"]

    def expected(y, m, c):
        b = bud.get((y, m, c))
        return (b or {}).get("expected") or 0

    def actual(y, m, c):
        b = bud.get((y, m, c))
        if b and b.get("actual") is not None:
            return b["actual"]
        if cats[c]["tracking"] == "ledger":
            return ledger.get((y, m, c), 0)
        return None

    def projected(y, m, c):
        a = actual(y, m, c) or 0
        return a if closed[(y, m)] and a != 0 else expected(y, m, c)

    def totals(val):  # val: cat_id -> number
        t = {"income": 0, "expenses": 0, "saved": 0, "contributions": 0}
        for cid, c in cats.items():
            v = val(cid) or 0
            if c["kind"] == "income":
                t["income"] += v
            elif c["kind"] == "expense":
                t["expenses"] += v
            else:
                t["saved"] += v * c["match_multiplier"]
                t["contributions"] += v
        t["leftover"] = t["income"] - t["expenses"] - t["contributions"]
        return {k: round(v, 4) for k, v in t.items()}

    out = {"months": {}, "years": {}}
    for y, m in months:
        out["months"][f"{y}-{m:02d}"] = {
            "closed": closed[(y, m)],
            "categories": {cats[c]["name"]: {"expected": round(expected(y, m, c), 4),
                                             "actual": None if actual(y, m, c) is None else round(actual(y, m, c), 4)}
                           for c in cats},
            "expected": totals(lambda c: expected(y, m, c)),
            "actual": totals(lambda c: actual(y, m, c)),
        }
    st = data["settings"]
    for y in sorted({y for y, _ in months}):
        ms = [(yy, mm) for yy, mm in months if yy == y]
        n = len(ms)
        res = {}
        for col, fn in (("expected", expected), ("actual", projected)):
            per = {c: sum(fn(yy, mm, c) or 0 for yy, mm in ms) for c in cats}
            t = totals(lambda c: per[c])
            ann = (t["saved"] + t["leftover"]) * 12 / n
            t.update(annualized_savings=round(ann, 4), pct_net=round(ann / st["net_income"], 6),
                     pct_gross=round(ann / st["gross_income"], 6))
            t["categories"] = {cats[c]["name"]: round(per[c], 4) for c in cats}
            res[col] = t
        out["years"][str(y)] = res

    sums = {}
    for (y, m, c), b in bud.items():
        sums[c] = sums.get(c, 0) + (b.get("actual") or 0)
    accts = {}
    for a in data["accounts"]:
        if a.get("deleted") or a.get("archived"):
            continue
        if a.get("linked_category_id"):
            lc = a["linked_category_id"]
            bal = a["base_amount"] + cats[lc]["match_multiplier"] * sums.get(lc, 0)
        else:
            bal = a["balance"]
        accts[a["name"]] = round(bal, 4)
    recon = sum(e["amount"] for e in data["ledger_entries"] if not e.get("deleted") and not e["settled"])
    liquid = sum(accts[a["name"]] for a in data["accounts"] if a["name"] in accts and a["liquid"])
    out["net_worth"] = {"accounts": accts, "net_reconciliations": round(recon, 4),
                        "net_worth": round(sum(accts.values()) + recon, 4), "super_liquid": round(liquid, 4)}

    return out


if __name__ == "__main__":
    print(json.dumps(compute(json.load(open(sys.argv[1]))), indent=1))
