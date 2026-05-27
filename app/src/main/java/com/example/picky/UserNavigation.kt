package com.example.picky

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun UserNavigation(onLogout: () -> Unit, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedRestaurant by remember { mutableStateOf<Restaurant?>(null) }
    var selectedMenuItemData by remember { mutableStateOf<Pair<MenuItem, String>?>(null) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = selectedMenuItemData != null || selectedRestaurant != null || selectedTab != 0) {
        when {
            selectedMenuItemData != null -> selectedMenuItemData = null
            selectedRestaurant != null -> selectedRestaurant = null
            else -> selectedTab = 0
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedRestaurant == null && selectedMenuItemData == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Trending") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Filled.ShoppingCart, null) }, label = { Text("Orders") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Filled.Person, null) }, label = { Text("Profile") })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedMenuItemData != null) {
                LocalMenuItemDetailScreen(
                    menuItem = selectedMenuItemData!!.first,
                    vendorId = selectedMenuItemData!!.second,
                    onBack = { selectedMenuItemData = null }
                )
            } else if (selectedRestaurant != null) {
                LocalRestaurantDetailScreen(
                    restaurant = selectedRestaurant!!,
                    onBack = { selectedRestaurant = null },
                    onOrderPlaced = { selectedRestaurant = null; selectedTab = 2 },
                    onItemClick = { item -> selectedMenuItemData = item to selectedRestaurant!!.id }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(onRestaurantClick = { selectedRestaurant = it })
                    1 -> TrendingScreen()
                    2 -> MyOrdersScreen(onReorder = { vendorId ->
                        // FIXED REORDER LOGIC
                        val db = FirebaseFirestore.getInstance()
                        // 1. Try Vendors
                        db.collection("vendors").document(vendorId).get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                selectedRestaurant = Restaurant(doc.id, doc.getString("businessName")?:doc.getString("Name")?:"Unknown", doc.getDouble("rating")?:0.0, doc.getString("ownerName")?:"", doc.getString("imageUrl")?:"")
                            } else {
                                // 2. Try Carts (Fallback)
                                db.collection("carts").document(vendorId).get().addOnSuccessListener { doc2 ->
                                    if (doc2.exists()) {
                                        selectedRestaurant = Restaurant(doc2.id, doc2.getString("Name")?:"Unknown", doc2.getDouble("Rating")?:0.0, doc2.getString("Owner Name")?:"", doc2.getString("ImageUrl")?:"")
                                    } else {
                                        Toast.makeText(context, "Restaurant not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    })
                    3 -> UserProfileScreen(onLogout, isDarkTheme, onToggleTheme)
                }
            }
        }
    }
}