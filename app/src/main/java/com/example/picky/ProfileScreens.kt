/*package com.example.picky

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

@Composable
fun UserProfileScreen(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    var userProfile by remember { mutableStateOf(UserProfile()) }
    var isEditing by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // Address State
    var showAddressDialog by remember { mutableStateOf(false) }
    var newAddressLabel by remember { mutableStateOf("Home") }
    var newAddressText by remember { mutableStateOf("") }

    // Image Upload
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val ref = FirebaseStorage.getInstance().reference.child("profile_images/$uid.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { dlUrl ->
                    db.collection("users").document(uid).update("profileImageUrl", dlUrl.toString())
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        db.collection("users").document(uid).addSnapshotListener { s, _ ->
            if (s != null && s.exists()) {
                val addrs = (s.get("addresses") as? List<Map<String, String>>)?.map {
                    Address(it["id"]?:"", it["label"]?:"", it["fullAddress"]?:"")
                } ?: emptyList()

                userProfile = UserProfile(
                    uid = uid,
                    name = s.getString("name") ?: auth.currentUser?.displayName ?: "User",
                    email = auth.currentUser?.email ?: "",
                    profileImageUrl = s.getString("profileImageUrl") ?: "",
                    addresses = addrs
                )
                newName = userProfile.name
            }
        }
    }

    // Address Dialog
    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("New Location") },
            text = {
                Column {
                    OutlinedTextField(value = newAddressLabel, onValueChange = { newAddressLabel = it }, label = { Text("Label (e.g. Home)") }, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newAddressText, onValueChange = { newAddressText = it }, label = { Text("Full Address") }, shape = RoundedCornerShape(12.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newAddressText.isNotEmpty()) {
                        val newAddr = mapOf("id" to UUID.randomUUID().toString(), "label" to newAddressLabel, "fullAddress" to newAddressText)
                        db.collection("users").document(uid).set(mapOf("exists" to true), com.google.firebase.firestore.SetOptions.merge())
                        db.collection("users").document(uid).update("addresses", FieldValue.arrayUnion(newAddr))
                        showAddressDialog = false
                    }
                }) { Text("Save Address") }
            },
            dismissButton = { TextButton(onClick = { showAddressDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- 1. MODERN HEADER WITH GRADIENT ---
        Box(modifier = Modifier.height(240.dp).fillMaxWidth()) {
            // Background Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                        ),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            )

            // Profile Image (Floating)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White) // Border effect
                    .padding(4.dp) // Thickness of border
                    .clip(CircleShape)
                    .clickable { launcher.launch("image/*") }
            ) {
                if (userProfile.profileImageUrl.isNotEmpty()) {
                    AsyncImage(model = userProfile.profileImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.fillMaxSize(), tint = Color.LightGray)
                }
                // Edit Icon Badge
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.primary, CircleShape).padding(6.dp)
                ) {
                    Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // --- 2. USER INFO ---
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, modifier = Modifier.width(200.dp))
                    IconButton(onClick = { db.collection("users").document(uid).update("name", newName); isEditing = false }) {
                        Icon(Icons.Filled.Check, null, tint = Color.Green)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(userProfile.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp), tint = Color.Gray) }
                }
            }
            Text(userProfile.email, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. ADDRESS LIST (Modern Cards) ---
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Saved Addresses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddressDialog = true }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Text("Add New")
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(userProfile.addresses) { addr ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.background(Color.White, CircleShape).padding(8.dp)) {
                                Icon(Icons.Filled.Home, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(addr.label, fontWeight = FontWeight.Bold)
                                Text(addr.fullAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                val addrMap = mapOf("id" to addr.id, "label" to addr.label, "fullAddress" to addr.fullAddress)
                                db.collection("users").document(uid).update("addresses", FieldValue.arrayRemove(addrMap))
                            }) { Icon(Icons.Filled.Delete, null, tint = Color.Red.copy(alpha=0.7f)) }
                        }
                    }
                }
            }

            // Log Out Button
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color.Red)
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold)
            }
        }
    }
}*/