/*package com.example.picky

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDashboard(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val userId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }

    var menuList by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var ordersList by remember { mutableStateOf<List<Order>>(emptyList()) }
    var reviewsList by remember { mutableStateOf<List<Review>>(emptyList()) }
    var couponsList by remember { mutableStateOf<List<Coupon>>(emptyList()) }

    // Menu Item State
    var showAddItem by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemDesc by remember { mutableStateOf("") }
    var itemImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingItem by remember { mutableStateOf(false) }
    val itemImageLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? -> itemImageUri = uri }

    // Coupon State
    var newCouponCode by remember { mutableStateOf("") }
    var newCouponAmount by remember { mutableStateOf("") }
    var newMinOrder by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("carts").document(userId).collection("menu").addSnapshotListener { s, _ ->
            if (s != null) menuList = s.documents.map {
                MenuItem(
                    id = it.id,
                    name = it.getString("name")?:"",
                    price = it.getDouble("price")?:0.0,
                    imageUrl = it.getString("imageUrl")?:"",
                    description = it.getString("description")?:""
                )
            }
        }

        db.collection("orders").whereEqualTo("vendorId", userId).addSnapshotListener { s, _ ->
            if (s != null) {
                ordersList = s.documents.map { doc ->
                    Order(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        userName = doc.getString("userName") ?: "Guest",
                        vendorId = doc.getString("vendorId") ?: "",
                        totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                        status = doc.getString("status") ?: "Pending",
                        paymentMethod = doc.getString("paymentMethod") ?: "COD",
                        deliveryAddress = doc.getString("deliveryAddress") ?: "",
                        timestamp = doc.getDate("timestamp"),
                        items = (doc.get("items") as? List<Map<String, Any>>) ?: emptyList(),
                        rating = doc.getLong("rating")?.toInt() ?: 0
                    )
                }.sortedByDescending { it.timestamp }
            }
        }

        db.collection("carts").document(userId).collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    reviewsList = s.documents.map { doc ->
                        Review(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            userName = doc.getString("userName") ?: "Anonymous",
                            rating = doc.getLong("rating")?.toInt() ?: 0,
                            review = doc.getString("review") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            timestamp = doc.getDate("timestamp"),
                            likes = (doc.get("likes") as? List<String>) ?: emptyList(),
                            dislikes = (doc.get("dislikes") as? List<String>) ?: emptyList(),
                            replies = (doc.get("replies") as? List<Map<String, Any>>) ?: emptyList()
                        )
                    }
                }
            }

        db.collection("coupons").addSnapshotListener { s, _ ->
            if (s != null) couponsList = s.documents.map {
                Coupon(
                    code = it.id,
                    discountAmount = it.getDouble("discountAmount") ?: 0.0,
                    isActive = it.getBoolean("active") ?: true,
                    minOrderAmount = it.getDouble("minOrderAmount") ?: 0.0
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Business Dashboard", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(auth.currentUser?.displayName ?: "Vendor", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.Red) } }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Filled.Edit, null) }, label = { Text("Menu") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Filled.List, null) }, label = { Text("Orders") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Reviews") })
                // FIXED: Changed LocalActivity -> List (Standard Icon)
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Filled.List, null) }, label = { Text("Coupons") })
                // FIXED: Changed PieChart -> DateRange (Standard Icon)
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("Stats") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            when(selectedTab) {
                0 -> {
                    // --- MENU TAB ---
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            VendorStatHeader("Total Items", "${menuList.size}", Icons.Filled.Edit, Color(0xFF9C27B0))
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { showAddItem = !showAddItem },
                                colors = CardDefaults.cardColors(containerColor = if(showAddItem) Color.White else MaterialTheme.colorScheme.primaryContainer),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if(showAddItem) Icons.Filled.KeyboardArrowUp else Icons.Filled.Add, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if(showAddItem) "Close Form" else "Add New Item", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }

                                    AnimatedVisibility(visible = showAddItem) {
                                        Column {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            // Image Picker Box
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).clickable { itemImageLauncher.launch("image/*") },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (itemImageUri != null) {
                                                    AsyncImage(model = itemImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                } else {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        // FIXED: AddPhotoAlternate -> Add (Standard Icon)
                                                        Icon(Icons.Filled.Add, null, tint = Color.Gray)
                                                        Text("Upload Dish Photo", fontSize = 10.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = itemDesc, onValueChange = { itemDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = itemPrice, onValueChange = { itemPrice = it }, label = { Text("Price (₹)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    if(itemName.isNotEmpty() && itemPrice.isNotEmpty() && !isUploadingItem) {
                                                        isUploadingItem = true
                                                        val price = itemPrice.toDoubleOrNull() ?: 0.0
                                                        val itemData = hashMapOf(
                                                            "name" to itemName, "price" to price, "description" to itemDesc,
                                                            "restaurantName" to (auth.currentUser?.displayName ?: "My Restaurant")
                                                        )

                                                        if (itemImageUri != null) {
                                                            val ref = storage.reference.child("menu/${UUID.randomUUID()}.jpg")
                                                            ref.putFile(itemImageUri!!).addOnSuccessListener {
                                                                ref.downloadUrl.addOnSuccessListener { dlUrl ->
                                                                    itemData["imageUrl"] = dlUrl.toString()
                                                                    db.collection("carts").document(userId).collection("menu").add(itemData).addOnSuccessListener {
                                                                        itemName=""; itemPrice=""; itemDesc=""; itemImageUri=null; isUploadingItem=false; showAddItem=false; Toast.makeText(context, "Item Added!", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            itemData["imageUrl"] = ""
                                                            db.collection("carts").document(userId).collection("menu").add(itemData).addOnSuccessListener {
                                                                itemName=""; itemPrice=""; itemDesc=""; isUploadingItem=false; showAddItem=false; Toast.makeText(context, "Item Added!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !isUploadingItem
                                            ) {
                                                if(isUploadingItem) Text("Publishing...") else Text("Publish Item")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        items(menuList) { item -> VendorMenuItemCard(item) { db.collection("carts").document(userId).collection("menu").document(item.id).delete() } }
                    }
                }
                1 -> {
                    // --- ORDERS TAB ---
                    LazyColumn {
                        item { VendorStatHeader("Pending Orders", "${ordersList.count { it.status == "Pending" }}", Icons.Filled.Notifications, Color(0xFFFF9800)); Spacer(modifier = Modifier.height(16.dp)) }
                        items(ordersList) { order -> VendorOrderCard(order, db) }
                    }
                }
                2 -> {
                    // --- REVIEWS TAB ---
                    Column(modifier = Modifier.fillMaxSize()) {
                        RatingBreakdown(reviewsList)
                        LazyColumn(modifier = Modifier.weight(1f)) { items(reviewsList) { r -> ReviewCard(r, userId, true) } }
                    }
                }
                3 -> {
                    // --- COUPONS TAB ---
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("Promotions Manager", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("New Coupon", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(value = newCouponCode, onValueChange = { newCouponCode = it.uppercase() }, label = { Text("Code (e.g. SAVE20)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row {
                                    OutlinedTextField(value = newCouponAmount, onValueChange = { newCouponAmount = it }, label = { Text("Discount ₹") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(value = newMinOrder, onValueChange = { newMinOrder = it }, label = { Text("Min Order ₹") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp))
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { if(newCouponCode.isNotEmpty()) db.collection("coupons").document(newCouponCode).set(mapOf("discountAmount" to (newCouponAmount.toDoubleOrNull()?:0.0), "minOrderAmount" to (newMinOrder.toDoubleOrNull()?:0.0), "active" to true)).addOnSuccessListener { newCouponCode=""; newCouponAmount=""; newMinOrder="" } },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                                ) { Icon(Icons.Filled.Add, null); Spacer(modifier = Modifier.width(4.dp)); Text("Launch Coupon") }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        LazyColumn {
                            items(couponsList) { c ->
                                EnhancedCouponTicket(c,
                                    onDelete = { db.collection("coupons").document(c.code).delete() },
                                    onToggle = { isActive -> db.collection("coupons").document(c.code).update("active", isActive) }
                                )
                            }
                        }
                    }
                }
                4 -> {
                    // --- STATS TAB ---
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("Financial Overview", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))

                            val completed = ordersList.filter { it.status == "Completed" || it.status == "Rated" }
                            val rev = completed.sumOf { it.totalPrice }
                            val count = completed.size
                            val avg = if (count > 0) rev / count else 0.0

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // FIXED: AttachMoney -> Star (Standard Icon)
                                StatCardSmall("Revenue", "₹$rev", Color(0xFF4CAF50), Icons.Filled.Star, Modifier.weight(1f))
                                // FIXED: ShoppingBag -> ShoppingCart (Standard Icon)
                                StatCardSmall("Orders", "$count", Color(0xFF2196F3), Icons.Filled.ShoppingCart, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // FIXED: TrendingUp -> Info (Standard Icon)
                            StatCardSmall("Avg. Order Value", "₹${String.format("%.0f", avg)}", Color(0xFFFF9800), Icons.Filled.Info, Modifier.fillMaxWidth())

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Revenue Trend", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Top Selling Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val itemCounts = ordersList.flatMap { it.items }.groupingBy { it["name"] as String }.eachCount().entries.sortedByDescending { it.value }.take(5)

                        items(itemCounts) { (name, count) ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical=4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, fontWeight = FontWeight.Medium)
                                    Text("$count sold", color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun VendorOrderCard(order: Order, db: FirebaseFirestore) {
    val statusColor = when (order.status) { "Pending" -> Color(0xFFFF9800); "Preparing" -> Color(0xFF2196F3); "Ready" -> Color(0xFF4CAF50); "Completed", "Rated" -> Color.Gray; else -> Color.Red }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (order.rating > 0) {
                    Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Rated ${order.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Surface(color = statusColor.copy(alpha=0.1f), shape = RoundedCornerShape(50)) { Text(order.status, color = statusColor, modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
                Text("₹${order.totalPrice}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(8.dp)); Text("Customer: ${order.userName}", fontWeight = FontWeight.Bold); Text("Pay: ${order.paymentMethod}", fontSize = 12.sp, color = Color.Gray); Divider(modifier = Modifier.padding(vertical = 8.dp))
            order.items.forEach { item -> Row { Text("• ", color = Color.Gray); Text("${item["name"]} ", fontWeight = FontWeight.Medium); Text("x ${item["qty"]}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
            Spacer(modifier = Modifier.height(16.dp))
            if(order.status == "Pending") { Row { Button(onClick = { db.collection("orders").document(order.id).update("status", "Rejected") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color.Red)) { Text("Reject") }; Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { db.collection("orders").document(order.id).update("status", "Preparing") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Accept") } } }
            else if (order.status == "Preparing") { Button(onClick = { db.collection("orders").document(order.id).update("status", "Ready") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) { Text("Mark Ready") } }
            else if (order.status == "Ready") { Button(onClick = { db.collection("orders").document(order.id).update("status", "Completed") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("Mark Completed") } }
        }
    }
}

@Composable
fun EnhancedCouponTicket(coupon: Coupon, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if(coupon.isActive) Color(0xFFE8F5E9) else Color(0xFFEEEEEE)), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(coupon.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = if(coupon.isActive) Color(0xFF2E7D32) else Color.Gray)
                    val minText = if (coupon.minOrderAmount > 0) "on orders > ₹${coupon.minOrderAmount.toInt()}" else "on any order"
                    Text("Save ₹${coupon.discountAmount.toInt()} $minText", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = if(coupon.isActive) Color(0xFFC8E6C9) else Color(0xFFFFCDD2), shape = RoundedCornerShape(4.dp)) { Text(if(coupon.isActive) "ACTIVE" else "PAUSED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(coupon.isActive) Color(0xFF1B5E20) else Color(0xFFB71C1C)) }
                }
                DashedLineVertical()
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 16.dp)) {
                    Switch(checked = coupon.isActive, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50)))
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = Color.Gray) }
                }
            }
        }
    }
}

@Composable
fun DashedLineVertical() { Canvas(modifier = Modifier.height(80.dp).width(2.dp)) { drawLine(color = Color.Gray.copy(alpha = 0.5f), start = Offset(0f, 0f), end = Offset(0f, size.height), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f), strokeWidth = 4f) } }

@Composable
fun StatCardSmall(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) { Icon(icon, null, tint = color); Spacer(modifier = Modifier.height(8.dp)); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color); Text(label, fontSize = 12.sp, color = Color.Gray) }
    }
}

@Composable
fun VendorStatHeader(title: String, value: String, icon: ImageVector, color: Color) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha=0.1f)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.background(color, CircleShape).padding(12.dp)) { Icon(icon, null, tint = Color.White) }; Spacer(modifier = Modifier.width(16.dp)); Column { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color); Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.Gray) } }
    }
}
*/
 */
