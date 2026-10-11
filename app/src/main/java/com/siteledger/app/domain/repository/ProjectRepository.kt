package com.siteledger.app.domain.repository

import com.siteledger.app.domain.model.Project
import com.siteledger.app.domain.model.ProjectDraft
import com.siteledger.app.domain.model.ProjectTotals
import kotlinx.coroutines.flow.Flow

/** Thrown by repository save operations when domain validation fails. */
class RepositoryValidationException(
    val errors: List<com.siteledger.app.domain.FieldError>,
) : Exception(errors.joinToString("; ") { it.message })

sealed class SaveResult {
    data class Success(val id: String) : SaveResult()
    data class ValidationFailed(val errors: List<com.siteledger.app.domain.FieldError>) : SaveResult()
    data class Failure(val message: String) : SaveResult()
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeProject(id: String): Flow<Project?>
    fun observeProjectTotals(): Flow<List<ProjectTotals>>
    suspend fun getProject(id: String): Project?
    suspend fun saveProject(draft: ProjectDraft): SaveResult
    suspend fun archiveProject(id: String): SaveResult
    suspend fun deleteProject(id: String): SaveResult
}
