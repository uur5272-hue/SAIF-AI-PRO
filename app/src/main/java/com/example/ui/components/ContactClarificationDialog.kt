package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ActionResult
import com.example.model.ContactItem
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTertiary

@Composable
fun ContactClarificationDialog(
    clarification: ActionResult,
    onSelectContact: (ContactItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1433),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("contact_clarification_dialog")
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Which contact should I call?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Multiple matches found for '${clarification.target}'",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArushiTertiary,
                                fontSize = 12.sp
                            )
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_clarification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(clarification.candidates) { contact ->
                        ContactItemCard(
                            contact = contact,
                            onSelect = { onSelectContact(contact) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tip: You can also say the full name or phone number out loud.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ContactItemCard(
    contact: ContactItem,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF281E48))
            .border(1.dp, Color(0xFF3F306B), RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp)
            .testTag("contact_item_${contact.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ArushiPrimary.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = ArushiPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFA5F3FC),
                        fontSize = 12.sp
                    )
                )
            }
        }

        Button(
            onClick = onSelect,
            colors = ButtonDefaults.buttonColors(containerColor = ArushiSuccess),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .height(36.dp)
                .testTag("call_contact_btn_${contact.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Call",
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Call",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
