"""What the hint extractions share: Wikipedia and Wikidata access, cache, report, output."""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = "Ageguessr/1.0 (https://ageguessr.app; contact@ageguessr.app)"
CACHE = Path(__file__).parent / ".cache"
FIRST_YEAR, LAST_YEAR = 1932, 2015
MINIMUM, TARGET = 5, 10
# Matches HintText on the Java side: the import refuses any of these numbers.
YEAR_IN_TEXT = re.compile(r"(?<!\d)(19|20)\d{2}(?!\d)")
SPARQL_ENDPOINTS = ("https://qlever.dev/api/wikidata", "https://query.wikidata.org/sparql")
SPARQL_PREFIXES = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX schema: <http://schema.org/>
PREFIX p: <http://www.wikidata.org/prop/>
PREFIX psv: <http://www.wikidata.org/prop/statement/value/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX wikibase: <http://wikiba.se/ontology#>
"""


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
    CACHE.mkdir(exist_ok=True)
    path = CACHE / (urllib.parse.quote(name, safe="") + ".json")
    if path.exists():
        return json.loads(path.read_text())
    value = compute()
    path.write_text(json.dumps(value, ensure_ascii=False))
    return value


def page_wikitext(lang, title):
    def fetch():
        result = wiki_api(lang, action="parse", page=title, prop="wikitext", redirects=1)
        return result.get("parse", {}).get("wikitext")

    return cached(f"{lang}-wikitext-{title}", fetch)


def link_fame(lang, page):
    """Number of language versions of every page this one links to."""

    def fetch():
        fame, cont = {}, {}
        while True:
            result = wiki_api(lang, action="query", generator="links", titles=page, gpllimit="max", gplnamespace=0,
                              prop="langlinkscount", **cont)
            for linked in result.get("query", {}).get("pages", []):
                if "langlinkscount" in linked:
                    fame[linked["title"]] = linked["langlinkscount"]
            if "continue" not in result:
                return fame
            cont = result["continue"]

    return cached(f"{lang}-linkfame-{page}", fetch)


def sparql(query):
    """QLever first, it answers the big queries; the Wikidata service when it is down."""
    data = urllib.parse.urlencode({"query": SPARQL_PREFIXES + query}).encode()
    headers = {"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json"}
    for attempt in range(4):
        for endpoint in SPARQL_ENDPOINTS:
            try:
                request = urllib.request.Request(endpoint, data=data, headers=headers)
                with urllib.request.urlopen(request, timeout=120) as response:
                    result = json.load(response)["results"]["bindings"]
                time.sleep(1)
                return [{name: cell["value"] for name, cell in row.items()} for row in result]
            except (OSError, ValueError) as error:
                print(f"{endpoint}: {error}", file=sys.stderr)
        time.sleep(15 * (attempt + 1))
    raise RuntimeError("No SPARQL endpoint answers")


def qid(entity_uri):
    return entity_uri.rsplit("/", 1)[-1]


def url(lang, page):
    return f"https://{lang}.wikipedia.org/wiki/" + urllib.parse.quote(page.replace(" ", "_"), safe="/:()',")


def wikidata_url(item):
    return f"https://www.wikidata.org/wiki/{item}"


def key(*parts):
    return re.sub(r"[^a-z0-9à-ÿ]+", "-", " ".join(parts).lower()).strip("-")


def hint(category, year, audience, *, text_fr=None, text_en=None, source, kind, key_parts, subject=None,
         translated=False):
    entry = {
        "category": category, "year": year, "audience": audience, "textFr": text_fr, "textEn": text_en,
        "sourceUrl": source, "sourceKind": kind,
        "dedupeKey": key(category, audience, kind, *key_parts)[:255], "translated": translated,
    }
    if subject:
        entry["subjectQid"] = subject
    return entry


def texts_of(entry):
    return [text for text in (entry["textFr"], entry["textEn"]) if text]


def covered(entry, audience):
    return entry["audience"] in ("ALL", audience)


def report(hints, years):
    """Per audience, the years under the minimum and under the target."""
    print(f"{'public':8}{'indices':>9}{'années >= 5':>13}{'années >= 10':>14}   par source")
    for audience in ("FR", "EN"):
        mine = [h for h in hints if covered(h, audience)]
        counts = {y: sum(h["year"] == y for h in mine) for y in years}
        kinds = {}
        for h in mine:
            kinds[h["sourceKind"]] = kinds.get(h["sourceKind"], 0) + 1
        print(f"{audience:8}{len(mine):>9}{sum(v >= MINIMUM for v in counts.values()):>13}"
              f"{sum(v >= TARGET for v in counts.values()):>14}   {kinds}")
        print(f"  sous {MINIMUM} : {[y for y, v in counts.items() if v < MINIMUM]}")
        print(f"  sous {TARGET} : {[y for y, v in counts.items() if MINIMUM <= v < TARGET]}")


def write(hints, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(hints, ensure_ascii=False, indent=1) + "\n")
