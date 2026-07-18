package com.sketcher.sketchercompanionv1.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workspace_profiles")
data class WorkspaceProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val layoutJson: String,
    val themeJson: String,
    val isDefault: Boolean = false,
    val isReadOnly: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)
