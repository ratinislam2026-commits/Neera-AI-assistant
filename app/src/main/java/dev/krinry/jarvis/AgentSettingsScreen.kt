package dev.krinry.jarvis

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krinry.jarvis.ai.GroqApiClient
import dev.krinry.jarvis.security.SecureKeyStore
import dev.krinry.jarvis.service.AutoAgentService
import dev.krinry.jarvis.service.FloatingBubbleService
import dev.krinry.jarvis.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }

    // State
    val isAccessibilityEnabled = remember(refreshKey) { checkAccessibilityEnabled(context) }
    val hasOverlayPermission = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    }
    var hasAudioPermission by remember(refreshKey) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) refreshKey++
    }

    var agentEnabled by remember { mutableStateOf(SecureKeyStore.isAgentEnabled(context)) }
    var groqApiKey by remember { mutableStateOf(SecureKeyStore.getGroqApiKey(context) ?: "") }
    var openRouterApiKey by remember { mutableStateOf(SecureKeyStore.getOpenRouterApiKey(context) ?: "") }
    var showGroqKeyDialog by remember { mutableStateOf(false) }
    var showOpenRouterKeyDialog by remember { mutableStateOf(false) }
    var apiProvider by remember { mutableStateOf(SecureKeyStore.getApiProvider(context)) }
    var primaryModel by remember { mutableStateOf(SecureKeyStore.getPrimaryModel(context)) }
    var fallbackModel by remember { mutableStateOf(SecureKeyStore.getFallbackModel(context)) }
    var requestDelayMs by remember { mutableStateOf(SecureKeyStore.getRequestDelayMs(context)) }
    var showModelPicker by remember { mutableStateOf(false) }
    var modelPickerTarget by remember { mutableStateOf("primary") }
    var showDelayDialog by remember { mutableStateOf(false) }
    val allReady = isAccessibilityEnabled && hasOverlayPermission && hasAudioPermission

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        refreshKey++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ===== HERO SECTION =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                JarvisPrimary,
                                Color(0xFF8E7CF3),
                                JarvisSecondary.copy(alpha = 0.6f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Logo circle
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("J", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "JARVIS",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                letterSpacing = 3.sp
                            )
                            Text(
                                "AI Voice Agent",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Power toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (agentEnabled) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (agentEnabled) "Agent Active" else "Agent Offline",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (agentEnabled) "Tap floating bubble to give command"
                                else "Turn on to start voice control",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = agentEnabled,
                            onCheckedChange = { enabled ->
                                agentEnabled = enabled
                                SecureKeyStore.setAgentEnabled(context, enabled)
                                if (enabled) startBubbleService(context) else stopBubbleService(context)
                                refreshKey++
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Color.White.copy(alpha = 0.35f),
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== PERMISSIONS SECTION =====
            SectionHeader("Setup", Icons.Default.Shield)

            PermissionRow(
                icon = Icons.Default.Accessibility,
                title = "Accessibility Service",
                subtitle = "Read & interact with apps",
                isGranted = isAccessibilityEnabled,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            )
            PermissionRow(
                icon = Icons.Default.Layers,
                title = "Display Over Apps",
                subtitle = "Floating AI bubble overlay",
                isGranted = hasOverlayPermission,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    }
                }
            )
            PermissionRow(
                icon = Icons.Default.Mic,
                title = "Microphone",
                subtitle = "Voice command recognition",
                isGranted = hasAudioPermission,
                onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            // Status badge
            if (agentEnabled) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (allReady) JarvisSuccess.copy(alpha = 0.1f)
                            else JarvisError.copy(alpha = 0.1f)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (allReady) "✅ All set! Tap the floating bubble to start."
                        else "⚠️ Grant all permissions above to activate.",
                        fontSize = 13.sp,
                        color = if (allReady) JarvisSuccess else JarvisError,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== API PROVIDER =====
            SectionHeader("AI Provider", Icons.Default.Hub)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProviderChip("Groq", apiProvider == "groq", JarvisOrange, Modifier.weight(1f)) {
                    apiProvider = "groq"
                    SecureKeyStore.setApiProvider(context, "groq")
                }
                ProviderChip("OpenRouter", apiProvider == "openrouter", JarvisPrimary, Modifier.weight(1f)) {
                    apiProvider = "openrouter"
                    SecureKeyStore.setApiProvider(context, "openrouter")
                }
            }

            Spacer(Modifier.height(12.dp))

            // API Keys
            SettingsRow(Icons.Default.Key, "Groq API Key",
                if (groqApiKey.isNotEmpty()) "••••${groqApiKey.takeLast(4)}" else "Not configured",
                JarvisOrange
            ) { showGroqKeyDialog = true }

            SettingsRow(Icons.Default.Key, "OpenRouter API Key",
                if (openRouterApiKey.isNotEmpty()) "••••${openRouterApiKey.takeLast(4)}" else "Not set",
                JarvisPrimary
            ) { showOpenRouterKeyDialog = true }

            Spacer(Modifier.height(24.dp))

            // ===== MODEL SELECTION =====
            SectionHeader("Models", Icons.Default.Psychology)

            SettingsRow(Icons.Default.AutoAwesome, "Primary Model",
                primaryModel.ifEmpty { "Default" }, JarvisSecondary
            ) {
                modelPickerTarget = "primary"
                showModelPicker = true
            }

            SettingsRow(Icons.Default.SwapHoriz, "Fallback Model",
                fallbackModel.ifEmpty { "Default" }, JarvisAccent
            ) {
                modelPickerTarget = "fallback"
                showModelPicker = true
            }

            Spacer(Modifier.height(24.dp))

            // ===== PERFORMANCE =====
            SectionHeader("Performance", Icons.Default.Speed)

            SettingsRow(Icons.Default.Timer, "Request Delay",
                "${requestDelayMs}ms between API calls", JarvisWarning
            ) { showDelayDialog = true }

            Spacer(Modifier.height(40.dp))

            // Footer
            Text(
                "Jarvis v1.0 • by Krinry",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = DarkOnSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ===== DIALOGS =====

    if (showGroqKeyDialog) {
        ApiKeyDialog("Groq API Key", groqApiKey,
            onSave = { key ->
                groqApiKey = key
                SecureKeyStore.saveGroqApiKey(context, key)
                showGroqKeyDialog = false
                Toast.makeText(context, "Groq key saved!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showGroqKeyDialog = false }
        )
    }

    if (showOpenRouterKeyDialog) {
        ApiKeyDialog("OpenRouter API Key", openRouterApiKey,
            onSave = { key ->
                openRouterApiKey = key
                SecureKeyStore.saveOpenRouterApiKey(context, key)
                showOpenRouterKeyDialog = false
                Toast.makeText(context, "OpenRouter key saved!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showOpenRouterKeyDialog = false }
        )
    }

    if (showDelayDialog) {
        DelayPickerDialog(
            currentDelay = requestDelayMs,
            onSave = { delay ->
                requestDelayMs = delay
                SecureKeyStore.setRequestDelayMs(context, delay)
                showDelayDialog = false
            },
            onDismiss = { showDelayDialog = false }
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            context = context,
            target = modelPickerTarget,
            onSelect = { modelId ->
                if (modelPickerTarget == "primary") {
                    primaryModel = modelId
                    SecureKeyStore.setPrimaryModel(context, modelId)
                } else {
                    fallbackModel = modelId
                    SecureKeyStore.setFallbackModel(context, modelId)
                }
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}

// =============================================================================
// === Reusable Components ===
// =============================================================================

@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
    ) {
        Icon(
            icon, null,
            tint = JarvisAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            color = JarvisAccent
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector, title: String, subtitle: String,
    iconColor: Color, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = DarkOnSurface)
                Text(subtitle, fontSize = 12.sp, color = DarkOnSurfaceVariant, maxLines = 1)
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector, title: String, subtitle: String,
    isGranted: Boolean, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) JarvisSuccess.copy(alpha = 0.15f)
                        else JarvisPrimary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint = if (isGranted) JarvisSuccess else JarvisPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = DarkOnSurface)
                Text(subtitle, fontSize = 12.sp, color = DarkOnSurfaceVariant)
            }
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(JarvisSuccess.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = JarvisSuccess, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisPrimary)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Grant", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String, selected: Boolean, color: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) color else DarkCard,
        modifier = modifier.height(48.dp),
        border = if (!selected) androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceVariant) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else DarkOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

// =============================================================================
// === Dialogs ===
// =============================================================================

@Composable
private fun ApiKeyDialog(title: String, currentKey: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var tempKey by remember { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = tempKey, onValueChange = { tempKey = it },
                label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp))
        },
        confirmButton = { TextButton(onClick = { onSave(tempKey) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DelayPickerDialog(currentDelay: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(500L, 1000L, 1500L, 2000L, 3000L, 5000L, 8000L, 10000L)
    var selected by remember { mutableStateOf(currentDelay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Delay") },
        text = {
            Column {
                Text("Wait between API calls:", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                options.forEach { delay ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = delay }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == delay, onClick = { selected = delay })
                        Spacer(Modifier.width(8.dp))
                        val label = when {
                            delay >= 1000 -> "${delay / 1000.0}s"
                            else -> "${delay}ms"
                        }
                        Text(label, fontWeight = if (selected == delay) FontWeight.Bold else FontWeight.Normal)
                        if (delay == 2000L) {
                            Spacer(Modifier.width(8.dp))
                            Text("(recommended)", fontSize = 12.sp, color = JarvisSuccess)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerDialog(
    context: Context, target: String,
    onSelect: (String) -> Unit, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<GroqApiClient.ModelInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showFreeOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            models = GroqApiClient.fetchAvailableModels(context)
            isLoading = false
        }
    }

    val filteredModels = models.filter { model ->
        val matchesSearch = searchQuery.isEmpty() ||
            model.id.contains(searchQuery, ignoreCase = true) ||
            model.name.contains(searchQuery, ignoreCase = true)
        val matchesFree = !showFreeOnly || model.isFree
        matchesSearch && matchesFree
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target == "primary") "Primary Model" else "Fallback Model") },
        text = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showFreeOnly, onCheckedChange = { showFreeOnly = it })
                    Text("Only free models", fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${filteredModels.size} models", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredModels.isEmpty()) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No models found\nCheck API key", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)) {
                        items(filteredModels) { model ->
                            Surface(
                                onClick = { onSelect(model.id) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(model.name.take(40), fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp, maxLines = 1)
                                        Text(model.id, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1)
                                    }
                                    if (model.isFree) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = JarvisSuccess.copy(alpha = 0.15f)
                                        ) {
                                            Text("FREE", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                color = JarvisSuccess,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = {}
    )
}

// =============================================================================
// === Helpers ===
// =============================================================================

private fun checkAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

private fun startBubbleService(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Grant overlay permission first", Toast.LENGTH_LONG).show()
        return
    }
    context.startService(Intent(context, FloatingBubbleService::class.java).apply {
        action = FloatingBubbleService.ACTION_START
    })
}

private fun stopBubbleService(context: Context) {
    context.startService(Intent(context, FloatingBubbleService::class.java).apply {
        action = FloatingBubbleService.ACTION_STOP
    })
}
