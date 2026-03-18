package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class FaqItem(
    val question: String,
    val answer: String
)

private data class FaqSection(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<FaqItem>
)

private val faqSections = listOf(
    FaqSection(
        title = "Démarrage",
        icon = Icons.Filled.RocketLaunch,
        color = Color(0xFF4FC3F7),
        items = listOf(
            FaqItem(
                "Comment créer un compte ?",
                "Rendez-vous dans l'onglet Profil, puis appuyez sur \"Se connecter\". Choisissez \"Créer un compte\", renseignez votre email et un mot de passe d'au moins 8 caractères."
            ),
            FaqItem(
                "Puis-je utiliser l'app sans compte ?",
                "Oui, vous pouvez ajouter et gérer vos idées localement sans compte. La synchronisation entre appareils et les fonctionnalités sociales (explorer, likes, commentaires) nécessitent un compte."
            ),
            FaqItem(
                "Comment synchroniser mes données ?",
                "Une fois connecté, vos données se synchronisent automatiquement. Vous pouvez aussi forcer la synchronisation en tirant vers le bas sur l'écran principal."
            )
        )
    ),
    FaqSection(
        title = "Idées & Listes",
        icon = Icons.Filled.Lightbulb,
        color = Color(0xFFFFD700),
        items = listOf(
            FaqItem(
                "Comment ajouter une idée ?",
                "Appuyez sur le bouton \"+\" dans la barre de navigation en bas. Remplissez le titre (obligatoire), l'URL, la description et choisissez une catégorie."
            ),
            FaqItem(
                "Quelles sont les catégories disponibles ?",
                "Tribbae propose 5 catégories : 💡 Idée, 🎁 Cadeau, 🏃 Activité, 📅 Événement et 🍳 Recette. Chaque catégorie a sa propre couleur pour une identification rapide."
            ),
            FaqItem(
                "Comment organiser mes idées en dossiers ?",
                "Dans l'onglet \"Mes idées\", appuyez sur \"Nouveau dossier\". Donnez-lui un nom, une icône et une couleur. Vous pouvez ensuite assigner vos idées à ce dossier lors de leur création ou modification."
            ),
            FaqItem(
                "Comment rendre une idée publique ?",
                "Lors de la création ou modification d'une idée, changez la visibilité de \"Privé\" à \"Public\". Les idées publiques apparaissent dans l'Explorer pour les autres utilisateurs."
            ),
            FaqItem(
                "Comment utiliser les rappels ?",
                "Pour les idées de type Événement, vous pouvez activer un rappel en cochant l'option lors de la création. Définissez une date d'événement et l'app vous enverra une notification."
            )
        )
    ),
    FaqSection(
        title = "Explorer & Communauté",
        icon = Icons.Filled.Explore,
        color = Color(0xFF81C784),
        items = listOf(
            FaqItem(
                "Qu'est-ce que l'Explorer ?",
                "L'Explorer affiche les idées publiques partagées par tous les utilisateurs de Tribbae. C'est l'endroit idéal pour trouver de l'inspiration pour vos activités, recettes et cadeaux."
            ),
            FaqItem(
                "Comment sauvegarder une idée de la communauté ?",
                "Dans le détail d'une idée publique, appuyez sur \"Sauvegarder\". Vous pouvez choisir de l'ajouter directement à vos idées ou dans un dossier spécifique."
            ),
            FaqItem(
                "Comment liker une idée ?",
                "Dans le détail d'une idée, appuyez sur le bouton cœur ❤️ en bas de l'écran. Vous devez être connecté pour liker."
            ),
            FaqItem(
                "Comment suivre un utilisateur ?",
                "Dans le détail d'une idée publique, appuyez sur le bouton \"Suivre\" à côté du nom de l'auteur."
            )
        )
    ),
    FaqSection(
        title = "IA & Suggestions",
        icon = Icons.Filled.AutoAwesome,
        color = Color(0xFF8B5CF6),
        items = listOf(
            FaqItem(
                "Comment fonctionne la génération d'idées par IA ?",
                "Depuis l'écran d'ajout, appuyez sur \"Générer avec l'IA\". Décrivez ce que vous cherchez (ex: \"activités pour enfants de 5 ans en plein air\") et l'IA vous propose des idées personnalisées."
            ),
            FaqItem(
                "La génération IA est-elle gratuite ?",
                "Oui, la génération d'idées par IA est incluse dans l'application. Vous devez être connecté pour l'utiliser."
            )
        )
    ),
    FaqSection(
        title = "Compte & Confidentialité",
        icon = Icons.Filled.Security,
        color = Color(0xFFEF4444),
        items = listOf(
            FaqItem(
                "Comment changer mon mot de passe ?",
                "La modification du mot de passe n'est pas encore disponible directement dans l'app. Contactez le support à support@tribbae.com pour obtenir de l'aide."
            ),
            FaqItem(
                "Mes données sont-elles sécurisées ?",
                "Oui. Vos données sont chiffrées en transit (HTTPS) et stockées de manière sécurisée. Vos idées privées ne sont jamais visibles par les autres utilisateurs."
            ),
            FaqItem(
                "Comment supprimer mon compte ?",
                "Pour supprimer votre compte et toutes vos données, contactez-nous à support@tribbae.com. La suppression est définitive et irréversible."
            )
        )
    )
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    // Garde en mémoire quelle question est ouverte (null = aucune)
    var expandedKey by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFF6B35), Color(0xFFF97316))
                        )
                    )
                    .padding(top = 48.dp, bottom = 28.dp, start = 20.dp, end = 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Bouton retour
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Help, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Column {
                            Text(
                                "Aide & FAQ",
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                            Text(
                                "Trouvez des réponses à vos questions",
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sections FAQ
            faqSections.forEach { section ->
                FaqSectionBlock(
                    section = section,
                    expandedKey = expandedKey,
                    onToggle = { key -> expandedKey = if (expandedKey == key) null else key }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Contact support
            Spacer(Modifier.height(8.dp))
            ContactSupportCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FaqSectionBlock(
    section: FaqSection,
    expandedKey: String?,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Titre de section
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(section.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(section.icon, null, tint = section.color, modifier = Modifier.size(16.dp))
            }
            Text(
                section.title,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7280), letterSpacing = 0.5.sp
            )
        }

        // Items
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Column {
                section.items.forEachIndexed { index, item ->
                    val key = "${section.title}_$index"
                    val isExpanded = expandedKey == key
                    val isLast = index == section.items.lastIndex

                    FaqItemRow(
                        item = item,
                        isExpanded = isExpanded,
                        accentColor = section.color,
                        onClick = { onToggle(key) }
                    )

                    if (!isLast) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color(0xFFF3F4F6)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItemRow(
    item: FaqItem,
    isExpanded: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.question,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                tint = if (isExpanded) accentColor else Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                item.answer,
                fontSize = 13.sp, color = Color(0xFF6B7280),
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun ContactSupportCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF97316).copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFF97316).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Email, null, tint = Color(0xFFF97316), modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Vous n'avez pas trouvé votre réponse ?",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)
                )
                Text(
                    "Contactez-nous : support@tribbae.com",
                    fontSize = 12.sp, color = Color(0xFF6B7280)
                )
            }
        }
    }
}

