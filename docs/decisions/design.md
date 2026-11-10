# Décisions de design — AgeGuessr

Charte graphique et décisions produit transverses, posées avant toute implémentation
visuelle. Complète `docs/decisions/architecture.md` (qui fixe déjà Tailwind, Vitest,
Signals) sans le contredire.

## Refonte visuelle (2026-11) : direction A, « Archives soignées »

**Décision** (2026-11-04) : l'identité archives est gardée. C'était son exécution qui
rendait le site brouillon : pas de hiérarchie, un menu à plat, des boutons indigo
saturé sur fond brun. Référence visuelle : `docs/decisions/mockups/index.html?d=a`.
La direction B (« Contemporain épuré », `?d=b`) a été écartée et reste dans le fichier
pour mémoire. Plan : `plans/design-overhaul.md`.

**Ce qui change** (les sections plus bas restent valables sauf mention contraire) :
- **Bouton principal crème** (`primary` = `foreground`, texte = `background`) au lieu
  de l'indigo. L'indigo devient un accent fin : lien, focus, élément sélectionné,
  progression, icône de piste.
- **Or pour le score** (`--color-score`), réservé aux points et à l'onglet actif. Il
  sert aussi au tampon « Affaire classée » du verdict, la seule fantaisie graphique.
- **La photo est un tirage monté** : un carton crème autour, une légende en Fraunces
  italique, avec le crédit (auteur, licence, Commons) en bas à droite du carton. Le
  carton est légèrement incliné sur desktop, droit sur mobile.
- **L'indice est une coupure de journal** : un fond papier (`--color-paper*`) avec un
  morceau de ruban adhésif, et le choix FR/EN de la langue des pistes dans son coin.
- **Desktop en deux colonnes** (photo à gauche, dossier à droite), largeur maximale de
  1 120 px. **Mobile en une colonne**, avec le menu en panneau latéral.
- **Navigation** : Dossier du jour et Enquête libre dans l'en-tête, avec Se connecter /
  S'inscrire, ou un menu de compte (compte, historique, admin, préférences,
  déconnexion). La langue du site, la langue des pistes et le thème vont dans ce menu
  (ou dans le panneau mobile), plus dans l'en-tête ni sous le jeu.
- **Saisie de l'âge** : un grand champ avec des boutons − et +, sans les flèches natives
  du champ numérique.
- Le vocabulaire « enquête » (affaire, piste, dossier) est confirmé.

**Nouveaux tokens (thème sombre)**, vérifiés WCAG AA le 2026-11-04 :

| Token | Valeur | Usage |
|---|---|---|
| `--color-background` | `#15120E` | fond |
| `--color-surface` | `#1F1A14` | cartes |
| `--color-surface-2` | `#2B241B` | survol, élément sélectionné, fond d'avatar |
| `--color-foreground` | `#EFE6D4` | texte, fond du bouton principal |
| `--color-muted` | `#A3967F` | texte secondaire |
| `--color-input-border` | `#75695A` | bord des champs |
| `--color-accent` | `#A3A5F2` | accent indigo, plus doux |
| `--color-score` | `#E0AD4F` | score, onglet actif, tampon |
| `--color-paper` / `-foreground` / `-muted` | `#E9DFC9` / `#2A2116` / `#6E604A` | coupure, carton photo |

**Règle de contraste** : `input-border` (2.86:1) et `danger` (4.13:1) ne passent pas sur
`surface-2`. Ce fond ne porte donc jamais un champ ni un message d'erreur. Le thème
clair de la direction A est défini en Phase 2, avec la même règle (luminosité ajustée,
teintes inchangées).

## Revue produit (2026-11-09)

Corrections après une revue sur la prod (captures, une série jouée) :
- **Aide de la piste** : elle disait « un fait de l'année où la photo a été prise », alors
  que l'API tire un fait de (année du jour − âge sur la photo). Elle dit désormais « il y a
  autant d'années que l'âge de la personne sur la photo », et la révélation ajoute
  « il y a 66 ans » à côté de l'année.
- **Règles** : une carte « Comment jouer » (3 lignes) au-dessus de la 1re manche, cachée
  pour de bon par « Compris » (stockage local, simple confort). Les règles restent sur la
  page `/how-to-play` (pied de page) et sous le choix de longueur de l'Enquête libre.
  Aucun nombre de manches par jour : c'est un réglage serveur.
- **2e essai** : le champ part d'un an après le 1er essai, du côté indiqué, au lieu d'être
  vide. − et + ajustent depuis là, et Entrée seule ne renvoie jamais un âge déjà faux.
