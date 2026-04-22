package com.smartgalleryapp.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartgalleryapp.MainTab
import com.smartgalleryapp.SmartGalleryViewModel
import com.smartgalleryapp.UiState
import com.smartgalleryapp.data.AppSettings
import com.smartgalleryapp.data.MediaCategory
import com.smartgalleryapp.data.MediaItem
import com.smartgalleryapp.data.MediaLocation
import com.smartgalleryapp.data.SmartGalleryRepository
import com.smartgalleryapp.data.toHumanReadable
import com.smartgalleryapp.ui.theme.Amber500
import com.smartgalleryapp.ui.theme.Cream50
import com.smartgalleryapp.ui.theme.Cream100
import com.smartgalleryapp.ui.theme.Forest700
import com.smartgalleryapp.ui.theme.Indigo500
import com.smartgalleryapp.ui.theme.Moss500
import com.smartgalleryapp.ui.theme.Red500
import com.smartgalleryapp.ui.theme.Slate700
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun SmartGalleryAppRoot(
    viewModel: SmartGalleryViewModel,
    activity: Activity,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.eventMessage) {
        uiState.eventMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearMessage()
        }
    }

    if (!uiState.hasRequestedInitialScan) {
        PermissionGate(
            activity = activity,
            onGranted = { viewModel.onPermissionFlowCompleted(granted = true) },
            onSkip = { viewModel.onPermissionFlowCompleted(granted = false) },
        )
        return
    }

    if (uiState.isScanning) {
        ScanScreen(step = uiState.scanStep, count = uiState.repositoryState.stats.totalFiles)
        return
    }

    SmartGalleryScaffold(
        uiState = uiState,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun PermissionGate(
    activity: Activity,
    onGranted: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                if (Build.VERSION.SDK_INT >= 34) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val grantedMedia = when {
            Build.VERSION.SDK_INT >= 34 -> {
                result[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                    result[Manifest.permission.READ_MEDIA_VIDEO] == true ||
                    result[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                result[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                    result[Manifest.permission.READ_MEDIA_VIDEO] == true
            }
            else -> result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (grantedMedia) onGranted() else onSkip()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Smart Gallery", color = Cream100, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Privacy-first media cleanup", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionRow("Read photos & videos")
                    PermissionRow("Manage storage safely")
                    PermissionRow("Send notifications")
                    PermissionRow("Background cleanup tasks")
                    Text(
                        "Everything stays on-device. No cloud sync, no accounts, and no uploads.",
                        color = Cream100,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val alreadyGranted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (alreadyGranted) onGranted() else launcher.launch(permissions)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow Access & Continue")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                    activity.startActivity(intent)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Open App Settings")
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Moss500, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White)
    }
}

@Composable
private fun ScanScreen(step: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Cream50,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(color = Moss500, alpha = 0.18f)
                drawArc(
                    color = Forest700,
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 16f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Scanning your gallery", style = MaterialTheme.typography.headlineSmall, color = Forest700)
            Spacer(Modifier.height(8.dp))
            Text(step, color = Slate700)
            Spacer(Modifier.height(8.dp))
            Text("$count items indexed", color = Slate700)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartGalleryScaffold(
    uiState: UiState,
    viewModel: SmartGalleryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    var deleteKeys by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Forest700) {
                listOf(
                    MainTab.HOME to "Home",
                    MainTab.LIBRARY to "Library",
                    MainTab.VAULT to "Vault",
                    MainTab.REVIEW to "Review",
                    MainTab.SETTINGS to "Settings",
                ).forEach { (tab, label) ->
                    NavigationBarItem(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(label.take(1)) },
                        label = { Text(label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            when (uiState.currentTab) {
                MainTab.HOME -> HomeScreen(uiState, onScan = { viewModel.scan() }, onOpenTrash = { viewModel.selectTab(MainTab.TRASH) }, onOpenTab = { viewModel.selectTab(it) })
                MainTab.LIBRARY -> LibraryScreen(
                    uiState = uiState,
                    onItemClick = {
                        if (uiState.selectionMode) viewModel.toggleSelection(it.key)
                        else viewModel.openViewer(it, uiState.libraryItems)
                    },
                    onItemLongPress = { viewModel.toggleSelection(it.key) },
                    onCategorySelected = viewModel::selectCategory,
                    onMoveToVault = { viewModel.moveSelectedToVault() },
                    onDelete = { viewModel.moveSelectedToTrash() },
                    onAddToReview = viewModel::addSelectedToReview,
                    onClearSelection = viewModel::clearSelection,
                    onToggleSelectionMode = viewModel::setSelectionMode,
                    onRequestScan = viewModel::scan,
                )
                MainTab.VAULT -> VaultScreen(
                    items = uiState.vaultItems,
                    onTrash = { deleteKeys = listOf(it.key) },
                    onOpen = { viewModel.openViewer(it, uiState.vaultItems) },
                )
                MainTab.REVIEW -> ReviewScreen(
                    items = uiState.reviewItems,
                    onKeep = viewModel::keepForever,
                    onTrash = { deleteKeys = listOf(it.key) },
                    onTrashAll = { deleteKeys = uiState.reviewItems.map { item -> item.key } },
                    onOpen = { viewModel.openViewer(it, uiState.reviewItems) },
                )
                MainTab.SETTINGS -> SettingsScreen(
                    state = uiState,
                    onUpdateSettings = viewModel::updateSettings,
                    onOpenTrash = { viewModel.selectTab(MainTab.TRASH) },
                    onPurgeExpired = viewModel::purgeExpired,
                    onClearData = viewModel::clearAllData,
                    onEmptyTrash = viewModel::emptyTrash,
                )
                MainTab.TRASH -> TrashScreen(
                    items = uiState.trashItems,
                    onRecover = viewModel::recover,
                    onDeleteNow = viewModel::deleteNow,
                    onEmptyTrash = viewModel::emptyTrash,
                    onBack = { viewModel.selectTab(MainTab.HOME) },
                )
            }
        }
    }

    if (deleteKeys.isNotEmpty()) {
        ConfirmDeleteDialog(
            items = uiState.repositoryState.items.filter { it.key in deleteKeys },
            onDismiss = { deleteKeys = emptyList() },
            onConfirm = {
                viewModel.moveToTrash(deleteKeys)
                deleteKeys = emptyList()
            },
        )
    }

    uiState.viewerItem?.let { item ->
        FullScreenViewer(
            item = item,
            canGoPrevious = uiState.viewerIndex > 0,
            canGoNext = uiState.viewerIndex < uiState.viewerItems.lastIndex,
            onPrevious = viewModel::showPreviousViewerItem,
            onNext = viewModel::showNextViewerItem,
            onDismiss = { viewModel.closeViewer(if (item.isVideo) 100 else 100) },
        )
    }
}

@Composable
private fun HomeScreen(
    uiState: UiState,
    onScan: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenTab: (MainTab) -> Unit,
) {
    val stats = uiState.repositoryState.stats
    val storage = uiState.repositoryState.storage
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Smart Gallery", color = Cream100)
                    Text("Overview", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                Button(onClick = onScan) { Text("Rescan") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Storage", color = Cream100, fontWeight = FontWeight.SemiBold)
                    Text("${storage.usedBytes.toHumanReadable()} used of ${storage.totalBytes.toHumanReadable()}", color = Color.White)
                    SegmentedStorageBar(
                        usedFraction = if (storage.totalBytes == 0L) 0f else storage.usedBytes.toFloat() / storage.totalBytes.toFloat(),
                    )
                    Text("Last scan: ${formatTimestamp(uiState.repositoryState.summary.scannedAt)}", color = Cream100)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total Files", stats.totalFiles.toString(), Forest700)
                StatCard("Watched", stats.watchedFiles.toString(), Moss500)
                StatCard("Unwatched", stats.unwatchedFiles.toString(), Indigo500)
                StatCard("Need Review", stats.reviewFiles.toString(), Red500)
            }
        }
        if (stats.trashFiles > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Red500),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenTrash,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Trash has ${stats.trashFiles} files", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${stats.trashBytes.toHumanReadable()} is still recoverable.", color = Color.White)
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Library") { onOpenTab(MainTab.LIBRARY) }
                QuickAction("Vault") { onOpenTab(MainTab.VAULT) }
                QuickAction("Review") { onOpenTab(MainTab.REVIEW) }
                QuickAction("Settings") { onOpenTab(MainTab.SETTINGS) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Legend", color = Color.White, fontWeight = FontWeight.SemiBold)
                    LegendRow(Moss500, "Watched")
                    LegendRow(Indigo500, "Unwatched")
                    LegendRow(Red500, "Review due")
                }
            }
        }
    }
}

@Composable
private fun SegmentedStorageBar(usedFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Cream100.copy(alpha = 0.2f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(usedFraction.coerceIn(0f, 1f))
                .height(14.dp)
                .background(Moss500),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.width(160.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color.White)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun QuickAction(label: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Forest700),
        modifier = Modifier.width(160.dp),
        onClick = onClick,
    ) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Cream100)
    }
}

