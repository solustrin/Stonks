package com.trin.stonks

import androidx.compose.animation.core.Animatable
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.trin.stonks.data.*
import com.trin.stonks.ui.theme.StonksTheme
import com.trin.stonks.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormatSymbols
import java.util.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trin.stonks.utils.PdfUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.tween

fun createCameraUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera_images").apply { mkdirs() }
    val file = File(folder, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "com.trin.stonks.fileprovider", file)
}

enum class StonksScreen(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    History("Transactions", Icons.AutoMirrored.Filled.List),
    Invoice("Invoice", Icons.Default.Receipt),
    Settings("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cloudRepository = CloudRepository()
        val factory = AccountingViewModelFactory(cloudRepository)
        val viewModel = ViewModelProvider(this, factory)[AccountingViewModel::class.java]

        val prefs = getSharedPreferences("stonks_settings", Context.MODE_PRIVATE)
        val lastWorkspaceId = prefs.getString("workspace_id", null)

        setContent {
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
            var workspaceSelected by remember { mutableStateOf(false) }
            var rememberWorkspace by remember { mutableStateOf(prefs.getBoolean("remember_workspace", true)) }   // <-- new

            val workspaces by viewModel.workspaces.collectAsState()

            // Auto-select last used workspace only if "rememberWorkspace" is true
            LaunchedEffect(workspaces) {
                if (rememberWorkspace && !workspaceSelected && lastWorkspaceId != null && workspaces.any { it.id == lastWorkspaceId }) {
                    viewModel.setWorkspace(lastWorkspaceId)
                    workspaceSelected = true
                }
            }

            StonksTheme(darkTheme = isDarkTheme) {
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
                        rememberWorkspace = rememberWorkspace,                    // <-- new
                        onRememberWorkspaceToggle = { newValue ->                // <-- new
                            rememberWorkspace = newValue
                            prefs.edit().putBoolean("remember_workspace", newValue).apply()
                        }
                    )
                }
            }
        }
    }
}

