# Phase 2 — Tests sur téléphone

## Installer

APK : `apk/IGN-Trail-phase2.apk` (34 Mo). Elle remplace la version de la Phase 1 sans la désinstaller, avec la même méthode d'installation que pour la Phase 1 :

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r apk/IGN-Trail-phase2.apk
```

**Recommandé avant la première vraie sortie :**

- Au départ, l'écran de choix d'activité propose un lien **« Réglages »** pour retirer IGN Trail de l'optimisation de batterie. Certains constructeurs (Xiaomi, Huawei, Samsung, Oppo…) coupent sinon les applications en arrière-plan.
- Sur certains téléphones, il faut aussi autoriser le **démarrage automatique** ou désactiver la **mise en veille de l'application** dans les réglages du constructeur.

## Scénarios

| # | Scénario | Résultat attendu |
|---|---|---|
| 1 | « Démarrer ma randonnée » | Choix Marche / Randonnée / Course, avec le dernier profil présélectionné et le délai de pause automatique. Au premier départ, Android demande l'autorisation des notifications. |
| 2 | Démarrage | Le panneau sombre apparaît (Distance, Durée, Vitesse, D+), avec les boutons Pause et Terminer. La barre d'onglets se masque et une notification « Randonnée en cours » s'affiche avec un chronomètre. |
| 3 | Marche de 10 min | Le tracé rouge se dessine sous vos pas. La distance augmente de façon réaliste et la vitesse affichée est cohérente. |
| 4 | **Écran verrouillé 10 min en marchant** (test C) | Au déverrouillage, la distance a continué d'augmenter et le tracé est continu. |
| 5 | Autre application ouverte, puis retour | L'enregistrement a continué. |
| 6 | Mode avion pendant la marche | L'enregistrement continue (le GPS ne dépend pas d'Internet). |
| 7 | **Arrêt de 1 min** (pause automatique, délai 30 s) | Bandeau orange « Pause automatique ». Le temps de pause augmente et le temps de déplacement ne bouge plus. |
| 8 | Repartir | Reprise automatique en quelques secondes. |
| 9 | **Pause manuelle**, puis marcher 2 min | La distance n'augmente **pas** et la pause **ne se lève jamais toute seule** (test E). |
| 10 | Reprendre | Le tracé repart, **sans ligne droite** depuis l'endroit de la pause. |
| 11 | Toucher la poignée du panneau | Toutes les statistiques s'affichent : les trois chronomètres, les vitesses, l'allure, les altitudes et le D+/D-. |
| 12 | Bouton plein écran | Carte maximale et bandeau Distance / Durée / Vitesse en haut. La flèche ˄ ramène le panneau. |
| 13 | Notification : bouton Pause / Reprendre | Agit sur l'enregistrement. |
| 14 | Terminer | Confirmation demandée, puis résumé avec un nom modifiable. Le tracé reste affiché sur la carte jusqu'à la fermeture du résumé. |
| 15 | Onglet Parcours | La randonnée apparaît dans la liste. |
| 16 | **Redémarrer le téléphone pendant un enregistrement** (test H) | Après le redémarrage, une notification « Randonnée interrompue » apparaît. En ouvrant l'application, on peut choisir **Reprendre** (la coupure est comptée à part) ou **Terminer**. |
| 17 | Terminer → « Supprimer sans enregistrer… » | Deuxième confirmation, puis la sortie est effacée. |

## Points à observer pendant une vraie sortie

- La **distance** comparée à un repère connu (panneau de sentier, autre application).
- Le **D+** comparé au dénivelé annoncé sur la carte. En GPS seul, un écart de 10 % est normal. Le baromètre sera exploité en Phase 6.
- **L'autonomie** : le pourcentage de batterie consommé par heure.
