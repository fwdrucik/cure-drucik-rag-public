package com.fwdrucik.ragwarsztat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fwdrucik.ragwarsztat.model.ConnectionProfile
import com.fwdrucik.ragwarsztat.model.ConnectionState
import com.fwdrucik.ragwarsztat.model.MessageSender
import com.fwdrucik.ragwarsztat.model.RagChatMessage
import com.fwdrucik.ragwarsztat.model.RagEngineType
import com.fwdrucik.ragwarsztat.model.RagSource
import com.fwdrucik.ragwarsztat.theme.AccentGreen
import com.fwdrucik.ragwarsztat.theme.CyanDiagnostics
import com.fwdrucik.ragwarsztat.theme.DarkBackground
import com.fwdrucik.ragwarsztat.theme.DarkCardBorder
import com.fwdrucik.ragwarsztat.theme.DarkSurface
import com.fwdrucik.ragwarsztat.theme.DarkSurfaceVariant
import com.fwdrucik.ragwarsztat.theme.ErrorRed
import com.fwdrucik.ragwarsztat.theme.IndustrialAmber
import com.fwdrucik.ragwarsztat.theme.IndustrialOrange
import com.fwdrucik.ragwarsztat.theme.TextPrimaryDark
import com.fwdrucik.ragwarsztat.theme.TextSecondaryDark

private val AVAILABLE_MODELS = listOf(
    "bielik-11b-v3.0-instruct-heretic-mpoa" to "🦅 Bielik 11B",
    "qwen3.5-35b-a3b-uncensored-hauhaucs-aggressive" to "⚡ Qwen 35B MoE",
    "qwen3.6-27b-heretic-uncensored-finetune-neo-code-di-imatrix-max" to "🧠 Qwen 27B"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RagViewModel) {
    val state by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) {
            try { listState.animateScrollToItem(state.messages.size - 1) } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cure Drucik", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimaryDark)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = IndustrialOrange.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, IndustrialOrange)
                            ) {
                                Text(
                                    text = "521 PDF",
                                    color = IndustrialOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${state.selectedEngine.displayName} • ${state.hostIp}",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    // Przycisk Czyszczenia VRAM / RAM
                    Surface(
                        onClick = { viewModel.cleanVramAndRam() },
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("🧹", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VRAM",
                                color = CyanDiagnostics,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val isConnOk = state.connectionState is ConnectionState.Connected
                    Surface(
                        onClick = { viewModel.setEngineSelectorDialogVisible(true) },
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnOk) AccentGreen else if (state.connectionState is ConnectionState.Checking) IndustrialAmber else ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.selectedEngine.icon + " Silnik",
                                color = TextPrimaryDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Pasek statusu pamięci VRAM / RAM
            AnimatedVisibility(visible = state.memoryStatusMessage != null) {
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyanDiagnostics.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(
                        text = state.memoryStatusMessage ?: "",
                        color = CyanDiagnostics,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Przełącznik Modeli LLM
            if (state.selectedEngine == RagEngineType.LM_STUDIO_RAG) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    AVAILABLE_MODELS.forEach { (modelId, label) ->
                        val isSelected = state.selectedModel == modelId
                        Surface(
                            onClick = { viewModel.setSelectedModel(modelId) },
                            color = if (isSelected) IndustrialOrange else DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) IndustrialOrange else DarkCardBorder)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else TextSecondaryDark,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Szybkie Kafelki Pytań Warsztatowych
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                item {
                    SuggestionChip(
                        enabled = !state.isQueryLoading,
                        onClick = { viewModel.sendMessage("Podaj kody błędów układu EBS Iveco Stralis.") },
                        label = { Text("⚡ Błędy Iveco EBS", fontSize = 11.sp, color = CyanDiagnostics) }
                    )
                }
                item {
                    SuggestionChip(
                        enabled = !state.isQueryLoading,
                        onClick = { viewModel.sendMessage("Jaki jest moment dokręcania głowicy silnika Volvo D12?") },
                        label = { Text("🔩 Głowica Volvo D12", fontSize = 11.sp, color = IndustrialOrange) }
                    )
                }
                item {
                    SuggestionChip(
                        enabled = !state.isQueryLoading,
                        onClick = { viewModel.sendMessage("Pokaż schemat hydrauliki i rozdzielacza koparki Komatsu PC340.") },
                        label = { Text("🚜 Hydraulika Komatsu", fontSize = 11.sp, color = AccentGreen) }
                    )
                }
                item {
                    SuggestionChip(
                        enabled = !state.isQueryLoading,
                        onClick = { viewModel.sendMessage("Jak zaprogramować piloty i krańcówki w centrali FAAC 748D?") },
                        label = { Text("🔌 Centrala FAAC 748D", fontSize = 11.sp, color = IndustrialAmber) }
                    )
                }
            }

            // Strumień Wiadomości
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    RagMessageCard(
                        msg = msg,
                        onOpenSourceViewer = { source ->
                            viewModel.openPdfPageViewer(source)
                        }
                    )
                }

                if (state.isQueryLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = IndustrialOrange,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Przeszukiwanie 521 książek serwisowych przez ${state.selectedEngine.displayName}...",
                                color = TextSecondaryDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pasek Wpisywania
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Zadaj pytanie o procedury, momenty, schematy...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !state.isQueryLoading) {
                                val q = inputText
                                inputText = ""
                                viewModel.sendMessage(q)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndustrialOrange,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (inputText.isNotBlank() && !state.isQueryLoading) {
                            val q = inputText
                            inputText = ""
                            viewModel.sendMessage(q)
                        }
                    },
                    enabled = !state.isQueryLoading && inputText.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IndustrialOrange,
                        disabledContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text("🔍", fontSize = 18.sp)
                }
            }
        }
    }

    // Modal Wyboru Silnika i Połączenia
    if (state.isEngineSelectorOpen) {
        EngineSelectorDialog(
            state = state,
            onSelectEngine = { viewModel.setEngine(it) },
            onSelectProfile = { viewModel.setConnectionProfile(it) },
            onSetCustomHost = { viewModel.setCustomHost(it) },
            onCleanMemory = { viewModel.cleanVramAndRam() },
            onTestConnection = { viewModel.testEngineConnection() },
            onDismiss = { viewModel.setEngineSelectorDialogVisible(false) }
        )
    }

    // Pełnoekranowy Interaktywny Podgląd Strony PDF z Zoomem
    state.activeViewerDoc?.let { doc ->
        PdfPageViewerDialog(
            doc = doc,
            onPreviousPage = { viewModel.changeViewerPage(doc.page - 1) },
            onNextPage = { viewModel.changeViewerPage(doc.page + 1) },
            onDismiss = { viewModel.closePdfPageViewer() }
        )
    }
}

