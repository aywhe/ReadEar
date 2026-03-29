package com.example.readear.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书籍实体类
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    val bookId: String,
    val title: String,
    val filePath: String,
    val totalWords: Int,
    val totalPages: Int,
    val lastReadTime: Long,
    val isCompleted: Boolean = false,
    // 断点信息
    val breakpoint: Int = 0,
    val breakpointPage: Int = 0,
    val breakRemainContent: String = ""
)
