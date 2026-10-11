package com.siteledger.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.siteledger.app.data.local.dao.AppMetadataDao
import com.siteledger.app.data.local.dao.CategoryDao
import com.siteledger.app.data.local.dao.ExpenseDao
import com.siteledger.app.data.local.dao.MaintenanceDao
import com.siteledger.app.data.local.dao.OutboxDao
import com.siteledger.app.data.local.dao.ProjectDao
import com.siteledger.app.data.local.dao.SupplierDao
import com.siteledger.app.data.local.entity.AppMetadataEntity
import com.siteledger.app.data.local.entity.ExpenseCategoryEntity
import com.siteledger.app.data.local.entity.ExpenseEntity
import com.siteledger.app.data.local.entity.ProjectEntity
import com.siteledger.app.data.local.entity.SupplierEntity
import com.siteledger.app.data.local.entity.SyncOutboxEntity

@Database(
    entities = [
        ProjectEntity::class,
        ExpenseEntity::class,
        ExpenseCategoryEntity::class,
        SupplierEntity::class,
        SyncOutboxEntity::class,
        AppMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun supplierDao(): SupplierDao
    abstract fun outboxDao(): OutboxDao
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "siteledger.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Schema changes must ship as explicit, tested migrations (blueprint section 04).
                .build()
    }
}
