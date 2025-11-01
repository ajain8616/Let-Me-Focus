package com.sakhi.mindfulminutes.models

/**
 * Author: Arihant Jain
 * Date: 02-11-2025
 * Time: 02:55
 * Year: 2025
 * Month: November (Nov)
 * Day: 02 (Sunday)
 * Hour: 02
 * Minute: 55
 * Project: Let Me Focus
 * Package: com.sakhi.mindfulminutes.models
 */
data class ActivityInstance(
    val id: String = "",
    val activityId: String = "",
    val duration: Long = 0L, // in seconds
    val startTime: String = "", // formatted time string
    val stopTime: String = "" // formatted time string
)