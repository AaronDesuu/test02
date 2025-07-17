package com.example.meterkenshin.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meterkenshin.R
import com.example.meterkenshin.permissions.BluetoothPermissionHandler
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothPermissionScreen(
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = BluetoothPermissionHandler.getRequiredBluetoothPermissions()

    val bluetoothPermissionsState = rememberMultiplePermissionsState(
        permissions = requiredPermissions
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied()
        }
    }

    LaunchedEffect(bluetoothPermissionsState.allPermissionsGranted) {
        if (bluetoothPermissionsState.allPermissionsGranted) {
            onPermissionsGranted()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorResource(R.color.background_light)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Card(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(60.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorResource(R.color.primary_light).copy(alpha = 0.1f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = colorResource(R.color.primary_light)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Bluetooth Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colorResource(R.color.on_background_light)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = "MeterKenshin needs Bluetooth permissions to connect and print to your Woosim printer. This enables automatic receipt printing for meter readings.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colorResource(R.color.on_surface_variant_light),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission List
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorResource(R.color.surface_light)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Required Permissions:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.marginBottom(12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(getPermissionDescriptions()) { permission ->
                            PermissionItem(
                                icon = permission.icon,
                                title = permission.title,
                                description = permission.description,
                                isGranted = BluetoothPermissionHandler.hasPermission(context, permission.permission)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (bluetoothPermissionsState.shouldShowRationale) {
                            // Show rationale and request again
                            bluetoothPermissionsState.launchMultiplePermissionRequest()
                        } else {
                            bluetoothPermissionsState.launchMultiplePermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.primary_light)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant Permissions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedButton(
                    onClick = onPermissionsDenied,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Skip for Now",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (bluetoothPermissionsState.shouldShowRationale) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(R.color.warning_container_light)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colorResource(R.color.warning_light),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "These permissions are required for printer functionality. You can enable them later in app settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorResource(R.color.on_warning_container_light)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) {
                colorResource(R.color.success_light)
            } else {
                colorResource(R.color.on_surface_variant_light)
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colorResource(R.color.on_surface_variant_light)
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = colorResource(R.color.success_light),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Extension function to add bottom margin
private fun Modifier.marginBottom(dp: androidx.compose.ui.unit.Dp): Modifier {
    return this.then(Modifier.padding(bottom = dp))
}

// Data class for permission descriptions
private data class PermissionDescription(
    val permission: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String
)

private fun getPermissionDescriptions(): List<PermissionDescription> {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        listOf(
            PermissionDescription(
                permission = android.Manifest.permission.BLUETOOTH_CONNECT,
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth Connect",
                description = "Connect to your Woosim printer"
            ),
            PermissionDescription(
                permission = android.Manifest.permission.BLUETOOTH_SCAN,
                icon = Icons.Default.Search,
                title = "Bluetooth Scan",
                description = "Discover nearby Bluetooth devices"
            ),
            PermissionDescription(
                permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
                icon = Icons.Default.LocationOn,
                title = "Location Access",
                description = "Required for Bluetooth device discovery"
            )
        )
    } else {
        listOf(
            PermissionDescription(
                permission = android.Manifest.permission.BLUETOOTH,
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth",
                description = "Basic Bluetooth functionality"
            ),
            PermissionDescription(
                permission = android.Manifest.permission.BLUETOOTH_ADMIN,
                icon = Icons.Default.Settings,
                title = "Bluetooth Admin",
                description = "Manage Bluetooth connections"
            ),
            PermissionDescription(
                permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
                icon = Icons.Default.LocationOn,
                title = "Fine Location",
                description = "Required for Bluetooth device discovery"
            ),
            PermissionDescription(
                permission = android.Manifest.permission.ACCESS_COARSE_LOCATION,
                icon = Icons.Default.LocationOn,
                title = "Coarse Location",
                description = "Required for Bluetooth device discovery"
            )
        )
    }
}