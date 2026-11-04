# Maquettes de la refonte visuelle (2026-11)

Maquettes statiques qui servent de décision pour la refonte (`plans/design-overhaul.md`).
Ce ne sont pas du code de production : elles chargent Fraunces et Inter depuis Google
Fonts et les icônes Lucide depuis unpkg pour rester autonomes, alors que l'app
auto-héberge ses polices.

- `index.html` : les mêmes écrans dans deux directions, `?d=a` (« Archives soignées ») et
  `?d=b` (« Contemporain épuré »). Chaque cadre passe en version mobile par container
  query, sans changer le balisage.
- `shoot.mjs` : captures sans suite Playwright ni serveur de dev (voir l'en-tête du
  script). `timeout 240 node docs/decisions/mockups/shoot.mjs mockups` capture chaque
  écran, `… prod` capture les pages publiques du site.

Ouvrir depuis WSL : `explorer.exe "$(wslpath -w docs/decisions/mockups/index.html)"`.

## Crédits des photos

Photos recadrées et teintées à partir de leur version servie par Ageguessr :

- `photos/raikkonen.jpg` : Kimi Räikkönen, par Morio, licence
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), Wikimedia Commons.
  La version modifiée est diffusée sous la même licence.
- `photos/lavigne.jpg` : Avril Lavigne, par Justin Higuchi (Los Angeles, CA, USA),
  licence [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/), Wikimedia Commons.
