package com.trin.stonks.data

import androidx.lifecycle.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class InventoryStats(val item: InventoryItem, val totalRevenue: Double, val totalCost: Double, val netProfit: Double)

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingViewModel(private val repository: CloudRepository) : ViewModel() {

    private val _currentWorkspaceId = MutableStateFlow("default")
    val currentWorkspaceId: StateFlow<String> = _currentWorkspaceId

    val workspaces: StateFlow<List<Workspace>> = repository.getWorkspacesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<Transaction>> = _currentWorkspaceId.flatMapLatest { id ->
        repository.currentWorkspaceId = id
        repository.getAllTransactions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<String>> = _currentWorkspaceId.flatMapLatest { id ->
        repository.currentWorkspaceId = id
        repository.getProfiles()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Guest"))

    val paymentTypes: StateFlow<List<String>> = _currentWorkspaceId.flatMapLatest { id ->
        repository.currentWorkspaceId = id
        repository.getPaymentTypes()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Cash"))

    val uniqueItemNames: StateFlow<List<String>> = allTransactions.map { list ->
        list.map { it.itemName }.filter { !it.contains(",") }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRevenue: StateFlow<Double> = allTransactions.map { list -> list.filter { it.type == "SALE" }.sumOf { it.amount } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val totalExpenses: StateFlow<Double> = allTransactions.map { list -> list.filter { it.type == "EXPENSE" }.sumOf { it.amount } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val netProfit: StateFlow<Double> = combine(totalRevenue, totalExpenses) { r, e -> r - e }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val inventory: StateFlow<List<InventoryItem>> = _currentWorkspaceId.flatMapLatest { id ->
        repository.currentWorkspaceId = id
        repository.getInventoryFlow()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryStats: StateFlow<List<InventoryStats>> = combine(inventory, allTransactions) { inv, txs ->
        inv.map { stock ->
            val related = txs.filter { it.itemName.equals(stock.name, ignoreCase = true) }
            val rev = related.filter { it.type == "SALE" }.sumOf { it.amount }
            val cost = related.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            InventoryStats(stock, rev, cost, rev - cost)
        }.sortedByDescending { it.netProfit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTransactions(
        type: String, createdBy: String, paymentType: String,
        items: List<Triple<String, Int, Double>>, receiptBytes: ByteArray?,
        note: String, affectsStock: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val receiptUrl = if (receiptBytes != null) {
                    repository.uploadReceipt(receiptBytes)
                } else null

                items.forEach { (name, _, price) ->
                    repository.upsertInventoryItem(name, price)
                }

                items.forEach { (name, qty, unitPrice) ->
                    repository.insert(
                        Transaction(
                            type = type,
                            itemName = name,
                            amount = qty * unitPrice,
                            quantity = qty,
                            unitPrice = unitPrice,
                            createdBy = createdBy,
                            paymentType = paymentType,
                            note = note,
                            affectsStock = affectsStock,
                            receiptUrl = receiptUrl
                        )
                    )
                }

                if (affectsStock) {
                    items.forEach { (name, qty, _) ->
                        val itemId = repository.getInventoryItemIdByName(name)
                        if (itemId != null) {
                            val adjustment = if (type == "SALE") -qty else qty
                            repository.adjustStock(itemId, adjustment)
                        }
                    }
                }

                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    fun updateInventoryItem(id: String, name: String, price: Double, stock: Int) {
        repository.updateInventoryItem(id, name, price, stock)
    }

    fun updateTransaction(
        id: String,
        type: String,
        name: String,
        amt: Double,
        qty: Int,
        up: Double,
        cb: String,
        pt: String,
        bid: String,
        note: String,
        receiptBytes: ByteArray?
    ) {
        viewModelScope.launch {
            try {
                val receiptUrl = if (receiptBytes != null) {
                    repository.uploadReceipt(receiptBytes)
                } else null
                repository.update(id, type, name, amt, qty, up, cb, pt, bid, note, receiptUrl)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun getChartData(transactions: List<Transaction>, period: ChartPeriod): List<Triple<String, Double, Int>> {
        val now = System.currentTimeMillis()
        val durationMillis = when (period) {
            ChartPeriod.HOUR -> 24 * 60 * 60 * 1000L
            ChartPeriod.DAY -> 7 * 24 * 60 * 60 * 1000L
            ChartPeriod.WEEK -> 28 * 24 * 60 * 60 * 1000L
            ChartPeriod.MONTH -> 365L * 24 * 60 * 60 * 1000L
            ChartPeriod.YEAR -> 5L * 365 * 24 * 60 * 60 * 1000L
        }
        val filtered = transactions.filter { it.timestamp >= (now - durationMillis) }
        val fmt = when (period) {
            ChartPeriod.HOUR -> SimpleDateFormat("HH:mm", Locale.getDefault())
            ChartPeriod.DAY -> SimpleDateFormat("MMM d", Locale.getDefault())
            ChartPeriod.WEEK -> SimpleDateFormat("EEE", Locale.getDefault())
            ChartPeriod.MONTH -> SimpleDateFormat("MMM yy", Locale.getDefault())
            ChartPeriod.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault())
        }

        val grouped = filtered.groupBy { fmt.format(Date(it.timestamp)) }

        data class ChartEntry(val label: String, val total: Double, val count: Int, val minTimestamp: Long)
        val entries = grouped.map { (label, txs) ->
            ChartEntry(
                label = label,
                total = txs.sumOf { if (it.type == "SALE") it.amount else -it.amount },
                count = txs.size,
                minTimestamp = txs.minOf { it.timestamp }
            )
        }
        return entries.sortedBy { it.minTimestamp }
            .map { Triple(it.label, it.total, it.count) }
    }

    fun setWorkspace(id: String) { _currentWorkspaceId.value = id }
    fun createWorkspace(name: String) = repository.createWorkspace(name)
    fun deleteWorkspace(id: String) = repository.deleteWorkspace(id)
    fun updateWorkspaceName(wId: String, name: String) = repository.updateWorkspaceName(wId, name)
    fun addProfile(name: String) = repository.addProfile(name)
    fun deleteProfile(name: String) = repository.deleteProfile(name)
    fun addPaymentType(name: String) = repository.addPaymentType(name)
    fun deletePaymentType(name: String) = repository.deletePaymentType(name)
    fun deductStock(id: String, qty: Int) = repository.adjustStock(id, qty)
    fun addInventoryItem(name: String, price: Double, stock: Int) = repository.addInventoryItem(name, price, stock)
    fun deleteInventoryItem(id: String) = repository.deleteInventoryItem(id)
    fun deleteTransaction(id: String) = repository.delete(id)
}