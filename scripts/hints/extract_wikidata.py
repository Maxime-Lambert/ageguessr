#!/usr/bin/env python3
"""Extracts the Film and Celebrities hints from Wikidata. See scripts/hints/README.md.

    python3 scripts/hints/extract_wikidata.py --category film --report
    python3 scripts/hints/extract_wikidata.py --category celebrities --out ageguessr-api/src/main/resources/hints/celebrities.json

Labels and descriptions exist in each language on Wikidata: nothing is translated, and a
hint carries both texts (audience ALL). Fame is the number of sitelinks.
"""
import argparse
import datetime
import re
from pathlib import Path

from common import (FIRST_YEAR, LAST_YEAR, TARGET, YEAR_IN_TEXT, cached, hint, link_fame, qid, report, sparql,
                    tables_of, tidy, url, wiki_api, wikidata_url, write)

MAX_FILM_YEAR = LAST_YEAR
FILM_SITELINKS = 12
# No minor in a public file: only people already 18 on the day of the extraction.
ADULT_AGE = 18
BATCH = 200
TITLES_PER_CALL = 50
LINKS_PER_LIST = 400
RANKED_PEOPLE = 40
BIRTH_LISTS = (("fr", "Naissance en {year}"), ("en", "{year}"), ("en", "Births in {year}"))
BOX_OFFICE_TOP = 5
FILM_HEADER = re.compile(r"^(film|titre)", re.I)
BORN_NOTE = re.compile(r"\s*\((born|née?|naissance)[^)]*\)|,?\s*\b(born|née? en)\b.*$", re.I)
# A hint must not reopen a trauma, and a description can say more than the criteria below.
NOT_A_CELEBRITY = re.compile(
    r"criminal|killer|murder|terroris|dictator|porn|gangster|nazi|criminel|tueur|meurtri|dictateur|assassin", re.I)

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


def entities(qids):
    return " ".join(f"wd:{item}" for item in sorted(qids))


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
    caught = set()
    for start in range(0, len(people), BATCH):
        values = entities(people[start:start + BATCH])
        for query in editorial_queries(values):
            caught |= {qid(row["person"]) for row in sparql(f"SELECT DISTINCT ?person WHERE {{ {query} }}")}
    return caught


def films():
    """Well-known films with their earliest release: P577 can also be a release abroad."""

    def fetch():
        return sparql(f"""
            SELECT ?film ?n ?date ?fr ?en WHERE {{
              ?film wdt:P31 wd:Q11424 ; wikibase:sitelinks ?n ; wdt:P577 ?date .
              FILTER(?n >= {FILM_SITELINKS})
              ?film rdfs:label ?fr . FILTER(LANG(?fr) = "fr")
              ?film rdfs:label ?en . FILTER(LANG(?en) = "en")
            }}""")

    merged = {}
    for row in cached("wikidata-films", fetch):
        item = qid(row["film"])
        film = merged.setdefault(item, {"item": item, "fame": int(row["n"]), "year": 9999,
                                        "fr": row["fr"], "en": row["en"]})
        film["year"] = min(film["year"], int(row["date"][:4]))
    return list(merged.values())


def directors(items):
    def fetch():
        found = {}
        for start in range(0, len(items), BATCH):
            rows = sparql(f"""
                SELECT ?film ?name WHERE {{
                  VALUES ?film {{ {entities(items[start:start + BATCH])} }}
                  ?film wdt:P57 ?director . ?director rdfs:label ?name . FILTER(LANG(?name) = "en")
                }}""")
            for row in rows:
                found.setdefault(qid(row["film"]), []).append(row["name"])
        return found

    return cached(f"wikidata-directors-{len(items)}", fetch)


