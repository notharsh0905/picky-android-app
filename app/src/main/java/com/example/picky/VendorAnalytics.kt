package com.example.picky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RevenueChart(orders: List<Order>) {
    // 1. Calculate Stats
    // Fix: Completed orders include those that are "Rated"
    val completedCount = orders.count { it.status == "Completed" || it.status == "Rated" }
    val rejectedCount = orders.count { it.status == "Rejected" }
    val pendingCount = orders.count { it.status == "Pending" || it.status == "Preparing" }

    // Revenue only counts valid orders (Completed, Rated, Ready, etc.) - excludes Rejected/Pending
    val validOrders = orders.filter { it.status == "Completed" || it.status == "Rated" }
    val totalRevenue = validOrders.sumOf { it.totalPrice }

    // New Metric: Average Order Value
    val avgOrderValue = if (validOrders.isNotEmpty()) totalRevenue / validOrders.size else 0.0

    // Chart Data: Last 7 valid orders
    val dataPoints = orders.take(7).map { it.totalPrice.toFloat() }.ifEmpty { listOf(0f) }
    val maxVal = dataPoints.maxOrNull() ?: 100f

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Business Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // BIG REVENUE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)) // Light Green
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Total Revenue", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text("₹$totalRevenue", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Avg. Order: ₹${String.format("%.0f", avgOrderValue)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- STATS ROW ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Completed (Green)
            StatCard("Completed", "$completedCount", Color(0xFF4CAF50), Icons.Filled.CheckCircle, Modifier.weight(1f))

            // Rejected (Red)
            StatCard("Rejected", "$rejectedCount", Color(0xFFE57373), Icons.Filled.Close, Modifier.weight(1f))

            // Pending (Orange)
            StatCard("Pending", "$pendingCount", Color(0xFFFFB74D), Icons.Filled.Info, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- CHART ---
        Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val barWidth = size.width / (dataPoints.size * 2).coerceAtLeast(1)
            val spacing = size.width / dataPoints.size.coerceAtLeast(1)

            dataPoints.forEachIndexed { index, value ->
                val barHeight = if(maxVal > 0) (value / maxVal) * size.height else 0f
                // Draw Bar
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = Offset(x = (index * spacing) + (spacing/4), y = size.height - barHeight),
                    size = Size(width = barWidth, height = barHeight)
                )
            }
            // Draw Baseline
            drawLine(
                color = Color.Black,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 4f
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}