- **Champ d'âge vide** : « ? » en `muted` au lieu d'un « 00 » pâle, pris pour une valeur.
- **Enquête libre** : une durée estimée sur chaque longueur (~30 s par affaire).
- **Signaler la photo** : sur sa propre ligne sous le bouton principal, plus à côté.
- **Profil** : calendrier des 30 derniers jours (jours UTC), or plus ou moins plein selon
  le score, contour pointillé pour un jour pas terminé. Redit la liste pour l'œil
  seulement : une phrase pour les lecteurs d'écran.
- **En-tête invité** : un globe et le code de langue (« FR ») au lieu d'une icône de
  réglages sans libellé. Le nom accessible garde le code visible (« Preferences · FR »).

## Identité visuelle

### Ambiance : archives / vieille photographie
**Décision** : identité inspirée d'une vieille photo d'archive ou d'une coupure de
journal — fond sombre par défaut façon salle d'archives/chambre noire, photos au
traitement vieilli, accent bleu-violet froid qui tranche avec la chaleur des photos
(façon annotation à l'encre sur un document ancien), thème clair disponible en option.

**Raison** : colle directement au concept du jeu (deviner l'âge à partir d'une vieille
photo et d'un fait historique de la même année) plutôt qu'une identité générique de jeu
mobile. Le contraste chaud (photos) / froid (interface) donne une identité visuelle
immédiatement reconnaissable sans logo illustré.

### Couleurs
**Décision** : tokens sémantiques définis en CSS (Tailwind v4, `@theme` dans
`frontend/src/styles.css`), jamais de couleur Tailwind brute directement dans les
composants — même règle que pour toute future librairie de composants.

**Valeurs** (vérifiées WCAG AA, voir « Accessibilité des couleurs » ; on peut encore
les affiner visuellement tant que le test de contraste passe) :

| Token | Sombre (défaut) | Clair | Usage |
|---|---|---|---|
| `--color-background` | `#17130F` | `#F5EEDF` | fond de page, fond des champs |
| `--color-surface` | `#221C15` | `#FFFCF5` | cartes |
| `--color-foreground` | `#EDE3D2` | `#241D14` | texte principal |
| `--color-muted` | `#9C8F79` | `#746B5A` | texte secondaire |
| `--color-border` | `#3A3021` | `#DDD0B5` | séparateurs, bord des cartes (décoratif) |
| `--color-input-border` | `#736855` | `#928772` | bord des champs de formulaire |
| `--color-accent` | `#7779EB` | `#4548B0` | liens, boutons, focus |
| `--color-accent-foreground` | `#17130F` | `#FFFFFF` | texte posé sur l'accent |

**Raison des teintes chaudes de base** (background/surface/foreground/muted/border) :
évoque le papier vieilli, le bois sombre d'une chambre noire ou le carton d'un vieux
dossier d'archives — jamais un gris/noir neutre de dashboard.

**Raison de l'accent bleu-violet** : couleur de marque volontairement froide, en
contraste avec la chaleur des photos et du fond — inspirée de l'indigo, un pigment/encre
historique (loin d'être un bleu "tech" arbitraire), utilisée pour les éléments
d'interface (boutons, liens, focus) jamais pour les photos elles-mêmes.

**Raison du texte sombre sur l'accent en thème sombre** : pour que les liens indigo se
lisent sur le fond sombre (4.5:1), l'accent sombre est assez clair pour que le blanc posé
dessus ne passe plus (3.7:1). Les boutons du thème sombre portent donc un texte
`#17130F` (5:1). En clair, l'accent est foncé et le texte reste blanc.

### Couleur d'erreur
**Décision** : `--color-danger`, une brique mate, pour les messages d'erreur des
formulaires et les actions destructrices (zone de suppression du compte).

| Token | Sombre | Clair |
|---|---|---|
| `--color-danger` | `#CC675D` | `#B23A2E` |
| `--color-danger-foreground` | `#17130F` | `#FFFFFF` |

**Raison** : distincte de `--color-hot` (rouge-orangé vif). Une erreur ne doit jamais se
lire comme un indicateur de jeu « tu chauffes », et inversement. Le texte sur fond
`danger` suit la même règle que l'accent : sombre en thème sombre, blanc en clair.

### Couleurs fonctionnelles chaud/froid (mécanique de jeu)
**Décision** : palette dédiée, volontairement distincte de `--color-accent`, réservée à
l'indicateur de proximité de la tentative.

| Token | Sombre | Clair | Usage |
|---|---|---|---|
| `--color-hot` | `#E2572B` | `#C8431D` | tentative proche (rouge-orangé chaud) |
| `--color-hot-foreground` | `#17130F` | `#FFFFFF` | texte posé sur `hot` |
| `--color-cold` | `#3E92B0` | `#1F6E89` | tentative éloignée (bleu-sarcelle froid, pas indigo) |
| `--color-cold-foreground` | `#17130F` | `#FFFFFF` | texte posé sur `cold` |

**Raison** : `--color-accent` (indigo-violet) et `--color-cold` (bleu-sarcelle) doivent
rester visuellement distincts pour ne jamais laisser penser qu'un bouton normal est un
indicateur de jeu, ou l'inverse — deux familles de teintes bleues différentes plutôt que
de réutiliser l'accent de marque comme couleur "froide" du jeu.

**Tokens libres depuis le 2026-11-02** : le chaud/froid a été abandonné (voir
`architecture.md`, « Déroulé d'une manche »). Le jeu n'indique plus que le sens, avec une
flèche et un texte (« Older than 30 ») en couleurs neutres, sans aucune distance. Les
tokens `hot` et `cold` restent définis et vérifiés pour le contraste, mais aucun écran
ne les utilise. Les réutiliser seulement pour un sens qui ne suggère pas une proximité.

### Accessibilité des couleurs
**Décision** : toute paire de tokens utilisée ensemble passe WCAG 2.x AA, en sombre comme
en clair :
- **4.5:1** pour le texte (1.4.3) : `foreground`, `muted`, `accent`, `danger` sur
  `background` et `surface` ; chaque `-foreground` sur sa couleur ;
- **3:1** pour les éléments graphiques porteurs d'information (1.4.11) :
  `input-border`, `hot` et `cold` sur `background` et `surface`.

**Deux bordures** : `--color-border` reste décoratif (séparateurs, cartes), où 1.4.11 ne
s'applique pas. Le monter à 3:1 alourdirait tous les séparateurs et casserait la douceur
du thème sombre. Le bord d'un champ de saisie, lui, indique où cliquer : il utilise
`--color-input-border`, à 3:1.

**Ajuster sans changer l'identité** : une correction de contraste ne touche que la
luminosité du token, jamais sa teinte (indigo, brique, papier).

**Garde-fou** : `frontend/src/app/design-tokens.spec.ts` lit `styles.css` et vérifie
toutes ces paires dans les deux thèmes. Un nouveau token utilisé en paire s'ajoute à sa
liste. Exemptés, comme le prévoit WCAG : les boutons désactivés (`disabled:opacity-50`)
et le wordmark (logotype).

### Traitement des photos
**Décision** : filtre CSS de vieillissement (désaturation partielle + léger sépia +
contraste augmenté) appliqué à toutes les photos de personnes, jamais de photo affichée
en couleurs neutres/non traitées.

**Raison** : renforce l'ambiance "archive" et uniformise visuellement des photos source
de qualité/année très hétérogènes (Wikimedia Commons).

**Duotone : décision reportée à la Semaine 4** (roadmap, « Interface de jeu »). Un
duotone (ombres teintées indigo foncé, tons clairs teintés crème) rapprocherait le
traitement photo de `--color-accent`. Il est faisable en CSS, avec des limites : `filter:
grayscale() sepia() hue-rotate()` n'en donne qu'une approximation, un vrai duotone
demande un filtre SVG. On ne tranche que sur de vraies photos Commons, donc une fois la
Semaine 2 livrée. D'ici là, le filtre de vieillissement ci-dessus fait foi.

**Tranché le 2026-11-04 : pas de duotone.** Sur les vraies photos de la maquette, le
filtre léger (`sepia(0.18) contrast(1.06) saturate(0.85)`, utilitaire `archive-photo`)
suffit : c'est le carton crème du tirage qui porte l'ambiance archives, et un duotone
indigo rendrait les visages moins lisibles, alors que le joueur doit justement les
juger. La photo n'est jamais recadrée (`CasePhoto`), pour ne rien cacher de ce qu'il
faut évaluer.

### Typographie
**Décision** : Fraunces (variable) pour les titres, le wordmark et les moments
éditoriaux (indices, révélation du résultat) ; Inter pour l'UI et le texte courant.

**Raison** : Fraunces est un serif moderne à l'esprit volontairement suranné (empattements
doux, contraste marqué) — cohérent avec l'ambiance "vieille photo/archive" sans tomber
dans un pastiche de machine à écrire illisible à petite taille. Inter reste le choix
pragmatique pour l'UI (haute lisibilité à petite taille, déjà éprouvé).

**Implémentation** : self-host via `@fontsource-variable/fraunces` et
`@fontsource/inter` (npm), pas de lien Google Fonts CDN — évite une requête tierce non
nécessaire et le partage d'IP avec Google.

### Formes
**Décision** : coins peu arrondis, plus proches d'un cadre/document que d'une app
mobile moderne — `--radius-sm: 0.25rem` (inputs, badges), `--radius-md: 0.375rem`
(boutons), `--radius-lg: 0.5rem` (cards). Les photos elles-mêmes gardent un cadre net
(bordure fine type passe-partout, coin non arrondi ou à peine) plutôt qu'un habillage
"carte mobile" à coins très arrondis.

**Raison** : cohérent avec l'ambiance photo d'archive/document — des coins très arrondis
casseraient l'effet "photo encadrée".

### Icônes
**Décision** : `lucide-angular` (même bibliothèque d'icônes que Lucide, binding officiel
Angular).

**Raison** : open-source, catalogue large, tree-shakable — même choix que la plupart des
projets Tailwind actuels, aucune dépendance à une charte d'icônes propriétaire.

### Composants
**Remplacé le 2026-11-04** : `@angular/cdk` (Menu, Dialog, Overlay, A11y) et nos propres
composants dans `frontend/src/app/shared/ui/`. Pas de Spartan : il est construit sur le
CDK et ajoute luxon, clsx et tw-animate-css pour des composants qu'on restylerait
entièrement. Pas de Material ni de PrimeNG : leur look écraserait l'identité. Détails
dans `plans/design-overhaul.md`. La décision d'origine suit, pour mémoire.

**Ce que contient `shared/ui`** (2026-11-09, fin de la refonte) :
- `Button` : directive sur `button` et `a` (`primary` crème, `secondary`, `ghost`,
  `danger` ; tailles `md` et `sm` ; `block` pour la pleine largeur). Une directive plutôt
  qu'un composant, pour garder la sémantique native de l'élément.
- `Field` + `TextInput` (`appInput`) : libellé, aide et erreur liés au champ pour les
  lecteurs d'écran. `controlId` fixe l'id du champ (`email`, `password`…) quand des liens
  ou les tests de bout en bout le visent ; sans lui, chaque champ a un id généré.
  `appInput` s'applique aussi aux `textarea`, qui gardent leur hauteur en lignes.
- `AgeInput` (saisie de l'âge, − et +), `Segmented` (choix court en groupe radio),
  `Dropdown` (disclosure, pas un menu ARIA : le panneau mêle liens et groupes radio),
  `Page` (trois largeurs : `narrow` formulaire, `default` lecture, `wide` jeu).
- Du CDK, seuls `Dialog` et le positionnement d'`Overlay` servent aujourd'hui (panneau
  mobile). Les utilitaires CSS `card`, `eyebrow` et `menu-item` complètent ces
  composants dans `styles.css`.
- Les pages légales gardent une feuille de style de lecture partagée
  (`features/legal/legal-page.css`) : environ 70 caractères par ligne, titres de section
  séparés par un filet, mêmes marges que `Page`.

**Décision d'origine** : Tailwind pur + tokens custom, pas de librairie de composants
(Shadcn/Radix) au démarrage — déjà acté dans `docs/decisions/architecture.md`.

**Conséquence à assumer** : l'accessibilité des composants complexes (modals, selects,
dropdowns) — focus trap, `aria-*`, navigation clavier — doit être écrite à la main.
Ajouter Radix primitives ultérieurement si un composant complexe le justifie reste
possible sans tout casser (mêmes tokens CSS).

---

## Ton éditorial

### Voix "dossier d'enquête", adoptée à l'essai
**Décision** (2026-10-21) : le vocabulaire du jeu suit la métaphore enquête/archives,
avec un ton un peu taquin et journalistique dans les textes de résultat. C'est un
**essai** : chaque terme a un équivalent neutre de repli.

| Notion | Terme enquête | Repli neutre |
|---|---|---|
| Une personne à deviner | *case* | *round* |
| La partie du jour (3 personnes) | *today's file* | *daily game* |
| Catégorie d'indice | *lead* | *hint* |
| Révélation du résultat | *verdict* / *case closed* | *result* |
| Récapitulatif final | *case report* | *summary* |
| Mode hors partie du jour | *free investigation* | *series* |
| Série partagée entre amis | *challenge* | *challenge* |

**Raison** : cohérent avec l'ambiance visuelle archives. Mais un vocabulaire thématique
peut gêner la compréhension d'un jeu qu'on découvre : d'où l'essai plutôt qu'un choix
définitif.

**Critère pour garder ou abandonner** : au premier playtest, un joueur qui découvre le
jeu comprend sans explication ce que sont un *case* et un *lead*. Une hésitation
suffit pour repasser au vocabulaire neutre.

**Règle d'implémentation (Semaine 4)** : tout le vocabulaire de jeu vient d'un seul
fichier. Il n'est jamais écrit en dur dans les templates. Abandonner l'essai revient à
modifier ce fichier, sans toucher aux écrans.

**Mise en œuvre** (PR `feature/daily-guess`) : `frontend/src/app/features/game/game-vocabulary.ts`.
`INVESTIGATION_VOICE` et `NEUTRAL_VOICE` ont les mêmes clés (vérifié par un test), et la
constante `VOICE` choisit entre les deux.

**Périmètre** : seulement les écrans de jeu. Compte, connexion et inscription restent
en vocabulaire neutre (*Sign in* reste *Sign in*) : ce sont des parcours standards où
l'on cherche des repères familiers.

### Bilingue anglais / français, ton direct
**Décision** (2026-11-02, remplace « anglais seul ») : le site joueur existe en anglais
et en français (voir « Internationalisation » dans `docs/decisions/architecture.md`).
Ton direct dans les deux langues. En français :
- **vouvoiement** partout ;
- la **voix enquête** est gardée, avec son repli neutre, dans `game-vocabulary.ts` :

  | Notion | Enquête | Repli neutre |
  |---|---|---|
  | Une personne à deviner | *Affaire* | *Manche* |
  | La partie du jour | *Le dossier du jour* | *La partie du jour* |
  | Catégorie d'indice | *Piste* | *Indice* |
  | Révélation du résultat | *Affaire classée* | *Résultat* |
  | Récapitulatif final | *Rapport d'enquête* | *Récapitulatif* |

- le sens de l'âge se dit **« Plus de 30 ans » / « Moins de 30 ans »** : neutre en genre
  (on ne connaît pas celui de la personne) et sans distance ;
- chaque langue est nommée dans sa propre langue dans les sélecteurs (*English*,
  *Français*).

Les traductions sont à relire au fil de l'usage et à corriger au cas par cas dans
`messages.fr.xlf`.

---

## Responsive

### Mobile-first
**Décision** : chaque écran est conçu d'abord pour mobile, puis adapté au desktop
(`sm:`/`md:`/`lg:` en ajout, jamais en retrait).

**Raison** : un quiz quotidien de quelques minutes est un usage typiquement mobile
(pause, transport, avant de dormir) plutôt qu'une session posée au bureau.

---

## Accès

### Jeu sans compte
**Décision** : le quiz quotidien est jouable sans connexion — voir
`docs/decisions/architecture.md`. Le compte est présenté comme un moyen de garder son
historique et comparer avec ses amis, jamais comme un mur d'entrée.

---

## Nom et logo

### Wordmark typographique, pas d'icône
**Décision** : "AgeGuessr" en Fraunces comme seul élément de marque pour le MVP, pas
d'icône ni de mascotte.

**Raison** : suffisant pour lancer, rapide à livrer, laisse la porte ouverte à une
icône dédiée plus tard sans remettre en cause l'identité typographique.

### Casse du nom : « AgeGuessr »
**Décision** (2026-11-09) : la marque s'écrit « AgeGuessr » partout où un humain la lit
(wordmark, titres d'onglet, emails, pages légales, texte de partage), et le domaine
« AgeGuessr.app » dans les textes. Les identifiants techniques (package `app.ageguessr`,
classes `AgeguessrApi*`, noms d'images Docker, URL) restent en minuscules ou inchangés.

**Raison** : « Ageguessr » se lit mal, les deux majuscules découpent « Age » et
« Guessr ». Le nom de domaine ne tient pas compte de la casse : `AgeGuessr.app` mène au
même site, mais la barre d'adresse du navigateur l'affiche toujours en minuscules,
c'est la norme des URL. Le titre d'onglet porte donc la casse à la place.

### Favicon
**Décision** : le « A » de Fraunces, converti en tracé, en `--color-accent` sur un carré
`--color-background` sombre aux coins à peine arrondis. Trois fichiers dans
`frontend/public/` : `favicon.svg`, `favicon.ico` (32×32) et `apple-touch-icon.png`
(180×180, carré plein).

**Raison** : prolonge le wordmark plutôt que d'inventer un symbole. Le glyphe vient de
l'instance `opsz` 9 et graisse 700 : à 16-32 px, les déliés fins de l'optique « titre »
disparaîtraient. Le tracé (et non un `<text>`) rend l'icône indépendante des polices
installées. Fraunces est sous SIL OFL 1.1, qui autorise d'en intégrer les contours dans
un logo. Méthode de régénération : `frontend/public/README.md`.

---

## À définir

- **Modèle économique** : gratuit, freemium, ou autre — non tranché
