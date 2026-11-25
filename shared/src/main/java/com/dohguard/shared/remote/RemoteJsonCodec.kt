package com.dohguard.shared.remote

import com.dohguard.shared.model.*
import com.dohguard.shared.sync.ControlRulesRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Codec JSON usando org.json (nativo Android) para serialização de comandos,
 * confirmações (ACK), envelopes de regras e payloads de telemetria.
 */
object RemoteJsonCodec {

    fun commandToJson(cmd: RemoteCommand): JSONObject {
        val obj = JSONObject()
        obj.put("commandId", cmd.commandId)
        obj.put("targetDeviceId", cmd.targetDeviceId)
        obj.put("issuedByParentId", cmd.issuedByParentId)
        obj.put("type", cmd.type.name)
        obj.put("createdAtMillis", cmd.createdAtMillis)
        obj.put("expiresAtMillis", cmd.expiresAtMillis)
        obj.put("sequenceNumber", cmd.sequenceNumber)
        obj.put("nonce", cmd.nonce)
        obj.put("signature", cmd.signature)
        obj.put("status", cmd.status.name)
        cmd.sentAtMillis?.let { obj.put("sentAtMillis", it) }
        cmd.receivedAtMillis?.let { obj.put("receivedAtMillis", it) }
        cmd.executedAtMillis?.let { obj.put("executedAtMillis", it) }
        cmd.resultMessage?.let { obj.put("resultMessage", it) }
        cmd.errorMessage?.let { obj.put("errorMessage", it) }

        val payloadObj = JSONObject()
        cmd.payload.forEach { (k, v) -> payloadObj.put(k, v) }
        obj.put("payload", payloadObj)
        return obj
    }

