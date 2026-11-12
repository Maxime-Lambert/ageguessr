#!/usr/bin/env python3
"""Extracts the hints of every category from Wikidata, ranked by fame. See scripts/hints/README.md.

    python3 scripts/hints/extract_notoriety.py --report --out ageguessr-api/src/main/resources/hints
    python3 scripts/hints/extract_notoriety.py --only SPORT,MUSIC --samples 1934,1952

Candidates come from Wikidata, by category, with a date in the year and both labels. Their
fame is the views over twelve months of the article of the thing itself, on the Wikipedia of
the player's language: a FR hint is ranked by fr.wikipedia views, an EN hint by
en.wikipedia views. Per category, year and language, the ten best known are kept above a
floor computed per category and decade (see `select`). Texts are templates filled with
Wikidata labels: nothing is translated, and the files are CC0 like Wikidata.
"""
import argparse
import concurrent.futures
import datetime
import json
import re
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from common import FIRST_YEAR, LAST_YEAR, MINIMUM, TARGET, USER_AGENT, YEAR_IN_TEXT, cached, hint, qid, sparql, \
    wikidata_url, write

# Twelve full months, so that a season or a piece of news weighs a twelfth. A new extraction
# moves the window: the import only adds what is new.
VIEWS_FROM, VIEWS_TO = "20251101", "20261031"
WORKERS = 32
LANGS = ("fr", "en")
SOURCE_KIND = "wikidata-notoriety"
FILES = {"SPORT": "sport", "WORLD": "world", "SCIENCE": "science", "MUSIC": "music", "FILM": "film",
         "CELEBRITIES": "celebrities"}

# The floor of a category and decade: a share of the median of the best known fact of each
# year, never under the absolute floor of the language. A floor per decade follows the
# decline of old facts in views; a single floor would empty the thirties in French.
DECADE_FLOOR_SHARE = 0.02
DECADE_ABSOLUTE = {"fr": 500, "en": 5000}
# Albums and songs of one artist the same year: two at most, so that a year is not one name.
PER_ARTIST = 2
# First flights of aircraft the same year: four at most. Aviation articles are much read,
# military prototypes would otherwise fill Science while few players know them.
PER_YEAR_OF_KIND = {"flight": 4}

SITELINKS = {"SPORT": 12, "WORLD": 15, "SCIENCE": 15, "MUSIC": 12, "FILM": 15, "CELEBRITIES": 40}
# How many candidates per year go on to the views, by sitelinks: more where the pool is thin.
TOP = {"SPORT": 80, "WORLD": 80, "SCIENCE": 80, "MUSIC": 80, "FILM": 60, "CELEBRITIES": 40}

# No minor in a public file: only people already 18 on the day of the extraction.
ADULT_AGE = 18
BATCH = 200
# A year span written in a label: "1998–99", "1998-1999".
SPAN = re.compile(r"\s*\(?\b(19|20)\d{2}\s*[–-]\s*\d{2,4}\)?")
YEAR_WORD = re.compile(r"\s*\b(de |of |in |en )?\(?(19|20)\d{2}\)?\b")
# Prizes have a "winner" too: out of Sport, but the Grands Prix of motor racing stay.
NOT_SPORT = re.compile(r"(?<!grand )\b(prize|award|prix|médaille|medal|miss |concours|contest)\b", re.I)
# A description can say more than the editorial criteria below.
NOT_A_CELEBRITY = re.compile(
    r"criminal|killer|murder|terroris|dictator|porn|gangster|nazi|criminel|tueur|meurtri|dictateur|assassin", re.I)
BORN_NOTE = re.compile(r"\s*\((born|née?|naissance)[^)]*\)|,?\s*\b(born|née? en)\b.*$", re.I)

# Facts left out by hand, by Wikidata item: a living person named next to a crime (editorial
# line), or a fact badly described in Wikidata. The next fact of the year takes its place.
LEFT_OUT = {
    "Q12912943", "Q30325337",  # living people named, as victim or accused (review of 2026-11-12)
    "Q219141", "Q690158",  # a folklore figure and a sentence, dated as a discovery
}

# Same criteria as EditorialLine.java (ageguessr-api), which documents them. Criteria only,
# never a list of names: the repository is public.
CRIMES = ("Q135010 Q173462 Q41397 Q7283 Q47092 Q673281 Q43414 Q26553 Q660567 Q21129531 Q1968926 Q181784 "
          "Q132821").split()
DICTATOR = "Q183318"
FAR_RIGHT_ALIGNMENTS = "Q204481 Q127869500 Q23018230".split()
FAR_RIGHT_IDEOLOGIES = "Q6223 Q696116 Q7310 Q151250 Q278929 Q698752".split()
LEADER_ROLES = "Q48352 Q2285706".split()
AUTHORITARIAN_STATES = (
    "Q1000 Q1006 Q1007 Q1009 Q1016 Q1025 Q1029 Q1032 Q1037 Q1049 Q1050 Q115 Q148 Q15180 Q159 Q16957 Q184 Q219060 "
    "Q227 Q232 Q241 Q262 Q265 Q3108185 Q398 Q423 Q424 Q6500954 Q657 Q717 Q79 Q794 Q796 Q805 Q810 Q811 Q817 Q819 "
    "Q83286 Q836 Q838261 Q842 Q846 Q851 Q858 Q863 Q874 Q878 Q881 Q889 Q912 Q916 Q929 Q945 Q954 Q965 Q967 Q970 "
    "Q971 Q974 Q977 Q983 Q986").split()

