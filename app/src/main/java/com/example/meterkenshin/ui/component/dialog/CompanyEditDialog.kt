package com.example.meterkenshin.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.utils.CompanyInfo

@Composable
fun CompanyEditDialog(
    currentInfo: CompanyInfo,
    onSave: (CompanyInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var companyName by remember { mutableStateOf(currentInfo.companyName) }
    var addressLine1 by remember { mutableStateOf(currentInfo.addressLine1) }
    var addressLine2 by remember { mutableStateOf(currentInfo.addressLine2) }
    var phone by remember { mutableStateOf(currentInfo.phone) }
    var paymentNote by remember { mutableStateOf(currentInfo.paymentNote) }
    var disclaimer by remember { mutableStateOf(currentInfo.disclaimer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Receipt Branding") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("Company Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = addressLine1,
                    onValueChange = { addressLine1 = it },
                    label = { Text("Address Line 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = addressLine2,
                    onValueChange = { addressLine2 = it },
                    label = { Text("Address Line 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = paymentNote,
                    onValueChange = { paymentNote = it },
                    label = { Text("Payment Note") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = disclaimer,
                    onValueChange = { disclaimer = it },
                    label = { Text("Disclaimer") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CompanyInfo(
                            companyName = companyName.trim(),
                            addressLine1 = addressLine1.trim(),
                            addressLine2 = addressLine2.trim(),
                            phone = phone.trim(),
                            paymentNote = paymentNote.trim(),
                            disclaimer = disclaimer.trim()
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
