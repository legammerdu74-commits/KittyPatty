# 🐱 Gamelle — appli Android + serveur maison

> « Gamelle » est un nom provisoire. Pour le changer : `android/app/src/main/res/values/strings.xml` (ligne `app_name`).

Une appli pour suivre la pâtée de ton chat :

- **Programmes** : « tous les jours / tous les 2 jours / tous les 3 jours… à telle heure, nourriture seule ou combinaison, telle quantité ».
- **Notification** à l'heure du repas, avec le type de pâtée et la quantité, et un bouton **Donné ✓**.
- **Encodage** : une fois l'heure passée, le repas apparaît dans « À encoder » ; tu indiques ce que tu as réellement donné (type, quantité, remarque, heure).
- **Accueil** : un calendrier du mois (pastilles : donné / à venir / à encoder), les totaux du mois (prévus, donnés, à venir), le détail d'un jour en touchant une case, puis les prochains repas. Les repas d'un même jour sont regroupés dans une seule case ; le menu du calendrier permet de filtrer par chat.
- **Plusieurs chats** : onglet « Chats » pour ajouter des profils ; en ouvrant un chat, consulte ses repas donnés, le total des grammes enregistrés et une estimation de son coût alimentaire pour le mois calendrier en cours (du 1er au dernier jour). Les repas partagés sont répartis entre les chats.
- **Alimentation personnalisée** : l’onglet « Alimentation » regroupe « Nourriture », « Combinaisons » et « Croquettes ». Poulet et Bœuf ainsi que leurs deux combinaisons sont préremplis. Ajoute ensuite les nourritures (nom, marque, prix et poids) ; les combinaisons réutilisent directement les aliments du catalogue et leur ordre peut être modifié. Chaque programme peut choisir une nourriture seule ou une combinaison.
- **Paquets de croquettes** : enregistre la marque, le goût, une spécificité (par exemple Urinary ou Senior), le prix, la date d’ajout et les chats qui partagent chaque paquet ; marque-le comme terminé pour connaître sa durée. Le coût mensuel des croquettes est réparti sur la durée enregistrée et équitablement entre les chats associés. Tant qu’un paquet est en cours, le calcul utilise sa durée écoulée jusqu’à aujourd’hui et reste donc une estimation.
- **Estimation des coûts** : dans les programmes, indique la quantité avec son unité (par exemple « 1 sachet » ou « 85 g »). Pour les anciennes quantités sans unité, si le nombre correspond exactement au poids renseigné du sachet, l’application le traite comme des grammes ; les autres nombres seuls restent des portions.
- **Photos facultatives** : photo de profil pour chaque chat et photo pour chaque repas (prise de vue ou galerie). Elles sont redimensionnées et compressées avant sauvegarde et synchronisation ; leur aperçu apparaît dans l'appli et sur la page du serveur.
- **Catalogue de nourriture** : ajoute des sachets/portions avec nom, marque, prix et poids depuis l’onglet « Alimentation » → « Nourriture » ; sélectionne-les seuls, dans une combinaison, dans un programme ou lors de l’encodage du repas réellement servi.
- **Serveur (facultatif)** : sur ton Debian, pour consulter et encoder aussi depuis ton PC. Sans serveur, l'appli marche très bien seule.
- **Page PC** : onglets pour les repas à encoder, les prochains repas, les programmes, l’alimentation et les chats ; dans le calendrier, les repas d'un même jour sont regroupés et filtrables par chat. Dans « Chats », choisis un profil pour voir ses repas prévus, ses statistiques, son coût estimé pour le mois calendrier en cours et son historique, avec la couleur du profil comme couleur d'accent de l'interface.

```
chat-pate/
├── android/   ← le projet de l'appli (à ouvrir avec Android Studio)
└── server/    ← le petit serveur + la page web pour le PC
```

---

## 1. Installer l'appli sur ton téléphone

> ⚠️ Je n'ai pas pu compiler l'appli de mon côté (pas d'outils Android dans mon environnement), donc elle n'a **pas encore été testée sur un vrai téléphone**. Si Android Studio affiche une erreur, copie-la-moi tel quel et je corrige.

1. Installe **Android Studio** (gratuit) : <https://developer.android.com/studio>
2. Ouvre Android Studio → **Open** → choisis le dossier `android`.
3. Attends que la barre de progression en bas finisse (« Gradle sync », la première fois ça télécharge pas mal de choses). Si une fenêtre propose de **mettre à jour** des versions (« Upgrade »), accepte.
4. Sur ton téléphone : **Paramètres → À propos du téléphone** → appuie 7 fois sur « Numéro de build » pour activer le mode développeur, puis **Options pour les développeurs → Débogage USB**.
5. Branche le téléphone en USB, accepte la demande sur l'écran, puis clique sur le bouton ▶ vert en haut d'Android Studio.

L'appli s'installe et s'ouvre. (Tu peux aussi générer un fichier `.apk` : menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**, puis l'envoyer sur ton téléphone.)