TITLES = """
  OPTIONAL { ?aen schema:about ?i ; schema:isPartOf <https://en.wikipedia.org/> ; schema:name ?ten }
  OPTIONAL { ?afr schema:about ?i ; schema:isPartOf <https://fr.wikipedia.org/> ; schema:name ?tfr }
"""
LABELS = """
  ?i rdfs:label ?en FILTER(LANG(?en) = "en") ?i rdfs:label ?fr FILTER(LANG(?fr) = "fr")
"""
WINNER = """
  ?w wikibase:sitelinks ?wn ; rdfs:label ?wen FILTER(LANG(?wen) = "en")
  ?w rdfs:label ?wfr FILTER(LANG(?wfr) = "fr")
"""
# Dates kept apart: a point in time, or a start and an end (a war over several years gives
# two hints, its start and its end).
DATES = """
  OPTIONAL { ?i wdt:P585 ?p } OPTIONAL { ?i wdt:P580 ?s } OPTIONAL { ?i wdt:P582 ?e }
  FILTER(BOUND(?p) || BOUND(?s))
"""
# The host country, with its own inception: Wikidata gives today's country (Russia for a
# Soviet event), which is dropped when it did not exist yet (see `hosted`).
HOST = """
        OPTIONAL { ?i wdt:P17 ?host . ?host rdfs:label ?hostEn FILTER(LANG(?hostEn) = "en")
                   ?host rdfs:label ?hostFr FILTER(LANG(?hostFr) = "fr") OPTIONAL { ?host wdt:P571 ?hostStart } }"""

# A prize and what its laureate must be: a performance award goes to a person, and Wikidata
# also hangs it on the film ("Best Actress: A Streetcar Named Desire").
HUMAN, FILM_WORK = "wd:Q5", "wd:Q11424"
AWARD = """
      PREFIX ps: <http://www.wikidata.org/prop/statement/>
      PREFIX pq: <http://www.wikidata.org/prop/qualifier/>
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr ?prize ?prizeEn ?prizeFr WHERE {
        VALUES (?prize ?type) { %(prizes)s }
        ?i p:P166 ?st . ?st ps:P166 ?prize ; pq:P585 ?p . ?i wdt:P31 ?type ; wikibase:sitelinks ?n .
        FILTER(?n >= %%(n)d)
        ?prize rdfs:label ?prizeEn FILTER(LANG(?prizeEn) = "en") ?prize rdfs:label ?prizeFr FILTER(LANG(?prizeFr) = "fr")
        %%(labels)s %%(titles)s
      }"""


def award(*prizes):
    """Each prize as (item, type of its laureate)."""
    return AWARD % {"prizes": " ".join(f"(wd:{prize} {laureate})" for prize, laureate in prizes)}


