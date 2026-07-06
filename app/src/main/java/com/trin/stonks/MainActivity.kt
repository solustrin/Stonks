package com.trin.stonks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.Animatable
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.trin.stonks.data.*
import com.trin.stonks.data.InventoryItem
import com.trin.stonks.ui.theme.StonksTheme
import com.trin.stonks.utils.ImageUtils
import com.trin.stonks.utils.PdfUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextOverflow
import com.trin.stonks.utils.ConfigManager
import com.trin.stonks.utils.FirebaseInitializer
import android.app.Activity
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Check

fun createCameraUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera_images").apply { mkdirs() }
    val file = File(folder, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "com.trin.stonks.fileprovider", file)
}

enum class StonksScreen(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    History("Transactions", Icons.AutoMirrored.Filled.List),
    Inventory("Stock Tracker", Icons.Default.Inventory),
    Invoice("Invoice", Icons.Default.Receipt),
    Calculator("Calculator", Icons.Default.Calculate),
    Settings("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("stonks_settings", Context.MODE_PRIVATE)

        setContent {
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
            var isConfigured by remember { mutableStateOf(ConfigManager.hasConfig(this)) }
            var workspaceSelected by remember { mutableStateOf(false) }
            var rememberWorkspace by remember { mutableStateOf(prefs.getBoolean("remember_workspace", true)) }

            StonksTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isConfigured) {
                        SetupScreen(
                            onSetupComplete = {
                                isConfigured = true
                                recreate()
                            },
                            onError = { /* show error */ }
                        )
                    } else {
                        val config = ConfigManager.getConfig(this)
                        val imgbbKey = ConfigManager.getImgbbKey(this)

                        if (config != null && imgbbKey != null) {
                            val firestore = FirebaseInitializer.initializeFirebase(this, config)
                            val repository = CloudRepository(firestore, imgbbKey)
                            val factory = AccountingViewModelFactory(repository)
                            val viewModel = ViewModelProvider(this, factory)[AccountingViewModel::class.java]

                            val lastWorkspaceId = prefs.getString("workspace_id", null)
                            val workspaces by viewModel.workspaces.collectAsState()

                            LaunchedEffect(workspaces) {
                                if (rememberWorkspace && !workspaceSelected && lastWorkspaceId != null &&
                                    workspaces.any { it.id == lastWorkspaceId }) {
                                    viewModel.setWorkspace(lastWorkspaceId)
                                    workspaceSelected = true
                                }
                            }

                            if (!workspaceSelected) {
                                WorkspaceSelectionScreen(
                                    workspaces = workspaces,
                                    onWorkspaceSelected = { id ->
                                        viewModel.setWorkspace(id)
                                        prefs.edit().putString("workspace_id", id).apply()
                                        workspaceSelected = true
                                    },
                                    onWorkspaceCreated = { id ->
                                        viewModel.setWorkspace(id)
                                        prefs.edit().putString("workspace_id", id).apply()
                                        workspaceSelected = true
                                    },
                                    viewModel = viewModel
                                )
                            } else {
                                MainNavigationShell(
                                    viewModel = viewModel,
                                    isDarkTheme = isDarkTheme,
                                    onThemeToggle = { newTheme ->
                                        isDarkTheme = newTheme
                                        prefs.edit().putBoolean("dark_theme", newTheme).apply()
                                    },
                                    onWorkspaceChanged = { workspaceId ->
                                        prefs.edit().putString("workspace_id", workspaceId).apply()
                                    },
                                    onExitToWorkspaceSelection = {
                                        workspaceSelected = false
                                    },
                                    rememberWorkspace = rememberWorkspace,
                                    onRememberWorkspaceToggle = { newValue ->
                                        rememberWorkspace = newValue
                                        prefs.edit().putBoolean("remember_workspace", newValue).apply()
                                    }
                                )
                            }
                        } else {
                            ConfigManager.clearConfig(this)
                            isConfigured = false
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// WORKSPACE SELECTION
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSelectionScreen(
    workspaces: List<Workspace>,
    onWorkspaceSelected: (String) -> Unit,
    onWorkspaceCreated: (String) -> Unit,
    viewModel: AccountingViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newWorkspaceName by remember { mutableStateOf("") }

    var workspaceToEdit by remember { mutableStateOf<Workspace?>(null) }
    var editWorkspaceName by remember { mutableStateOf("") }

    var workspaceToDelete by remember { mutableStateOf<Workspace?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Select Workspace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Choose or create a workspace to start tracking.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))

            workspaces.forEach { ws ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onWorkspaceSelected(ws.id) },
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(ws.name, fontWeight = FontWeight.Medium) }

                        IconButton(
                            onClick = {
                                workspaceToEdit = ws
                                editWorkspaceName = ws.name
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Workspace", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { workspaceToDelete = ws },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Workspace", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Workspace")
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Workspace") },
            text = {
                OutlinedTextField(
                    value = newWorkspaceName,
                    onValueChange = { newWorkspaceName = it },
                    label = { Text("Workspace Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newWorkspaceName.isNotBlank()) {
                        viewModel.createWorkspace(newWorkspaceName.trim())
                        showCreateDialog = false
                        newWorkspaceName = ""
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (workspaceToEdit != null) {
        AlertDialog(
            onDismissRequest = { workspaceToEdit = null },
            title = { Text("Rename Workspace") },
            text = {
                OutlinedTextField(
                    value = editWorkspaceName,
                    onValueChange = { editWorkspaceName = it },
                    label = { Text("Workspace Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editWorkspaceName.isNotBlank() && workspaceToEdit != null) {
                        viewModel.updateWorkspaceName(workspaceToEdit!!.id, editWorkspaceName.trim())
                        workspaceToEdit = null
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToEdit = null }) { Text("Cancel") }
            }
        )
    }

    if (workspaceToDelete != null) {
        AlertDialog(
            onDismissRequest = { workspaceToDelete = null },
            title = { Text("Delete Workspace") },
            text = { Text("Are you sure you want to delete '${workspaceToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        workspaceToDelete?.let { viewModel.deleteWorkspace(it.id) }
                        workspaceToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ============================================================
// MAIN NAVIGATION SHELL
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationShell(
    viewModel: AccountingViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onWorkspaceChanged: (String) -> Unit,
    onExitToWorkspaceSelection: () -> Unit,
    rememberWorkspace: Boolean,
    onRememberWorkspaceToggle: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by remember { mutableStateOf(StonksScreen.Dashboard) }
    var showAddDialog by remember { mutableStateOf(false) }
    val inventoryList by viewModel.inventory.collectAsState()

    val profiles by viewModel.profiles.collectAsState()
    val paymentTypes by viewModel.paymentTypes.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val currentWorkspaceId by viewModel.currentWorkspaceId.collectAsState()

    val currentWorkspaceName = workspaces.find { it.id == currentWorkspaceId }?.name ?: "Default"

    LaunchedEffect(workspaces, currentWorkspaceId) {
        if (workspaces.none { it.id == currentWorkspaceId }) {
            onExitToWorkspaceSelection()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("  STONKS", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                StonksScreen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.title, fontWeight = FontWeight.Medium) },
                        selected = currentScreen == screen,
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        onClick = { currentScreen = screen; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                Text("Current Workspace", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(currentWorkspaceName, fontWeight = FontWeight.Medium)
                }

                TextButton(
                    onClick = {
                        onExitToWorkspaceSelection()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch Workspace")
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                Text("Other Workspaces", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                workspaces.forEach { ws ->
                    if (ws.id != currentWorkspaceId) {
                        NavigationDrawerItem(
                            label = { Text(ws.name) },
                            selected = false,
                            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                viewModel.setWorkspace(ws.id)
                                onWorkspaceChanged(ws.id)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        content = {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(currentScreen.title, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        " $currentWorkspaceName ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                floatingActionButton = {
                    if (currentScreen == StonksScreen.Dashboard) {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    when (currentScreen) {
                        StonksScreen.Dashboard -> CleanDashboard(viewModel) {
                            currentScreen = StonksScreen.History
                        }

                        StonksScreen.Inventory -> InventoryScreen(
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )
                        StonksScreen.History -> TransactionHistoryScreen(
                            viewModel, profiles, paymentTypes, snackbarHostState, scope
                        )

                        StonksScreen.Invoice -> InvoiceMakerScreen(
                            inventoryList = inventoryList,
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )

                        StonksScreen.Calculator -> CalculatorScreen()

                        StonksScreen.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            profiles = profiles,
                            paymentTypes = paymentTypes,
                            workspaces = workspaces,
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = onThemeToggle,
                            rememberWorkspace = rememberWorkspace,
                            onRememberWorkspaceToggle = onRememberWorkspaceToggle,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )
                    }
                }
            }
        }
    )

    if (showAddDialog) {
        MultiItemTransactionDialog(
            profiles = profiles,
            paymentTypes = paymentTypes,
            inventoryList = inventoryList,
            onDismiss = { showAddDialog = false },
            onSave = { type, createdBy, paymentType, items, receiptBytes, note, affectsStock ->
                viewModel.addTransactions(type, createdBy, paymentType, items, receiptBytes, note, affectsStock) { success, errorMsg ->
                    showAddDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "Transaction Saved" else "Failed: $errorMsg"
                        )
                    }
                }
            }
        )
    }
}

// ---------- Dashboard ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanDashboard(
    viewModel: AccountingViewModel,
    onNavigateToHistory: () -> Unit
) {
    val netProfit by viewModel.netProfit.collectAsState()
    val totalRevenue by viewModel.totalRevenue.collectAsState()
    val totalExpenses by viewModel.totalExpenses.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()

    var selectedPeriod by remember { mutableStateOf(ChartPeriod.WEEK) }
    var chartType by remember { mutableStateOf("DONUT") }

    val recent = allTransactions.sortedByDescending { it.timestamp }.take(3)
    val groupedRecent = remember(recent) { groupTransactions(recent) }

    val periodTransactions = remember(allTransactions, selectedPeriod) {
        val now = System.currentTimeMillis()
        val durationMillis = when (selectedPeriod) {
            ChartPeriod.HOUR -> 24 * 60 * 60 * 1000L
            ChartPeriod.DAY -> 7 * 24 * 60 * 60 * 1000L
            ChartPeriod.WEEK -> 28 * 24 * 60 * 60 * 1000L
            ChartPeriod.MONTH -> 365L * 24 * 60 * 60 * 1000L
            ChartPeriod.YEAR -> 5L * 365 * 24 * 60 * 60 * 1000L
        }
        allTransactions.filter { it.timestamp >= (now - durationMillis) }
    }
    val periodRev = periodTransactions.filter { it.type == "SALE" }.sumOf { it.amount }
    val periodExp = periodTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("NET PROFIT", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(text = "RM $netProfit", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = if (netProfit >= 0) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F))
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ElevatedCard(modifier = Modifier.weight(1f), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("REVENUE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text("RM $totalRevenue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF388E3C))
                    }
                }
                ElevatedCard(modifier = Modifier.weight(1f), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("EXPENSES", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text("RM $totalExpenses", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD32F2F))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Breakdown", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { chartType = if (chartType == "DONUT") "BAR" else "DONUT" }) {
                    Icon(
                        imageVector = if (chartType == "DONUT") Icons.Default.BarChart else Icons.Default.PieChart,
                        contentDescription = "Toggle Chart",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(250)), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (chartType == "DONUT") {
                        CircularProfitChart(revenue = periodRev, expense = periodExp)
                    } else {
                        ProfitBarChart(viewModel.getChartData(allTransactions, selectedPeriod))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Recent Transactions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(groupedRecent) { group ->
            if (group.size == 1) {
                TransactionCard(tx = group.first(), showActions = false, onEdit = {}, onDelete = {})
            } else {
                GroupedTransactionCard(group = group, showActions = false, onEditItem = {}, onDeleteItem = {})
            }
        }

        item {
            TextButton(onClick = onNavigateToHistory, modifier = Modifier.fillMaxWidth()) { Text("View All Transactions") }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---------- ProfitBarChart ----------
@Composable
fun ProfitBarChart(data: List<Triple<String, Double, Int>>) {
    var selectedBar by remember { mutableStateOf<Triple<String, Double, Int>?>(null) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val progress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    if (data.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("No transactions yet.", color = subTextColor)
        }
        return
    }

    val maxProfit = data.maxOfOrNull { kotlin.math.abs(it.second) }?.takeIf { it > 0 } ?: 1.0
    val displayData = data.takeLast(12)
    val steps = 3
    val barWidth = 24.dp
    val barSpacing = 8.dp
    val chartHeight = 200.dp

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedBar?.let {
                "Selected: ${it.first} • RM ${"%.2f".format(it.second)} • ${it.third} tx"
            } ?: "Tap a bar to see details",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp).height(20.dp)
        )

        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.width(50.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                for (i in steps downTo 0) {
                    val value = (maxProfit / steps) * i
                    Text(text = "RM${value.toInt()}", fontSize = 9.sp, color = subTextColor)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(scrollState)) {
                val totalWidth = (barWidth + barSpacing) * displayData.size + 8.dp
                Row(
                    modifier = Modifier.width(totalWidth).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(barSpacing),
                    verticalAlignment = Alignment.Bottom
                ) {
                    displayData.forEach { dataPoint ->
                        val (_, profit, _) = dataPoint
                        val targetFraction = (kotlin.math.abs(profit) / maxProfit).toFloat()
                        val barColor = if (profit >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)

                        Column(
                            modifier = Modifier
                                .width(barWidth)
                                .fillMaxHeight()
                                .clickable { selectedBar = dataPoint },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((targetFraction * progress.value).coerceAtLeast(0f))
                                    .defaultMinSize(minHeight = 2.dp)
                                    .background(barColor, RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
                Canvas(modifier = Modifier.matchParentSize()) {
                    for (i in 0..steps) {
                        val y = size.height * (i.toFloat() / steps)
                        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 2f)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 50.dp).horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(barSpacing)
        ) {
            displayData.forEach { (label, _, _) ->
                Box(modifier = Modifier.width(barWidth), contentAlignment = Alignment.TopCenter) {
                    Text(text = label, fontSize = 8.sp, color = textColor, maxLines = 1)
                }
            }
        }
    }
}

// ---------- Circular Donut Chart ----------
@Composable
fun CircularProfitChart(revenue: Double, expense: Double) {
    val total = revenue + expense
    val revAngle = if (total > 0) (revenue / total).toFloat() * 360f else 0f
    val expAngle = if (total > 0) (expense / total).toFloat() * 360f else 0f

    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(revenue, expense) {
        animationPlayed = false
        kotlinx.coroutines.delay(50)
        animationPlayed = true
    }

    val animateRev by animateFloatAsState(
        targetValue = if (animationPlayed) revAngle else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "rev"
    )
    val animateExp by animateFloatAsState(
        targetValue = if (animationPlayed) expAngle else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "exp"
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val strokeWidth = 32.dp.toPx()

                if (total == 0.0) {
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                } else {
                    if (animateRev > 0f) {
                        drawArc(
                            color = Color(0xFF388E3C),
                            startAngle = -90f, sweepAngle = animateRev, useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                    if (animateExp > 0f) {
                        drawArc(
                            color = Color(0xFFD32F2F),
                            startAngle = -90f + animateRev, sweepAngle = animateExp, useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Net Profit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "RM ${"%.2f".format(revenue - expense)}",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = if (revenue >= expense) Color(0xFF388E3C) else Color(0xFFD32F2F)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF388E3C), RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Revenue", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("RM ${"%.2f".format(revenue)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFD32F2F), RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Expenses", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("RM ${"%.2f".format(expense)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ---------- Transaction History ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: AccountingViewModel,
    profiles: List<String>,
    paymentTypes: List<String>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val transactions by viewModel.allTransactions.collectAsState()
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var filterYear by remember { mutableStateOf<String?>(null) }
    var filterMonth by remember { mutableStateOf<Int?>(null) }
    var filterType by remember { mutableStateOf("All") }
    var filterCreator by remember { mutableStateOf("All") }
    var showFilterDialog by remember { mutableStateOf(false) }

    val availableYears = remember(transactions) {
        transactions.map { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.YEAR)
        }.distinct().sortedDescending()
    }
    val monthNames = remember { DateFormatSymbols().shortMonths.toList() }

    val filtered = transactions.filter { tx ->
        val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
        (filterYear == null || filterYear == "All" || txCal.get(Calendar.YEAR).toString() == filterYear) &&
                (filterMonth == null || (txCal.get(Calendar.MONTH) + 1) == filterMonth) &&
                (filterType == "All" || tx.type == filterType) &&
                (filterCreator == "All" || tx.createdBy == filterCreator) &&
                (searchQuery.isBlank() || tx.itemName.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by item") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            IconButton(onClick = { showFilterDialog = true }) {
                BadgedBox(badge = {
                    val cnt = listOfNotNull(
                        if (filterYear != null && filterYear != "All") 1 else null,
                        if (filterMonth != null) 1 else null,
                        if (filterType != "All") 1 else null,
                        if (filterCreator != "All") 1 else null
                    ).sum()
                    if (cnt > 0) Badge { Text(cnt.toString()) }
                }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                }
            }
        }

        if (filterYear != null || filterMonth != null || filterType != "All" || filterCreator != "All") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filterYear != null) {
                    val y = filterYear!!
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                        FilterChip(selected = true, onClick = { filterYear = null }, label = { Text(y) }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Transparent, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                }
                if (filterMonth != null) {
                    val m = filterMonth!!
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                        FilterChip(selected = true, onClick = { filterMonth = null }, label = { Text(monthNames.getOrElse(m-1){"?"}) }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Transparent, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                }
                if (filterType != "All") {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                        FilterChip(selected = true, onClick = { filterType = "All" }, label = { Text(filterType) }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Transparent, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                }
                if (filterCreator != "All") {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                        FilterChip(selected = true, onClick = { filterCreator = "All" }, label = { Text(filterCreator) }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Transparent, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                }
            }
        }

        Text("Showing ${filtered.size} transactions", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        val grouped = remember(filtered) { groupTransactions(filtered) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(grouped) { group ->
                if (group.size == 1) {
                    val tx = group.first()
                    TransactionCard(tx = tx, onEdit = { transactionToEdit = tx }, onDelete = { transactionToDelete = tx })
                } else {
                    GroupedTransactionCard(group = group, onEditItem = { transactionToEdit = it }, onDeleteItem = { transactionToDelete = it })
                }
            }
        }
    }

    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction") },
            text = { Text("Delete '${transactionToDelete?.itemName}'?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteTransaction(transactionToDelete!!.id); transactionToDelete = null; scope.launch { snackbarHostState.showSnackbar("Deleted") } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("Cancel") } }
        )
    }

    if (transactionToEdit != null) {
        EditTransactionDialog(
            transaction = transactionToEdit!!,
            profiles = profiles,
            paymentTypes = paymentTypes,
            onDismiss = { transactionToEdit = null },
            onSave = { id, type, name, amount, quantity, unitPrice, createdBy, paymentType, batchId, note, receiptBytes ->
                viewModel.updateTransaction(id, type, name, amount, quantity, unitPrice, createdBy, paymentType, batchId, note, receiptBytes)
                transactionToEdit = null
                scope.launch { snackbarHostState.showSnackbar("Transaction updated") }
            }
        )
    }
    if (showFilterDialog) {
        FilterDialog(
            years = availableYears, monthNames = monthNames, profiles = profiles,
            currentYear = filterYear, currentMonth = filterMonth,
            currentType = filterType, currentCreator = filterCreator,
            onDismiss = { showFilterDialog = false },
            onApply = { y, m, t, c -> filterYear = y; filterMonth = m; filterType = t; filterCreator = c; showFilterDialog = false }
        )
    }
}

// ---------- SettingsScreen (Cleaned up: Removed Inventory Section) ----------
@Composable
fun SettingsScreen(
    viewModel: AccountingViewModel,
    profiles: List<String>,
    paymentTypes: List<String>,
    workspaces: List<Workspace>,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    rememberWorkspace: Boolean,
    onRememberWorkspaceToggle: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    var newProfileName by remember { mutableStateOf("") }
    var newPaymentTypeName by remember { mutableStateOf("") }

    var profileToDelete by remember { mutableStateOf<String?>(null) }
    var paymentTypeToDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Appearance section
        item {
            Text("Appearance/Database", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Mode", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Switch(checked = isDarkTheme, onCheckedChange = onThemeToggle)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Remember last workspace", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Switch(checked = rememberWorkspace, onCheckedChange = onRememberWorkspaceToggle)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Disconnect from Firebase", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = {
                                ConfigManager.clearConfig(context)
                                (context as? Activity)?.recreate()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }

        // Users section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Manage Users", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it }, label = { Text("New User Name", maxLines = 1) }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (newProfileName.isNotBlank()) { viewModel.addProfile(newProfileName.trim()); newProfileName = "" } }) { Text("Add") }
                }
            }
        }
        items(profiles) { profile ->
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(profile, modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { profileToDelete = profile }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        // Payment types section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Manage Payment Types", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newPaymentTypeName, onValueChange = { newPaymentTypeName = it }, label = { Text("New Payment Type", maxLines = 1) }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (newPaymentTypeName.isNotBlank()) { viewModel.addPaymentType(newPaymentTypeName.trim()); newPaymentTypeName = "" } }) { Text("Add") }
                }
            }
        }
        items(paymentTypes) { pType ->
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(pType, modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { paymentTypeToDelete = pType }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // Confirmation dialogs
    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete User") },
            text = { Text("Delete '${profileToDelete}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profileToDelete!!)
                    scope.launch { snackbarHostState.showSnackbar("User deleted") }
                    profileToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { profileToDelete = null }) { Text("Cancel") } }
        )
    }

    if (paymentTypeToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentTypeToDelete = null },
            title = { Text("Delete Payment Type") },
            text = { Text("Delete '${paymentTypeToDelete}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePaymentType(paymentTypeToDelete!!)
                    scope.launch { snackbarHostState.showSnackbar("Payment type deleted") }
                    paymentTypeToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { paymentTypeToDelete = null }) { Text("Cancel") } }
        )
    }
}

// Define the sorting options for the Inventory Screen
enum class InventorySortType(val label: String) {
    PROFIT_DESC("Profit (High to Low)"),
    PROFIT_ASC("Profit (Low to High)"),
    STOCK_DESC("Stock (High to Low)"),
    STOCK_ASC("Stock (Low to High)"),
    REVENUE_DESC("Revenue (High to Low)"),
    REVENUE_ASC("Revenue (Low to High)"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: AccountingViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val statsList by viewModel.inventoryStats.collectAsState()
    var showAddItemDialog by remember { mutableStateOf(false) }

    // For editing stock via dialog
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var editName by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editStockValue by remember { mutableStateOf("") }

    // For delete confirmation
    var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }

    // --- Search and Sort State ---
    var searchQuery by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(InventorySortType.PROFIT_DESC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Summary Calculations (Based on the full list)
    val totalInventoryValue = statsList.sumOf { it.item.currentStock * it.item.defaultPrice }
    val overallItemProfit = statsList.sumOf { it.netProfit }

    // The dynamically filtered and sorted list
    val filteredAndSortedList = remember(statsList, searchQuery, sortType) {
        // 1. Filter by search query
        val filtered = if (searchQuery.isBlank()) {
            statsList
        } else {
            statsList.filter { it.item.name.contains(searchQuery, ignoreCase = true) }
        }

        // 2. Sort the filtered results
        when (sortType) {
            InventorySortType.PROFIT_DESC -> filtered.sortedByDescending { it.netProfit }
            InventorySortType.PROFIT_ASC -> filtered.sortedBy { it.netProfit }
            InventorySortType.STOCK_DESC -> filtered.sortedByDescending { it.item.currentStock }
            InventorySortType.STOCK_ASC -> filtered.sortedBy { it.item.currentStock }
            InventorySortType.REVENUE_DESC -> filtered.sortedByDescending { it.totalRevenue }
            InventorySortType.REVENUE_ASC -> filtered.sortedBy { it.totalRevenue }
            InventorySortType.NAME_ASC -> filtered.sortedBy { it.item.name.lowercase() }
            InventorySortType.NAME_DESC -> filtered.sortedByDescending { it.item.name.lowercase() }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddItemDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Item") },
                text = { Text("New Stock Item") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // --- TOP SUMMARY CARD ---
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("TOTAL INVENTORY VALUE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("RM ${"%.2f".format(totalInventoryValue)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("OVERALL STOCK PROFIT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${if (overallItemProfit >= 0) "+" else "-"} RM ${"%.2f".format(Math.abs(overallItemProfit))}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overallItemProfit >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Stock Performance", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                // --- CONNECTED DOTS CHART ---
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ConnectedDotsInventoryChart(statsList = statsList)
                }
            }

            // --- SEARCH AND SORT BAR ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Inventory", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .size(56.dp) // Match height of OutlinedTextField
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            InventorySortType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = type.label,
                                            fontWeight = if (sortType == type) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sortType = type
                                        sortMenuExpanded = false
                                    },
                                    trailingIcon = if (sortType == type) {
                                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // --- INDIVIDUAL ITEM CARDS ---
            items(filteredAndSortedList) { stats ->
                val item = stats.item

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header: Name, Stock badge, Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (item.currentStock > 10)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "${item.currentStock} in stock",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.currentStock > 10)
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        editingItem = item
                                        editName = item.name
                                        editPrice = item.defaultPrice.toString()
                                        editStockValue = item.currentStock.toString()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Stock", modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { itemToDelete = item },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Text("Default Price: RM ${"%.2f".format(item.defaultPrice)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Financial Breakdown
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("Cost (Bought)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("RM ${"%.2f".format(stats.totalCost)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Revenue (Sold)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("RM ${"%.2f".format(stats.totalRevenue)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net Profit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "RM ${"%.2f".format(stats.netProfit)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (stats.netProfit >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredAndSortedList.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No items match your search." else "No inventory items yet. Click add to start tracking stock!",
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // --- EDIT STOCK DIALOG ---
    if (editingItem != null) {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit Stock Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Item Name", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Default Price (RM)", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = editStockValue,
                        onValueChange = { editStockValue = it },
                        label = { Text("Stock Quantity", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Current: ${editingItem?.name} • RM ${"%.2f".format(editingItem?.defaultPrice ?: 0.0)} • ${editingItem?.currentStock} in stock",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = editName.trim()
                        val newPrice = editPrice.toDoubleOrNull()
                        val newQty = editStockValue.toIntOrNull()

                        if (newName.isNotBlank() && newPrice != null && newQty != null && newQty >= 0 && editingItem != null) {
                            viewModel.updateInventoryItem(editingItem!!.id, newName, newPrice, newQty)
                            scope.launch { snackbarHostState.showSnackbar("Item updated successfully") }
                            editingItem = null
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Please fill all fields correctly") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("Cancel") }
            }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Inventory Item") },
            text = { Text("Delete '${itemToDelete?.name}'? This will also remove its stock data, but transactions will remain.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteInventoryItem(itemToDelete!!.id)
                        scope.launch { snackbarHostState.showSnackbar("Item deleted") }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // --- ADD ITEM DIALOG ---
    if (showAddItemDialog) {
        var newName by remember { mutableStateOf("") }
        var newPrice by remember { mutableStateOf("") }
        var newQty by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add New Stock Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Item Name", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newPrice,
                            onValueChange = { newPrice = it },
                            label = { Text("Price (RM)", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = newQty,
                            onValueChange = { newQty = it },
                            label = { Text("Initial Stock", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = newPrice.toDoubleOrNull()
                        val qty = newQty.toIntOrNull()
                        if (newName.isNotBlank() && price != null && qty != null) {
                            viewModel.addInventoryItem(newName.trim(), price, qty)
                            showAddItemDialog = false
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Please fill all fields correctly") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- Dialogs ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiItemTransactionDialog(
    profiles: List<String>,
    paymentTypes: List<String>,
    inventoryList: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSave: (type: String, createdBy: String, paymentType: String, items: List<Triple<String, Int, Double>>, receiptBytes: ByteArray?, note: String, affectsStock: Boolean) -> Unit
) {
    var selectedProfile by remember { mutableStateOf(profiles.firstOrNull() ?: "Guest") }
    var selectedPayment by remember { mutableStateOf(paymentTypes.firstOrNull() ?: "Cash") }
    var isSale by remember { mutableStateOf(true) }
    var affectsStock by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    var profileExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }

    var receiptBytes by remember { mutableStateOf<ByteArray?>(null) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }

    data class LineItem(var name: String, var quantity: String, var unitPrice: String = "", var expanded: Boolean = false)
    val items = remember { mutableStateListOf(LineItem("", "1", "")) }

    var detailsExpanded by remember { mutableStateOf(true) }
    var itemsExpanded by remember { mutableStateOf(true) }
    var noteExpanded by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            receiptUri = it
            receiptBytes = try { ImageUtils.compressImageFromUri(context, it) } catch (e: Exception) { null }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            receiptUri?.let {
                receiptBytes = try { ImageUtils.compressImageFromUri(context, it) } catch (e: Exception) { null }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Text(
                "New Transaction",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                CollapsibleSection("Details", detailsExpanded, { detailsExpanded = !detailsExpanded }) {
                    ExposedDropdownMenuBox(
                        expanded = profileExpanded,
                        onExpandedChange = { profileExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedProfile,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Profile", fontSize = 13.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = profileExpanded,
                            onDismissRequest = { profileExpanded = false }
                        ) {
                            profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p) },
                                    onClick = { selectedProfile = p; profileExpanded = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = paymentExpanded,
                        onExpandedChange = { paymentExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedPayment,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Payment", fontSize = 13.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = paymentExpanded,
                            onDismissRequest = { paymentExpanded = false }
                        ) {
                            paymentTypes.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p) },
                                    onClick = { selectedPayment = p; paymentExpanded = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSale, onClick = { isSale = true })
                        Text("Sale")
                        RadioButton(selected = !isSale, onClick = { isSale = false })
                        Text("Expense")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Update Inventory?", modifier = Modifier.weight(1f))
                        Switch(checked = affectsStock, onCheckedChange = { affectsStock = it })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CollapsibleSection("Items", itemsExpanded, { itemsExpanded = !itemsExpanded }) {
                    items.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = item.quantity,
                                    onValueChange = { items[index] = item.copy(quantity = it) },
                                    label = { Text("Qty", fontSize = 13.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.40f).fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )

                                OutlinedTextField(
                                    value = item.unitPrice,
                                    onValueChange = { items[index] = item.copy(unitPrice = it) },
                                    label = { Text("Price (RM)", fontSize = 13.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(0.60f).fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )

                                if (items.size > 1) {
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = item.expanded && inventoryList.isNotEmpty(),
                                onExpandedChange = { items[index] = item.copy(expanded = it) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = item.name,
                                    onValueChange = { input ->
                                        items[index] = item.copy(name = input, expanded = input.isNotBlank())
                                    },
                                    label = { Text("Item Name", fontSize = 13.sp) },
                                    placeholder = { Text("", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = item.expanded) }
                                )
                                ExposedDropdownMenu(
                                    expanded = item.expanded && inventoryList.isNotEmpty(),
                                    onDismissRequest = { items[index] = item.copy(expanded = false) }
                                ) {
                                    inventoryList
                                        .filter { it.name.contains(item.name, ignoreCase = true) && it.name != item.name }
                                        .take(5)
                                        .forEach { stockItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(stockItem.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                                        Text(
                                                            "RM ${"%.2f".format(stockItem.defaultPrice)} (${stockItem.currentStock} left)",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    items[index] = item.copy(
                                                        name = stockItem.name,
                                                        unitPrice = stockItem.defaultPrice.toString(),
                                                        expanded = false
                                                    )
                                                }
                                            )
                                        }
                                }
                            }

                            val qty = item.quantity.toIntOrNull() ?: 0
                            val price = item.unitPrice.toDoubleOrNull() ?: 0.0
                            Text(
                                text = "Total: RM ${"%.2f".format(qty * price)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    TextButton(onClick = { items.add(LineItem("", "1", "")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Text("Add Item")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CollapsibleSection("Note & Receipt", noteExpanded, { noteExpanded = !noteExpanded }) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note / Description", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery")
                        }
                        Button(
                            onClick = {
                                val uri = createCameraUri(context)
                                receiptUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Camera")
                        }
                    }

                    if (receiptUri != null) {
                        Text(
                            text = "Receipt attached!",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validItems = items.mapNotNull {
                        val name = it.name.trim()
                        val qty = it.quantity.toIntOrNull() ?: 1
                        val price = it.unitPrice.toDoubleOrNull() ?: 0.0
                        if (name.isBlank()) null else Triple(name, qty, price)
                    }
                    if (validItems.isNotEmpty()) {
                        onSave(
                            if (isSale) "SALE" else "EXPENSE",
                            selectedProfile,
                            selectedPayment,
                            validItems,
                            receiptBytes,
                            note,
                            affectsStock
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun groupTransactions(transactions: List<Transaction>): List<List<Transaction>> {
    val groups = linkedMapOf<String, MutableList<Transaction>>()
    val ungrouped = mutableListOf<Transaction>()

    for (tx in transactions) {
        if (tx.batchId.isNotEmpty()) {
            groups.getOrPut(tx.batchId) { mutableListOf() }.add(tx)
        } else {
            ungrouped.add(tx)
        }
    }

    val result = groups.values.map { it.toList() }.toMutableList()
    result.addAll(ungrouped.map { listOf(it) })
    result.sortByDescending { group -> group.minOf { it.timestamp } }
    return result
}

@Composable
fun TransactionCard(
    tx: Transaction,
    showActions: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showReceiptViewer by remember { mutableStateOf(false) }
    var showFullNote by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locale

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tx.itemName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "${tx.createdBy} • ${tx.paymentType}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = SimpleDateFormat("dd/MM/yy HH:mm", locale).format(Date(tx.timestamp)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
                if (tx.quantity > 1) {
                    Text(
                        text = "${tx.quantity} × RM${"%.2f".format(tx.unitPrice)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (tx.note.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = "Note",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tx.note.take(60) + if (tx.note.length > 60) "…" else "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showFullNote = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (tx.type == "SALE") "+" else "-"} RM ${"%.2f".format(tx.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = if (tx.type == "SALE") Color(0xFF388E3C) else Color(0xFFD32F2F),
                    fontSize = 16.sp
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    if (tx.receiptUrl != null) {
                        IconButton(onClick = { showReceiptViewer = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = "View Receipt",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (showActions) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showReceiptViewer && tx.receiptUrl != null) {
        AlertDialog(
            onDismissRequest = { showReceiptViewer = false },
            title = { Text("Proof of Purchase") },
            text = {
                AsyncImage(
                    model = tx.receiptUrl,
                    contentDescription = "Receipt Image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                )
            },
            confirmButton = { TextButton(onClick = { showReceiptViewer = false }) { Text("Close") } }
        )
    }

    if (showFullNote && tx.note.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showFullNote = false },
            title = { Text("Note") },
            text = { Text(tx.note) },
            confirmButton = { TextButton(onClick = { showFullNote = false }) { Text("Close") } }
        )
    }
}

@Composable
fun GroupedTransactionCard(
    group: List<Transaction>,
    showActions: Boolean = true,
    onEditItem: (Transaction) -> Unit,
    onDeleteItem: (Transaction) -> Unit
) {
    val first = group.first()
    val totalAmount = group.sumOf { it.amount }
    val type = first.type
    val creator = first.createdBy
    val paymentType = first.paymentType
    val locale = LocalConfiguration.current.locale
    val date = SimpleDateFormat("dd/MM/yy HH:mm", locale).format(Date(first.timestamp))

    var showReceiptViewer by remember { mutableStateOf(false) }
    var selectedReceiptUrl by remember { mutableStateOf<String?>(null) }
    var showFullNote by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf("") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${if (type == "SALE") "Sale" else "Expense"} • $creator",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = paymentType,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = date,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (type == "SALE") "+" else "-"} RM ${"%.2f".format(totalAmount)}",
                        fontWeight = FontWeight.Bold,
                        color = if (type == "SALE") Color(0xFF388E3C) else Color(0xFFD32F2F),
                        fontSize = 16.sp
                    )
                    if (first.receiptUrl != null) {
                        IconButton(onClick = { selectedReceiptUrl = first.receiptUrl; showReceiptViewer = true }, modifier = Modifier.size(32.dp).padding(top = 4.dp)) {
                            Icon(Icons.Default.Receipt, contentDescription = "View Receipt", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            group.forEach { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${tx.quantity} × ${tx.itemName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "RM${"%.2f".format(tx.unitPrice)} each",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tx.note.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Notes,
                                    contentDescription = "Note",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tx.note.take(40) + if (tx.note.length > 40) "…" else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        selectedNote = tx.note
                                        showFullNote = true
                                    }
                                )
                            }
                        }
                    }
                    if (showActions) {
                        Row {
                            if (tx.receiptUrl != null) {
                                IconButton(onClick = { selectedReceiptUrl = tx.receiptUrl; showReceiptViewer = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Receipt, contentDescription = "Receipt", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { onEditItem(tx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { onDeleteItem(tx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReceiptViewer && selectedReceiptUrl != null) {
        AlertDialog(
            onDismissRequest = { showReceiptViewer = false },
            title = { Text("Proof of Purchase") },
            text = {
                AsyncImage(
                    model = selectedReceiptUrl,
                    contentDescription = "Receipt Image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                )
            },
            confirmButton = { TextButton(onClick = { showReceiptViewer = false }) { Text("Close") } }
        )
    }

    if (showFullNote && selectedNote.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showFullNote = false },
            title = { Text("Note") },
            text = { Text(selectedNote) },
            confirmButton = { TextButton(onClick = { showFullNote = false }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    profiles: List<String>,
    paymentTypes: List<String>,
    onDismiss: () -> Unit,
    onSave: (id: String, type: String, name: String, amount: Double, quantity: Int, unitPrice: Double, createdBy: String, paymentType: String, batchId: String, note: String, receiptBytes: ByteArray?) -> Unit
) {
    var editName by remember { mutableStateOf(transaction.itemName) }
    var editQty by remember { mutableStateOf(transaction.quantity.toString()) }
    var editPrice by remember { mutableStateOf(transaction.unitPrice.toString()) }
    var editIsSale by remember { mutableStateOf(transaction.type == "SALE") }
    var editCreatedBy by remember { mutableStateOf(transaction.createdBy) }
    var editPaymentType by remember { mutableStateOf(transaction.paymentType) }
    var editNote by remember { mutableStateOf(transaction.note) }

    var profileExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }

    val calculatedAmount = (editQty.toIntOrNull() ?: 0) * (editPrice.toDoubleOrNull() ?: 0.0)

    val context = LocalContext.current
    var receiptBytes by remember { mutableStateOf<ByteArray?>(null) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    val hasExistingReceipt = transaction.receiptUrl != null

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            receiptUri = it
            receiptBytes = try { ImageUtils.compressImageFromUri(context, it) } catch (e: Exception) { null }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            receiptUri?.let {
                receiptBytes = try { ImageUtils.compressImageFromUri(context, it) } catch (e: Exception) { null }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Text(
                "Edit Transaction",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = profileExpanded,
                        onExpandedChange = { profileExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = editCreatedBy,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Profile", fontSize = 13.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = profileExpanded,
                            onDismissRequest = { profileExpanded = false }
                        ) {
                            profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p) },
                                    onClick = { editCreatedBy = p; profileExpanded = false }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = paymentExpanded,
                        onExpandedChange = { paymentExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = editPaymentType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Payment", fontSize = 13.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = paymentExpanded,
                            onDismissRequest = { paymentExpanded = false }
                        ) {
                            paymentTypes.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p) },
                                    onClick = { editPaymentType = p; paymentExpanded = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { editQty = it },
                        label = { Text("Qty", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(60.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Item", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Price", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total: RM ${"%.2f".format(calculatedAmount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(start = 78.dp),
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = editIsSale, onClick = { editIsSale = true })
                    Text("Sale")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !editIsSale, onClick = { editIsSale = false })
                    Text("Expense")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = { Text("Note / Description", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Receipt", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Gallery",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = {
                            val uri = createCameraUri(context)
                            receiptUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Camera",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    }
                }

                if (receiptUri != null) {
                    Text(
                        "New receipt selected ✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (hasExistingReceipt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "Existing receipt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Existing receipt (will be kept unless you pick a new one)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        "No receipt attached",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = editQty.toIntOrNull() ?: 1
                    val price = editPrice.toDoubleOrNull() ?: 0.0
                    val total = qty * price
                    if (editName.isNotBlank() && total > 0) {
                        onSave(
                            transaction.id,
                            if (editIsSale) "SALE" else "EXPENSE",
                            editName,
                            total,
                            qty,
                            price,
                            editCreatedBy,
                            editPaymentType,
                            transaction.batchId,
                            editNote,
                            receiptBytes
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Section"
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceMakerScreen(
    inventoryList: List<InventoryItem>,
    viewModel: AccountingViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("stonks_settings", Context.MODE_PRIVATE)

    var senderName by remember { mutableStateOf(prefs.getString("sender_name", "") ?: "") }
    var senderContact by remember { mutableStateOf(prefs.getString("sender_contact", "") ?: "") }

    var clientName by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var clientContact by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf("") }

    var senderExpanded by remember { mutableStateOf(senderName.isBlank()) }
    var clientExpanded by remember { mutableStateOf(true) }
    var itemsExpanded by remember { mutableStateOf(true) }
    var notesExpanded by remember { mutableStateOf(false) }

    val currentInvoiceId = prefs.getInt("last_invoice_id", 1)
    val invoiceNumber by remember(currentInvoiceId) {
        mutableStateOf("INV-${String.format("%04d", currentInvoiceId)}")
    }

    data class InvoiceItem(var name: String, var quantity: String, var unitPrice: String, var expanded: Boolean = false)
    val items = remember { mutableStateListOf(InvoiceItem("", "1", "")) }

    val currentTotal = items.sumOf {
        val q = it.quantity.toIntOrNull() ?: 0
        val p = it.unitPrice.toDoubleOrNull() ?: 0.0
        q * p
    }

    fun processInvoice(shareMode: Boolean) {
        val validItems = items.mapNotNull {
            val name = it.name.trim()
            val qty = it.quantity.toIntOrNull()
            val price = it.unitPrice.toDoubleOrNull()
            if (name.isNotBlank() && qty != null && price != null) Triple(name, qty, price) else null
        }

        if (clientName.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Client Name is required") }
        } else if (validItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Add at least one valid item") }
        } else {
            try {
                prefs.edit()
                    .putString("sender_name", senderName)
                    .putString("sender_contact", senderContact)
                    .apply()

                PdfUtils.generateAndShareInvoice(
                    context = context,
                    invoiceNumber = invoiceNumber,
                    senderName = senderName.trim(),
                    senderContact = senderContact.trim(),
                    clientName = clientName.trim(),
                    clientAddress = clientAddress.trim(),
                    clientContact = clientContact.trim(),
                    notes = notes.trim(),
                    items = validItems,
                    shareMode = shareMode
                )

                if (shareMode) {
                    validItems.forEach { (soldName, soldQty, _) ->
                        val matchedItem = inventoryList.find { it.name.equals(soldName, ignoreCase = true) }
                        if (matchedItem != null) {
                            viewModel.deductStock(matchedItem.id, soldQty)
                        }
                    }
                    prefs.edit().putInt("last_invoice_id", currentInvoiceId + 1).apply()
                    scope.launch { snackbarHostState.showSnackbar("Invoice Generated & Stock Updated!") }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Error: ${e.localizedMessage}") }
            }
        }
    }

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Due", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("RM ${"%.2f".format(currentTotal)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { processInvoice(shareMode = false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preview")
                        }
                        Button(
                            onClick = { processInvoice(shareMode = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share & Save")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                CollapsibleSection(
                    title = "Your Details (Sender)",
                    expanded = senderExpanded,
                    onToggle = { senderExpanded = !senderExpanded }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = senderName, onValueChange = { senderName = it },
                            label = { Text("Business Name") }, modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            value = senderContact, onValueChange = { senderContact = it },
                            label = { Text("Phone / Email") }, modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                }
            }

            item {
                CollapsibleSection(
                    title = "Bill To (Client) - #$invoiceNumber",
                    expanded = clientExpanded,
                    onToggle = { clientExpanded = !clientExpanded }
                ) {
                    OutlinedTextField(
                        value = clientName, onValueChange = { clientName = it },
                        label = { Text("Client/Company Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clientContact, onValueChange = { clientContact = it },
                        label = { Text("Contact Info (Phone/Email)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clientAddress, onValueChange = { clientAddress = it },
                        label = { Text("Address (Optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                    )
                }
            }

            item {
                CollapsibleSection(
                    title = "Line Items",
                    expanded = itemsExpanded,
                    onToggle = { itemsExpanded = !itemsExpanded }
                ) {
                    items.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = item.quantity,
                                    onValueChange = { items[index] = item.copy(quantity = it) },
                                    label = { Text("Qty", fontSize = 13.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.40f).fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )

                                OutlinedTextField(
                                    value = item.unitPrice,
                                    onValueChange = { items[index] = item.copy(unitPrice = it) },
                                    label = { Text("Price (RM)", fontSize = 13.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(0.60f).fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )

                                if (items.size > 1) {
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }

                            var expanded by remember(item.expanded) { mutableStateOf(item.expanded) }
                            ExposedDropdownMenuBox(
                                expanded = expanded && inventoryList.isNotEmpty(),
                                onExpandedChange = { expanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = item.name,
                                    onValueChange = { input ->
                                        items[index] = item.copy(name = input, expanded = input.isNotBlank())
                                        expanded = input.isNotBlank()
                                    },
                                    label = { Text("Item Name", fontSize = 13.sp) },
                                    placeholder = { Text("Start typing to see suggestions", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded && inventoryList.isNotEmpty(),
                                    onDismissRequest = { expanded = false }
                                ) {
                                    inventoryList
                                        .filter { it.name.contains(item.name, ignoreCase = true) && it.name != item.name }
                                        .take(5)
                                        .forEach { stockItem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(stockItem.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                                        Text(
                                                            "RM ${"%.2f".format(stockItem.defaultPrice)} (${stockItem.currentStock} left)",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    items[index] = item.copy(
                                                        name = stockItem.name,
                                                        unitPrice = stockItem.defaultPrice.toString(),
                                                        expanded = false
                                                    )
                                                    expanded = false
                                                }
                                            )
                                        }
                                }
                            }

                            val qty = item.quantity.toIntOrNull() ?: 0
                            val price = item.unitPrice.toDoubleOrNull() ?: 0.0
                            Text(
                                text = "Total: RM ${"%.2f".format(qty * price)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    TextButton(onClick = { items.add(InvoiceItem("", "1", "")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add another item")
                    }
                }
            }

            item {
                CollapsibleSection(
                    title = "Notes",
                    expanded = notesExpanded,
                    onToggle = { notesExpanded = !notesExpanded }
                ) {
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Optional Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    years: List<Int>, monthNames: List<String>, profiles: List<String>,
    currentYear: String?, currentMonth: Int?, currentType: String, currentCreator: String,
    onDismiss: () -> Unit, onApply: (year: String?, month: Int?, type: String, creator: String) -> Unit
) {
    var year by remember { mutableStateOf(currentYear ?: "All") }
    var month by remember { mutableStateOf(currentMonth?.toString() ?: "All") }
    var type by remember { mutableStateOf(currentType) }
    var creator by remember { mutableStateOf(currentCreator) }
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var creatorExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Filters", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = yearExpanded, onExpandedChange = { yearExpanded = it }) {
                    OutlinedTextField(
                        value = if (year == "All") "All years" else year, onValueChange = {}, readOnly = true, singleLine = true,
                        label = { Text("Year") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        DropdownMenuItem(text = { Text("All years") }, onClick = { year = "All"; yearExpanded = false })
                        years.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { year = y.toString(); yearExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = monthExpanded, onExpandedChange = { monthExpanded = it }) {
                    OutlinedTextField(
                        value = if (month == "All") "All months" else monthNames.getOrElse(month.toIntOrNull()?.minus(1) ?: -1) { "?" },
                        onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Month") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        DropdownMenuItem(text = { Text("All months") }, onClick = { month = "All"; monthExpanded = false })
                        for (i in 1..12) { DropdownMenuItem(text = { Text(monthNames[i-1]) }, onClick = { month = i.toString(); monthExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = type, onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        listOf("All", "SALE", "EXPENSE").forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { type = t; typeExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = creatorExpanded, onExpandedChange = { creatorExpanded = it }) {
                    OutlinedTextField(value = creator, onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Creator") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = creatorExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = creatorExpanded, onDismissRequest = { creatorExpanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        DropdownMenuItem(text = { Text("All") }, onClick = { creator = "All"; creatorExpanded = false })
                        profiles.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { creator = p; creatorExpanded = false }) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(if (year == "All") null else year, if (month == "All") null else month.toIntOrNull(), type, creator) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { onApply(null, null, "All", "All") }) { Text("Clear all") } }
    )
}

// ============================================================
// NEW CALCULATOR COMPOSABLE
// ============================================================
@Composable
fun CalculatorScreen() {
    var input by remember { mutableStateOf("0") }
    var previousInput by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var isNewOperand by remember { mutableStateOf(true) }

    fun onAction(action: String) {
        when (action) {
            "C" -> {
                input = "0"
                previousInput = ""
                operator = ""
                isNewOperand = true
            }
            "⌫" -> {
                if (input.length > 1) input = input.dropLast(1)
                else input = "0"
            }
            "+", "-", "×", "÷" -> {
                if (operator.isNotEmpty() && !isNewOperand) {
                    val val1 = previousInput.toDoubleOrNull() ?: 0.0
                    val val2 = input.toDoubleOrNull() ?: 0.0
                    val res = when(operator) {
                        "+" -> val1 + val2
                        "-" -> val1 - val2
                        "×" -> val1 * val2
                        "÷" -> if (val2 != 0.0) val1 / val2 else 0.0
                        else -> 0.0
                    }
                    input = if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
                }
                previousInput = input
                operator = action
                isNewOperand = true
            }
            "=" -> {
                if (operator.isNotEmpty()) {
                    val val1 = previousInput.toDoubleOrNull() ?: 0.0
                    val val2 = input.toDoubleOrNull() ?: 0.0
                    val res = when(operator) {
                        "+" -> val1 + val2
                        "-" -> val1 - val2
                        "×" -> val1 * val2
                        "÷" -> if (val2 != 0.0) val1 / val2 else 0.0
                        else -> 0.0
                    }
                    input = if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
                    operator = ""
                    previousInput = ""
                    isNewOperand = true
                }
            }
            "." -> {
                if (isNewOperand) {
                    input = "0."
                    isNewOperand = false
                } else if (!input.contains(".")) {
                    input += "."
                }
            }
            else -> {
                if (isNewOperand) {
                    input = action
                    isNewOperand = false
                } else {
                    if (input == "0") input = action else input += action
                }
            }
        }
    }

    val buttons = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "=")
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = previousInput + if (operator.isNotEmpty()) " $operator" else "",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = input,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 32.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { btn ->
                    Button(
                        onClick = {
                            if (btn == "%") {
                                val current = input.toDoubleOrNull() ?: 0.0
                                input = (current / 100.0).toString()
                            } else {
                                onAction(btn)
                            }
                        },
                        modifier = Modifier
                            .weight(if (btn == "0") 2f else 1f)
                            .aspectRatio(if (btn == "0") 2f else 1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                btn in listOf("÷", "×", "-", "+", "=") -> MaterialTheme.colorScheme.primary
                                btn in listOf("C", "⌫", "%") -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                btn in listOf("÷", "×", "-", "+", "=") -> MaterialTheme.colorScheme.onPrimary
                                btn in listOf("C", "⌫", "%") -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Text(text = btn, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedDotsInventoryChart(statsList: List<InventoryStats>) {
    if (statsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("Not enough data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Colors for our three metrics
    val profitColor = Color(0xFF388E3C) // Green
    val priceColor = Color(0xFF1976D2)  // Blue
    val stockColor = Color(0xFFF57C00)  // Orange
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    // Find the max values to normalize the scale
    val maxProfit = statsList.maxOfOrNull { it.netProfit.toFloat() }?.coerceAtLeast(1f) ?: 1f
    val maxPrice = statsList.maxOfOrNull { it.item.defaultPrice.toFloat() }?.coerceAtLeast(1f) ?: 1f
    val maxStock = statsList.maxOfOrNull { it.item.currentStock.toFloat() }?.coerceAtLeast(1f) ?: 1f

    // State to hold the currently tapped/selected item index
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // Extract to a local variable to prevent smart-cast and @Composable inline lambda errors
    val currentIndex = selectedIndex

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {

        // --- DETAILS CARD (Shows Exact Numbers when tapped) ---
        AnimatedVisibility(visible = currentIndex != null) {
            if (currentIndex != null) {
                val stat = statsList[currentIndex]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stat.item.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // FIXED LAYOUT: Using Columns with weights for perfect spacing
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text("Stock", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${stat.item.currentStock}", color = stockColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Price", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("RM ${"%.2f".format(stat.item.defaultPrice)}", color = priceColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("Profit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("RM ${"%.2f".format(stat.netProfit)}", color = profitColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- THE CHART CANVAS ---
        Row(modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Y-Axis Scale Indicators
            Column(
                modifier = Modifier.fillMaxHeight().width(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Max", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(statsList) {
                            detectTapGestures { offset ->
                                val spacing = if (statsList.size > 1) size.width / (statsList.size - 1) else size.width / 2
                                val index = (offset.x / spacing).roundToInt().coerceIn(0, statsList.lastIndex)
                                selectedIndex = index
                            }
                        }
                        .pointerInput(statsList) {
                            detectDragGestures { change, _ ->
                                val spacing = if (statsList.size > 1) size.width / (statsList.size - 1) else size.width / 2
                                val index = (change.position.x / spacing).roundToInt().coerceIn(0, statsList.lastIndex)
                                selectedIndex = index
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    // Draw horizontal grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = height * (i.toFloat() / gridLines)
                        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 2f)
                    }

                    val spacing = if (statsList.size > 1) width / (statsList.size - 1) else width / 2

                    // If an item is selected, draw a vertical highlight line
                    currentIndex?.let { index ->
                        val x = if (statsList.size > 1) index * spacing else spacing
                        drawLine(
                            color = gridColor.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 4.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    // Helper function to draw a line + dots for a specific metric
                    fun drawMetricLine(
                        metricExtractor: (InventoryStats) -> Float,
                        maxVal: Float,
                        lineColor: Color
                    ) {
                        val path = androidx.compose.ui.graphics.Path()

                        statsList.forEachIndexed { index, stat ->
                            val x = if (statsList.size > 1) index * spacing else spacing
                            val rawValue = metricExtractor(stat)
                            val normalized = (Math.abs(rawValue) / maxVal).coerceIn(0f, 1f)
                            val y = height - (normalized * height)

                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Make the dot slightly larger if it's the currently selected one
                            val isSelected = index == currentIndex
                            val radiusOuter = if (isSelected) 8.dp.toPx() else 5.dp.toPx()
                            val radiusInner = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx()

                            drawCircle(color = lineColor, radius = radiusOuter, center = Offset(x, y))
                            drawCircle(color = Color.White, radius = radiusInner, center = Offset(x, y))
                        }
                    }

                    // Draw the three series
                    drawMetricLine({ it.item.currentStock.toFloat() }, maxStock, stockColor)
                    drawMetricLine({ it.item.defaultPrice.toFloat() }, maxPrice, priceColor)
                    drawMetricLine({ it.netProfit.toFloat() }, maxProfit, profitColor)
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(color = profitColor, label = "Profit")
            LegendItem(color = priceColor, label = "Price")
            LegendItem(color = stockColor, label = "Stock")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}