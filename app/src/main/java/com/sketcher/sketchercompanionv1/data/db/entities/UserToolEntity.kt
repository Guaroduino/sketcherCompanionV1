package com.sketcher.sketchercompanionv1.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_tools")
data class UserToolEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconName: String,
    val iconResName: String?,
    val baseToolType: String,
    val presetJson: String, // serialized BrushPresetJson
    val customIconJson: String?,
    val isDefault: Boolean, // true if it's one of the app's base brushes
    val sortOrder: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