# Each category is one or more queries; a row's `kind` tells how to word it.
QUERIES = {
    # An edition of a competition with its winner, not a single match nor a beauty pageant.
    # The host country tells apart two editions won by the same team: Italy won four World Cups.
    "SPORT": [("edition", """
      SELECT ?i ?n ?en ?fr ?p ?s ?e ?ten ?tfr ?w ?wn ?wen ?wfr ?host ?hostEn ?hostFr ?hostStart WHERE {
        ?i wdt:P1346 ?w ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d) %(winner)s %(dates)s %(labels)s %(titles)s
        FILTER NOT EXISTS { ?i wdt:P31 wd:Q110288240 }
        FILTER NOT EXISTS { ?i wdt:P31/wdt:P279* wd:Q118496986 }
        FILTER NOT EXISTS { VALUES ?pageant { wd:Q62391930 wd:Q58863414 } ?i wdt:P31/wdt:P279* ?pageant }""" + HOST + """
      }"""),
        # The Games themselves, told apart by their host city.
        ("games", """
      SELECT ?i ?n ?en ?fr ?p ?s ?ten ?tfr ?cityEn ?cityFr WHERE {
        VALUES ?c { wd:Q159821 wd:Q82414 } ?i wdt:P31 ?c ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d)
        { ?i wdt:P585 ?p } UNION { ?i wdt:P580 ?s }
        ?i wdt:P276 ?city . ?city rdfs:label ?cityEn FILTER(LANG(?cityEn) = "en")
        ?city rdfs:label ?cityFr FILTER(LANG(?cityFr) = "fr")
        %(labels)s %(titles)s
      }"""),
        ("award", award(("Q166177", HUMAN))),
    ],
    # Events of the world, elections with their winner, military operations (D-Day).
    "WORLD": [("event", """
      SELECT ?i ?n ?en ?fr ?p ?s ?e ?ten ?tfr ?w ?wn ?wen ?wfr ?c WHERE {
        VALUES ?c { wd:Q209715 wd:Q3839081 wd:Q350604 wd:Q178561 wd:Q2223653 wd:Q131569 wd:Q273120 wd:Q3882219
                    wd:Q45382 wd:Q40231 wd:Q10931 wd:Q171558 wd:Q3199915 wd:Q7944 wd:Q43109 wd:Q645883 }
        ?i wdt:P31/wdt:P279* ?c ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d) %(dates)s
        OPTIONAL { ?i wdt:P991 ?w . %(winner)s }
        %(labels)s %(titles)s
      }"""),
        # A state that comes into being (Israel, Morocco), an organisation founded (UN, NATO).
        ("state", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr WHERE {
        ?i wdt:P31 wd:Q3624078 ; wdt:P571 ?p ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d) %(labels)s %(titles)s
      }"""),
        ("organisation", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr WHERE {
        ?i wdt:P31/wdt:P279* wd:Q484652 ; wdt:P571 ?p ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d)
        %(labels)s %(titles)s
      }"""),
    ],
    "SCIENCE": [
        # A discovery or an invention dated by Wikidata: neutron, transistor, laser. Neither a
        # place nor a celestial body (an asteroid is discovered every week, see CLASS_CHECKS).
        ("discovery", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr WHERE {
        ?i wdt:P575 ?p ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d) %(labels)s %(titles)s
        FILTER NOT EXISTS { ?i wdt:P625 ?place }
      }"""),
        # A Nobel prize of physics, chemistry or medicine, and its laureate (whose fame counts).
        ("nobel", award(("Q38104", HUMAN), ("Q44585", HUMAN), ("Q80061", HUMAN))),
        # Space missions and technical products: consoles, phones, websites, software. Not an
        # organisation among them (the NBA is an online database too): out of Science, and
        # World has the international ones (see CLASS_CHECKS).
        ("launch", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr WHERE {
        VALUES ?c { wd:Q2133344 wd:Q8076 wd:Q19723451 wd:Q35127 wd:Q7397 wd:Q26540 wd:Q134808 }
        ?i wdt:P31/wdt:P279* ?c ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d)
        FILTER NOT EXISTS { ?i wdt:P31 wd:Q7889 }
        { ?i wdt:P619 ?p } UNION { ?i wdt:P577 ?p } UNION { ?i wdt:P571 ?p }
        %(labels)s %(titles)s
      }"""),
        ("flight", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr WHERE {
        ?i wdt:P606 ?p ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d) %(labels)s %(titles)s
      }"""),
    ],
    "MUSIC": [("album", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr ?w ?wn ?wen ?wfr WHERE {
        ?i wdt:P31 wd:Q482994 ; wikibase:sitelinks ?n ; wdt:P577 ?p . FILTER(?n >= %(n)d)
        OPTIONAL { ?i wdt:P175 ?w . %(winner)s } %(labels)s %(titles)s
      }"""), ("song", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr ?w ?wn ?wen ?wfr WHERE {
        VALUES ?c { wd:Q134556 wd:Q7366 }
        ?i wdt:P31 ?c ; wikibase:sitelinks ?n ; wdt:P577 ?p . FILTER(?n >= %(n)d)
        OPTIONAL { ?i wdt:P175 ?w . %(winner)s } %(labels)s %(titles)s
      }"""),
        ("edition", """
      SELECT ?i ?n ?en ?fr ?p ?s ?e ?ten ?tfr ?w ?wn ?wen ?wfr ?host ?hostEn ?hostFr ?hostStart WHERE {
        ?i wdt:P31 wd:Q110288240 ; wdt:P1346 ?w ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d)
        %(winner)s %(dates)s %(labels)s %(titles)s""" + HOST + """
      }"""),
        ("award", award(("Q904528", "wd:Q482994"), ("Q843219", "wd:Q134556"), ("Q843219", "wd:Q7366"),
                        ("Q1027904", "wd:Q7366"))),
        ("work", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr ?w ?wn ?wen ?wfr WHERE {
        ?i wdt:P31 wd:Q207628 ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d)
        { ?i wdt:P577 ?p } UNION { ?i wdt:P571 ?p }
        OPTIONAL { ?i wdt:P86 ?w . %(winner)s } %(labels)s %(titles)s
      }"""),
    ],
    "FILM": [("film", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr WHERE {
        ?i wdt:P31 wd:Q11424 ; wikibase:sitelinks ?n ; wdt:P577 ?p . FILTER(?n >= %(n)d) %(labels)s %(titles)s
      }"""),
        ("award", award(("Q102427", FILM_WORK), ("Q103360", HUMAN), ("Q103916", HUMAN), ("Q103618", HUMAN),
                        ("Q179808", FILM_WORK), ("Q645595", FILM_WORK), ("Q900494", HUMAN))),
    ],
    # The birth date to the day, so that the adult rule holds on the day of the extraction.
    "CELEBRITIES": [("person", """
      SELECT ?i ?n ?en ?fr ?p ?ten ?tfr ?descEn ?descFr WHERE {
        ?i wdt:P31 wd:Q5 ; wikibase:sitelinks ?n . FILTER(?n >= %(n)d)
        ?i p:P569/psv:P569 ?value . ?value wikibase:timePrecision 11 ; wikibase:timeValue ?p .
        ?i schema:description ?descFr FILTER(LANG(?descFr) = "fr")
        ?i schema:description ?descEn FILTER(LANG(?descEn) = "en")
        %(labels)s %(titles)s
      }""")],
}

# The years a country of today was part of another state, when Wikidata dates it from its
# origins (Russia: 1263): a Soviet event is not hosted by Russia. Its P571 cannot tell.
NOT_A_HOST = {
    "Q159": (None, 1991), "Q212": (None, 1991), "Q184": (None, 1991), "Q232": (None, 1991),
    "Q230": (None, 1991), "Q399": (None, 1991), "Q1027": (None, 1991), "Q265": (None, 1991),
    "Q813": (None, 1991), "Q863": (None, 1991), "Q874": (None, 1991), "Q217": (None, 1991),
    "Q227": (None, 1991), "Q211": (1940, 1991), "Q37": (1940, 1991), "Q191": (1940, 1991),
    "Q403": (None, 2006), "Q224": (None, 1991), "Q215": (None, 1991), "Q225": (None, 1992),
    "Q221": (None, 1991), "Q236": (None, 2006), "Q213": (None, 1993), "Q214": (None, 1993),
}


def hosted(row, when):
    """Whether the row's host country existed as such the year of the event."""
    absent = NOT_A_HOST.get(qid(row["host"]))
    if absent and (absent[0] is None or absent[0] <= when) and when < absent[1]:
        return False
    return not (year(row.get("hostStart")) or 0) > when


# People whose French label is wrong in Wikidata on the day of the extraction (vandalism),
# as a fact's subject or as its winner: their English name is used in both languages.
ENGLISH_NAME = {"Q15897"}


# Class tests too heavy inside the queries above (QLever answers 500 on the whole of
# Wikidata): run afterwards on the candidates only, by batches. (kind, class, action).
CLASS_CHECKS = {
    # An edition must be a sports competition: P1346 also gives the winner of a film prize,
    # of a referendum or of a car race to a car.
    "SPORT": [("edition", "Q13406554", "keep")],
    "SCIENCE": [("discovery", "Q6999", "drop"), ("launch", "Q43229", "drop")],
}

NATIONAL_TEAM = {
    "fr": re.compile(r"^équipe (?:de |d'|du |des )(.+?)(?: masculine| féminine)? (?:de |d')"
                     r"(?:football|basket-ball|handball|hockey sur glace|rugby à XV|volley-ball|water-polo|cricket)\b.*$", re.I),
    "en": re.compile(r"^(.+?) (?:men's |women's )?national (?:association |under-\d+ )?"
                     r"(?:football|basketball|handball|ice hockey|rugby union|volleyball|water polo|cricket) team$", re.I),
}
WON = {"fr": "{} — vainqueur : {}", "en": "{} — winner: {}"}
HOSTED = {"fr": "{}, pays hôte : {}", "en": "{}, hosted by {}"}
SPAN_WORDS = {"fr": ("{} : début", "{} : fin"), "en": ("{}: start", "{}: end")}
CITY = {"fr": "{}, ville hôte : {}", "en": "{}, host city: {}"}
STATE = {"fr": "Création ou indépendance de l'État : {}", "en": "Founding or independence of the state: {}"}
FOUNDED = {"fr": "Création : {}", "en": "Founded: {}"}
FLIGHT = {"fr": "Premier vol : {}", "en": "First flight: {}"}
WORK = {"fr": "Œuvre « {} »", "en": "Work \"{}\""}
DISCOVERY = {"fr": "Découverte ou invention : {}", "en": "Discovered or invented: {}"}
LAUNCH = {"fr": "Lancement : {}", "en": "Launched: {}"}
FILM = {"fr": "Sortie du film « {} »", "en": "Release of the film \"{}\""}
ALBUM = {"fr": ("Album « {} » — {}", "Album éponyme — {}", "Album « {} »"),
         "en": ("Album \"{}\" — {}", "Self-titled album — {}", "Album \"{}\"")}
SONG = {"fr": ("Chanson « {} » — {}", "Chanson « {} »"), "en": ("Song \"{}\" — {}", "Song \"{}\"")}
CELEBRITY = {"fr": "Sur la photo, cette personne a l'âge qu'a (ou qu'aurait) cette année {}, {}.",
             "en": "On the photo, this person is as old as this celebrity is (or would be) this year: {}, {}."}


def year(value):
    return int(value[:4]) if value and re.match(r"^\d{4}", value) else None


def dated(kind, row):
    """The (year, part) of a row: a war over a year or more gives its start and its end."""
    point, start, end = year(row.get("p")), year(row.get("s")), year(row.get("e"))
    if kind == "edition":
        # A season over two years is remembered by its end ("1998–99": 1999).
        return [(end or point or start, None)]
    if kind == "event" and start and end and row.get("c", "").endswith("/Q350604") and long_enough(row):
        return [(start, "start"), (end, "end")]
    return [(point or start, None)]


def long_enough(row):
    try:
        begin = datetime.date.fromisoformat(row["s"][:10])
        finish = datetime.date.fromisoformat(row["e"][:10])
    except ValueError:
        return False
    return (finish - begin).days >= 365


def without_year(label):
    return re.sub(r"\s{2,}", " ", YEAR_WORD.sub("", SPAN.sub("", label))).strip(" ,:-–")


def short_winner(name, lang):
    match = NATIONAL_TEAM[lang].match(name)
    return match.group(1) if match else name


def capitalized(text):
    return text[:1].upper() + text[1:]


def prized(item, lang):
    prize = capitalized(item["prize" + lang.capitalize()])
    return f"{prize} : {item[lang]}" if lang == "fr" else f"{prize}: {item[lang]}"


def description(text):
    return BORN_NOTE.sub("", text).strip(" ,.")


def text(item, lang):
    """The hint of a candidate in one language, or None when it cannot be worded."""
    kind, label, winner = item["kind"], item[lang], item.get("w" + lang)
    capital = capitalized(label)
    if kind == "edition":
        if not winner:
            return None
        competition = without_year(capital)
        # Five host countries tell nothing, and a country younger than the event is today's.
        hosts = item.get("host" + lang.capitalize(), set())
        if hosts and len(hosts) <= 2:
            competition = HOSTED[lang].format(competition, " / ".join(sorted(hosts)))
        return WON[lang].format(competition, short_winner(winner, lang))
    if kind == "event":
        if item.get("part"):
            start, end = SPAN_WORDS[lang]
            return (start if item["part"] == "start" else end).format(capital)
        if YEAR_IN_TEXT.search(label):
            # "Élection présidentielle américaine — vainqueur : John F. Kennedy".
            return WON[lang].format(without_year(capital), winner) if winner else None
        return capital
    if kind == "games":
        city = item.get("city" + lang.capitalize())
        return CITY[lang].format(without_year(capital), city) if city else None
    if kind in ("award", "nobel"):
        return prized(item, lang)
    if kind == "state":
        return STATE[lang].format(label)
    if kind == "organisation":
        return FOUNDED[lang].format(label)
    if kind == "launch":
        return LAUNCH[lang].format(label)
    if kind == "flight":
        return FLIGHT[lang].format(label)
    if kind == "work":
        return WORK[lang].format(label) + (f" — {winner}" if winner else "")
    if kind == "discovery":
        return DISCOVERY[lang].format(label)
    if kind == "film":
        return FILM[lang].format(label)
    if kind == "album":
        if not winner:
            return ALBUM[lang][2].format(label)
        return ALBUM[lang][1].format(winner) if label.casefold() == winner.casefold() else ALBUM[lang][0].format(label, winner)
    if kind == "song":
        return SONG[lang][0].format(label, winner) if winner else SONG[lang][1].format(label)
    if kind == "person":
        return CELEBRITY[lang].format(label, item["desc" + lang.capitalize()])
    return capital


def candidates(category, today):
    """The facts of a category that can be worded, the best known by sitelinks for each year."""
    items = {}
    adult_until = today.replace(year=today.year - ADULT_AGE).isoformat()
    for kind, query in QUERIES[category]:
        def fetch():
            return sparql(query % {"n": SITELINKS[category], "labels": LABELS, "titles": TITLES,
                                   "winner": WINNER, "dates": DATES})

        # v2: the queries that gained ?host.
        rows = cached(f"notoriety-{category.lower()}-{kind}" + ("-v2" if "?host " in query else ""), fetch)
        # An event is a war as soon as one of its classes is: one row per class matched.
        wars = {row["i"] for row in rows if row.get("c", "").endswith("/Q350604")}
        for row in rows:
            if row["i"] in wars:
                row["c"] = "http://www.wikidata.org/entity/Q350604"
        for row in rows:
            if qid(row["i"]) in LEFT_OUT:
                continue
            if kind == "person" and row["p"][:10] > adult_until:
                continue
            for when, part in dated(kind, row):
                if when is None or not FIRST_YEAR <= when <= LAST_YEAR:
                    continue
                prize = qid(row["prize"]) if row.get("prize") else ""
                key = (qid(row["i"]), part, kind + prize if prize else "")
                item = items.get(key)
                if item is None:
                    item = items[key] = {"item": qid(row["i"]), "kind": kind, "part": part, "prize": prize,
                                         "n": int(row["n"]), "year": when, "en": row["en"],
                                         "fr": row["en"] if qid(row["i"]) in ENGLISH_NAME else row["fr"],
                                         "wn": -1}
                    for field in ("prizeEn", "prizeFr", "cityEn", "cityFr"):
                        if row.get(field):
                            item[field] = row[field]
                    if kind == "person":
                        item.update(descEn=description(row["descEn"]), descFr=description(row["descFr"]))
                # The earliest date: a release abroad comes later.
                if kind not in ("edition", "event"):
                    item["year"] = min(item["year"], when)
                for lang in LANGS:
                    if row.get("t" + lang):
                        item["t" + lang] = row["t" + lang]
                # A host country founded after the event is today's country, not the host's.
                if row.get("hostEn") and hosted(row, when):
                    for field in ("hostEn", "hostFr"):
                        item.setdefault(field, set()).add(row[field])
                if row.get("wn") and int(row["wn"]) > item["wn"]:
                    english_name = qid(row["w"]) in ENGLISH_NAME
                    item.update(wn=int(row["wn"]), wen=row["wen"], wfr=row["wen"] if english_name else row["wfr"])
    values = list(items.values())
    for kind, cls, action in CLASS_CHECKS.get(category, []):
        found = members(category, kind, cls, sorted({i["item"] for i in values if i["kind"] == kind}))
        if action == "drop":
            values = [i for i in values if not (i["kind"] == kind and i["item"] in found)]
        elif action == "keep":
            values = [i for i in values if i["kind"] != kind or i["item"] in found]
    if category == "SPORT":
        # An edition and its final name the same winner: one hint. Prizes stay out of Sport.
        best = {}
        for item in values:
            if NOT_SPORT.search(item["en"] + " " + item["fr"]):
                continue
            # Games and prizes have no winner label: each stays on its own.
            key = (item["year"], item.get("wen") or item["item"])
            if key not in best or item["n"] > best[key]["n"]:
                best[key] = item
        values = list(best.values())
    kept = []
    for item in values:
        for lang in LANGS:
            hint_text = text(item, lang)
            # The import refuses a text with a year: it would give the answer.
            item["text_" + lang] = hint_text if hint_text and not YEAR_IN_TEXT.search(hint_text) else None
        if category == "CELEBRITIES" and NOT_A_CELEBRITY.search(f"{item['descEn']} {item['descFr']}"):
            continue
        if item["text_en"] or item["text_fr"]:
            kept.append(item)
    # A text found in two years dates neither: "Tour de France — winner: Eddy Merckx" (five).
    for lang in LANGS:
        years_of = {}
        for item in kept:
            if item["text_" + lang]:
                years_of.setdefault(item["text_" + lang], set()).add(item["year"])
        for item in kept:
            if item["text_" + lang] and len(years_of[item["text_" + lang]]) > 1:
                item["text_" + lang] = None
    kept = [item for item in kept if item["text_en"] or item["text_fr"]]
    # Only the best known by sitelinks go on to the views: a year of games holds hundreds.
    by_year = {}
    for item in kept:
        by_year.setdefault(item["year"], []).append(item)
    return [i for year_items in by_year.values()
            for i in sorted(year_items, key=lambda i: (-i["n"], i["item"]))[:TOP[category]]]


def entities(qids):
    return " ".join(f"wd:{item}" for item in sorted(qids))


def members(category, kind, cls, qids):
    """Those of these items that are an instance of the class, or of one of its subclasses."""

    def fetch():
        found = set()
        for start in range(0, len(qids), BATCH):
            rows = sparql(f"SELECT DISTINCT ?i WHERE {{ VALUES ?i {{ {entities(qids[start:start + BATCH])} }} "
                          f"?i wdt:P31/wdt:P279* wd:{cls} }}")
            found |= {qid(row["i"]) for row in rows}
        return sorted(found)

    return set(cached(f"notoriety-{category.lower()}-{kind}-{cls}-{len(qids)}", fetch))


def editorial_queries(values):
    return [
        f"VALUES ?person {{ {values} }} VALUES ?crime {{ {entities(CRIMES)} }} ?charge wdt:P279* ?crime . "
        "{ ?person wdt:P1399 ?charge } UNION { ?person wdt:P1595 ?charge }",
        f"VALUES ?person {{ {values} }} ?person wdt:P106 wd:{DICTATOR} .",
        f"VALUES ?person {{ {values} }} ?person wdt:P102 ?party . "
        f"{{ VALUES ?a {{ {entities(FAR_RIGHT_ALIGNMENTS)} }} ?party wdt:P1387 ?a }} "
        f"UNION {{ VALUES ?i {{ {entities(FAR_RIGHT_IDEOLOGIES)} }} ?party wdt:P1142 ?i }}",
        f"VALUES ?person {{ {values} }} VALUES ?state {{ {entities(AUTHORITARIAN_STATES)} }} "
        f"VALUES ?role {{ {entities(LEADER_ROLES)} }} ?person wdt:P39 ?position . ?position wdt:P279* ?role . "
        "{ ?position wdt:P1001 ?state } UNION { ?position wdt:P17 ?state }",
    ]


def caught_by_editorial_line(people):
    """The people the editorial line keeps out of the game, as for the photos."""

    def fetch():
        caught = set()
        for start in range(0, len(people), BATCH):
            values = entities(people[start:start + BATCH])
            for query in editorial_queries(values):
                caught |= {qid(row["person"]) for row in sparql(f"SELECT DISTINCT ?person WHERE {{ {query} }}")}
        return sorted(caught)

    return set(cached(f"notoriety-editorial-{len(people)}", fetch))


def propaganda(films):
    """Films whose genre says propaganda: they would reopen what the people pool keeps out."""

    def fetch():
        found = set()
        for start in range(0, len(films), BATCH):
            rows = sparql(f"""
                SELECT ?film ?genre WHERE {{
                  VALUES ?film {{ {entities(films[start:start + BATCH])} }}
                  ?film wdt:P136 ?kind . ?kind rdfs:label ?genre . FILTER(LANG(?genre) = "en")
                }}""")
            found |= {qid(row["film"]) for row in rows if "propaganda" in row["genre"].lower()}
        return sorted(found)

    return set(cached(f"notoriety-propaganda-{len(films)}", fetch))


def screened(category, items):
    """Without the people and films the editorial line keeps out."""
    if category == "CELEBRITIES":
        banned = caught_by_editorial_line(sorted({i["item"] for i in items}))
    elif category == "FILM":
        banned = propaganda(sorted({i["item"] for i in items if i["kind"] == "film"}))
    else:
        return items
    return [i for i in items if i["item"] not in banned]


def views(lang, title):
    def fetch():
        article = urllib.parse.quote(title.replace(" ", "_"), safe="")
        url = (f"https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/{lang}.wikipedia/all-access/user/"
               f"{article}/monthly/{VIEWS_FROM}/{VIEWS_TO}")
        for attempt in range(5):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(request, timeout=60) as response:
                    return sum(month["views"] for month in json.load(response)["items"])
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    return 0
                time.sleep(2 * (attempt + 1))
            except OSError:
                time.sleep(2 * (attempt + 1))
        return None

    return cached(f"views-{lang}-{title}", fetch)


def measure(items):
    """The views of each candidate's own article, in each language it has one."""
    jobs = [(item, lang) for item in items for lang in LANGS if item.get("t" + lang)]
    with concurrent.futures.ThreadPoolExecutor(WORKERS) as pool:
        for (item, lang), count in zip(jobs, pool.map(lambda job: views(job[1], job[0]["t" + job[1]]), jobs)):
            item["v" + lang] = count or 0
    return items


def decade(year_value):
    return year_value // 10 * 10


def top(pool, count, category, artists=None):
    """The first `count` of a ranked pool, two albums or songs of one artist at most, four
    first flights at most. `artists` carries the counts over to the French complement."""
    artists = artists if artists is not None else {}
    kept = []
    for item in pool:
        if len(kept) == count:
            break
        limit = PER_YEAR_OF_KIND.get(item["kind"])
        if limit is not None:
            if artists.get(("kind", item["kind"]), 0) >= limit:
                continue
            artists[("kind", item["kind"])] = artists.get(("kind", item["kind"]), 0) + 1
        artist = item.get("wen") if category == "MUSIC" and item["kind"] in ("album", "song") else None
        if artist and artists.get(artist, 0) >= PER_ARTIST:
            continue
        if artist:
            artists[artist] = artists.get(artist, 0) + 1
        kept.append(item)
    return kept


def select(items, category, lang, years, fill_floors=None):
    """Top 10 of each year, above a floor computed per decade.

    With fill_floors (the English floors), a French year short of 10 is completed with the
    facts best known in English above their floor: their French label comes from Wikidata,
    so nothing is translated (marked `filled`, for the report).
    """
    pools = {y: [] for y in years}
    for item in items:
        if item.get("text_" + lang) and item["year"] in pools:
            pools[item["year"]].append(item)
    for pool in pools.values():
        pool.sort(key=lambda i: (-i.get("v" + lang, 0), i["item"]))
    chosen, floors = {}, {}
    for dec in sorted({decade(y) for y in years}):
        leaders = [pools[y][0].get("v" + lang, 0) for y in years if decade(y) == dec and pools[y]]
        floor = max(DECADE_FLOOR_SHARE * statistics.median(leaders) if leaders else 0, DECADE_ABSOLUTE[lang])
        floors[dec] = floor
        for y in (y for y in years if decade(y) == dec):
            artists = {}
            kept = top([i for i in pools[y] if i.get("v" + lang, 0) >= floor], TARGET, category, artists)
            if fill_floors and len(kept) < TARGET:
                english = sorted((i for i in pools[y] if i not in kept and i.get("ven", 0) >= fill_floors[dec]),
                                 key=lambda i: (-i.get("ven", 0), i["item"]))
                kept += [dict(i, filled=True) for i in top(english, TARGET - len(kept), category, artists)]
            chosen[y] = kept
    return chosen, floors


def years_of(category, today):
    # Celebrities never cite a minor: the last years are the people already 18.
    return range(FIRST_YEAR, min(LAST_YEAR, today.year - ADULT_AGE) + 1 if category == "CELEBRITIES" else LAST_YEAR + 1)


def hints_of(category, chosen):
    """The entries of the hint file: one per language, keyed by item (and start or end)."""
    entries = []
    for lang, per_year in chosen.items():
        for y in sorted(per_year):
            for item in per_year[y]:
                parts = [item["kind"], item["item"]] + ([item["prize"]] if item["prize"] else []) \
                        + ([item["part"]] if item["part"] else [])
                texts = {"text_fr": item["text_fr"]} if lang == "fr" else {"text_en": item["text_en"]}
                entries.append(hint(category, y, lang.upper(), **texts, source=wikidata_url(item["item"]),
                                    kind=SOURCE_KIND, key_parts=parts,
                                    subject=item["item"] if category == "CELEBRITIES" else None))
    return entries


def report(selections, floors_of):
    decades = sorted({decade(y) for y in range(FIRST_YEAR, LAST_YEAR + 1)})
    all_years = range(FIRST_YEAR, LAST_YEAR + 1)
    for lang in LANGS:
        absolute = f"{DECADE_ABSOLUTE[lang]:,}".replace(",", " ")
        print(f"\n## {lang.upper()} — top {TARGET} par an, plancher par catégorie et décennie "
              f"({DECADE_FLOOR_SHARE:.0%} de la médiane des meilleurs, jamais sous {absolute})")
        print("\nIndices retenus (médiane par an)\n")
        print("| Catégorie | " + " | ".join(f"{d}s" for d in decades) + f" | Années sous {MINIMUM} | Total |")
        print("|---" * (len(decades) + 3) + "|")
        for category, chosen in selections.items():
            per_year = chosen[lang]
            medians = [statistics.median([len(per_year.get(y, [])) for y in all_years if decade(y) == d])
                       for d in decades]
            print(f"| {category} | " + " | ".join(f"{m:g}" for m in medians)
                  + f" | {sum(len(per_year.get(y, [])) < MINIMUM for y in all_years)}"
                  + f" | {sum(len(v) for v in per_year.values())} |")
        total = sum(len(v) for chosen in selections.values() for v in chosen[lang].values())
        if lang == "fr":
            filled = sum(1 for chosen in selections.values() for v in chosen["fr"].values() for i in v
                         if i.get("filled"))
            print(f"\nComplétés depuis l'anglais : {filled} indices sur {total}")
        else:
            print(f"\nTotal : {total} indices")
        print("\nPlanchers calculés (vues sur 12 mois)\n")
        print("| Catégorie | " + " | ".join(f"{d}s" for d in decades) + " |")
        print("|---" * (len(decades) + 1) + "|")
        for category in selections:
            floors = floors_of[category][lang]
            print(f"| {category} | " + " | ".join(f"{floors[d]:,.0f}".replace(",", " ") if d in floors else "—"
                                                  for d in decades) + " |")
        enough = [sum(len(c[lang].get(y, [])) >= MINIMUM for c in selections.values()) for y in all_years]
        full = [sum(len(c[lang].get(y, [])) >= TARGET for c in selections.values()) for y in all_years]
        print(f"\nAu moins 3 catégories à {MINIMUM}+ : {sum(e >= 3 for e in enough)} / {len(enough)} ; au moins 4 : "
              f"{sum(e >= 4 for e in enough)} / {len(enough)} ; à {TARGET} : {sum(f >= 3 for f in full)} / {len(full)}"
              " ; sous 3 : " + (", ".join(f"{y} ({e})" for y, e in zip(all_years, enough) if e < 3) or "aucune"))


def samples(selections, years, lang):
    for y in years:
        print(f"\n### {y} ({lang.upper()})")
        for category, chosen in selections.items():
            kept = chosen[lang].get(y, [])
            print(f"- {category} : " + " · ".join(
                f"{i['text_' + lang]} ({i.get('v' + lang, 0):,}{', EN' if i.get('filled') else ''})" for i in kept))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", help="categories, comma separated (all by default)")
    parser.add_argument("--report", action="store_true", help="coverage tables per decade")
    parser.add_argument("--samples", help="years to print in full, comma separated")
    parser.add_argument("--out", type=Path, help="directory of the hint files")
    args = parser.parse_args()
    today = datetime.date.today()
    selections, floors_of = {}, {}
    for category in (args.only.split(",") if args.only else list(QUERIES)):
        items = screened(category, candidates(category, today))
        print(f"{category}: {len(items)} candidates", file=sys.stderr)
        measure(items)
        years = years_of(category, today)
        english, en_floors = select(items, category, "en", years)
        french, fr_floors = select(items, category, "fr", years, fill_floors=en_floors)
        selections[category] = {"fr": french, "en": english}
        floors_of[category] = {"fr": fr_floors, "en": en_floors}
        if args.out:
            write(hints_of(category, selections[category]), args.out / f"{FILES[category]}.json")
    if args.report:
        report(selections, floors_of)
    if args.samples:
        for lang in LANGS:
            samples(selections, [int(y) for y in args.samples.split(",")], lang)


if __name__ == "__main__":
    main()
