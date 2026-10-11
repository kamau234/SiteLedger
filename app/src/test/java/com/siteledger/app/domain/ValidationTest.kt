package com.siteledger.app.domain

import com.siteledger.app.domain.model.ExpenseDraft
import com.siteledger.app.domain.model.PaymentMethod
import com.siteledger.app.domain.model.ProjectDraft
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.SupplierDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidationTest {

    private fun projectDraft(
        name: String = "Riverside Villas",
        budget: Long = 500_000,
        start: LocalDate? = null,
        end: LocalDate? = null,
    ) = ProjectDraft(
        id = null,
        name = name,
        clientName = null,
        location = null,
        budgetAmount = budget,
        startDate = start,
        expectedEndDate = end,
        status = ProjectStatus.ACTIVE,
    )

    private fun expenseDraft(
        projectId: String? = "p1",
        categoryId: String? = "c1",
        description: String = "Cement bags",
        amount: Long = 125,
        quantityMilli: Long? = null,
        unitPriceKes: Long? = null,
    ) = ExpenseDraft(
        id = null,
        projectId = projectId,
        categoryId = categoryId,
        supplierId = null,
        description = description,
        quantityMilli = quantityMilli,
        unitPriceKes = unitPriceKes,
        amountKes = amount,
        paymentMethod = PaymentMethod.CASH,
        expenseDate = LocalDate.of(2026, 10, 1),
        notes = null,
        receiptLocalPath = null,
        receiptReplaced = false,
    )

    @Test
    fun `valid project passes`() {
        assertTrue(Validation.validateProject(projectDraft()).isEmpty())
    }

    @Test
    fun `project requires a name`() {
        val errors = Validation.validateProject(projectDraft(name = "   "))
        assertEquals(Validation.ProjectField.NAME, errors.single().field)
    }

    @Test
    fun `project rejects negative budget`() {
        val errors = Validation.validateProject(projectDraft(budget = -1))
        assertEquals(Validation.ProjectField.BUDGET, errors.single().field)
    }

    @Test
    fun `project rejects end date before start date`() {
        val errors = Validation.validateProject(
            projectDraft(
                start = LocalDate.of(2026, 10, 10),
                end = LocalDate.of(2026, 10, 1),
            ),
        )
        assertEquals(Validation.ProjectField.DATES, errors.single().field)
    }

    @Test
    fun `valid expense passes`() {
        assertTrue(Validation.validateExpense(expenseDraft()).isEmpty())
    }

    @Test
    fun `expense requires project category and description`() {
        val errors = Validation.validateExpense(
            expenseDraft(projectId = null, categoryId = "", description = " "),
        )
        assertEquals(
            listOf(
                Validation.ExpenseField.PROJECT,
                Validation.ExpenseField.CATEGORY,
                Validation.ExpenseField.DESCRIPTION,
            ),
            errors.map { it.field },
        )
    }

    @Test
    fun `expense rejects negative amount and zero quantity`() {
        val errors = Validation.validateExpense(
            expenseDraft(amount = -5, quantityMilli = 0),
        )
        assertEquals(
            listOf(Validation.ExpenseField.AMOUNT, Validation.ExpenseField.QUANTITY),
            errors.map { it.field },
        )
    }

    @Test
    fun `expense rejects negative unit price`() {
        val errors = Validation.validateExpense(expenseDraft(unitPriceKes = -1))
        assertEquals(Validation.ExpenseField.UNIT_PRICE, errors.single().field)
    }

    @Test
    fun `supplier requires a name and allows reasonable phones`() {
        assertTrue(Validation.validateSupplier(SupplierDraft(null, "Njoroge Hardware", "0712 000 000", null, null)).isEmpty())
        val missingName = Validation.validateSupplier(SupplierDraft(null, "", null, null, null))
        assertEquals(Validation.SupplierField.NAME, missingName.single().field)
        val longPhone = Validation.validateSupplier(SupplierDraft(null, "Njoroge", "0".repeat(33), null, null))
        assertEquals(Validation.SupplierField.PHONE, longPhone.single().field)
    }

    @Test
    fun `empty password has no strength`() {
        assertEquals(Validation.PasswordStrength.EMPTY, Validation.passwordStrength(""))
    }

    @Test
    fun `short or letters-only passwords are weak`() {
        assertEquals(Validation.PasswordStrength.WEAK, Validation.passwordStrength("abc"))
        assertEquals(Validation.PasswordStrength.WEAK, Validation.passwordStrength("abcdef"))
        assertEquals(Validation.PasswordStrength.WEAK, Validation.passwordStrength("abcdefgh"))
    }

    @Test
    fun `mixed passwords climb the strength scale`() {
        assertEquals(Validation.PasswordStrength.FAIR, Validation.passwordStrength("abcdef12"))
        assertEquals(Validation.PasswordStrength.GOOD, Validation.passwordStrength("Abcdef12"))
        assertEquals(Validation.PasswordStrength.STRONG, Validation.passwordStrength("Abcdef12!"))
        assertEquals(Validation.PasswordStrength.STRONG, Validation.passwordStrength("Abcdefghij12!"))
    }

    @Test
    fun `strength never exceeds its maximum score`() {
        val overTheTop = "Abcdefghij123456!@#"
        assertEquals(Validation.PasswordStrength.STRONG, Validation.passwordStrength(overTheTop))
        assertEquals(4, Validation.passwordStrength(overTheTop).score)
    }
}
