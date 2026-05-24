package com.frerox.toolz.ui.screens.network

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.network.DnsBenchmarkResult
import com.frerox.toolz.data.network.DnsCategory
import com.frerox.toolz.data.network.DnsProvider
import com.frerox.toolz.data.network.NetworkPowerUiState
import com.frerox.toolz.data.network.PingSample
import com.frerox.toolz.data.network.ProcessNetworkUsage
import com.frerox.toolz.data.network.VpnStatus
import com.frerox.toolz.ui.screens.network.components.TerminalOverlay
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

private enum class SuiteTab(val label: String) {
    OVERVIEW("Overview"),
    DNS("DNS Engine"),
    DIAGNOSTICS("Diagnostics"),
    TRAFFIC("Traffic")
}

private data class ShizukuPrompt(
    val featureName: String,
    val supportingText: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NetworkPowerSuiteScreen(
    onBack: () -> Unit,
    viewModel: NetworkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val privilegedReady = uiState.privilegedState.isAuthorized && uiState.privilegedState.isServiceReady
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf(DnsCategory.SPEED) }
    var customLabel by rememberSaveable { mutableStateOf("Custom DNS") }
    var customPrimary by rememberSaveable { mutableStateOf("") }
    var customSecondary by rememberSaveable { mutableStateOf("") }
    var customHostname by rememberSaveable { mutableStateOf("") }
    var shizukuPrompt by rememberSaveable { mutableStateOf<ShizukuPrompt?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(privilegedReady) {
        if (privilegedReady && shizukuPrompt != null) {
            shizukuPrompt = null
        }
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn("client\ndev tun\nproto udp\nremote 1.2.3.4 1194\nverb 3")
        }
    }

    fun showShizukuPrompt(featureName: String, supportingText: String) {
        shizukuPrompt = ShizukuPrompt(
            featureName = featureName,
            supportingText = supportingText
        )
    }

    fun requestShizukuAccess() {
        when {
            !uiState.privilegedState.isReachable -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Start the Shizuku app and pair or launch the service first."
                    )
                }
            }
            !uiState.privilegedState.isAuthorized -> {
                try {
                    if (Shizuku.isPreV11()) {
                        (context as? Activity)?.requestPermissions(
                            arrayOf("rikka.shizuku.permission.API_V23"),
                            MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE
                        )
                    } else {
                        Shizuku.requestPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                    }
                } catch (_: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Unable to launch the Shizuku permission request.")
                    }
                }
            }
            else -> viewModel.verifyPrivilegedAccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Network Power-Suite",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                uiState.wifiState.ssid,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.refreshSuite()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh suite")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                TabStrip(
                    selectedTab = selectedTab,
                    onSelected = { index ->
                        vibrationManager?.vibrateTick()
                        selectedTab = index
                    }
                )

                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(tween(240)) togetherWith fadeOut(tween(200))
                    },
                    label = "network_suite_tab"
                ) { tabIndex ->
                    Crossfade(targetState = tabIndex, label = "network_crossfade") { page ->
                        when (SuiteTab.entries[page]) {
                            SuiteTab.OVERVIEW -> OverviewTab(
                                uiState = uiState,
                                privilegedReady = privilegedReady,
                                onRefreshIp = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.fetchPublicIp()
                                },
                                onToggleData = {
                                    if (privilegedReady) {
                                        vibrationManager?.vibrateTick()
                                        viewModel.toggleMobileData(it)
                                    } else {
                                        vibrationManager?.vibrateError()
                                        showShizukuPrompt(
                                            featureName = "Mobile data toggle",
                                            supportingText = "System radio controls are protected. Connect Shizuku to unlock one-tap mobile data toggles."
                                        )
                                    }
                                },
                                onScanDevices = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.scanSubnet()
                                },
                                onScanPorts = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.scanGatewayPorts()
                                }
                            )

                            SuiteTab.DNS -> DnsTab(
                                uiState = uiState,
                                privilegedReady = privilegedReady,
                                selectedCategory = selectedCategory,
                                onCategorySelected = { selectedCategory = it },
                                onRefreshDns = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.refreshDns()
                                },
                                onApplyProvider = {
                                    if (privilegedReady) {
                                        vibrationManager?.vibrateSuccess()
                                        viewModel.applyDnsProvider(it)
                                    } else {
                                        vibrationManager?.vibrateError()
                                        showShizukuPrompt(
                                            featureName = "Private DNS apply",
                                            supportingText = "Applying a provider system-wide uses `settings put global private_dns_spec` and stays locked until Shizuku is verified."
                                        )
                                    }
                                },
                                onBenchmarkCustom = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.benchmarkCustomDns(
                                        label = customLabel,
                                        primaryAddress = customPrimary,
                                        secondaryAddress = customSecondary.ifBlank { null },
                                        privateDnsHostname = customHostname.ifBlank { null }
                                    )
                                },
                                onApplyCustomHost = {
                                    if (privilegedReady) {
                                        vibrationManager?.vibrateSuccess()
                                        viewModel.applyCustomPrivateDns(customHostname)
                                    } else {
                                        vibrationManager?.vibrateError()
                                        showShizukuPrompt(
                                            featureName = "Custom Private DNS",
                                            supportingText = "Custom Private DNS hostnames write directly to global settings, so this flow stays locked behind Shizuku."
                                        )
                                    }
                                },
                                onResetDns = {
                                    if (privilegedReady) {
                                        vibrationManager?.vibrateClick()
                                        viewModel.resetPrivateDns()
                                    } else {
                                        vibrationManager?.vibrateError()
                                        showShizukuPrompt(
                                            featureName = "DNS reset",
                                            supportingText = "Resetting Private DNS back to automatic uses protected global settings and needs Shizuku."
                                        )
                                    }
                                },
                                onFlushCache = {
                                    if (privilegedReady) {
                                        vibrationManager?.vibrateClick()
                                        viewModel.flushDnsCache()
                                    } else {
                                        vibrationManager?.vibrateError()
                                        showShizukuPrompt(
                                            featureName = "DNS cache flush",
                                            supportingText = "Flushing the resolver cache is a privileged network command. Connect Shizuku to unlock it."
                                        )
                                    }
                                },
                                customLabel = customLabel,
                                onCustomLabelChange = { customLabel = it },
                                customPrimary = customPrimary,
                                onCustomPrimaryChange = { customPrimary = it },
                                customSecondary = customSecondary,
                                onCustomSecondaryChange = { customSecondary = it },
                                customHostname = customHostname,
                                onCustomHostnameChange = { customHostname = it }
                            )

                            SuiteTab.DIAGNOSTICS -> DiagnosticsTab(
                                uiState = uiState,
                                privilegedReady = privilegedReady,
                                onRunSpeedTest = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.runSpeedTest()
                                },
                                onTraceRoute = {
                                    if (privilegedReady) {
                                        vibrationManager?.vibrateClick()
                                        viewModel.runTraceRoute()
                                    } else {
                                        vibrationManager?.vibrateError()
                                        showShizukuPrompt(
                                            featureName = "Traceroute",
                                            supportingText = "Traceroute uses shell networking tools that are only available through the privileged Shizuku layer."
                                        )
                                    }
                                },
                                onVpnToggle = {
                                    vibrationManager?.vibrateClick()
                                    if (uiState.vpnStatus == VpnStatus.DISCONNECTED) {
                                        val intent = viewModel.prepareVpn()
                                        if (intent != null) {
                                            vpnPrepareLauncher.launch(intent)
                                        } else {
                                            viewModel.startVpn("mock_config")
                                        }
                                    } else {
                                        viewModel.stopVpn()
                                    }
                                }
                            )

                            SuiteTab.TRAFFIC -> TrafficTab(
                                uiState = uiState,
                                onRefresh = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.refreshSuite()
                                }
                            )
                        }
                    }
                }
            }
        }

        TerminalOverlay(
            logs = logs,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    shizukuPrompt?.let { prompt ->
        ShizukuAccessDialog(
            prompt = prompt,
            uiState = uiState,
            onDismiss = { shizukuPrompt = null },
            onRequestAccess = { requestShizukuAccess() },
            onVerify = { viewModel.verifyPrivilegedAccess() }
        )
    }
}

