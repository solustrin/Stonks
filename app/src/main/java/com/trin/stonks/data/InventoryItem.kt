package com.trin.stonks.data

data class InventoryItem(
    val id: String = "",
    val name: String = "",
    val defaultPrice: Double = 0.0,
    val currentStock: Int = 0
)