@Composable
fun RagMessageCard(
    msg: RagChatMessage,
    onOpenSourceViewer: (RagSource) -> Unit
) {
    val isUser = msg.sender == MessageSender.USER
    var areSourcesExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) IndustrialOrange.copy(alpha = 0.22f) else DarkSurface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) IndustrialOrange.copy(alpha = 0.6f) else DarkCardBorder
            ),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser && msg.modelUsed != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 ${msg.modelUsed} (${msg.engineUsed ?: "Cure Drucik"})",
                            color = IndustrialOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = msg.text,
                    color = TextPrimaryDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (msg.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { areSourcesExpanded = !areSourcesExpanded },
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp).animateContentSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄 Źródła (${msg.sources.size} z 521 PDF)",
                                    color = CyanDiagnostics,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (areSourcesExpanded) "▲ Zwiń" else "▼ Pokaż & Zobacz Strony",
                                    color = TextSecondaryDark,
                                    fontSize = 10.sp
                                )
                            }

                            if (areSourcesExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    msg.sources.forEach { src ->
                                        Surface(
                                            color = DarkSurface,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, DarkCardBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "📖 ${src.brand} • ${src.filename}",
                                                        color = IndustrialOrange,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = IndustrialAmber.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "str. ${src.page}",
                                                            color = IndustrialAmber,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = src.snippet,
                                                    color = TextSecondaryDark,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Button(
                                                    onClick = { onOpenSourceViewer(src) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = IndustrialOrange.copy(alpha = 0.25f),
                                                        contentColor = IndustrialOrange
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("🔍 Zobacz stronę PDF (JPG & Zoom)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EngineSelectorDialog(
    state: RagUiState,
    onSelectEngine: (RagEngineType) -> Unit,
    onSelectProfile: (ConnectionProfile) -> Unit,
    onSetCustomHost: (String) -> Unit,
    onCleanMemory: () -> Unit,
    onTestConnection: () -> Unit,
    onDismiss: () -> Unit
) {
    var customHostText by remember(state.hostIp) { mutableStateOf(state.hostIp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cure Drucik • Silnik & Pamięć VRAM",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Wybierz aktywny silnik:",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RagEngineType.entries.forEach { engine ->
                        val isSelected = state.selectedEngine == engine
                        Surface(
                            onClick = { onSelectEngine(engine) },
                            color = if (isSelected) IndustrialOrange else DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(engine.icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = engine.displayName,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.Black else TextPrimaryDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkCardBorder)

                Text(
                    text = "2. Czyszczenie pamięci karty GPU & RAM:",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onCleanMemory,
                    enabled = !state.isCleaningMemory,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanDiagnostics),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (state.isCleaningMemory) "Czyszczenie w toku..." else "🧹 Wyczyść VRAM GPU (RTX 5070) & RAM",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = DarkCardBorder)

                Text(
                    text = "3. Profil sieciowy:",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionProfile.entries.forEach { profile ->
                        val isSelected = state.selectedProfile == profile
                        Surface(
                            onClick = { onSelectProfile(profile) },
                            color = if (isSelected) DarkSurfaceVariant else DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) IndustrialOrange else DarkCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(profile.label, fontSize = 12.sp, color = TextPrimaryDark, fontWeight = FontWeight.Medium)
                                Text(profile.ip, fontSize = 11.sp, color = IndustrialOrange, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkCardBorder)

                Text(
                    text = "4. Ręczny adres IP hosta:",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = customHostText,
                    onValueChange = {
                        customHostText = it
                        onSetCustomHost(it)
                    },
                    placeholder = { Text("192.168.0.166 lub 127.0.0.1") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndustrialOrange,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = DarkCardBorder)

                val (statusText, statusColor) = when (val s = state.connectionState) {
                    is ConnectionState.Idle -> "Gotowy do połączenia" to TextSecondaryDark
                    is ConnectionState.Checking -> "Sprawdzanie połączenia..." to IndustrialAmber
                    is ConnectionState.Connected -> s.message to AccentGreen
                    is ConnectionState.Error -> "⚠️ ${s.errorMessage}" to ErrorRed
                }

                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
            ) {
                Text("Zastosuj", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onTestConnection) {
                Text("Testuj Połączenie", color = CyanDiagnostics)
            }
        },
        containerColor = DarkSurface
    )
}
