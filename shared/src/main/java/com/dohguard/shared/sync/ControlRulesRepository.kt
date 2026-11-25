package com.dohguard.shared.sync

import android.content.Context
import android.content.SharedPreferences
import com.dohguard.shared.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Repositório central de regras parentais, rotinas, limites, concessões de tempo extra
 * e fila de sincronização offline/online entre MonitorDad Parent e MonitorDad Kids.
 */
object ControlRulesRepository {

    private const val PREFS_NAME = "monitordad_control_rules"
    private const val KEY_RULES_JSON = "rules_state_json"
    private const val KEY_LAST_SEQ = "last_accepted_sequence"
    private const val KEY_PENDING_JSON = "pending_envelopes_json"
    private const val KEY_PROCESSED_IDS = "processed_envelope_ids"

    private var prefs: SharedPreferences? = null
    private val processedEnvelopeIds = mutableSetOf<String>()
    private var lastAcceptedSequence: Long = 1L

    private val _rulesState = MutableStateFlow(ControlRulesState.defaultDemo())
    val rulesState: StateFlow<ControlRulesState> = _rulesState.asStateFlow()

    private val _syncEnvelopes = MutableStateFlow<List<RuleSyncEnvelope>>(emptyList())
    val syncEnvelopes: StateFlow<List<RuleSyncEnvelope>> = _syncEnvelopes.asStateFlow()

    private val _pendingRequests = MutableStateFlow(PendingRequest.DEMO_REQUESTS)
    val pendingRequests: StateFlow<List<PendingRequest>> = _pendingRequests.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        lastAcceptedSequence = sp.getLong(KEY_LAST_SEQ, 1L)
        val savedIds = sp.getStringSet(KEY_PROCESSED_IDS, emptySet()) ?: emptySet()
        processedEnvelopeIds.addAll(savedIds)

