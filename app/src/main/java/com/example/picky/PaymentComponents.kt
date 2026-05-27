package com.example.picky

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PaymentSelector(
    selectedMethod: String,
    onMethodSelected: (String) -> Unit
) {
    Column {
        Text(
            "Payment Method",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentOption(
                label = "Cash",
                icon = Icons.Filled.Home,
                isSelected = selectedMethod == "COD"
            ) { onMethodSelected("COD") }

            PaymentOption(
                label = "Online",
                icon = Icons.Filled.ShoppingCart,
                isSelected = selectedMethod == "Online"
            ) { onMethodSelected("Online") }
        }
    }
}

@Composable
fun PaymentOption(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF4CAF50) else Color.LightGray

    Card(
        modifier = Modifier
            .width(120.dp)
            .height(90.dp)
            .clickable { onClick() }
            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE8F5E9) else Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = borderColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SimulatedPaymentGateway(
    amount: Double,
    onSuccess: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Processing Payment...") }

    LaunchedEffect(Unit) {
        while (progress < 1f) {
            progress += 0.05f
            delay(100)
        }
        status = "Payment Successful!"
        delay(800)
        onSuccess()
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Secure Payment") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Paying ₹$amount",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (progress < 1f) {
                    CircularProgressIndicator(progress = { progress })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status, color = Color.Gray)
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        tint = Color.Green,
                        modifier = Modifier.size(64.dp)
                    )
                    Text("Success!", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {}
    )
}
