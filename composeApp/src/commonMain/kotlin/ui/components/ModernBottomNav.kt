package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NavDestination {
    HOME,
    EXPLORE,
    ADD,
    MY_IDEAS,
    PROFILE
}

data class NavItem(
    val destination: NavDestination,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,
    val label: String,
    val isSpecial: Boolean = false
)

@Composable
fun ModernBottomNav(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        NavItem(NavDestination.HOME, Icons.Outlined.Home, Icons.Filled.Home, "Accueil"),
        NavItem(NavDestination.EXPLORE, Icons.Outlined.Explore, Icons.Filled.Explore, "Explorer"),
        NavItem(NavDestination.ADD, Icons.Filled.AddCircle, Icons.Filled.AddCircle, "Ajouter", isSpecial = true),
        NavItem(NavDestination.MY_IDEAS, Icons.Outlined.Bookmark, Icons.Filled.Bookmark, "Mes idées"),
        NavItem(NavDestination.PROFILE, Icons.Outlined.Person, Icons.Filled.Person, "Profil")
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    if (item.isSpecial) {
                        // Espace vide pour le bouton central
                        Spacer(modifier = Modifier.size(56.dp))
                    } else {
                        // Boutons normaux
                        val isSelected = currentDestination == item.destination
                        Column(
                            modifier = Modifier
                                .clickable { onNavigate(item.destination) }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.iconFilled else item.iconOutlined,
                                contentDescription = item.label,
                                tint = if (isSelected) Color(0xFFF97316) else Color(0xFF9CA3AF),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFF97316) else Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
            }
        }
        
        // Bouton central flottant au-dessus
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(56.dp)
                .background(
                    color = Color(0xFFF97316),
                    shape = CircleShape
                )
                .clickable { onNavigate(NavDestination.ADD) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Ajouter",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
