package com.albionboost.app

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        ServerRepository.init(applicationContext)
        AlbionTunnelManager.init(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0C1017),
                    surface = Color(0xFF151B26),
                    primary = Color(0xFFD97706),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                AlbionBoostApp(
                    onRequestVpnPermission = { VpnService.prepare(this) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbionBoostApp(
    onRequestVpnPermission: () -> android.content.Intent?
) {
    val uiState by AlbionTunnelManager.state.collectAsState()
    val servers by ServerRepository.servers.collectAsState()
    val selectedId by ServerRepository.selectedId.collectAsState()
    val selectedServer = servers.find { it.id == selectedId } ?: servers.first()

    val scope = rememberCoroutineScope()
    var showServerSheet by remember { mutableStateOf(false) }
    var importTargetServer by remember { mutableStateOf<ServerNode?>(null) }
    var showAddNewServerDialog by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        AlbionTunnelManager.refreshState()
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status == TunnelStatus.CONNECTED) {
            elapsedSec = 0L
            while (true) {
                delay(1000)
                elapsedSec += 1
            }
        } else {
            elapsedSec = 0L
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                AlbionTunnelManager.connect(selectedServer)
            }
        } else {
            AlbionTunnelManager.setPermissionDenied()
        }
    }

    val isConnected = uiState.status == TunnelStatus.CONNECTED
    val isBusy = uiState.status == TunnelStatus.CONNECTING || uiState.status == TunnelStatus.DISCONNECTING

    val buttonColor by animateColorAsState(
        targetValue = when (uiState.status) {
            TunnelStatus.CONNECTED -> Color(0xFF14532D)
            TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> Color(0xFF232C3B)
            TunnelStatus.ERROR -> Color(0xFF3F1D20)
            TunnelStatus.DISCONNECTED -> Color(0xFF151B26)
        },
        animationSpec = tween(180),
        label = "btnColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when (uiState.status) {
            TunnelStatus.CONNECTED -> Color(0xFF22C55E)
            TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> Color(0xFFD97706)
            TunnelStatus.ERROR -> Color(0xFFEF4444)
            TunnelStatus.DISCONNECTED -> Color(0xFF334155)
        },
        animationSpec = tween(180),
        label = "borderColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Albion Boost",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Albion Online · Раздельный маршрут",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Center Connect Button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = {
                    if (isBusy) return@Surface
                    if (isConnected) {
                        scope.launch { AlbionTunnelManager.disconnect() }
                    } else if (!selectedServer.isConfigured()) {
                        importTargetServer = selectedServer
                    } else {
                        val intent = onRequestVpnPermission()
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            scope.launch { AlbionTunnelManager.connect(selectedServer) }
                        }
                    }
                },
                enabled = !isBusy,
                shape = CircleShape,
                color = buttonColor,
                border = BorderStroke(2.dp, borderColor),
                modifier = Modifier.size(208.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF86EFAC) else Color(0xFFF8FAFC),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (uiState.status) {
                            TunnelStatus.CONNECTED -> "Отключиться"
                            TunnelStatus.CONNECTING -> "Подключение..."
                            TunnelStatus.DISCONNECTING -> "Отключение..."
                            else -> "Подключиться"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF8FAFC)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = uiState.message,
                fontSize = 14.sp,
                color = when (uiState.status) {
                    TunnelStatus.CONNECTED -> Color(0xFF86EFAC)
                    TunnelStatus.ERROR -> Color(0xFFFCA5A5)
                    else -> Color(0xFF94A3B8)
                },
                textAlign = TextAlign.Center
            )

            if (isConnected) {
                Spacer(modifier = Modifier.height(6.dp))
                val h = elapsedSec / 3600
                val m = (elapsedSec % 3600) / 60
                val s = elapsedSec % 60
                Text(
                    text = String.format("%02d:%02d:%02d", h, m, s),
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Bottom Selected Server Card (LagX / Standard VPN style)
        Surface(
            onClick = { showServerSheet = true },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF151B26),
            border = BorderStroke(1.dp, Color(0xFF232C3B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedServer.countryCode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${selectedServer.country} · ${selectedServer.city}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF8FAFC)
                        )
                        Text(
                            text = if (selectedServer.isConfigured()) {
                                "${selectedServer.albionTarget} · Готов"
                            } else {
                                "Нажмите, чтобы выбрать или добавить конфиг"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Выбрать сервер",
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }

    // Server Search & Picker Bottom Sheet
    if (showServerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var searchQuery by remember { mutableStateOf("") }
        var regionFilter by remember { mutableStateOf("all") }
        var isPinging by remember { mutableStateOf(false) }

        val filteredServers = servers.filter { node ->
            val matchesRegion = regionFilter == "all" || node.region == regionFilter
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                node.city.lowercase().contains(q) ||
                node.country.lowercase().contains(q) ||
                node.albionTarget.lowercase().contains(q)
            matchesRegion && matchesSearch
        }

        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF151B26),
            contentColor = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выбор сервера",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        IconButton(
                            onClick = {
                                if (!isPinging) {
                                    isPinging = true
                                    scope.launch {
                                        ServerRepository.pingAllServers()
                                        isPinging = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Проверить пинг",
                                tint = if (isPinging) Color(0xFFD97706) else Color(0xFFCBD5E1)
                            )
                        }
                        IconButton(onClick = { showAddNewServerDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Добавить сервер",
                                tint = Color(0xFFD97706)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск сервера или страны...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to "Все",
                        "eu" to "Европа",
                        "asia" to "Азия",
                        "na" to "Америка"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = regionFilter == key,
                            onClick = { regionFilter = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                var isAutoFetching by remember { mutableStateOf(false) }
                var autoFetchError by remember { mutableStateOf<String?>(null) }

                Button(
                    onClick = {
                        if (!isAutoFetching) {
                            isAutoFetching = true
                            autoFetchError = null
                            scope.launch {
                                val res = AutoWarpFetcher.fetchAndRegister()
                                isAutoFetching = false
                                res.onSuccess { nodes ->
                                    ServerRepository.addAutoWarpNodes(nodes)
                                    showServerSheet = false
                                }.onFailure { err ->
                                    autoFetchError = "Не удалось получить авто-конфиг: ${err.localizedMessage ?: "нет сети"}"
                                }
                            }
                        }
                    },
                    enabled = !isAutoFetching,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isAutoFetching) "Ищем и регистрируем конфиг..." else "Найти конфиг автоматически (WARP)",
                        color = Color(0xFF0C1017),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (autoFetchError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = autoFetchError!!, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF232C3B))

                LazyColumn(modifier = Modifier.height(310.dp)) {
                    items(filteredServers, key = { it.id }) { node ->
                        val isSelected = node.id == selectedServer.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ServerRepository.selectServer(node.id)
                                    showServerSheet = false
                                    if (!node.isConfigured()) {
                                        importTargetServer = node
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${node.countryCode} · ${node.city}",
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = if (node.isConfigured()) {
                                        "${node.albionTarget} · Готов"
                                    } else {
                                        "${node.albionTarget} · Нужен .conf"
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = node.latencyMs?.let { "${it} мс" } ?: "—",
                                    fontSize = 12.sp,
                                    color = if (node.latencyMs != null && node.latencyMs < 110) {
                                        Color(0xFF86EFAC)
                                    } else {
                                        Color(0xFF94A3B8)
                                    }
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Выбран",
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }
                }
            }
        }
    }

    // Paste WireGuard .conf for Selected Server
    importTargetServer?.let { target ->
        var confText by remember { mutableStateOf("") }
        var parseError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { importTargetServer = null },
            containerColor = Color(0xFF151B26),
            title = { Text("Конфиг для «${target.city}»") },
            text = {
                Column {
                    Text(
                        text = "Вставьте содержимое готового WireGuard-файла (.conf), и сервер сразу заработает:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confText,
                        onValueChange = {
                            confText = it
                            parseError = null
                        },
                        placeholder = { Text("[Interface]\nPrivateKey = ...\n[Peer]\nPublicKey = ...\nEndpoint = ...") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                val res = AutoWarpFetcher.fetchAndRegister()
                                res.onSuccess { nodes ->
                                    ServerRepository.addAutoWarpNodes(nodes)
                                    importTargetServer = null
                                }.onFailure { err ->
                                    parseError = "Автопоиск не сработал: ${err.localizedMessage ?: "ошибка сети"}"
                                }
                            }
                        }
                    ) {
                        Text("Или получить бесплатный конфиг автоматически (WARP)", color = Color(0xFFFBBF24))
                    }
                    if (parseError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = parseError!!, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = WgConfigParser.parse(confText)
                        if (parsed == null) {
                            parseError = "Не найдены PrivateKey, PublicKey или Endpoint"
                        } else {
                            ServerRepository.applyConfigToServer(target.id, parsed)
                            importTargetServer = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { importTargetServer = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Add Custom Server Dialog
    if (showAddNewServerDialog) {
        var cityName by remember { mutableStateOf("") }
        var confText by remember { mutableStateOf("") }
        var parseError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddNewServerDialog = false },
            containerColor = Color(0xFF151B26),
            title = { Text("Добавить свой сервер") },
            text = {
                Column {
                    OutlinedTextField(
                        value = cityName,
                        onValueChange = { cityName = it },
                        label = { Text("Название (например, Амстердам)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confText,
                        onValueChange = {
                            confText = it
                            parseError = null
                        },
                        label = { Text("Вставьте WireGuard .conf") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (parseError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = parseError!!, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = WgConfigParser.parse(confText)
                        if (parsed == null) {
                            parseError = "Не удалось прочитать .conf (нужны PrivateKey, PublicKey, Endpoint)"
                        } else {
                            ServerRepository.addCustomServer(cityName, "eu", parsed)
                            showAddNewServerDialog = false
                            showServerSheet = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNewServerDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
