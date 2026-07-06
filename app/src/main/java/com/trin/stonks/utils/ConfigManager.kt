package com.trin.stonks.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.trin.stonks.data.FirebaseConfig

object ConfigManager {
    private const val PREF_NAME = "secure_config"
    private const val KEY_FIREBASE_CONFIG = "firebase_config"
    private const val KEY_IMGBB_KEY = "imgbb_key"
    private const val KEY_CONFIG_EXISTS = "config_exists"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveConfig(context: Context, config: FirebaseConfig, imgbbKey: String) {
        val prefs = getPrefs(context)
        val json = Gson().toJson(config)
        prefs.edit().apply {
            putString(KEY_FIREBASE_CONFIG, json)
            putString(KEY_IMGBB_KEY, imgbbKey)
            putBoolean(KEY_CONFIG_EXISTS, true)
            apply()
        }
    }

    fun getConfig(context: Context): FirebaseConfig? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_FIREBASE_CONFIG, null) ?: return null
        return try {
            Gson().fromJson(json, FirebaseConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getImgbbKey(context: Context): String? {
        return getPrefs(context).getString(KEY_IMGBB_KEY, null)
    }

    fun hasConfig(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CONFIG_EXISTS, false)
    }

    fun clearConfig(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}