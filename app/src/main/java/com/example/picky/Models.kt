package com.example.picky

import java.util.Date

// --- USER & PROFILE MODELS ---
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profileImageUrl: String = "",
    val addresses: List<Address> = emptyList()
)

/*data class Address(
    val id: String,
    val label: String,       // e.g., "Home", "Work"
    val fullAddress: String
)

// --- RESTAURANT & MENU MODELS ---
data class Restaurant(
    val id: String,
    val name: String,
    val rating: Double,
    val ownerName: String,
    val imageUrl: String,
    val distanceKm: Double = 1.5,
    val avgPrice: Int = 200
)

data class MenuItem(
    val id: String,
    val name: String,
    val price: Double,
    val imageUrl: String,
    val description: String = "",      // Feature: Item Description
    val rating: Double = 0.0,          // Feature: Item specific rating
    val ratingCount: Int = 0,          // Feature: Number of people who rated this item
    val restaurantName: String = "",   // Feature: Needed for Trending Screen
    val isAvailable: Boolean = true
)

// --- CART & ORDER MODELS ---
data class CartItem(
    val name: String,
    val price: Double,
    val quantity: Int
)

data class Order(
    val id: String,
    val userId: String,
    val userName: String,
    val vendorId: String,
    val totalPrice: Double,
    val status: String, // Pending, Preparing, Ready, Completed, Rated
    val paymentMethod: String,
    val deliveryAddress: String,
    val timestamp: Date?,
    val items: List<Map<String, Any>>,
    val rating: Int = 0 // Rating given to this specific order
)

data class Coupon(
    val code: String,
    val discountAmount: Double,
    val isActive: Boolean,
    val minOrderAmount: Double = 0.0
)

// --- REVIEW & COUPON MODELS ---
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
    val orderItems: String = "" // NEW FIELD
)
*/
