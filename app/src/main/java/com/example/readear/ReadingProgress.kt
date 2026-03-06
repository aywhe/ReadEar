package com.example.readear

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 阅读进度实体类
 */
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey
    val bookId: String,
    val currentPage: Int,
    val timestamp: Long
)
