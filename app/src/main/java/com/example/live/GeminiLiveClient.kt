package com.example.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.bridge.AndroidAppActionBridge
import com.example.model.ActionResult
import com.example.model.ChatMessage
import com.example.model.ConnectionStatus
import com.example.model.LiveVoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class GeminiLiveClient(
    private val context: Context,
    private val actionBridge: AndroidAppActionBridge,
    private val scope: CoroutineScope
) {

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _voiceState = MutableStateFlow(LiveVoiceState.IDLE)
    val voiceState: StateFlow<LiveVoiceState> = _voiceState.asStateFlow()

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _lastAction = MutableSharedFlow<ActionResult>(extraBufferCapacity = 5)
    val lastAction: SharedFlow<ActionResult> = _lastAction.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Audio recording (16 kHz mono 16-bit PCM)
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val sampleRateRecord = 16000
    private val channelConfigRecord = AudioFormat.CHANNEL_IN_MONO
    private val audioFormatRecord = AudioFormat.ENCODING_PCM_16BIT

    // Audio playback (24 kHz mono 16-bit PCM)
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val sampleRatePlay = 24000
    private val channelConfigPlay = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormatPlay = AudioFormat.ENCODING_PCM_16BIT

    // Buffer for assembling live speech transcript
    private var currentModelTurnText = StringBuilder()
    private var activeModelMessageId: String? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRatePlay,
                channelConfigPlay,
                audioFormatPlay
            )
            val bufferSize = minBufferSize * 4

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormatPlay)
                        .setSampleRate(sampleRatePlay)
                        .setChannelMask(channelConfigPlay)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startAudioPlaybackWorker()
        } catch (e: Exception) {
            _errorMessage.value = "Audio output initialization failed: ${e.message}"
        }
    }

    private fun startAudioPlaybackWorker() {
        playJob?.cancel()
        playJob = scope.launch(Dispatchers.IO) {
            for (chunk in audioChannel) {
                if (!isActive) break
                val track = audioTrack ?: continue
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        track.play()
                    } catch (_: Exception) {}
                }
                track.write(chunk, 0, chunk.size)

                // Compute output amplitude for wave animation
                var sum = 0.0
                val shortCount = chunk.size / 2
                for (i in 0 until shortCount) {
                    val low = chunk[i * 2].toInt()
                    val high = chunk[i * 2 + 1].toInt()
                    val sample = (high shl 8) or (low and 0xFF)
                    sum += sample * sample
                }
                val rms = sqrt(sum / shortCount).toFloat()
                val normalized = (rms / 32767f).coerceIn(0f, 1f)
                _amplitude.value = normalized
            }
        }
    }

    fun startLiveSession() {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED || _connectionStatus.value == ConnectionStatus.CONNECTING) {
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _errorMessage.value = "Gemini API key is not configured. Please add GEMINI_API_KEY in the Secrets panel."
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING
        _errorMessage.value = null

        val liveEndpoint = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(liveEndpoint).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _voiceState.value = LiveVoiceState.LISTENING
                sendSetupMessage(webSocket)
                startMicRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingLiveMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _voiceState.value = LiveVoiceState.ERROR
                _errorMessage.value = "Live connection failed: ${t.localizedMessage ?: "Unknown error"}"
                stopMicRecording()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _voiceState.value = LiveVoiceState.IDLE
                stopMicRecording()
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val setupJson = JSONObject()
        val setupObj = JSONObject()

        // Recommended model for Gemini Live audio & video conversation
        setupObj.put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")

        // Generation config with AUDIO modality and Aoede/Kore voice
        val genConfig = JSONObject()
        val modalities = JSONArray().apply { put("AUDIO") }
        genConfig.put("responseModalities", modalities)

        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", "Aoede") // Warm, articulate, multilingual female voice
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        genConfig.put("speechConfig", speechConfig)
        setupObj.put("generationConfig", genConfig)

        // System Instruction enforcing complete Multilingual Voice support & Android Control
        val systemInstruction = JSONObject()
        val parts = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", """
You are Arushi, an intelligent, charming, multilingual voice assistant and personal companion for Android devices.
CORE MULTILINGUAL INSTRUCTIONS:
1. You naturally understand and speak Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and all Gemini Live supported languages.
2. AUTOMATIC LANGUAGE DETECTION: Detect the user's spoken language on every turn.
   - If user speaks Hindi -> reply fluently in pure, natural Hindi.
   - If user speaks English -> reply fluently in natural English.
   - If user speaks Hinglish -> reply naturally in everyday Indian Hinglish.
   - If user switches language mid-conversation -> instantly adapt and respond in that new language.
   - Never ask user to manually select or switch languages.
3. Natural Voice-to-Voice: Keep spoken responses concise, friendly, warm, and natural.
4. APP CONTROL & FUNCTION CALLING:
   You have real Android tools:
   - 'openWhatsApp': call when user wants to open WhatsApp ("WhatsApp kholo", "open WhatsApp", "WhatsApp open karo", "WhatsApp chalao").
   - 'openApp': call when user wants to open an installed app or settings ("Open YouTube", "Instagram kholo", "Open Chrome", "Open Settings", "Camera open karo").
   - 'openUrl': call when user asks to open a web link or website.
   - 'makeCall': call when user gives a phone number to call ("Call 9876543210").
   - 'callContact': call when user wants to call someone by name ("Call Mom", "Mummy ko call karo", "Rahul ko call lagao", "Call Dad").
   ALWAYS call the tool when an action is commanded! Once you receive the tool response, briefly inform the user what was done in their spoken language.
        """.trimIndent())
        parts.put(partObj)
        systemInstruction.put("parts", parts)
        setupObj.put("systemInstruction", systemInstruction)

        // Tools: Function declarations
        val toolsArray = JSONArray()
        val toolsObj = JSONObject()
        val functionDeclarations = JSONArray()

        // 1. openWhatsApp
        val openWhatsAppFunc = JSONObject().apply {
            put("name", "openWhatsApp")
            put("description", "Opens WhatsApp application on the device. Triggered by requests like 'open WhatsApp', 'WhatsApp kholo', 'WhatsApp open karo'.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        }
        functionDeclarations.put(openWhatsAppFunc)

        // 2. openApp
        val openAppFunc = JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens an installed Android app or settings by name (e.g. YouTube, Instagram, Chrome, Camera, Settings, Maps, Spotify).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject()
                props.put("appName", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "The name of the app to launch (e.g., 'YouTube', 'Instagram', 'Chrome', 'Settings', 'Camera', 'Spotify')")
                })
                put("properties", props)
                put("required", JSONArray().apply { put("appName") })
            })
        }
        functionDeclarations.put(openAppFunc)

        // 3. openUrl
        val openUrlFunc = JSONObject().apply {
            put("name", "openUrl")
            put("description", "Opens a website URL in the browser.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject()
                props.put("url", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "The complete website URL (e.g. https://google.com)")
                })
                put("properties", props)
                put("required", JSONArray().apply { put("url") })
            })
        }
        functionDeclarations.put(openUrlFunc)

        // 4. makeCall
        val makeCallFunc = JSONObject().apply {
            put("name", "makeCall")
            put("description", "Initiates a phone call or opens dialer to a specified phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject()
                props.put("phoneNumber", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "The phone number to dial or call (e.g., '9876543210' or '+919876543210')")
                })
                put("properties", props)
                put("required", JSONArray().apply { put("phoneNumber") })
            })
        }
        functionDeclarations.put(makeCallFunc)

        // 5. callContact
        val callContactFunc = JSONObject().apply {
            put("name", "callContact")
            put("description", "Searches device contacts by name and initiates a phone call. (e.g., 'Mom', 'Mummy', 'Rahul', 'Dad', 'Priya').")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject()
                props.put("contactName", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "The contact name or relationship (e.g., 'Mom', 'Mummy', 'Rahul', 'Dad')")
                })
                put("properties", props)
                put("required", JSONArray().apply { put("contactName") })
            })
        }
        functionDeclarations.put(callContactFunc)

        toolsObj.put("functionDeclarations", functionDeclarations)
        toolsArray.put(toolsObj)
        setupObj.put("tools", toolsArray)

        setupJson.put("setup", setupObj)
        ws.send(setupJson.toString())
    }

    private fun handleIncomingLiveMessage(text: String) {
        try {
            val root = JSONObject(text)

            // 1. Check for Server Content (Audio streaming & transcripts)
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Handle model speech turn
                if (serverContent.has("modelTurn")) {
                    _voiceState.value = LiveVoiceState.SPEAKING
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Real-time Audio PCM data
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType", "")
                            if (mimeType.contains("audio/pcm")) {
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotBlank()) {
                                    val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                    audioChannel.trySend(pcmBytes)
                                }
                            }
                        }

                        // Text transcript
                        if (part.has("text")) {
                            val chunkText = part.getString("text")
                            currentModelTurnText.append(chunkText)
                            updateActiveModelMessage(currentModelTurnText.toString())
                        }
                    }
                }

                // Check for Interruption
                if (serverContent.optBoolean("interrupted", false)) {
                    flushAudioOutput()
                    _voiceState.value = LiveVoiceState.LISTENING
                }

                // Turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    currentModelTurnText.clear()
                    activeModelMessageId = null
                    if (_isMicActive.value) {
                        _voiceState.value = LiveVoiceState.LISTENING
                    } else {
                        _voiceState.value = LiveVoiceState.IDLE
                    }
                }
            }

            // 2. Check for Tool Call
            if (root.has("toolCall")) {
                _voiceState.value = LiveVoiceState.EXECUTING_ACTION
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()
                handleToolCalls(functionCalls)
            }

        } catch (e: Exception) {
            // Json parse warning
        }
    }

    private fun handleToolCalls(functionCalls: JSONArray) {
        scope.launch(Dispatchers.Main) {
            val responses = JSONArray()

            for (i in 0 until functionCalls.length()) {
                val call = functionCalls.getJSONObject(i)
                val callId = call.optString("id", UUID.randomUUID().toString())
                val functionName = call.optString("name", "")
                val args = call.optJSONObject("args") ?: JSONObject()

                val actionResult = when (functionName) {
                    "openWhatsApp" -> actionBridge.openWhatsApp()
                    "openApp" -> {
                        val appName = args.optString("appName", "App")
                        actionBridge.openApp(appName)
                    }
                    "openUrl" -> {
                        val url = args.optString("url", "")
                        actionBridge.openUrl(url)
                    }
                    "makeCall" -> {
                        val phone = args.optString("phoneNumber", "")
                        actionBridge.makeCall(phone)
                    }
                    "callContact" -> {
                        val contactName = args.optString("contactName", "")
                        actionBridge.callContact(contactName)
                    }
                    else -> ActionResult(
                        actionType = functionName,
                        target = "Unknown",
                        success = false,
                        message = "Unsupported function '$functionName'"
                    )
                }

                // Publish action result
                _lastAction.emit(actionResult)

                // Add to chat history
                val chatMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "⚙️ Executed ${actionResult.actionType}: ${actionResult.message}",
                    isUser = false,
                    actionResult = actionResult
                )
                _chatMessages.value = _chatMessages.value + chatMsg

                // Construct function response for Gemini Live
                val funcResponse = JSONObject()
                funcResponse.put("id", callId)
                funcResponse.put("name", functionName)

                val outputObj = JSONObject()
                outputObj.put("result", actionResult.message)
                outputObj.put("status", if (actionResult.success) "success" else "failed")
                outputObj.put("needsClarification", actionResult.needsClarification)
                if (actionResult.candidates.isNotEmpty()) {
                    val candArr = JSONArray()
                    actionResult.candidates.forEach { c ->
                        candArr.put("${c.name} (${c.phoneNumber})")
                    }
                    outputObj.put("candidateContacts", candArr)
                }
                funcResponse.put("response", JSONObject().apply { put("output", outputObj) })
                responses.put(funcResponse)
            }

            // Send toolResponse back to Gemini Live
            val toolResponseWrapper = JSONObject()
            val toolResponseObj = JSONObject()
            toolResponseObj.put("functionResponses", responses)
            toolResponseWrapper.put("toolResponse", toolResponseObj)

            withContext(Dispatchers.IO) {
                webSocket?.send(toolResponseWrapper.toString())
            }
        }
    }

    private fun updateActiveModelMessage(text: String) {
        val currentList = _chatMessages.value.toMutableList()
        val id = activeModelMessageId ?: UUID.randomUUID().toString().also {
            activeModelMessageId = it
            currentList.add(ChatMessage(id = it, text = text, isUser = false))
            _chatMessages.value = currentList
            return
        }

        val idx = currentList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            currentList[idx] = currentList[idx].copy(text = text)
            _chatMessages.value = currentList
        }
    }

    fun startMicRecording() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _errorMessage.value = "Microphone permission is required for voice conversation."
            return
        }

        if (_isMicActive.value) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRateRecord,
                channelConfigRecord,
                audioFormatRecord
            )
            val bufferSize = minBufferSize.coerceAtLeast(4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateRecord,
                channelConfigRecord,
                audioFormatRecord,
                bufferSize
            )

            audioRecord?.startRecording()
            _isMicActive.value = true
            _voiceState.value = LiveVoiceState.LISTENING

            recordJob?.cancel()
            recordJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                while (isActive && _isMicActive.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Calculate input amplitude
                        var sum = 0.0
                        val shortCount = read / 2
                        for (i in 0 until shortCount) {
                            val low = buffer[i * 2].toInt()
                            val high = buffer[i * 2 + 1].toInt()
                            val sample = (high shl 8) or (low and 0xFF)
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / shortCount).toFloat()
                        val normalized = (rms / 32767f).coerceIn(0f, 1f)
                        if (_voiceState.value == LiveVoiceState.LISTENING) {
                            _amplitude.value = normalized
                        }

                        // Send audio chunk to Gemini Live
                        val chunkData = if (read == buffer.size) buffer else buffer.copyOf(read)
                        val base64 = Base64.encodeToString(chunkData, Base64.NO_WRAP)

                        val realtimeInput = JSONObject()
                        val inputObj = JSONObject()
                        val mediaChunks = JSONArray()
                        val chunkObj = JSONObject()
                        chunkObj.put("mimeType", "audio/pcm;rate=16000")
                        chunkObj.put("data", base64)
                        mediaChunks.put(chunkObj)
                        inputObj.put("mediaChunks", mediaChunks)
                        realtimeInput.put("realtimeInput", inputObj)

                        webSocket?.send(realtimeInput.toString())
                    }
                    delay(40) // ~40ms chunk cadence
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Microphone error: ${e.message}"
            _isMicActive.value = false
        }
    }

    fun stopMicRecording() {
        _isMicActive.value = false
        _amplitude.value = 0f
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
        if (_voiceState.value == LiveVoiceState.LISTENING) {
            _voiceState.value = LiveVoiceState.IDLE
        }
    }

    fun toggleMic() {
        if (_isMicActive.value) {
            stopMicRecording()
        } else {
            if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
                startLiveSession()
            } else {
                startMicRecording()
            }
        }
    }

    fun sendTextPrompt(text: String) {
        if (text.isBlank()) return

        // Interrupt existing playback if speaking
        flushAudioOutput()

        // Append to chat history
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = true
        )
        _chatMessages.value = _chatMessages.value + userMsg

        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            startLiveSession()
        }

        scope.launch(Dispatchers.IO) {
            // Wait briefly for connection if connecting
            var attempts = 0
            while (_connectionStatus.value != ConnectionStatus.CONNECTED && attempts < 25) {
                delay(200)
                attempts++
            }

            val clientContent = JSONObject()
            val contentObj = JSONObject()
            val turns = JSONArray()
            val turn = JSONObject()
            turn.put("role", "user")
            val parts = JSONArray()
            parts.put(JSONObject().apply { put("text", text) })
            turn.put("parts", parts)
            turns.put(turn)
            contentObj.put("turns", turns)
            contentObj.put("turnComplete", true)
            clientContent.put("clientContent", contentObj)

            val sent = webSocket?.send(clientContent.toString()) ?: false
            if (sent) {
                _voiceState.value = LiveVoiceState.THINKING
            } else {
                _errorMessage.value = "Failed to send message. Please reconnect."
            }
        }
    }

    fun flushAudioOutput() {
        // Drain pending audio chunks
        while (audioChannel.tryReceive().isSuccess) {}
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {}
        _amplitude.value = 0f
    }

    fun cleanup() {
        stopMicRecording()
        playJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
    }
}
