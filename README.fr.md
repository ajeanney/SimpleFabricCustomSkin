# SimpleFabricCustomSkin

Distribuez des skins depuis des fichiers PNG locaux sur un serveur Fabric : un opérateur donne à un joueur un skin du dossier `skins`, il apparaît immédiatement, survit aux redémarrages et est rendu quand on l'enlève.

Il gère :

- Le rafraîchissement immédiat du skin en jeu (aucune reconnexion nécessaire).
- La restauration du skin personnel d'origine du joueur à l'effacement.
- La persistance entre les déconnexions et les redémarrages du serveur.
- L'autocomplétion des noms de fichiers dans la commande.

## Commandes

Toutes les commandes demandent la permission d'opérateur.

- Définir un skin :
  - `/skin set <joueur> <fichier_sans_png>`
- Effacer un skin :
  - `/skin clear <joueur>`
  - `/skin clear` (soi-même quand la commande est lancée par un joueur)

Avec `/skin set`, `<fichier_sans_png>` est suggéré à partir des fichiers du dossier skins.

## Fichiers de skin

Placez les skins PNG dans :

- `skins/` dans le dossier de travail du serveur.

Prérequis :

- Extension : `.png`
- Résolution : `64x64` ou `64x32`

Exemple :

- Fichier : `skins/knight.png`
- Commande : `/skin set Steve knight`

## Persistance

Les attributions sont stockées dans :

- `config/simplefabriccustomskin-player-skins.json`

À la connexion d'un joueur, les skins enregistrés sont réappliqués automatiquement.

## Comportement technique

- Set :
  - Télécharge/déduit les données de texture depuis le PNG local.
  - Applique la texture au profil visé.
  - Envoie des paquets de rafraîchissement pour que le changement soit visible immédiatement.
- Clear :
  - Supprime l'attribution personnalisée.
  - Restaure la texture du skin d'origine mise en cache.

## Développement

Compiler :

```bash
./gradlew build
```

Lancer les GameTests :

```bash
./gradlew runGameTest
```