def propaganda(items):
    """Films whose genre says propaganda: they would reopen what the people pool keeps out."""

    def fetch():
        found = set()
        for start in range(0, len(items), BATCH):
            rows = sparql(f"""
                SELECT ?film ?genre WHERE {{
                  VALUES ?film {{ {entities(items[start:start + BATCH])} }}
                  ?film wdt:P136 ?kind . ?kind rdfs:label ?genre . FILTER(LANG(?genre) = "en")
                }}""")
            found |= {qid(row["film"]) for row in rows if "propaganda" in row["genre"].lower()}
        return sorted(found)

    return set(cached(f"wikidata-propaganda-{len(items)}", fetch))


def of(name):
    return f"d'{name}" if name[0].lower() in "aeiouyàâéèêîôû" else f"de {name}"


def film_hints(years):
    by_year = {}
    for film in films():
        by_year.setdefault(film["year"], []).append(film)
    selected = {}
    for year in years:
        ranked = sorted(by_year.get(year, []), key=lambda f: (-f["fame"], f["item"]))
        # A few more than the target: the propaganda filter can still remove some.
        selected[year] = [f for f in ranked if not YEAR_IN_TEXT.search(f["fr"] + f["en"])][:TARGET + 3]
    banned = propaganda(sorted(f["item"] for films_of_year in selected.values() for f in films_of_year))
    selected = {year: [f for f in films_of_year if f["item"] not in banned][:TARGET]
                for year, films_of_year in selected.items()}
    credits = directors(sorted(f["item"] for films_of_year in selected.values() for f in films_of_year))
    hints = []
    for year in years:
        for film in selected[year]:
            names = credits.get(film["item"], [])
            # Two directors at most, the others would only lengthen the hint.
            fr = f" {of(names[0])}" + (f" et {names[1]}" if len(names) > 1 else "") if names else ""
            en = f" by {' and '.join(names[:2])}" if names else ""
            text_fr = f"Film sorti cette année-là : « {film['fr']} »{fr}."
            text_en = f"Film released that year: \"{film['en']}\"{en}."
            # A director's label can hold a year too, not only a title.
            if YEAR_IN_TEXT.search(text_fr + text_en):
                continue
            hints.append(hint("FILM", year, "ALL", text_fr=text_fr, text_en=text_en,
                              source=wikidata_url(film["item"]), kind="wikidata", key_parts=[film["item"]]))
        for title in box_office_france(year):
            hints.append(hint("FILM", year, "FR", text_fr=f"En tête du box-office français : « {title} ».",
                              source=url("fr", f"Box-office France {year}"), kind="box-office",
                              key_parts=[str(year), title]))
    return hints


def items_of(lang, titles):
    items = []
    for start in range(0, len(titles), TITLES_PER_CALL):
        result = wiki_api(lang, action="query", titles="|".join(titles[start:start + TITLES_PER_CALL]),
                          prop="pageprops", ppprop="wikibase_item", redirects=1)
        items += [page["pageprops"]["wikibase_item"] for page in result.get("query", {}).get("pages", [])
                  if "wikibase_item" in page.get("pageprops", {})]
    return items


def box_office_france(year):
    """The first films of the French box office: French players know their own cinema."""
    page = f"Box-office France {year}"
    for table in tables_of("fr", page):
        rows = table["rows"]
        header = next((r for r in rows[:2] if any(FILM_HEADER.match(c["text"]) for c in r)), None)
        if header is None:
            continue
        column = next(i for i, c in enumerate(header) if FILM_HEADER.match(c["text"]))
        titles = [tidy(r[column]["text"]) for r in rows[rows.index(header) + 1:] if len(r) > column]
        return [t for t in titles if t and not YEAR_IN_TEXT.search(t)][:BOX_OFFICE_TOP]
    return []


