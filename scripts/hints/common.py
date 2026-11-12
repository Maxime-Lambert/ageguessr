"""What the hint extraction needs: Wikidata access, cache, hint entries, output."""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = "AgeGuessr/1.0 (https://ageguessr.app; contact@ageguessr.app)"
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


def cached(name, compute):
    CACHE.mkdir(exist_ok=True)
    path = CACHE / (urllib.parse.quote(name, safe="") + ".json")
    if path.exists():
        return json.loads(path.read_text())
    value = compute()
    path.write_text(json.dumps(value, ensure_ascii=False))
    return value


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


def write(hints, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(hints, ensure_ascii=False, indent=1) + "\n")
