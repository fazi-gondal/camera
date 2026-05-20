package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stamped_photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val timestamp: Long,
    val formattedDateString: String,
    val note: String = "",
    val stampColorHex: String = "#FFEB3B" // yellow default
)
