package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import viewmodel.LinkViewModel

@Composable
fun ShoppingListScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier
) {
    val allLinks by viewModel.repository.links.collectAsState()
    val recipes = allLinks.filter { it.category == LinkCategory.RECETTE }

    // Recettes sélectionnées pour la liste de courses
    var selectedRecipeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Ingrédients cochés (déjà dans le panier)
    var checkedIngredients by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Vue : sélection ou liste de courses
    var showShoppingList by remember { mutableStateOf(false) }

    // Liste consolidée des ingrédients des recettes sélectionnées
    val shoppingIngredients = remember(selectedRecipeIds, allLinks) {
        allLinks
            .filter { it.id in selectedRecipeIds }
            .flatMap { recipe ->
                recipe.ingredients.map { ingredient ->
                    ShoppingItem(ingredient = ingredient, recipeName = recipe.title)
                }
            }
            .groupBy { it.ingredient.trim().lowercase() }
            .map { (key, items) ->
                // Regroupe les doublons, liste les recettes sources
                MergedIngredient(
                    name = items.first().ingredient.trim(),
                    recipes = items.map { it.recipeName }.distinct()
                )
            }
            .sortedBy { it.name }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text("Liste de courses", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                Text(
                    if (selectedRecipeIds.isEmpty()) "Sélectionnez des recettes"
                    else "${selectedRecipeIds.size} recette${if (selectedRecipeIds.size > 1) "s" else ""} · ${shoppingIngredients.size} ingrédient${if (shoppingIngredients.size > 1) "s" else ""}",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        if (recipes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Restaurant, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = OrangeLight.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucune recette", color = TextSecondary, fontSize = 16.sp)
                    Text("Ajoutez des recettes pour créer votre liste", fontSize = 13.sp, color = TextSecondary)
                }
            }
            return
        }

        // Tabs : Recettes / Liste
        TabRow(
            selectedTabIndex = if (showShoppingList) 1 else 0,
            containerColor = CardColor,
            contentColor = Orange
        ) {
            Tab(
                selected = !showShoppingList,
                onClick = { showShoppingList = false },
                text = { Text("Recettes") },
                icon = { Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = showShoppingList,
                onClick = { showShoppingList = true },
                enabled = selectedRecipeIds.isNotEmpty(),
                text = { Text("Courses") },
                icon = { Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (!showShoppingList) {
            // Vue sélection des recettes
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    if (selectedRecipeIds.isNotEmpty()) {
                        Button(
                            onClick = { showShoppingList = true; checkedIngredients = emptySet() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voir ma liste (${shoppingIngredients.size} ingrédients)", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                items(recipes) { recipe ->
                    val selected = recipe.id in selectedRecipeIds
                    RecipeSelectionCard(
                        recipe = recipe,
                        selected = selected,
                        onToggle = {
                            selectedRecipeIds = if (selected)
                                selectedRecipeIds - recipe.id
                            else
                                selectedRecipeIds + recipe.id
                        }
                    )
                }
            }
        } else {
            // Vue liste de courses
            Column(modifier = Modifier.fillMaxSize()) {
                // Actions en haut
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val uncheckedCount = shoppingIngredients.count { it.name !in checkedIngredients }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Orange.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "$uncheckedCount restant${if (uncheckedCount > 1) "s" else ""}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Orange, fontWeight = FontWeight.Medium, fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (checkedIngredients.isNotEmpty()) {
                        TextButton(onClick = { checkedIngredients = emptySet() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Réinitialiser", fontSize = 13.sp)
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(shoppingIngredients) { item ->
                        val checked = item.name in checkedIngredients
                        IngredientShoppingRow(
                            item = item,
                            checked = checked,
                            onToggle = {
                                checkedIngredients = if (checked)
                                    checkedIngredients - item.name
                                else
                                    checkedIngredients + item.name
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeSelectionCard(recipe: Link, selected: Boolean, onToggle: () -> Unit) {
    val recipeColor = CategoryColors[LinkCategory.RECETTE.name] ?: Orange
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) recipeColor.copy(alpha = 0.12f) else CardColor
        ),
        elevation = CardDefaults.cardElevation(if (selected) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image ou icône
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
            ) {
                if (recipe.imageUrl.isNotBlank()) {
                    NetworkImage(
                        url = recipe.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(recipeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Restaurant, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(recipe.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${recipe.ingredients.size} ingrédient${if (recipe.ingredients.size > 1) "s" else ""}",
                    fontSize = 12.sp, color = TextSecondary
                )
                if (recipe.rating > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    StarRating(rating = recipe.rating, starSize = 14)
                }
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = recipeColor)
            )
        }
    }
}

@Composable
private fun IngredientShoppingRow(item: MergedIngredient, checked: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) Color(0xFFF5F5F5) else CardColor
        ),
        elevation = CardDefaults.cardElevation(if (checked) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox custom
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) Orange else Color.Transparent)
                    .then(
                        if (!checked) Modifier.background(Color.Transparent) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(OrangeLight.copy(alpha = 0.2f))
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = if (checked) FontWeight.Normal else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (checked) TextSecondary else TextPrimary,
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                )
                if (item.recipes.size > 1 || !checked) {
                    Text(
                        item.recipes.joinToString(", "),
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = if (checked) 0.5f else 0.8f)
                    )
                }
            }
        }
    }
}

// --- Composable réutilisable pour saisir les ingrédients ---

@Composable
fun IngredientsField(
    ingredients: List<String>,
    onIngredientsChange: (List<String>) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.FormatListBulleted, contentDescription = null,
                tint = Orange, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ingrédients", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            if (ingredients.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(shape = CircleShape, color = Orange) {
                    Text(
                        "${ingredients.size}",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Champ de saisie
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Ex: 200g farine, 2 œufs...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Orange) },
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = {
                        val trimmed = input.trim()
                        if (trimmed.isNotEmpty() && trimmed !in ingredients) {
                            onIngredientsChange(ingredients + trimmed)
                        }
                        input = ""
                    }) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Ajouter", tint = Orange)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                focusedContainerColor = CardColor,
                unfocusedContainerColor = CardColor
            )
        )

        // Liste des ingrédients
        AnimatedVisibility(visible = ingredients.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ingredients.forEachIndexed { index, ingredient ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp).clip(CircleShape).background(Orange)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(ingredient, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(
                                onClick = { onIngredientsChange(ingredients - ingredient) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Supprimer",
                                    tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (index < ingredients.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

// --- Modèles internes ---
private data class ShoppingItem(val ingredient: String, val recipeName: String)
data class MergedIngredient(val name: String, val recipes: List<String>)

