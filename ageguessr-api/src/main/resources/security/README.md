# Mots de passe interdits

`common-passwords.txt` est la liste des mots de passe courants refusés par
`features/auth/PasswordPolicy` (voir `docs/decisions/architecture.md`, « Politique de mot
de passe »).

- **Source** : `Passwords/Common-Credentials/Pwdb_top-1000000.txt` du dépôt
  [SecLists](https://github.com/danielmiessler/SecLists), licence MIT
  (Copyright (c) 2018 Daniel Miessler). Le million de mots de passe les plus fréquents
  dans les fuites de données publiques, classés par fréquence.
- **Filtrage** : on ne garde que les entrées d'au moins 12 caractères (une entrée plus
  courte est déjà refusée par la longueur minimale), en minuscules et dédupliquées.
  La longueur se compte en caractères, pas en octets : il faut un `awk` qui gère
  l'UTF-8 (GNU awk) et une locale UTF-8, sinon des entrées cyrilliques de 9 caractères
  passeraient le filtre. `PasswordPolicy` repasse de toute façon chaque entrée en
  minuscules au chargement, accents compris (le `tr` ne traite que l'ASCII).

Pour régénérer le fichier :

```sh
curl -sSfLO https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Common-Credentials/Pwdb_top-1000000.txt
tr -d '\r' < Pwdb_top-1000000.txt | LC_ALL=C.UTF-8 awk 'length($0)>=12' | tr A-Z a-z \
  | LC_ALL=C sort -u > common-passwords.txt
```
