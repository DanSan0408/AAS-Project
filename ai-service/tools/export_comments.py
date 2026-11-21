"""Exports comment text from the MySQL database to a CSV for people to label, with personal details masked.

Usage (from the ai-service folder):
    python -m tools.export_comments --host localhost --user root --database aas_db --out comments_to_label.csv
It uses the `mysql` command-line client and asks for the password. Two Malaysian readers should fill the `label` column
independently (ok, negative_constructive, abusive, sarcastic, inappropriate, unclear, injection_attempt) and agree on differences;
then run:  python -m tools.eval_comments --file comments_to_label.csv
Keep the file out of git and out of any chat or cloud tool: it is real student writing.
"""
import argparse
import csv
import re
import subprocess
import sys

_PATTERNS = [
    (re.compile(r"[\w.+-]+@[\w-]+(\.[\w-]+)+"), "[email]"),
    (re.compile(r"\b\d{6}-?\d{2}-?\d{4}\b"), "[ic number]"),
    (re.compile(r"(\+?60|0)1\d[- ]?\d{3,4}[- ]?\d{4}\b"), "[phone]"),
    (re.compile(r"https?://\S+"), "[link]"),
]


def redact(text: str) -> str:
    for pattern, replacement in _PATTERNS:
        text = pattern.sub(replacement, text)
    return text


def fetch(host: str, user: str, database: str) -> list:
    query = "SELECT id, REPLACE(REPLACE(commentText, CHAR(9), ' '), CHAR(10), ' ') FROM assessment_comments WHERE commentText IS NOT NULL AND TRIM(commentText) <> ''"
    out = subprocess.run(["mysql", "-h", host, "-u", user, "-p", "-D", database, "--batch", "--raw", "--skip-column-names", "-e", query],
                         capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(out.stderr.strip() or "The mysql client failed.")
    rows = []
    for line in out.stdout.splitlines():
        if "\t" in line:
            cid, text = line.split("\t", 1)
            rows.append((cid.strip(), text.strip()))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="aas_db")
    parser.add_argument("--out", default="comments_to_label.csv")
    args = parser.parse_args()
    seen, rows = set(), []
    for cid, text in fetch(args.host, args.user, args.database):
        clean = redact(text)
        if clean.lower() in seen:
            continue
        seen.add(clean.lower())
        rows.append({"id": f"r{cid}", "text": clean, "label": "", "language": ""})
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["id", "text", "label", "language"])
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} comments to {args.out}. Fill in the label column, keep the file private, then run tools.eval_comments.")


if __name__ == "__main__":
    main()