@Composable
private fun TabStrip(
    selectedTab: Int,
    onSelected: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuiteTab.entries.forEachIndexed { index, tab ->
                val selected = index == selectedTab
                val container by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "network_tab_bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(container)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    uiState: NetworkPowerUiState,
    privilegedReady: Boolean,
    onRefreshIp: () -> Unit,
    onToggleData: (Boolean) -> Unit,
    onScanDevices: () -> Unit,
    onScanPorts: () -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        verticalItemSpacing = 16.dp,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            HeroStatusCard(uiState)
        }
        
        item(span = StaggeredGridItemSpan.FullLine) {
            ConnectivityWave(score = uiState.networkHealthScore)
        }

        item {
            GlassCard(
                title = "Local Link",
                icon = Icons.Rounded.Wifi,
                subtitle = "Active profile"
            ) {
                MetricRow("IPv4", uiState.wifiState.ipAddress)
                MetricRow("Gateway", uiState.wifiState.gateway)
                MetricRow("Band", uiState.wifiState.band)
            }
        }
        item {
            GlassCard(
                title = "Public Identity",
                icon = Icons.Rounded.Public,
                subtitle = "Identity",
                trailing = {
                    IconButton(onClick = onRefreshIp, enabled = !uiState.isRefreshingPublicIp) {
                        if (uiState.isRefreshingPublicIp) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null)
                        }
                    }
                }
            ) {
                MetricRow("IP", uiState.publicIpInfo.ip)
                MetricRow("ISP", uiState.publicIpInfo.isp)
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            GlassCard(
                title = "Visual Topology",
                icon = Icons.Rounded.Hub,
                subtitle = "Live mesh data pulses",
                trailing = {
                    Button(
                        onClick = onScanDevices,
                        enabled = !uiState.isScanningDevices,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(if (uiState.isScanningDevices) "Scanning" else "Scan Subnet")
                    }
                }
            ) {
                NetworkMap(uiState)
            }
        }
        item {
            GlassCard(
                title = "Privileged Radios",
                icon = Icons.Rounded.CellTower,
                subtitle = "Automation"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mobile Data", fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = uiState.isDataEnabled,
                        onCheckedChange = onToggleData,
                        thumbContent = {
                            if (!privilegedReady) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                StatusBadge("State", if (uiState.privilegedState.isServiceReady) "Bound" else "Locked")
            }
        }
        item {
            GlassCard(
                title = "Port Sentry",
                icon = Icons.Rounded.Security,
                subtitle = "Probe gateway"
            ) {
                OutlinedButton(
                    onClick = onScanPorts,
                    enabled = !uiState.isScanningPorts,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isScanningPorts) "Probing..." else "Probe Ports")
                }
            }
        }
    }
}

