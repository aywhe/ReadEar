package com.example.readear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.repositoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "file_list")

class FileRepository(private val context: Context) {
    
    private val dataStore = context.repositoryDataStore
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 异步保存文件列表到 DataStore
     * 在后台线程执行，不阻塞调用方
     */
    fun saveFileList(fileList: List<FileItem>) {
        coroutineScope.launch {
            try {
                val jsonArray = JSONArray()
                fileList.forEach { file ->
                    val jsonObject = JSONObject().apply {
                        put("fileName", file.fileName)
                        put("fileType", file.fileType.name)
                        put("fileUri", file.fileUri)
                        put("fileSize", file.fileSize)
                    }
                    jsonArray.put(jsonObject)
                }
                
                dataStore.edit { preferences ->
                    preferences[FILE_LIST_KEY] = jsonArray.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除文件及其相关缓存和进度
     * @param file 要删除的文件
     * @param fileList 当前文件列表
     */
    fun deleteFile(file: FileItem, fileList: List<FileItem>) {
        // 清除缓存和阅读进度
        val cacheManager = TextCacheManager(context)
        cacheManager.clearCache(file.fileUri)
        cacheManager.clearReadingProgress(file.fileUri)
        
        // 从列表中移除并保存
        val updatedList = fileList.filter { it.fileUri != file.fileUri }
        saveFileList(updatedList)
    }
    
    /**
     * 从 DataStore 加载文件列表
     * 需要在协程中调用
     */
    suspend fun loadFileList(): List<FileItem> {
        return try {
            val jsonString = dataStore.data.map { 
                it[FILE_LIST_KEY] 
            }.first()
            
            if (!jsonString.isNullOrEmpty()) {
                val jsonArray = JSONArray(jsonString)
                val restoredList = mutableListOf<FileItem>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val fileItem = FileItem(
                        fileName = jsonObject.getString("fileName"),
                        fileType = FileType.valueOf(jsonObject.getString("fileType")),
                        fileUri = jsonObject.getString("fileUri"),
                        fileSize = jsonObject.getLong("fileSize")
                    )
                    restoredList.add(fileItem)
                }
                
                restoredList
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    companion object {
        private val FILE_LIST_KEY = stringPreferencesKey("file_list")
    }
}
