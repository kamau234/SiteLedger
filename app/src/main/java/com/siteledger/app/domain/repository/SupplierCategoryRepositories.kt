package com.siteledger.app.domain.repository

import com.siteledger.app.domain.model.ExpenseCategory
import com.siteledger.app.domain.model.Supplier
import com.siteledger.app.domain.model.SupplierDraft
import kotlinx.coroutines.flow.Flow

interface SupplierRepository {
    fun observeSuppliers(): Flow<List<Supplier>>
    suspend fun getSupplier(id: String): Supplier?
    suspend fun saveSupplier(draft: SupplierDraft): SaveResult
    suspend fun deleteSupplier(id: String): SaveResult
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<ExpenseCategory>>
    suspend fun getCategory(id: String): ExpenseCategory?
    /** Seeds the default categories (Materials, Labour, ...) when missing. Idempotent. */
    suspend fun ensureDefaults()
    suspend fun addCategory(name: String): SaveResult
    suspend fun deleteCategory(id: String): SaveResult
}