@Composable
private fun LibraryScreen(
    uiState: UiState,
    onItemClick: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onCategorySelected: (MediaCategory) -> Unit,
    onMoveToVault: () -> Unit,
    onDelete: () -> Unit,
    onAddToReview: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onRequestScan: () -> Unit,
) {
    val selected = uiState.selectedKeys
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${uiState.libraryItems.size} files", color = Cream100)
                Text("Library", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            TextButton(onClick = {
                if (uiState.selectionMode) onClearSelection()
                else onToggleSelectionMode(true)
            }) {
                Text(if (uiState.selectionMode) "Done" else "Select")
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MediaCategory.ALL, MediaCategory.CAMERA, MediaCategory.VIDEOS, MediaCategory.DOWNLOADS, MediaCategory.SCREENSHOTS, MediaCategory.WHATSAPP, MediaCategory.IMAGES).forEach { category ->
                FilterChip(
                    selected = uiState.selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.name.replace('_', ' ')) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AssistChip(onClick = {}, label = { Text("Status dots show watched, unwatched, and review items") })
        Spacer(Modifier.height(8.dp))

        val unwatched = uiState.libraryItems.filterNot { it.watched }
        val watched = uiState.libraryItems.filter { it.watched }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            if (!uiState.hasMediaPermission) {
                item {
                    LibraryEmptyState(
                        title = "Media access is off",
                        body = "Grant photo and video access in system settings, then come back and rescan.",
                        actionLabel = "Open App Settings",
                        onAction = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        secondaryLabel = "Rescan",
                        onSecondary = onRequestScan,
                    )
                }
            } else if (uiState.libraryItems.isEmpty()) {
                item {
                    LibraryEmptyState(
                        title = "No media found",
                        body = "Your library is empty or Android has only granted access to a limited photo selection.",
                        actionLabel = "Rescan",
                        onAction = onRequestScan,
                    )
                }
            } else {
                item { SectionHeader("UNWATCHED", unwatched.size, Indigo500) }
                items(unwatched.chunked(3)) { row ->
                    MediaRow(items = row, selectedKeys = selected, onItemClick = onItemClick, onItemLongPress = onItemLongPress)
                }
                item { SectionHeader("WATCHED", watched.size, Moss500) }
                items(watched.chunked(3)) { row ->
                    MediaRow(items = row, selectedKeys = selected, onItemClick = onItemClick, onItemLongPress = onItemLongPress)
                }
            }
        }
    }

    if (selected.isNotEmpty()) {
        SelectionBar(
            selectedCount = selected.size,
            onVault = onMoveToVault,
            onReview = onAddToReview,
            onDelete = onDelete,
        )
    } else if (uiState.selectionMode) {
        SelectionHintBar(onDone = onClearSelection)
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = Cream100)
    }
}

