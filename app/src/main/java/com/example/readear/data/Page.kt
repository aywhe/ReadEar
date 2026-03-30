package com.example.readear.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 页面内容实体类
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId", "pageNumber"], unique = true)]
)
data class Page(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Int? = null,
    val pageNumber: Int,
    val content: String,
    val isCompleted: Boolean = false
)
