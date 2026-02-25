package data

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val icon: FolderIcon = FolderIcon.FOLDER,
    val color: FolderColor = FolderColor.BLUE
)

@Serializable
data class Link(
    val id: String,
    val title: String,
    val url: String,
    val description: String = "",
    val category: LinkCategory,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val ageRange: String = "",
    val location: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val eventDate: Long? = null,
    val reminderEnabled: Boolean = false,
    val rating: Int = 0,           // 0 = pas de note, 1-5 étoiles
    val ingredients: List<String> = emptyList(),  // pour les recettes
    val favorite: Boolean = false
)

@Serializable
enum class LinkCategory(val label: String, val iconName: String) {
    IDEE("Idée", "Lightbulb"),
    CADEAU("Cadeau", "CardGiftcard"),
    ACTIVITE("Activité", "DirectionsRun"),
    EVENEMENT("Événement", "Event"),
    RECETTE("Recette", "Restaurant")
}

@Serializable
enum class FolderIcon {
    FOLDER, STAR, HEART, BOOKMARK, HOME, WORK, TRAVEL, SHOPPING,
    // Nature / Outdoor
    CAMPING, PARK, BEACH, MOUNTAIN, FOREST,
    // Activités
    SPORTS, MUSIC, GAMES, PALETTE, MOVIE,
    // Nourriture
    RESTAURANT, CAKE, COFFEE,
    // Ambiance
    CANDLE, PIRATE, MAGIC, PARTY, ROCKET,
    // Famille
    BABY, PETS, SCHOOL
}

@Serializable
enum class FolderColor { BLUE, GREEN, ORANGE, PURPLE, RED, TEAL, PINK, YELLOW }

@Serializable
data class Child(
    val id: String,
    val name: String,
    val birthDate: Long  // timestamp en ms de la date de naissance
)
