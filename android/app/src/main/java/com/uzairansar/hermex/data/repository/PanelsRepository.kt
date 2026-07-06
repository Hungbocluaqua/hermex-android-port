package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.*
import com.uzairansar.hermex.core.network.HermesApiClient

class PanelsRepository(private val client: HermesApiClient) {
    suspend fun crons(): List<CronJob> {
        val response = client.crons()
        return response.crons ?: response.jobs.orEmpty()
    }
    suspend fun createCron(request: CronCreateRequest): CronMutationResponse = client.createCron(request)
    suspend fun updateCron(request: CronUpdateRequest): CronMutationResponse = client.updateCron(request)
    suspend fun runCron(jobId: String): CronMutationResponse = client.runCron(jobId)
    suspend fun pauseCron(jobId: String, reason: String? = null): CronMutationResponse = client.pauseCron(jobId, reason)
    suspend fun resumeCron(jobId: String): CronMutationResponse = client.resumeCron(jobId)
    suspend fun deleteCron(jobId: String): CronMutationResponse = client.deleteCron(jobId)
    suspend fun cronStatus(): CronStatusResponse = client.cronStatus()
    suspend fun cronOutput(jobId: String): CronOutputResponse = client.cronOutput(jobId)
    suspend fun insights(days: Int = 30): InsightsResponse = client.insights(days)
    suspend fun sessions(): List<SessionSummary> = client.sessions().sessions.orEmpty()
    suspend fun skills(): List<SkillSummary> = client.skills().skills.orEmpty()
    suspend fun skillContent(name: String, file: String? = null): SkillContentResponse = client.skillContent(name, file)
    suspend fun toggleSkill(name: String, enabled: Boolean): ToggleSkillResponse = client.toggleSkill(name, enabled)
    suspend fun memory(): MemoryResponse = client.memory()
    suspend fun writeMemory(section: String, content: String): MemoryWriteResponse = client.writeMemory(section, content)
    suspend fun settings(): SettingsResponse = client.settings()
    suspend fun updateSettings(showCliSessions: Boolean): SettingsResponse = client.updateSettings(showCliSessions)
    suspend fun updatesCheck(): UpdatesCheckResponse = client.updatesCheck()
    suspend fun updatesCheckForced(): UpdatesCheckResponse = client.updatesCheckForced()
    suspend fun applyUpdate(target: String = "webui"): UpdatesApplyResponse = client.applyUpdate(target)
    suspend fun models(): ModelCatalogResponse = client.models()
    suspend fun modelsLive(): ModelsLiveResponse = client.modelsLive()
    suspend fun saveDefaultModel(model: String): DefaultModelResponse = client.defaultModel(model)
    suspend fun profiles(): ProfilesResponse = client.profiles()
    suspend fun switchProfile(profile: String): ProfileSwitchResponse = client.switchProfile(profile)
    suspend fun createProfile(
        name: String,
        cloneConfig: Boolean = false,
        defaultModel: String? = null,
        modelProvider: String? = null,
        baseUrl: String? = null,
        apiKey: String? = null,
    ): ProfileCreateResponse = client.createProfile(name, cloneConfig, defaultModel, modelProvider, baseUrl, apiKey)
    suspend fun reasoning(model: String? = null, provider: String? = null): ReasoningResponse = client.reasoning(model, provider)
}
