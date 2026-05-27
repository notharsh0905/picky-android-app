package com.example.picky

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuItemDetailScreen(
    menuItem: MenuItem,
    vendorId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }

    var rating by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri }

    // Load reviews
    LaunchedEffect(menuItem.id) {
        db.collection("carts")
            .document(vendorId)
            .collection("menu")
            .document(menuItem.id)
            .collection("itemReviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    reviews = snap.documents.map { d ->
                        Review(
                            id = d.id,
                            userId = d.getString("userId") ?: "",
                            userName = d.getString("userName") ?: "User",
                            rating = d.getLong("rating")?.toInt() ?: 0,
                            review = d.getString("review") ?: "",
                            imageUrl = d.getString("imageUrl") ?: "",
                            timestamp = d.getDate("timestamp"),
                            menuItemId = menuItem.id
                        )
                    }
                }
            }
    }

    // REVIEW DIALOG
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!uploading) showDialog = false },
            title = { Text("Review ${menuItem.name}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        repeat(5) { i ->
                            IconButton(onClick = { rating = i + 1 }) {
                                Icon(
                                    Icons.Filled.Star,
                                    null,
                                    tint = if (i < rating) Color(0xFFFFD700) else Color.LightGray
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Your review") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Photo")
                        }

                        if (imageUri != null) {
                            Spacer(Modifier.width(8.dp))
                            AsyncImage(
                                model = imageUri,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(onClick = { imageUri = null }) {
                                Icon(Icons.Filled.Close, null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !uploading,
                    onClick = {
                        uploading = true

                        val data = mapOf(
                            "userId" to (auth.currentUser?.uid ?: ""),
                            "userName" to (auth.currentUser?.displayName ?: "User"),
                            "rating" to rating,
                            "review" to reviewText,
                            "timestamp" to Date(),
                            "menuItemId" to menuItem.id
                        )

                        // ✅ CORRECT FUNCTION CALL
                        uploadItemReviewAndSubmit(
                            context = context,
                            db = db,
                            vendorId = vendorId,
                            menuItemId = menuItem.id,
                            reviewData = data,
                            imageUri = imageUri
                        ) {
                            uploading = false
                            showDialog = false
                            reviewText = ""
                            imageUri = null
                            rating = 5
                        }
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(menuItem.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Star, null)
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            item {
                if (menuItem.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = menuItem.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color.LightGray)
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(menuItem.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("₹${menuItem.price}", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    if (menuItem.description.isNotEmpty()) {
                        Text(menuItem.description, color = Color.Gray)
                    }
                }
            }

            item {
                Text(
                    "Reviews",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            if (reviews.isEmpty()) {
                item {
                    Text(
                        "No reviews yet",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Gray
                    )
                }
            } else {
                items(reviews) {
                    ReviewCard(it, vendorId)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
