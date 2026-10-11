package com.siteledger.app.data.remote

import com.siteledger.app.data.remote.dto.ExpenseCategoryDto
import com.siteledger.app.data.remote.dto.ExpenseDto
import com.siteledger.app.data.remote.dto.ProjectDto
import com.siteledger.app.data.remote.dto.SupplierDto
import com.siteledger.app.data.remote.dto.UpdatedAtDto
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage

/**
 * Thin typed wrapper over PostgREST and Storage calls. All supabase-kt API usage for
 * business data is confined here so the rest of the app stays testable and replaceable.
 */
class RemoteGateway(private val provider: SupabaseProvider) {

    fun isEnabled(): Boolean = provider.isConfigured

    // ---- upserts (idempotent: locally generated UUID + full-row payload) ----

    suspend fun upsertProject(dto: ProjectDto) {
        provider.client.from(TABLE_PROJECTS).upsert(dto) { onConflict = "id" }
    }

    suspend fun upsertCategory(dto: ExpenseCategoryDto) {
        provider.client.from(TABLE_CATEGORIES).upsert(dto) { onConflict = "id" }
    }

    suspend fun upsertSupplier(dto: SupplierDto) {
        provider.client.from(TABLE_SUPPLIERS).upsert(dto) { onConflict = "id" }
    }

    suspend fun upsertExpense(dto: ExpenseDto) {
        provider.client.from(TABLE_EXPENSES).upsert(dto) { onConflict = "id" }
    }

    // ---- pulls (owner-scoped by RLS) ----

    suspend fun fetchProjects(sinceIso: String?): List<ProjectDto> =
        provider.client.from(TABLE_PROJECTS).select {
            if (sinceIso != null) filter { gt("updated_at", sinceIso) }
        }.decodeList()

    suspend fun fetchCategories(sinceIso: String?): List<ExpenseCategoryDto> =
        provider.client.from(TABLE_CATEGORIES).select {
            if (sinceIso != null) filter { gt("updated_at", sinceIso) }
        }.decodeList()

    suspend fun fetchSuppliers(sinceIso: String?): List<SupplierDto> =
        provider.client.from(TABLE_SUPPLIERS).select {
            if (sinceIso != null) filter { gt("updated_at", sinceIso) }
        }.decodeList()

    suspend fun fetchExpenses(sinceIso: String?): List<ExpenseDto> =
        provider.client.from(TABLE_EXPENSES).select {
            if (sinceIso != null) filter { gt("updated_at", sinceIso) }
        }.decodeList()

    /** Server revision lookup used by the conflict pre-flight before uploading a change. */
    suspend fun remoteUpdatedAt(table: String, id: String): String? =
        provider.client.from(table)
            .select(Columns.list("id", "updated_at")) { filter { eq("id", id) } }
            .decodeList<UpdatedAtDto>()
            .firstOrNull()
            ?.updatedAt

    // ---- receipt storage (private bucket) ----

    suspend fun uploadReceipt(remotePath: String, bytes: ByteArray) {
        provider.client.storage.from(BUCKET_RECEIPTS).upload(remotePath, bytes) { upsert = true }
    }

    companion object {
        const val TABLE_PROJECTS = "projects"
        const val TABLE_CATEGORIES = "expense_categories"
        const val TABLE_SUPPLIERS = "suppliers"
        const val TABLE_EXPENSES = "expenses"
        const val BUCKET_RECEIPTS = "receipts"

        /** Receipt path convention from the blueprint: {user_uuid}/{project_uuid}/{expense_uuid}.jpg */
        fun receiptPath(userId: String, projectId: String, expenseId: String): String =
            "$userId/$projectId/$expenseId.jpg"
    }
}
