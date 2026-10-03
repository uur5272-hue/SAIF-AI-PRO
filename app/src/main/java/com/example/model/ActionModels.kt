package com.example.model

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

data class ActionResult(
    val actionType: String,
    val target: String,
    val success: Boolean,
    val message: String,
    val directExecution: Boolean = true,
    val needsClarification: Boolean = false,
    val candidates: List<ContactItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val actionResult: ActionResult? = null,
    val language: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LiveVoiceState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING_ACTION,
    ERROR
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
