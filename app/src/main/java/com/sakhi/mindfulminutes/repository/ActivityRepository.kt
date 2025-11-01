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
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.model.ActivityInstance
import kotlinx.coroutines.tasks.await
import java.util.*

class ActivityRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserId(): String = auth.currentUser?.uid ?: throw Exception("User not authenticated")

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

    // Activity Instances Collection
    suspend fun addActivityInstance(instance: ActivityInstance): String {
        val userId = getUserId()
        val docRef = db.collection("users").document(userId)
            .collection("activity_instances").document()
        val newInstance = instance.copy(id = docRef.id)
        docRef.set(newInstance).await()
        return docRef.id
    }

    suspend fun updateActivityInstance(instanceId: String, updates: Map<String, Any>) {
        val userId = getUserId()
        db.collection("users").document(userId)
            .collection("activity_instances").document(instanceId)
            .update(updates).await()
    }

    suspend fun getActivityInstances(activityId: String): List<ActivityInstance> {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activity_instances")
            .whereEqualTo("activityId", activityId)
            .get().await()
        return snapshot.toObjects(ActivityInstance::class.java)
    }

    suspend fun getLastActivityInstance(activityId: String): ActivityInstance? {
        val userId = getUserId()
        val snapshot = db.collection("users").document(userId)
            .collection("activity_instances")
            .whereEqualTo("activityId", activityId)
            .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get().await()
        return snapshot.documents.firstOrNull()?.toObject(ActivityInstance::class.java)
    }

    suspend fun getTotalActivityTime(activityId: String): Long {
        val instances = getActivityInstances(activityId)
        return instances.sumOf { it.duration }
    }

    suspend fun getActivitySessionCount(activityId: String): Int {
        val instances = getActivityInstances(activityId)
        return instances.size
    }

    // Add these methods to your existing ActivityRepository class

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


}