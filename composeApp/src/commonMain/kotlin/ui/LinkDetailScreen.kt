package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Link
import data.Comment
import data.CommentRepository
import data.FollowRepository
import data.SessionManager
import kotlinx.coroutines.launch
import ui.components.CategoryPatternBackground
import ui.components.getCategoryColor
import ui.components.getCategoryEmoji
import ui.components.getCategoryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDetailScreen(
    link: Link,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
    onSaveToMyList: ((Link, String?) -> Unit)? = null,
    folders: List<data.Folder> = emptyList(),
    followRepository: FollowRepository? = null,
    sessionManager: SessionManager? = null,
    commentRepository: CommentRepository? = null
) {
    val categoryColor = getCategoryColor(link.category)
    val coroutineScope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(link.favorite) }
    var liked by remember { mutableStateOf(link.likedByMe) }
    var currentLikeCount by remember { mutableStateOf(link.likeCount) }
    var isFollowing by remember { mutableStateOf(false) }
    var isLoadingFollow by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    // Comments state
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var isLoadingComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var isPostingComment by remember { mutableStateOf(false) }

    // Check if we're following this user
    LaunchedEffect(link.ownerId) {
        if (followRepository != null && link.ownerId.isNotEmpty() && sessionManager?.getUserId() != link.ownerId) {
            followRepository.isFollowing(link.ownerId).onSuccess { following ->
                isFollowing = following
            }
        }
    }

    // Load comments
    LaunchedEffect(link.id) {
        if (commentRepository != null) {
            isLoadingComments = true
            commentRepository.getComments(link.id).onSuccess { loadedComments ->
                comments = loadedComments
                isLoadingComments = false
            }.onFailure {
                println("ERROR: Failed to load comments: ${it.message}")
                isLoadingComments = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {
            // ── Hero image ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (link.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = link.imageUrl,
                        contentDescription = link.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CategoryPatternBackground(
                        category = link.category,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.70f)
                                )
                            )
                        )
                )

                // Back button
                Box(
                    modifier = Modifier
                        .padding(start = 20.dp, top = 48.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Top-right action buttons
                Row(
                    modifier = Modifier
                        .padding(end = 20.dp, top = 48.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Save/Bookmark
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable { saved = !saved },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (saved) Color(0xFFF97316) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (!readOnly) {
                        // Edit
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(onClick = onEdit),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Edit, "Modifier", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        // Delete
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.9f))
                                .clickable { showDeleteDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Delete, "Supprimer", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Category badge + title at bottom of hero
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(20.dp), color = categoryColor) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(getCategoryEmoji(link.category), fontSize = 11.sp)
                                Text(
                                    getCategoryLabel(link.category),
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (readOnly) Color(0xFF10B981).copy(alpha = 0.85f) else Color(0xFF6B7280).copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (readOnly) Icons.Filled.Public else Icons.Filled.Lock,
                                    null, tint = Color.White, modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    if (readOnly) "Public" else "Privé",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = link.title,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, lineHeight = 26.sp
                    )
                }
            }

            // ── White card overlapping hero ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White
            ) {
                Column {
                    // ── Stats row ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        if (link.rating > 0) {
                            StatItem(
                                top = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                        Text(link.rating.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                                    }
                                },
                                label = "Note"
                            )
                        }
                        StatItem(
                            top = { Text(currentLikeCount.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937)) },
                            label = "J'aime"
                        )
                        StatItem(
                            top = { Text(link.commentCount.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937)) },
                            label = "Commentaires"
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3F4F6))

                    // ── Author row ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = Brush.linearGradient(listOf(categoryColor, categoryColor.copy(alpha = 0.8f))),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = link.ownerDisplayName.firstOrNull()?.uppercase() ?: "?",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    link.ownerDisplayName.ifBlank { "Anonyme" },
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)
                                )
                                if (link.ownerIsAdmin) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEF4444)) {
                                        Text(
                                            "ADMIN", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (link.createdAt.isNotEmpty()) {
                                    "Partagé le ${formatDateFr(link.createdAt)}"
                                } else "Partagé récemment",
                                fontSize = 11.sp, color = Color(0xFF9CA3AF)
                            )
                        }
                        if (followRepository != null && sessionManager?.getUserId() != link.ownerId && link.ownerId.isNotEmpty()) {
                            Button(
                                onClick = {
                                    isLoadingFollow = true
                                    coroutineScope.launch {
                                        if (isFollowing) {
                                            followRepository.unfollow(link.ownerId).onSuccess {
                                                isFollowing = false; isLoadingFollow = false
                                            }.onFailure {
                                                println("ERROR: Failed to unfollow: ${it.message}")
                                                isLoadingFollow = false
                                            }
                                        } else {
                                            followRepository.follow(link.ownerId).onSuccess {
                                                isFollowing = true; isLoadingFollow = false
                                            }.onFailure {
                                                println("ERROR: Failed to follow: ${it.message}")
                                                isLoadingFollow = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoadingFollow,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) Color(0xFF9CA3AF) else categoryColor
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    if (isFollowing) "Abonné" else "Suivre",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3F4F6))

                    // ── URL section ──
                    if (link.url.isNotEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, categoryColor.copy(alpha = 0.25f)),
                                color = Color.White
                            ) {
                                Column {
                                    // Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(categoryColor.copy(alpha = 0.08f))
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Link, null, tint = categoryColor, modifier = Modifier.size(14.dp))
                                        Text(
                                            "LIEN DE L'IDÉE",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = categoryColor, letterSpacing = 0.5.sp
                                        )
                                    }
                                    // URL row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenUrl?.invoke(link.url) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.OpenInNew, null, tint = categoryColor, modifier = Modifier.size(14.dp))
                                        Text(
                                            link.url,
                                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            color = categoryColor, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF3F4F6),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Filled.ContentCopy, "Copier", tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Description ──
                    if (link.description.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = if (link.url.isEmpty()) 16.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "DESCRIPTION",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp
                            )
                            Text(
                                link.description,
                                fontSize = 14.sp, color = Color(0xFF374151), lineHeight = 24.5.sp
                            )
                        }
                    }

                    // ── Infos pratiques (2-col grid) ──
                    val hasInfos = link.price.isNotEmpty() || link.ageRange.isNotEmpty() || link.location.isNotEmpty() || !link.folderId.isNullOrEmpty()
                    if (hasInfos) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "INFOS PRATIQUES",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp
                            )
                            // Build info items
                            val infoItems = mutableListOf<InfoPillData>()
                            if (link.price.isNotEmpty()) {
                                infoItems.add(InfoPillData(Icons.Filled.Euro, "Budget", link.price, Color(0xFF10B981)))
                            }
                            if (link.ageRange.isNotEmpty()) {
                                infoItems.add(InfoPillData(Icons.Filled.ChildCare, "Âge conseillé", link.ageRange, Color(0xFF8B5CF6)))
                            }
                            if (link.location.isNotEmpty()) {
                                infoItems.add(InfoPillData(
                                    icon = Icons.Filled.LocationOn,
                                    label = "Lieu",
                                    value = link.location,
                                    accent = Color(0xFF3B82F6),
                                    onClick = { onOpenUrl?.invoke("geo:0,0?q=${link.location}") }
                                ))
                            }
                            if (!link.folderId.isNullOrEmpty()) {
                                val folderName = folders.find { it.id == link.folderId }?.name ?: "Dossier"
                                infoItems.add(InfoPillData(Icons.Filled.FolderOpen, "Dossier", folderName, categoryColor))
                            }
                            // 2-column grid
                            val rows = infoItems.chunked(2)
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        InfoPill(
                                            icon = item.icon,
                                            label = item.label,
                                            value = item.value,
                                            accent = item.accent,
                                            modifier = Modifier.weight(1f),
                                            onClick = item.onClick
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // ── Tags ──
                    if (link.tags.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "TAGS",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                link.tags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = categoryColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "#$tag",
                                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = categoryColor,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Comments Section ──
                    HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(top = 8.dp))

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "COMMENTAIRES",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp
                            )
                            Surface(shape = CircleShape, color = Color(0xFFF3F4F6)) {
                                Text(
                                    comments.size.toString(),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Comment input
                        if (commentRepository != null && sessionManager != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(categoryColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        sessionManager.getDisplayName()?.firstOrNull()?.uppercase() ?: "?",
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { commentText = it },
                                        placeholder = { Text("Ajouter un commentaire...", fontSize = 14.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = categoryColor,
                                            unfocusedBorderColor = Color(0xFFE5E7EB)
                                        ),
                                        minLines = 2, maxLines = 4
                                    )
                                    if (commentText.isNotBlank()) {
                                        Button(
                                            onClick = {
                                                isPostingComment = true
                                                coroutineScope.launch {
                                                    commentRepository.createComment(link.id, commentText).onSuccess { newComment ->
                                                        comments = listOf(newComment) + comments
                                                        commentText = ""
                                                        isPostingComment = false
                                                    }.onFailure {
                                                        println("ERROR: Failed to create comment: ${it.message}")
                                                        isPostingComment = false
                                                    }
                                                }
                                            },
                                            enabled = !isPostingComment,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(
                                                if (isPostingComment) "Publication..." else "Publier",
                                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Comments list
                        if (isLoadingComments) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = categoryColor, modifier = Modifier.size(32.dp))
                            }
                        } else if (comments.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.ChatBubbleOutline, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(48.dp))
                                    Text("Aucun commentaire", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                                    Text("Soyez le premier à commenter", fontSize = 12.sp, color = Color(0xFFD1D5DB))
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                comments.forEach { comment ->
                                    CommentItem(
                                        comment = comment,
                                        categoryColor = categoryColor,
                                        currentUserId = sessionManager?.getUserId(),
                                        linkOwnerId = link.ownerId,
                                        onDelete = { commentId ->
                                            coroutineScope.launch {
                                                commentRepository?.deleteComment(commentId)?.onSuccess {
                                                    comments = comments.filter { it.id != commentId }
                                                }?.onFailure {
                                                    println("ERROR: Failed to delete comment: ${it.message}")
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom CTA bar ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Like button
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            liked = !liked
                            currentLikeCount += if (liked) 1 else -1
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (liked) Color(0xFFFEF2F2) else Color.White,
                    border = BorderStroke(2.dp, if (liked) Color(0xFFEF4444) else Color(0xFFE5E7EB))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Like",
                            tint = if (liked) Color(0xFFEF4444) else Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // Comment button
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showComments = !showComments },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, Color(0xFFE5E7EB))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ChatBubble, "Comment", tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp))
                    }
                }
                // Save/unsave gradient button
                Button(
                    onClick = {
                        if (readOnly && onSaveToMyList != null) {
                            showSaveDialog = true
                        } else {
                            saved = !saved
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (saved) Color(0xFF9CA3AF) else categoryColor
                    )
                ) {
                    Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (saved) "Sauvegardé ✓" else "Sauvegarder",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // ── Save dialog ──
    if (showSaveDialog && onSaveToMyList != null) {
        var selectedFolderId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon = { Icon(Icons.Default.BookmarkAdd, null, tint = Color(0xFFF97316)) },
            title = { Text("Ajouter à mes listes", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choisissez une liste (optionnel)", fontSize = 14.sp, color = Color(0xFF6B7280))
                    FilterChip(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                        label = { Text("Mes idées (sans liste)") },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF97316),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id },
                            label = { Text(folder.name) },
                            leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF97316),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveToMyList(link, selectedFolderId)
                    showSaveDialog = false
                    saved = true
                }) { Text("Ajouter", color = Color(0xFFF97316), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Annuler") }
            }
        )
    }

    // ── Delete confirmation dialog ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444)) },
            title = { Text("Supprimer cette idée ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Cette action est irréversible. L'idée \"${link.title}\" sera définitivement supprimée.",
                    fontSize = 14.sp, color = Color(0xFF6B7280)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Supprimer", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") }
            }
        )
    }
}

