# Fixtures Wikidata / Commons

Réponses réelles enregistrées le 2026-10-26 pendant le spike de
`plans/active-plan.md` (Phase 1). Elles sont reformatées (indentation) mais pas
modifiées. Les tests de parsing les rejouent avec `MockRestServiceServer`, sans réseau.

Sources : données Wikidata et Structured Data on Commons sous CC0. Les champs
`extmetadata` de Commons (auteur, licence) sont des métadonnées publiques des fichiers.
Aucune image n'est versionnée ici.

| Fichier | Requête | Ce qu'il couvre |
|---|---|---|
| `qlever-selection.rq` / `.json` | SPARQL QLever de sélection (≥ 50 sitelinks, `LIMIT 8` lignes) | Plusieurs lignes par personne (produit `P18` × `P569`) ; `P569` en `DeprecatedRank` (Q1002250) ; deux `P18` (Q10068) ; personnes décédées ; label FR différent du label EN (Q1001) ; image en URL `Special:FilePath` |
| `commons-search-<QID>.json` | `list=search`, `haswbstatement:P180=<QID> filetype:bitmap` | Fichiers « depicts » d'une personne |
| `commons-imageinfo-<QID>.json` | `prop=imageinfo`, `iiprop=url\|mime\|extmetadata\|metadata`, `iiurlwidth=800` | Q123344 (1844-1898) : EXIF avec `DateTime` (date du logiciel) mais **sans** `DateTimeOriginal`, 2 dates ambiguës. Q216924 (1932-1982) : non datée, sans EXIF, photo de groupe, âge ambigu. Q262665 (né en 1976) : 5 photos valides, 14 sans EXIF, 4 de groupe, `metadata` en `_error` |
| `commons-mediainfo-<QID>.json` | `wbgetentities` sur les `M<pageid>` | SDC `P571` (avec précision) et `P180` des mêmes fichiers |
| `commons-imageinfo-scan-date-trap.json` | `imageinfo` de *Marie-Thérèse Kerschbaumer (1988) by Guenter Prust.jpg* | Date déclarée 2012 (scan ou upload) contredite par l'année 1988 du nom de fichier |
| `commons-maxlag-error.json` | requête avec `maxlag=-1` | Format réel de l'erreur `maxlag` (HTTP 200 + `error.code = "maxlag"` ; l'en-tête `Retry-After: 5` n'est pas dans le fichier) |