@Composable
private fun ConnectivityWave(score: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "phase"
    )
    
    val color = healthColor(score)
    val height = 48.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path()
            val step = 8f
            val amplitude = (score / 100f) * 14.dp.toPx()
            
            path.moveTo(0f, size.height / 2f)
            for (x in 0..size.width.toInt() step step.toInt()) {
                val y = size.height / 2f + amplitude * sin(x * 0.02f + phase)
                path.lineTo(x.toFloat(), y)
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = 0.45f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsTab(
    uiState: NetworkPowerUiState,
    privilegedReady: Boolean,
    selectedCategory: DnsCategory,
    onCategorySelected: (DnsCategory) -> Unit,
    onRefreshDns: () -> Unit,
    onApplyProvider: (DnsProvider) -> Unit,
    onBenchmarkCustom: () -> Unit,
    onApplyCustomHost: () -> Unit,
    onResetDns: () -> Unit,
    onFlushCache: () -> Unit,
    customLabel: String,
    onCustomLabelChange: (String) -> Unit,
    customPrimary: String,
    onCustomPrimaryChange: (String) -> Unit,
    customSecondary: String,
    onCustomSecondaryChange: (String) -> Unit,
    customHostname: String,
    onCustomHostnameChange: (String) -> Unit
) {
    val filtered = remember(uiState.dnsResults, selectedCategory) {
        uiState.dnsResults.filter { selectedCategory in it.provider.categories }
            .ifEmpty { uiState.dnsResults }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(
                title = "Ultimate DNS Engine",
                icon = Icons.Rounded.Dns,
                subtitle = "Parallel benchmark of top resolvers",
                trailing = {
                    IconButton(onClick = onRefreshDns, enabled = !uiState.isRefreshingDns) {
                        if (uiState.isRefreshingDns) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = null)
                        }
                    }
                }
            ) {
                RecommendationPanel(uiState)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DnsCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { onCategorySelected(category) },
                            label = { Text(categoryTitle(category)) },
                            shape = RoundedCornerShape(24.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassCard(
                        title = "System DNS",
                        icon = Icons.Rounded.VpnKey,
                        subtitle = "Shizuku bridge"
                    ) {
                        MetricRow("Mode", uiState.privateDnsMode)
                        MetricRow("Host", uiState.privateDnsHost.ifBlank { "Auto" })
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProtectedActionButton(
                                label = "Reset",
                                privilegedReady = privilegedReady,
                                onClick = onResetDns,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassCard(
                        title = "Cache",
                        icon = Icons.Rounded.AutoGraph,
                        subtitle = "Netd metrics"
                    ) {
                        MetricRow("Hits", uiState.cacheAnalytics.hitRatioPercent?.let { "$it%" } ?: "—")
                        MetricRow("Entries", uiState.cacheAnalytics.entryCount?.toString() ?: "0")
                        Spacer(Modifier.height(14.dp))
                        ProtectedActionButton(
                            label = "Flush",
                            privilegedReady = privilegedReady,
                            onClick = onFlushCache,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Benchmark Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }

        items(items = filtered.chunked(2)) { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { result ->
                    Box(modifier = Modifier.weight(1f)) {
                        DnsResultCard(
                            result = result,
                            privilegedReady = privilegedReady,
                            onApply = { onApplyProvider(result.provider) }
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            GlassCard(
                title = "Manual Endpoint",
                icon = Icons.Rounded.Bolt,
                subtitle = "Custom resolver properties"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customHostname,
                        onValueChange = onCustomHostnameChange,
                        label = { Text("Private DNS hostname") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProtectedActionButton(
                            label = "Apply Custom Host",
                            privilegedReady = privilegedReady,
                            onClick = onApplyCustomHost,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsTab(
    uiState: NetworkPowerUiState,
    privilegedReady: Boolean,
    onRunSpeedTest: () -> Unit,
    onTraceRoute: () -> Unit,
    onVpnToggle: () -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        verticalItemSpacing = 14.dp,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            GlassCard(
                title = "Ping Master",
                icon = Icons.Rounded.Timeline,
                subtitle = "60-second latency stream with jitter alert"
            ) {
                LatencyChart(samples = uiState.pingSamples, modifier = Modifier.fillMaxWidth().height(148.dp))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniMetric("Latency", "${uiState.stabilityInfo.avgLatency}ms")
                    MiniMetric("Jitter", "${uiState.stabilityInfo.jitter}ms")
                    MiniMetric("Loss", "${uiState.stabilityInfo.packetLoss.roundToInt()}%")
                }
                if (uiState.stabilityInfo.jitter >= 12) {
                    Spacer(Modifier.height(10.dp))
                    StatusBadge("Alert", "Jitter spike detected")
                }
            }
        }
        item {
            GlassCard(
                title = "Speed Test",
                icon = Icons.Rounded.Speed,
                subtitle = "Throughput probe with animated phase"
            ) {
                RunningStatePill(
                    label = uiState.speedTestResult.phaseLabel,
                    running = uiState.speedTestResult.isRunning
                )
                Spacer(Modifier.height(12.dp))
                MetricRow("Download", "${uiState.speedTestResult.downloadSpeedMbps.format(1)} Mbps")
                MetricRow("Progress", "${(uiState.speedTestResult.progress * 100).roundToInt()}%")
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { uiState.speedTestResult.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRunSpeedTest, enabled = !uiState.speedTestResult.isRunning, shape = RoundedCornerShape(16.dp)) {
                    Text(if (uiState.speedTestResult.isRunning) "Testing" else "Run test")
                }
            }
        }
        item {
            GlassCard(
                title = "Trace Route",
                icon = Icons.Rounded.Route,
                subtitle = "Hop-by-hop path summary"
            ) {
                if (uiState.traceHops.isEmpty()) {
                    Text(
                        "Launch a quick route trace to inspect transit hops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.traceHops.take(4).forEach { hop ->
                        MetricRow("Hop ${hop.hop}", "${hop.ip} - ${hop.location}")
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProtectedOutlinedButton(
                    label = "Trace route",
                    privilegedReady = privilegedReady,
                    onClick = onTraceRoute
                )
                if (!privilegedReady) {
                    Spacer(Modifier.height(10.dp))
                    LockHint("Traceroute is executed through the privileged shell layer.")
                }
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            GlassCard(
                title = "VPN + Route Control",
                icon = Icons.Rounded.Lan,
                subtitle = "Tunnel state, audits, and live route tables"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(uiState.vpnStatus.name.replace("_", " "), fontWeight = FontWeight.Bold)
                        Text(
                            "Shizuku state: ${if (uiState.privilegedState.isServiceReady) "ready" else "locked"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onVpnToggle, shape = RoundedCornerShape(16.dp)) {
                        Text(if (uiState.vpnStatus == VpnStatus.DISCONNECTED) "Connect" else "Disconnect")
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (uiState.ipAudit.routes.isEmpty()) {
                    Text(
                        "Route and neighbor audit loads automatically when privileged access is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.ipAudit.routes.take(3).forEach { route ->
                        StatusBadge("Route", route)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficTab(
    uiState: NetworkPowerUiState,
    onRefresh: () -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        verticalItemSpacing = 14.dp,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            GlassCard(
                title = "Deep Traffic Inspection",
                icon = Icons.Rounded.NetworkCheck,
                subtitle = "Live socket inventory and radio context",
                trailing = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                    }
                }
            ) {
                if (uiState.activeProcesses.isEmpty()) {
                    Text(
                        "Waiting for socket state from the privileged layer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.activeProcesses, key = { "${it.localAddr}-${it.remoteAddr}" }) { process ->
                            TrafficRow(process)
                        }
                    }
                }
            }
        }
        item {
            GlassCard(
                title = "Cellular Audit",
                icon = Icons.Rounded.CellTower,
                subtitle = "Current access tech and signal state"
            ) {
                MetricRow("Tech", uiState.cellularAudit.tech)
                MetricRow("Cell ID", uiState.cellularAudit.cellId)
                MetricRow("TAC", uiState.cellularAudit.tac)
                MetricRow("Signal", uiState.cellularAudit.signalStrength)
            }
        }
        item {
            GlassCard(
                title = "Neighbor Table",
                icon = Icons.Rounded.Memory,
                subtitle = "ARP / route-side awareness"
            ) {
                if (uiState.ipAudit.neighbors.isEmpty()) {
                    Text(
                        "Neighbor discovery appears after a successful privileged refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.ipAudit.neighbors.take(4).forEach { neighbor ->
                        StatusBadge("Peer", neighbor)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(uiState: NetworkPowerUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val meshPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "phase"
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface

    GlassCard(
        title = "Healthy Mesh",
        icon = Icons.Rounded.AutoGraph,
        subtitle = "Live mesh-gradient status and signal health",
        modifier = Modifier.drawBehind {
            val center = Offset(size.width * 0.8f, size.height * 0.5f)
            val radius = size.minDimension * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to primary.copy(alpha = 0.08f + 0.02f * sin(meshPhase)),
                    0.5f to tertiary.copy(alpha = 0.04f + 0.02f * cos(meshPhase)),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius
                )
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusOrb(
                score = uiState.networkHealthScore,
                modifier = Modifier.size(140.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = uiState.wifiState.ssid,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "ssid_anim"
                ) { ssid ->
                    Text(
                        text = ssid,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${uiState.wifiState.rssi} dBm • ${uiState.wifiState.wifiStandard}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(16.dp))
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusBadge("Latency", "${uiState.stabilityInfo.avgLatency}ms")
                    StatusBadge("Jitter", "${uiState.stabilityInfo.jitter}ms")
                    StatusBadge("Packet Loss", "${uiState.stabilityInfo.packetLoss.roundToInt()}%")
                }
            }
        }
    }
}

@Composable
private fun RecommendationPanel(uiState: NetworkPowerUiState) {
    val recommendation = uiState.dnsRecommendation
    val panelColor = if ((recommendation?.score ?: 0) >= 70) Color(0xFF2E9D66) else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = panelColor.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = recommendation?.provider?.name ?: "Benchmarking providers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = recommendation?.rationale ?: "Latency, jitter, and packet loss are combined into a weighted score.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DnsResultCard(
    result: DnsBenchmarkResult,
    privilegedReady: Boolean,
    onApply: () -> Unit
) {
    GlassCard(
        title = result.provider.name,
        icon = Icons.Rounded.Dns,
        subtitle = result.provider.badge.ifBlank { result.provider.description }
    ) {
        StatusBadge(
            "Rank",
            if (result.rank == Int.MAX_VALUE) "Pending" else "#${result.rank}"
        )
        Spacer(Modifier.height(10.dp))
        MetricRow("Latency", result.metrics.latencyMs?.let { "${it}ms" } ?: "Timeout")
        MetricRow("Jitter", result.metrics.jitterMs?.let { "${it}ms" } ?: "n/a")
        MetricRow("Loss", "${result.metrics.packetLossPercent.roundToInt()}%")
        MetricRow("Score", "${result.metrics.weightedScore}/100")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtectedActionButton(
                label = if (result.isRecommended) "Apply best" else "Apply",
                privilegedReady = privilegedReady,
                enabled = !result.provider.privateDnsHostname.isNullOrBlank(),
                onClick = onApply
            )
            if (result.isRecommended) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Optimal choice") },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = Color(0xFF2E9D66).copy(alpha = 0.14f),
                        disabledLabelColor = Color(0xFF2E9D66)
                    )
                )
            }
        }
    }
}

@Composable
private fun NetworkMap(uiState: NetworkPowerUiState) {
    val nodes = uiState.topology.nodes
    val edges = uiState.topology.edges
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    
    val infiniteTransition = rememberInfiniteTransition(label = "topology")
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
        label = "pulse"
    )

    if (nodes.isEmpty()) {
        Text(
            "Start a device scan to paint the local node graph.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Draw Edges with Pulses
            edges.forEach { edge ->
                val from = nodes.firstOrNull { it.id == edge.from } ?: return@forEach
                val to = nodes.firstOrNull { it.id == edge.to } ?: return@forEach
                val start = Offset(from.xBias * size.width, from.yBias * size.height)
                val end = Offset(to.xBias * size.width, to.yBias * size.height)
                
                // Base Edge
                drawLine(
                    color = primary.copy(alpha = 0.15f),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Animated Pulse
                val pulsePos = Offset(
                    start.x + (end.x - start.x) * pulseOffset,
                    start.y + (end.y - start.y) * pulseOffset
                )
                drawCircle(
                    color = primary.copy(alpha = 0.6f),
                    radius = 3.dp.toPx(),
                    center = pulsePos
                )
            }

            // Draw Nodes
            nodes.forEach { node ->
                val center = Offset(node.xBias * size.width, node.yBias * size.height)
                val glowRadius = if (node.isPrimary) 24.dp.toPx() else 16.dp.toPx()
                
                // Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(primary.copy(alpha = 0.15f), Color.Transparent)
                    ),
                    radius = glowRadius * 1.5f,
                    center = center
                )
                
                // Node
                drawCircle(
                    color = if (node.isPrimary) primary else secondary,
                    radius = if (node.isPrimary) 14.dp.toPx() else 10.dp.toPx(),
                    center = center
                )
                
                // Ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = if (node.isPrimary) 20.dp.toPx() else 14.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            nodes.take(3).forEach { node ->
                StatusBadge(node.label, if (node.isPrimary) "Active" else "Peer")
            }
        }
    }
}

@Composable
private fun TrafficRow(process: ProcessNetworkUsage) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(process.name, fontWeight = FontWeight.Bold)
                StatusBadge(process.protocol, process.state)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                process.localAddr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                process.remoteAddr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LatencyChart(
    samples: List<PingSample>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val points = samples.takeLast(60)
        if (points.size < 2) return@Canvas

        val validLatencies = points.mapNotNull { it.latencyMs }
        val maxLatency = maxOf(80L, (validLatencies.maxOrNull() ?: 50L) + 10L).toFloat()
        val linePath = Path()

        points.forEachIndexed { index, sample ->
            val x = (index.toFloat() / (points.lastIndex.coerceAtLeast(1))) * size.width
            val normalized = (sample.latencyMs ?: maxLatency.toLong()).coerceAtMost(maxLatency.toLong()) / maxLatency
            val y = size.height - (normalized * size.height)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent))
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun RunningStatePill(
    label: String,
    running: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "state_pill")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "state_pill_anim"
    )
    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = if (running) 0.42f else 0.16f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (running) 0.32f else 0.12f),
            MaterialTheme.colorScheme.primary.copy(alpha = if (running) 0.42f else 0.16f)
        ),
        start = Offset.Zero,
        end = Offset(300f * shimmer, 120f)
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(brush)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (running) Icons.Rounded.Speed else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CompactDeviceRow(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProtectedActionButton(
    label: String,
    privilegedReady: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(),
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
        colors = if (privilegedReady) {
            colors
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        if (!privilegedReady) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun ProtectedOutlinedButton(
    label: String,
    privilegedReady: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (privilegedReady) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        if (!privilegedReady) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun LockHint(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun GlassCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 280f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun StatusOrb(
    score: Int,
    modifier: Modifier = Modifier
) {
    val accent = healthColor(score)
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val progress by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = spring(stiffness = 180f),
        label = "health_progress"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        accent.copy(alpha = 0.18f * pulse),
                        secondary.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2f
            )
            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        accent.copy(alpha = 0.65f),
                        accent,
                        tertiary
                    )
                ),
                startAngle = 140f,
                sweepAngle = 260f * progress,
                useCenter = false,
                style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text("Healthy", style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
private fun StatusBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Text(
            "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ShizukuAccessDialog(
    prompt: ShizukuPrompt,
    uiState: NetworkPowerUiState,
    onDismiss: () -> Unit,
    onRequestAccess: () -> Unit,
    onVerify: () -> Unit
) {
    val status = when {
        uiState.privilegedState.isServiceReady -> "Connected"
        uiState.privilegedState.isAuthorized -> "Authorized, waiting for service"
        uiState.privilegedState.isReachable -> "Service reachable, permission needed"
        else -> "Shizuku service offline"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (uiState.privilegedState.isServiceReady) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock,
                contentDescription = null
            )
        },
        title = { Text("Unlock ${prompt.featureName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(prompt.supportingText)
                StatusBadge("Status", status)
                Text(
                    "1. Start or pair Shizuku.\n2. Grant the permission prompt.\n3. Verify access and retry the locked action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onRequestAccess) {
                Text(
                    when {
                        !uiState.privilegedState.isReachable -> "Open Shizuku first"
                        !uiState.privilegedState.isAuthorized -> "Grant access"
                        else -> "Retry binding"
                    }
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onVerify) {
                    Text("Verify")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

private fun healthColor(score: Int): Color {
    return when {
        score >= 80 -> Color(0xFF2E9D66)
        score >= 55 -> Color(0xFFE0A12E)
        else -> Color(0xFFCF5252)
    }
}

private fun categoryTitle(category: DnsCategory): String {
    return when (category) {
        DnsCategory.PRIVACY -> "Privacy"
        DnsCategory.SPEED -> "Speed"
        DnsCategory.SECURITY -> "Security"
        DnsCategory.FAMILY -> "Family"
    }
}

private fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
