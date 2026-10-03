package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.ui.PermissionState
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTertiary

@Composable
fun PermissionsDialog(
    permissionsState: PermissionState,
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasApiKey = BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1433),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("permissions_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Device Permissions & Live AI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_permissions_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                PermissionRow(
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Required for Gemini Live voice-to-voice conversation",
                    granted = permissionsState.hasAudioPermission,
                    icon = Icons.Default.Mic
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow(
                    title = "Contacts (READ_CONTACTS)",
                    description = "Needed to look up contacts by name (e.g. Mom, Rahul)",
                    granted = permissionsState.hasContactsPermission,
                    icon = Icons.Default.ContactPhone
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow(
                    title = "Phone Calling (CALL_PHONE)",
                    description = "Enables direct calling (opens dialer if not granted)",
                    granted = permissionsState.hasCallPermission,
                    icon = Icons.Default.Phone
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow(
                    title = "Gemini API Key",
                    description = if (hasApiKey) "Active from Secrets panel" else "Configured via AI Studio Secrets",
                    granted = hasApiKey,
                    icon = Icons.Default.Key
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = ArushiPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_permissions_action_button")
                ) {
                    Text(
                        text = "Request All Permissions",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF281E48))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) ArushiSuccess.copy(alpha = 0.2f)
                        else ArushiTertiary.copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) ArushiSuccess else ArushiTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (granted) ArushiSuccess.copy(alpha = 0.2f)
                    else ArushiTertiary.copy(alpha = 0.2f)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (granted) "Granted" else "Needed",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (granted) ArushiSuccess else ArushiTertiary
                )
            )
        }
    }
}