def people_of(year):
    """Wikidata cannot rank a whole year of people: the lists of births narrow them down first."""

    def fetch():
        items = set()
        for lang, pattern in BIRTH_LISTS:
            fame = link_fame(lang, pattern.format(year=year))
            # Dates come first in those lists, and they all hold a digit.
            people = [title for title in fame if not re.search(r"\d|^(février|mars|avril|mai|juin|juillet)", title, re.I)]
            best = sorted(people, key=lambda title: -fame[title])[:LINKS_PER_LIST]
            items |= set(items_of(lang, best))
        born = {}
        ordered = sorted(items)
        for start in range(0, len(ordered), BATCH):
            for row in sparql(f"""
                SELECT ?person ?n ?date WHERE {{
                  VALUES ?person {{ {entities(ordered[start:start + BATCH])} }}
                  ?person wdt:P31 wd:Q5 ; wikibase:sitelinks ?n ; wdt:P569 ?date .
                }}"""):
                if row["date"].startswith(str(year)):
                    born[qid(row["person"])] = int(row["n"])
        best = sorted(born, key=lambda item: (-born[item], item))[:RANKED_PEOPLE]
        return sparql(f"""
            SELECT DISTINCT ?person ?n ?date ?fr ?en ?descFr ?descEn WHERE {{
              VALUES ?person {{ {entities(best)} }}
              ?person wikibase:sitelinks ?n .
              ?person p:P569/psv:P569 ?value .
              ?value wikibase:timePrecision 11 ; wikibase:timeValue ?date .
              ?person rdfs:label ?fr . FILTER(LANG(?fr) = "fr")
              ?person rdfs:label ?en . FILTER(LANG(?en) = "en")
              ?person schema:description ?descFr . FILTER(LANG(?descFr) = "fr")
              ?person schema:description ?descEn . FILTER(LANG(?descEn) = "en")
            }}""") if best else []

    return cached(f"wikidata-people-{year}", fetch)


def description(text):
    return BORN_NOTE.sub("", text).strip(" ,.")


def celebrity_hints(years, today):
    adult_until = today.replace(year=today.year - ADULT_AGE).isoformat()
    candidates = {}
    for year in years:
        ranked = {}
        for row in people_of(year):
            fr, en = description(row["descFr"]), description(row["descEn"])
            texts = f"{row['fr']} {row['en']} {fr} {en}"
            if row["date"][:10] > adult_until or YEAR_IN_TEXT.search(texts) or NOT_A_CELEBRITY.search(texts):
                continue
            if fr and en:
                ranked[qid(row["person"])] = {**row, "item": qid(row["person"]), "fame": int(row["n"]),
                                              "descFr": fr, "descEn": en}
        # Twice the target: the editorial line can still remove some.
        candidates[year] = sorted(ranked.values(), key=lambda p: (-p["fame"], p["item"]))[:TARGET * 2]
    everyone = sorted(p["item"] for people in candidates.values() for p in people)
    excluded = set(cached(f"wikidata-editorial-{len(everyone)}", lambda: sorted(caught_by_editorial_line(everyone))))
    hints = []
    for year in years:
        for person in [p for p in candidates[year] if p["item"] not in excluded][:TARGET]:
            hints.append(hint("CELEBRITIES", year, "ALL",
                              text_fr=f"Personnalité née cette année-là : {person['fr']}, {person['descFr']}.",
                              text_en=f"Born that year: {person['en']}, {person['descEn']}.",
                              source=wikidata_url(person["item"]), kind="wikidata", key_parts=[person["item"]],
                              subject=person["item"]))
    return hints


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--category", choices=("film", "celebrities"), required=True)
    parser.add_argument("--first", type=int, default=FIRST_YEAR)
    parser.add_argument("--last", type=int)
    parser.add_argument("--report", action="store_true")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    today = datetime.date.today()
    last = args.last or (MAX_FILM_YEAR if args.category == "film" else today.year - ADULT_AGE)
    years = range(args.first, last + 1)
    hints = film_hints(years) if args.category == "film" else celebrity_hints(years, today)
    if args.report:
        report(hints, years)
    if args.out:
        write(hints, args.out)


if __name__ == "__main__":
    main()
