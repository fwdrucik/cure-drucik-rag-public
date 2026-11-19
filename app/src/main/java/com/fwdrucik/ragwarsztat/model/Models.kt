package com.fwdrucik.ragwarsztat.model

enum class RagEngineType(val displayName: String, val icon: String, val defaultPort: Int) {
    LM_STUDIO_RAG("LM Studio RAG", "⚡", 8777),
    ANYTHING_LLM("AnythingLLM RAG", "🌐", 8777)
}

enum class ConnectionProfile(val label: String, val ip: String) {
    WIFI_WARSZTAT("Wi-Fi Warsztat", "192.168.0.166"),
    USB_CABLE("Kabel USB (ADB)", "127.0.0.1"),
    TAILSCALE("Tailscale VPN", "100.64.0.10"),
    EMULATOR("Emulator Android", "10.0.2.2")
}

enum class MessageSender {
    USER, MECHANIK_AI
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Checking : ConnectionState
    data class Connected(val message: String) : ConnectionState
    data class Error(val errorMessage: String) : ConnectionState
}

data class RagSource(
    val id: Int,
    val filename: String,
    val brand: String,
    val page: Int,
    val totalPages: Int = 1,
    val pageImageUrl: String? = null,
    val downloadUrl: String? = null,
    val snippet: String = ""
)

data class RagChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val sources: List<RagSource> = emptyList(),
    val engineUsed: String? = null,
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
