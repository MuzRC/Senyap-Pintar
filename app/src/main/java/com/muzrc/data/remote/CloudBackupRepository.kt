package com.muzrc.data.remote

import android.content.Context
import com.muzrc.R
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.CloudAppSoundProfile
import com.muzrc.model.CloudQuietSchedule
import com.muzrc.model.CloudUserPreferences
import com.muzrc.model.ProfileMode
import com.muzrc.model.QuietSchedule
import com.muzrc.model.ThemeMode
import com.muzrc.model.UserPreferences
import com.muzrc.model.sanitizeFirestoreDocId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudBackupSnapshot(
    val preferences: UserPreferences?,
    val profiles: List<AppSoundProfile>,
    val schedules: List<QuietSchedule>
)

class CloudBackupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna perlu log masuk dengan Google untuk sandaran awan.")
        check(uid == expectedUserId) { "Sesi pengguna telah bertukar." }
        return uid
    }

    fun observeCloudPreferences(userId: String): Flow<CloudUserPreferences?> {
        val path = "users/$userId/settings/preferences"
        return db.collection("users").document(userId)
            .collection("settings").document("preferences")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    CloudUserPreferences::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeCloudProfiles(userId: String): Flow<List<AppSoundProfile>> {
        val path = "users/$userId/profiles"
        return db.collection("users").document(userId)
            .collection("profiles")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    CloudAppSoundProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { dto ->
                    AppSoundProfile(
                        packageName = dto.packageName,
                        appName = dto.appName,
                        monitored = dto.monitored,
                        profileMode = ProfileMode.fromRaw(dto.profileMode),
                        targetVolumePercent = dto.targetVolumePercent.toInt().coerceIn(0, 100),
                        restoreOnExit = dto.restoreOnExit,
                        scheduleOnly = dto.scheduleOnly,
                        updatedAtMillis = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeCloudSchedules(userId: String): Flow<List<QuietSchedule>> {
        val path = "users/$userId/schedules"
        return db.collection("users").document(userId)
            .collection("schedules")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    CloudQuietSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { dto ->
                    QuietSchedule(
                        id = dto.id,
                        title = dto.title,
                        startHour = dto.startHour.toInt().coerceIn(0, 23),
                        startMinute = dto.startMinute.toInt().coerceIn(0, 59),
                        endHour = dto.endHour.toInt().coerceIn(0, 23),
                        endMinute = dto.endMinute.toInt().coerceIn(0, 59),
                        daysOfWeek = dto.daysOfWeek.map { it.toInt() }.filter { it in 1..7 }
                            .ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) },
                        targetVolumePercent = dto.targetVolumePercent.toInt().coerceIn(0, 100),
                        applyToAllApps = dto.applyToAllApps,
                        enabled = dto.enabled,
                        createdAtMillis = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        updatedAtMillis = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun savePreferences(userId: String, prefs: UserPreferences): Result<Unit> {
        val path = "users/$userId/settings/preferences"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("settings").document("preferences")
            val exists = docRef.get().await().exists()
            val clampedPoll = prefs.pollIntervalMs.coerceIn(500L, 10000L)
            if (!exists) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "themeMode" to prefs.themeMode.name,
                    "monitoringEnabled" to prefs.monitoringEnabled,
                    "defaultRestoreOnExit" to prefs.defaultRestoreOnExit,
                    "pollIntervalMs" to clampedPoll,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "themeMode" to prefs.themeMode.name,
                    "monitoringEnabled" to prefs.monitoringEnabled,
                    "defaultRestoreOnExit" to prefs.defaultRestoreOnExit,
                    "pollIntervalMs" to clampedPoll,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveProfile(userId: String, profile: AppSoundProfile): Result<Unit> {
        val docId = sanitizeFirestoreDocId(profile.packageName)
        val path = "users/$userId/profiles/$docId"
        return try {
            val uid = requireUserId(userId)
            require(profile.packageName.isNotBlank() && profile.packageName.length <= 160) {
                "Nama pakej aplikasi tidak sah."
            }
            val safeAppName = profile.appName.trim().ifEmpty { profile.packageName }.take(100)
            val safeVol = profile.targetVolumePercent.coerceIn(0, 100).toLong()
            val docRef = db.collection("users").document(uid)
                .collection("profiles").document(docId)
            val exists = docRef.get().await().exists()
            if (!exists) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "packageName" to profile.packageName.take(160),
                    "appName" to safeAppName,
                    "monitored" to profile.monitored,
                    "profileMode" to profile.profileMode.name,
                    "targetVolumePercent" to safeVol,
                    "restoreOnExit" to profile.restoreOnExit,
                    "scheduleOnly" to profile.scheduleOnly,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "appName" to safeAppName,
                    "monitored" to profile.monitored,
                    "profileMode" to profile.profileMode.name,
                    "targetVolumePercent" to safeVol,
                    "restoreOnExit" to profile.restoreOnExit,
                    "scheduleOnly" to profile.scheduleOnly,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProfile(userId: String, packageName: String): Result<Unit> {
        val docId = sanitizeFirestoreDocId(packageName)
        val path = "users/$userId/profiles/$docId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid)
                .collection("profiles").document(docId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveSchedule(userId: String, schedule: QuietSchedule): Result<Unit> {
        val docId = sanitizeFirestoreDocId(schedule.id)
        val path = "users/$userId/schedules/$docId"
        return try {
            val uid = requireUserId(userId)
            val safeTitle = schedule.title.trim().ifEmpty { "Jadual Senyap" }.take(80)
            val safeDays = schedule.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
                .ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
                .map { it.toLong() }
            val docRef = db.collection("users").document(uid)
                .collection("schedules").document(docId)
            val exists = docRef.get().await().exists()
            if (!exists) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "startHour" to schedule.startHour.coerceIn(0, 23).toLong(),
                    "startMinute" to schedule.startMinute.coerceIn(0, 59).toLong(),
                    "endHour" to schedule.endHour.coerceIn(0, 23).toLong(),
                    "endMinute" to schedule.endMinute.coerceIn(0, 59).toLong(),
                    "daysOfWeek" to safeDays,
                    "targetVolumePercent" to schedule.targetVolumePercent.coerceIn(0, 100).toLong(),
                    "applyToAllApps" to schedule.applyToAllApps,
                    "enabled" to schedule.enabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "title" to safeTitle,
                    "startHour" to schedule.startHour.coerceIn(0, 23).toLong(),
                    "startMinute" to schedule.startMinute.coerceIn(0, 59).toLong(),
                    "endHour" to schedule.endHour.coerceIn(0, 23).toLong(),
                    "endMinute" to schedule.endMinute.coerceIn(0, 59).toLong(),
                    "daysOfWeek" to safeDays,
                    "targetVolumePercent" to schedule.targetVolumePercent.coerceIn(0, 100).toLong(),
                    "applyToAllApps" to schedule.applyToAllApps,
                    "enabled" to schedule.enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(userId: String, scheduleId: String): Result<Unit> {
        val docId = sanitizeFirestoreDocId(scheduleId)
        val path = "users/$userId/schedules/$docId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid)
                .collection("schedules").document(docId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun backupAll(
        userId: String,
        prefs: UserPreferences,
        profiles: List<AppSoundProfile>,
        schedules: List<QuietSchedule>
    ): Result<Long> {
        return try {
            requireUserId(userId)
            savePreferences(userId, prefs).getOrThrow()
            for (profile in profiles) {
                saveProfile(userId, profile).getOrThrow()
            }
            for (schedule in schedules) {
                saveSchedule(userId, schedule).getOrThrow()
            }
            Result.success(System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreAllFromCloud(userId: String): Result<CloudBackupSnapshot> {
        val basePath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val userDoc = db.collection("users").document(uid)

            val prefSnap = userDoc.collection("settings").document("preferences").get().await()
            val cloudPref = prefSnap.toObject(
                CloudUserPreferences::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val mappedPrefs = cloudPref?.let {
                UserPreferences(
                    themeMode = ThemeMode.fromRaw(it.themeMode),
                    monitoringEnabled = it.monitoringEnabled,
                    defaultRestoreOnExit = it.defaultRestoreOnExit,
                    pollIntervalMs = it.pollIntervalMs.coerceIn(500L, 10000L),
                    lastCloudBackupAtMillis = it.updatedAt?.toDate()?.time
                )
            }

            val profileSnap = userDoc.collection("profiles").get().await()
            val mappedProfiles = profileSnap.toObjects(
                CloudAppSoundProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { dto ->
                AppSoundProfile(
                    packageName = dto.packageName,
                    appName = dto.appName,
                    monitored = dto.monitored,
                    profileMode = ProfileMode.fromRaw(dto.profileMode),
                    targetVolumePercent = dto.targetVolumePercent.toInt().coerceIn(0, 100),
                    restoreOnExit = dto.restoreOnExit,
                    scheduleOnly = dto.scheduleOnly,
                    updatedAtMillis = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            val scheduleSnap = userDoc.collection("schedules").get().await()
            val mappedSchedules = scheduleSnap.toObjects(
                CloudQuietSchedule::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { dto ->
                QuietSchedule(
                    id = dto.id,
                    title = dto.title,
                    startHour = dto.startHour.toInt().coerceIn(0, 23),
                    startMinute = dto.startMinute.toInt().coerceIn(0, 59),
                    endHour = dto.endHour.toInt().coerceIn(0, 23),
                    endMinute = dto.endMinute.toInt().coerceIn(0, 59),
                    daysOfWeek = dto.daysOfWeek.map { it.toInt() }.filter { it in 1..7 }
                        .ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) },
                    targetVolumePercent = dto.targetVolumePercent.toInt().coerceIn(0, 100),
                    applyToAllApps = dto.applyToAllApps,
                    enabled = dto.enabled,
                    createdAtMillis = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    updatedAtMillis = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }

            Result.success(CloudBackupSnapshot(mappedPrefs, mappedProfiles, mappedSchedules))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, basePath)
            Result.failure(e)
        }
    }
}
