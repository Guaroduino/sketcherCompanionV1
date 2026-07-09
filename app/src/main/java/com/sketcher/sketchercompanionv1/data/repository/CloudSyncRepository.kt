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
            val versionId = UUID.randomUUID().toString()
            val uploadTimestamp = timestamp ?: System.currentTimeMillis()

            // Upload project file under versioned path
            val projectRef = storage.reference.child("users/${user.uid}/projects/$projectId/versions/$versionId/project.skc")
            projectRef.putFile(projectFileUri).await()
            val projectUrl = projectRef.downloadUrl.await().toString()

            var thumbnailUrl: String? = null
            if (thumbnailUri != null) {
                val thumbRef = storage.reference.child("users/${user.uid}/projects/$projectId/versions/$versionId/thumbnail.png")
                thumbRef.putFile(thumbnailUri).await()
                thumbnailUrl = thumbRef.downloadUrl.await().toString()
            }

            // Get existing versions from Firestore
            val docRef = firestore.collection("users").document(user.uid)
                .collection("projects").document(projectId)
            
            var existingVersions: List<Map<String, Any>> = emptyList()
            try {
                val docSnapshot = docRef.get().await()
                if (docSnapshot.exists()) {
                    existingVersions = docSnapshot.get("versions") as? List<Map<String, Any>> ?: emptyList()
                }
            } catch (e: Exception) {
                // If document does not exist, existingVersions is empty
            }

            // Construct new version metadata
            val newVersion = mapOf(
                "versionId" to versionId,
                "timestamp" to uploadTimestamp,
                "fileUrl" to projectUrl,
                "thumbnailUrl" to (thumbnailUrl ?: ""),
                "deviceName" to (metadata["deviceName"] as? String ?: android.os.Build.MODEL),
                "deviceUid" to (metadata["deviceUid"] as? String ?: ""),
                "fileSize" to (metadata["fileSize"] as? Long ?: 0L)
            )

            // Combine and sort by timestamp descending
            val updatedVersions = (existingVersions + newVersion)
                .sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }

            // Keep up to 5 versions, delete older from Cloud Storage
            val keepVersions = updatedVersions.take(5)
            val deleteVersions = updatedVersions.drop(5)

            for (v in deleteVersions) {
                val oldVersionId = v["versionId"] as? String ?: continue
                try {
                    storage.reference.child("users/${user.uid}/projects/$projectId/versions/$oldVersionId/project.skc").delete().await()
                } catch (e: Exception) { /* ignore */ }
                try {
                    storage.reference.child("users/${user.uid}/projects/$projectId/versions/$oldVersionId/thumbnail.png").delete().await()
                } catch (e: Exception) { /* ignore */ }
            }

            // Save root project metadata + versions list
            val projectData = mutableMapOf<String, Any>(
                "id" to projectId,
                "name" to projectName,
                "relativePath" to relativePath,
                "fileUrl" to projectUrl,
                "thumbnailUrl" to (thumbnailUrl ?: ""),
                "timestamp" to uploadTimestamp,
                "deleted" to false,
                "versions" to keepVersions
            )
            projectData.putAll(metadata)

            docRef.set(projectData).await()

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
            
            // Delete folders from Firestore
            val foldersSnapshot = firestore.collection("users").document(user.uid)
                .collection("folders").get().await()
            for (doc in foldersSnapshot.documents) {
                firestore.collection("users").document(user.uid)
                    .collection("folders").document(doc.id).delete().await()
            }

            // Delete library document from Firestore
            try {
                firestore.collection("users").document(user.uid)
                    .collection("library").document("state").delete().await()
            } catch (e: Exception) { /* ignore */ }

            // Delete preferences from Firestore
            try {
                firestore.collection("users").document(user.uid)
                    .collection("preferences").document("deviceSettings").delete().await()
            } catch (e: Exception) { /* ignore */ }
            
            // Delete projects from Storage
            val listResult = storage.reference.child("users/${user.uid}/projects").listAll().await()
            for (prefix in listResult.prefixes) {
                try {
                    prefix.child("project.skc").delete().await()
                } catch (e: Exception) { /* ignore if doesn't exist */ }
                try {
                    prefix.child("thumbnail.png").delete().await()
                } catch (e: Exception) { /* ignore */ }
                // Also list version files and delete them
                try {
                    val versionsList = prefix.child("versions").listAll().await()
                    for (versionFolder in versionsList.prefixes) {
                        try { versionFolder.child("project.skc").delete().await() } catch (e: Exception) {}
                        try { versionFolder.child("thumbnail.png").delete().await() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }

            // Delete library assets from Storage
            try {
                val libAssetsList = storage.reference.child("users/${user.uid}/library_assets").listAll().await()
                for (item in libAssetsList.items) {
                    item.delete().await()
                }
            } catch (e: Exception) { /* ignore */ }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val docRef = firestore.collection("users").document(user.uid)
                .collection("projects").document(projectId)
            
            var versions: List<Map<String, Any>> = emptyList()
            val currentData = mutableMapOf<String, Any>()
            try {
                val docSnapshot = docRef.get().await()
                if (docSnapshot.exists()) {
                    versions = docSnapshot.get("versions") as? List<Map<String, Any>> ?: emptyList()
                    docSnapshot.data?.let { currentData.putAll(it) }
                }
            } catch (e: Exception) { /* ignore */ }
            
            // Delete older versions' files from Storage (keep only the latest version in case of restore/conflict resolution)
            if (versions.size > 1) {
                val sortedVersions = versions.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                val latestVersion = sortedVersions.firstOrNull()
                val olderVersions = sortedVersions.drop(1)
                for (v in olderVersions) {
                    val oldVersionId = v["versionId"] as? String ?: continue
                    try {
                        storage.reference.child("users/${user.uid}/projects/$projectId/versions/$oldVersionId/project.skc").delete().await()
                    } catch (e: Exception) { /* ignore */ }
                    try {
                        storage.reference.child("users/${user.uid}/projects/$projectId/versions/$oldVersionId/thumbnail.png").delete().await()
                    } catch (e: Exception) { /* ignore */ }
                }
                
                currentData["deleted"] = true
                currentData["timestamp"] = System.currentTimeMillis()
                currentData["versions"] = listOfNotNull(latestVersion)
                docRef.set(currentData).await()
            } else {
                currentData["deleted"] = true
                currentData["timestamp"] = System.currentTimeMillis()
                docRef.set(currentData).await()
            }
            
            // Delete legacy unversioned files if they exist
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

    suspend fun downloadProject(projectId: String, destinationFile: File, fileUrl: String? = null): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val projectRef = if (!fileUrl.isNullOrEmpty()) {
                storage.getReferenceFromUrl(fileUrl)
            } else {
                storage.reference.child("users/${user.uid}/projects/$projectId/project.skc")
            }
            projectRef.getFile(destinationFile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadThumbnail(projectId: String, destinationFile: File, thumbnailUrl: String? = null): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val thumbRef = if (!thumbnailUrl.isNullOrEmpty()) {
                storage.getReferenceFromUrl(thumbnailUrl)
            } else {
                storage.reference.child("users/${user.uid}/projects/$projectId/thumbnail.png")
            }
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

    // --- CUSTOM BRUSHES SYNC ---
    suspend fun syncCustomBrush(brushId: String, brushData: Map<String, Any>?): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val docRef = firestore.collection("users").document(user.uid)
                .collection("custom_brushes").document(brushId)
            
            if (brushData == null) {
                // Delete
                docRef.delete().await()
            } else {
                // Save or Update
                val dataToSave = brushData.toMutableMap()
                dataToSave["updatedAt"] = System.currentTimeMillis()
                docRef.set(dataToSave).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllCustomBrushes(): Result<List<Map<String, Any>>> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("custom_brushes").get().await()
            val brushes = snapshot.documents.map { it.data ?: emptyMap<String, Any>() }
            Result.success(brushes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- UI PRESETS SYNC ---
    suspend fun syncUiPreset(presetName: String, presetData: String?): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val docRef = firestore.collection("users").document(user.uid)
                .collection("ui_presets").document(presetName)
            
            if (presetData == null) {
                // Delete
                docRef.delete().await()
            } else {
                // Save or Update
                val dataToSave = mapOf(
                    "name" to presetName,
                    "data" to presetData,
                    "updatedAt" to System.currentTimeMillis()
                )
                docRef.set(dataToSave).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUiPresets(): Result<List<Map<String, Any>>> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("ui_presets").get().await()
            val presets = snapshot.documents.map { it.data ?: emptyMap<String, Any>() }
            Result.success(presets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- LIBRARY SYNC ---
    suspend fun backupLibrary(jsonString: String, timestamp: Long, assetsDir: File): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            // 1. Save JSON to Firebase Storage to bypass 1MB limit
            val stateBytes = jsonString.toByteArray(Charsets.UTF_8)
            val stateRef = storage.reference.child("users/${user.uid}/library_state/global_library.json")
            stateRef.putBytes(stateBytes).await()

            // 1.5. Save timestamp to Firestore
            val libraryData = mapOf("timestamp" to timestamp)
            firestore.collection("users").document(user.uid)
                .collection("library").document("state")
                .set(libraryData).await()

            // 2. Upload assets (images)
            if (assetsDir.exists() && assetsDir.isDirectory) {
                assetsDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val ref = storage.reference.child("users/${user.uid}/library_assets/${file.name}")
                        ref.putFile(android.net.Uri.fromFile(file)).await()
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
            // 1. Download JSON from Firestore or Storage
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("library").document("state").get().await()
            
            var jsonString = snapshot.getString("state")
            val timestamp = (snapshot.get("timestamp") as? Number)?.toLong() ?: 0L

            if (jsonString == null) {
                try {
                    val stateRef = storage.reference.child("users/${user.uid}/library_state/global_library.json")
                    val maxDownloadSize = 25L * 1024 * 1024 // 25MB limit for library state JSON
                    val bytes = stateRef.getBytes(maxDownloadSize).await()
                    jsonString = String(bytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    return Result.failure(Exception("No library found"))
                }
            }

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

    // --- FOLDER SYNC ---
    suspend fun uploadFolder(
        relativePath: String,
        coverStyle: String,
        coverFill: Map<String, Any>?,
        coverProject: String?,
        timestamp: Long
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        val docId = relativePath.replace("/", "__")
        return try {
            val folderData = mutableMapOf<String, Any>(
                "relativePath" to relativePath,
                "coverStyle" to coverStyle,
                "timestamp" to timestamp,
                "deleted" to false
            )
            if (coverFill != null) {
                folderData["coverFill"] = coverFill
            }
            if (coverProject != null) {
                folderData["coverProject"] = coverProject
            }

            firestore.collection("users").document(user.uid)
                .collection("folders").document(docId)
                .set(folderData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFolders(): Result<List<Map<String, Any>>> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        return try {
            val snapshot = firestore.collection("users").document(user.uid)
                .collection("folders").get().await()
            val folders = snapshot.documents.map { it.data ?: emptyMap<String, Any>() }
            Result.success(folders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(relativePath: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
        val docId = relativePath.replace("/", "__")
        return try {
            val deleteData = mapOf(
                "relativePath" to relativePath,
                "deleted" to true,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("users").document(user.uid)
                .collection("folders").document(docId)
                .set(deleteData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
