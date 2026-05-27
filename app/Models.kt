package com.example.foodexplorer.model

import com.google.firebase.firestore.GeoPoint

// 1. The Cart Itself
data class FoodCart(
    val id: String = "",
    val name: String = "",
    val ownerName: String = "",
    val description: String = "",
    val location: GeoPoint? = null, // Stores Latitude/Longitude
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val bestSellers: List<String> = emptyList(), // e.g. ["Spicy Momos", "Masala Chai"]
    val imageUrl: String = ""
)

// 2. The User
data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val favorites: List<String> = emptyList() // List of Cart IDs
)