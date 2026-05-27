package com.example.picky

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import java.util.Date

// ==========================================
// 1. MENU ITEM DETAIL SCREEN (Detailed View)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMenuItemDetailScreen(menuItem: MenuItem, vendorId: String, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    var itemReviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    val context = LocalContext.current

    val myExistingReview = itemReviews.find { it.userId == currentUser?.uid }

    var showRatingDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reviewToDeleteId by remember { mutableStateOf("") }

    var currentRating by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }
    var isUploading by remember { mutableStateOf(false) }

    val isNonVeg = menuItem.name.contains("Chicken", ignoreCase = true) || menuItem.name.contains("Egg", ignoreCase = true) || menuItem.name.contains("Mutton", ignoreCase = true)
    val ingredients = if (isNonVeg) "Chicken, Spices, Oil, Onion, Garlic, Ginger" else "Fresh Vegetables, Paneer, Spices, Tomato, Cream"
    val calories = if (isNonVeg) "350 kcal" else "240 kcal"
    val prepTime = "15-20 mins"

    // Fetch Reviews
    LaunchedEffect(menuItem.id) {
        val listener = {
            db.collection("vendors").document(vendorId).collection("menu").document(menuItem.id)
                .collection("itemReviews").orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { s, _ ->
                    if (s != null && !s.isEmpty) {
                        itemReviews = s.documents.map { doc ->
                            Review(
                                id = doc.id,
                                userId = doc.getString("userId")?:"",
                                userName = doc.getString("userName")?:"Anonymous",
                                rating = doc.getLong("rating")?.toInt()?:0,
                                review = doc.getString("review")?:"",
                                imageUrl = doc.getString("imageUrl")?:"",
                                timestamp = doc.getDate("timestamp"),
                                menuItemId = menuItem.id,
                                orderItems = doc.getString("orderItems")?:""
                            )
                        }
                    } else {
                        // Fallback check
                        db.collection("carts").document(vendorId).collection("menu").document(menuItem.id)
                            .collection("itemReviews").orderBy("timestamp", Query.Direction.DESCENDING)
                            .addSnapshotListener { s2, _ ->
                                if (s2 != null) {
                                    itemReviews = s2.documents.map { doc ->
                                        Review(
                                            id = doc.id,
                                            userId = doc.getString("userId")?:"",
                                            userName = doc.getString("userName")?:"Anonymous",
                                            rating = doc.getLong("rating")?.toInt()?:0,
                                            review = doc.getString("review")?:"",
                                            imageUrl = doc.getString("imageUrl")?:"",
                                            timestamp = doc.getDate("timestamp"),
                                            menuItemId = menuItem.id,
                                            orderItems = doc.getString("orderItems")?:""
                                        )
                                    }
                                }
                            }
                    }
                }
        }
        listener()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(menuItem.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (myExistingReview != null) {
                        currentRating = myExistingReview.rating
                        reviewText = myExistingReview.review
                        imageUri = null
                    } else {
                        currentRating = 5
                        reviewText = ""
                        imageUri = null
                    }
                    showRatingDialog = true
                },
                icon = { Icon(if (myExistingReview != null) Icons.Filled.Edit else Icons.Filled.Star, null) },
                text = { Text(if (myExistingReview != null) "Edit Rating" else "Rate Item") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Box {
                    if (menuItem.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = menuItem.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color.LightGray))
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(if(isNonVeg) MaterialTheme.colorScheme.error else Color(0xFF4CAF50), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if(isNonVeg) "Non-Veg" else "Veg",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if(isNonVeg) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = menuItem.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "₹${menuItem.price}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if(!menuItem.isAvailable) {
                        Text("Currently Unavailable", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=4.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (menuItem.description.isNotEmpty()) {
                        Text(menuItem.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DetailStat(Icons.Filled.Star, "${menuItem.rating}", "${menuItem.ratingCount} ratings")
                        DetailStat(Icons.Filled.Info, calories, "Calories")
                        DetailStat(Icons.Filled.Info, prepTime, "Prep Time")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(ingredients, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Ratings & Reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (itemReviews.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No reviews yet. Be the first to try it!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(itemReviews) { review ->
                    ReviewCard(
                        review,
                        vendorId,
                        onEdit = {
                            currentRating = review.rating
                            reviewText = review.review
                            imageUri = null
                            showRatingDialog = true
                        },
                        onDelete = {
                            reviewToDeleteId = review.id
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showRatingDialog) {
        AlertDialog(
            onDismissRequest = { if(!isUploading) showRatingDialog = false },
            title = { Text(if (myExistingReview != null) "Edit Review" else "Rate Item") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isUploading) {
                        CircularProgressIndicator()
                        Text("Submitting...", modifier = Modifier.padding(top=8.dp))
                    } else {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            (1..5).forEach { star ->
                                IconButton(onClick = { currentRating = star }, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = if (star <= currentRating) Color(0xFFFFD700) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (imageUri != null) {
                            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { imageUri = null },
                                    modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha=0.5f), CircleShape).size(20.dp)
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = reviewText,
                            onValueChange = { reviewText = it },
                            label = { Text("Write a review") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { launcher.launch("image/*") }) {
                                    Icon(Icons.Filled.Add, "Upload Image")
                                }
                            },
                            maxLines = 3
                        )
                    }
                }
            },
            confirmButton = {
                if (!isUploading) {
                    Button(onClick = {
                        isUploading = true
                        val reviewData: Map<String, Any> = hashMapOf(
                            "userId" to (auth.currentUser?.uid ?: ""),
                            "userName" to (auth.currentUser?.displayName ?: "Foodie"),
                            "rating" to currentRating,
                            "review" to reviewText,
                            "timestamp" to Date(),
                            "menuItemId" to menuItem.id,
                            "likes" to emptyList<String>(),
                            "dislikes" to emptyList<String>()
                        )
                        uploadItemReviewAndSubmit(context, db, vendorId, menuItem.id, reviewData, imageUri, myExistingReview?.id) {
                            isUploading = false
                            showRatingDialog = false
                            reviewText = ""
                            imageUri = null
                        }
                    }) { Text(if (myExistingReview != null) "Update" else "Submit") }
                }
            },
            dismissButton = { if(!isUploading) TextButton(onClick = { showRatingDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Review?") },
            text = { Text("Are you sure you want to delete this review?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        deleteItemReview(context, db, vendorId, menuItem.id, reviewToDeleteId) { showDeleteConfirm = false }
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DetailStat(icon: ImageVector, title: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==========================================
// 2. RESTAURANT DETAIL SCREEN (Merged Menu)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRestaurantDetailScreen(restaurant: Restaurant, onBack: () -> Unit, onOrderPlaced: () -> Unit, onItemClick: (MenuItem) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var itemsFromVendors by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var itemsFromCarts by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    val menuItems = remember(itemsFromVendors, itemsFromCarts) { (itemsFromVendors + itemsFromCarts).distinctBy { it.id } }

    var filteredMenuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var reviewsList by remember { mutableStateOf<List<Review>>(emptyList()) }
    val cart = remember { mutableStateMapOf<String, CartItem>() }

    var menuSearchQuery by remember { mutableStateOf("") }
    var vegFilter by remember { mutableStateOf("All") }

    var showPayment by remember { mutableStateOf(false) }
    var isSimulatingPayment by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("COD") }
    var userAddresses by remember { mutableStateOf<List<Address>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<Address?>(null) }
    var couponCode by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf(0.0) }
    var couponMessage by remember { mutableStateOf("") }

    LaunchedEffect(restaurant.id) {
        db.collection("vendors").document(restaurant.id).collection("menu").addSnapshotListener { r, _ ->
            if (r != null) itemsFromVendors = r.documents.map {
                MenuItem(it.id, it.getString("name")?:"", it.getDouble("price")?:0.0, it.getString("imageUrl")?:"", it.getString("description")?:"", rating = it.getDouble("rating")?:0.0, ratingCount = it.getLong("ratingCount")?.toInt()?:0, isAvailable = it.getBoolean("isAvailable") ?: true)
            }
        }
        db.collection("carts").document(restaurant.id).collection("menu").addSnapshotListener { r, _ ->
            if (r != null) itemsFromCarts = r.documents.map {
                MenuItem(it.id, it.getString("name")?:"", it.getDouble("price")?:0.0, it.getString("imageUrl")?:"", it.getString("description")?:"", rating = it.getDouble("rating")?:0.0, ratingCount = it.getLong("ratingCount")?.toInt()?:0, isAvailable = it.getBoolean("isAvailable") ?: true)
            }
        }
        db.collection("vendors").document(restaurant.id).collection("reviews").addSnapshotListener { s, _ ->
            if (s != null && !s.isEmpty) {
                reviewsList = s.documents.map {
                    Review(it.id, it.getString("userId")?:"", it.getString("userName")?:"", it.getLong("rating")?.toInt()?:0, it.getString("review")?:"", it.getString("imageUrl")?:"", it.getDate("timestamp"), it.get("likes") as? List<String>?:emptyList(), it.get("dislikes") as? List<String>?:emptyList(), it.get("replies") as? List<Map<String,Any>>?:emptyList(), orderItems = it.getString("orderItems")?:"")
                }
            } else {
                db.collection("carts").document(restaurant.id).collection("reviews").addSnapshotListener { s2, _ ->
                    if (s2 != null) reviewsList = s2.documents.map {
                        Review(it.id, it.getString("userId")?:"", it.getString("userName")?:"", it.getLong("rating")?.toInt()?:0, it.getString("review")?:"", it.getString("imageUrl")?:"", it.getDate("timestamp"), it.get("likes") as? List<String>?:emptyList(), it.get("dislikes") as? List<String>?:emptyList(), it.get("replies") as? List<Map<String,Any>>?:emptyList(), orderItems = it.getString("orderItems")?:"")
                    }
                }
            }
        }
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            userAddresses = (doc.get("addresses") as? List<Map<String, String>>)?.map { Address(it["id"]?:"", it["label"]?:"", it["fullAddress"]?:"") } ?: emptyList()
            selectedAddress = userAddresses.firstOrNull()
        }
    }

    LaunchedEffect(menuSearchQuery, vegFilter, menuItems) {
        filteredMenuItems = menuItems.filter { item ->
            val matchesSearch = item.name.contains(menuSearchQuery, ignoreCase = true)
            val isNonVeg = item.name.contains("Chicken", ignoreCase = true) || item.name.contains("Egg", ignoreCase = true) || item.name.contains("Mutton", ignoreCase = true)
            val matchesFilter = when(vegFilter) { "Veg" -> !isNonVeg; "Non-Veg" -> isNonVeg; else -> true }
            matchesSearch && matchesFilter
        }
    }

    val subTotal = cart.values.sumOf { it.price * it.quantity }
    val finalTotal = (subTotal - discount).coerceAtLeast(0.0)

    if (isSimulatingPayment) {
        LocalSimulatedPaymentGateway(amount = finalTotal) {
            val order = hashMapOf(
                "vendorId" to restaurant.id,
                "vendorName" to restaurant.name,
                "userId" to uid,
                "userName" to auth.currentUser?.displayName,
                "items" to cart.values.map { mapOf("name" to it.name, "qty" to it.quantity, "price" to it.price) },
                "totalPrice" to finalTotal,
                "status" to "Pending",
                "paymentMethod" to selectedPaymentMethod,
                "deliveryAddress" to (selectedAddress?.fullAddress ?: "Pickup"),
                "timestamp" to Date()
            )
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
                    if (userAddresses.isEmpty()) Text("Add address in Profile!", color = MaterialTheme.colorScheme.error)
                    userAddresses.forEach { addr ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedAddress = addr }) {
                            RadioButton(selected = (selectedAddress == addr), onClick = { selectedAddress = addr })
                            Text(addr.label)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp)); Text("Have a Coupon?", fontWeight = FontWeight.Bold)
                    Row {
                        OutlinedTextField(value = couponCode, onValueChange = { couponCode = it.uppercase() }, modifier = Modifier.weight(1f), placeholder = { Text("Code") })
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            db.collection("coupons").document(couponCode).get().addOnSuccessListener { doc ->
                                if (doc.exists() && doc.getBoolean("active") == true) {
                                    val min = doc.getDouble("minOrderAmount") ?: 0.0
                                    val amt = doc.getDouble("discountAmount") ?: 0.0
                                    if (subTotal >= min) { discount = amt; couponMessage = "Applied!" } else { discount = 0.0; couponMessage = "Min order ₹$min" }
                                } else { discount = 0.0; couponMessage = "Invalid" }
                            }
                        }) { Text("Apply") }
                    }
                    Text(couponMessage, color = if(discount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    LocalPaymentSelector(selectedPaymentMethod) { selectedPaymentMethod = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedPaymentMethod == "Online") isSimulatingPayment = true
                    else {
                        val order = hashMapOf(
                            "vendorId" to restaurant.id,
                            "vendorName" to restaurant.name,
                            "userId" to uid,
                            "userName" to auth.currentUser?.displayName,
                            "items" to cart.values.map { mapOf("name" to it.name, "qty" to it.quantity, "price" to it.price) },
                            "totalPrice" to finalTotal,
                            "status" to "Pending",
                            "paymentMethod" to "COD",
                            "deliveryAddress" to (selectedAddress?.fullAddress ?: "Pickup"),
                            "timestamp" to Date()
                        )
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
                Button(
                    onClick = { showPayment = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("View Cart (${cart.values.sumOf { it.quantity }}) - ₹$subTotal", fontWeight = FontWeight.Bold) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Menu") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reviews") })
            }
            if (selectedTab == 0) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = menuSearchQuery,
                            onValueChange = { menuSearchQuery = it },
                            placeholder = { Text("Search items...") },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            leadingIcon = { Icon(Icons.Filled.Search, null) }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Veg", "Non-Veg").forEach { type ->
                            FilterChip(
                                selected = vegFilter == type,
                                onClick = { vegFilter = type },
                                label = { Text(type) },
                                leadingIcon = if(type != "All") { { Box(modifier = Modifier.size(12.dp).background(if(type == "Veg") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, CircleShape)) } } else null
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        if (filteredMenuItems.isEmpty()) {
                            item { Text("No items found", modifier = Modifier.padding(16.dp), color = Color.Gray) }
                        } else {
                            items(filteredMenuItems) { item ->
                                ModernMenuItemCard(
                                    item = item,
                                    quantity = cart[item.id]?.quantity ?: 0,
                                    onAdd = { cart[item.id] = CartItem(item.name, item.price, (cart[item.id]?.quantity ?: 0) + 1) },
                                    onRemove = { if ((cart[item.id]?.quantity ?: 0) > 1) cart[item.id] = CartItem(item.name, item.price, cart[item.id]!!.quantity - 1) else cart.remove(item.id) },
                                    onClick = { onItemClick(item) }
                                )
                            }
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

@Composable
fun ModernMenuItemCard(item: MenuItem, quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit, onClick: () -> Unit) {
    val isNonVeg = item.name.contains("Chicken", ignoreCase = true) || item.name.contains("Egg", ignoreCase = true) || item.name.contains("Mutton", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(if(isNonVeg) MaterialTheme.colorScheme.error else Color(0xFF4CAF50), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    if (item.rating > 0) {
                        Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${item.rating}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFF57F17), modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if(item.description.isNotEmpty()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("₹${item.price}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box(contentAlignment = Alignment.BottomCenter) {
                if(item.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray))
                }

                if(item.isAvailable) {
                    if (quantity == 0) {
                        Button(
                            onClick = onAdd,
                            modifier = Modifier.offset(y = 12.dp).height(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) { Text("ADD", fontWeight = FontWeight.Bold) }
                    } else {
                        Row(
                            modifier = Modifier.offset(y = 12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).height(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                            Text("$quantity", color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.offset(y = 12.dp),
                        color = Color.Gray,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("SOLD OUT", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
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
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Processing Payment") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(progress = { progress })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Paying ₹$amount...")
            }
        },
        confirmButton = {}
    )
}