#!/usr/bin/env python3
"""Extracts the World, Science and Sport hints from the year pages. See scripts/hints/README.md.

    python3 scripts/hints/extract_events.py --category world --report
    python3 scripts/hints/extract_events.py --category science --out ageguessr-api/src/main/resources/hints/science.json

Each page gives one hint per line of its event lists, in its own language: nothing is
translated. A year page holds a hundred lines or more, so the best known ones are kept:
a line is worth the fame of what it links to.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

from common import (FIRST_YEAR, LAST_YEAR, MINIMUM, TARGET, YEAR_IN_TEXT, hint, link_fame, page_wikitext, report, url, write)

MONTHS_FR = "janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre"
MONTHS_EN = "January|February|March|April|May|June|July|August|September|October|November|December"
DATE_FR = re.compile(rf"^[^:]{{0,50}}\b({MONTHS_FR})\b[^:]{{0,30}}:\s*|^\d{{1,2}}(er)?\s*[:–-]\s*", re.I)
DATE_EN = re.compile(rf"^[^–—:]{{0,40}}\b({MONTHS_EN})\b[^–—:]{{0,30}}[–—:]\s*|^\d{{1,2}}([–-]\d{{1,2}})?\s*[–—]\s*")
HEADING = re.compile(r"^(={2,})\s*(.*?)\s*\1\s*$")
NEVER_EVENTS = (r"naissance|décès|birth|death|award|prize|prix|nobel|référence|reference|notes?\b|voir aussi|see also|"
                r"fondation|lien|external|bibliograph|paléontologie|paleontology|publication|en bref|calendrier|calendar|"
                r"chronologie thématique")
OTHER_CATEGORIES = r"|film|musique|music|sport|littérature|literature"
TEMPLATE = re.compile(r"\{\{([^{}]*)\}\}")
LINK = re.compile(r"\[\[([^\[\]|]+)(?:\|([^\[\]]*))?\]\]")
KEPT_TEMPLATES = re.compile(r"^(lang|langue|nobr|nowrap|small|nom|sp|s-|siècle)\b", re.I)
DASH_TEMPLATES = re.compile(r"^(snd|spnd|ndash|mdash|spaced ndash)$", re.I)
# Private life, and lines that make no sense without the one before.
PRIVATE = re.compile(
    r"\b(marri|divorc|pregnan|gives? birth|engaged|mariage|épous|enceinte|accouch|fiançailles)", re.I)
NEEDS_CONTEXT = re.compile(r"^(it|he|she|they|this|these|the same|il|elle|ils|elles|ce|cet|cette|ces|celui)\b", re.I)
# A date the patterns above missed, misspelt or listing several days.
LEFTOVER_DATE = re.compile(r"^\d{1,2}(er)?\s*(–|-|,|\w+\s*:|(janvier|février|mars|avril|mai|juin|juillet|août)\b)")
DECADE = re.compile(r"\d0s\b|années \d0|\bsiècle|century", re.I)
MIN_LENGTH, MAX_LENGTH = 35, 230
# A link found on few year pages is an event or a person; on many, a country or a city.
MAX_YEAR_PAGES = 10
# The first hints of a year are kept whatever their fame, the others must be well known.
FAME_FLOOR = 40
SENTENCE_END = re.compile(r"(?<=[a-zéèàûôî)»\"]{3})\. (?=[A-ZÉÈÀ«])")
# French year pages hold no events from this size down: they point to a page per month.
THIN_PAGE = 20000
MONTH_PAGES_FR = MONTHS_FR.split("|")
# A sports line is a hint only if it tells who won: the rest is a fixture or a record.
RESULT = re.compile(r"\b(remport|gagn|vainqueur|champions?\b|bat\b|battent|wins?\b|won\b|beats?\b|defeats?\b)", re.I)
HEADLESS = re.compile(r"^(vainqueur|winner|champion|won|wins|bat|men's|women's|final|finale|première édition)\b", re.I)
TRANSLATIONS = Path(__file__).parent / "science_translations.json"
SOURCES = {
    "world": {"FR": ("fr", "{year}"), "EN": ("en", "{year}")},
    "science": {"FR": ("fr", "{year} en science"), "EN": ("en", "{year} in science")},
    "sport": {"FR": ("fr", "{year} en sport"), "EN": ("en", "{year} in sports")},
}


def template_text(match):
    parts = match.group(1).split("|")
    if DASH_TEMPLATES.match(parts[0].strip()):
        return " – "
    if KEPT_TEMPLATES.match(parts[0].strip()):
        return parts[-1]
    if parts[0].strip().lower().startswith(("date", "1er")) or re.match(r"^\d", parts[0]):
        return " ".join(p for p in parts[1:] if "=" not in p) or parts[0]
    return "\0"


def clean(line, audience):
    """The plain sentence of a list line, and what it links to. None when it cannot stand alone."""
    text = re.sub(r"<ref[^>]*/>|<ref.*?(</ref>|$)|<!--.*?-->", "", line)
    text = text.lstrip("*:# ").replace("&nbsp;", " ").replace("''", "")
    text = text.replace("&ndash;", "–").replace("&mdash;", "—")
    links = [m.group(1).split("#")[0].strip() for m in LINK.finditer(text)]
    for _ in range(3):
        text = LINK.sub(lambda m: m.group(2) or m.group(1), text)
        text = TEMPLATE.sub(template_text, text)
    date = DATE_FR if audience == "FR" else DATE_EN
    text = date.sub("", text, count=1)
    text = re.sub(r"\s+", " ", text).strip(" .;")
    text = SENTENCE_END.split(text)[0]
    if re.search(r"[\0{}\[\]|<>=]", text) or not MIN_LENGTH <= len(text) <= MAX_LENGTH:
        return None
    text = re.sub(r"\s+,", ",", text)
    if text.endswith(":") or YEAR_IN_TEXT.search(text) or DECADE.search(text) or LEFTOVER_DATE.match(text):
        return None
    if PRIVATE.search(text) or NEEDS_CONTEXT.match(text):
        return None
    return {"text": text[0].upper() + text[1:] + ".", "links": links}


def event_lines(category, lang, page):
    skipped = re.compile(NEVER_EVENTS + ("" if category == "sport" else OTHER_CATEGORIES), re.I)
    lines, skipping, skip_depth = [], False, 0
    for raw in (page_wikitext(lang, page) or "").split("\n"):
        heading = HEADING.match(raw.strip())
        if heading:
            depth = len(heading.group(1))
            if skipping and depth > skip_depth:
                continue
            skipping = bool(skipped.search(heading.group(2)))
            skip_depth = depth
        elif not skipping and raw.startswith("*"):
            lines.append(raw)
    return lines


def is_result(text):
    return bool(RESULT.search(text)) and not HEADLESS.match(text)


def normalized(title):
    return (title[:1].upper() + title[1:]).replace("_", " ").strip()


def pages_of(category, audience, year):
    lang, pattern = SOURCES[category][audience]
    page = pattern.format(year=year)
    if category == "world" and lang == "fr" and len(page_wikitext(lang, page) or "") < THIN_PAGE:
        return [f"{month.capitalize()} {year}" for month in MONTH_PAGES_FR]
    return [page]


def collect(category, years):
    hints = []
    for audience, (lang, _) in SOURCES[category].items():
        pages = {year: pages_of(category, audience, year) for year in years}
        fame = {year: {} for year in years}
        year_pages = {}
        for year, titles in pages.items():
            for page in titles:
                fame[year].update(link_fame(lang, page))
            for title in fame[year]:
                year_pages[title] = year_pages.get(title, 0) + 1
        for year, titles in pages.items():
            def worth(title):
                return fame[year].get(title, 0) if year_pages.get(title, 0) <= MAX_YEAR_PAGES else 0

            candidates = []
            for page in titles:
                for line in event_lines(category, lang, page):
                    candidate = clean(line, audience)
                    if candidate and (category != "sport" or is_result(candidate["text"])):
                        candidate["page"] = page
                        candidate["subject"] = max(map(normalized, candidate["links"]), key=worth, default=None)
                        candidate["worth"] = worth(candidate["subject"])
                        candidates.append(candidate)
            used, kept = set(), 0
            for candidate in sorted(candidates, key=lambda c: (-c["worth"], c["text"])):
                # Two lines about the same event would be the same hint.
                if kept >= TARGET or candidate["subject"] in used:
                    continue
                if kept >= MINIMUM and candidate["worth"] < FAME_FLOOR:
                    continue
                used.add(candidate["subject"])
                kept += 1
                digest = hashlib.sha1(candidate["text"].encode()).hexdigest()[:12]
                texts = {"text_fr": candidate["text"]} if audience == "FR" else {"text_en": candidate["text"]}
                hints.append(hint(category.upper(), year, audience, **texts, source=url(lang, candidate["page"]),
                                  kind="year-page", key_parts=[str(year), digest]))
    if category == "science":
        hints += translations(hints, years)
    return hints


def translations(hints, years):
    """Fills the French years under the minimum, last resort: these hints wait for an admin."""
    french = {h["year"]: 0 for h in hints}
    for h in hints:
        french[h["year"]] += h["audience"] == "FR"
    filled = []
    translated = json.loads(TRANSLATIONS.read_text())
    for h in hints:
        digest = h["dedupeKey"].rsplit("-", 1)[-1]
        if h["audience"] == "EN" and digest in translated and h["year"] in years and french[h["year"]] < MINIMUM:
            filled.append(hint("SCIENCE", h["year"], "FR", text_fr=translated[digest], source=h["sourceUrl"],
                               kind="translated", key_parts=[str(h["year"]), digest], translated=True))
    return filled


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--category", choices=SOURCES, required=True)
    parser.add_argument("--first", type=int, default=FIRST_YEAR)
    parser.add_argument("--last", type=int, default=LAST_YEAR)
    parser.add_argument("--report", action="store_true")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    years = range(args.first, args.last + 1)
    hints = collect(args.category, years)
    if args.report:
        report(hints, years)
    if args.out:
        write(hints, args.out)


if __name__ == "__main__":
    main()
