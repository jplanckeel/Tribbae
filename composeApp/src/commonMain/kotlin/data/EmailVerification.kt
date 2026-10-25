package data

/** Confirmation de l'adresse email du compte (mêmes messages que lib/api.ts côté web). */
object EmailVerification {
    /** Erreur du serveur quand l'adresse du compte n'est pas confirmée. */
    const val NOT_VERIFIED = "email address not verified"
    const val VERIFY_FIRST = "Confirmez d'abord votre adresse email : ouvrez le lien reçu par email, ou demandez-en un nouveau avec « Renvoyer l'email »."
    const val PUBLISH_LOCKED = "Confirmez votre adresse email pour publier."
}
