package com.trin.stonks.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ChartPeriod(val label: String) {
    HOUR("Hourly"),
    DAY("Daily"),
    WEEK("Weekly"),
    MONTH("Monthly"),
    YEAR("Yearly"),
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingViewModel(private val repository: CloudRepository) : ViewModel() {

    // --- Workspace State ---
    private val _currentWorkspaceId = MutableStateFlow("default")
    val currentWorkspaceId: StateFlow<String> = _currentWorkspaceId

    val workspaces: StateFlow<List<Workspace>> = repository.getWorkspacesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Data flows reactively depend on the active workspace ---
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
        list.map { it.itemName }
            .filter { !it.contains(",") && !it.matches(Regex("^\\d+x\\s.*")) }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRevenue: StateFlow<Double> = allTransactions.map { list ->
        list.filter { it.type == "SALE" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpenses: StateFlow<Double> = allTransactions.map { list ->
        list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netProfit: StateFlow<Double> = combine(totalRevenue, totalExpenses) { rev, exp ->
        rev - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- Workspace actions ---
    fun setWorkspace(workspaceId: String) {
        _currentWorkspaceId.value = workspaceId
    }

    fun createWorkspace(name: String) {
        repository.createWorkspace(name)
    }

    fun deleteWorkspace(id: String) {
        repository.deleteWorkspace(id)
    }

    fun updateWorkspaceName(workspaceId: String, newName: String) {
        repository.updateWorkspaceName(workspaceId, newName)
    }

    // --- Profile actions ---
    fun addProfile(name: String) = repository.addProfile(name)
    fun deleteProfile(name: String) = repository.deleteProfile(name)

    // --- Payment type actions ---
    fun addPaymentType(name: String) = repository.addPaymentType(name)
    fun deletePaymentType(name: String) = repository.deletePaymentType(name)

    // --- Transaction actions ---
    fun addTransactions(
        type: String, createdBy: String, paymentType: String,
        items: List<Triple<String, Int, Double>>, receiptBytes: ByteArray?,
        onComplete: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                var uploadedUrl: String? = null
                if (receiptBytes != null) {
                    uploadedUrl = repository.uploadReceipt(receiptBytes)
                }

                val batchId = UUID.randomUUID().toString()
                val sharedTimestamp = System.currentTimeMillis()
                items.forEach { (name, qty, unitPrice) ->
                    val tx = Transaction(
                        type = type, itemName = name, amount = qty * unitPrice, quantity = qty,
                        unitPrice = unitPrice, createdBy = createdBy, paymentType = paymentType,
                        receiptUrl = uploadedUrl, timestamp = sharedTimestamp, batchId = batchId
                    )
                    repository.insert(tx)
                }
                onComplete(true, null)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, e.message ?: "Unknown error")
            }
        }
    }

    fun deleteTransaction(transactionId: String) = repository.delete(transactionId)

    fun updateTransaction(
        id: String, type: String, name: String, amount: Double,
        quantity: Int, unitPrice: Double, createdBy: String, paymentType: String, batchId: String
    ) {
        repository.update(id, type, name, amount, quantity, unitPrice, createdBy, paymentType, batchId)
    }

    // --- Chart Data ---
    fun getChartData(transactions: List<Transaction>, period: ChartPeriod): List<Triple<String, Double, Int>> {
        if (transactions.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val durationMillis = when (period) {
            ChartPeriod.HOUR   -> 24 * 60 * 60 * 1000L
            ChartPeriod.DAY    -> 7 * 24 * 60 * 60 * 1000L
            ChartPeriod.WEEK   -> 28 * 24 * 60 * 60 * 1000L
            ChartPeriod.MONTH  -> 365L * 24 * 60 * 60 * 1000L
            ChartPeriod.YEAR   -> 5L * 365 * 24 * 60 * 60 * 1000L
        }

        val filtered = transactions.filter { it.timestamp >= (now - durationMillis) }

        // Weekly: group by day of week (Mon, Tue, ...)
        if (period == ChartPeriod.WEEK) {
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val dayOfWeekMap = filtered.groupBy { tx ->
                Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    .get(Calendar.DAY_OF_WEEK)
            }
            val orderedDays = listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
            )
            return orderedDays.mapIndexed { index, dayValue ->
                val txs = dayOfWeekMap[dayValue] ?: emptyList()
                val profit = txs.sumOf { if (it.type == "SALE") it.amount else -it.amount }
                Triple(dayNames[index], profit, txs.size)
            }
        }


        // Other periods (Hourly, Daily, Monthly, Yearly)
        val grouped = filtered.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            when (period) {
                ChartPeriod.HOUR -> {
                    cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                }
                ChartPeriod.DAY -> {
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
                }
                ChartPeriod.MONTH -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    SimpleDateFormat("MMM yy", Locale.getDefault()).format(cal.time)
                }
                ChartPeriod.YEAR -> {
                    cal.set(Calendar.MONTH, Calendar.JANUARY); cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                    SimpleDateFormat("yyyy", Locale.getDefault()).format(cal.time)
                }
                // This else covers WEEK and ALL, but they are already handled – it's just for compilation
                else -> ""
            }
        }

        return grouped
            .map { (label, txs) ->
                val profit = txs.sumOf { if (it.type == "SALE") it.amount else -it.amount }
                val time = txs.minOf { it.timestamp }
                Triple(time, label, profit to txs.size)
            }
            .sortedBy { it.first }
            .map { (_, label, pair) -> Triple(label, pair.first, pair.second) }
    }
}

class AccountingViewModelFactory(private val repository: CloudRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccountingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}