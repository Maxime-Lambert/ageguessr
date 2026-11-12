# Année de l'indice écrite en deux chiffres

Un joueur a vu « Mai 68 » comme indice de 1968 : l'année donnait la réponse. Le contrôle ne
refusait que les nombres de 1900 à 2099. Branche `fix/two-digit-year-in-hints`.

## For Future Agents
Plan court d'un correctif, rempli une fois le travail fait.

## Phase 1 : contrôle et nouvelle extraction
Status: Complete

- [x] `HintText.containsItsYear(text, year)` : l'année de l'indice en deux chiffres, nombre
      isolé (pas dans un nombre plus long ni un décimal), apostrophe permise (« '69 »).
      Seule l'année de l'indice : « 12 Years a Slave » (2013) reste permis.
- [x] Appliqué à l'import (`HintRules`, rejet `YEAR_IN_TEXT`) et à la correction admin
      (`ReviewHintHandler`, `HINT_TEXT_CONTAINS_YEAR`) ; message admin précisé.
- [x] Même règle dans l'extracteur (`gives_the_year`), fichiers régénérés.
- [x] Tests : `HintRulesTest` (refusés : « Mai 76 », « Windows 76 », « Woodstock '76 » ;
      permis : « 12 Years a Slave », « Vol 1176 », « Boeing 767 », « Top 7.76 »),
      `ReviewHintHandlerTest`, et le test d'intégration des fichiers livrés, qui refusait
      les anciens fichiers.

### Verification Plan
- Recherche sur les 8 674 indices livrés : 4 indices concernés (« Mai 68 » / « May 68 »,
  « Windows 95 » FR et EN) ; « 12 Years a Slave » écarté du constat, faux positif.
- `./mvnw test` et `pnpm test` passent.

### Phase Summary
Les nouveaux fichiers retirent 7 indices et en ajoutent 8 : les 4 ci-dessus, « Premier
vol : Boeing B-52 Stratofortress » en 1952 (FR et EN, « B-52 » donnait l'année), et par
ricochet « Teardrop » (Musique 1998 FR) remplacé par « S'il suffisait d'aimer » : un
candidat retiré libère une place parmi les mieux liés mesurés. Le 8e ajout complète
Science 1955 FR. Un remplaçant, une base de films pornographiques (Science 1995 EN), est
écarté par `LEFT_OUT`.
`./mvnw test` 1 225 tests, `pnpm test` 425 tests, tout passe.

## Final Recap
Une année de l'indice écrite en deux chiffres est refusée partout où un texte entre en base.

## Deployment Plan
1. Merger la PR dans `develop`, puis la release `develop → main` (merge commit).
2. Après le déploiement, sur le VPS :
   `docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,import-hints --app.hints.import.retire-absent=true`
   Rapport attendu : `added=8`, `retired=7`, `refused={}`.
