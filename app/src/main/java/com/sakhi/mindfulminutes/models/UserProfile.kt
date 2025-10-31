package com.sakhi.mindfulminutes.models

/**
 * Author: Arihant Jain
 * Date: 01-11-2025
 * Time: 02:43
 * Year: 2025
 * Month: November (Nov)
 * Day: 01 (Saturday)
 * Hour: 02
 * Minute: 43
 * Project: Let Me Focus
 * Package: com.sakhi.mindfulminutes.models
 */
data class UserProfile(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val createdAt: Long = 0L,
    val isVerified: Boolean = false,
    val lastLoginAt: Long = 0L,
    val accountStatus: String = "active",
    val profileCompleted: Boolean = false,
    val profileImageUrl: String = "" // Add this field if you want to store profile images
) {
    // Default constructor for Firebase
    constructor() : this("", "", "", 0L, false, 0L, "active", false, "")
}
