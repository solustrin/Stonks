package com.trin.stonks.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AccountingViewModelFactory(private val repository: CloudRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountingViewModel::class.java)) {
            return AccountingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}