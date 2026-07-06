package com.trin.stonks.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.trin.stonks.data.FirebaseConfig

object FirebaseInitializer {
    private const val APP_NAME = "userApp"

    fun initializeFirebase(context: Context, config: FirebaseConfig): FirebaseFirestore {
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.appId)
            .setApiKey(config.apiKey)
            .setDatabaseUrl(config.databaseUrl ?: "https://${config.projectId}.firebaseio.com")
            .setStorageBucket(config.storageBucket ?: "${config.projectId}.appspot.com")
            .build()

        try {
            FirebaseApp.initializeApp(context, options, APP_NAME)
        } catch (e: IllegalStateException) {
            // already initialized
        }
        val app = FirebaseApp.getInstance(APP_NAME)
        return FirebaseFirestore.getInstance(app)
    }

    fun getFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance(FirebaseApp.getInstance(APP_NAME))
    }
}