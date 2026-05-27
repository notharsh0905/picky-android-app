package com.example.picky

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.Locale

// ==========================================
// SHARED DATA MODELS (Global Access)
// ==========================================

data class MenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val imageUrl: String,
    val description: String,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val restaurantName: String = "",
    val isAvailable: Boolean = true // Controls "Out of Stock"
)

data class Restaurant(
    val id: String,
    val name: String,
    val rating: Double,
    val ownerName: String,
    val imageUrl: String,
    val distanceKm: Double = 1.5,
    val avgPrice: Int = 200
)

data class Order(
    val id: String,
    val userId: String,
    val userName: String,
    val vendorId: String,
    val totalPrice: Double,
    val status: String,
    val paymentMethod: String,
    val deliveryAddress: String,
    val timestamp: Date?,
    val items: List<Map<String, Any>>,
    val rating: Int = 0
)

data class Coupon(
    val code: String,
    val discountAmount: Double,
    val isActive: Boolean,
    val minOrderAmount: Double
)

data class Address(
    val id: String,
    val label: String,
    val fullAddress: String
)

data class CartItem(
    val name: String,
    val price: Double,
    val quantity: Int
)

// ==========================================
// TAB 0: HOME SCREEN (Themed & Expert UI)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onRestaurantClick: (Restaurant) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""

    // --- State Variables ---
    var rawRestaurants by remember { mutableStateOf<List<Restaurant>>(emptyList()) }
    var displayedRestaurants by remember { mutableStateOf<List<Restaurant>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }
    var calculatedRatings by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    // QR Scanner State
    var isScanning by remember { mutableStateOf(false) }

    val categories = listOf("All", "Pizza", "Burger", "Asian", "Desi", "Healthy", "Dessert")

    // --- Fetch Data Logic ---
    LaunchedEffect(Unit) {
        isLoading = true
        val vendorsRef = db.collection("vendors")
        val cartsRef = db.collection("carts")

        fun parse(doc: com.google.firebase.firestore.DocumentSnapshot): Restaurant? {
            return try {
                Restaurant(
                    id = doc.id,
                    name = doc.getString("businessName") ?: doc.getString("Name") ?: "Unknown",
                    rating = doc.getDouble("rating") ?: 0.0,
                    ownerName = doc.getString("ownerName") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: ""
                )
            } catch (e: Exception) { null }
        }

        // Listener for Vendors
        vendorsRef.addSnapshotListener { vSnap, _ ->
            // Listener for Carts
            cartsRef.addSnapshotListener { cSnap, _ ->
                val vList = vSnap?.documents?.mapNotNull { parse(it) } ?: emptyList()
                val cList = cSnap?.documents?.mapNotNull { parse(it) } ?: emptyList()

                // Merge and remove duplicates
                val merged = (vList + cList).distinctBy { it.id }
                rawRestaurants = merged

                // Calculate live ratings for each restaurant
                merged.forEach { res ->
                    db.collection("vendors").document(res.id).collection("menu").get()
                        .addOnSuccessListener { menuSnap ->
                            val items = if (!menuSnap.isEmpty) menuSnap.documents else emptyList()
                            val ratings = items.mapNotNull { it.getDouble("rating") }

                            if (ratings.isNotEmpty()) {
                                // Average of top 5 items
                                val top5 = ratings.sortedDescending().take(5)
                                val avg = top5.average()
                                calculatedRatings = calculatedRatings.toMutableMap().apply {
                                    put(res.id, avg)
                                }
                            }
                        }
                }
                isLoading = false
            }
        }

        // Listener for Favorites
        db.collection("users").document(uid).addSnapshotListener { s, _ ->
            favorites = (s?.get("favorites") as? List<String>) ?: emptyList()
        }
    }

    // --- Filtering Logic ---
    LaunchedEffect(searchQuery, selectedFilter, rawRestaurants, favorites, calculatedRatings) {
        // Apply calculated ratings
        val updatedList = rawRestaurants.map { r ->
            r.copy(rating = calculatedRatings[r.id] ?: r.rating)
        }

        var list = updatedList

        // Search Filter
        if (searchQuery.isNotEmpty()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        // Category Filter
        list = when (selectedFilter) {
            "Nearby" -> list.sortedBy { it.distanceKm }
            "Cheapest" -> list.sortedBy { it.avgPrice }
            "Popular" -> list.sortedByDescending { it.rating }
            "Favorites" -> list.filter { favorites.contains(it.id) }
            "All" -> list
            else -> list.filter { it.name.contains(selectedFilter, ignoreCase = true) }
        }
        displayedRestaurants = list
    }

    // --- UI Render ---
    if (isScanning) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            QRScannerView { scannedId ->
                isScanning = false
                val restaurant = rawRestaurants.find { it.id == scannedId }
                if (restaurant != null) {
                    onRestaurantClick(restaurant)
                }
            }
            IconButton(
                onClick = { isScanning = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                // Custom App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Deliver to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Current Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = { isScanning = true },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        // Use generic icon if extended library missing, or QrCodeScanner if added
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            "Scan",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Search Bar
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        onClick = {}
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search food or restaurants...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                // Categories Row
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedFilter == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = cat },
                                label = { Text(cat) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null
                            )
                        }
                    }
                }

                // Featured Title
                item {
                    Text(
                        text = "Featured Restaurants",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Restaurant List
                if (isLoading && rawRestaurants.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (displayedRestaurants.isEmpty()) {
                    item {
                        EmptyStateScreen("No restaurants found", "Try adjusting your filters.")
                    }
                } else {
                    items(displayedRestaurants) { r ->
                        ModernRestaurantCard(
                            restaurant = r,
                            isFavorite = favorites.contains(r.id),
                            onClick = { onRestaurantClick(r) },
                            onFavClick = {
                                val ref = db.collection("users").document(uid)
                                if (favorites.contains(r.id)) {
                                    ref.update("favorites", FieldValue.arrayRemove(r.id))
                                } else {
                                    ref.set(mapOf("exists" to true), com.google.firebase.firestore.SetOptions.merge())
                                    ref.update("favorites", FieldValue.arrayUnion(r.id))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- QR SCANNER COMPONENT (Fixed Annotation Error) ---
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QRScannerView(onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasPermission = it }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            BarcodeScanning.getClient().process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        barcode.rawValue?.let { onQrDetected(it) }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera Permission Required", color = Color.White)
        }
    }
}

// ==========================================
// TAB 1: TRENDING SCREEN
// ==========================================
@Composable
fun TrendingScreen() {
    val db = FirebaseFirestore.getInstance()
    var trendingItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collectionGroup("menu")
            .orderBy("rating", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { result ->
                trendingItems = result.documents.map {
                    MenuItem(
                        id = it.id,
                        name = it.getString("name")?:"",
                        price = it.getDouble("price")?:0.0,
                        imageUrl = it.getString("imageUrl")?:"",
                        description = it.getString("description")?:"",
                        rating = it.getDouble("rating")?:0.0,
                        ratingCount = it.getLong("ratingCount")?.toInt()?:0,
                        restaurantName = it.getString("restaurantName")?:""
                    )
                }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Trending Now 🔥",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Top rated by foodies near you",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn {
                items(trendingItems) { item ->
                    TrendingItemCard(item)
                }
            }
        }
    }
}

// ==========================================
// TAB 2: ORDERS HISTORY
// ==========================================
@Composable
fun MyOrdersScreen(onReorder: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var myOrders by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Map<String, Any>?>(null) }
    var currentRating by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }
    var isUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        db.collection("orders")
            .whereEqualTo("userId", auth.currentUser?.uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    myOrders = s.documents.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    }
                }
            }
    }

    if (showRatingDialog && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { if(!isUploading) showRatingDialog = false },
            title = { Text("Rate Order", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { currentRating = star }) {
                                Icon(
                                    Icons.Filled.Star,
                                    null,
                                    tint = if (star <= currentRating) Color(0xFFFFD700) else MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Review") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    isUploading = true
                    // Extract items for review summary
                    val items = selectedOrder!!["items"] as? List<Map<String, Any>> ?: emptyList()
                    val itemsSummary = items.joinToString(", ") { "${it["qty"]}x ${it["name"]}" }

                    val reviewData: Map<String, Any> = hashMapOf(
                        "userId" to (auth.currentUser?.uid?:""),
                        "userName" to (auth.currentUser?.displayName?:"User"),
                        "rating" to currentRating,
                        "review" to reviewText,
                        "timestamp" to Date(),
                        "orderId" to (selectedOrder!!["id"] as String),
                        "orderItems" to itemsSummary // SAVING SUMMARY HERE
                    )

                    uploadImageAndSubmit(context, db, selectedOrder!!["vendorId"] as String, reviewData, imageUri, null) {
                        isUploading = false
                        showRatingDialog = false
                    }
                }) { Text(if(isUploading) "Wait..." else "Submit") }
            },
            dismissButton = { TextButton(onClick = { showRatingDialog = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Your Orders",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (myOrders.isEmpty()) {
            EmptyStateScreen("No orders", "Hungry? Place an order now!")
        } else {
            LazyColumn {
                items(myOrders) { order ->
                    ModernOrderCard(order, onReorder) { selected ->
                        selectedOrder = selected
                        showRatingDialog = true
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: PROFILE SCREEN
// ==========================================
@Composable
fun UserProfileScreen(onLogout: () -> Unit, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var addresses by remember { mutableStateOf<List<Address>>(emptyList()) }
    var showAddAddress by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newFull by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("users").document(user?.uid?:"").addSnapshotListener { s, _ ->
            addresses = (s?.get("addresses") as? List<Map<String, String>>)?.map {
                Address(it["id"]?:"", it["label"]?:"", it["fullAddress"]?:"")
            } ?: emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Profile Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(user?.email?:"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // Settings List
        ListItem(
            headlineContent = { Text("Dark Mode", color = MaterialTheme.colorScheme.onSurface) },
            trailingContent = { Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() }) },
            leadingContent = { Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.primary) }
        )

        ListItem(
            headlineContent = { Text("Saved Addresses", color = MaterialTheme.colorScheme.onSurface) },
            trailingContent = {
                IconButton(onClick = { showAddAddress=true }) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            leadingContent = { Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary) }
        )

        // Address List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(addresses) { a ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ListItem(
                        headlineContent = { Text(a.label, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(a.fullAddress) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    db.collection("users").document(user?.uid?:"").update("addresses", FieldValue.arrayRemove(mapOf("id" to a.id, "label" to a.label, "fullAddress" to a.fullAddress)))
                                }
                            ) {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        ) {
            Text("Log Out")
        }
    }

    // Add Address Dialog
    if (showAddAddress) {
        AlertDialog(
            onDismissRequest={showAddAddress=false},
            confirmButton={
                Button(onClick={
                    if(newLabel.isNotEmpty() && newFull.isNotEmpty()){
                        db.collection("users").document(user?.uid?:"").update("addresses", FieldValue.arrayUnion(mapOf("id" to java.util.UUID.randomUUID().toString(),"label" to newLabel,"fullAddress" to newFull)))
                        showAddAddress=false
                    }
                }){ Text("Save") }
            },
            text={
                Column{
                    OutlinedTextField(value=newLabel, onValueChange={newLabel=it}, label={Text("Label")})
                    OutlinedTextField(value=newFull, onValueChange={newFull=it}, label={Text("Address")})
                }
            }
        )
    }
}

// --- SHARED COMPONENTS (EXPERT UI) ---
@Composable
fun EmptyStateScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.SearchOff,
            null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ModernRestaurantCard(restaurant: Restaurant, isFavorite: Boolean, onClick: () -> Unit, onFavClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                if (restaurant.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = restaurant.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                }

                // Gradient Overlay
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))

                IconButton(
                    onClick = onFavClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
                ) {
                    Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (isFavorite) Color.Red else Color.Gray)
                }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(restaurant.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("• ${restaurant.distanceKm} km • ₹${restaurant.avgPrice} for two", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                }

                if (restaurant.rating > 0) {
                    Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFF4CAF50)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(String.format("%.1f", restaurant.rating), color = Color.White, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrendingItemCard(item: MenuItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(item.restaurantName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Text(" ${item.rating}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun ModernOrderCard(order: Map<String, Any>, onReorder: (String) -> Unit, onRateClick: (Map<String, Any>) -> Unit) {
    val status = order["status"] as? String ?: "Pending"
    val color = when(status) { "Ready", "Completed" -> Color(0xFF4CAF50); "Rejected" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(order["vendorName"] as? String ?: "Restaurant", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("₹${order["totalPrice"]}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Show item summary
            val items = order["items"] as? List<Map<String, Any>>
            val itemText = items?.take(2)?.joinToString { "${it["qty"]}x ${it["name"]}" } ?: "Items"
            Text(itemText, color = Color.Gray, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                if(status == "Completed" || status == "Rated") {
                    Button(onClick = { onRateClick(order) }, modifier = Modifier.height(32.dp)) { Text("Rate") }
                } else if(status == "Ready") {
                    Button(onClick = { /* Mark complete logic */ }, modifier = Modifier.height(32.dp)) { Text("Picked Up") }
                }
            }
        }
    }
}

// --- PUSH NOTIFICATION SERVICE CLASS ---
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel("Default", "Default", NotificationManager.IMPORTANCE_DEFAULT))
            }
            manager.notify(
                0,
                NotificationCompat.Builder(this, "Default")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(it.title)
                    .setContentText(it.body)
                    .build()
            )
        }
    }
}