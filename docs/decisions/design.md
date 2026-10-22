# Décisions de design — Ageguessr

Charte graphique et décisions produit transverses, posées avant toute implémentation
visuelle. Complète `docs/decisions/architecture.md` (qui fixe déjà Tailwind, Vitest,
Signals) sans le contredire.

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
**Décision** : Tailwind pur + tokens custom, pas de librairie de composants
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

**Raison** : cohérent avec l'ambiance visuelle archives. Mais un vocabulaire thématique
peut gêner la compréhension d'un jeu qu'on découvre : d'où l'essai plutôt qu'un choix
définitif.

**Critère pour garder ou abandonner** : au premier playtest, un joueur qui découvre le
jeu comprend sans explication ce que sont un *case* et un *lead*. Une hésitation
suffit pour repasser au vocabulaire neutre.

**Règle d'implémentation (Semaine 4)** : tout le vocabulaire de jeu vient d'un seul
fichier. Il n'est jamais écrit en dur dans les templates. Abandonner l'essai revient à
modifier ce fichier, sans toucher aux écrans.

**Périmètre** : seulement les écrans de jeu. Compte, connexion et inscription restent
en vocabulaire neutre (*Sign in* reste *Sign in*) : ce sont des parcours standards où
l'on cherche des repères familiers.

### Anglais, ton direct
**Décision** (déjà actée) : anglais comme langue principale, ton direct adapté à une
audience internationale — voir `docs/decisions/architecture.md`.

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
**Décision** : "Ageguessr" en Fraunces comme seul élément de marque pour le MVP, pas
d'icône ni de mascotte.

**Raison** : suffisant pour lancer, rapide à livrer, laisse la porte ouverte à une
icône dédiée plus tard sans remettre en cause l'identité typographique.

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
- **Mentions légales / CGU / RGPD** : à traiter avant toute ouverture publique —
  juridiction et libellés à adapter à une audience internationale (pas uniquement
  française)
