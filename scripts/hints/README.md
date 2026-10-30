# Extraction des indices

Scripts ponctuels, lancés à la main, hors de l'application (voir
`docs/decisions/architecture.md`). Bibliothèque standard Python uniquement. Les pages et
réponses sont mises en cache dans `.cache/` (ignoré par Git) : une relance n'appelle plus
Wikipédia ni Wikidata. Vider `.cache/` pour repartir de données à jour.

```bash
R=ageguessr-api/src/main/resources/hints
python3 scripts/hints/extract_music.py --report --out $R/music.json
python3 scripts/hints/extract_events.py --category world --report --out $R/world.json
python3 scripts/hints/extract_events.py --category science --report --out $R/science.json
python3 scripts/hints/extract_events.py --category sport --report --out $R/sport.json
python3 scripts/hints/extract_wikidata.py --category film --report --out $R/film.json
python3 scripts/hints/extract_wikidata.py --category celebrities --report --out $R/celebrities.json
```

`--first` et `--last` limitent les années (1932-2015 par défaut). `--report` liste les
années sous le minimum de 5 indices et sous la cible de 10.

| Catégorie | Script | Source |
|---|---|---|
| Musique | `extract_music.py` | Classements Wikipédia, puis catégories de chansons |
| Monde | `extract_events.py` | Pages `Y` ; pages mensuelles en français quand la page de l'année est vide |
| Science | `extract_events.py` | `Y en science`, `Y in science` |
| Sport | `extract_events.py` | `Y en sport`, `Y in sports`, lignes qui donnent un vainqueur |
| Cinéma | `extract_wikidata.py` | Films les plus connus, date `MIN`, et box-office France |
| Célébrités | `extract_wikidata.py` | Listes de naissances Wikipédia, puis Wikidata |

Sur une page de l'année, une ligne vaut la notoriété de ce vers quoi elle pointe (nombre de
langues), hors liens présents sur beaucoup d'années (pays, villes).

`science_translations.json` traduit des indices anglais pour les années françaises sous le
minimum, en dernier recours. Ces indices arrivent en attente dans `/admin/hints`. Si
l'extraction change un texte anglais, sa traduction ne s'applique plus : le rapport le montre.

## Relance annuelle de Célébrités, vers mars

Les indices sont rangés par année de naissance, donc les autres n'ont pas à changer. Il ne
manque que les personnes qui viennent d'avoir 18 ans, et seules celles qui ont déjà cet âge le
jour de l'extraction sont citées.

```bash
rm scripts/hints/.cache/wikidata-people-*.json scripts/hints/.cache/*Naissance* scripts/hints/.cache/wikidata-editorial-*
python3 scripts/hints/extract_wikidata.py --category celebrities --report --out ageguessr-api/src/main/resources/hints/celebrities.json
```

Commiter le fichier dans une PR, puis relancer l'import en prod : il n'ajoute que le nouveau.
Vérifier dans la couverture de `/admin/hints` que l'année dépasse 5 indices.

Wikidata : QLever d'abord, le service officiel en repli. Le critère de la ligne éditoriale
reprend `EditorialLine.java` : les deux sont à modifier ensemble.
