# Icônes du site

`favicon.svg`, `favicon.ico` (32×32) et `apple-touch-icon.png` (180×180) sont générés à
partir du « A » de Fraunces, la police du wordmark (voir `docs/decisions/design.md`,
« Nom et logo »). Le glyphe est converti en tracé : l'icône ne dépend d'aucune police
installée chez le visiteur.

- Instance figée de la police variable : `wght` 700, `opsz` 9. L'optique la plus petite
  garde des déliés assez épais pour rester lisibles à 16-32 px ; l'optique « titre »
  (`opsz` 144) les fait disparaître.
- Couleurs : `--color-background` et `--color-accent` du thème sombre ; coin `rx` 3/64,
  proche de `--radius-sm`. `apple-touch-icon.png` est un carré plein sans coin ni
  transparence : iOS arrondit lui-même et remplit la transparence en noir.
- `favicon.ico` contient un seul PNG 32×32, format lu par tous les navigateurs actuels.

## Licence
Fraunces est distribuée sous SIL Open Font License 1.1, qui autorise l'intégration de
ses contours dans un logo ou une icône. Licence complète :
`node_modules/@fontsource-variable/fraunces/LICENSE`.

## Régénérer
Le script n'est pas versionné : c'est une opération ponctuelle. Il tient en ~40 lignes
de Node avec `fontkit@2.0.4`, `wawoff2@2.0.1` (fontkit ne sait pas instancier un WOFF2
variable, on le décompresse en TTF d'abord) et `@resvg/resvg-js@2.6.2` (rendu PNG) :

1. décompresser `node_modules/@fontsource-variable/fraunces/files/fraunces-latin-full-normal.woff2` ;
2. `getVariation({ wght: 700, opsz: 9, SOFT: 0, WONK: 0 })`, puis récupérer le glyphe
   via l'id trouvé sur la police de base (l'instance perd sa table `cmap`) ;
3. mettre le glyphe à l'échelle (hauteur 38/64, centré), inverser l'axe y, écrire le
   `<path>` ;
4. rendre le SVG en PNG 32 (emballé dans un en-tête ICO de 22 octets) et 180 (sans coin).
