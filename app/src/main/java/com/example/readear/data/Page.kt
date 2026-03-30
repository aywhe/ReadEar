package com.example.readear.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 页面内容实体类
 */
@Entity(
    tableName = "pages",
    indices = [Index(value = ["bookUri", "pageNumber"], unique = true)]
)
data class Page(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookUri: String,
    val pageNumber: Int,
    val content: String,
    val isCompleted: Boolean = false
)