// ---------- Workspace Selection Screen (unchanged) ----------
@Composable
fun WorkspaceSelectionScreen(
    workspaces: List<Workspace>,
    onWorkspaceSelected: (String) -> Unit,
    onWorkspaceCreated: (String) -> Unit,
    viewModel: AccountingViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var workspaceToDelete by remember { mutableStateOf<Workspace?>(null) }
    var workspaceToEdit by remember { mutableStateOf<Workspace?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Select Workspace",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Choose or create a workspace to start tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (workspaces.isEmpty()) {
                Text("No workspaces yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
            }

            workspaces.forEach { ws ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onWorkspaceSelected(ws.id) },
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ws.name, fontWeight = FontWeight.Medium)
                        }
                        // Edit button
                        IconButton(onClick = { workspaceToEdit = ws }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit workspace name",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Delete button
                        IconButton(onClick = { workspaceToDelete = ws }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete workspace",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Workspace")
            }
        }
    }

    // Create workspace dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Workspace") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Workspace Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.createWorkspace(newName.trim())
                        showCreateDialog = false
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

    // Delete workspace confirmation dialog
    if (workspaceToDelete != null) {
        AlertDialog(
            onDismissRequest = { workspaceToDelete = null },
            title = { Text("Delete Workspace") },
            text = { Text("Delete '${workspaceToDelete?.name}'? All data inside will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkspace(workspaceToDelete!!.id)
                    workspaceToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Edit workspace name dialog
    if (workspaceToEdit != null) {
        var editedName by remember(workspaceToEdit) { mutableStateOf(workspaceToEdit!!.name) }
        AlertDialog(
            onDismissRequest = { workspaceToEdit = null },
            title = { Text("Rename Workspace") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editedName.isNotBlank() && editedName != workspaceToEdit!!.name) {
                        viewModel.updateWorkspaceName(workspaceToEdit!!.id, editedName.trim())
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
}

// ---------- Main Navigation Shell (updated signature + passes new params) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationShell(
    viewModel: AccountingViewModel,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onWorkspaceChanged: (String) -> Unit,
    onExitToWorkspaceSelection: () -> Unit,
    rememberWorkspace: Boolean,                    // new param
    onRememberWorkspaceToggle: (Boolean) -> Unit   // new param
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by remember { mutableStateOf(StonksScreen.Dashboard) }
    var showAddDialog by remember { mutableStateOf(false) }

    val profiles by viewModel.profiles.collectAsState()
    val paymentTypes by viewModel.paymentTypes.collectAsState()
    val uniqueItems by viewModel.uniqueItemNames.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val currentWorkspaceId by viewModel.currentWorkspaceId.collectAsState()

    val currentWorkspaceName = workspaces.find { it.id == currentWorkspaceId }?.name ?: "Default"

    // If the current workspace is deleted while we're inside it, force exit to selection screen
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

                // Navigation items
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

                // Current workspace info and switch button
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

                // Other workspaces (quick switch)
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
                        StonksScreen.History -> TransactionHistoryScreen(
                            viewModel, profiles, paymentTypes, snackbarHostState, scope
                        )

                        StonksScreen.Invoice -> InvoiceMakerScreen(
                            suggestedItems = uniqueItems,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )

                        StonksScreen.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            profiles = profiles,
                            paymentTypes = paymentTypes,
                            workspaces = workspaces,
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = onThemeToggle,
                            rememberWorkspace = rememberWorkspace,                    // new param
                            onRememberWorkspaceToggle = onRememberWorkspaceToggle,   // new param
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
            suggestedItems = uniqueItems,
            onDismiss = { showAddDialog = false },
            onSave = { type, createdBy, paymentType, items, receiptBytes ->
                viewModel.addTransactions(type, createdBy, paymentType, items, receiptBytes) { success, errorMsg ->
                    showAddDialog = false
                    scope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("Transactions added")
                        } else {
                            snackbarHostState.showSnackbar("Upload Failed: $errorMsg")
                        }
                    }
                }
            }
        )
    }
}

// ---------- Dashboard (unchanged) ----------
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

// ---------- ProfitBarChart (unchanged) ----------
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
                                    .fillMaxHeight((targetFraction * progress.value).coerceAtLeast(0f))   // ← use .value
                                    .defaultMinSize(minHeight = 2.dp)
                                    .background(barColor, RoundedCornerShape(6.dp))   // ← Color, not Float
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

// ---------- Circular Donut Chart (unchanged) ----------
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
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)   // changed to Butt
                    )
                } else {
                    if (animateRev > 0f) {
                        drawArc(
                            color = Color(0xFF388E3C),
                            startAngle = -90f, sweepAngle = animateRev, useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)   // changed to Butt
                        )
                    }
                    if (animateExp > 0f) {
                        drawArc(
                            color = Color(0xFFD32F2F),
                            startAngle = -90f + animateRev, sweepAngle = animateExp, useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)   // changed to Butt
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

// ---------- Transaction History (unchanged) ----------
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
            transaction = transactionToEdit!!, profiles = profiles, paymentTypes = paymentTypes,
            onDismiss = { transactionToEdit = null },
            onSave = { id, type, name, amount, quantity, unitPrice, createdBy, paymentType, batchId ->
                viewModel.updateTransaction(id, type, name, amount, quantity, unitPrice, createdBy, paymentType, batchId)
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

// ---------- SettingsScreen (added "Remember last workspace" switch) ----------
@Composable
fun SettingsScreen(
    viewModel: AccountingViewModel,
    profiles: List<String>,
    paymentTypes: List<String>,
    workspaces: List<Workspace>,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    rememberWorkspace: Boolean,                    // new param
    onRememberWorkspaceToggle: (Boolean) -> Unit,  // new param
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    var newProfileName by remember { mutableStateOf("") }
    var newPaymentTypeName by remember { mutableStateOf("") }

    var profileToDelete by remember { mutableStateOf<String?>(null) }
    var paymentTypeToDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Appearance section
        item {
            Text("Appearance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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

// ---------- Dialogs (unchanged) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiItemTransactionDialog(
    profiles: List<String>,
    paymentTypes: List<String>,
    suggestedItems: List<String>,
    onDismiss: () -> Unit,
    onSave: (type: String, createdBy: String, paymentType: String, items: List<Triple<String, Int, Double>>, receiptBytes: ByteArray?) -> Unit
) {
    var selectedProfile by remember { mutableStateOf(profiles.firstOrNull() ?: "Guest") }
    var selectedPayment by remember { mutableStateOf(paymentTypes.firstOrNull() ?: "Cash") }
    var isSale by remember { mutableStateOf(true) }
    var profileExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }
    var bulkMode by remember { mutableStateOf(false) }
    var bulkPrice by remember { mutableStateOf("") }

    data class LineItem(var name: String, var quantity: String, var unitPrice: String = "", var expanded: Boolean = false)
    val items = remember { mutableStateListOf(LineItem("", "1", "")) }

    val context = LocalContext.current
    var receiptUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) receiptUri = uri }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) receiptUri = tempCameraUri }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "New Transaction",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = it }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedProfile, onValueChange = {}, readOnly = true,
                                label = { Text("Profile", maxLines = 1) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine = true
                            )
                            ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                                profiles.forEach { p -> DropdownMenuItem(text = { Text(p, maxLines = 1) }, onClick = { selectedProfile = p; profileExpanded = false }) }
                            }
                        }
                        ExposedDropdownMenuBox(expanded = paymentExpanded, onExpandedChange = { paymentExpanded = it }, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedPayment, onValueChange = {}, readOnly = true,
                                label = { Text("Payment", maxLines = 1) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine = true
                            )
                            ExposedDropdownMenu(expanded = paymentExpanded, onDismissRequest = { paymentExpanded = false }) {
                                paymentTypes.forEach { p -> DropdownMenuItem(text = { Text(p, maxLines = 1) }, onClick = { selectedPayment = p; paymentExpanded = false }) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = isSale, onClick = { isSale = true }); Text("Sale")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = !isSale, onClick = { isSale = false }); Text("Expense")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Bulk Mode (Total Price)", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Switch(checked = bulkMode, onCheckedChange = { bulkMode = it })
                    }

                    AnimatedVisibility(visible = bulkMode, enter = expandVertically(), exit = shrinkVertically()) {
                        OutlinedTextField(
                            value = bulkPrice,
                            onValueChange = { bulkPrice = it },
                            label = { Text("Total Price (RM)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Items", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Column(modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                        items.forEachIndexed { index, item ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = item.quantity,
                                        onValueChange = { items[index] = item.copy(quantity = it) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(50.dp),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    OutlinedTextField(
                                        value = item.name,
                                        onValueChange = { items[index] = item.copy(name = it, expanded = true) },
                                        placeholder = { Text("Name") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    if (!bulkMode) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        OutlinedTextField(
                                            value = item.unitPrice,
                                            onValueChange = { items[index] = item.copy(unitPrice = it) },
                                            prefix = { Text("RM ") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.width(80.dp),
                                            singleLine = true
                                        )
                                    }
                                    if (items.size > 1) {
                                        IconButton(onClick = { items.removeAt(index) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                val filtered = suggestedItems.filter { it.contains(item.name, ignoreCase = true) && it != item.name }
                                if (item.expanded && item.name.isNotBlank() && filtered.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.padding(start = 54.dp, end = 4.dp).fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column {
                                            filtered.take(3).forEach { suggestion ->
                                                Text(
                                                    text = suggestion,
                                                    modifier = Modifier.fillMaxWidth().clickable { items[index] = item.copy(name = suggestion, expanded = false) }.padding(12.dp),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { items.add(LineItem("", "1", "")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null); Text("Add another item")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row {
                            IconButton(onClick = { val uri = createCameraUri(context); tempCameraUri = uri; cameraLauncher.launch(uri) }) {
                                Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        AnimatedVisibility(visible = receiptUri != null, enter = scaleIn(), exit = scaleOut()) {
                            Text("Attached", color = Color(0xFF388E3C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            try {
                                val receiptBytes = receiptUri?.let { ImageUtils.compressImageFromUri(context, it) }
                                if (bulkMode) {
                                    val validItems = items.filter { it.name.isNotBlank() }
                                    if (validItems.isNotEmpty()) {
                                        val combinedName = validItems.joinToString(", ") { "${it.quantity}x ${it.name.trim()}" }
                                        val totalPrice = bulkPrice.toDoubleOrNull() ?: 0.0
                                        if (totalPrice > 0) onSave(if (isSale) "SALE" else "EXPENSE", selectedProfile, selectedPayment, listOf(Triple(combinedName, 1, totalPrice)), receiptBytes)
                                        else isSaving = false
                                    } else isSaving = false
                                } else {
                                    val validItems = items.mapNotNull {
                                        val name = it.name.trim()
                                        val qty = it.quantity.toIntOrNull() ?: return@mapNotNull null
                                        val price = it.unitPrice.toDoubleOrNull() ?: return@mapNotNull null
                                        if (name.isBlank()) null else Triple(name, qty, price)
                                    }
                                    if (validItems.isNotEmpty()) onSave(if (isSale) "SALE" else "EXPENSE", selectedProfile, selectedPayment, validItems, receiptBytes)
                                    else isSaving = false
                                }
                            } catch (e: Exception) { isSaving = false }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
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
                    text = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(tx.timestamp)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
                if (tx.quantity > 1) {
                    Text(
                        text = "${tx.quantity} × RM${"%.2f".format(tx.unitPrice)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            Icon(Icons.Default.Receipt, contentDescription = "View Receipt", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
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
    val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(first.timestamp))

    var showReceiptViewer by remember { mutableStateOf(false) }

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
                        maxLines = 1, overflow = TextOverflow.Ellipsis
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
                        IconButton(onClick = { showReceiptViewer = true }, modifier = Modifier.size(32.dp).padding(top = 4.dp)) {
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
                    }
                    if (showActions) {
                        Row {
                            IconButton(onClick = { onEditItem(tx) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDeleteItem(tx) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReceiptViewer && first.receiptUrl != null) {
        AlertDialog(
            onDismissRequest = { showReceiptViewer = false },
            title = { Text("Proof of Purchase") },
            text = {
                AsyncImage(
                    model = first.receiptUrl,
                    contentDescription = "Receipt Image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                )
            },
            confirmButton = { TextButton(onClick = { showReceiptViewer = false }) { Text("Close") } }
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
    onSave: (id: String, type: String, name: String, amount: Double, quantity: Int, unitPrice: Double, createdBy: String, paymentType: String, batchId: String) -> Unit
) {
    var editName by remember { mutableStateOf(transaction.itemName) }
    var editQty by remember { mutableStateOf(transaction.quantity.toString()) }
    var editPrice by remember { mutableStateOf(transaction.unitPrice.toString()) }
    var editIsSale by remember { mutableStateOf(transaction.type == "SALE") }
    var editCreatedBy by remember { mutableStateOf(transaction.createdBy) }
    var editPaymentType by remember { mutableStateOf(transaction.paymentType) }
    var profileExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }

    val calculatedAmount = (editQty.toIntOrNull() ?: 0) * (editPrice.toDoubleOrNull() ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit Transaction", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = editCreatedBy, onValueChange = {}, readOnly = true,
                            label = { Text("Profile", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                            profiles.forEach { p -> DropdownMenuItem(text = { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { editCreatedBy = p; profileExpanded = false }) }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = paymentExpanded, onExpandedChange = { paymentExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = editPaymentType, onValueChange = {}, readOnly = true,
                            label = { Text("Payment", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = paymentExpanded, onDismissRequest = { paymentExpanded = false }) {
                            paymentTypes.forEach { p -> DropdownMenuItem(text = { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { editPaymentType = p; paymentExpanded = false }) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Item Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { editQty = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Unit Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total: RM ${"%.2f".format(calculatedAmount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = editIsSale, onClick = { editIsSale = true }); Text("Sale")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !editIsSale, onClick = { editIsSale = false }); Text("Expense")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val qty = editQty.toIntOrNull() ?: 1
                val price = editPrice.toDoubleOrNull() ?: 0.0
                val total = qty * price
                if (editName.isNotBlank() && total > 0) {
                    onSave(transaction.id, if (editIsSale) "SALE" else "EXPENSE", editName, total, qty, price, editCreatedBy, editPaymentType, transaction.batchId)
                }
            }) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- HELPER COMPOSABLE FOR SMOOTH TOGGLES ---
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

            // By removing animateContentSize() above and defining the same duration
            // for both enter and exit transitions, the container and content sync perfectly.
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
    suggestedItems: List<String>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("stonks_settings", Context.MODE_PRIVATE)

    // Form State
    var senderName by remember { mutableStateOf(prefs.getString("sender_name", "") ?: "") }
    var senderContact by remember { mutableStateOf(prefs.getString("sender_contact", "") ?: "") }

    var clientName by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var clientContact by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf("") }

    // Toggle States (Sender collapsed by default if data exists)
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

    // Helper to validate and execute PDF generation
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
                    prefs.edit().putInt("last_invoice_id", currentInvoiceId + 1).apply()
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
                            Text("Share")
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

            // --- SENDER SECTION ---
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

            // --- CLIENT SECTION ---
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

            // --- LINE ITEMS ---
            item {
                CollapsibleSection(
                    title = "Line Items",
                    expanded = itemsExpanded,
                    onToggle = { itemsExpanded = !itemsExpanded }
                ) {
                    items.forEachIndexed { index, item ->
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = item.quantity, onValueChange = { items[index] = item.copy(quantity = it) },
                                    label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(64.dp), singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = item.name, onValueChange = { items[index] = item.copy(name = it, expanded = true) },
                                    label = { Text("Item Description") }, modifier = Modifier.weight(1f), singleLine = true
                                )
                                if (items.size > 1) {
                                    IconButton(onClick = { items.removeAt(index) }, modifier = Modifier.padding(start = 4.dp)) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            val filtered = suggestedItems.filter { it.contains(item.name, ignoreCase = true) && it != item.name }
                            if (item.expanded && item.name.isNotBlank() && filtered.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.padding(start = 72.dp, top = 4.dp).fillMaxWidth(0.9f),
                                    shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column {
                                        filtered.take(3).forEach { suggestion ->
                                            Text(
                                                text = suggestion,
                                                modifier = Modifier.fillMaxWidth().clickable { items[index] = item.copy(name = suggestion, expanded = false) }.padding(12.dp),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = item.unitPrice, onValueChange = { items[index] = item.copy(unitPrice = it) },
                                label = { Text("Unit Price (RM)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(), singleLine = true
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

            // --- NOTES SECTION ---
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

