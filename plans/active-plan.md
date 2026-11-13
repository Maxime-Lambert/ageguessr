# Explication des pistes : bulle par catégorie

Retour des joueurs : les indices ne sont pas assez clairs. Chaque carte de piste reçoit une
explication (genre d'indice, fonctionnement, exemple). Deux modes selon la façon
d'interagir : tooltip au survol à la souris, bulle collée à la carte au doigt. Branche
`feature/lead-help`, depuis `develop`. Décisions de l'utilisateur du 2026-11-12.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions

- **Un seul composant, une seule bulle, deux déclenchements** : on se fie à la façon
  d'interagir (`pointerType` de l'événement), pas à la taille d'écran. Une tablette avec
  souris ou un PC tactile basculent d'eux-mêmes.
  - **Souris** : survol de la carte entière, la bulle s'ouvre après 400 ms (pas de
    clignotement en traversant la grille) et se ferme quand la souris sort. Le clic sur la
    carte choisit toujours la piste.
  - **Doigt** (et clic sur ⓘ) : un tap sur ⓘ ouvre la bulle, un second tap, un tap
    ailleurs ou Échap la ferment.
  - **Clavier** : la bulle s'ouvre quand la carte reçoit le focus au clavier
    (`:focus-visible`) et se ferme avec Échap ou en quittant la carte.
  - Une seule bulle ouverte à la fois.
- **Structure** : la carte est un `<button>` (bascule `aria-pressed`) ; ⓘ ne peut pas être
  imbriqué dedans. Un conteneur relatif porte la carte et, à côté, le bouton ⓘ placé dans
  son coin. ⓘ : `aria-expanded`, `aria-controls`, libellé « Comment marche la piste X ».
  La carte pointe vers le texte par `aria-describedby` : un lecteur d'écran l'entend sans
  rien ouvrir.
