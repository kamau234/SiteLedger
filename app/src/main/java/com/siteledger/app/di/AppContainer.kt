package com.siteledger.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.siteledger.app.BuildConfig
import com.siteledger.app.core.common.ConnectivityObserver
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.AppClock
import com.siteledger.app.core.util.IdGenerator
import com.siteledger.app.core.util.SystemAppClock
import com.siteledger.app.core.util.UuidGenerator
import com.siteledger.app.data.receipt.ReceiptStore
import com.siteledger.app.data.remote.RemoteGateway
import com.siteledger.app.data.remote.SupabaseProvider
import com.siteledger.app.data.repository.AuthRepositoryImpl
import com.siteledger.app.data.repository.CategoryRepositoryImpl
import com.siteledger.app.data.repository.ExpenseRepositoryImpl
import com.siteledger.app.data.repository.ProjectRepositoryImpl
import com.siteledger.app.data.repository.SettingsRepositoryImpl
import com.siteledger.app.data.repository.SupplierRepositoryImpl
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.repository.CategoryRepository
import com.siteledger.app.domain.repository.ExpenseRepository
import com.siteledger.app.domain.repository.ProjectRepository
import com.siteledger.app.domain.repository.SettingsRepository
import com.siteledger.app.domain.repository.SupplierRepository
import com.siteledger.app.reports.ReportComposer
import com.siteledger.app.reports.ReportExporter
import com.siteledger.app.sync.OutboxWriter
import com.siteledger.app.sync.PullMerger
import com.siteledger.app.sync.SyncEngine
import com.siteledger.app.sync.SyncScheduler

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "siteledger_settings")

/**
 * Manual dependency container (a small testable app container, per blueprint section 01).
 * Everything is lazy so the database is only opened when first needed.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val clock: AppClock = SystemAppClock()
    val ids: IdGenerator = UuidGenerator
    val connectivity: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }
    val supabase: SupabaseProvider by lazy {
        SupabaseProvider(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    }
    val remoteGateway: RemoteGateway by lazy { RemoteGateway(supabase) }
    val receiptStore: ReceiptStore by lazy { ReceiptStore(appContext) }
    val activeUser: ActiveUserStore by lazy { ActiveUserStore(database.appMetadataDao()) }
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
    val outboxWriter: OutboxWriter by lazy { OutboxWriter(ids, clock) }

    val pullMerger: PullMerger by lazy {
        PullMerger(
            db = database,
            gateway = remoteGateway,
            projectDao = database.projectDao(),
            categoryDao = database.categoryDao(),
            supplierDao = database.supplierDao(),
            expenseDao = database.expenseDao(),
            outboxDao = database.outboxDao(),
            outboxWriter = outboxWriter,
            activeUser = activeUser,
            clock = clock,
        )
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            db = database,
            outboxDao = database.outboxDao(),
            projectDao = database.projectDao(),
            categoryDao = database.categoryDao(),
            supplierDao = database.supplierDao(),
            expenseDao = database.expenseDao(),
            metadataDao = database.appMetadataDao(),
            gateway = remoteGateway,
            activeUser = activeUser,
            connectivity = connectivity,
            receiptStore = receiptStore,
            pullMerger = pullMerger,
            outboxWriter = outboxWriter,
            clock = clock,
        )
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(
            db = database,
            categoryDao = database.categoryDao(),
            expenseDao = database.expenseDao(),
            outboxDao = database.outboxDao(),
            outboxWriter = outboxWriter,
            activeUser = activeUser,
            syncTrigger = syncScheduler,
            clock = clock,
            ids = ids,
        )
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepositoryImpl(
            db = database,
            projectDao = database.projectDao(),
            expenseDao = database.expenseDao(),
            outboxDao = database.outboxDao(),
            outboxWriter = outboxWriter,
            activeUser = activeUser,
            syncTrigger = syncScheduler,
            clock = clock,
            ids = ids,
        )
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepositoryImpl(
            db = database,
            expenseDao = database.expenseDao(),
            outboxDao = database.outboxDao(),
            outboxWriter = outboxWriter,
            receiptStore = receiptStore,
            activeUser = activeUser,
            syncTrigger = syncScheduler,
            clock = clock,
            ids = ids,
        )
    }

    val supplierRepository: SupplierRepository by lazy {
        SupplierRepositoryImpl(
            db = database,
            supplierDao = database.supplierDao(),
            outboxDao = database.outboxDao(),
            outboxWriter = outboxWriter,
            activeUser = activeUser,
            syncTrigger = syncScheduler,
            clock = clock,
            ids = ids,
        )
    }

    val authRepository: AuthRepositoryImpl by lazy {
        AuthRepositoryImpl(
            provider = supabase,
            db = database,
            maintenanceDao = database.maintenanceDao(),
            activeUser = activeUser,
            syncEngine = syncEngine,
            categoryRepository = categoryRepository,
            receiptStore = receiptStore,
            syncTrigger = syncScheduler,
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(appContext.settingsDataStore)
    }

    val reportComposer: ReportComposer by lazy {
        ReportComposer(
            expenseRepository = expenseRepository,
            projectRepository = projectRepository,
            settingsRepository = settingsRepository,
            clock = clock,
        )
    }

    val reportExporter: ReportExporter by lazy { ReportExporter(appContext) }
}
