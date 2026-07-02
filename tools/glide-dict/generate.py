#!/usr/bin/env python3
"""
Generate a glide-typing dictionary (<lang>.json) from Leipzig Corpora word lists.

Glide typing (issue #127) matches a swipe path against a word list; each word carries a frequency in
[128,255] (log-scaled) used to rank candidates. This script builds one such file per language from the
Leipzig Corpora Collection (https://wortschatz.uni-leipzig.de), which — unlike OpenSubtitles-derived
lists — preserves correct casing and diacritics (so German nouns stay capitalised, e.g. "Baum").

Source & attribution: Leipzig Corpora Collection, Universität Leipzig. Cite per their terms; only use
corpora whose licence permits your distribution (most are CC BY; skip any NC-only ones).

Usage:
    python3 generate.py <lang> <corpus> [<corpus> ...] [--top N] [--out DIR]

    <lang>    output language code (e.g. de, en, fr) → writes <out>/<lang>.json
    <corpus>  Leipzig corpus name(s), e.g. deu_news_2023_1M  (downloaded from the corpora server;
              multiple are merged by summed frequency)
    --top N   keep the top N words by frequency (default 50000)
    --out DIR output directory (default: current dir)

Casing: a word can appear as several surface forms ("Und" at sentence start, "und" mid-sentence). For
each lowercase key we keep the single most frequent surface form as canonical and sum the frequencies of
all its forms — so common words collapse to lowercase while true proper-cased words (nouns) stay capital.
"""
import sys, os, re, io, json, math, gzip, tarfile, hashlib, argparse, urllib.request

BASE = "https://downloads.wortschatz-leipzig.de/corpora"
WORD_RE = re.compile(r"[^\W\d_]+(?:['’-][^\W\d_]+)*$", re.UNICODE)


def fetch_words(corpus: str) -> dict:
    """Download a Leipzig corpus tarball and return {surface_form: frequency} from its *-words.txt."""
    url = f"{BASE}/{corpus}.tar.gz"
    sys.stderr.write(f"  downloading {url}\n")
    with urllib.request.urlopen(url) as resp:
        raw = resp.read()
    forms = {}
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as tar:
        member = next((m for m in tar.getmembers() if m.name.endswith("-words.txt")), None)
        if member is None:
            raise SystemExit(f"no *-words.txt in {corpus}.tar.gz")
        for line in io.TextIOWrapper(tar.extractfile(member), encoding="utf-8"):
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            word, freq = parts[1], parts[2]
            if not freq.isdigit():
                continue
            forms[word] = forms.get(word, 0) + int(freq)
    return forms


def build(forms: dict, top: int) -> dict:
    # Group surface forms by lowercase key; canonical = most frequent form, freq = sum of all forms.
    groups = {}  # lower -> [best_form, best_form_freq, total_freq]
    for form, freq in forms.items():
        if len(form) < 1 or len(form) > 30 or not WORD_RE.match(form):
            continue
        key = form.lower()
        g = groups.get(key)
        if g is None:
            groups[key] = [form, freq, freq]
        else:
            g[2] += freq
            if freq > g[1]:
                g[0], g[1] = form, freq
    # Rank by combined frequency, keep top N.
    ranked = sorted(groups.values(), key=lambda g: g[2], reverse=True)[:top]
    counts = [g[2] for g in ranked]
    lo, hi = math.log(min(counts)), math.log(max(counts))
    span = (hi - lo) or 1.0
    out = {}
    for form, _best, total in ranked:
        v = 128 + round(127 * (math.log(total) - lo) / span)
        out[form] = max(128, min(255, v))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("lang")
    ap.add_argument("corpus", nargs="+")
    ap.add_argument("--top", type=int, default=50000)
    ap.add_argument("--out", default=".")
    args = ap.parse_args()

    merged = {}
    for corpus in args.corpus:
        for form, freq in fetch_words(corpus).items():
            merged[form] = merged.get(form, 0) + freq

    data = build(merged, args.top)
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{args.lang}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))

    blob = open(path, "rb").read()
    sha = hashlib.sha256(blob).hexdigest()
    caps = sum(1 for w in data if w != w.lower())
    sys.stderr.write(
        f"  wrote {path}: {len(data)} words, {caps} capitalised, {len(blob)} bytes\n"
        f"  sha256 {sha}\n"
        f"  sample {list(data.items())[:6]}\n"
    )
    # Catalog line for GlideDictionaryCatalog.kt
    print(f'GlideDict("{args.lang}", "<DisplayName>", "$REL/{args.lang}.json", {len(blob)}, "{sha}"),')


if __name__ == "__main__":
    main()