- **Placement** : sous la carte, largeur `min(18rem, 100vw − 2rem)`, alignée à gauche, à
  droite pour la dernière colonne (pour ne pas sortir de l'écran), au-dessus des cartes
  suivantes. Pas de positionnement par ancre CSS (Firefox ne le gère pas).
- **Contenu** (FR et EN, `$localize`, traduit dans `messages.fr.xlf`) : une phrase sur le
  genre d'indice, la règle commune, un exemple dont l'écart en années est calculé à partir
  de l'année de la partie (jamais faux d'une année sur l'autre). Célébrités a sa propre
  règle (l'âge qu'a la célébrité cette année).

  | Catégorie | Genre (EN) | Exemple |
  |---|---|---|
  | Musique | An album, a song, a Eurovision or a Grammy win | Album « Thriller » — Michael Jackson (1982) |
  | Cinéma | A film released, or an Oscar, Palme d'or or César | Sortie du film « Titanic » (1997) |
  | Sport | A competition and its winner, a Ballon d'Or, the Olympics | Coupe du monde, pays hôte : France — vainqueur : France (1998) |
  | Science | A discovery or invention, a Nobel, a launch, a first flight | Premier vol : Concorde (1969) |
  | Monde | An event, a treaty, an election, a new state | Chute du mur de Berlin (1989) |
  | Littérature | A work published, or a literary prize | Parution : « Harry Potter à l'école des sorciers » — J. K. Rowling (1997) |
  | Célébrités | A famous person, as old this year as the person on the photo | Tom Hanks, né en 1956 : N ans cette année → N ans sur la photo |

  Règle commune : « Le fait date d'autant d'années que l'âge de la personne sur la
  photo. » Exemple rédigé : « « Sortie du film Titanic » : il y a 29 ans → 29 ans sur la
  photo. »
- **Pas de texte en double** : `pickHintHelp` (phrase sous le titre) reste, la bulle donne
  le détail par catégorie.

## Phase 1 : contenu
Status: Complete

- [x] `lead-help-texts.ts` (`features/game/`) : par catégorie, le genre, l'exemple (texte
      de l'indice, année), la règle (commune ou Célébrités), avec `$localize` et des ids
      `@@game.leadHelp.<categorie>.*` ; l'écart calculé depuis l'année de la partie.
- [x] Traductions FR dans `messages.fr.xlf`.
- [x] Tests unitaires : les 7 catégories ont un texte, l'écart suit l'année de la partie
      (2026 → Titanic 29 ans, 2030 → 33), Célébrités utilise sa propre règle.

### Verification Plan
- `cd frontend && pnpm test` → tout passe.
- Résultat (2026-11-13) : 65 fichiers, 434 tests verts.

### Phase Summary
`leadHelp(category, gameYear)` rend `{ kind, rule, example }`. Les exemples gardent leur
année, l'écart est calculé (`gameYear − year`). Ids `@@game.leadHelp.<categorie>.kind|hint`,
`@@game.leadHelp.rule`, `@@game.leadHelp.example`, et pour Célébrités `.rule`/`.example`
propres. FR dans `messages.fr.xlf` (après `game.category.literature`). `ng build --localize`
passe, mais le module n'étant importé nulle part il est éliminé : les placeholders FR
(`hint`, `name`, `year`, `years`, `age`) ne seront vraiment vérifiés qu'une fois branché en
phase 2 (vérifier « né en » dans `dist/frontend/browser/fr`). Année de la partie : la partie
du jour a `TodayGame.date` (YYYY-MM-DD) ; une série libre n'a pas de date → année UTC courante.

## Phase 2 : composant et intégration
Status: Complete

- [x] Composant `LeadCard` (`features/game/lead-card/`) : carte bascule + ⓘ + bulle,
      entrées `category`, `picked`, `disabled`, `open`, sorties `toggled`, `helpOpened`,
      `helpClosed`. Délai de survol 400 ms, `pointerType`, Échap, focus clavier.
- [x] `Round` : remplace la carte actuelle par `LeadCard`, garde l'état « quelle bulle est
      ouverte » (une seule), ferme au tap hors bulle (`document:pointerdown`) et quand
      l'étape change. Le focus initial reste sur la première carte, la sélection et
      l'envoi ne changent pas.
- [x] Placement : dernière colonne alignée à droite (2 colonnes mobile, 3 au-delà de
      `sm`), y compris la carte seule en dernière ligne (pleine largeur, alignée à gauche).
- [x] Tests :
  - interface (`lead-card.spec.ts`) : souris → ouverte après 400 ms (faux minuteurs),
    fermée à la sortie, pas d'ouverture si la souris repart avant ; doigt → tap ⓘ ouvre,
    second tap ferme ; Échap ferme ; `aria-expanded`, `aria-controls`,
    `aria-describedby` ; le clic sur la carte bascule toujours la piste sans ouvrir ;
  - `round.spec.ts` : une seule bulle ouverte, fermée par un tap ailleurs, la sélection
    de 3 pistes inchangée.
  Pas de test e2e : le parcours ne change pas (la bulle est facultative). Pas de backend.

### Verification Plan
- `cd frontend && pnpm test` et `pnpm exec ng lint` → tout passe.
- Capture d'écran unique (`shoot.mjs`, un seul navigateur) en largeur mobile, une bulle
  ouverte en colonne droite, pour vérifier qu'elle reste dans l'écran.
- Résultat (2026-11-13) : 66 fichiers, 448 tests verts ; lint OK ; `ng build --localize`
  OK, « né en … » et « Comment marche la piste » présents dans le bundle FR. Capture :
  pas de serveur de dev (WSL), donc page statique avec le balisage de la grille et le CSS
  compilé, à 360 px : bulle de la 2e carte de 56 à 344 px, pas de scroll horizontal,
  au-dessus des cartes suivantes.

### Phase Summary
`LeadCard` (`features/game/lead-card/`) : la carte bascule et le ⓘ sont frères dans
l'hôte `relative`, la bulle est toujours dans le DOM (`hidden` quand fermée) pour
`aria-describedby`. Écarts au plan :
- **Clavier** : ouverture sur `keyup` de Tab/Maj+Tab sur la carte, pas sur
  `:focus-visible`. Le focus programmatique de la 1re carte (après le 1er essai validé au
  clavier) aurait sinon ouvert une bulle à chaque manche.
- **Clic souris sur ⓘ** : ouvre seulement, ne referme pas. Le survol l'a souvent déjà
  ouverte en chemin, le clic ne doit pas fermer ce qu'il vient chercher. Doigt et clavier :
  bascule.
- **Échap** : écouté par `Round` sur `document`, quelle que soit l'origine de l'ouverture.
- **Année** : `Round.gameYear` = année UTC courante (le `RoundView` ne porte pas la date ;
  l'API tire depuis `round.gameDate()`), faux seulement dans un exemple, pour une partie
  commencée avant le Nouvel An.
- La sélection des pistes est `[role=group] button[aria-pressed]` dans les tests (le ⓘ
  est aussi un bouton du groupe).
⚠️ Budget : le bundle initial passe de 547,34 à 551,45 kB, au-dessus de l'avertissement à
550 kB (l'erreur est à 1 MB). Les icônes Lucide sont toutes dans `main` ; `LucideInfo` et
les nouvelles classes CSS suffisent à franchir le seuil. Décision à prendre avec
l'utilisateur (relever le seuil ou alléger).

## Phase 3 : documentation, revues et PR
Status: Not started

- [ ] `docs/roadmap.md` (ligne cochée + vérification en prod sur mobile et ordinateur),
      `docs/decisions/architecture.md` si une règle d'interface y a sa place (« Déroulé
      d'une manche »).
- [ ] Revue `architecture-reviewer` (pas de `code-reviewer` : ni auth, ni données, ni
      sourcing), puis `/pr`.

### Verification Plan
- `pnpm test` passe ; revue sans 🔴 ni 🟠 ouvert.

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_