// ── Helper composables ──

@Composable
private fun StatItem(top: @Composable () -> Unit, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        top()
        Text(label, fontSize = 10.sp, color = Color(0xFF9CA3AF))
    }
}

private data class InfoPillData(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val accent: Color,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun InfoPill(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        modifier = clickableModifier
            .background(accent.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp
            )
            Text(
                value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    categoryColor: Color,
    currentUserId: String?,
    linkOwnerId: String,
    onDelete: (String) -> Unit
) {
    val canDelete = currentUserId == comment.userId || currentUserId == linkOwnerId

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(categoryColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                comment.userDisplayName.firstOrNull()?.uppercase() ?: "?",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = categoryColor
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    comment.userDisplayName.ifBlank { "Anonyme" },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)
                )
                if (comment.userIsAdmin) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEF4444)) {
                        Text(
                            "ADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text("•", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                Text(formatTimestamp(comment.createdAt), fontSize = 11.sp, color = Color(0xFF9CA3AF))
            }
            Text(comment.text, fontSize = 14.sp, color = Color(0xFF374151), lineHeight = 20.sp)
        }
        if (canDelete) {
            IconButton(onClick = { onDelete(comment.id) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Supprimer", tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun formatTimestamp(timestamp: String): String {
    return try {
        "à l'instant"
    } catch (e: Exception) {
        "récemment"
    }
}

fun formatDateFr(isoDate: String): String {
    return try {
        // Parse ISO date like "2025-01-15T10:30:00Z"
        val parts = isoDate.take(10).split("-")
        if (parts.size == 3) {
            val day = parts[2].toIntOrNull() ?: return isoDate
            val month = when (parts[1]) {
                "01" -> "janvier"; "02" -> "février"; "03" -> "mars"
                "04" -> "avril"; "05" -> "mai"; "06" -> "juin"
                "07" -> "juillet"; "08" -> "août"; "09" -> "septembre"
                "10" -> "octobre"; "11" -> "novembre"; "12" -> "décembre"
                else -> parts[1]
            }
            "$day $month ${parts[0]}"
        } else isoDate
    } catch (e: Exception) {
        isoDate
    }
}
