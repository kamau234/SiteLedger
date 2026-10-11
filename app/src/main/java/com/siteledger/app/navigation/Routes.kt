package com.siteledger.app.navigation

/** Route patterns and builders used by the NavHost. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val PROJECTS = "projects"
    const val EXPENSES = "expenses"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val SYNC = "sync"
    const val CATEGORIES = "categories"
    const val SUPPLIERS = "suppliers"

    const val ARG_PROJECT_ID = "projectId"
    const val ARG_EXPENSE_ID = "expenseId"
    const val ARG_SUPPLIER_ID = "supplierId"

    const val PROJECT_DETAIL = "projects/{$ARG_PROJECT_ID}"
    const val PROJECT_FORM = "project-form?$ARG_PROJECT_ID={$ARG_PROJECT_ID}"
    const val EXPENSE_FORM =
        "expense-form?$ARG_EXPENSE_ID={$ARG_EXPENSE_ID}&$ARG_PROJECT_ID={$ARG_PROJECT_ID}"
    const val SUPPLIER_FORM = "supplier-form?$ARG_SUPPLIER_ID={$ARG_SUPPLIER_ID}"

    fun projectDetail(projectId: String): String = "projects/$projectId"

    fun projectForm(projectId: String? = null): String =
        "project-form?$ARG_PROJECT_ID=${projectId.orEmpty()}"

    fun expenseForm(expenseId: String? = null, projectId: String? = null): String =
        "expense-form?$ARG_EXPENSE_ID=${expenseId.orEmpty()}&$ARG_PROJECT_ID=${projectId.orEmpty()}"

    fun supplierForm(supplierId: String? = null): String =
        "supplier-form?$ARG_SUPPLIER_ID=${supplierId.orEmpty()}"

    val TOP_LEVEL = setOf(DASHBOARD, PROJECTS, EXPENSES, REPORTS, SETTINGS)
}