### Au premier lancement
- Accepte les **notifications**.
- Si un bandeau rouge parle d'**alarmes et rappels**, appuie sur « Autoriser » : c'est ce qui permet à la notification de sonner pile à l'heure.
- Dans **Paramètres → Applications → Pâtée du chat → Batterie**, choisis **Sans restriction** : certains téléphones (Xiaomi, Samsung, Huawei…) endorment sinon les alarmes.
- Dans l'appli, ⚙️ **Réglages → Envoyer une notification de test** pour vérifier.

### Utilisation
1. Onglet **Alimentation** : les nourritures Poulet et Bœuf et leurs combinaisons sont déjà prêtes. Ajoute tes autres nourritures dans « Nourriture », puis crée des séquences dans « Combinaisons » avec les aliments du catalogue. Dans **Programmes** → **+ Programme**, sélectionne une nourriture seule ou une combinaison.
2. Onglet **Repas** : les repas à venir. À l'heure, tu reçois la notification. Appuie dessus (ou sur « Encoder ») pour dire ce que tu as donné, ou sur **Donné ✓** directement dans la notification si tout s'est passé comme prévu.
3. « Passer » = rien n'a été donné à ce repas.
4. Onglet **Chats** : ouvre un profil pour consulter ses statistiques et son historique ; appuie sur un repas pour le corriger.

---

## 2. (Facultatif) Le serveur sur ton Debian

Il garde une copie des données et affiche une page web pour ton PC. Il n'a besoin que de Python 3 (déjà présent sur Debian), aucune installation.

```bash
# Copie le dossier "server" sur ton Debian, par exemple dans /opt/chat-pate/server
cd /opt/chat-pate/server
PATE_TOKEN=un-code-secret python3 server.py
```

Puis ouvre `http://IP-DU-SERVEUR:8765` dans le navigateur de ton PC.

Pour qu'il tourne en permanence (redémarre tout seul) :

```bash
sudo useradd --system --home /opt/chat-pate pate
sudo chown -R pate /opt/chat-pate
sudo cp chat-pate.service /etc/systemd/system/     # modifie PATE_TOKEN dans ce fichier
sudo systemctl daemon-reload
sudo systemctl enable --now chat-pate
```

Dans l'appli : ⚙️ **Réglages** → *Adresse du serveur* = `http://IP-DU-SERVEUR:8765` et *Code secret* = ton `PATE_TOKEN`, puis « Tester la synchronisation ».

### Comment ça se synchronise
- Le téléphone envoie ses données à chaque ouverture de l'appli, après chaque modification et à chaque alarme.
- Si tu encodes un repas depuis le PC, le téléphone le récupère à sa prochaine synchronisation (ouverture de l'appli ou alarme suivante) ; la notification d'un repas déjà encodé sur le PC est retirée.
- Si le serveur est éteint ou hors de portée, l'appli continue en local et rattrape plus tard.
- En cas de modification des deux côtés, la plus récente gagne.

### Sécurité
Le serveur est prévu pour ton **réseau local** (pas de HTTPS). Ne l'expose pas sur Internet tel quel. Pour y accéder hors de chez toi, passe plutôt par un VPN (WireGuard, Tailscale…).

---

## Ce qu'on peut ajouter ensuite
- Un 3ᵉ type de pâtée (une ligne à ajouter dans `Model.kt`, enum `Pate`).
- Statistiques (quantité par semaine, répartition poulet/bœuf).
- Un widget sur l'écran d'accueil.

---

## Mise à jour depuis la première version

1. Ferme Android Studio, puis remplace le contenu du dossier `android` par celui du nouveau zip (garde ton dossier si tu préfères, copie simplement les fichiers par-dessus).
2. Rouvre le projet, laisse le « Gradle sync » se faire, puis relance ▶ sur ton téléphone (installe **par-dessus** l'ancienne version : tes programmes et ton historique sont conservés).
3. Ton chat actuel est repris automatiquement dans la liste des chats ; renomme-le ou ajoute-en d'autres dans l'onglet « Chats ».
4. Si tu utilises le serveur, remplace aussi le dossier `server` sur ton Debian et relance-le (`sudo systemctl restart chat-pate`). Tant que tu ne l'as pas fait, l'appli marche mais les chats ne sont pas sauvegardés côté serveur.

---

## Logo et icônes

Le logo (un bol en forme de tête de chat) et toutes les icônes de l'appli (onglets, boutons) sont dessinés dans `design/icons.py` et convertis au format Android par `design/generate.py` :

```bash
python3 design/generate.py
```

Un aperçu est dans `design/apercu-logos.png`. L'icône de l'appli existe aussi en version monochrome (thème d'icônes d'Android 13+) et sert de petite icône de notification.
