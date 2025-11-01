package com.sakhi.mindfulminutes.model

data class Activity(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val color: Int = 0,
    val icon: Int = 0,
    val status: String = "active", // "active" or "inactive"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)