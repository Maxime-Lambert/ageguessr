# Fixtures Wikidata / Commons

Réponses réelles enregistrées le 2026-10-26 pendant le spike de
`plans/done/people-sourcing.md` (Phase 1), puis lors de la refonte du client QLever (Phase 5). Elles sont reformatées (indentation) mais pas
modifiées. Les tests de parsing les rejouent avec `MockRestServiceServer`, sans réseau.

Sources : données Wikidata et Structured Data on Commons sous CC0. Les champs
`extmetadata` de Commons (auteur, licence) sont des métadonnées publiques des fichiers.
Aucune image n'est versionnée ici.

| Fichier | Requête | Ce qu'il couvre |
|---|---|---|
| `qlever-page.json` | Classement `?person ?sitelinks`, restreint par `VALUES` à 7 personnes choisies (re-enregistré le 2026-10-26 après la refonte du client) | Ordre par sitelinks décroissants |
| `qlever-births.json` | Déclarations `p:P569` avec rang, précision, calendrier | `P569` en `DeprecatedRank` (Q1002250) ; naissance `PreferredRank` en calendrier julien (Q77017) |
| `qlever-deaths.json` | Déclarations `p:P570`, valeur optionnelle | Deux décès dont un `PreferredRank` (Q76745) ; décès à l'année (Q77017) |
| `qlever-images.json` | `wdt:P18` | Deux `P18` (Q10068) ; URL `Special:FilePath` |
| `qlever-labels.json` | Labels `en*` / `fr*` avec leur langue | Label FR différent du label EN (Q1001) ; Q11571 (Cristiano Ronaldo) avec `en`, `en-gb`, `en-us`, que `FILTER(LANG(?l) = "en")` ne trouvait pas |
| `qlever-profiles.json` | `wdt:P21` UNION `wdt:P27` → `wdt:P30`, enregistré le 2026-10-27 | Genre et continents de nationalité ; Lindsey Vonn (Q10068) femme en Amérique du Nord, Gandhi (Q1001) en Asie, lignes en double pour deux pays du même continent (Q1002250) |
| `commons-search-<QID>.json` | `list=search`, `haswbstatement:P180=<QID> filetype:bitmap` | Fichiers « depicts » d'une personne |
| `commons-imageinfo-<QID>.json` | `prop=imageinfo`, `iiprop=url\|mime\|extmetadata\|metadata`, `iiurlwidth=800` | Q123344 (1844-1898) : EXIF avec `DateTime` (date du logiciel) mais **sans** `DateTimeOriginal`, 2 dates ambiguës. Q216924 (1932-1982) : non datée, sans EXIF, photo de groupe, âge ambigu. Q262665 (né en 1976) : 5 photos valides, 14 sans EXIF, 4 de groupe, `metadata` en `_error` |
| `commons-mediainfo-<QID>.json` | `wbgetentities` sur les `M<pageid>` | SDC `P571` (avec précision) et `P180` des mêmes fichiers |
| `commons-imageinfo-scan-date-trap.json` | `imageinfo` de *Marie-Thérèse Kerschbaumer (1988) by Guenter Prust.jpg* | Date déclarée 2012 (scan ou upload) contredite par l'année 1988 du nom de fichier |
| `commons-maxlag-error.json` | requête avec `maxlag=-1` | Format réel de l'erreur `maxlag` (HTTP 200 + `error.code = "maxlag"` ; l'en-tête `Retry-After: 5` n'est pas dans le fichier) |
