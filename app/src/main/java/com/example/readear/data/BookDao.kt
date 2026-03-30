package com.example.readear.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 书籍数据访问对象
 */
@Dao
interface BookDao {
    
    // ==================== Books ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)
    
    @Query("SELECT * FROM books WHERE bookUri = :bookUri")
    suspend fun getBook(bookUri: String): Book?
    
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    suspend fun getAllBooks(): List<Book>
    
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooksFlow(): Flow<List<Book>>
    
    @Query("DELETE FROM books WHERE bookUri = :bookUri")
    suspend fun deleteBook(bookUri: String)
    
    // ==================== Pages ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPages(pages: List<Page>)
    
    @Query("SELECT * FROM pages WHERE bookUri = :bookUri AND pageNumber = :page")
    suspend fun getPage(bookUri: String, page: Int): Page?
    
    @Query("SELECT * FROM pages WHERE bookUri = :bookUri AND pageNumber IN (:pages)")
    suspend fun getPages(bookUri: String, pages: List<Int>): List<Page>
    
    @Query(
        """
        SELECT * FROM pages 
        WHERE bookUri = :bookUri 
        AND pageNumber BETWEEN :startPage AND :endPage
        ORDER BY pageNumber
    """
    )
    suspend fun getPagesRange(bookUri: String, startPage: Int, endPage: Int): List<Page>
    
    @Query("SELECT * FROM pages WHERE bookUri = :bookUri")
    suspend fun getAllPages(bookUri: String): List<Page>
    
    @Query("SELECT COUNT(*) FROM pages WHERE bookUri = :bookUri")
    suspend fun getTotalPagesCount(bookUri: String): Int
    @Query("SELECT MAX(pageNumber) FROM pages WHERE bookUri = :bookUri")
    suspend fun getMaxPageIndex(bookUri: String): Int
    
    @Query("DELETE FROM pages WHERE bookUri = :bookUri")
    suspend fun deletePages(bookUri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pages WHERE bookUri = :bookUri)")
    fun hasAnyPages(bookUri: String): Boolean
    
    // ==================== Reading Progress ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingProgress(progress: ReadingProgress)
    
    @Query("SELECT * FROM reading_progress WHERE bookUri = :bookUri")
    suspend fun getReadingProgress(bookUri: String): ReadingProgress?
    
    @Query("DELETE FROM reading_progress WHERE bookUri = :bookUri")
    suspend fun deleteReadingProgress(bookUri: String)
}
