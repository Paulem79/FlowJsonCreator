# FlowJsonCreator
Un utilitaire permettant de générer le fichier mods.json pour FlowUpdater.

| Support    | 1.0 | 1.1 | 1.2 | 1.3 |
|------------|-----|-----|-----|-----|
| Curseforge | ✅*  | ✅*  | ✅   | ✅   |
| Modrinth   | ❌   | ✅   | ✅   | ✅   |
| Url        | ❌   | ✅   | ✅   | ✅   |

*Requiert une clé d'API

---
## Utiliser le projet
**Vous aurez besoin de Java 17 ou plus pour lancer le projet !**<br>
Rendez vous [ici](https://github.com/Paulem79/FlowJsonCreator/releases/latest) pour obtenir la dernière version, puis, lancez-la avec :
```shell
java -jar FlowJsonCreator-x-x.jar
```
(Si votre `java` est Java 17 ou plus, évidemment)<br>
Et profitez !

### Arguments de lancement
`--cfKey` permet de définir la clé d'API Curseforge (pour utiliser la navigation des fichiers).<br>
Vous pouvez l'obtenir en suivant [ce tutoriel](https://support.curseforge.com/en/support/solutions/articles/9000208346-about-the-curseforge-api-and-how-to-apply-for-a-key).

---
## Compiler le projet

**Vous aurez besoin de Java 17 ou plus !**<br>

Ensuite, compilez le projet avec :
```shell
./gradlew shadowJar
```

Puis exécutez le fichier jar sorti dans `build/libs/FlowJsonCreator-x.x.jar` avec Java 17 ou plus :
```shell
java -jar build/libs/FlowJsonCreator-x.x.jar
```
(Si votre `java` est Java 17 ou plus, évidemment)<br>