package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ChatMessage
import com.example.model.ConnectionStatus
import com.example.model.LiveVoiceState
import com.example.ui.components.ActionBanner
import com.example.ui.components.ContactClarificationDialog
import com.example.ui.components.OrbVisualizer
import com.example.ui.components.PermissionsDialog
import com.example.ui.theme.ArushiDeepBg
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiPrimaryLight
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTertiary
import kotlinx.coroutines.launch

@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isMicActive by viewModel.isMicActive.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val pendingClarification by viewModel.pendingClarification.collectAsState()
    val permissionsState by viewModel.permissionsState.collectAsState()
    val recentActions by viewModel.recentActions.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    // Multi-permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val audioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val contactsGranted = permissionsMap[Manifest.permission.READ_CONTACTS]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
        val callGranted = permissionsMap[Manifest.permission.CALL_PHONE]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)

        viewModel.updatePermissions(audioGranted, contactsGranted, callGranted)
    }

    fun checkAndRequestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE
            )
        )
    }

    // Initial check on launch
    LaunchedEffect(Unit) {
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val callGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermissions(audioGranted, contactsGranted, callGranted)
    }

    // Auto-scroll chat on new message
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Show error snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ArushiDeepBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. Top Bar
            TopBarSection(
                connectionStatus = connectionStatus,
                onSettingsClick = { showPermissionsDialog = true }
            )

            // 2. Multilingual & Status Indicator Chips
            LanguagePillsRow()

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Central Interactive Orb Visualizer
            OrbVisualizer(
                voiceState = voiceState,
                amplitude = amplitude,
                isMicActive = isMicActive,
                onOrbClick = {
                    if (!permissionsState.hasAudioPermission) {
                        checkAndRequestPermissions()
                    } else {
                        viewModel.toggleLiveSession()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // 4. Latest Action Notification Banner
            val latestAction = recentActions.firstOrNull()
            ActionBanner(
                actionResult = latestAction,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 5. Quick Voice Command Testing Chips
            QuickPromptsSection(
                onPromptSelected = { prompt ->
                    textInput = ""
                    viewModel.sendUserPrompt(prompt)
                }
            )

            // 6. Live Conversation & Execution History
            ConversationSection(
                messages = chatMessages,
                listState = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 7. Bottom Voice & Input Bar
            BottomInputBar(
                textInput = textInput,
                onTextChanged = { textInput = it },
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendUserPrompt(textInput)
                        textInput = ""
                    }
                },
                isMicActive = isMicActive,
                onMicToggle = {
                    if (!permissionsState.hasAudioPermission) {
                        checkAndRequestPermissions()
                    } else {
                        viewModel.toggleLiveSession()
                    }
                }
            )
        }
    }

    // Contact Disambiguation Dialog (multiple contacts matched e.g. 2 Rahuls)
    pendingClarification?.let { clarification ->
        ContactClarificationDialog(
            clarification = clarification,
            onSelectContact = { contact ->
                viewModel.selectCandidateContact(contact)
            },
            onDismiss = { viewModel.dismissClarification() }
        )
    }

    // Permissions Dialog
    if (showPermissionsDialog) {
        PermissionsDialog(
            permissionsState = permissionsState,
            onRequestPermissions = {
                showPermissionsDialog = false
                checkAndRequestPermissions()
            },
            onDismiss = { showPermissionsDialog = false }
        )
    }
}

@Composable
private fun TopBarSection(
    connectionStatus: ConnectionStatus,
    onSettingsClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ArushiPrimary, ArushiSecondary)
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "Arushi AI",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = "Multilingual Voice Assistant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArushiPrimaryLight,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live Status Pill
            val (statusText, statusColor) = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> Pair("Live", ArushiSuccess)
                ConnectionStatus.CONNECTING -> Pair("Connecting", ArushiTertiary)
                ConnectionStatus.ERROR -> Pair("Offline", ArushiError)
                ConnectionStatus.DISCONNECTED -> Pair("Ready", ArushiSecondary)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("connection_status_badge")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun LanguagePillsRow() {
    val languages = listOf("Hindi", "English", "Hinglish", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Punjabi", "Urdu")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(languages) { lang ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1438))
                    .border(0.5.dp, Color(0xFF3B2E60), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = lang,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickPromptsSection(
    onPromptSelected: (String) -> Unit
) {
    val testPrompts = listOf(
        "WhatsApp kholo" to "💬",
        "Open YouTube" to "📺",
        "Mummy ko call karo" to "📞",
        "Call Rahul" to "👤",
        "Call 9876543210" to "📱",
        "Hindi mein baat karo" to "🇮🇳",
        "Talk to me in English" to "🇬🇧",
        "Hinglish mein baat karo" to "✨",
        "Open settings" to "⚙️",
        "Open Chrome" to "🌐"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = "Voice Commands & Test Cases",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(testPrompts) { (prompt, emoji) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF241A45))
                        .border(1.dp, Color(0xFF3F306B), RoundedCornerShape(20.dp))
                        .clickable { onPromptSelected(prompt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("quick_prompt_${prompt.replace(" ", "_")}")
                ) {
                    Text(text = emoji, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationSection(
    messages: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
        ) {
            Text(
                text = "Tap the glowing orb or say \"Hello Arushi\" to start talking in any language.\nTry \"WhatsApp kholo\", \"Open YouTube\", or \"Call Mom\".",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(message = msg)
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) Brush.linearGradient(listOf(ArushiPrimary, Color(0xFF6D28D9)))
             else Brush.linearGradient(listOf(Color(0xFF1E1738), Color(0xFF281E48)))

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .testTag(if (isUser) "user_message_${message.id}" else "assistant_message_${message.id}")
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

@Composable
private fun BottomInputBar(
    textInput: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    isMicActive: Boolean,
    onMicToggle: () -> Unit
) {
    Surface(
        color = Color(0xFF140F2A),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Microphone FAB
            FloatingActionButton(
                onClick = onMicToggle,
                containerColor = if (isMicActive) ArushiSecondary else ArushiPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("microphone_fab")
            ) {
                Icon(
                    imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (isMicActive) "Mute Voice" else "Start Voice",
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text input field
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = if (isMicActive) "Listening or type command..." else "Type in Hindi, Hinglish, English...",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArushiPrimary,
                    unfocusedBorderColor = Color(0xFF342858),
                    focusedContainerColor = Color(0xFF1F173B),
                    unfocusedContainerColor = Color(0xFF1F173B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    IconButton(
                        onClick = onSend,
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier.testTag("send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank()) ArushiSecondary else Color(0xFF475569)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("chat_text_input")
            )
        }
    }
}
