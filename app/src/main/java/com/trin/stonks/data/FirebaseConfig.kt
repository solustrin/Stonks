package com.trin.stonks.data

data class FirebaseConfig(
    val projectId: String,
    val appId: String,
    val apiKey: String,
    val databaseUrl: String? = null,
    val storageBucket: String? = null
)