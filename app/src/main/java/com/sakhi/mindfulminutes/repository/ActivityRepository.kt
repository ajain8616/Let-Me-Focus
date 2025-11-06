/**
 * Author: Arihant Jain
 * Date: 01-11-2025
 * Time: 23:47
 * Year: 2025
 * Month: November (Nov)
 * Day: 01 (Saturday)
 * Hour: 23
 * Minute: 47
 * Project: Let Me Focus
 * Package: com.sakhi.mindfulminutes.repository
 */
package com.sakhi.mindfulminutes.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.models.ActivityInstance
import kotlinx.coroutines.tasks.await
import java.util.*

class ActivityRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserId(): String = auth.currentUser?.uid ?: throw Exception("User not authenticated")

    // Add this method to update activity status
    suspend fun updateActivityStatus(activityId: String, status: String) {
        val userId = getUserId()
        db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .update("status", status).await()
    }

    // Activities Collection
    suspend fun addActivity(activity: Activity): String {
        val userId = getUserId()
        val docRef = db.collection("users").document(userId)
            .collection("activities").document()
        val newActivity = activity.copy(id = docRef.id)
        docRef.set(newActivity).await()
        return docRef.id
    }

    suspend fun updateActivity(activityId: String, updates: Map<String, Any>) {
        val userId = getUserId()
        db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .update(updates).await()
    }

    suspend fun deleteActivity(activityId: String) {
        val userId = getUserId()
        db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .update("status", "inactive").await()
    }

    suspend fun getActiveActivities(): List<Activity> {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activities")
            .whereEqualTo("status", "active")
            .get().await()
        return snapshot.toObjects(Activity::class.java)
    }

    fun listenToActiveActivities(onUpdate: (List<Activity>) -> Unit): ListenerRegistration {
        val userId = getUserId()
        return db.collection("users").document(userId)
            .collection("activities")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                error?.let {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val activities = snapshot?.toObjects(Activity::class.java) ?: emptyList()
                onUpdate(activities)
            }
    }

    // Activity Instances Collection - Following your Firebase structure
    suspend fun addActivityInstance(
        activityId: String,
        totalSpentTime: Long,
        startTime: String,
        stopTime: String
    ): String {
        val userId = getUserId()

        // Get the next instance ID based on existing instances count
        val instancesSnapshot = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances")
            .get().await()

        val instanceId = instancesSnapshot.size().toString()

        val instanceData = mapOf(
            "totalSpentTime" to totalSpentTime,
            "startTime" to startTime,
            "stopTime" to stopTime,
            "createdAt" to System.currentTimeMillis()
        )

        // Store the instance with numerical ID
        db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances").document(instanceId)
            .set(instanceData).await()

        return instanceId
    }

    suspend fun addActivityInstanceWithObject(instance: ActivityInstance): String {
        val userId = getUserId()

        // Get the next instance ID based on existing instances count
        val instancesSnapshot = db.collection("users").document(userId)
            .collection("activities").document(instance.activityId)
            .collection("instances")
            .get().await()

        val instanceId = instancesSnapshot.size().toString()

        val instanceData = mapOf(
            "totalSpentTime" to instance.duration,
            "startTime" to instance.startTime,
            "stopTime" to instance.stopTime,
            "createdAt" to System.currentTimeMillis()
        )

        // Store the instance with numerical ID
        db.collection("users").document(userId)
            .collection("activities").document(instance.activityId)
            .collection("instances").document(instanceId)
            .set(instanceData).await()

        return instanceId
    }

    suspend fun updateActivityInstance(activityId: String, instanceId: String, updates: Map<String, Any>) {
        val userId = getUserId()
        db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances").document(instanceId)
            .update(updates).await()
    }

    suspend fun getActivityInstances(activityId: String): List<ActivityInstance> {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()

        return snapshot.documents.mapNotNull { document ->
            try {
                val totalSpentTime = document.getLong("totalSpentTime") ?: 0L
                val startTime = document.getString("startTime") ?: ""
                val stopTime = document.getString("stopTime") ?: ""

                ActivityInstance(
                    id = document.id,
                    activityId = activityId,
                    duration = totalSpentTime,
                    startTime = startTime,
                    stopTime = stopTime
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getLastActivityInstance(activityId: String): ActivityInstance? {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get().await()

        return snapshot.documents.firstOrNull()?.let { document ->
            try {
                val totalSpentTime = document.getLong("totalSpentTime") ?: 0L
                val startTime = document.getString("startTime") ?: ""
                val stopTime = document.getString("stopTime") ?: ""

                ActivityInstance(
                    id = document.id,
                    activityId = activityId,
                    duration = totalSpentTime,
                    startTime = startTime,
                    stopTime = stopTime
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getTotalActivityTime(activityId: String): Long {
        val instances = getActivityInstances(activityId)
        return instances.sumOf { it.duration }
    }

    suspend fun getActivitySessionCount(activityId: String): Int {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances")
            .get().await()
        return snapshot.size()
    }

    suspend fun getAllActivities(): List<Activity> {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activities")
            .get().await()

        return snapshot.documents.mapNotNull { document ->
            try {
                document.toObject(Activity::class.java)?.copy(id = document.id)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name }
    }

    suspend fun getActivityWithStats(activityId: String): Activity? {
        val userId = getUserId()
        val document = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .get().await()

        return if (document.exists()) {
            document.toObject(Activity::class.java)?.copy(id = document.id)
        } else {
            null
        }
    }

    fun listenToAllActivities(onUpdate: (List<Activity>) -> Unit): ListenerRegistration {
        val userId = getUserId()
        return db.collection("users").document(userId)
            .collection("activities")
            .addSnapshotListener { snapshot, error ->
                error?.let {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val activities = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(Activity::class.java)?.copy(id = document.id)
                } ?: emptyList()
                onUpdate(activities.sortedBy { it.name })
            }
    }

    // Additional methods for better instance management
    suspend fun deleteActivityInstance(activityId: String, instanceId: String) {
        val userId = getUserId()
        db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances").document(instanceId)
            .delete().await()
    }

    suspend fun getActivityInstance(activityId: String, instanceId: String): ActivityInstance? {
        val userId = getUserId()
        val document = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances").document(instanceId)
            .get().await()

        return if (document.exists()) {
            try {
                val totalSpentTime = document.getLong("totalSpentTime") ?: 0L
                val startTime = document.getString("startTime") ?: ""
                val stopTime = document.getString("stopTime") ?: ""

                ActivityInstance(
                    id = document.id,
                    activityId = activityId,
                    duration = totalSpentTime,
                    startTime = startTime,
                    stopTime = stopTime
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Method to get instances within a date range
    suspend fun getActivityInstancesInRange(activityId: String, startDate: Long, endDate: Long): List<ActivityInstance> {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances")
            .whereGreaterThanOrEqualTo("createdAt", startDate)
            .whereLessThanOrEqualTo("createdAt", endDate)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()

        return snapshot.documents.mapNotNull { document ->
            try {
                val totalSpentTime = document.getLong("totalSpentTime") ?: 0L
                val startTime = document.getString("startTime") ?: ""
                val stopTime = document.getString("stopTime") ?: ""

                ActivityInstance(
                    id = document.id,
                    activityId = activityId,
                    duration = totalSpentTime,
                    startTime = startTime,
                    stopTime = stopTime
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // Method to reset all instances for an activity
    suspend fun resetActivityInstances(activityId: String) {
        val userId = getUserId()
        val instancesSnapshot = db.collection("users").document(userId)
            .collection("activities").document(activityId)
            .collection("instances")
            .get().await()

        val batch = db.batch()
        instancesSnapshot.documents.forEach { document ->
            batch.delete(document.reference)
        }
        batch.commit().await()
    }

    // Method to get total time spent on all activities
    suspend fun getTotalTimeAllActivities(): Long {
        val userId = getUserId()
        val activities = getAllActivities()
        var totalTime = 0L

        activities.forEach { activity ->
            totalTime += getTotalActivityTime(activity.id)
        }

        return totalTime
    }

    // Method to get today's instances for an activity
    suspend fun getTodayActivityInstances(activityId: String): List<ActivityInstance> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1

        return getActivityInstancesInRange(activityId, startOfDay, endOfDay)
    }
}