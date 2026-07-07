package com.trin.stonks.data
data class CloudInvoice(
    val invoiceNumber: String = "",
    val timestamp: Long = 0L,
    val senderName: String = "",
    val senderContact: String = "",
    val clientName: String = "",
    val clientAddress: String = "",
    val clientContact: String = "",
    val notes: String = "",
    val discount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val totalDue: Double = 0.0,
    val items: List<CloudInvoiceItem> = emptyList()
)

data class CloudInvoiceItem(
    val name: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0
)