# Focus du champ de l'âge

Retour de joueurs : cliquer sur « ans » (ou à côté des chiffres) ne donnait pas la main
pour écrire. Le champ ne fait que 2,2 caractères de large au milieu de sa zone. Branche
`fix/age-input-focus`.

## Phase 1 : toute la zone donne le focus
Status: Complete

- [x] `AgeInput` : un `mousedown` entre les boutons − et +, hors du champ, met le curseur
      dans le champ (après les chiffres), sans blur intermédiaire ; rien si le champ est
      désactivé. Curseur texte sur la zone. Raccourci pointeur seul : le clavier et les
      lecteurs d'écran gardent le champ `spinbutton` comme cible.
- [x] Tests (`age-input.spec.ts`) : clic sur « years » et sur la zone, champ désactivé.

### Verification Plan
- `pnpm test` passe (pas de test e2e : pas de nouveau parcours).

### Phase Summary
Revue `architecture-reviewer` : aucun 🔴 ni 🟠, deux 🟡 appliqués (nom de variable, test du
champ désactivé). `pnpm test` passe.

## Final Recap
Toute la zone du champ de l'âge donne le focus.

## Deployment Plan
Avec la prochaine release `develop → main` (merge commit), rien à lancer.
