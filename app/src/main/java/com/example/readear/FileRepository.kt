package com.example.readear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.repositoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "file_list")

class FileRepository(private val context: Context) {
    
    private val dataStore = context.repositoryDataStore
    
    /**
     * 异步保存文件列表到 DataStore
     * 在后台线程执行，不阻塞调用方
     */
    fun saveFileList(fileList: List<FileItem>) {
        kotlinx.coroutines.GlobalScope.launch {
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
