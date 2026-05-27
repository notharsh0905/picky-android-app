package com.example.picky

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.util.Date
import java.util.UUID

// --- DATA MODEL FOR REVIEWS ---
data class Review(
    val id: String,
    val userId: String,
    val userName: String,
    val rating: Int,
    val review: String,
    val imageUrl: String = "",
    val timestamp: Date? = null,
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val replies: List<Map<String, Any>> = emptyList(),
    val menuItemId: String? = null,
    val orderItems: String = "" // NEW: Stores "2x Pizza, 1x Coke"
)

// ==========================================
// 1. HELPER: HANDLE LIKE / DISLIKE
// ==========================================
fun updateReviewReaction(
    db: FirebaseFirestore,
    vendorId: String,
    review: Review,
    reaction: String, // "like" or "dislike"
    currentUserId: String
) {
    val docRef = if (!review.menuItemId.isNullOrEmpty()) {
        db.collection("vendors").document(vendorId).collection("menu").document(review.menuItemId).collection("itemReviews").document(review.id)
    } else {
        db.collection("vendors").document(vendorId).collection("reviews").document(review.id)
    }

    db.runTransaction { transaction ->
        val snapshot = transaction.get(docRef)
        if (snapshot.exists()) {
            val likes = snapshot.get("likes") as? MutableList<String> ?: mutableListOf()
            val dislikes = snapshot.get("dislikes") as? MutableList<String> ?: mutableListOf()

            if (reaction == "like") {
                if (likes.contains(currentUserId)) likes.remove(currentUserId) else { likes.add(currentUserId); dislikes.remove(currentUserId) }
            } else if (reaction == "dislike") {
                if (dislikes.contains(currentUserId)) dislikes.remove(currentUserId) else { dislikes.add(currentUserId); likes.remove(currentUserId) }
            }
            transaction.update(docRef, "likes", likes, "dislikes", dislikes)
        }
    }
}

