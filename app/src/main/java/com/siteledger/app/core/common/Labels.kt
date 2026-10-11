package com.siteledger.app.core.common

import com.siteledger.app.domain.model.EntitySyncState
import com.siteledger.app.domain.model.PaymentMethod
import com.siteledger.app.domain.model.ProjectStatus
import com.siteledger.app.domain.model.ReceiptUploadState
import com.siteledger.app.domain.model.ReportPreset

fun ProjectStatus.label(): String = when (this) {
    ProjectStatus.PLANNED -> "Planned"
    ProjectStatus.ACTIVE -> "Active"
    ProjectStatus.COMPLETED -> "Completed"
    ProjectStatus.ARCHIVED -> "Archived"
}

fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.MOBILE_MONEY -> "Mobile money"
    PaymentMethod.BANK -> "Bank"
    PaymentMethod.OTHER -> "Other"
}

fun EntitySyncState.label(): String = when (this) {
    EntitySyncState.PENDING -> "Pending upload"
    EntitySyncState.SYNCED -> "Synced"
    EntitySyncState.FAILED -> "Failed"
}

fun ReceiptUploadState.label(): String = when (this) {
    ReceiptUploadState.NONE -> "No receipt"
    ReceiptUploadState.PENDING -> "Receipt pending"
    ReceiptUploadState.UPLOADED -> "Receipt stored"
    ReceiptUploadState.FAILED -> "Receipt failed"
}

fun ReportPreset.label(): String = when (this) {
    ReportPreset.TODAY -> "Today"
    ReportPreset.THIS_WEEK -> "This week"
    ReportPreset.THIS_MONTH -> "This month"
    ReportPreset.LAST_MONTH -> "Last month"
    ReportPreset.ALL_TIME -> "All time"
    ReportPreset.CUSTOM -> "Custom"
}
