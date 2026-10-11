package com.siteledger.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.siteledger.app.domain.model.BudgetMath
import com.siteledger.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val companyName: Flow<String> =
        dataStore.data.map { it[KEY_COMPANY_NAME] ?: "" }

    override val warningThresholdPercent: Flow<Int> =
        dataStore.data.map { it[KEY_THRESHOLD] ?: BudgetMath.DEFAULT_WARNING_THRESHOLD_PERCENT }

    override suspend fun setCompanyName(name: String) {
        dataStore.edit { it[KEY_COMPANY_NAME] = name.trim() }
    }

    override suspend fun setWarningThresholdPercent(percent: Int) {
        dataStore.edit { it[KEY_THRESHOLD] = percent.coerceIn(1, 100) }
    }

    companion object {
        private val KEY_COMPANY_NAME = stringPreferencesKey("company_name")
        private val KEY_THRESHOLD = intPreferencesKey("warning_threshold_percent")
    }
}
