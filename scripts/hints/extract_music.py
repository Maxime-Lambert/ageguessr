#!/usr/bin/env python3
"""Extracts the music hints, by year and audience. See scripts/hints/README.md.

    python3 scripts/hints/extract_music.py --report
    python3 scripts/hints/extract_music.py --out ageguessr-api/src/main/resources/hints/music.json

Standard library only. Pages are cached, so a rerun does not call Wikipedia again.
"""
import argparse
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

USER_AGENT = "Ageguessr/1.0 (https://ageguessr.app; contact@ageguessr.app)"
CACHE = Path(__file__).parent / ".cache"
FIRST_YEAR, LAST_YEAR = 1932, 2015
MINIMUM, TARGET = 5, 10
YEAR_IN_TEXT = re.compile(r"\b(1[89]|20)\d\d\b")
# Political and military songs say more about a regime than about the year's music.
NOT_POP = re.compile(
    r"hymne|anthem|\bnational\b|nationale|marche |march\b|militaire|military|patrioti|politi|communis|socialis|"
    r"sovi[ée]t|fascis|nazi|partisan|r[ée]sistance|r[ée]volution|propagand|arm[ée]e rouge", re.I)
FRENCH_SONG = re.compile(r"chanson en français|chanson française", re.I)
REDIRECT = re.compile(r"redirect", re.I)
ORCHESTRA = re.compile(r"\s+(with|and (his|her|the))\b.*orchestra.*$", re.I)
TITLE_HEADER = re.compile(r"^(titre|title|single|song|chanson)s?\b", re.I)
ARTIST_HEADER = re.compile(r"^(artiste|artist|interpr[èe]te|performer)", re.I)
ALBUM_HEADER = re.compile(r"^album", re.I)


