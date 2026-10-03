package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.AndroidAppActionBridge
import com.example.live.GeminiLiveClient
import com.example.model.ActionResult
import com.example.model.ChatMessage
import com.example.model.ConnectionStatus
import com.example.model.ContactItem
import com.example.model.LiveVoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val hasAudioPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasCallPermission: Boolean = false
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    val actionBridge = AndroidAppActionBridge(application.applicationContext)
    val liveClient = GeminiLiveClient(application.applicationContext, actionBridge, viewModelScope)

    val connectionStatus: StateFlow<ConnectionStatus> = liveClient.connectionStatus
    val voiceState: StateFlow<LiveVoiceState> = liveClient.voiceState
    val isMicActive: StateFlow<Boolean> = liveClient.isMicActive
    val amplitude: StateFlow<Float> = liveClient.amplitude
    val chatMessages: StateFlow<List<ChatMessage>> = liveClient.chatMessages
    val errorMessage: StateFlow<String?> = liveClient.errorMessage

    private val _pendingClarification = MutableStateFlow<ActionResult?>(null)
    val pendingClarification: StateFlow<ActionResult?> = _pendingClarification.asStateFlow()

    private val _permissionsState = MutableStateFlow(PermissionState())
    val permissionsState: StateFlow<PermissionState> = _permissionsState.asStateFlow()

    private val _recentActions = MutableStateFlow<List<ActionResult>>(emptyList())
    val recentActions: StateFlow<List<ActionResult>> = _recentActions.asStateFlow()

    init {
        viewModelScope.launch {
            liveClient.lastAction.collect { action ->
                _recentActions.value = (listOf(action) + _recentActions.value).take(10)
                if (action.needsClarification && action.candidates.isNotEmpty()) {
                    _pendingClarification.value = action
                }
            }
        }
    }

    fun updatePermissions(audio: Boolean, contacts: Boolean, call: Boolean) {
        _permissionsState.value = PermissionState(
            hasAudioPermission = audio,
            hasContactsPermission = contacts,
            hasCallPermission = call
        )
    }

    fun toggleLiveSession() {
        if (connectionStatus.value == ConnectionStatus.CONNECTED) {
            liveClient.toggleMic()
        } else {
            liveClient.startLiveSession()
        }
    }

    fun sendUserPrompt(text: String) {
        liveClient.sendTextPrompt(text)
    }

    fun selectCandidateContact(contact: ContactItem) {
        _pendingClarification.value = null
        val result = actionBridge.makeCall(contact.phoneNumber)
        viewModelScope.launch {
            _recentActions.value = (listOf(result) + _recentActions.value).take(10)
            // Inform Live assistant what was selected
            liveClient.sendTextPrompt("Calling ${contact.name}")
        }
    }

    fun dismissClarification() {
        _pendingClarification.value = null
    }

    fun directExecuteAction(actionName: String, param: String = "") {
        viewModelScope.launch {
            val result = when (actionName) {
                "openWhatsApp" -> actionBridge.openWhatsApp()
                "openApp" -> actionBridge.openApp(param)
                "makeCall" -> actionBridge.makeCall(param)
                "callContact" -> actionBridge.callContact(param)
                "openUrl" -> actionBridge.openUrl(param)
                else -> ActionResult(actionType = actionName, target = param, success = false, message = "Unknown action")
            }
            _recentActions.value = (listOf(result) + _recentActions.value).take(10)
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveClient.cleanup()
    }
}
