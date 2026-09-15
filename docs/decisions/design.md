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

**Valeurs de départ** (points de départ à affiner visuellement une fois les premiers
écrans stylés, pas des valeurs gravées) :

| Token | Sombre (défaut) | Clair |
|---|---|---|
| `--color-background` | `#17130F` | `#F5EEDF` |
| `--color-surface` | `#221C15` | `#FFFCF5` |
| `--color-foreground` | `#EDE3D2` | `#241D14` |
| `--color-muted` | `#9C8F79` | `#79705E` |
| `--color-border` | `#3A3021` | `#DDD0B5` |
| `--color-accent` | `#6467E8` | `#4548B0` |
| `--color-accent-foreground` | `#FFFFFF` | `#FFFFFF` |

**Raison des teintes chaudes de base** (background/surface/foreground/muted/border) :
évoque le papier vieilli, le bois sombre d'une chambre noire ou le carton d'un vieux
dossier d'archives — jamais un gris/noir neutre de dashboard.

**Raison de l'accent bleu-violet** : couleur de marque volontairement froide, en
contraste avec la chaleur des photos et du fond — inspirée de l'indigo, un pigment/encre
historique (loin d'être un bleu "tech" arbitraire), utilisée pour les éléments
d'interface (boutons, liens, focus) jamais pour les photos elles-mêmes.

### Couleurs fonctionnelles chaud/froid (mécanique de jeu)
**Décision** : palette dédiée, volontairement distincte de `--color-accent`, réservée à
l'indicateur de proximité de la tentative.

| Token | Sombre | Clair | Usage |
|---|---|---|---|
| `--color-hot` | `#E2572B` | `#C8431D` | tentative proche (rouge-orangé chaud) |
| `--color-cold` | `#3E92B0` | `#1F6E89` | tentative éloignée (bleu-sarcelle froid, pas indigo) |

**Raison** : `--color-accent` (indigo-violet) et `--color-cold` (bleu-sarcelle) doivent
rester visuellement distincts pour ne jamais laisser penser qu'un bouton normal est un
indicateur de jeu, ou l'inverse — deux familles de teintes bleues différentes plutôt que
de réutiliser l'accent de marque comme couleur "froide" du jeu.

### Traitement des photos
**Décision** : filtre CSS de vieillissement (désaturation partielle + léger sépia +
contraste augmenté) appliqué à toutes les photos de personnes, jamais de photo affichée
en couleurs neutres/non traitées.

**Raison** : renforce l'ambiance "archive" et uniformise visuellement des photos source
de qualité/année très hétérogènes (Wikimedia Commons).

**Piste à valider visuellement, pas encore engagée** : un duotone (ombres teintées
indigo foncé, tons clairs teintés crème) rapprocherait encore plus le traitement photo
de `--color-accent` — techniquement faisable en CSS pur mais avec des limites de rendu
navigateur (approximation via `filter: grayscale() sepia() hue-rotate()`, un vrai duotone
demande un filtre SVG) : à prototyper sur de vraies photos avant de trancher, pas figé
dans ce document.

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

### Voix "dossier d'enquête", suggestion à valider
**Piste, pas encore tranchée** : cadrer le vocabulaire du jeu autour de la métaphore
enquête/archives plutôt qu'un vocabulaire neutre de quiz — ex. "case" pour une partie,
"leads" pour les catégories d'indices, un ton un peu taquin/journalistique dans les
textes de résultat. Cohérent avec l'ambiance visuelle retenue, mais c'est une piste de
copywriting à valider séparément (pas une conséquence automatique de la charte
graphique) — à confirmer avant de l'appliquer partout.

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
icône/favicon dédiée plus tard sans remettre en cause l'identité typographique.

---

## À définir

- **Modèle économique** : gratuit, freemium, ou autre — non tranché
- **Mentions légales / CGU / RGPD** : à traiter avant toute ouverture publique —
  juridiction et libellés à adapter à une audience internationale (pas uniquement
  française)
