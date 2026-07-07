package com.trin.stonks.data

import android.content.SharedPreferences

data class SavedClient(val id: String, val name: String, val contact: String, val address: String)

data class InvoiceHistory(val invoiceNumber: String, val timestamp: Long, val clientName: String, val total: Double)

object LocalDataHelper {
    // --- CLIENTS ---
    fun getClients(prefs: SharedPreferences): List<SavedClient> {
        val str = prefs.getString("saved_clients", "") ?: ""
        if (str.isBlank()) return emptyList()
        return str.split("<EOR>").mapNotNull {
            val parts = it.split("|||")
            if (parts.size == 4) SavedClient(parts[0], parts[1], parts[2], parts[3]) else null
        }
    }

    fun saveClient(prefs: SharedPreferences, client: SavedClient) {
        val current = getClients(prefs).toMutableList()
        current.removeAll { it.name.equals(client.name, ignoreCase = true) }
        current.add(client)
        val str = current.joinToString("<EOR>") { "${it.id}|||${it.name}|||${it.contact}|||${it.address}" }
        prefs.edit().putString("saved_clients", str).apply()
    }

    fun deleteClient(prefs: SharedPreferences, id: String) {
        val current = getClients(prefs).toMutableList()
        current.removeAll { it.id == id }
        val str = current.joinToString("<EOR>") { "${it.id}|||${it.name}|||${it.contact}|||${it.address}" }
        prefs.edit().putString("saved_clients", str).apply()
    }

    // --- INVOICE HISTORY ---
    fun getInvoiceHistory(prefs: SharedPreferences): List<InvoiceHistory> {
        val str = prefs.getString("invoice_history", "") ?: ""
        if (str.isBlank()) return emptyList()
        return str.split("<EOR>").mapNotNull {
            val parts = it.split("|||")
            if (parts.size == 4) InvoiceHistory(parts[0], parts[1].toLong(), parts[2], parts[3].toDouble()) else null
        }
    }

    fun saveInvoiceHistory(prefs: SharedPreferences, history: InvoiceHistory) {
        val current = getInvoiceHistory(prefs).toMutableList()
        current.add(0, history)
        val str = current.joinToString("<EOR>") { "${it.invoiceNumber}|||${it.timestamp}|||${it.clientName}|||${it.total}" }
        prefs.edit().putString("invoice_history", str).apply()
    }
}