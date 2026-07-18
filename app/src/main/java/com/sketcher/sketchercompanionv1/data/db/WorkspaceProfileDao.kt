package com.sketcher.sketchercompanionv1.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sketcher.sketchercompanionv1.data.db.entities.WorkspaceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceProfileDao {

    @Query("SELECT * FROM workspace_profiles")
    fun getAllProfiles(): Flow<List<WorkspaceProfileEntity>>

    @Query("SELECT * FROM workspace_profiles")
    suspend fun getAllProfilesSync(): List<WorkspaceProfileEntity>

    @Query("SELECT * FROM workspace_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): WorkspaceProfileEntity?

    @Query("SELECT * FROM workspace_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): WorkspaceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: WorkspaceProfileEntity)

    @Update
    suspend fun updateProfile(profile: WorkspaceProfileEntity)

    @Query("DELETE FROM workspace_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)
}
