# FlowJsonCreator
Un utilitaire permettant de générer le mods.json pour FlowUpdater.

| Support    | [1.0](https://github.com/Paulem79/FlowJsonCreator/tree/848743dbb304ed1b5bb4dfe6a0a2741a01358c32) (Obsolète) | 1.1 |
|------------|-------------------------------------------------------------------------------------------------------------|-----|
| Curseforge | ✅*                                                                                                          | ✅*  |
| Modrinth   | ❌                                                                                                           | ✅   |
| Url        | ❌                                                                                                           | ✅   |

*Besoin de mettre sa clé d'API

## Compiler le projet

**Vous aurez besoin de Java 17 !**

Vous devrez créer le fichier `Config.java` dans
`src/main/java/io/github/paulem/fjc` et y mettre ceci :
```java
package io.github.paulem.fjc;

public class Config {
    public static final String CF_API_KEY = "Votre_Clé_D_API";
}
```
Remplacez le champ de `CF_API_KEY` par votre propre clé d'API, vous pouvez l'obtenir en suivant [ce tutoriel](https://support.curseforge.com/en/support/solutions/articles/9000208346-about-the-curseforge-api-and-how-to-apply-for-a-key).

Ensuite, compilez le projet avec :
```gradle
./gradlew build
```

Puis exécutez le fichier jar sorti dans `build/libs/FlowJsonCreator-x.x-all.jar` avec Java 17 :
```shell
java -jar build/libs/FlowJsonCreator-x.x-all.jar
```
(Si votre `java` est Java 17, évidemment)<br>
Enjoy !