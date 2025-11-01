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
    val lastLoginAt: Long = 0L,
    val profileImageUrl: String = ""
) {
    // Default constructor for Firebase (no-arg constructor)
    constructor() : this("", "", "", 0L, 0L, "")
}
