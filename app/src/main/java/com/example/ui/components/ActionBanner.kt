package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.model.ActionResult
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTertiary

@Composable
fun ActionBanner(
    actionResult: ActionResult?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = actionResult != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        if (actionResult == null) return@AnimatedVisibility

        val (icon, tint) = getActionIconAndTint(actionResult)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1738))
                .border(
                    width = 1.dp,
                    color = if (actionResult.success) ArushiSuccess.copy(alpha = 0.5f) else ArushiTertiary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
                .testTag("action_execution_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = actionResult.actionType,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = getFriendlyActionTitle(actionResult),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (actionResult.success) ArushiSuccess.copy(alpha = 0.2f)
                                    else if (actionResult.needsClarification) ArushiTertiary.copy(alpha = 0.2f)
                                    else ArushiError.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    actionResult.success && actionResult.directExecution -> "Executed Native"
                                    actionResult.success && !actionResult.directExecution -> "Dialer Opened"
                                    actionResult.needsClarification -> "Needs Choice"
                                    else -> "Notice"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = if (actionResult.success) ArushiSuccess
                                    else if (actionResult.needsClarification) ArushiTertiary
                                    else ArushiError
                                )
                            )
                        }
                    }

                    Text(
                        text = actionResult.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun getActionIconAndTint(result: ActionResult): Pair<ImageVector, Color> {
    return when (result.actionType) {
        "openWhatsApp" -> Pair(Icons.AutoMirrored.Filled.OpenInNew, Color(0xFF25D366))
        "makeCall", "callContact" -> Pair(Icons.Default.Phone, ArushiSuccess)
        "openUrl" -> Pair(Icons.Default.Language, ArushiSecondary)
        "openApp" -> {
            when {
                result.target.contains("Settings", ignoreCase = true) -> Pair(Icons.Default.Settings, ArushiTertiary)
                result.target.contains("YouTube", ignoreCase = true) -> Pair(Icons.Default.PlayCircleOutline, Color(0xFFFF0000))
                else -> Pair(Icons.AutoMirrored.Filled.OpenInNew, ArushiPrimary)
            }
        }
        else -> Pair(Icons.Default.CheckCircle, ArushiPrimary)
    }
}

private fun getFriendlyActionTitle(result: ActionResult): String {
    return when (result.actionType) {
        "openWhatsApp" -> "WhatsApp Action"
        "makeCall" -> "Phone Call: ${result.target}"
        "callContact" -> "Calling Contact: ${result.target}"
        "openApp" -> "Launch App: ${result.target}"
        "openUrl" -> "Browser Link: ${result.target}"
        else -> result.actionType
    }
}
