package com.example.meterkenshin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Meter Statistics Row with Nearby Count
 */
@Composable
fun MeterStatisticsRow(
    totalMeters: Int,
    showingMeters: Int,
    nearbyMeters: Int = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(), // Ensure Row fills the width
        horizontalArrangement = Arrangement.SpaceEvenly // Use SpaceEvenly
    ) {
        StatisticCard(
            label = "Total",
            value = totalMeters.toString(),
            icon = Icons.Default.ElectricBolt,
            color = MaterialTheme.colorScheme.primary
            // modifier = Modifier.weight(1f) // <-- REMOVE THIS
        )
        StatisticCard(
            label = "Showing",
            value = showingMeters.toString(),
            icon = Icons.Default.Search,
            color = MaterialTheme.colorScheme.secondary
            // modifier = Modifier.weight(1f) // <-- REMOVE THIS
        )
        StatisticCard(
            label = "Online",
            value = nearbyMeters.toString(),
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50)
            // modifier = Modifier.weight(1f) // <-- REMOVE THIS
        )
    }
}


/**
 * Individual statistic card
 */
@Composable
private fun StatisticCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier, // <-- REMOVE .fillMaxWidth()
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp), // <-- REMOVE .fillMaxWidth()
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}