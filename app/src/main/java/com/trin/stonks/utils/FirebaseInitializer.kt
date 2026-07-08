package com.trin.stonks.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.trin.stonks.data.FirebaseConfig

object FirebaseInitializer {
    private const val APP_NAME = "userApp"

    fun initializeFirebase(context: Context, config: FirebaseConfig): FirebaseFirestore {
        // If the app is already initialized, just return the existing instance
        val existingApp = try {
            FirebaseApp.getInstance(APP_NAME)
        } catch (e: Exception) {
            null
        }

        if (existingApp != null) {
            return FirebaseFirestore.getInstance(existingApp)
        }

        // Only initialize if it doesn't exist
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.appId)
            .setApiKey(config.apiKey)
            .setDatabaseUrl(config.databaseUrl ?: "https://${config.projectId}.firebaseio.com")
            .setStorageBucket(config.storageBucket ?: "${config.projectId}.appspot.com")
            .build()

        val app = FirebaseApp.initializeApp(context, options, APP_NAME)
        return FirebaseFirestore.getInstance(app)
    }

    fun getFirestore(): FirebaseFirestore {
        return try {
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(APP_NAME))
        } catch (e: Exception) {
            FirebaseFirestore.getInstance()
        }
    }
}