// ==========================================
// 2. HELPER: UPLOAD GENERAL REVIEW
// ==========================================
fun uploadImageAndSubmit(
    context: Context,
    db: FirebaseFirestore,
    vendorId: String,
    reviewData: Map<String, Any>,
    imageUri: Uri?,
    parentReviewId: String? = null,
    onComplete: () -> Unit
) {
    val storageRef = FirebaseStorage.getInstance().reference.child("reviews/${UUID.randomUUID()}.jpg")

    fun saveToFirestore(finalData: Map<String, Any>) {
        if (parentReviewId == null) {
            db.collection("vendors").document(vendorId).collection("reviews").add(finalData)
                .addOnSuccessListener {
                    val orderId = finalData["orderId"] as? String
                    if (orderId != null) {
                        db.collection("orders").document(orderId).update("rating", finalData["rating"], "status", "Rated")
                    }
                    Toast.makeText(context, "Review Added!", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
                .addOnFailureListener {
                    db.collection("carts").document(vendorId).collection("reviews").add(finalData)
                        .addOnSuccessListener { Toast.makeText(context, "Review Added!", Toast.LENGTH_SHORT).show(); onComplete() }
                }
        } else {
            db.collection("vendors").document(vendorId).collection("reviews").document(parentReviewId)
                .update("replies", FieldValue.arrayUnion(finalData))
                .addOnSuccessListener { Toast.makeText(context, "Reply Posted!", Toast.LENGTH_SHORT).show(); onComplete() }
        }
    }

    if (imageUri != null) {
        storageRef.putFile(imageUri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                val mutableData = reviewData.toMutableMap()
                mutableData["imageUrl"] = uri.toString()
                saveToFirestore(mutableData)
            }
        }
    } else {
        val mutableData = reviewData.toMutableMap()
        mutableData["imageUrl"] = ""
        saveToFirestore(mutableData)
    }
}

// ==========================================
// 3. HELPER: UPLOAD MENU ITEM REVIEW
// ==========================================
fun uploadItemReviewAndSubmit(
    context: Context,
    db: FirebaseFirestore,
    vendorId: String,
    menuItemId: String,
    reviewData: Map<String, Any>,
    imageUri: Uri?,
    existingReviewId: String? = null,
    onComplete: () -> Unit
) {
    val storageRef = FirebaseStorage.getInstance().reference.child("item_reviews/${UUID.randomUUID()}.jpg")

    fun performSafeUpdate(finalData: Map<String, Any>) {
        val vendorItemRef = db.collection("vendors").document(vendorId).collection("menu").document(menuItemId)

        vendorItemRef.get().addOnSuccessListener { snapshot ->
            val finalItemRef = if (snapshot.exists()) vendorItemRef else db.collection("carts").document(vendorId).collection("menu").document(menuItemId)
            val reviewsRef = finalItemRef.collection("itemReviews")

            val saveTask = if (existingReviewId != null) reviewsRef.document(existingReviewId).set(finalData, SetOptions.merge()) else reviewsRef.add(finalData)

            saveTask.addOnSuccessListener {
                reviewsRef.get().addOnSuccessListener { allReviews ->
                    if (!allReviews.isEmpty) {
                        val ratings = allReviews.documents.mapNotNull { it.getDouble("rating") }
                        val newCount = ratings.size
                        val newAvg = if (newCount > 0) ratings.average() else 0.0
                        finalItemRef.update(mapOf("rating" to newAvg, "ratingCount" to newCount))
                    }
                    Toast.makeText(context, if(existingReviewId!=null) "Updated!" else "Submitted!", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }
        }
    }

    if (imageUri != null) {
        storageRef.putFile(imageUri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                val mutableData = reviewData.toMutableMap()
                mutableData["imageUrl"] = uri.toString()
                performSafeUpdate(mutableData)
            }
        }
    } else {
        val mutableData = reviewData.toMutableMap()
        if (existingReviewId == null) mutableData["imageUrl"] = ""
        performSafeUpdate(mutableData)
    }
}

// ==========================================
// 4. HELPER: DELETE REVIEW
// ==========================================
fun deleteItemReview(
    context: Context,
    db: FirebaseFirestore,
    vendorId: String,
    menuItemId: String,
    reviewId: String,
    onComplete: () -> Unit
) {
    val vendorItemRef = db.collection("vendors").document(vendorId).collection("menu").document(menuItemId)

    vendorItemRef.get().addOnSuccessListener { snapshot ->
        val finalItemRef = if (snapshot.exists()) vendorItemRef else db.collection("carts").document(vendorId).collection("menu").document(menuItemId)
        val reviewsRef = finalItemRef.collection("itemReviews")

        reviewsRef.document(reviewId).delete().addOnSuccessListener {
            reviewsRef.get().addOnSuccessListener { allReviews ->
                val ratings = allReviews.documents.mapNotNull { it.getDouble("rating") }
                val newCount = ratings.size
                val newAvg = if (newCount > 0) ratings.average() else 0.0
                finalItemRef.update(mapOf("rating" to newAvg, "ratingCount" to newCount)).addOnSuccessListener {
                    Toast.makeText(context, "Review Deleted", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }
        }
    }
}

// ==========================================
// 5. UI COMPONENTS
// ==========================================

@Composable
fun RatingBreakdown(reviews: List<Review>) {
    if (reviews.isEmpty()) return
    val total = reviews.size
    val avg = if (total > 0) reviews.map { it.rating }.average() else 0.0

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 16.dp)) {
                Text(text = String.format("%.1f", avg), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("$total ratings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f)) {
                (5 downTo 1).forEach { star ->
                    val count = reviews.count { it.rating == star }
                    val progress = if (total > 0) count.toFloat() / total else 0f
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(18.dp)) {
                        Text("$star", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(12.dp), color = MaterialTheme.colorScheme.onSurface)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)), color = if(star >= 4) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewCard(
    review: Review,
    vendorId: String,
    isVendorView: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val currentUserId = auth.currentUser?.uid ?: ""
    val userName = auth.currentUser?.displayName ?: "User"

    var showReplyDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val isMyReview = review.userId == currentUserId

    val isLiked = review.likes.contains(currentUserId)
    val isDisliked = review.dislikes.contains(currentUserId)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(review.userName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(review.userName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(review.rating) { Icon(Icons.Filled.Star, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFD700)) }
                        if (review.orderItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("• ${review.orderItems}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isMyReview) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(review.review, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            if (review.imageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(model = review.imageUrl, contentDescription = null, modifier = Modifier.height(150.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { updateReviewReaction(db, vendorId, review, "like", currentUserId) }) {
                    Icon(imageVector = if(isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, contentDescription = "Like", tint = if(isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Text("${review.likes.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { updateReviewReaction(db, vendorId, review, "dislike", currentUserId) }) {
                    Icon(imageVector = if(isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, contentDescription = "Dislike", tint = if(isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Text("${review.dislikes.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = { showReplyDialog = true }) {
                    Icon(Icons.Filled.Reply, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reply", fontSize = 12.sp)
                }
            }

            if (review.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    review.replies.forEach { replyData ->
                        val rName = replyData["userName"] as? String ?: "User"
                        val rText = replyData["text"] as? String ?: ""
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("| ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column {
                                Text(rName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(rText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReplyDialog) {
        AlertDialog(
            onDismissRequest = { showReplyDialog = false },
            title = { Text("Reply") },
            text = { OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Write a reply...") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    if (replyText.isNotEmpty()) {
                        val replyData = mapOf("userId" to currentUserId, "userName" to userName, "text" to replyText, "timestamp" to Date())
                        uploadImageAndSubmit(context, db, vendorId, replyData, null, parentReviewId = review.id) { showReplyDialog = false; replyText = "" }
                    }
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showReplyDialog = false }) { Text("Cancel") } }
        )
    }
}