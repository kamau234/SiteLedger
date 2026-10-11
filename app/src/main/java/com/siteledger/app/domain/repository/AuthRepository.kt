package com.siteledger.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SessionPhase {
    /** App is restoring a previously stored session (local data still usable). */
    INITIALIZING,

    /** No session; first-time sign-in is required and needs connectivity. */
    SIGNED_OUT,

    /** Cloud account restored; offline data operations are allowed. */
    SIGNED_IN,

    /** Access token could not be refreshed. Local writes continue; cloud work pauses. */
    SESSION_EXPIRED,

    /** No Supabase project configured; records stay on this phone only. */
    LOCAL_ONLY,
}

data class SessionInfo(
    val phase: SessionPhase,
    val userId: String?,
    val email: String?,
    val cloudConfigured: Boolean,
)

interface AuthRepository {
    val session: StateFlow<SessionInfo>

    suspend fun signIn(email: String, password: String): Result<Unit>

    /**
     * Create a new cloud account. When the project requires email confirmation no session is
     * returned yet — the session flow stays signed out until the user confirms and signs in.
     */
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Sign out. Pending unsynchronized changes are explained to the user beforehand.
     * [keepLocalData] = true keeps local records on the phone (default behavior).
     */
    suspend fun signOut(keepLocalData: Boolean)

    /** Uses the app without a cloud account (only offered when no project is configured). */
    suspend fun continueLocalOnly()

    /**
     * First sign-in bootstrap: download the user's cloud dataset, merge it into Room and
     * seed default categories. Requires connectivity; safe to retry.
     */
    suspend fun initialBootstrap(): Result<Unit>
}

interface SettingsRepository {
    val companyName: Flow<String>
    val warningThresholdPercent: Flow<Int>
    suspend fun setCompanyName(name: String)
    suspend fun setWarningThresholdPercent(percent: Int)
}
