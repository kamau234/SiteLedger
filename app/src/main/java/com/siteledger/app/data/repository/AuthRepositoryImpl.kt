package com.siteledger.app.data.repository

import androidx.room.withTransaction
import com.siteledger.app.core.database.AppDatabase
import com.siteledger.app.core.util.UuidGenerator
import com.siteledger.app.data.local.dao.MaintenanceDao
import com.siteledger.app.data.receipt.ReceiptStore
import com.siteledger.app.data.remote.SupabaseProvider
import com.siteledger.app.data.session.ActiveUserStore
import com.siteledger.app.domain.repository.AuthRepository
import com.siteledger.app.domain.repository.CategoryRepository
import com.siteledger.app.domain.repository.SessionInfo
import com.siteledger.app.domain.repository.SessionPhase
import com.siteledger.app.sync.SyncEngine
import com.siteledger.app.sync.SyncTrigger
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the auth/session lifecycle. Local data keeps working when the session expires; only
 * cloud operations pause. Sign-out never silently erases pending records — wiping local
 * data is an explicit, explained choice ([signOut] with keepLocalData = false).
 */
class AuthRepositoryImpl(
    private val provider: SupabaseProvider,
    private val db: AppDatabase,
    private val maintenanceDao: MaintenanceDao,
    private val activeUser: ActiveUserStore,
    private val syncEngine: SyncEngine,
    private val categoryRepository: CategoryRepository,
    private val receiptStore: ReceiptStore,
    private val syncTrigger: SyncTrigger,
) : AuthRepository {

    private val _session = MutableStateFlow(
        SessionInfo(
            phase = if (provider.isConfigured) SessionPhase.INITIALIZING else SessionPhase.SIGNED_OUT,
            userId = null,
            email = null,
            cloudConfigured = provider.isConfigured,
        ),
    )
    override val session: StateFlow<SessionInfo> = _session

    private var watcher: Job? = null

    /** Starts observing the persisted Supabase session; call once from the application. */
    fun start(scope: CoroutineScope) {
        if (watcher != null) return
        watcher = scope.launch {
            activeUser.load()
            if (!provider.isConfigured) {
                val restoredUser = activeUser.current
                _session.value = SessionInfo(
                    phase = if (restoredUser != null) SessionPhase.LOCAL_ONLY else SessionPhase.SIGNED_OUT,
                    userId = restoredUser,
                    email = null,
                    cloudConfigured = false,
                )
                if (restoredUser != null) runCatching { categoryRepository.ensureDefaults() }
                return@launch
            }
            provider.client.auth.sessionStatus.collect { status ->
                _session.value = when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user != null) activeUser.set(user.id)
                        SessionInfo(
                            phase = SessionPhase.SIGNED_IN,
                            userId = user?.id ?: activeUser.current,
                            email = user?.email,
                            cloudConfigured = true,
                        )
                    }
                    is SessionStatus.Initializing -> SessionInfo(
                        SessionPhase.INITIALIZING,
                        activeUser.current,
                        _session.value.email,
                        cloudConfigured = true,
                    )
                    is SessionStatus.NotAuthenticated -> SessionInfo(
                        SessionPhase.SIGNED_OUT,
                        activeUser.current,
                        null,
                        cloudConfigured = true,
                    )
                    is SessionStatus.RefreshFailure -> SessionInfo(
                        SessionPhase.SESSION_EXPIRED,
                        activeUser.current,
                        _session.value.email,
                        cloudConfigured = true,
                    )
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        check(provider.isConfigured) { "Cloud sync is not configured in this build" }
        provider.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val user = (provider.client.auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user
            ?: throw IllegalStateException("Sign-in did not return a session. Check your connection and try again.")
        activeUser.set(user.id)
        _session.value = SessionInfo(SessionPhase.SIGNED_IN, user.id, user.email, cloudConfigured = true)
        runCatching { initialBootstrap() }
        syncTrigger.requestSync()
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        check(provider.isConfigured) { "Cloud sync is not configured in this build" }
        provider.client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val user = (provider.client.auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user
        if (user != null) {
            // Project auto-confirms emails: the signup returned a usable session right away.
            activeUser.set(user.id)
            _session.value = SessionInfo(SessionPhase.SIGNED_IN, user.id, user.email, cloudConfigured = true)
            runCatching { initialBootstrap() }
            syncTrigger.requestSync()
        }
        // Otherwise the project requires email confirmation; the user confirms then signs in.
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        check(provider.isConfigured) { "Cloud sync is not configured in this build" }
        provider.client.auth.resetPasswordForEmail(email.trim())
    }

    override suspend fun signOut(keepLocalData: Boolean) {
        if (provider.isConfigured) {
            runCatching { provider.client.auth.signOut() }
        }
        if (!keepLocalData) {
            // Order matters: children before parents (foreign keys are enforced).
            db.withTransaction {
                maintenanceDao.clearOutbox()
                maintenanceDao.clearExpenses()
                maintenanceDao.clearProjects()
                maintenanceDao.clearCategories()
                maintenanceDao.clearSuppliers()
                maintenanceDao.clearMetadata()
            }
            receiptStore.clearAll()
        }
        activeUser.clear()
        _session.value = SessionInfo(
            phase = SessionPhase.SIGNED_OUT,
            userId = null,
            email = null,
            cloudConfigured = provider.isConfigured,
        )
    }

    override suspend fun continueLocalOnly() {
        if (provider.isConfigured) return
        val localId = activeUser.current ?: "local-${UuidGenerator.newId()}"
        activeUser.set(localId)
        _session.value = SessionInfo(SessionPhase.LOCAL_ONLY, localId, null, cloudConfigured = false)
        runCatching { categoryRepository.ensureDefaults() }
    }

    override suspend fun initialBootstrap(): Result<Unit> {
        if (!provider.isConfigured) return runCatching { categoryRepository.ensureDefaults() }
        return syncEngine.runInitialBootstrap().mapCatching {
            categoryRepository.ensureDefaults()
        }
    }
}
