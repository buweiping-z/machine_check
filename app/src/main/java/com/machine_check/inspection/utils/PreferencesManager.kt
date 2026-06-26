package com.machine_check.inspection.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 应用级别的 DataStore 扩展属性 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "inspection_prefs")

/**
 * DataStore 偏好设置管理器
 * 用于持久化存储工号等信息
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_EMPLOYEE_ID = stringPreferencesKey("employee_id")
    }

    /** 获取存储的工号（Flow，实时监听变化） */
    val employeeId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_EMPLOYEE_ID] ?: ""
    }

    /** 保存工号 */
    suspend fun saveEmployeeId(employeeId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EMPLOYEE_ID] = employeeId
        }
    }
}
