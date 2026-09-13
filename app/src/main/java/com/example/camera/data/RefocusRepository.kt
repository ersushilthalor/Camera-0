package com.example.camera.data

import android.content.Context
import com.example.camera.data.db.AppDatabase
import com.example.camera.data.db.RefocusPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository managing persistent Refocus photo packages and database records.
 */
class RefocusRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.refocusDao()

    val allRefocusPhotos: Flow<List<RefocusPhotoEntity>> = dao.getAllRefocusPhotos()

    suspend fun getRefocusPhoto(photoUri: String): RefocusPhotoEntity? = withContext(Dispatchers.IO) {
        // 1. Check Room database
        val entity = dao.getRefocusPhoto(photoUri)
        if (entity != null && File(entity.bundleDir).exists()) {
            return@withContext entity
        }

        // 2. Fallback check in refocus storage directory by URI filename matching
        try {
            val bundlesDir = getRefocusStorageDir()
            val cleanName = photoUri.substringAfterLast("/").substringBeforeLast(".")
            val candidateDir = File(bundlesDir, cleanName)
            if (candidateDir.exists() && candidateDir.isDirectory) {
                val near = File(candidateDir, "plane_near.jpg")
                val mid = File(candidateDir, "plane_mid.jpg")
                val far = File(candidateDir, "plane_far.jpg")
                val depth = File(candidateDir, "depth_map.png")
                if (near.exists() && mid.exists() && far.exists()) {
                    val recoveredEntity = RefocusPhotoEntity(
                        photoUri = photoUri,
                        bundleDir = candidateDir.absolutePath,
                        nearPlanePath = near.absolutePath,
                        midPlanePath = mid.absolutePath,
                        farPlanePath = far.absolutePath,
                        depthMapPath = if (depth.exists()) depth.absolutePath else "",
                        planeCount = 3
                    )
                    dao.insertRefocusPhoto(recoveredEntity)
                    return@withContext recoveredEntity
                }
            }
        } catch (e: Exception) {
            // Ignore fallback lookup errors
        }

        null
    }

    suspend fun saveRefocusPhoto(entity: RefocusPhotoEntity) = withContext(Dispatchers.IO) {
        dao.insertRefocusPhoto(entity)
    }

    suspend fun deleteRefocusPhoto(photoUri: String) = withContext(Dispatchers.IO) {
        val entity = dao.getRefocusPhoto(photoUri)
        if (entity != null) {
            try {
                File(entity.bundleDir).deleteRecursively()
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
            dao.deleteRefocusPhoto(photoUri)
        }
    }

    fun getRefocusStorageDir(): File {
        val dir = File(context.filesDir, "refocus_bundles")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
