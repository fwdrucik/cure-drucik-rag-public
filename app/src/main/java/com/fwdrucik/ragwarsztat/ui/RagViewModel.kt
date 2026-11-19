package com.fwdrucik.ragwarsztat.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fwdrucik.ragwarsztat.data.RagRepository
import com.fwdrucik.ragwarsztat.model.ConnectionProfile
import com.fwdrucik.ragwarsztat.model.ConnectionState
import com.fwdrucik.ragwarsztat.model.MessageSender
import com.fwdrucik.ragwarsztat.model.RagChatMessage
import com.fwdrucik.ragwarsztat.model.RagEngineType
import com.fwdrucik.ragwarsztat.model.RagSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ActiveViewerDoc(
    val filename: String,
    val brand: String,
    val page: Int,
    val totalPages: Int,
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class RagUiState(
    val selectedEngine: RagEngineType = RagEngineType.LM_STUDIO_RAG,
    val selectedProfile: ConnectionProfile = ConnectionProfile.WIFI_WARSZTAT,
    val hostIp: String = "192.168.0.166",
    val serverPort: Int = 8777,
    val selectedModel: String = "bielik-11b-v3.0-instruct-heretic-mpoa",
    val connectionState: ConnectionState = ConnectionState.Idle,
    val isEngineSelectorOpen: Boolean = false,
    val isCleaningMemory: Boolean = false,
    val memoryStatusMessage: String? = null,
    val activeViewerDoc: ActiveViewerDoc? = null,
    val messages: List<RagChatMessage> = listOf(
        RagChatMessage(
            id = "welcome-msg",
            sender = MessageSender.MECHANIK_AI,
            text = "Cześć! Jestem Cure Drucik – Twój dedykowany asystent techniczny podłączony bezpośrednio do bazy 521 książek serwisowych maszyn ciężkich i pojazdów.\n\nZadaj dowolne pytanie o kody usterek, momenty dokręcania, luzy zaworowe czy schematy hydrauliki i elektryki.",
            engineUsed = "Cure Drucik RAG",
            modelUsed = "Bielik 11B v3.0"
        )
    ),
    val isQueryLoading: Boolean = false,
    val totalDocumentsCount: Int = 521
)

class RagViewModel(
    private val repository: RagRepository = RagRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RagUiState())
    val uiState: StateFlow<RagUiState> = _uiState.asStateFlow()

    init {
        testEngineConnection()
    }

    fun setEngine(engine: RagEngineType) {
        _uiState.update { it.copy(selectedEngine = engine) }
        cleanMemoryAndSwitch(engine, _uiState.value.selectedModel)
    }

    fun setConnectionProfile(profile: ConnectionProfile) {
        _uiState.update {
            it.copy(
                selectedProfile = profile,
                hostIp = profile.ip
            )
        }
        testEngineConnection()
    }

    fun setCustomHost(ip: String) {
        _uiState.update { it.copy(hostIp = ip.trim()) }
    }

    fun setSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        cleanMemoryAndSwitch(_uiState.value.selectedEngine, model)
    }

    fun setEngineSelectorDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isEngineSelectorOpen = visible) }
    }

    fun cleanVramAndRam() {
        val state = _uiState.value
        _uiState.update { it.copy(isCleaningMemory = true, memoryStatusMessage = "Czyszczenie pamięci VRAM & RAM...") }
        viewModelScope.launch {
            val res = repository.cleanVramAndRam(state.hostIp, state.serverPort, state.selectedEngine, state.selectedModel)
            res.onSuccess { msg ->
                _uiState.update {
                    it.copy(
                        isCleaningMemory = false,
                        memoryStatusMessage = "🧹 $msg"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCleaningMemory = false,
                        memoryStatusMessage = "⚠️ Błąd: ${err.message}"
                    )
                }
            }
            testEngineConnection()
        }
    }

    private fun cleanMemoryAndSwitch(engine: RagEngineType, model: String) {
        val state = _uiState.value
        viewModelScope.launch {
            repository.cleanVramAndRam(state.hostIp, state.serverPort, engine, model)
            testEngineConnection()
        }
    }

    fun openPdfPageViewer(source: RagSource) {
        _uiState.update {
            it.copy(
                activeViewerDoc = ActiveViewerDoc(
                    filename = source.filename,
                    brand = source.brand,
                    page = source.page,
                    totalPages = source.totalPages,
                    isLoading = true
                )
            )
        }
        loadPageBitmap(source.filename, source.page)
    }

    fun changeViewerPage(newPage: Int) {
        val current = _uiState.value.activeViewerDoc ?: return
        val clamped = newPage.coerceIn(1, current.totalPages.coerceAtLeast(1))
        _uiState.update {
            it.copy(
                activeViewerDoc = current.copy(
                    page = clamped,
                    isLoading = true,
                    bitmap = null,
                    errorMessage = null
                )
            )
        }
        loadPageBitmap(current.filename, clamped)
    }

    fun closePdfPageViewer() {
        _uiState.update { it.copy(activeViewerDoc = null) }
    }

    private fun loadPageBitmap(filename: String, page: Int) {
        val state = _uiState.value
        viewModelScope.launch {
            val bitmap = repository.loadPdfPageImage(filename, page, state.hostIp, state.serverPort)
            _uiState.update {
                val doc = it.activeViewerDoc
                if (doc != null && doc.filename == filename && doc.page == page) {
                    it.copy(
                        activeViewerDoc = doc.copy(
                            bitmap = bitmap,
                            isLoading = false,
                            errorMessage = if (bitmap == null) "Nie udało się pobrać podglądu strony PDF." else null
                        )
                    )
                } else {
                    it
                }
            }
        }
    }

    fun testEngineConnection() {
        val state = _uiState.value
        _uiState.update { it.copy(connectionState = ConnectionState.Checking) }

        viewModelScope.launch {
            val result = repository.pingEngine(state.selectedEngine, state.hostIp, state.serverPort)
            result.onSuccess { msg ->
                _uiState.update { it.copy(connectionState = ConnectionState.Connected(msg)) }
            }.onFailure { err ->
                _uiState.update { it.copy(connectionState = ConnectionState.Error(err.message ?: "Błąd połączenia")) }
            }
        }
    }

    fun sendMessage(queryText: String) {
        if (queryText.isBlank() || _uiState.value.isQueryLoading) return
        val userMsg = RagChatMessage(
            id = "user-${UUID.randomUUID()}",
            sender = MessageSender.USER,
            text = queryText
        )
        _uiState.update { it.copy(messages = it.messages + userMsg, isQueryLoading = true) }

        val state = _uiState.value

        viewModelScope.launch {
            val result = repository.queryRag(
                query = queryText,
                engine = state.selectedEngine,
                host = state.hostIp,
                port = state.serverPort,
                selectedModel = state.selectedModel
            )
            result.onSuccess { aiMsg ->
                _uiState.update { it.copy(messages = it.messages + aiMsg, isQueryLoading = false) }
            }.onFailure { err ->
                val errorMsg = RagChatMessage(
                    id = "err-${UUID.randomUUID()}",
                    sender = MessageSender.MECHANIK_AI,
                    text = "⚠️ Błąd zapytania: ${err.message}\nUpewnij się, że komputer (${state.hostIp}:${state.serverPort}) jest połączony w sieci.",
                    engineUsed = state.selectedEngine.displayName,
                    modelUsed = "Błąd Połączenia"
                )
                _uiState.update { it.copy(messages = it.messages + errorMsg, isQueryLoading = false) }
            }
        }
    }
}
