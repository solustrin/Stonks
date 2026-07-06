package com.trin.stonks.utils

import com.google.gson.JsonParser
import com.trin.stonks.data.FirebaseConfig

object JsonParser {
    fun parseGoogleServicesJson(json: String): FirebaseConfig? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val projectId = root.getAsJsonObject("project_info").get("project_id").asString
            val client = root.getAsJsonArray("client").get(0).asJsonObject
            val appId = client.getAsJsonObject("client_info").get("mobilesdk_app_id").asString
            val apiKey = client.getAsJsonArray("api_key")
                .get(0).asJsonObject.get("current_key").asString

            val services = client.getAsJsonObject("services")
            val databaseUrl = services?.getAsJsonObject("firebase_database")
                ?.get("database_url")?.asString
            val storageBucket = services?.getAsJsonObject("firebase_storage")
                ?.get("storage_bucket")?.asString

            FirebaseConfig(projectId, appId, apiKey, databaseUrl, storageBucket)
        } catch (e: Exception) {
            null
        }
    }
}