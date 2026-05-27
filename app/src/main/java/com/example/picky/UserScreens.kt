/*package com.example.picky

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import java.util.Date

// --- UI CONSTANTS ---
val PrimaryGradient = Brush.horizontalGradient(listOf(Color(0xFF6200EE), Color(0xFF3700B3)))
val CardShadow = 8.dp

// ==========================================
// MAIN NAVIGATION CONTROLLER
// ==========================================
@Composable
fun UserNavigation(onLogout: () -> Unit) {
    // 0 = Home, 1 = Trending, 2 = Orders, 3 = Profile
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedRestaurant by remember { mutableStateOf<Restaurant?>(null) }
    var selectedMenuItemData by remember { mutableStateOf<Pair<MenuItem, String>?>(null) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        // Ensure NotificationHelper exists in your project
        NotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handle Back Button
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
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Home, null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.Star, null) },
                        label = { Text("Trending") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.ShoppingCart, null) },
                        label = { Text("Orders") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Filled.Person, null) },
                        label = { Text("Profile") }
                    )
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
                    onOrderPlaced = {
                        selectedRestaurant = null
                        selectedTab = 2 // Go to orders tab
                    },
                    onItemClick = { item ->
                        selectedMenuItemData = item to selectedRestaurant!!.id
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(onRestaurantClick = { selectedRestaurant = it })
                    1 -> TrendingScreen()
                    2 -> MyOrdersScreen(onReorder = { vendorId ->
                        FirebaseFirestore.getInstance().collection("vendors").document(vendorId).get().addOnSuccessListener { doc ->
                            if(doc.exists()) {
                                selectedRestaurant = Restaurant(
                                    doc.id,
                                    doc.getString("businessName") ?: doc.getString("Name") ?: "Unknown",
                                    doc.getDouble("Rating") ?: 0.0,
                                    doc.getString("Owner Name") ?: "",
                                    doc.getString("imageUrl") ?: doc.getString("ImageUrl") ?: ""
                                )
                            }
                        }
                    })
                    3 -> UserProfileScreen(onLogout)
                }
            }
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
        Text("Trending in Your City 🔥", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Most loved dishes this week", color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (trendingItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No trending items found yet.", color = Color.Gray)
                    Text("Ratings will appear here soon!", fontSize = 12.sp, color = Color.LightGray)
                }
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

@Composable
fun TrendingItemCard(item: MenuItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (item.restaurantName.isNotEmpty()) {
                    Text("from ${item.restaurantName}", fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Text(" ${item.rating} (${item.ratingCount})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("₹${item.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// LOCAL MENU ITEM DETAIL SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMenuItemDetailScreen(menuItem: MenuItem, vendorId: String, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var itemReviews by remember { mutableStateOf<List<Review>>(emptyList()) }

    LaunchedEffect(menuItem.id) {
        // Try getting reviews from BOTH collections to ensure we find them
        db.collection("vendors").document(vendorId).collection("menu").document(menuItem.id)
            .collection("itemReviews").orderBy("timestamp", Query.Direction.DESCENDING)
            .get().addOnSuccessListener { s ->
                if (!s.isEmpty) {
                    itemReviews = s.documents.map { doc ->
                        Review(doc.id, doc.getString("userId")?:"", doc.getString("userName")?:"Anonymous", doc.getLong("rating")?.toInt()?:0, doc.getString("review")?:"", doc.getString("imageUrl")?:"", doc.getDate("timestamp"), menuItemId = menuItem.id)
                    }
                } else {
                    // Fallback to CARTS if Vendors was empty
                    db.collection("carts").document(vendorId).collection("menu").document(menuItem.id)
                        .collection("itemReviews").orderBy("timestamp", Query.Direction.DESCENDING)
                        .get().addOnSuccessListener { s2 ->
                            itemReviews = s2.documents.map { doc ->
                                Review(doc.id, doc.getString("userId")?:"", doc.getString("userName")?:"Anonymous", doc.getLong("rating")?.toInt()?:0, doc.getString("review")?:"", doc.getString("imageUrl")?:"", doc.getDate("timestamp"), menuItemId = menuItem.id)
                            }
                        }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(menuItem.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                if (menuItem.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = menuItem.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.LightGray))
                }
            }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(menuItem.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("₹${menuItem.price}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    if (menuItem.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(menuItem.description, style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ratings & Reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${menuItem.rating}", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Row { repeat(5) { index -> Icon(Icons.Filled.Star, null, tint = if (index < menuItem.rating.toInt()) Color(0xFFFFD700) else Color.LightGray) } }
                            Text("${menuItem.ratingCount} ratings", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (itemReviews.isEmpty()) {
                item { Text("No reviews yet for this item.", modifier = Modifier.padding(16.dp), color = Color.Gray) }
            } else {
                items(itemReviews) { review -> ReviewCard(review, vendorId) }
            }
        }
    }
}

// ==========================================
// TAB 0: HOME SCREEN (DUAL FETCHING LOGIC)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onRestaurantClick: (Restaurant) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""

    var displayedRestaurants by remember { mutableStateOf<List<Restaurant>>(emptyList()) }
    var allRestaurants by remember { mutableStateOf<List<Restaurant>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isRefreshing by remember { mutableStateOf(false) }

    val categories = listOf("All", "Pizza", "Burger", "Asian", "Desi", "Healthy")

    fun fetchRestaurants() {
        isRefreshing = true
        // Try fetching from 'vendors' collection first (likely where Exoticafe is)
        db.collection("vendors").get().addOnSuccessListener { vendorSnap ->
            val vendorList = vendorSnap.documents.mapNotNull {
                try {
                    Restaurant(
                        id = it.id,
                        name = it.getString("businessName") ?: it.getString("Name") ?: "Unknown Cafe",
                        rating = it.getDouble("rating") ?: it.getDouble("Rating") ?: 0.0,
                        ownerName = it.getString("ownerName") ?: it.getString("Owner Name") ?: "",
                        imageUrl = it.getString("imageUrl") ?: it.getString("ImageUrl") ?: ""
                    )
                } catch (e: Exception) { null }
            }

            // If empty, try fallback 'carts' collection
            if (vendorList.isEmpty()) {
                db.collection("carts").get().addOnSuccessListener { cartSnap ->
                    val cartList = cartSnap.documents.mapNotNull {
                        try {
                            Restaurant(
                                id = it.id,
                                name = it.getString("Name") ?: "Unknown",
                                rating = it.getDouble("Rating") ?: 0.0,
                                ownerName = it.getString("Owner Name") ?: "",
                                imageUrl = it.getString("ImageUrl") ?: ""
                            )
                        } catch (e: Exception) { null }
                    }
                    allRestaurants = cartList
                    displayedRestaurants = cartList
                    isRefreshing = false
                }
            } else {
                allRestaurants = vendorList
                displayedRestaurants = vendorList
                isRefreshing = false
            }
        }.addOnFailureListener { isRefreshing = false }
    }

    LaunchedEffect(Unit) {
        fetchRestaurants()
        db.collection("users").document(uid).addSnapshotListener { s, _ ->
            favorites = (s?.get("favorites") as? List<String>) ?: emptyList()
        }
    }

    LaunchedEffect(searchQuery, selectedFilter, allRestaurants, favorites) {
        var list = allRestaurants
        if (searchQuery.isNotEmpty()) list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }

        list = when (selectedFilter) {
            "Nearby" -> list.sortedBy { it.distanceKm }
            "Cheapest" -> list.sortedBy { it.avgPrice }
            "Popular" -> list.sortedByDescending { it.rating }
            "Favorites" -> list.filter { favorites.contains(it.id) }
            else -> list.filter { it.name.contains(selectedFilter, ignoreCase = true) }
        }
        displayedRestaurants = list
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hungry?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text("Let's find some delicious food.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            }
            IconButton(onClick = { fetchRestaurants() }) {
                if (isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Icon(Icons.Filled.Refresh, "Refresh")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search restaurants...") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.LightGray)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(categories) { cat ->
                val isSelected = selectedFilter == cat
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedFilter = cat }) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(if(isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
                        Text(cat.take(1), color = if(isSelected) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(cat, fontSize = 12.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Nearby Restaurants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (displayedRestaurants.isEmpty()) {
            EmptyStateScreen("No restaurants found", "Try refreshing or changing filters.")
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(displayedRestaurants) { r ->
                    ModernRestaurantCard(
                        restaurant = r,
                        isFavorite = favorites.contains(r.id),
                        onClick = { onRestaurantClick(r) },
                        onFavClick = {
                            val userRef = db.collection("users").document(uid)
                            if (favorites.contains(r.id)) userRef.update("favorites", FieldValue.arrayRemove(r.id))
                            else {
                                userRef.set(mapOf("exists" to true), com.google.firebase.firestore.SetOptions.merge())
                                userRef.update("favorites", FieldValue.arrayUnion(r.id))
                            }
                        }
                    )
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
    val context = LocalContext.current
    var myOrders by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Map<String, Any>?>(null) }
    var currentRating by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.collection("orders").whereEqualTo("userId", auth.currentUser?.uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    myOrders = s.documents.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data["rating"] = doc.getLong("rating")?.toInt() ?: 0
                        data
                    }
                }
            }
    }

    if (showRatingDialog && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { if(!isUploading) showRatingDialog = false },
            title = { Text("Rate & Review") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isUploading) { CircularProgressIndicator(); Text("Uploading...", modifier = Modifier.padding(top=8.dp)) }
                    else {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            (1..5).forEach { star -> IconButton(onClick = { currentRating = star }, modifier = Modifier.size(48.dp)) { Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = if (star <= currentRating) Color(0xFFFFD700) else Color.LightGray, modifier = Modifier.size(40.dp)) } }
                        }
                        if (imageUri != null) {
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                IconButton(onClick = { imageUri = null }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha=0.5f), CircleShape).size(24.dp)) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        OutlinedTextField(value = reviewText, onValueChange = { reviewText = it }, label = { Text("Write a review") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { launcher.launch("image/*") }) { Icon(Icons.Filled.Add, "Upload Image") } }, maxLines = 3)
                    }
                }
            },
            confirmButton = {
                if (!isUploading) {
                    Button(onClick = {
                        isUploading = true
                        val uid = auth.currentUser?.uid ?: ""
                        val vendorId = selectedOrder!!["vendorId"] as String
                        val orderId = selectedOrder!!["id"] as String
                        val reviewData: Map<String, Any> = hashMapOf("userId" to uid, "userName" to (auth.currentUser?.displayName ?: "Foodie"), "rating" to currentRating, "review" to reviewText, "timestamp" to Date(), "orderId" to orderId, "likes" to emptyList<String>(), "dislikes" to emptyList<String>())
                        uploadImageAndSubmit(context, db, vendorId, reviewData, imageUri, null) { isUploading = false; showRatingDialog = false }
                    }) { Text("Submit") }
                }
            },
            dismissButton = { if(!isUploading) TextButton(onClick = { showRatingDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Past Orders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (myOrders.isEmpty()) {
            EmptyStateScreen("No orders yet", "Time to eat!")
        } else {
            LazyColumn {
                items(myOrders) { order ->
                    ModernOrderCard(order, onReorder) { selected ->
                        selectedOrder = selected; showRatingDialog = true; currentRating = 5; reviewText = ""; imageUri = null
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
fun UserProfileScreen(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val user = auth.currentUser

    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var addresses by remember { mutableStateOf<List<Address>>(emptyList()) }

    // Dialog States
    var showAddAddress by remember { mutableStateOf(false) }
    var showEditName by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Inputs
    var newAddressLabel by remember { mutableStateOf("") }
    var newAddressFull by remember { mutableStateOf("") }
    var newNameInput by remember { mutableStateOf("") }
    var passwordForDelete by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.collection("users").document(user?.uid ?: "").addSnapshotListener { s, _ ->
            if (s != null && s.exists()) {
                addresses = (s.get("addresses") as? List<Map<String, String>>)?.map {
                    Address(it["id"]?:"", it["label"]?:"", it["fullAddress"]?:"")
                } ?: emptyList()
            }
        }
    }

    // EDIT NAME DIALOG
    if (showEditName) {
        AlertDialog(
            onDismissRequest = { showEditName = false },
            title = { Text("Edit Name") },
            text = { OutlinedTextField(value = newNameInput, onValueChange = { newNameInput = it }, label = { Text("New Name") }) },
            confirmButton = {
                Button(onClick = {
                    if (newNameInput.isNotEmpty()) {
                        user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(newNameInput).build())?.addOnSuccessListener {
                            // Also update in Firestore if you store user data there
                            db.collection("users").document(user.uid).update("name", newNameInput)
                            name = newNameInput
                            showEditName = false
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditName = false }) { Text("Cancel") } }
        )
    }

    // ADD ADDRESS DIALOG
    if (showAddAddress) {
        AlertDialog(
            onDismissRequest = { showAddAddress = false },
            title = { Text("Add Address") },
            text = {
                Column {
                    OutlinedTextField(value = newAddressLabel, onValueChange = { newAddressLabel = it }, label = { Text("Label (e.g. Home)") })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newAddressFull, onValueChange = { newAddressFull = it }, label = { Text("Full Address") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if(newAddressLabel.isNotEmpty() && newAddressFull.isNotEmpty()) {
                        val newAddr = mapOf("id" to java.util.UUID.randomUUID().toString(), "label" to newAddressLabel, "fullAddress" to newAddressFull)
                        db.collection("users").document(user?.uid?:"").update("addresses", FieldValue.arrayUnion(newAddr))
                        showAddAddress = false; newAddressLabel = ""; newAddressFull = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddAddress = false }) { Text("Cancel") } }
        )
    }

    // DELETE ACCOUNT DIALOG
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if(!isProcessing) showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = {
                Column {
                    Text("This action cannot be undone. All your data will be lost.", color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Please enter your password to confirm:")
                    OutlinedTextField(
                        value = passwordForDelete,
                        onValueChange = { passwordForDelete = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordForDelete.isNotEmpty()) {
                            isProcessing = true
                            val credential = EmailAuthProvider.getCredential(user?.email!!, passwordForDelete)
                            user.reauthenticate(credential).addOnSuccessListener {
                                // 1. Delete Firestore Data
                                db.collection("users").document(user.uid).delete()
                                // 2. Delete Auth Account
                                user.delete().addOnSuccessListener {
                                    isProcessing = false
                                    showDeleteConfirm = false
                                    onLogout() // Navigate back to login
                                }.addOnFailureListener { e ->
                                    isProcessing = false
                                    Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }.addOnFailureListener {
                                isProcessing = false
                                Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE PERMANENTLY") }
            },
            dismissButton = { if(!isProcessing) TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Profile Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp).background(Color.LightGray, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontSize = 32.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { newNameInput = name; showEditName = true }) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(email, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Saved Addresses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddAddress = true }) { Icon(Icons.Filled.Add, null) }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(addresses) { addr ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(addr.label, fontWeight = FontWeight.Bold)
                        Text(addr.fullAddress, color = Color.Gray)
                        IconButton(
                            onClick = {
                                val mapToRemove = mapOf("id" to addr.id, "label" to addr.label, "fullAddress" to addr.fullAddress)
                                db.collection("users").document(user?.uid?:"").update("addresses", FieldValue.arrayRemove(mapToRemove))
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) { Icon(Icons.Filled.Delete, null, tint = Color.Red) }
                    }
                }
            }
        }

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF3E0), contentColor = Color(0xFFFF9800))
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }

            Button(
                onClick = { passwordForDelete = ""; showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color.Red)
            ) {
                Icon(Icons.Filled.Delete, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account")
            }
        }
    }
}

// ==========================================
// LOCAL RESTAURANT DETAIL SCREEN (FIXED FOR MENU/REVIEW LOADING)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRestaurantDetailScreen(
    restaurant: Restaurant,
    onBack: () -> Unit,
    onOrderPlaced: () -> Unit,
    onItemClick: (MenuItem) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var reviewsList by remember { mutableStateOf<List<Review>>(emptyList()) }
    val cart = remember { mutableStateMapOf<String, CartItem>() }

    var showPayment by remember { mutableStateOf(false) }
    var isSimulatingPayment by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("COD") }
    var userAddresses by remember { mutableStateOf<List<Address>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<Address?>(null) }
    var couponCode by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf(0.0) }
    var couponMessage by remember { mutableStateOf("") }

    LaunchedEffect(restaurant.id) {
        // 1. Try Fetching Menu from 'vendors' collection first
        db.collection("vendors").document(restaurant.id).collection("menu").get().addOnSuccessListener { r ->
            if (!r.isEmpty) {
                menuItems = r.documents.map {
                    MenuItem(id = it.id, name = it.getString("name")?:"", price = it.getDouble("price")?:0.0, imageUrl = it.getString("imageUrl")?:"", description = it.getString("description")?:"", rating = it.getDouble("rating")?:0.0, ratingCount = it.getLong("ratingCount")?.toInt()?:0)
                }
            } else {
                // Fallback to 'carts' collection
                db.collection("carts").document(restaurant.id).collection("menu").get().addOnSuccessListener { r2 ->
                    menuItems = r2.documents.map {
                        MenuItem(id = it.id, name = it.getString("name")?:"", price = it.getDouble("price")?:0.0, imageUrl = it.getString("imageUrl")?:"", description = it.getString("description")?:"", rating = it.getDouble("rating")?:0.0, ratingCount = it.getLong("ratingCount")?.toInt()?:0)
                    }
                }
            }
        }

        // 2. Fetch Reviews (Try 'vendors' first, fallback to 'carts')
        db.collection("vendors").document(restaurant.id).collection("reviews").addSnapshotListener { s, _ ->
            if (s != null && !s.isEmpty) {
                reviewsList = s.documents.map { Review(it.id, it.getString("userId")?:"", it.getString("userName")?:"", it.getLong("rating")?.toInt()?:0, it.getString("review")?:"", it.getString("imageUrl")?:"", it.getDate("timestamp"), it.get("likes") as? List<String>?:emptyList(), it.get("dislikes") as? List<String>?:emptyList(), it.get("replies") as? List<Map<String,Any>>?:emptyList()) }
            } else {
                db.collection("carts").document(restaurant.id).collection("reviews").addSnapshotListener { s2, _ ->
                    if (s2 != null) {
                        reviewsList = s2.documents.map { Review(it.id, it.getString("userId")?:"", it.getString("userName")?:"", it.getLong("rating")?.toInt()?:0, it.getString("review")?:"", it.getString("imageUrl")?:"", it.getDate("timestamp"), it.get("likes") as? List<String>?:emptyList(), it.get("dislikes") as? List<String>?:emptyList(), it.get("replies") as? List<Map<String,Any>>?:emptyList()) }
                    }
                }
            }
        }

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            userAddresses = (doc.get("addresses") as? List<Map<String, String>>)?.map { Address(it["id"]?:"", it["label"]?:"", it["fullAddress"]?:"") } ?: emptyList()
            selectedAddress = userAddresses.firstOrNull()
        }
    }

    val subTotal = cart.values.sumOf { it.price * it.quantity }
    val finalTotal = (subTotal - discount).coerceAtLeast(0.0)

    if (isSimulatingPayment) {
        LocalSimulatedPaymentGateway(amount = finalTotal) {
            val order = hashMapOf("vendorId" to restaurant.id, "userId" to uid, "userName" to auth.currentUser?.displayName, "items" to cart.values.map { mapOf("name" to it.name, "qty" to it.quantity, "price" to it.price) }, "totalPrice" to finalTotal, "status" to "Pending", "paymentMethod" to selectedPaymentMethod, "deliveryAddress" to (selectedAddress?.fullAddress ?: "Pickup"), "timestamp" to Date())
            db.collection("orders").add(order).addOnSuccessListener { isSimulatingPayment = false; showPayment = false; onOrderPlaced() }
        }
    }

    if (showPayment) {
        AlertDialog(
            onDismissRequest = { if(!isSimulatingPayment) showPayment = false },
            title = { Text("Checkout") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Subtotal: ₹$subTotal")
                    if (discount > 0) Text("Discount: -₹$discount", color = Color(0xFF4CAF50))
                    Text("Total: ₹$finalTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    if (userAddresses.isEmpty()) Text("Add address in Profile!", color = Color.Red)
                    userAddresses.forEach { addr ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedAddress = addr }) {
                            RadioButton(selected = (selectedAddress == addr), onClick = { selectedAddress = addr })
                            Text(addr.label)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Have a Coupon?", fontWeight = FontWeight.Bold)
                    Row {
                        OutlinedTextField(value = couponCode, onValueChange = { couponCode = it.uppercase() }, modifier = Modifier.weight(1f), placeholder = { Text("Code") })
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            db.collection("coupons").document(couponCode).get().addOnSuccessListener { doc ->
                                if (doc.exists() && doc.getBoolean("active") == true) {
                                    val min = doc.getDouble("minOrderAmount") ?: 0.0; val amt = doc.getDouble("discountAmount") ?: 0.0
                                    if (subTotal >= min) { discount = amt; couponMessage = "Applied!" } else { discount = 0.0; couponMessage = "Min order ₹$min" }
                                } else { discount = 0.0; couponMessage = "Invalid" }
                            }
                        }) { Text("Apply") }
                    }
                    Text(couponMessage, color = if(discount > 0) Color(0xFF4CAF50) else Color.Red, fontSize = 12.sp)
                    LocalPaymentSelector(selectedPaymentMethod) { selectedPaymentMethod = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedPaymentMethod == "Online") isSimulatingPayment = true
                    else {
                        val order = hashMapOf("vendorId" to restaurant.id, "userId" to uid, "userName" to auth.currentUser?.displayName, "items" to cart.values.map { mapOf("name" to it.name, "qty" to it.quantity, "price" to it.price) }, "totalPrice" to finalTotal, "status" to "Pending", "paymentMethod" to "COD", "deliveryAddress" to (selectedAddress?.fullAddress ?: "Pickup"), "timestamp" to Date())
                        db.collection("orders").add(order).addOnSuccessListener { showPayment = false; onOrderPlaced() }
                    }
                }) { Text("Place Order") }
            },
            dismissButton = { TextButton(onClick = { showPayment = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(restaurant.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }) },
        bottomBar = {
            if (cart.isNotEmpty()) {
                Button(onClick = { showPayment = true }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                    Text("View Cart (${cart.values.sumOf { it.quantity }}) - ₹$subTotal", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Menu") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reviews") })
            }
            if (selectedTab == 0) {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    if (menuItems.isEmpty()) {
                        item { Text("Menu is empty", modifier = Modifier.padding(16.dp), color = Color.Gray) }
                    } else {
                        items(menuItems) { item ->
                            ModernMenuItemCard(item = item, quantity = cart[item.name]?.quantity ?: 0, onAdd = { cart[item.name] = CartItem(item.name, item.price, (cart[item.name]?.quantity ?: 0) + 1) }, onRemove = { if ((cart[item.name]?.quantity ?: 0) > 1) cart[item.name] = CartItem(item.name, item.price, cart[item.name]!!.quantity - 1) else cart.remove(item.name) }, onClick = { onItemClick(item) })
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    RatingBreakdown(reviewsList)
                    LazyColumn { items(reviewsList) { r -> ReviewCard(r, restaurant.id) } }
                }
            }
        }
    }
}

// --- SHARED COMPONENTS ---
@Composable
fun EmptyStateScreen(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Search, null, modifier = Modifier.size(80.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, modifier = Modifier.padding(horizontal = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ModernRestaurantCard(restaurant: Restaurant, isFavorite: Boolean, onClick: () -> Unit, onFavClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(220.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Box {
            if (restaurant.imageUrl.isNotEmpty()) AsyncImage(model = restaurant.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(modifier = Modifier.fillMaxSize().background(Color.LightGray))
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            IconButton(onClick = onFavClick, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (isFavorite) Color.Red else Color.Gray)
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(restaurant.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Text(" ${String.format("%.1f", restaurant.rating)}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(" • ${restaurant.distanceKm} km • ₹${restaurant.avgPrice} for two", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ModernMenuItemCard(item: MenuItem, quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    if (item.rating > 0) {
                        Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${item.rating}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17)); Icon(Icons.Filled.Star, null, tint = Color(0xFFF57F17), modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if(item.description.isNotEmpty()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("₹${item.price}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box(contentAlignment = Alignment.BottomCenter) {
                if(item.imageUrl.isNotEmpty()) AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) else Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray))
                if (quantity == 0) { Button(onClick = onAdd, modifier = Modifier.offset(y = 12.dp).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp), contentPadding = PaddingValues(horizontal = 16.dp)) { Text("ADD", fontWeight = FontWeight.Bold) } }
                else { Row(modifier = Modifier.offset(y = 12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).height(32.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp)) }; Text("$quantity", color = Color.White, fontWeight = FontWeight.Bold); IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) } } }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ModernOrderCard(order: Map<String, Any>, onReorder: (String) -> Unit, onRateClick: (Map<String, Any>) -> Unit = {}) {
    val status = order["status"] as? String ?: "Pending"
    val rating = (order["rating"] as? Number)?.toInt() ?: 0
    val isCompleted = status == "Completed" || status == "Rated"
    val statusColor = when(status) { "Pending" -> Color(0xFFFF9800); "Preparing" -> Color(0xFF2196F3); "Ready" -> Color(0xFF4CAF50); "Completed", "Rated" -> Color.Gray; "Rejected" -> Color.Red; else -> Color.Gray }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = statusColor.copy(alpha=0.1f), shape = RoundedCornerShape(50)) { Row(modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape)); Spacer(modifier = Modifier.width(6.dp)); Text(status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
                Text("₹${order["totalPrice"]}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp)); Divider(color = Color.LightGray.copy(alpha=0.3f)); Spacer(modifier = Modifier.height(12.dp))
            (order["items"] as? List<Map<String, Any>>)?.take(3)?.forEach { Text("• ${it["name"]} x ${it["qty"]}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onReorder(order["vendorId"] as String) }) { Text("Reorder", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.width(8.dp))
                if (rating > 0) { Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))) { Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text("Rated $rating", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17)); Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp)) } } }
                else if (isCompleted) { Button(onClick = { onRateClick(order) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black)) { Text("Rate") } }
            }
        }
    }
}

@Composable
fun LocalPaymentSelector(selectedMethod: String, onSelect: (String) -> Unit) {
    Column {
        Text("Payment Method", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSelect("COD") }) { RadioButton(selected = selectedMethod == "COD", onClick = { onSelect("COD") }); Text("Cash on Delivery") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSelect("Online") }) { RadioButton(selected = selectedMethod == "Online", onClick = { onSelect("Online") }); Text("Pay Online (UPI/Card)") }
    }
}

@Composable
fun LocalSimulatedPaymentGateway(amount: Double, onPaymentSuccess: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while (progress < 1f) { delay(50); progress += 0.05f }; delay(500); onPaymentSuccess() }
    AlertDialog(onDismissRequest = {}, title = { Text("Processing Payment") }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { CircularProgressIndicator(progress = { progress }); Spacer(modifier = Modifier.height(16.dp)); Text("Paying ₹$amount...") } }, confirmButton = {})
}*/