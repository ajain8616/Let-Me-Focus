package com.sakhi.mindfulminutes.models

import com.sakhi.mindfulminutes.model.Activity

/**
 * Author: Arihant Jain
 * Date: 02-11-2025
 * Time: 03:21
 * Year: 2025
 * Month: November (Nov)
 * Day: 02 (Sunday)
 * Hour: 03
 * Minute: 21
 * Project: Let Me Focus
 * Package: com.sakhi.mindfulminutes.models
 */
data class ChartData(
    val activityName: String,
    val totalTime: Long,
    val sessionCount: Int,
    val lastInstance: ActivityInstance? = null,
    val todayTime: Long = 0,
    val allInstances: List<ActivityInstance> = emptyList(),
    val activityWithStats: Activity? = null
)