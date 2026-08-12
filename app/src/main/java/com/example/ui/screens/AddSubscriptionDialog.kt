package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, urlOrContent: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var urlOrContent by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_sub_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Add Subscription or Nodes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subscription Name (Optional)") },
                    placeholder = { Text("e.g. My SingBox Nodes") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = urlOrContent,
                    onValueChange = { urlOrContent = it },
                    label = { Text("Subscription URL or Raw Nodes") },
                    placeholder = { Text("http://example.com/sub or vless://...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_url_input")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (urlOrContent.isNotBlank()) {
                            onAdd(name, urlOrContent)
                            onDismiss()
                        }
                    },
                    enabled = urlOrContent.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_add_sub_button")
                ) {
                    Text("Import Nodes")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