        val rawJson = sp.getString(KEY_RULES_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            deserializeState(rawJson)?.let { restored ->
                _rulesState.value = restored
            }
        } else {
            persistState(_rulesState.value)
        }
    }

    fun setOnlineStatus(online: Boolean) {
        _isOnline.value = online
        if (online) {
            flushPendingSyncQueue()
        }
    }

    fun updateDailyLimit(limitMinutes: Int, days: Set<Weekday>) {
        val safeLimit = limitMinutes.coerceIn(0, 1440)
        mutateState { current ->
            current.copy(
                dailyDeviceLimitMinutes = safeLimit,
                dailyLimitDays = days
            )
        }
    }

    fun toggleDevicePause() {
        mutateState { current ->
            current.copy(isDevicePaused = !current.isDevicePaused)
        }
    }

    fun setDevicePaused(paused: Boolean) {
        mutateState { current ->
            current.copy(isDevicePaused = paused)
        }
    }

    fun toggleBlockApp(packageName: String, appName: String = packageName) {
        mutateState { current ->
            val existing = current.appLimits[packageName] ?: AppLimitRule(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = 60
            )
            val updated = existing.copy(isBlocked = !existing.isBlocked)
            current.copy(appLimits = current.appLimits + (packageName to updated))
        }
    }

    fun setAppBlocked(packageName: String, appName: String = packageName, blocked: Boolean) {
        mutateState { current ->
            val existing = current.appLimits[packageName] ?: AppLimitRule(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = 60
            )
            val updated = existing.copy(isBlocked = blocked)
            current.copy(appLimits = current.appLimits + (packageName to updated))
        }
    }

    fun updateAppLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        allowedWindow: TimeWindow? = null,
        daysOfWeek: Set<Weekday> = Weekday.ALL_DAYS,
        enabled: Boolean = true
    ) {
        mutateState { current ->
            val existing = current.appLimits[packageName]
            val updated = AppLimitRule(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = dailyLimitMinutes.coerceIn(0, 1440),
                isBlocked = existing?.isBlocked ?: false,
                allowedWindow = allowedWindow ?: existing?.allowedWindow,
                daysOfWeek = daysOfWeek,
                enabled = enabled
            )
            current.copy(appLimits = current.appLimits + (packageName to updated))
        }
    }

    fun upsertCategoryLimit(rule: CategoryLimitRule) {
        mutateState { current ->
            val safeRule = rule.copy(dailyLimitMinutes = rule.dailyLimitMinutes.coerceIn(0, 1440))
            current.copy(categoryLimits = current.categoryLimits + (safeRule.category to safeRule))
        }
    }

    fun removeCategoryLimit(category: AppCategory) {
        mutateState { current ->
            current.copy(categoryLimits = current.categoryLimits - category)
        }
    }

    fun upsertRoutine(routine: RoutineRule) {
        mutateState { current ->
            val existingIdx = current.routines.indexOfFirst { it.id == routine.id }
            val updatedList = if (existingIdx >= 0) {
                current.routines.toMutableList().apply { set(existingIdx, routine) }
            } else {
                current.routines + routine
            }
            current.copy(routines = updatedList)
        }
    }

    fun toggleRoutineEnabled(routineId: String, enabled: Boolean) {
        mutateState { current ->
            current.copy(
                routines = current.routines.map {
                    if (it.id == routineId) it.copy(enabled = enabled) else it
                }
            )
        }
    }

    fun deleteRoutine(routineId: String) {
        mutateState { current ->
            current.copy(routines = current.routines.filterNot { it.id == routineId })
        }
    }

    fun grantExtraTime(
        minutes: Int,
        targetPackage: String? = null,
        targetAppName: String? = null,
        reason: String = "Tempo extra concedido pelo responsável"
    ): ExtraTimeGrant? {
        if (minutes <= 0) return null
        val now = System.currentTimeMillis()
        val grant = ExtraTimeGrant(
            id = "grant-${UUID.randomUUID().toString().take(8)}",
            targetPackage = targetPackage,
            targetAppName = targetAppName,
            minutes = minutes,
            grantedAtMillis = now,
            expiresAtMillis = now + minutes * 60_000L,
            grantedBy = "Responsável",
            reason = reason
        )
        mutateState { current ->
            val activeGrants = current.extraGrants.filter { it.expiresAtMillis > now } + grant
            val history = (listOf(grant) + current.grantHistory).take(50)
            current.copy(
                isDevicePaused = if (targetPackage == null) false else current.isDevicePaused,
                extraGrants = activeGrants,
                grantHistory = history
            )
        }
        return grant
    }

    fun requestExtraTimeFromKids(
        appName: String? = null,
        requestedMinutes: Int = 15,
        description: String = "Lucas pediu +$requestedMinutes min de tempo adicional"
    ) {
        val req = PendingRequest(
            id = "req-${UUID.randomUUID().toString().take(8)}",
            type = if (appName != null) RequestType.APP_UNLOCK else RequestType.EXTRA_TIME,
            title = if (appName != null) "Desbloquear $appName" else "Tempo Adicional",
            description = description,
            requestedMinutes = requestedMinutes,
            requestedAt = "agora mesmo",
            appName = appName
        )
        _pendingRequests.value = listOf(req) + _pendingRequests.value
    }

    fun approvePendingRequest(request: PendingRequest) {
        val targetPkg = if (request.type == RequestType.APP_UNLOCK && request.appName != null) {
            _rulesState.value.appLimits.values
                .firstOrNull { it.appName.equals(request.appName, ignoreCase = true) }
                ?.packageName
        } else {
            null
        }
        grantExtraTime(
            minutes = request.requestedMinutes.coerceAtLeast(5),
            targetPackage = targetPkg,
            targetAppName = request.appName,
            reason = "Solicitação aprovada: ${request.title}"
        )
        _pendingRequests.value = _pendingRequests.value.filterNot { it.id == request.id }
    }

    fun rejectPendingRequest(request: PendingRequest) {
        _pendingRequests.value = _pendingRequests.value.filterNot { it.id == request.id }
    }

    fun applyIncomingSyncEnvelope(envelope: RuleSyncEnvelope): SyncValidationResult {
        val result = RuleSyncValidator.validateIncomingEnvelope(
            envelope = envelope,
            lastAcceptedSequence = lastAcceptedSequence,
            processedEnvelopeIds = processedEnvelopeIds
        )
        if (result is SyncValidationResult.Accepted) {
            lastAcceptedSequence = envelope.sequenceNumber
            processedEnvelopeIds.add(envelope.envelopeId)
            _rulesState.value = envelope.state
            persistState(envelope.state)
            val acked = envelope.copy(ackedAtMillis = System.currentTimeMillis())
            _syncEnvelopes.value = (_syncEnvelopes.value.filterNot { it.envelopeId == envelope.envelopeId } + acked)
                .takeLast(30)
        }
        return result
    }

    fun flushPendingSyncQueue() {
        if (!_isOnline.value) return
        val now = System.currentTimeMillis()
        _syncEnvelopes.value = _syncEnvelopes.value.map { env ->
            if (env.ackedAtMillis == null) env.copy(ackedAtMillis = now) else env
        }
    }

    private fun mutateState(transform: (ControlRulesState) -> ControlRulesState) {
        val now = System.currentTimeMillis()
        val current = _rulesState.value
        val nextSeq = maxOf(current.version, lastAcceptedSequence) + 1L
        val updated = transform(current).copy(
            version = nextSeq,
            updatedAtMillis = now
        )
        lastAcceptedSequence = nextSeq
        _rulesState.value = updated
        persistState(updated)

        val envId = "env-$nextSeq-${UUID.randomUUID().toString().take(6)}"
        val envelope = RuleSyncValidator.createEnvelope(
            envelopeId = envId,
            sequenceNumber = nextSeq,
            timestampMillis = now,
            state = updated
        ).let { env ->
            if (_isOnline.value) env.copy(ackedAtMillis = now) else env
        }
        processedEnvelopeIds.add(envId)
        _syncEnvelopes.value = (_syncEnvelopes.value + envelope).takeLast(30)
    }

    private fun persistState(state: ControlRulesState) {
        val sp = prefs ?: return
        sp.edit()
            .putString(KEY_RULES_JSON, serializeState(state))
            .putLong(KEY_LAST_SEQ, lastAcceptedSequence)
            .putStringSet(KEY_PROCESSED_IDS, processedEnvelopeIds.take(100).toSet())
            .apply()
    }

    fun serializeState(state: ControlRulesState): String {
        val root = JSONObject()
        root.put("deviceId", state.deviceId)
        root.put("version", state.version)
        root.put("updatedAtMillis", state.updatedAtMillis)
        root.put("dailyDeviceLimitMinutes", state.dailyDeviceLimitMinutes)
        root.put("isDevicePaused", state.isDevicePaused)
        root.put("dailyLimitDays", JSONArray(state.dailyLimitDays.map { it.name }))

        val appsArr = JSONArray()
        state.appLimits.values.forEach { r ->
            val obj = JSONObject()
            obj.put("packageName", r.packageName)
            obj.put("appName", r.appName)
            obj.put("dailyLimitMinutes", r.dailyLimitMinutes)
            obj.put("isBlocked", r.isBlocked)
            obj.put("enabled", r.enabled)
            obj.put("daysOfWeek", JSONArray(r.daysOfWeek.map { it.name }))
            r.allowedWindow?.let { w ->
                obj.put("windowStart", w.startTime)
                obj.put("windowEnd", w.endTime)
            }
            appsArr.put(obj)
        }
        root.put("appLimits", appsArr)

        val catsArr = JSONArray()
        state.categoryLimits.values.forEach { c ->
            val obj = JSONObject()
            obj.put("category", c.category.name)
            obj.put("dailyLimitMinutes", c.dailyLimitMinutes)
            obj.put("enabled", c.enabled)
            obj.put("daysOfWeek", JSONArray(c.daysOfWeek.map { it.name }))
            catsArr.put(obj)
        }
        root.put("categoryLimits", catsArr)

        val routinesArr = JSONArray()
        state.routines.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("type", r.type.name)
            obj.put("startTime", r.startTime)
            obj.put("endTime", r.endTime)
            obj.put("blockAllExceptAllowed", r.blockAllExceptAllowed)
            obj.put("enabled", r.enabled)
            obj.put("daysOfWeek", JSONArray(r.daysOfWeek.map { it.name }))
            obj.put("allowedPackages", JSONArray(r.allowedPackages.toList()))
            obj.put("blockedPackages", JSONArray(r.blockedPackages.toList()))
            routinesArr.put(obj)
        }
        root.put("routines", routinesArr)

        root.put("extraGrants", serializeGrants(state.extraGrants))
        root.put("grantHistory", serializeGrants(state.grantHistory))
        return root.toString()
    }

    private fun serializeGrants(grants: List<ExtraTimeGrant>): JSONArray {
        val arr = JSONArray()
        grants.forEach { g ->
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("targetPackage", g.targetPackage ?: JSONObject.NULL)
            obj.put("targetAppName", g.targetAppName ?: JSONObject.NULL)
            obj.put("minutes", g.minutes)
            obj.put("grantedAtMillis", g.grantedAtMillis)
            obj.put("expiresAtMillis", g.expiresAtMillis)
            obj.put("grantedBy", g.grantedBy)
            obj.put("reason", g.reason)
            arr.put(obj)
        }
        return arr
    }

    fun deserializeState(raw: String): ControlRulesState? {
        return try {
            val root = JSONObject(raw)
            val dailyDays = parseWeekdays(root.optJSONArray("dailyLimitDays"))

            val appLimits = mutableMapOf<String, AppLimitRule>()
            val appsArr = root.optJSONArray("appLimits") ?: JSONArray()
            for (i in 0 until appsArr.length()) {
                val obj = appsArr.getJSONObject(i)
                val pkg = obj.getString("packageName")
                val ws = obj.optString("windowStart", "")
                val we = obj.optString("windowEnd", "")
                val window = if (ws.isNotBlank() && we.isNotBlank()) TimeWindow(ws, we) else null
                appLimits[pkg] = AppLimitRule(
                    packageName = pkg,
                    appName = obj.optString("appName", pkg),
                    dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 60),
                    isBlocked = obj.optBoolean("isBlocked", false),
                    allowedWindow = window,
                    daysOfWeek = parseWeekdays(obj.optJSONArray("daysOfWeek")),
                    enabled = obj.optBoolean("enabled", true)
                )
            }

            val categoryLimits = mutableMapOf<AppCategory, CategoryLimitRule>()
            val catsArr = root.optJSONArray("categoryLimits") ?: JSONArray()
            for (i in 0 until catsArr.length()) {
                val obj = catsArr.getJSONObject(i)
                val cat = AppCategory.valueOf(obj.getString("category"))
                categoryLimits[cat] = CategoryLimitRule(
                    category = cat,
                    dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 60),
                    daysOfWeek = parseWeekdays(obj.optJSONArray("daysOfWeek")),
                    enabled = obj.optBoolean("enabled", true)
                )
            }

            val routines = mutableListOf<RoutineRule>()
            val routinesArr = root.optJSONArray("routines") ?: JSONArray()
            for (i in 0 until routinesArr.length()) {
                val obj = routinesArr.getJSONObject(i)
                routines.add(
                    RoutineRule(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = RoutineType.valueOf(obj.getString("type")),
                        startTime = obj.getString("startTime"),
                        endTime = obj.getString("endTime"),
                        daysOfWeek = parseWeekdays(obj.optJSONArray("daysOfWeek")),
                        allowedPackages = parseStringSet(obj.optJSONArray("allowedPackages")),
                        blockedPackages = parseStringSet(obj.optJSONArray("blockedPackages")),
                        blockAllExceptAllowed = obj.optBoolean("blockAllExceptAllowed", true),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }

            ControlRulesState(
                deviceId = root.optString("deviceId", "tablet-lucas-01"),
                version = root.optLong("version", 1L),
                updatedAtMillis = root.optLong("updatedAtMillis", System.currentTimeMillis()),
                dailyDeviceLimitMinutes = root.optInt("dailyDeviceLimitMinutes", 210),
                dailyLimitDays = dailyDays,
                isDevicePaused = root.optBoolean("isDevicePaused", false),
                appLimits = appLimits,
                categoryLimits = categoryLimits,
                routines = routines,
                extraGrants = parseGrants(root.optJSONArray("extraGrants")),
                grantHistory = parseGrants(root.optJSONArray("grantHistory"))
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWeekdays(arr: JSONArray?): Set<Weekday> {
        if (arr == null || arr.length() == 0) return Weekday.ALL_DAYS
        val set = mutableSetOf<Weekday>()
        for (i in 0 until arr.length()) {
            runCatching { Weekday.valueOf(arr.getString(i)) }.getOrNull()?.let { set.add(it) }
        }
        return if (set.isEmpty()) Weekday.ALL_DAYS else set
    }

    private fun parseStringSet(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        val set = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
        return set
    }

    private fun parseGrants(arr: JSONArray?): List<ExtraTimeGrant> {
        if (arr == null) return emptyList()
        val list = mutableListOf<ExtraTimeGrant>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val pkg = if (obj.isNull("targetPackage")) null else obj.optString("targetPackage")
            val appName = if (obj.isNull("targetAppName")) null else obj.optString("targetAppName")
            list.add(
                ExtraTimeGrant(
                    id = obj.getString("id"),
                    targetPackage = pkg,
                    targetAppName = appName,
                    minutes = obj.getInt("minutes"),
                    grantedAtMillis = obj.getLong("grantedAtMillis"),
                    expiresAtMillis = obj.getLong("expiresAtMillis"),
                    grantedBy = obj.optString("grantedBy", "Responsável"),
                    reason = obj.optString("reason", "Tempo adicional")
                )
            )
        }
        return list
    }
}