def wiki_api(lang, **params):
    params.update(format="json", formatversion=2)
    request = urllib.request.Request(
        f"https://{lang}.wikipedia.org/w/api.php",
        data=urllib.parse.urlencode(params).encode(),
        headers={"User-Agent": USER_AGENT},
    )
    for attempt in range(6):
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                result = json.load(response)
            time.sleep(0.3)
            return result
        except OSError as error:
            print(f"retry after {error}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    raise RuntimeError(f"Wikipedia does not answer: {params}")


def cached(name, compute):
    path = CACHE / (urllib.parse.quote(name, safe="") + ".json")
    if path.exists():
        return json.loads(path.read_text())
    value = compute()
    path.write_text(json.dumps(value, ensure_ascii=False))
    return value


def page_html(lang, title):
    def fetch():
        result = wiki_api(lang, action="parse", page=title, prop="text", redirects=1)
        return result.get("parse", {}).get("text")

    return cached(f"{lang}-html-{title}", fetch)


def category_members(lang, category):
    """Pages of a category with their number of language versions, a measure of fame."""

    def fetch():
        members, cont = [], {}
        while True:
            result = wiki_api(
                lang, action="query", generator="categorymembers", gcmtitle=category, gcmnamespace=0, gcmlimit=50,
                prop="langlinks|categories", lllimit="max", cllimit="max", **cont)
            for page in result.get("query", {}).get("pages", []):
                members.append({
                    "title": page["title"],
                    "languages": len(page.get("langlinks", [])),
                    "categories": [c["title"] for c in page.get("categories", [])],
                })
            if "continue" not in result:
                # Merge the pages split over several continuation answers.
                merged = {}
                for member in members:
                    known = merged.setdefault(member["title"], member)
                    known["languages"] = max(known["languages"], member["languages"])
                    known["categories"] = sorted(set(known["categories"]) | set(member["categories"]))
                return list(merged.values())
            cont = result["continue"]

    return cached(f"{lang}-members-{category}", fetch)


class Tables(HTMLParser):
    """Wikitables of a rendered page: rows of text cells, rowspan and colspan resolved."""

    SKIPPED = {"sup", "style", "script"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables, self.heading = [], ""
        self.stack, self.skip, self.cell, self.in_heading, self.heading_text = [], 0, None, False, []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in self.SKIPPED:
            self.skip += 1
        elif tag in ("h2", "h3"):
            self.in_heading, self.heading_text = True, []
        elif tag == "table":
            self.stack.append({"rows": [], "row": None, "heading": self.heading,
                               "wiki": "wikitable" in (attrs.get("class") or "")})
        elif not self.stack:
            return
        elif tag == "tr":
            self.stack[-1]["row"] = []
        elif tag in ("td", "th") and self.stack[-1]["row"] is not None:
            self.cell = {"text": [], "rowspan": _span(attrs, "rowspan"), "colspan": _span(attrs, "colspan"),
                         "header": tag == "th"}

    def handle_endtag(self, tag):
        if tag in self.SKIPPED:
            self.skip = max(0, self.skip - 1)
        elif tag in ("h2", "h3"):
            self.in_heading, self.heading = False, " ".join(self.heading_text).strip()
        elif tag == "table" and self.stack:
            table = self.stack.pop()
            if table["wiki"]:
                self.tables.append({"heading": table["heading"], "rows": _grid(table["rows"])})
        elif not self.stack:
            return
        elif tag in ("td", "th") and self.cell is not None:
            self.cell["text"] = re.sub(r"\s+", " ", "".join(self.cell["text"])).strip()
            self.stack[-1]["row"].append(self.cell)
            self.cell = None
        elif tag == "tr" and self.stack[-1]["row"] is not None:
            self.stack[-1]["rows"].append(self.stack[-1]["row"])
            self.stack[-1]["row"] = None

    def handle_data(self, data):
        if self.skip:
            return
        if self.in_heading:
            self.heading_text.append(data)
        elif self.cell is not None:
            self.cell["text"].append(data)


def _span(attrs, name):
    match = re.match(r"\d+", attrs.get(name) or "1")
    return max(1, int(match.group())) if match else 1


def _grid(rows):
    """Repeats a spanning cell on each row it covers, so that columns line up."""
    grid, pending = [], {}
    for row in rows:
        line, cells, column = {}, iter(row), 0
        while True:
            if column in pending and pending[column][0] > 0:
                pending[column][0] -= 1
                line[column] = pending[column][1]
                column += 1
                continue
            cell = next(cells, None)
            if cell is None:
                if any(c in pending and pending[c][0] > 0 and c > column for c in pending):
                    column += 1
                    continue
                break
            for _ in range(cell["colspan"]):
                line[column] = cell
                if cell["rowspan"] > 1:
                    pending[column] = [cell["rowspan"] - 1, cell]
                column += 1
        grid.append([line[c] for c in sorted(line)])
    return grid


def tables_of(lang, title):
    text = page_html(lang, title)
    if not text:
        return []
    parser = Tables()
    parser.feed(text)
    return parser.tables


def short_artist(artist):
    return ORCHESTRA.sub("", artist)


def tidy(text):
    text = html.unescape(text)
    text = re.sub(r"\[[^\]]*\]", "", text)
    text = re.sub(r"[†‡§*^]+", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    text = text.strip("\"“”«» ‘’").strip()
    # A title followed by a remix name loses its opening quote above: drop the orphan.
    return text.replace('"', "") if text.count('"') % 2 else text


def from_french(artist):
    """The artist with the preposition French grammar wants: de Sardou, d'Adele, des Vagabonds."""
    if artist.startswith("Les "):
        return "des " + artist[4:]
    return ("d'" if re.match(r"[AEIOUYÀÂÉÈÊÎÔÛaeiouy]", artist) else "de ") + artist


def combined_cells(table, column, singles):
    """Before the Hot 100, each chart is a column whose cells read "Title"Artist."""
    for row in table["rows"][1:]:
        # A double A-side reads "First" / "Second"Artist: the artist follows the last quote.
        match = re.match(r"[\"“](.+?)[\"”].*[\"”]\W*(.+)$|[\"“](.+?)[\"”]\W*(.+)$", row[column]["text"]) if len(row) > column else None
        if match:
            name, performer = tidy(match.group(1) or match.group(3)), tidy(match.group(2) or match.group(4))
            entry = singles.setdefault(name.lower(), {"artist": performer, "title": name, "weeks": 0})
            entry["weeks"] += 1


def chart(lang, title):
    """Number ones of a page, (artist, title, weeks), most weeks first. Albums set apart."""
    singles, albums = {}, {}
    for table in tables_of(lang, title):
        rows = table["rows"]
        # From 2009 on, France has a first header row of chart names over Artiste / Titre.
        header_row = next((r for r in rows[:3] if any(ARTIST_HEADER.match(c["text"]) for c in r)), None)
        header_row = header_row or next((r for r in rows if sum(c["header"] for c in r) >= 2), None)
        if header_row is None:
            continue
        names = [c["text"] for c in header_row]
        best_sellers = next((i for i, n in enumerate(names) if re.match(r"best[ -]sell", n, re.I)), None)
        if best_sellers is not None:
            combined_cells(table, best_sellers, singles)
            continue
        artist = next((i for i, n in enumerate(names) if ARTIST_HEADER.match(n)), None)
        song = next((i for i, n in enumerate(names) if TITLE_HEADER.match(n)), None)
        album = next((i for i, n in enumerate(names) if ALBUM_HEADER.match(n)), None)
        heading = table["heading"].lower()
        is_album_table = album is not None and song is None or "album" in heading
        column = album if album is not None and song is None else song
        if artist is None or column is None:
            continue
        target = albums if is_album_table else singles
        start = rows.index(header_row) + 1
        for row in rows[start:]:
            if len(row) <= max(artist, column) or row[column]["header"] and row[column] is header_row[column]:
                continue
            name, performer = tidy(row[column]["text"]), tidy(row[artist]["text"])
            if not name or not performer or len(name) > 90 or len(performer) > 90:
                continue
            entry = target.setdefault(name.lower(), {"artist": performer, "title": name, "weeks": 0})
            entry["weeks"] += 1
    rank = lambda entries: sorted(entries.values(), key=lambda e: -e["weeks"])
    return rank(singles), rank(albums)


def song_page_title(title):
    return re.sub(r"\s*\([^)]*\)$", "", title).strip()


def category_songs(lang, category):
    """Songs of a yearly category, French ones first in French, then best known first."""
    songs = []
    for member in category_members(lang, category):
        labels = member["categories"]
        if NOT_POP.search(member["title"]) or any(NOT_POP.search(c) or REDIRECT.search(c) for c in labels):
            continue
        songs.append({"title": song_page_title(member["title"]), "page": member["title"],
                      "languages": member["languages"], "french": any(FRENCH_SONG.search(c) for c in labels)})
    return sorted(songs, key=lambda s: (lang == "fr" and not s["french"], -s["languages"]))


def url(lang, page):
    return f"https://{lang}.wikipedia.org/wiki/" + urllib.parse.quote(page.replace(" ", "_"), safe="/:()',")


def key(*parts):
    return re.sub(r"[^a-z0-9à-ÿ]+", "-", " ".join(parts).lower()).strip("-")


def hint(year, audience, text, source, kind, *parts):
    return {
        "category": "MUSIC", "year": year, "audience": audience,
        "textFr": text if audience == "FR" else None,
        "textEn": text if audience == "EN" else None,
        "sourceUrl": source, "sourceKind": kind,
        "dedupeKey": key("music", audience, kind, *parts), "translated": False,
    }


def french_candidates(year):
    page = f"Liste des titres musicaux numéro un en France en {year}"
    singles, _ = chart("fr", page)
    for entry in singles:
        yield hint(year, "FR", f"Numéro un en France : « {entry['title']} » {from_french(entry['artist'])}.",
                   url("fr", page), "chart", entry["artist"], entry["title"])
    for song in category_songs("fr", f"Catégorie:Chanson de {year}"):
        yield hint(year, "FR", f"Chanson sortie cette année-là : « {song['title']} ».",
                   url("fr", song["page"]), "category", song["title"])


def english_candidates(year):
    charts = [f"List of Billboard Hot 100 number ones of {year}", f"List of Billboard number-one singles of {year}"]
    for page in charts:
        for entry in chart("en", page)[0]:
            yield hint(year, "EN", f"Billboard No. 1: \"{entry['title']}\" by {short_artist(entry['artist'])}.",
                       url("en", page), "chart", entry["artist"], entry["title"])
    page = f"List of Billboard 200 number-one albums of {year}"
    for entry in chart("en", page)[1]:
        if "soundtrack" in entry["artist"].lower():
            continue
        yield hint(year, "EN", f"Billboard No. 1 album: {entry['title']} by {entry['artist']}.",
                   url("en", page), "album", entry["artist"], entry["title"])
    for song in category_songs("en", f"Category:{year} songs"):
        yield hint(year, "EN", f"A song released that year: \"{song['title']}\".",
                   url("en", song["page"]), "category", song["title"])


def collect(years):
    """Up to TARGET hints per year and audience. A title seen earlier belongs to its first year."""
    hints, seen = [], {"FR": set(), "EN": set()}
    for year in years:
        for audience, candidates in (("FR", french_candidates(year)), ("EN", english_candidates(year))):
            kept = 0
            for candidate in candidates:
                text = candidate["textFr"] or candidate["textEn"]
                identity = re.sub(r"\W+", "", text.split(":", 1)[1].lower())
                if identity in seen[audience] or YEAR_IN_TEXT.search(text) or kept >= TARGET:
                    continue
                seen[audience].add(identity)
                hints.append(candidate)
                kept += 1
    return hints


def report(hints, years):
    print(f"{'public':8}{'indices':>9}{'années >= 5':>13}{'années >= 10':>14}   par source")
    for audience in ("FR", "EN"):
        mine = [h for h in hints if h["audience"] == audience]
        counts = {y: sum(h["year"] == y for h in mine) for y in years}
        kinds = {}
        for h in mine:
            kinds[h["sourceKind"]] = kinds.get(h["sourceKind"], 0) + 1
        print(f"{audience:8}{len(mine):>9}{sum(v >= MINIMUM for v in counts.values()):>13}"
              f"{sum(v >= TARGET for v in counts.values()):>14}   {kinds}")
        print(f"  sous {MINIMUM} : {[y for y, v in counts.items() if v < MINIMUM]}")
        print(f"  sous {TARGET} : {[y for y, v in counts.items() if MINIMUM <= v < TARGET]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--first", type=int, default=FIRST_YEAR)
    parser.add_argument("--last", type=int, default=LAST_YEAR)
    parser.add_argument("--report", action="store_true")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    CACHE.mkdir(exist_ok=True)
    years = range(args.first, args.last + 1)
    hints = collect(years)
    if args.report:
        report(hints, years)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(hints, ensure_ascii=False, indent=1) + "\n")


if __name__ == "__main__":
    main()