@Composable
private fun MediaRow(
    items: List<MediaItem>,
    selectedKeys: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.fillMaxWidth(0.333f)) {
                MediaGridItem(
                    item = item,
                    selected = item.key in selectedKeys,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                )
            }
        }
        repeat(3 - items.size) {
            Spacer(modifier = Modifier.fillMaxWidth(0.333f))
        }
    }
}

@Composable
private fun MediaGridItem(
    item: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val borderColor = when {
        selected -> Amber500
        item.isReviewDue -> Red500
        item.watched -> Moss500
        else -> Indigo500
    }
    Card(
        modifier = Modifier
            .height(140.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Forest700),
    ) {
        Box(Modifier.fillMaxSize()) {
            ThumbnailBox(item = item, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(10.dp)
                    .background(borderColor, CircleShape)
                    .align(Alignment.TopEnd),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(8.dp),
            ) {
                Text(item.displayName, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.watched) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Moss500),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest700), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = Cream100)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    onVault: () -> Unit,
    onReview: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$selectedCount selected", color = Color.White)
                Button(onClick = onVault) { Text("Archive") }
                OutlinedButton(onClick = onReview) { Text("Review") }
                OutlinedButton(onClick = onDelete) { Text("Trash") }
            }
        }
    }
}

@Composable
private fun SelectionHintBar(onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tap items to select them", color = Color.White)
                OutlinedButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

@Composable
private fun VaultScreen(
    items: List<MediaItem>,
    onTrash: (MediaItem) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Vault", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("${items.size} archived files", color = Cream100)
                    Text("${items.sumOf { it.sizeBytes }.toHumanReadable()} archived", color = Cream100)
                }
            }
        }
        items(items, key = { it.key }) { item ->
            ListItemCard(item = item, badge = if (item.isReviewDue) "Review due" else "Watched") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpen(item) }) { Text("Open") }
                    Button(onClick = { onTrash(item) }) { Text("Trash") }
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    items: List<MediaItem>,
    onKeep: (String) -> Unit,
    onTrash: (MediaItem) -> Unit,
    onTrashAll: () -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("7+ days idle", color = Red500, fontWeight = FontWeight.SemiBold)
                    Text("Delete Review", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                if (items.isNotEmpty()) {
                    Button(onClick = onTrashAll) { Text("Trash All") }
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Forest700), modifier = Modifier.fillMaxWidth()) {
                    Text("Queue clear. Nothing needs review right now.", color = Cream100, modifier = Modifier.padding(20.dp))
                }
            }
        } else {
            items(items, key = { it.key }) { item ->
                ListItemCard(item = item, badge = "${daysIdle(item)}d idle") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth()) { Text("Preview") }
                        OutlinedButton(onClick = { onKeep(item.key) }, modifier = Modifier.fillMaxWidth()) { Text("Keep forever") }
                        Button(onClick = { onTrash(item) }, modifier = Modifier.fillMaxWidth()) { Text("Trash") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onOpenTrash: () -> Unit,
    onPurgeExpired: () -> Unit,
    onClearData: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val settings = state.repositoryState.settings
    val stats = state.repositoryState.stats
    val context = LocalContext.current
    val permissionSummary = remember {
        buildList {
            add("Media: granted when initial scan succeeds")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("Notifications: ${if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "Granted" else "Denied"}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("Manage storage: ${if (Environment.isExternalStorageManager()) "Granted" else "Needs settings access"}")
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Tracked ${stats.totalFiles + stats.vaultFiles}  •  In trash ${stats.trashFiles}  •  Deleted hidden from app history", color = Cream100)
                }
            }
        }
        item {
            ToggleCard("Auto-track watched files", settings.autoTrack) { checked ->
                onUpdateSettings { it.copy(autoTrack = checked) }
            }
        }
        item {
            ToggleCard("Background scan", settings.backgroundScan) { checked ->
                onUpdateSettings { it.copy(backgroundScan = checked) }
            }
        }
        item {
            ToggleCard("Notifications", settings.notificationsEnabled) { checked ->
                onUpdateSettings { it.copy(notificationsEnabled = checked) }
            }
        }
        item {
            ToggleCard("Weekly digest", settings.weeklyDigest) { checked ->
                onUpdateSettings { it.copy(weeklyDigest = checked) }
            }
        }
        item {
            SettingChips("Review schedule", listOf(7, 14, 30), settings.reviewDays) { value ->
                onUpdateSettings { it.copy(reviewDays = value) }
            }
        }
        item {
            SettingChips("Trash retention", listOf(14, 30, 60), settings.trashRetentionDays) { value ->
                onUpdateSettings { it.copy(trashRetentionDays = value) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Permission status", color = Color.White, fontWeight = FontWeight.SemiBold)
                    permissionSummary.forEach { Text(it, color = Cream100) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Danger zone", color = Amber500, fontWeight = FontWeight.Bold)
                    Button(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) { Text("Open Trash") }
                    OutlinedButton(onClick = onEmptyTrash, modifier = Modifier.fillMaxWidth()) { Text("Empty Trash now") }
                    OutlinedButton(onClick = onPurgeExpired, modifier = Modifier.fillMaxWidth()) { Text("Purge expired trash") }
                    OutlinedButton(onClick = onClearData, modifier = Modifier.fillMaxWidth()) { Text("Clear app data") }
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingChips(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest700)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = Color.White)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text("$option days") },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashScreen(
    items: List<MediaItem>,
    onRecover: (String) -> Unit,
    onDeleteNow: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("30-day window", color = Red500, fontWeight = FontWeight.SemiBold)
                    Text("Trash", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }
        if (items.isNotEmpty()) {
            item {
                Button(onClick = onEmptyTrash, modifier = Modifier.fillMaxWidth()) { Text("Empty Trash") }
            }
        }
        if (items.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Forest700), modifier = Modifier.fillMaxWidth()) {
                    Text("Trash is empty.", color = Cream100, modifier = Modifier.padding(20.dp))
                }
            }
        } else {
            items(items, key = { it.key }) { item ->
                val daysLeft = SmartGalleryRepository.daysUntil(item.purgeAt)
                ListItemCard(item = item, badge = "$daysLeft days left") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRecover(item.key) }, modifier = Modifier.fillMaxWidth()) { Text("Recover") }
                        OutlinedButton(onClick = { onDeleteNow(item.key) }, modifier = Modifier.fillMaxWidth()) { Text("Delete now") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListItemCard(item: MediaItem, badge: String, actions: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest700), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThumbnailBox(item = item, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${item.category.name} • ${item.sizeBytes.toHumanReadable()} • ${item.watchProgress}%", color = Cream100)
                AssistChip(onClick = {}, label = { Text(badge) })
                actions()
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val totalSize = items.sumOf { it.sizeBytes }.toHumanReadable()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text("Send to Trash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep files") } },
        title = { Text("Files are not permanently deleted immediately") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${items.size} files • $totalSize • 30 days to recover")
                items.take(5).forEach { Text("• ${it.displayName}") }
                if (items.size > 5) Text("+ ${items.size - 5} more")
            }
        },
    )
}

