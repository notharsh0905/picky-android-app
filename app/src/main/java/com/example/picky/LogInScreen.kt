package com.example.picky

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // UI State
    var name by remember { mutableStateOf("") } // New Field
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Toggles
    var isVendorLogin by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) } // New Toggle (Login vs Signup)

    // --- GOOGLE & FACEBOOK SETUP (Kept same as before) ---
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // Replace with your actual string from google-services.json if needed
            .requestIdToken("200441658918-bgtk7jjgkbnqc32qlrdrvv7ngqj5ovls.apps.googleusercontent.com")
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                isLoading = true
                auth.signInWithCredential(credential).addOnSuccessListener {
                    checkAndSaveRole(auth.currentUser!!.uid, isVendorLogin, db) { isLoading = false; onLoginSuccess() }
                }
            } catch (e: Exception) { isLoading = false }
        }
    }

    val callbackManager = remember { CallbackManager.Factory.create() }
    val fbLoginManager = LoginManager.getInstance()
    DisposableEffect(Unit) {
        val callback = object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                isLoading = true
                auth.signInWithCredential(credential).addOnSuccessListener {
                    checkAndSaveRole(auth.currentUser!!.uid, isVendorLogin, db) { isLoading = false; onLoginSuccess() }
                }
            }
            override fun onCancel() { isLoading = false }
            override fun onError(error: FacebookException) { isLoading = false }
        }
        fbLoginManager.registerCallback(callbackManager, callback)
        onDispose { fbLoginManager.unregisterCallback(callbackManager) }
    }

    // --- UI LAYOUT ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dynamic Title
        val titleText = when {
            isVendorLogin && isSignUpMode -> "Partner Sign Up"
            isVendorLogin -> "Partner Login"
            isSignUpMode -> "Create Account"
            else -> "Welcome Back"
        }

        Text(text = titleText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        // Name Field (Only visible when Signing Up)
        if (isSignUpMode) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            // MAIN ACTION BUTTON
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        if (isSignUpMode) {
                            // --- SIGN UP LOGIC ---
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { result ->
                                    // Update Display Name
                                    val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                                    result.user?.updateProfile(profileUpdates)

                                    checkAndSaveRole(result.user!!.uid, isVendorLogin, db) {
                                        isLoading = false
                                        onLoginSuccess()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(context, "Sign Up Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            // --- LOGIN LOGIC ---
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener { result ->
                                    checkAndSaveRole(result.user!!.uid, isVendorLogin, db) {
                                        isLoading = false
                                        onLoginSuccess()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(context, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSignUpMode) "Sign Up" else "Log In")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TOGGLE LOGIN / SIGNUP ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isSignUpMode) "Already have an account? " else "Don't have an account? ")
                Text(
                    text = if (isSignUpMode) "Log In" else "Sign Up",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { isSignUpMode = !isSignUpMode }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // GOOGLE
            OutlinedButton(
                onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("Continue with Google") }

            Spacer(modifier = Modifier.height(12.dp))

            // FACEBOOK
            Button(
                onClick = { fbLoginManager.logInWithReadPermissions(context as Activity, listOf("email", "public_profile")) },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
            ) { Text("Continue with Facebook", color = Color.White) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- PARTNER LINK ---
        Text(
            text = if (isVendorLogin) "Not a Vendor? Login as User" else "Own a Cart? Partner with us",
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { isVendorLogin = !isVendorLogin }
        )
    }
}

// Helper (Unchanged)
fun checkAndSaveRole(userId: String, isVendor: Boolean, db: FirebaseFirestore, onDone: () -> Unit) {
    val userRef = db.collection("users").document(userId)
    userRef.get().addOnSuccessListener { document ->
        if (document.exists() && document.contains("role")) {
            onDone()
        } else {
            val role = if (isVendor) "Vendor" else "User"
            userRef.set(hashMapOf("role" to role))
                .addOnSuccessListener { onDone() }
        }
    }
}