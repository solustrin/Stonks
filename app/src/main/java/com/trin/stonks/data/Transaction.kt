package com.trin.stonks.data

data class Transaction(
    val id: String = "",
    val type: String,
    val itemName: String,
    val amount: Double,
    val quantity: Int = 1,
    val unitPrice: Double = amount,
    val createdBy: String = "Anonymous",
    val paymentType: String = "Cash",
    val receiptUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val batchId: String = "",
    val note: String = "",
    val affectsStock: Boolean = false
)