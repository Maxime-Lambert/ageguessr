# Extraction des indices

Scripts ponctuels, lancés à la main, hors de l'application (voir
`plans/active-plan.md`). Bibliothèque standard Python uniquement. Les pages sont mises en
cache dans `.cache/` (ignoré par Git) : une relance n'appelle plus Wikipédia.

```bash
python3 scripts/hints/extract_music.py --report
python3 scripts/hints/extract_music.py --out ageguessr-api/src/main/resources/hints/music.json
```

`--first` et `--last` limitent les années (1932-2015 par défaut). Vider `.cache/` pour
repartir de pages à jour.
