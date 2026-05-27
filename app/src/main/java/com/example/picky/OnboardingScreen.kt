package com.example.picky

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

// ==========================================
// APP ENTRY POINT (Manages Flow & Roles)
// ==========================================
@Composable
fun AppEntryPoint(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }
    var showOnboarding by remember { mutableStateOf(true) }

    // "loading", "user", "vendor", "unknown" (triggers selection)
    var userRole by remember { mutableStateOf("loading") }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

    // 1. Check Role Logic (Strict Check)
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && auth.currentUser != null) {
            val uid = auth.currentUser!!.uid

            // Check the 'users' collection for a specific 'role' field
            db.collection("users").document(uid).get().addOnSuccessListener { document ->
                val role = document.getString("role")

                if (role == "vendor") {
                    userRole = "vendor"
                } else if (role == "user") {
                    userRole = "user"
                } else {
                    // If no 'role' field found in users, check vendors collection as fallback
                    db.collection("vendors").document(uid).get().addOnSuccessListener { vendorDoc ->
                        if (vendorDoc.exists()) {
                            userRole = "vendor"
                        } else {
                            // Document exists but has no role, OR doesn't exist -> Ask User
                            userRole = "unknown"
                        }
                    }
                }
            }.addOnFailureListener {
                userRole = "unknown"
            }
        } else {
            userRole = "loading"
        }
    }

    if (showSplash) {
        SplashScreen(onNext = { showSplash = false })
    } else if (showOnboarding && !isLoggedIn) {
        OnboardingScreen(onFinish = { showOnboarding = false })
    } else if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        // LOGGED IN: Route based on Role
        when (userRole) {
            "loading" -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            "vendor" -> {
                VendorNavigation(onLogout = {
                    auth.signOut()
                    isLoggedIn = false
                    userRole = "loading"
                }, isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)
            }
            "user" -> {
                UserNavigation(onLogout = {
                    auth.signOut()
                    isLoggedIn = false
                    userRole = "loading"
                }, isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)
            }
            "unknown" -> {
                // Show the Selection Screen if role is not found/set
                RoleSelectionScreen(
                    onRoleSelected = { role ->
                        val uid = auth.currentUser?.uid ?: return@RoleSelectionScreen

                        // 1. Update local state immediately to navigate
                        userRole = role

                        // 2. Save choice to DB so we remember next time
                        // We use SetOptions.merge() to avoid overwriting existing data
                        val data = mapOf("role" to role)

                        // Save to 'users' collection (central place for role)
                        db.collection("users").document(uid).set(data, SetOptions.merge())

                        // If vendor, ensure a doc exists in 'vendors' too
                        if (role == "vendor") {
                            db.collection("vendors").document(uid).set(mapOf("exists" to true), SetOptions.merge())
                        }
                    }
                )
            }
        }
    }
}

// ==========================================
// ROLE SELECTION SCREEN
// ==========================================
@Composable
fun RoleSelectionScreen(onRoleSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Who are you?", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Choose your account type to continue", color = Color.Gray)

        Spacer(modifier = Modifier.height(40.dp))

        // USER OPTION
        RoleCard(
            title = "Hungry User",
            subtitle = "I want to order food",
            icon = Icons.Filled.Person,
            color = Color(0xFF4CAF50),
            onClick = { onRoleSelected("user") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // VENDOR OPTION
        RoleCard(
            title = "Food Vendor",
            subtitle = "I want to sell food",
            icon = Icons.Filled.ShoppingCart, // Using standard icon
            color = Color(0xFFFF9800),
            onClick = { onRoleSelected("vendor") }
        )
    }
}

@Composable
fun RoleCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() }
            .border(2.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.Gray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowForward, null, tint = Color.LightGray)
        }
    }
}

// ==========================================
// 1. SPLASH SCREEN ("PICKY")
// ==========================================
@Composable
fun SplashScreen(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6200EE), Color(0xFF3700B3)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PICKY",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose the best.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "Start",
                    tint = Color(0xFF6200EE)
                )
            }
        }
    }
}

// ==========================================
// 2. ONBOARDING SCREEN (With Skip)
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val titles = listOf("Find Food You Love", "Fast Delivery", "Live Tracking")
    val descs = listOf(
        "Discover the best foods from over 1,000 restaurants and fast delivery to your doorstep.",
        "Fast food delivery to your home, office or wherever you are at any time.",
        "Real time tracking of your food on the app once you placed the order."
    )

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // SKIP BUTTON
                TextButton(onClick = onFinish) {
                    Text("Skip", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pager Section
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Circle Placeholder for Image
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = titles[page],
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = descs[page],
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // Dot Indicators
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { iteration ->
                    val color = if (pagerState.currentPage == iteration)
                        MaterialTheme.colorScheme.primary
                    else
                        Color.LightGray

                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                    )
                }
            }

            // Next / Get Started Button
            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage == 2) "Get Started" else "Next",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}