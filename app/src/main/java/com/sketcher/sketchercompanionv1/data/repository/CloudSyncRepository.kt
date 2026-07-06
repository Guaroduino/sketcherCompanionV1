package com.sketcher.sketchercompanionv1.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.io.File
import android.util.Log

class CloudSyncRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun uploadProject(
        projectId: String = UUID.randomUUID().toString(),
        projectName: String,
        projectFileUri: Uri,
        relativePath: String,
        thumbnailUri: Uri? = null,
        timestamp: Long? = null,
        metadata: Map<String, Any> = emptyMap()
    ): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))

        return try {
            // Upload project file (e.g. .skc zip file)
            val projectRef = storage.reference.child("users/${user.uid}/projects/$projectId/project.skc")
            projectRef.putFile(projectFileUri).await()
            val projectUrl = projectRef.downloadUrl.await().toString()

            var thumbnailUrl: String? = null
            if (thumbnailUri != null) {
                val thumbRef = storage.reference.child("users/${user.uid}/projects/$projectId/thumbnail.png")
                thumbRef.putFile(thumbnailUri).await()
                thumbnailUrl = thumbRef.downloadUrl.await().toString()
            }

            // Save metadata to Firestore
            val projectData = mutableMapOf<String, Any>(
                "id" to projectId,
                "name" to projectName,
                "relativePath" to relativePath,
                "fileUrl" to projectUrl,
                "thumbnailUrl" to (thumbnailUrl ?: ""),
                "timestamp" to (timestamp ?: System.currentTimeMillis())
            )
            projectData.putAll(metadata)

            firestore.collection("users").document(user.uid)
                .collection("projects").document(projectId)
                .set(projectData).await()

            Result.success(projectId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjects(): Result<List<Map<String, Any>>> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("projects").get().await()
            val projects = snapshot.documents.map { it.data ?: emptyMap<String, Any>() }
            Result.success(projects)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun wipeAllProjects(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("projects").get().await()
            
            // Delete from Firestore
            for (doc in snapshot.documents) {
                firestore.collection("users").document(user.uid)
                    .collection("projects").document(doc.id).delete().await()
            }
            
            // Delete from Storage
            val listResult = storage.reference.child("users/${user.uid}/projects").listAll().await()
            for (prefix in listResult.prefixes) {
                try {
                    prefix.child("project.skc").delete().await()
                } catch (e: Exception) { /* ignore if doesn't exist */ }
                try {
                    prefix.child("thumbnail.png").delete().await()
                } catch (e: Exception) { /* ignore */ }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            // Soft delete in Firestore
            val deleteData = mapOf(
                "id" to projectId,
                "deleted" to true,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("users").document(user.uid)
                .collection("projects").document(projectId)
                .set(deleteData).await()
            
            // Delete from Storage (ignore errors if not found)
            try {
                storage.reference.child("users/${user.uid}/projects/$projectId/project.skc").delete().await()
            } catch (e: Exception) { /* ignore */ }
            try {
                storage.reference.child("users/${user.uid}/projects/$projectId/thumbnail.png").delete().await()
            } catch (e: Exception) { /* ignore */ }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadProject(projectId: String, destinationFile: File): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val projectRef = storage.reference.child("users/${user.uid}/projects/$projectId/project.skc")
            projectRef.getFile(destinationFile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadThumbnail(projectId: String, destinationFile: File): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val thumbRef = storage.reference.child("users/${user.uid}/projects/$projectId/thumbnail.png")
            thumbRef.getFile(destinationFile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- PREFERENCES SYNC ---
    suspend fun backupPreferences(prefs: Map<String, Any>): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            firestore.collection("users").document(user.uid)
                .collection("preferences").document("deviceSettings")
                .set(prefs).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restorePreferences(): Result<Map<String, Any>> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("preferences").document("deviceSettings").get().await()
            val data = snapshot.data ?: emptyMap()
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- LIBRARY SYNC ---
    suspend fun backupLibrary(jsonString: String, timestamp: Long, assetsDir: File): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            // 1. Save JSON to Firestore
            val libraryData = mapOf("state" to jsonString, "timestamp" to timestamp)
            firestore.collection("users").document(user.uid)
                .collection("library").document("state")
                .set(libraryData).await()

            // 2. Upload assets (images)
            if (assetsDir.exists() && assetsDir.isDirectory) {
                assetsDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val ref = storage.reference.child("users/${user.uid}/library_assets/${file.name}")
                        ref.putFile(Uri.fromFile(file)).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLibraryTimestamp(): Result<Long> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("library").document("state").get().await()
            val ts = (snapshot.get("timestamp") as? Number)?.toLong() ?: 0L
            Result.success(ts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreLibrary(assetsDir: File): Result<Pair<String, Long>> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            // 1. Download JSON from Firestore
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("library").document("state").get().await()
            
            val jsonString = snapshot.getString("state") ?: return Result.failure(Exception("No library found"))
            val timestamp = (snapshot.get("timestamp") as? Number)?.toLong() ?: 0L

            // 2. Download assets
            val listResult = storage.reference.child("users/${user.uid}/library_assets").listAll().await()
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }
            listResult.items.forEach { item ->
                val localFile = File(assetsDir, item.name)
                item.getFile(localFile).await()
            }

            Result.success(Pair(jsonString, timestamp))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
