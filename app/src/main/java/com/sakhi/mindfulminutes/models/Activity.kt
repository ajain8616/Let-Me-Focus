package com.sakhi.mindfulminutes.model

import java.util.Date

data class Activity(
    val id: String = "",
    val name: String = "",
    val status: String = "active",
    val creationTime: Date = Date(),
    val totalTime: Long = 0, // in seconds
    val sessionCount: Int = 0
)

data class ActivityInstance(
    val id: String = "",
    val activityId: String = "",
    val startTime: Date = Date(),
    val endTime: Date? = null,
    val duration: Long = 0, // in seconds
    val status: String = "completed"
)