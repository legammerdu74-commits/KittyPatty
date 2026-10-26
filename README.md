# 🐱 KittyPatty

**Les repas des chats, sans devoir s’en souvenir à chaque fois.** KittyPatty aide à planifier les repas, suivre ce qui a été servi et garder un œil sur le budget. L’application Android fonctionne seule ; un petit serveur facultatif ajoute la synchronisation et une interface pour ordinateur.

> 🧪 **Version actuelle : 0.11 bêta** — [Télécharger l’APK](https://github.com/legammerdu74-commits/KittyPatty/releases/tag/v0.11). L’APK est une version de test signée en mode débogage.

## 📱 L’application

- **Programmes de repas** : choisis les chats, la nourriture, la quantité, l’heure et la fréquence. Alterne les aliments au fil des repas grâce aux combinaisons, dans l’ordre de ton choix.
- **Rappels** : reçois une notification à l’heure prévue. Marque le repas comme donné directement depuis la notification ou encode ce qui a réellement été servi, avec quantité, note et photo facultative.
- **Calendrier et historique** : visualise les repas du mois, filtre le calendrier par chat, retrouve les repas donnés et corrige ou supprime un encodage. Les repas futurs supprimés peuvent être remis au programme.
- **Profils de chats** : ajoute une photo et une couleur à chaque profil, puis consulte son historique, les quantités enregistrées et son estimation de coût pour le mois civil.
- **Catalogue alimentaire** : enregistre les nourritures avec leur marque, leur prix et leur poids. Réutilise-les dans les programmes ou compose des séquences comme « bœuf → poulet ».
- **Suivi des croquettes** : note la marque, le goût, la spécificité (par ex. Urinary ou Senior), le prix, la date d’ajout/ouverture et les chats qui partagent le paquet. Termine-le pour connaître sa durée.
- **Coût mensuel estimé** : les repas partagés sont répartis entre les chats concernés. Le prix d’un paquet de croquettes est amorti sur sa durée et partagé entre les chats sélectionnés ; pour un paquet encore ouvert, le calcul utilise la durée écoulée.
- **Photos** : ajoute une photo de profil ou de repas depuis l’appareil photo ou la galerie. Les images sont réduites avant d’être enregistrées.

> 💡 Pour éviter les ambiguïtés de quantité, saisis par exemple `1 sachet` ou `85 g`. Une ancienne quantité sans unité qui correspond exactement au poids du produit est considérée comme des grammes.

### Quelques moustaches en images

Les captures pourront être ajoutées plus tard dans `docs/screenshots/`. Dépose les images dans ce dossier puis active ou remplace les lignes ci-dessous.

<!--
![Accueil et calendrier](docs/screenshots/accueil.png)
![Programme de repas](docs/screenshots/programmes.png)
![Profil d’un chat](docs/screenshots/profil-chat.png)
![Suivi des croquettes](docs/screenshots/croquettes.png)
-->

## 🖥️ Serveur facultatif

Le serveur Python conserve une copie des données et sert une page web pour consulter le calendrier, les repas, les programmes, l’alimentation et les profils depuis un navigateur. Il n’utilise **aucune dépendance Python externe** ; Python 3.8 ou supérieur suffit.

### Lancer rapidement

Sur la machine qui hébergera le serveur :

```bash
cd server
PATE_TOKEN='choisis-un-secret-long' python3 server.py
```

Puis ouvre `http://IP-DU-SERVEUR:8765` sur ton réseau local. Dans l’application, va dans **Réglages**, renseigne cette adresse et le même code secret, puis teste la synchronisation.

### Lancer avec systemd (Debian)

Le dépôt contient un exemple de service dans [`server/chat-pate.service`](server/chat-pate.service). Adapte son utilisateur, son chemin d’installation et surtout `PATE_TOKEN`, puis installe-le :

```bash
sudo cp server/chat-pate.service /etc/systemd/system/chat-pate.service
sudo systemctl daemon-reload
sudo systemctl enable --now chat-pate
```

Commandes utiles : `sudo systemctl status chat-pate` et `sudo journalctl -u chat-pate -f`.

### Configuration

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `PATE_HOST` | `0.0.0.0` | Adresse d’écoute du serveur |
| `PATE_PORT` | `8765` | Port web et API |
| `PATE_TOKEN` | aucun | Code partagé avec l’application et la page web |
| `PATE_DATA` | `server/data/state.json` | Emplacement du fichier de données JSON |

La synchronisation a lieu à l’ouverture de l’application et après les modifications. En cas de conflit, la version la plus récente gagne. Si le serveur est indisponible, l’application continue à fonctionner localement et synchronisera plus tard.

> 🔐 Le serveur est conçu pour un **réseau de confiance** : HTTP n’est pas chiffré et, sans `PATE_TOKEN`, l’API est ouverte. Choisis un secret et ne publie pas le serveur directement sur Internet. Pour un accès à distance, préfère un VPN (WireGuard, Tailscale…) ou ajoute un proxy HTTPS correctement configuré.

## 🛠️ Développer et compiler

1. Ouvre le dossier `android/` dans Android Studio, avec un JDK 17.
2. Pour compiler depuis un terminal : `cd android` puis `gradlew.bat assembleDebug` sous Windows, ou `./gradlew assembleDebug` sous Linux/macOS.
3. L’APK de débogage est créée dans `android/app/build/outputs/apk/debug/`.

### Mises à jour Android

Au démarrage, KittyPatty vérifie les releases GitHub et propose l’installation si une version plus récente est publiée ; une vérification manuelle est aussi disponible dans les réglages. Le dépôt et ses releases doivent être publics. Chaque release doit contenir un APK et un tag de version croissant correspondant à `versionName` (par exemple `v0.12`) ; incrémente aussi `versionCode`. Android demande l’autorisation d’installer des applications provenant de KittyPatty, puis affiche sa confirmation habituelle. Pour que la mise à jour fonctionne, l’APK doit garder le même certificat de signature que la version installée.

Le projet Android est en Kotlin avec Jetpack Compose ; le serveur et son interface web se trouvent dans `server/`.

## 📂 Structure

```text
android/                 Application Android
server/server.py         API, synchronisation et serveur web
server/static/index.html Interface navigateur
server/chat-pate.service Exemple de service systemd
docs/screenshots/        Captures d’écran à ajouter
```

## 🐾 À propos

KittyPatty est un projet bêta : les idées, retours et rapports de bugs sont les bienvenus via les [issues GitHub](https://github.com/legammerdu74-commits/KittyPatty/issues). Les rappels dépendent des autorisations de notifications et d’alarmes exactes accordées à Android.