    fun commandFromJson(obj: JSONObject): RemoteCommand? {
        return try {
            val payloadMap = mutableMapOf<String, String>()
            val payloadObj = obj.optJSONObject("payload")
            if (payloadObj != null) {
                val keys = payloadObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    payloadMap[key] = payloadObj.optString(key, "")
                }
            }
            RemoteCommand(
                commandId = obj.getString("commandId"),
                targetDeviceId = obj.getString("targetDeviceId"),
                issuedByParentId = obj.getString("issuedByParentId"),
                type = CommandType.valueOf(obj.getString("type")),
                payload = payloadMap,
                createdAtMillis = obj.getLong("createdAtMillis"),
                expiresAtMillis = obj.getLong("expiresAtMillis"),
                sequenceNumber = obj.optLong("sequenceNumber", 0L),
                nonce = obj.getString("nonce"),
                signature = obj.optString("signature", ""),
                status = runCatching { CommandStatus.valueOf(obj.optString("status", "PENDING")) }
                    .getOrDefault(CommandStatus.PENDING),
                sentAtMillis = if (obj.has("sentAtMillis") && !obj.isNull("sentAtMillis")) obj.getLong("sentAtMillis") else null,
                receivedAtMillis = if (obj.has("receivedAtMillis") && !obj.isNull("receivedAtMillis")) obj.getLong("receivedAtMillis") else null,
                executedAtMillis = if (obj.has("executedAtMillis") && !obj.isNull("executedAtMillis")) obj.getLong("executedAtMillis") else null,
                resultMessage = if (obj.has("resultMessage") && !obj.isNull("resultMessage")) obj.optString("resultMessage") else null,
                errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.optString("errorMessage") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    fun commandsToJsonString(list: List<RemoteCommand>): String {
        val arr = JSONArray()
        list.forEach { arr.put(commandToJson(it)) }
        return arr.toString()
    }

    fun commandsFromJsonString(raw: String): List<RemoteCommand> {
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<RemoteCommand>()
            for (i in 0 until arr.length()) {
                commandFromJson(arr.getJSONObject(i))?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun ackToJson(ack: CommandAck): JSONObject {
        val obj = JSONObject()
        obj.put("commandId", ack.commandId)
        obj.put("deviceId", ack.deviceId)
        obj.put("status", ack.status.name)
        obj.put("timestampMillis", ack.timestampMillis)
        ack.resultMessage?.let { obj.put("resultMessage", it) }
        ack.errorMessage?.let { obj.put("errorMessage", it) }
        obj.put("signature", ack.signature)
        return obj
    }

    fun ackFromJson(obj: JSONObject): CommandAck? {
        return try {
            CommandAck(
                commandId = obj.getString("commandId"),
                deviceId = obj.getString("deviceId"),
                status = CommandStatus.valueOf(obj.getString("status")),
                timestampMillis = obj.getLong("timestampMillis"),
                resultMessage = if (obj.has("resultMessage") && !obj.isNull("resultMessage")) obj.optString("resultMessage") else null,
                errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.optString("errorMessage") else null,
                signature = obj.optString("signature", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun envelopeToJson(envelope: RuleSyncEnvelope): JSONObject {
        val obj = JSONObject()
        obj.put("envelopeId", envelope.envelopeId)
        obj.put("deviceId", envelope.deviceId)
        obj.put("sequenceNumber", envelope.sequenceNumber)
        obj.put("timestampMillis", envelope.timestampMillis)
        obj.put("checksum", envelope.checksum)
        envelope.ackedAtMillis?.let { obj.put("ackedAtMillis", it) }
        obj.put("state", JSONObject(ControlRulesRepository.serializeState(envelope.state)))
        return obj
    }

    fun envelopeFromJson(obj: JSONObject): RuleSyncEnvelope? {
        return try {
            val stateJson = obj.getJSONObject("state").toString()
            val state = ControlRulesRepository.deserializeState(stateJson) ?: return null
            RuleSyncEnvelope(
                envelopeId = obj.getString("envelopeId"),
                deviceId = obj.getString("deviceId"),
                sequenceNumber = obj.getLong("sequenceNumber"),
                timestampMillis = obj.getLong("timestampMillis"),
                checksum = obj.getString("checksum"),
                state = state,
                ackedAtMillis = if (obj.has("ackedAtMillis") && !obj.isNull("ackedAtMillis")) obj.getLong("ackedAtMillis") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    fun telemetryToJson(payload: TelemetryPayload): JSONObject {
        val obj = JSONObject()
        obj.put("deviceId", payload.deviceId)
        obj.put("deviceName", payload.deviceName)
        obj.put("timestamp", payload.timestamp)
        obj.put("isPermissionGranted", payload.isPermissionGranted)
        val appsArr = JSONArray()
        payload.apps.forEach { app ->
            val a = JSONObject()
            a.put("packageName", app.packageName)
            a.put("appName", app.appName)
            a.put("category", app.category.name)
            a.put("usedMinutesToday", app.usedMinutesToday)
            a.put("usedMinutesYesterday", app.usedMinutesYesterday)
            a.put("dailyLimitMinutes", app.dailyLimitMinutes)
            a.put("isBlocked", app.isBlocked)
            a.put("iconIdentifier", app.iconIdentifier)
            a.put("lastTimeUsedTimestamp", app.lastTimeUsedTimestamp)
            a.put("lastTimeUsedFormatted", app.lastTimeUsedFormatted)
            a.put("isSystemApp", app.isSystemApp)
            a.put("isMonitored", app.isMonitored)
            a.put("authorizationState", app.authorizationState.name)
            appsArr.put(a)
        }
        payload.currentForegroundPackage?.let { obj.put("currentForegroundPackage", it) }
        payload.currentForegroundAppName?.let { obj.put("currentForegroundAppName", it) }
        payload.currentForegroundStartedAtMillis?.let { obj.put("currentForegroundStartedAtMillis", it) }
        obj.put("apps", appsArr)
        return obj
    }

    fun telemetryFromJson(obj: JSONObject): TelemetryPayload? {
        return try {
            val deviceId = obj.getString("deviceId")
            val deviceName = obj.optString("deviceName", "Tablet do Lucas")
            val ts = obj.getLong("timestamp")
            val perm = obj.optBoolean("isPermissionGranted", true)
            val fgPkg = obj.optString("currentForegroundPackage", "").takeIf { it.isNotBlank() }
            val fgName = obj.optString("currentForegroundAppName", "").takeIf { it.isNotBlank() }
            val fgStarted = if (obj.has("currentForegroundStartedAtMillis") && !obj.isNull("currentForegroundStartedAtMillis")) {
                obj.optLong("currentForegroundStartedAtMillis", 0L).takeIf { it > 0L }
            } else null
            val appsArr = obj.optJSONArray("apps") ?: JSONArray()
            val apps = mutableListOf<AppUsageInfo>()
            for (i in 0 until appsArr.length()) {
                val a = appsArr.getJSONObject(i)
                apps.add(
                    AppUsageInfo(
                        packageName = a.getString("packageName"),
                        appName = a.optString("appName", a.getString("packageName")),
                        category = runCatching { AppCategory.valueOf(a.optString("category", "OTHER")) }
                            .getOrDefault(AppCategory.OTHER),
                        usedMinutesToday = a.optInt("usedMinutesToday", 0),
                        usedMinutesYesterday = a.optInt("usedMinutesYesterday", 0),
                        dailyLimitMinutes = a.optInt("dailyLimitMinutes", 60),
                        isBlocked = a.optBoolean("isBlocked", false),
                        iconIdentifier = a.optString("iconIdentifier", "default"),
                        lastTimeUsedTimestamp = a.optLong("lastTimeUsedTimestamp", ts),
                        lastTimeUsedFormatted = a.optString("lastTimeUsedFormatted", "Hoje"),
                        isSystemApp = a.optBoolean("isSystemApp", false),
                        isMonitored = a.optBoolean("isMonitored", true),
                        authorizationState = runCatching {
                            AppAuthorizationState.valueOf(a.optString("authorizationState", "ALLOWED"))
                        }.getOrDefault(AppAuthorizationState.ALLOWED)
                    )
                )
            }
            val summary = UsageSummary.fromAppsList(
                apps = apps,
                lastSyncTimestamp = ts,
                deviceId = deviceId,
                deviceName = deviceName,
                isDemoData = false
            )
            TelemetryPayload(
                deviceId = deviceId,
                deviceName = deviceName,
                timestamp = ts,
                isPermissionGranted = perm,
                summary = summary,
                apps = apps,
                currentForegroundPackage = fgPkg,
                currentForegroundAppName = fgName,
                currentForegroundStartedAtMillis = fgStarted
            )
        } catch (_: Exception) {
            null
        }
    }
}
