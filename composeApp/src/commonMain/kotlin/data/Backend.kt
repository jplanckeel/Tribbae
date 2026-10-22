package data

/**
 * Adresse du backend. Production par défaut ; une build de dev peut viser un
 * backend local : `./gradlew :composeApp:assembleDebug -Ptribbae.apiUrl=http://10.0.2.2:8080`
 * (10.0.2.2 = la machine hôte vue depuis l'émulateur). Fixée au démarrage par MainActivity.
 */
object Backend {
    const val PRODUCTION = "https://tribbae.fr"
    var url: String = PRODUCTION
}
