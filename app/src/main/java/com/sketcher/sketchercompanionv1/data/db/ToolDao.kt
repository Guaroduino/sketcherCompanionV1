package com.sketcher.sketchercompanionv1.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sketcher.sketchercompanionv1.data.db.entities.UserToolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {
    @Query("SELECT * FROM user_tools ORDER BY sortOrder ASC")
    fun getAllToolsFlow(): Flow<List<UserToolEntity>>

    @Query("SELECT * FROM user_tools ORDER BY sortOrder ASC")
    suspend fun getAllTools(): List<UserToolEntity>

    @Query("SELECT * FROM user_tools WHERE id = :id LIMIT 1")
    suspend fun getToolById(id: String): UserToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: UserToolEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<UserToolEntity>)

    @Update
    suspend fun updateTool(tool: UserToolEntity)

    @Query("DELETE FROM user_tools WHERE id = :id")
    suspend fun deleteToolById(id: String)
    
    @Query("DELETE FROM user_tools")
    suspend fun deleteAll()
}
