# FlowJsonCreator
Un utilitaire permettant de générer le fichier mods.json pour FlowUpdater.

| Support    | [1.0](https://github.com/Paulem79/FlowJsonCreator/tree/848743dbb304ed1b5bb4dfe6a0a2741a01358c32) (Obsolète) | [1.1](https://github.com/Paulem79/FlowJsonCreator/tree/a9c8c82a96f1f9a9c7a8dbfbcfb9986868597cee) (Obsolète) | 1.2 |
|------------|-------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|-----|
| Curseforge | ✅*                                                                                                          | ✅*                                                                                                          | ✅   |
| Modrinth   | ❌                                                                                                           | ✅                                                                                                           | ✅   |
| Url        | ❌                                                                                                           | ✅                                                                                                           | ✅   |

*Requiert une clé d'API

---
## Utiliser le projet
**Vous aurez besoin de Java 17 ou plus pour lancer le projet !**<br>
Rendez vous [ici](https://github.com/Paulem79/FlowJsonCreator/releases/latest) pour obtenir la dernière version, puis, lancez-la avec :
```shell
java -jar FlowJsonCreator.jar
```
(Si votre `java` est Java 17 ou plus, évidemment)<br>
Et profitez !

### Arguments de lancement
Vous pouvez lancer le projet avec différents arguments.<br>

`--cfKey` permet de définir la clé d'API Curseforge (pour utiliser la navigation des fichiers).<br>
Vous pouvez l'obtenir en suivant [ce tutoriel](https://support.curseforge.com/en/support/solutions/articles/9000208346-about-the-curseforge-api-and-how-to-apply-for-a-key).

`--version` et `--loader` permettent respectivement de définir la version de Minecraft et le loader (Fabric, Forge, Quilt, Neoforge, etc.) que vous souhaitez pour filtrer les résultats des fichiers dans les recherches Curseforge et Modrinth.<br>
**(Ceci reste expérimental et n'est pas forcément précis)**

---
## Compiler le projet

**Vous aurez besoin de Java 17 ou plus !**<br>
**Vous aurez aussi besoin de [modrinth-api](https://github.com/Paulem79/Java-ModrinthAPI/tree/main) ! Suivez sa section "Publish to maven local" pour l'ajouter au projet !**

Ensuite, compilez le projet avec :
```shell
./gradlew build
```

Puis exécutez le fichier jar sorti dans `build/libs/FlowJsonCreator-x.x-all.jar` avec Java 17 ou plus :
```shell
java -jar build/libs/FlowJsonCreator-x.x-all.jar
```
(Si votre `java` est Java 17 ou plus, évidemment)<br>