@Composable
private fun FullScreenViewer(
    item: MediaItem,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.displayName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("${item.category.name.lowercase().replaceFirstChar { it.uppercase() }} • ${item.sizeBytes.toHumanReadable()}", color = Cream100)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.isVideo) {
                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(item.safeUri)
                                    setOnPreparedListener { start() }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                        )
                    } else {
                        ThumbnailBox(item = item, modifier = Modifier.fillMaxSize())
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onPrevious, enabled = canGoPrevious) { Text("Previous") }
                    OutlinedButton(onClick = onNext, enabled = canGoNext) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailBox(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = item.key, key2 = item.location) {
        value = loadPreviewBitmap(context, item)
    }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(if (item.isVideo) "VIDEO" else "PHOTO", color = Color.White)
        }
    }
}

private fun loadPreviewBitmap(context: android.content.Context, item: MediaItem): Bitmap? {
    return runCatching {
        when (item.location) {
            MediaLocation.LIBRARY -> context.contentResolver.loadThumbnail(Uri.parse(item.sourceUri), Size(160, 160), null)
            MediaLocation.VAULT, MediaLocation.TRASH -> {
                val path = item.currentPath ?: return null
                if (item.isVideo) {
                    ThumbnailUtils.createVideoThumbnail(File(path), Size(160, 160), null)
                } else {
                    ThumbnailUtils.createImageThumbnail(File(path), Size(160, 160), null)
                }
            }
        }
    }.onFailure {
        Log.w("SmartGalleryUI", "Thumbnail load failed for ${item.displayName}", it)
    }.getOrNull()
}

private fun daysIdle(item: MediaItem): Long {
    val basis = item.lastOpenedAt ?: TimeUnit.SECONDS.toMillis(item.dateModifiedEpochSec)
    val delta = System.currentTimeMillis() - basis
    return TimeUnit.MILLISECONDS.toDays(delta).coerceAtLeast(0)
}

private fun formatTimestamp(timestamp: Long): String {
    val deltaHours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    return if (deltaHours < 24) "$deltaHours h ago" else "${deltaHours / 24} d ago"
}
