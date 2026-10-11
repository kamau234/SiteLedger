package com.siteledger.app.sync

import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.IdGenerator
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.entity.OutboxEntityType
import com.siteledger.app.data.local.entity.OutboxOperation
import com.siteledger.app.data.local.entity.SyncOutboxEntity

/**
 * Writes outbox entries. Must be called inside the same Room transaction as the business
 * change it describes (blueprint section 04, local save protocol step 2).
 *
 * Coalescing rule: a newer change for the same entity replaces pending row operations
 * because the payload always carries the full current row, so ordering stays valid.
 */
class OutboxWriter(
    private val ids: IdGenerator,
    private val clock: AppClock,
) {

    suspend fun enqueueRowChange(
        outboxDao: OutboxDao,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String,
    ) {
        outboxDao.deletePendingRowOps(entityType, entityId)
        outboxDao.insert(
            SyncOutboxEntity(
                id = ids.newId(),
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadJson = payloadJson,
                createdAt = clock.nowMillis(),
            ),
        )
    }

    suspend fun enqueueReceiptUpload(
        outboxDao: OutboxDao,
        expenseId: String,
        payloadJson: String,
    ) {
        outboxDao.deletePendingOps(OutboxEntityType.RECEIPT, expenseId, OutboxOperation.UPLOAD_RECEIPT)
        outboxDao.insert(
            SyncOutboxEntity(
                id = ids.newId(),
                entityType = OutboxEntityType.RECEIPT,
                entityId = expenseId,
                operation = OutboxOperation.UPLOAD_RECEIPT,
                payloadJson = payloadJson,
                createdAt = clock.nowMillis(),
            ),
        )
    }
}
