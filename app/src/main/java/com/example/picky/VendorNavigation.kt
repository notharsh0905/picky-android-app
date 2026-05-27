package com.example.picky

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

@Composable
fun VendorNavigation(onLogout: () -> Unit, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val userId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    // Navigation State
    var selectedTab by remember { mutableIntStateOf(0) }

    // Data Lists
    var menuFromVendors by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var menuFromCarts by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    val menuList = remember(menuFromVendors, menuFromCarts) { menuFromVendors + menuFromCarts }

    var ordersList by remember { mutableStateOf<List<Order>>(emptyList()) }
    var reviewsList by remember { mutableStateOf<List<Review>>(emptyList()) }
    var couponsList by remember { mutableStateOf<List<Coupon>>(emptyList()) }

    // Edit/Add Menu States
    var showMenuDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editingItemId by remember { mutableStateOf("") }
    var existingImageUrl by remember { mutableStateOf("") }

    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemDesc by remember { mutableStateOf("") }
    var isNonVeg by remember { mutableStateOf(false) }
    var itemImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingItem by remember { mutableStateOf(false) }
    val itemImageLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri -> itemImageUri = uri }

    // Coupon States
    var newCouponCode by remember { mutableStateOf("") }
    var newCouponAmount by remember { mutableStateOf("") }
    var newMinOrder by remember { mutableStateOf("") }

    // Profile States
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var passwordForDelete by remember { mutableStateOf("") }

    // --- DATA LISTENERS ---
    LaunchedEffect(Unit) {
        // 1. Fetch Menu (Merged)
        db.collection("vendors").document(userId).collection("menu").addSnapshotListener { s, _ ->
            if (s != null) menuFromVendors = s.documents.map {
                MenuItem(it.id, it.getString("name")?:"", it.getDouble("price")?:0.0, it.getString("imageUrl")?:"", it.getString("description")?:"", rating = it.getDouble("rating")?:0.0, ratingCount = it.getLong("ratingCount")?.toInt()?:0, isAvailable = it.getBoolean("isAvailable") ?: true)
            }
        }
        db.collection("carts").document(userId).collection("menu").addSnapshotListener { s2, _ ->
            if (s2 != null) menuFromCarts = s2.documents.map {
                MenuItem(it.id, it.getString("name")?:"", it.getDouble("price")?:0.0, it.getString("imageUrl")?:"", it.getString("description")?:"", rating = it.getDouble("rating")?:0.0, ratingCount = it.getLong("ratingCount")?.toInt()?:0, isAvailable = it.getBoolean("isAvailable") ?: true)
            }
        }

        // 2. Fetch Orders
        db.collection("orders").whereEqualTo("vendorId", userId).addSnapshotListener { s, _ ->
            if (s != null) ordersList = s.documents.map { doc ->
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

        // 3. Fetch Reviews (With Order Items)
        db.collection("vendors").document(userId).collection("reviews").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            if (s != null && !s.isEmpty) {
                reviewsList = s.documents.map { doc ->
                    Review(
                        id = doc.id,
                        userId = doc.getString("userId")?:"",
                        userName = doc.getString("userName")?:"Anonymous",
                        rating = doc.getLong("rating")?.toInt()?:0,
                        review = doc.getString("review")?:"",
                        imageUrl = doc.getString("imageUrl")?:"",
                        timestamp = doc.getDate("timestamp"),
                        likes = doc.get("likes") as? List<String>?:emptyList(),
                        dislikes = doc.get("dislikes") as? List<String>?:emptyList(),
                        replies = doc.get("replies") as? List<Map<String, Any>>?:emptyList(),
                        orderItems = doc.getString("orderItems") ?: ""
                    )
                }
            }
        }

        // 4. Fetch Coupons
        db.collection("coupons").addSnapshotListener { s, _ ->
            if (s != null) couponsList = s.documents.map { Coupon(it.id, it.getDouble("discountAmount")?:0.0, it.getBoolean("active")?:true, it.getDouble("minOrderAmount")?:0.0) }
        }
    }

    // --- HELPER FUNCTIONS ---
    fun openEditDialog(item: MenuItem) {
        itemName = item.name; itemPrice = item.price.toString(); itemDesc = item.description
        isNonVeg = item.name.contains("Chicken", true) || item.name.contains("Mutton", true) || item.name.contains("Egg", true)
        existingImageUrl = item.imageUrl; itemImageUri = null; editingItemId = item.id; isEditing = true; showMenuDialog = true
    }

    // --- SCAFFOLD UI ---
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.RestaurantMenu, null) },
                    label = { Text("Menu") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            val pending = ordersList.count { it.status == "Pending" }
                            if (pending > 0) Badge { Text("$pending") }
                        }) { Icon(Icons.Filled.ReceiptLong, null) }
                    },
                    label = { Text("Orders") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Reviews, null) },
                    label = { Text("Reviews") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Storefront, null) },
                    label = { Text("Business") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        itemName=""; itemPrice=""; itemDesc=""; isNonVeg=false; itemImageUri=null; existingImageUrl=""; isEditing=false; showMenuDialog=true
                    },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add Item") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when(selectedTab) {
                // --- TAB 0: MENU MANAGEMENT ---
                0 -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    item {
                        Text("Menu Management", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("${menuList.size} items active", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(menuList) { item ->
                        VendorMenuItemCard(
                            item = item,
                            onEdit = { openEditDialog(item) },
                            onDelete = {
                                db.collection("vendors").document(userId).collection("menu").document(item.id).delete()
                                db.collection("carts").document(userId).collection("menu").document(item.id).delete()
                            },
                            onToggleAvailability = { isAvail ->
                                db.collection("vendors").document(userId).collection("menu").document(item.id).update("isAvailable", isAvail)
                                db.collection("carts").document(userId).collection("menu").document(item.id).update("isAvailable", isAvail)
                            }
                        )
                    }

                    if(menuList.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Your menu is empty. Start adding items!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // --- TAB 1: ORDER MANAGEMENT ---
                1 -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    item {
                        VendorStatHeader(
                            title = "Pending Orders",
                            value = "${ordersList.count { it.status == "Pending" }}",
                            icon = Icons.Filled.NotificationsActive,
                            color = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (ordersList.isEmpty()) {
                        item { Text("No orders yet.", modifier = Modifier.padding(16.dp), color = Color.Gray) }
                    } else {
                        items(ordersList) { order ->
                            VendorOrderCard(order, db)
                        }
                    }
                }

                // --- TAB 2: REVIEWS & FEEDBACK ---
                2 -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        RatingBreakdown(reviewsList)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        items(reviewsList) { r ->
                            ReviewCard(r, userId, isVendorView = true)
                        }
                    }
                }

                // --- TAB 3: BUSINESS DASHBOARD ---
                3 -> VendorBusinessTab(
                    auth = auth, db = db, storage = storage,
                    ordersList = ordersList, couponsList = couponsList,
                    newCouponCode = newCouponCode, onCodeChange = { newCouponCode=it },
                    newCouponAmount = newCouponAmount, onAmountChange = { newCouponAmount=it },
                    newMinOrder = newMinOrder, onMinChange = { newMinOrder=it },
                    onLogout = onLogout, onDeleteAccountClick = { showDeleteConfirm=true },
                    isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme
                )
            }
        }
    }

    // --- MENU ADD/EDIT DIALOG ---
    if (showMenuDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploadingItem) showMenuDialog = false },
            title = { Text(if (isEditing) "Edit Item" else "New Item") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { itemImageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (itemImageUri != null) {
                            AsyncImage(model = itemImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else if (existingImageUrl.isNotEmpty()) {
                            AsyncImage(model = existingImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text("Upload Photo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = itemDesc,
                        onValueChange = { itemDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemPrice,
                            onValueChange = { itemPrice = it },
                            label = { Text("Price (₹)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Card(
                            modifier = Modifier.weight(1f).height(64.dp).clickable { isNonVeg = !isNonVeg },
                            colors = CardDefaults.cardColors(containerColor = if(isNonVeg) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if(isNonVeg) "Non-Veg" else "Veg", fontWeight = FontWeight.Bold, color = if(isNonVeg) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (itemName.isNotEmpty() && itemPrice.isNotEmpty()) {
                        isUploadingItem = true
                        val itemId = if (isEditing) editingItemId else UUID.randomUUID().toString()
                        val itemData = hashMapOf("name" to itemName, "price" to (itemPrice.toDoubleOrNull() ?: 0.0), "description" to itemDesc, "isNonVeg" to isNonVeg, "isAvailable" to true)
                        if (!isEditing) { itemData["rating"] = 0.0; itemData["ratingCount"] = 0 }

                        fun saveDoc(url: String) {
                            itemData["imageUrl"] = url
                            db.collection("vendors").document(userId).collection("menu").document(itemId).set(itemData, SetOptions.merge())
                                .addOnSuccessListener { isUploadingItem = false; showMenuDialog = false }
                        }

                        if (itemImageUri != null) {
                            storage.reference.child("menu/$itemId.jpg").putFile(itemImageUri!!)
                                .addOnSuccessListener { task -> task.storage.downloadUrl.addOnSuccessListener { saveDoc(it.toString()) } }
                        } else saveDoc(existingImageUrl)
                    }
                }) {
                    if (isUploadingItem) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("Save Item")
                }
            },
            dismissButton = { TextButton(onClick = { showMenuDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        var isProcessing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = { Column { Text("Permanently delete your business account?", color = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = passwordForDelete, onValueChange = { passwordForDelete = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation()) } },
            confirmButton = { Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { if (passwordForDelete.isNotEmpty()) { isProcessing = true; val cred = EmailAuthProvider.getCredential(auth.currentUser?.email!!, passwordForDelete); auth.currentUser?.reauthenticate(cred)?.addOnSuccessListener { db.collection("vendors").document(userId).delete(); auth.currentUser?.delete()?.addOnSuccessListener { isProcessing = false; showDeleteConfirm = false; onLogout() } }?.addOnFailureListener { isProcessing = false; Toast.makeText(context, "Wrong Password", Toast.LENGTH_SHORT).show() } } }) { Text("DELETE PERMANENTLY") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ==========================================
// BUSINESS DASHBOARD TAB
// ==========================================
@Composable
fun VendorBusinessTab(
    auth: FirebaseAuth, db: FirebaseFirestore, storage: FirebaseStorage,
    ordersList: List<Order>, couponsList: List<Coupon>,
    newCouponCode: String, onCodeChange: (String) -> Unit,
    newCouponAmount: String, onAmountChange: (String) -> Unit,
    newMinOrder: String, onMinChange: (String) -> Unit,
    onLogout: () -> Unit, onDeleteAccountClick: () -> Unit,
    isDarkTheme: Boolean, onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var businessName by remember { mutableStateOf("") }
    var businessImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentBusinessImage by remember { mutableStateOf("") }
    var isSavingProfile by remember { mutableStateOf(false) }
    val profileImageLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri -> businessImageUri = uri }

    LaunchedEffect(Unit) {
        db.collection("vendors").document(auth.currentUser?.uid ?: "").get().addOnSuccessListener {
            businessName = it.getString("businessName") ?: it.getString("restaurantName") ?: ""
            currentBusinessImage = it.getString("imageUrl") ?: ""
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- SECTION 1: PROFILE ---
        item {
            Text("Business Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { profileImageLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                        if(businessImageUri != null) AsyncImage(model = businessImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else if(currentBusinessImage.isNotEmpty()) AsyncImage(model = currentBusinessImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Store, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Restaurant Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if(businessName.isNotEmpty()) {
                                    isSavingProfile = true
                                    val uid = auth.currentUser?.uid ?: ""
                                    fun saveProfile(url: String) { db.collection("vendors").document(uid).set(mapOf("businessName" to businessName, "imageUrl" to url), SetOptions.merge()).addOnSuccessListener { isSavingProfile = false; Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show() } }
                                    if(businessImageUri != null) { storage.reference.child("vendors/$uid.jpg").putFile(businessImageUri!!).addOnSuccessListener { task -> task.storage.downloadUrl.addOnSuccessListener { saveProfile(it.toString()) } } } else saveProfile(currentBusinessImage)
                                }
                            },
                            enabled = !isSavingProfile,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if(isSavingProfile) "Saving..." else "Update Profile") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- SECTION 2: ANALYTICS ---
        item {
            Text("Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            val completed = ordersList.filter { it.status == "Completed" || it.status == "Rated" }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCardSmall(
                    label = "Total Revenue",
                    value = "₹${completed.sumOf { it.totalPrice }}",
                    color = Color(0xFF4CAF50),
                    icon = Icons.Filled.AttachMoney,
                    modifier = Modifier.weight(1f)
                )
                StatCardSmall(
                    label = "Total Orders",
                    value = "${completed.size}",
                    color = Color(0xFF2196F3),
                    icon = Icons.Filled.ShoppingBag,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- SECTION 3: COUPONS ---
        item {
            Text("Coupons", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create New Coupon", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newCouponCode, onValueChange = onCodeChange, label = { Text("Code (e.g. SAVE50)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(value = newCouponAmount, onValueChange = onAmountChange, label = { Text("Amount Off (₹)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = newMinOrder, onValueChange = onMinChange, label = { Text("Min Order") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { if(newCouponCode.isNotEmpty()) db.collection("coupons").document(newCouponCode).set(mapOf("discountAmount" to (newCouponAmount.toDoubleOrNull()?:0.0), "minOrderAmount" to (newMinOrder.toDoubleOrNull()?:0.0), "active" to true)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create Coupon") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            couponsList.forEach { c -> EnhancedCouponTicket(c, onDelete = { db.collection("coupons").document(c.code).delete() }, onToggle = { isActive -> db.collection("coupons").document(c.code).update("active", isActive) }) }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- SECTION 4: SETTINGS ---
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    ListItem(
                        headlineContent = { Text("Dark Mode") },
                        trailingContent = { Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() }) },
                        leadingContent = { Icon(Icons.Outlined.DarkMode, null) }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { onLogout() }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { onDeleteAccountClick() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ==========================================
// VENDOR UI SUB-COMPONENTS (Expert Cards)
// ==========================================

@Composable
fun VendorMenuItemCard(item: MenuItem, onEdit: () -> Unit, onDelete: () -> Unit, onToggleAvailability: (Boolean) -> Unit) {
    val opacity = if(item.isAvailable) 1f else 0.5f

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Image with Stock Overlay
            Box {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).alpha(opacity),
                    contentScale = ContentScale.Crop
                )
                if(!item.isAvailable) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("OFF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f).alpha(opacity)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("₹${item.price}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if(item.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                        Text(String.format("%.1f (%d)", item.rating, item.ratingCount), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            // Actions
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = item.isAvailable,
                    onCheckedChange = onToggleAvailability,
                    modifier = Modifier.scale(0.8f)
                )
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
fun VendorOrderCard(order: Order, db: FirebaseFirestore) {
    val statusColor = when (order.status) { "Pending" -> Color(0xFFFF9800); "Preparing" -> Color(0xFF2196F3); "Ready" -> Color(0xFF4CAF50); else -> Color.Gray }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if(order.status=="Pending") statusColor else Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(order.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Order #${order.id.takeLast(4).uppercase()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Surface(color = statusColor.copy(alpha=0.1f), shape = RoundedCornerShape(50)) {
                    Text(order.status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Items List
            order.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item["qty"]}x ${item["name"]}", style = MaterialTheme.typography.bodyMedium)
                    Text("₹${item["price"]}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total & Rating
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Total: ₹${order.totalPrice}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)

                if (order.status == "Rated" && order.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Customer Rating: ", fontSize = 12.sp, color = Color.Gray)
                        repeat(order.rating) { Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.status == "Pending") {
                    Button(
                        onClick = { db.collection("orders").document(order.id).update("status", "Rejected") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.weight(1f)
                    ) { Text("Reject") }

                    Button(
                        onClick = { db.collection("orders").document(order.id).update("status", "Preparing") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White), // Green for accept
                        modifier = Modifier.weight(1f)
                    ) { Text("Accept") }
                } else if (order.status == "Preparing") {
                    Button(
                        onClick = { db.collection("orders").document(order.id).update("status", "Ready") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Mark Order Ready") }
                }
            }
        }
    }
}

@Composable
fun StatCardSmall(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EnhancedCouponTicket(coupon: Coupon, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(coupon.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("₹${coupon.discountAmount} off on orders above ₹${coupon.minOrderAmount}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = coupon.isActive, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun VendorStatHeader(title: String, value: String, icon: ImageVector, color: Color) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha=0.1f))) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.background(color, CircleShape).padding(10.dp)) { Icon(icon, null, tint = Color.White) }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Extension to safely modify transparency modifier without imports issue
fun Modifier.alpha(alpha: Float) = this.then(Modifier.graphicsLayer(alpha = alpha))