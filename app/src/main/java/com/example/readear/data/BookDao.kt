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
    
    @Query("SELECT * FROM books WHERE bookId = :bookId")
    suspend fun getBook(bookId: String): Book?
    
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    suspend fun getAllBooks(): List<Book>
    
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooksFlow(): Flow<List<Book>>
    
    @Query("DELETE FROM books WHERE bookId = :bookId")
    suspend fun deleteBook(bookId: String)
    
    // ==================== Pages ====================
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPage(page: Page)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPages(pages: List<Page>)
    
    @Query("SELECT * FROM pages WHERE bookId = :bookId AND pageNumber = :page")
    suspend fun getPage(bookId: String, page: Int): Page?
    
    @Query("SELECT * FROM pages WHERE bookId = :bookId AND pageNumber IN (:pages)")
    suspend fun getPages(bookId: String, pages: List<Int>): List<Page>
    
    @Query("""
        SELECT * FROM pages 
        WHERE bookId = :bookId 
        AND pageNumber BETWEEN :startPage AND :endPage
        ORDER BY pageNumber
    """)
    suspend fun getPagesRange(bookId: String, startPage: Int, endPage: Int): List<Page>
    
    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    suspend fun getAllPages(bookId: String): List<Page>
    
    @Query("SELECT COUNT(*) FROM pages WHERE bookId = :bookId")
    suspend fun getTotalPagesCount(bookId: String): Int
    @Query("SELECT MAX(pageNumber) FROM pages WHERE bookId = :bookId")
    suspend fun getMaxPageIndex(bookId: String): Int
    
    @Query("DELETE FROM pages WHERE bookId = :bookId")
    suspend fun deletePages(bookId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pages WHERE bookId = :bookId)")
    fun hasAnyPages(bookId: String): Boolean
    
    // ==================== Reading Progress ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingProgress(progress: ReadingProgress)
    
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getReadingProgress(bookId: String): ReadingProgress?
    
    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteReadingProgress(bookId: String)
}
