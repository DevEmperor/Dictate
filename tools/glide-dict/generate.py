#!/usr/bin/env python3
"""
Generate a glide-typing dictionary (<lang>.json) with correct casing/diacritics.

Glide typing (issue #127) matches a swipe path against a word list; each word carries a frequency in
[128,255] (log-scaled) used to rank candidates. The classifier matches case-insensitively but commits the
stored form, so the dictionary must carry the correct case (German nouns capitalised, e.g. "Baum").

Two reachable sources are combined:
  * Frequency — OPUS OpenSubtitles frequency lists (https://opus.nlpl.eu, hosted on CSC object storage).
    Case-insensitive there (fully lowercased), which is fine for *ranking*.
  * Casing — the Hunspell dictionary for the language (github.com/wooorm/dictionaries), consulted through
    the local `hunspell` binary as an oracle: a lowercase word that is only valid when capitalised (a noun)
    is capitalised; words valid lowercase (function words, verbs) stay lowercase.

Requires the `hunspell` binary on PATH (sudo pacman -S hunspell / apt install hunspell). Without a Hunspell
dictionary for the language the result is left lowercase (a warning is printed).

Usage:
    python3 generate.py <lang> [--opus LANG] [--dict NAME] [--top N] [--out DIR]

    <lang>      output language code → writes <out>/<lang>.json (also the OPUS/dict default)
    --opus L    OPUS language code (default: <lang>), e.g. de, en, fr, pt
    --dict N    wooorm dictionary dir (default: <lang>), e.g. de, en-US, pt-BR
    --top N     keep the top N words by frequency (default 50000)
    --out DIR   output directory (default: current dir)

Licensing: OPUS OpenSubtitles data is freely redistributable; Hunspell dictionaries are used here only as a
build-time casing oracle (their word lists are not redistributed — only OPUS-derived frequencies with
restored case). Verify the per-language Hunspell licence (wooorm/dictionaries lists it) if concerned.
"""
import sys, os, re, io, json, math, gzip, hashlib, argparse, subprocess, tempfile, urllib.request

OPUS = "https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2018/freq"
WOOORM = "https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries"
WORD_RE = re.compile(r"[^\W\d_]+(?:['’-][^\W\d_]+)*$", re.UNICODE)


def get(url: str) -> bytes:
    sys.stderr.write(f"  GET {url}\n")
    with urllib.request.urlopen(url) as r:
        return r.read()


def load_frequencies(opus_lang: str, top: int) -> list:
    """Return [(word_lower, freq), ...] top-N, filtered to real words, frequency-desc."""
    raw = gzip.decompress(get(f"{OPUS}/{opus_lang}.freq.gz")).decode("utf-8", "replace")
    out = []
    for line in raw.splitlines():
        # Counts are right-aligned with leading spaces, so split on any whitespace run.
        parts = line.split(None, 1)
        if len(parts) != 2:
            continue
        cnt, word = parts
        if not cnt.isdigit():
            continue
        word = word.strip().lower()
        if len(word) < 1 or len(word) > 30 or not WORD_RE.match(word):
            continue
        out.append((word, int(cnt)))
        if len(out) >= top:
            break
    return out


def build_case_oracle(words: list, dict_name: str) -> dict:
    """
    Map each lowercase word to its correct case via hunspell (word→cased). Words that hunspell rejects in
    *both* cases are omitted (they are OPUS subtitle noise: typos, foreign/Swiss spellings like "gross",
    names) — the caller drops any word not in the returned map. Falls back to keeping everything lowercase
    if no Hunspell dictionary is available.
    """
    try:
        dic = get(f"{WOOORM}/{dict_name}/index.dic")
        aff = get(f"{WOOORM}/{dict_name}/index.aff")
    except Exception as e:
        sys.stderr.write(f"  WARN no Hunspell dict '{dict_name}' ({e}); keeping all words lowercase\n")
        return {w: w for w, _ in words}
    tmp = tempfile.mkdtemp()
    open(os.path.join(tmp, "d.dic"), "wb").write(dic)
    open(os.path.join(tmp, "d.aff"), "wb").write(aff)

    def rejected(cands: list) -> set:
        """
        Return the set of cands that hunspell considers misspelled. Uses `hunspell -l` (list only the
        unknown words, no correction suggestions) — orders of magnitude faster than `-a` on tens of
        thousands of words, and alignment-proof since we test set membership rather than line order.
        """
        proc = subprocess.run(
            ["hunspell", "-l", "-d", os.path.join(tmp, "d"), "-i", "utf-8"],
            input="\n".join(cands) + "\n", capture_output=True, text=True,
        )
        return set(proc.stdout.split("\n")) - {""}

    lowers = [w for w, _ in words]
    caps = [w[:1].upper() + w[1:] for w in lowers]
    bad_low = rejected(lowers)
    bad_cap = rejected(caps)
    oracle = {}
    for w, cap in zip(lowers, caps):
        if w not in bad_low:
            oracle[w] = w              # valid lowercase → keep lowercase (function words, verbs)
        elif cap not in bad_cap:
            oracle[w] = cap            # only valid capitalised → noun/proper, capitalise
        # else: rejected in both cases → drop (OPUS noise)
    return oracle


def to_json(words: list, oracle: dict) -> dict:
    # Keep only words the oracle recognised (drops OPUS noise / foreign spellings).
    kept = [(oracle[w], c) for w, c in words if w in oracle]
    if not kept:
        raise SystemExit("no words survived the Hunspell filter")
    counts = [c for _, c in kept]
    lo, hi = math.log(min(counts)), math.log(max(counts))
    span = (hi - lo) or 1.0
    out = {}
    for form, c in kept:
        v = 128 + round(127 * (math.log(c) - lo) / span)
        out[form] = max(out.get(form, 0), max(128, min(255, v)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("lang")
    ap.add_argument("--opus", default=None)
    ap.add_argument("--dict", default=None)
    ap.add_argument("--name", default="<DisplayName>")
    ap.add_argument("--top", type=int, default=50000)
    ap.add_argument("--out", default=".")
    args = ap.parse_args()

    words = load_frequencies(args.opus or args.lang, args.top)
    if not words:
        raise SystemExit("no frequency data (wrong --opus code?)")
    oracle = build_case_oracle(words, args.dict or args.lang)
    data = to_json(words, oracle)

    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{args.lang}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    blob = open(path, "rb").read()
    sha = hashlib.sha256(blob).hexdigest()
    caps = sum(1 for w in data if w != w.lower())
    sys.stderr.write(
        f"  wrote {path}: {len(data)} words, {caps} capitalised, {len(blob)} bytes, sha256 {sha}\n"
        f"  sample {list(data.items())[:8]}\n"
    )
    print(f'GlideDict("{args.lang}", "{args.name}", "$REL/{args.lang}.json", {len(blob)}, "{sha}"),')


if __name__ == "__main__":
    main()
