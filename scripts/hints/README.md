# Extraction des indices

Script ponctuel, lancé à la main, hors de l'application (voir
`docs/decisions/architecture.md`, « Indices »). Bibliothèque standard Python uniquement. Les
réponses Wikidata et les vues sont mises en cache dans `.cache/` (ignoré par Git) : une
relance n'appelle plus rien. Vider `.cache/` pour repartir de données à jour.

```bash
python3 scripts/hints/extract_notoriety.py --report --out ageguessr-api/src/main/resources/hints
```

`--only SPORT,MUSIC` limite les catégories, `--samples 1934,1975` affiche tous les indices
retenus de ces années (avec leurs vues, `EN` pour un indice français complété depuis
l'anglais). `--report` donne, par langue, la médiane d'indices par décennie, les planchers
calculés et les années sous 3 catégories jouables.

## Comment un indice est choisi

1. **Candidats Wikidata**, par catégorie (requêtes `QUERIES`), avec une date dans l'année
   (1932-2015) et un libellé en français et en anglais :

   | Catégorie | Sources |
   |---|---|
   | Sport | Éditions avec un vainqueur et leur pays hôte, Jeux olympiques et leur ville, Ballon d'or |
   | Monde | Événements (conflits, traités, élections avec le gagnant…), États créés ou indépendants, organisations internationales |
   | Science | Découvertes et inventions, Nobel de physique, chimie et médecine, lancements, premiers vols |
   | Musique | Albums, chansons, Eurovision, Grammy, œuvres composées |
   | Cinéma | Films, Oscars, Palme d'or, César |
   | Célébrités | Personnes, date de naissance au jour près, majeures le jour de l'extraction |
   | Littérature | Parutions (romans, pièces, poèmes, nouvelles, BD) avec leur auteur, Nobel de littérature, Goncourt, Booker, Pulitzer de la fiction |

2. **Notoriété** : les vues sur 12 mois (`VIEWS_FROM`, `VIEWS_TO`) de l'article de
   l'élément lui-même, sur fr.wikipedia pour un indice français, sur en.wikipedia pour un
   indice anglais. Seuls les mieux liés de chaque année (`TOP`, par nombre de sitelinks)
   passent à la mesure.
3. **Sélection**, par catégorie, année et langue : les 10 plus vus au-dessus d'un plancher
   égal à 2 % de la médiane des meilleurs faits de la décennie, jamais sous 500 vues (FR)
   ou 5 000 (EN). Un plancher par décennie suit la baisse des vues des faits anciens.
   Au plus 2 albums ou chansons par artiste, 2 œuvres par auteur et 4 premiers vols par an.
4. **Complément du français** : une année française sous 10 est complétée par les faits
   au-dessus du plancher anglais. Leur texte utilise le libellé français de Wikidata :
   rien n'est traduit, l'indice entre directement en jeu.

Un indice par langue (`audience` FR ou EN), clé `<catégorie>-<langue>-wikidata-notoriety-…`
fondée sur l'élément Wikidata (et `start` / `end` pour une guerre de plus d'un an, qui donne
deux indices). Aucun texte ne contient d'année, l'import le refuserait.

## Ligne éditoriale

- Célébrités : mêmes critères que `EditorialLine.java` (les deux sont à modifier ensemble),
  plus un filtre sur la description.
- Cinéma : sans les films de propagande.
- `LEFT_OUT` écarte des faits à la main, par élément Wikidata : une personne vivante nommée
  à côté d'un crime, ou un fait mal décrit. Le fait suivant de l'année prend sa place.

## Importer une nouvelle extraction

L'import n'ajoute que les clés inconnues. Pour une extraction qui **remplace** la
précédente, l'option `app.hints.import.retire-absent=true` (variable `HINTS_RETIRE_ABSENT`)
retire en plus, dans la même transaction, les indices dont la clé n'est plus dans les
fichiers : ils ne sont plus proposés, mais restent affichés là où ils ont déjà été montrés.
Rien n'est supprimé, et une clé qui revient est réactivée.

```bash
cd ageguessr-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=import-hints -Dspring-boot.run.arguments=--app.hints.import.retire-absent=true
```

Pour retirer un indice précis, sur demande d'une personne citée par exemple, le passer en
`REJECTED` en base plutôt que de le supprimer : l'import recréerait une ligne supprimée. Une
personne citée se retrouve par `subject_qid` en Célébrités, par recherche sur le texte
ailleurs.

## Relance annuelle de Célébrités, vers mars

Les indices sont rangés par année de naissance. Il ne manque que les personnes qui viennent
d'avoir 18 ans. Relancer l'extraction de Célébrités puis l'import **sans** `retire-absent`
(la fenêtre de vues a bougé : les autres indices peuvent changer de rang, il n'y a pas lieu
de les retirer) :

```bash
rm scripts/hints/.cache/notoriety-celebrities-* scripts/hints/.cache/notoriety-editorial-*
python3 scripts/hints/extract_notoriety.py --only CELEBRITIES --report --out ageguessr-api/src/main/resources/hints
```

Commiter le fichier dans une PR, puis relancer l'import en prod. Vérifier dans la couverture
de `/admin/hints` que l'année dépasse 5 indices.

Wikidata : QLever d'abord, le service officiel en repli. QLever renvoie des 429 sous
charge : relancer plutôt que paralléliser les requêtes.
