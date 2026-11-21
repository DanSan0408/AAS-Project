"""Measures how well the comment check agrees with human labels.

Usage (from the ai-service folder, with the virtualenv):
    python -m tools.eval_comments --rules-only                 # the word list alone, no key needed
    python -m tools.eval_comments                              # the full check with the model (needs GEMINI_API_KEY)
    python -m tools.eval_comments --file my_labelled.csv       # your own labelled comments (.csv with id,text,label[,language] or .jsonl)

The number that matters most is the false-flag rate on comments labelled ok or negative_constructive: honest criticism that the
tool would wrongly send to a moderator as a problem. A tool that flags fair criticism teaches students not to give it.
"""
import argparse
import csv
import json
import os
import sys
from collections import Counter, defaultdict
from typing import Dict, List

DEFAULT = os.path.join(os.path.dirname(__file__), "..", "tests", "data", "comment_eval.jsonl")
PROBLEM = {"abusive", "sarcastic", "inappropriate", "injection_attempt"}
FAIR = {"ok", "negative_constructive"}


def load(path: str) -> List[Dict[str, str]]:
    if path.endswith(".csv"):
        with open(path, newline="", encoding="utf-8") as f:
            return [r for r in csv.DictReader(f) if (r.get("label") or "").strip()]
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def score(rows: List[Dict[str, str]], predicted: Dict[str, Dict]) -> Dict:
    """rows: labelled; predicted: id -> {label, flagged}. Returns per-label precision/recall and the false-flag rate."""
    labels = sorted({r["label"] for r in rows} | {p["label"] for p in predicted.values()})
    tp, fp, fn = Counter(), Counter(), Counter()
    for r in rows:
        p = predicted[r["id"]]["label"]
        if p == r["label"]:
            tp[p] += 1
        else:
            fp[p] += 1
            fn[r["label"]] += 1
    per_label = {}
    for label in labels:
        precision = tp[label] / (tp[label] + fp[label]) if tp[label] + fp[label] else None
        recall = tp[label] / (tp[label] + fn[label]) if tp[label] + fn[label] else None
        per_label[label] = {"precision": precision, "recall": recall, "support": tp[label] + fn[label]}
    fair = [r for r in rows if r["label"] in FAIR]
    wrongly_flagged = [r for r in fair if predicted[r["id"]]["label"] in PROBLEM]
    sent_for_review = [r for r in fair if predicted[r["id"]].get("flagged")]
    problems = [r for r in rows if r["label"] in PROBLEM]
    missed = [r for r in problems if not predicted[r["id"]].get("flagged")]
    return {
        "per_label": per_label,
        "false_flag_rate": len(wrongly_flagged) / len(fair) if fair else None,
        "fair_sent_to_review_rate": len(sent_for_review) / len(fair) if fair else None,
        "problem_missed_rate": len(missed) / len(problems) if problems else None,
        "wrongly_flagged": [r["text"] for r in wrongly_flagged],
        "missed": [r["text"] for r in missed],
    }


def run(rows: List[Dict[str, str]], rules_only: bool) -> Dict[str, Dict]:
    from app.analysis.comment_rules import check
    predicted: Dict[str, Dict] = {}
    if rules_only:
        for r in rows:
            v = check(r["text"])
            predicted[r["id"]] = {"label": v.label if v else "not_checked", "flagged": v is not None}
        return predicted
    from app.graphs.comment_check import check_comments
    from app.schemas.comment_schemas import CheckCommentsRequest
    for i in range(0, len(rows), 20):
        batch = rows[i:i + 20]
        response = check_comments(CheckCommentsRequest(comments=[{"id": r["id"], "text": r["text"]} for r in batch]))
        if response.aiStatus == "UNAVAILABLE":
            sys.exit("The AI was not available; check GEMINI_API_KEY and the network. " + " ".join(response.aiNotes))
        for v in response.results:
            predicted[v.id] = {"label": v.label, "flagged": v.flagged}
    return predicted


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--file", default=DEFAULT)
    parser.add_argument("--rules-only", action="store_true")
    args = parser.parse_args()
    rows = load(args.file)
    result = score(rows, run(rows, args.rules_only))
    print(f"{len(rows)} labelled comments from {os.path.basename(args.file)} ({'word list only' if args.rules_only else 'full check'})\n")
    print(f"{'label':24}{'precision':>10}{'recall':>9}{'n':>5}")
    for label, m in result["per_label"].items():
        fmt = lambda v: "-" if v is None else f"{v:.2f}"
        print(f"{label:24}{fmt(m['precision']):>10}{fmt(m['recall']):>9}{m['support']:>5}")
    pct = lambda v: "n/a" if v is None else f"{v * 100:.0f}%"
    print(f"\nFair comments wrongly called a problem:  {pct(result['false_flag_rate'])}   <- the number to keep near zero")
    print(f"Fair comments sent to a moderator:       {pct(result['fair_sent_to_review_rate'])}")
    print(f"Problem comments nobody would be asked to look at: {pct(result['problem_missed_rate'])}")
    for title, items in (("Wrongly called a problem", result["wrongly_flagged"]), ("Missed", result["missed"])):
        if items:
            print(f"\n{title}:")
            for t in items[:15]:
                print("  -", t[:110])


if __name__ == "__main__":
    main()
