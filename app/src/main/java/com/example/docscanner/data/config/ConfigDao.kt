package com.example.docscanner.data.config

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 配置数据访问对象
 */
@Dao
interface ConfigDao {

    @Query("SELECT * FROM app_config WHERE `key` = :key")
    suspend fun getConfig(key: String): AppConfig?

    @Query("SELECT * FROM app_config")
    suspend fun getAllConfigs(): List<AppConfig>

    @Query("SELECT `value` FROM app_config WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: AppConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfigs(configs: List<AppConfig>)

    @Query("DELETE FROM app_config WHERE `key` = :key")
    suspend fun deleteConfig(key: String)

    @Query("DELETE FROM app_config")
    suspend fun deleteAllConfigs()

    // Flow 版本，用于监听配置变化
    @Query("SELECT `value` FROM app_config WHERE `key` = :key")
    fun getValueFlow(key: String): Flow<String?>
}
