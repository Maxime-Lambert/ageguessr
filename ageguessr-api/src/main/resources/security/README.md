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

Pour régénérer le fichier :

```sh
curl -sSfLO https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Common-Credentials/Pwdb_top-1000000.txt
tr -d '\r' < Pwdb_top-1000000.txt | LC_ALL=C awk 'length($0)>=12' | LC_ALL=C tr A-Z a-z \
  | LC_ALL=C sort -u > common-passwords.txt
```
