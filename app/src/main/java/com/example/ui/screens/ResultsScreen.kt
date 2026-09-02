package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ItemType
import com.example.model.ParsedIntent
import com.example.model.SearchItem
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.ResultExplanationDialog
import com.example.ui.theme.AccentGradientEnd
import com.example.ui.theme.AccentGradientStart

@Composable
fun ResultsScreen(
    query: String,
    intent: ParsedIntent?,
    items: List<SearchItem>,
    isSearching: Boolean,
    activeFilter: ItemType?,
    onFilterSelect: (ItemType?) -> Unit,
    onBackClick: () -> Unit,
    onOpenItem: (SearchItem) -> Unit,
    onShareItem: (SearchItem) -> Unit,
    onDeleteItem: (SearchItem) -> Unit,
    onConfirmMatch: ((String) -> Unit)? = null,
    onRejectMatch: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedItemForInfo by remember { mutableStateOf<SearchItem?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<SearchItem?>(null) }

    val filteredItems = remember(items, activeFilter) {
        if (activeFilter == null) items else items.filter { it.type == activeFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("results_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = query.ifBlank { "Search Results" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (intent != null && intent.explanation.isNotBlank()) {
                    Text(
                        text = intent.explanation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${filteredItems.size} found",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        // Filter Chips Row
        val chipBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = activeFilter == null,
                    onClick = { onFilterSelect(null) },
                    label = { Text("All (${items.size})") },
                    shape = RoundedCornerShape(20.dp),
                    border = chipBorder,
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_all")
                )
            }
            item {
                val count = items.count { it.type == ItemType.PHOTO }
                FilterChip(
                    selected = activeFilter == ItemType.PHOTO,
                    onClick = { onFilterSelect(ItemType.PHOTO) },
                    label = { Text("Photos ($count)") },
                    shape = RoundedCornerShape(20.dp),
                    border = chipBorder,
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_photos")
                )
            }
            item {
                val count = items.count { it.type == ItemType.DOCUMENT }
                FilterChip(
                    selected = activeFilter == ItemType.DOCUMENT,
                    onClick = { onFilterSelect(ItemType.DOCUMENT) },
                    label = { Text("Docs ($count)") },
                    shape = RoundedCornerShape(20.dp),
                    border = chipBorder,
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_docs")
                )
            }
            item {
                val count = items.count { it.type == ItemType.VIDEO }
                FilterChip(
                    selected = activeFilter == ItemType.VIDEO,
                    onClick = { onFilterSelect(ItemType.VIDEO) },
                    label = { Text("Videos ($count)") },
                    shape = RoundedCornerShape(20.dp),
                    border = chipBorder,
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_videos")
                )
            }
            item {
                val count = items.count { it.type == ItemType.APP }
                FilterChip(
                    selected = activeFilter == ItemType.APP,
                    onClick = { onFilterSelect(ItemType.APP) },
                    label = { Text("Apps ($count)") },
                    shape = RoundedCornerShape(20.dp),
                    border = chipBorder,
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_apps")
                )
            }
            item {
                val count = items.count { it.type == ItemType.CONTACT }
                FilterChip(
                    selected = activeFilter == ItemType.CONTACT,
                    onClick = { onFilterSelect(ItemType.CONTACT) },
                    label = { Text("Contacts ($count)") },
                    shape = RoundedCornerShape(20.dp),
                    border = chipBorder,
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_contacts")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Content Area
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching local index & storage…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No matching items found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Try searching by another keyword, date, or amount like “5000 screenshot” or “PDF”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Adaptive Grid / List
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    when (item.type) {
                        ItemType.PHOTO -> {
                            PhotoResultCard(
                                item = item,
                                onClick = { onOpenItem(item) },
                                onShare = { onShareItem(item) },
                                onDelete = { selectedItemForDelete = item },
                                onInfo = { selectedItemForInfo = item },
                                onConfirmMatch = onConfirmMatch,
                                onRejectMatch = onRejectMatch
                            )
                        }
                        else -> {
                            FileOrAppResultCard(
                                item = item,
                                onClick = { onOpenItem(item) },
                                onShare = { onShareItem(item) },
                                onDelete = { selectedItemForDelete = item },
                                onInfo = { selectedItemForInfo = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Info Dialog
    selectedItemForInfo?.let { item ->
        ResultExplanationDialog(
            item = item,
            onDismiss = { selectedItemForInfo = null }
        )
    }

    // Delete Dialog
    selectedItemForDelete?.let { item ->
        DeleteConfirmationDialog(
            item = item,
            onConfirm = {
                onDeleteItem(item)
                selectedItemForDelete = null
            },
            onDismiss = { selectedItemForDelete = null }
        )
    }
}

@Composable
fun PhotoResultCard(
    item: SearchItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onConfirmMatch: ((String) -> Unit)? = null,
    onRejectMatch: ((String) -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("result_photo_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = item.uri ?: item.filePath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (item.labelBadge != null) {
                    Surface(
                        color = when {
                            item.isConfirmed -> MaterialTheme.colorScheme.primary
                            item.isPossibleMatch -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            if (item.isConfirmed) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = item.labelBadge,
                                color = when {
                                    item.isConfirmed -> MaterialTheme.colorScheme.onPrimary
                                    item.isPossibleMatch -> MaterialTheme.colorScheme.onTertiary
                                    else -> MaterialTheme.colorScheme.onSecondary
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.4.sp
                                )
                            )
                        }
                    }
                } else if (item.isScreenshot) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "SCREENSHOT",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }

                if (item.matchReason.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 10.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = item.matchReason,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.formattedDate.ifBlank { item.formattedSize },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.isPossibleMatch && onConfirmMatch != null && onRejectMatch != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Confirm match?",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onConfirmMatch(item.id) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRejectMatch(item.id) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Reject",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onInfo, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FileOrAppResultCard(
    item: SearchItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("result_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.type) {
                                ItemType.APP -> MaterialTheme.colorScheme.primaryContainer
                                ItemType.CONTACT -> MaterialTheme.colorScheme.secondaryContainer
                                ItemType.DOCUMENT -> MaterialTheme.colorScheme.errorContainer
                                ItemType.VIDEO -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.type) {
                            ItemType.APP -> Icons.Default.Apps
                            ItemType.CONTACT -> Icons.Default.Person
                            ItemType.DOCUMENT -> Icons.Default.Description
                            ItemType.VIDEO -> Icons.Default.Videocam
                            else -> Icons.Default.Folder
                        },
                        contentDescription = item.type.name,
                        tint = when (item.type) {
                            ItemType.APP -> MaterialTheme.colorScheme.onPrimaryContainer
                            ItemType.CONTACT -> MaterialTheme.colorScheme.onSecondaryContainer
                            ItemType.DOCUMENT -> MaterialTheme.colorScheme.onErrorContainer
                            ItemType.VIDEO -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.subtitle.ifBlank { item.formattedSize },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.matchReason.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = item.matchReason,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (item.type == ItemType.CONTACT) {
                        IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    } else if (item.type == ItemType.APP) {
                        IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Launch", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
