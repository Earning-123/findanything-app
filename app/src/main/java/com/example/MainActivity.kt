package com.example

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.engine.VoiceState
import com.example.ui.components.VoiceSearchDialog
import com.example.ui.screens.CameraSearchScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PrivacyCenterScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartCleanupScreen
import com.example.ui.screens.UploadReferenceScreen
import com.example.ui.theme.FindAnythingTheme
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.SearchViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels {
        val app = application as FindAnythingApp
        SearchViewModelFactory(app.repository, app.actionEngine, app.voiceEngine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FindAnythingTheme {
                FindAnythingMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FindAnythingMainApp(viewModel: SearchViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val uiState by viewModel.uiState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val soundLevel by viewModel.soundLevel.collectAsState()

    var showVoiceDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Microphone permission launcher for Voice Search
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceDialog = true
            viewModel.startVoiceSearch()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice search", Toast.LENGTH_SHORT).show()
        }
    }

    // Status / Error message snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val showBottomBar = currentRoute in listOf("home", "cleanup", "permissions", "settings")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        NavigationBarItem(
                            selected = currentRoute == "home",
                            onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            colors = navColors,
                            modifier = Modifier.testTag("nav_item_search")
                        )

                        NavigationBarItem(
                            selected = currentRoute == "cleanup",
                            onClick = { navController.navigate("cleanup") },
                            icon = { Icon(Icons.Default.CleaningServices, contentDescription = "Cleanup") },
                            label = { Text("Cleanup") },
                            colors = navColors,
                            modifier = Modifier.testTag("nav_item_cleanup")
                        )

                        NavigationBarItem(
                            selected = currentRoute == "permissions",
                            onClick = { navController.navigate("permissions") },
                            icon = { Icon(Icons.Default.Security, contentDescription = "Permissions") },
                            label = { Text("Access") },
                            colors = navColors,
                            modifier = Modifier.testTag("nav_item_access")
                        )

                        NavigationBarItem(
                            selected = currentRoute == "settings",
                            onClick = { navController.navigate("settings") },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            colors = navColors,
                            modifier = Modifier.testTag("nav_item_settings")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                // Home Screen
                composable("home") {
                    HomeScreen(
                        query = uiState.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onSearchSubmit = { q ->
                            viewModel.executeSearch(q)
                            navController.navigate("results")
                        },
                        onVoiceClick = {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasMic) {
                                showVoiceDialog = true
                                viewModel.startVoiceSearch()
                            } else {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onUploadClick = { navController.navigate("upload") },
                        onCameraClick = { navController.navigate("camera") },
                        recentSearches = recentSearches,
                        onRecentClick = { q ->
                            viewModel.executeSearch(q)
                            navController.navigate("results")
                        },
                        onDeleteRecent = { id -> viewModel.deleteRecentSearch(id) },
                        onClearAllHistory = { viewModel.clearAllHistory() },
                        onSmartCollectionClick = { name ->
                            viewModel.loadSmartCollection(name)
                            navController.navigate("results")
                        },
                        onCleanupClick = { navController.navigate("cleanup") }
                    )
                }

                // Results Screen
                composable("results") {
                    ResultsScreen(
                        query = uiState.query,
                        intent = uiState.parsedIntent,
                        items = uiState.rawResults,
                        isSearching = uiState.isSearching,
                        activeFilter = uiState.activeFilter,
                        onFilterSelect = { filter -> viewModel.setFilter(filter) },
                        onBackClick = { navController.popBackStack() },
                        onOpenItem = { item -> viewModel.openItem(item) },
                        onShareItem = { item -> viewModel.shareItem(item) },
                        onDeleteItem = { item -> viewModel.requestDeleteItem(item) }
                    )
                }

                // Camera Search Screen
                composable("camera") {
                    CameraSearchScreen(
                        onPhotoCaptured = { uri ->
                            viewModel.searchWithReferenceImage(uri)
                            navController.navigate("results")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Upload Reference Screen
                composable("upload") {
                    UploadReferenceScreen(
                        onReferenceSelected = { uri ->
                            viewModel.searchWithReferenceImage(uri)
                            navController.navigate("results")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Duplicates & Smart Cleanup Screen
                composable("cleanup") {
                    SmartCleanupScreen(
                        clusters = uiState.duplicateClusters,
                        isScanning = uiState.isScanningDuplicates,
                        onScanDuplicates = { viewModel.scanDuplicates() },
                        onDeleteItem = { item -> viewModel.requestDeleteItem(item) },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Permissions Screen
                composable("permissions") {
                    PermissionsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Privacy Center Screen
                composable("privacy") {
                    PrivacyCenterScreen(
                        onClearHistory = { viewModel.clearAllHistory() },
                        onClearIndex = { viewModel.clearIndexAndCache() },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                // Settings Screen
                composable("settings") {
                    SettingsScreen(
                        onNavigatePermissions = { navController.navigate("permissions") },
                        onNavigatePrivacy = { navController.navigate("privacy") },
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    // Voice Search Dialog
    if (showVoiceDialog) {
        VoiceSearchDialog(
            voiceState = voiceState,
            soundLevel = soundLevel,
            onDismiss = {
                viewModel.stopVoiceSearch()
                showVoiceDialog = false
            },
            onRetry = {
                viewModel.startVoiceSearch()
            },
            onResultConfirmed = { spokenQuery ->
                viewModel.onVoiceResultReceived(spokenQuery)
                showVoiceDialog = false
                navController.navigate("results")
            }
        )
    }

    // Deletion Confirmation Dialog
    if (uiState.showDeleteConfirm && uiState.itemToDelete != null) {
        com.example.ui.components.DeleteConfirmationDialog(
            item = uiState.itemToDelete!!,
            onConfirm = { viewModel.confirmDeleteItem() },
            onDismiss = { viewModel.dismissDeleteConfirm() }
        )
    }
}
