package com.example.telecom

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.model.CallAudioEndpointItem
import com.example.model.CallAudioEndpointType
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot of the resolved Telecom call audio endpoints and active routing state.
 */
data class CallAudioRoutingSnapshot(
    val activeEndpoint: CallAudioEndpointItem,
    val availableEndpoints: List<CallAudioEndpointItem>,
    val pendingEndpointId: String? = null,
    val currentLegacyRouteMask: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedLegacyRouteMask: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
) {
    val isSpeakerActive: Boolean
        get() = activeEndpoint.type == CallAudioEndpointType.SPEAKER

    val isBluetoothActive: Boolean
        get() = activeEndpoint.type == CallAudioEndpointType.BLUETOOTH

    val isBluetoothAvailable: Boolean
        get() = availableEndpoints.any { it.type == CallAudioEndpointType.BLUETOOTH }
}

/**
 * Pure, testable Telecom Audio Endpoint Coordinator & Router.
 *
 * Responsibilities:
 * 1. Uses Android 14+ (API 34+) [CallEndpoint] and [InCallService.requestCallEndpointChange]
 *    when available, with full compatibility for [CallAudioState], [InCallService.requestBluetoothAudio],
 *    and [InCallService.setAudioRoute] on API 24–33.
 * 2. Dynamically detects all currently available endpoints:
 *    - Bluetooth audio devices (SCO / BLE / Hearing Aid) with device names
 *    - Phone loudspeaker
 *    - Earpiece
 *    - Wired headset (when connected)
 * 3. Preserves Bluetooth endpoint availability when switching to Speaker or Earpiece so the
 *    user can return to Bluetooth earbuds at any time during an active call.
 * 4. Updates the selected endpoint and UI state ONLY after Android Telecom confirms the routing
 *    change succeeds.
 * 5. Handles Bluetooth disconnections (immediately removing disconnected devices and selecting
 *    a valid available fallback endpoint) and reconnections during a call.
 */
class CallAudioEndpointRouter(
    private val onStateChanged: (CallAudioRoutingSnapshot) -> Unit,
    private val onRoutingError: (String) -> Unit
) {

    companion object {
        const val ID_EARPIECE = "endpoint_earpiece"
        const val ID_SPEAKER = "endpoint_speaker"
        const val ID_WIRED_HEADSET = "endpoint_wired_headset"
        const val ID_BLUETOOTH_DEFAULT = "endpoint_bluetooth_default"
        const val ID_STREAMING = "endpoint_streaming"

        fun bluetoothEndpointId(addressOrKey: String?): String {
            val clean = addressOrKey?.trim().orEmpty()
            return if (clean.isNotEmpty()) "endpoint_bt_$clean" else ID_BLUETOOTH_DEFAULT
        }
    }

    // Android 14+ (API 34+) platform CallEndpoint state delivered by InCallService
    private val platformEndpointsById = LinkedHashMap<String, Any>()
    private var activePlatformEndpointId: String? = null
    private var hasReceivedPlatformEndpoints: Boolean = false

    // Compatibility CallAudioState (API 24–33 or fallback)
    private var currentLegacyRoute: Int = CallAudioState.ROUTE_EARPIECE
    private var supportedLegacyRouteMask: Int =
        CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
    private val connectedBluetoothDevicesById = ConcurrentHashMap<String, BluetoothDevice>()
    private val connectedBluetoothNamesById = ConcurrentHashMap<String, String>()
    private var activeBluetoothEndpointId: String? = null
    private var externalBluetoothProductName: String? = null
    private var externalWiredHeadsetProductName: String? = null

    // Tracks the previously active non-speaker endpoint so toggling Speaker OFF can return to it
    private var lastNonSpeakerEndpointId: String? = null

    // Tracks an in-flight endpoint switch request; activeEndpoint only updates upon confirmation
    private var pendingEndpointId: String? = null

    // Current computed snapshot
    private var currentSnapshot: CallAudioRoutingSnapshot = buildDefaultSnapshot()

    fun getSnapshot(): CallAudioRoutingSnapshot = currentSnapshot

    fun reset() {
        platformEndpointsById.clear()
        activePlatformEndpointId = null
        hasReceivedPlatformEndpoints = false
        currentLegacyRoute = CallAudioState.ROUTE_EARPIECE
        supportedLegacyRouteMask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
        connectedBluetoothDevicesById.clear()
        connectedBluetoothNamesById.clear()
        activeBluetoothEndpointId = null
        lastNonSpeakerEndpointId = null
        pendingEndpointId = null
        currentSnapshot = buildDefaultSnapshot()
    }

    /**
     * Refreshes product names of connected audio output peripherals (Bluetooth / Wired Headset)
     * from [AudioManager.getDevices] for display labels without forcing or altering audio routing.
     */
    fun refreshConnectedAudioDeviceNames(context: Context?) {
        if (context == null) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            var btName: String? = null
            var wiredName: String? = null
            for (device in outputs) {
                val productName = device.productName?.toString()?.trim().orEmpty()
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_HEARING_AID -> {
                        if (productName.isNotEmpty() && !productName.equals(Build.MODEL, ignoreCase = true)) {
                            btName = productName
                        }
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        if (productName.isNotEmpty() && !productName.equals(Build.MODEL, ignoreCase = true)) {
                            wiredName = productName
                        }
                    }
                }
            }
            externalBluetoothProductName = btName
            externalWiredHeadsetProductName = wiredName
        } catch (_: Throwable) {
        }
    }

    /**
     * Handles Android 14+ (API 34+) [InCallService.onAvailableCallEndpointsChanged].
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onPlatformAvailableEndpointsChanged(
        endpoints: List<CallEndpoint>,
        service: InCallService? = null
    ) {
        hasReceivedPlatformEndpoints = true
        platformEndpointsById.clear()
        for (ep in endpoints) {
            val id = ep.identifier.toString()
            platformEndpointsById[id] = ep
        }

        // Check if previously active endpoint (e.g., Bluetooth) was disconnected
        val currentActiveId = activePlatformEndpointId
        val activeStillAvailable = currentActiveId != null && platformEndpointsById.containsKey(currentActiveId)

        if (!activeStillAvailable && platformEndpointsById.isNotEmpty()) {
            // Active endpoint disconnected — select a valid available fallback endpoint
            val fallbackEp = selectFallbackPlatformEndpoint(endpoints)
            if (fallbackEp != null) {
                val fallbackId = fallbackEp.identifier.toString()
                if (service != null) {
                    requestPlatformEndpointSwitch(service, fallbackEp)
                } else {
                    activePlatformEndpointId = fallbackId
                }
            }
        }

        recomputeAndPublish()
    }

    /**
     * Handles Android 14+ (API 34+) [InCallService.onCallEndpointChanged].
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onPlatformCallEndpointChanged(endpoint: CallEndpoint) {
        hasReceivedPlatformEndpoints = true
        val id = endpoint.identifier.toString()
        platformEndpointsById[id] = endpoint
        activePlatformEndpointId = id
        pendingEndpointId = null

        val mappedType = mapPlatformType(endpoint.endpointType)
        if (mappedType != CallAudioEndpointType.SPEAKER) {
            lastNonSpeakerEndpointId = id
        }

        currentLegacyRoute = when (mappedType) {
            CallAudioEndpointType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallAudioEndpointType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallAudioEndpointType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            CallAudioEndpointType.STREAMING -> CallAudioState.ROUTE_STREAMING
            CallAudioEndpointType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        }

        recomputeAndPublish()
    }

    /**
     * Handles [InCallService.onCallAudioStateChanged] across all Android versions.
     * On API 34+, if platform [CallEndpoint] callbacks are already active, this keeps
     * legacy route masks synchronized without overwriting multi-endpoint [CallEndpoint] data.
     */
    @Suppress("DEPRECATION")
    fun onCallAudioStateChanged(
        context: Context?,
        audioState: CallAudioState?,
        service: InCallService? = null
    ) {
        if (audioState == null) return
        refreshConnectedAudioDeviceNames(context)

        val newRoute = audioState.route
        val newMask = audioState.supportedRouteMask

        // Extract connected Bluetooth devices from CallAudioState on API 26+
        val btSupportedInMask = (newMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        connectedBluetoothDevicesById.clear()
        connectedBluetoothNamesById.clear()

        if (btSupportedInMask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val supportedBt = audioState.supportedBluetoothDevices.orEmpty()
                for (btDevice in supportedBt) {
                    val address = try {
                        btDevice.address
                    } catch (_: SecurityException) {
                        null
                    }
                    val id = bluetoothEndpointId(address)
                    connectedBluetoothDevicesById[id] = btDevice
                    val resolvedName = resolveBluetoothDeviceName(context, btDevice)
                    connectedBluetoothNamesById[id] = resolvedName
                }

                val activeBt = try {
                    audioState.activeBluetoothDevice
                } catch (_: Throwable) {
                    null
                }
                if (activeBt != null) {
                    val activeAddr = try {
                        activeBt.address
                    } catch (_: SecurityException) {
                        null
                    }
                    val activeId = bluetoothEndpointId(activeAddr)
                    connectedBluetoothDevicesById[activeId] = activeBt
                    connectedBluetoothNamesById[activeId] = resolveBluetoothDeviceName(context, activeBt)
                    activeBluetoothEndpointId = activeId
                } else if (activeBluetoothEndpointId == null ||
                    !connectedBluetoothDevicesById.containsKey(activeBluetoothEndpointId)
                ) {
                    activeBluetoothEndpointId = connectedBluetoothDevicesById.keys.firstOrNull()
                        ?: ID_BLUETOOTH_DEFAULT
                }
            } catch (_: Throwable) {
            }
        } else if (!btSupportedInMask) {
            // Never assume a Bluetooth device is still available if ROUTE_BLUETOOTH is no longer in supportedRouteMask
            activeBluetoothEndpointId = null
        }

        val wasBluetoothActive = currentLegacyRoute == CallAudioState.ROUTE_BLUETOOTH
        currentLegacyRoute = newRoute
        supportedLegacyRouteMask = newMask
        pendingEndpointId = null

        // Track last non-speaker endpoint so user or notification toggle can return to Bluetooth or Earpiece
        when (newRoute) {
            CallAudioState.ROUTE_BLUETOOTH -> {
                lastNonSpeakerEndpointId = activeBluetoothEndpointId
                    ?: connectedBluetoothNamesById.keys.firstOrNull()
                    ?: ID_BLUETOOTH_DEFAULT
            }
            CallAudioState.ROUTE_WIRED_HEADSET -> {
                lastNonSpeakerEndpointId = ID_WIRED_HEADSET
            }
            CallAudioState.ROUTE_EARPIECE -> {
                if (lastNonSpeakerEndpointId == null) {
                    lastNonSpeakerEndpointId = ID_EARPIECE
                }
            }
        }

        // If Bluetooth disconnected while active and Telecom hasn't yet switched away from ROUTE_BLUETOOTH,
        // automatically select a valid available fallback route.
        if (wasBluetoothActive && !btSupportedInMask && newRoute == CallAudioState.ROUTE_BLUETOOTH) {
            val fallbackRoute = when {
                (newMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                (newMask and CallAudioState.ROUTE_EARPIECE) != 0 -> CallAudioState.ROUTE_EARPIECE
                (newMask and CallAudioState.ROUTE_SPEAKER) != 0 -> CallAudioState.ROUTE_SPEAKER
                else -> CallAudioState.ROUTE_EARPIECE
            }
            if (service != null) {
                try {
                    service.setAudioRoute(fallbackRoute)
                } catch (_: Throwable) {
                }
            } else {
                currentLegacyRoute = fallbackRoute
            }
        }

        recomputeAndPublish()
    }

    /**
     * Pure/direct state update method for deterministic testing and non-platform state synchronization.
     * Replaces available endpoints and updates active endpoint only when confirmed.
     */
    fun applyAvailableAndActiveEndpoints(
        available: List<CallAudioEndpointItem>,
        confirmedActiveEndpointId: String?,
        onFallbackRequested: ((CallAudioEndpointItem) -> Unit)? = null
    ) {
        val validAvailable = if (available.isEmpty()) {
            listOf(
                CallAudioEndpointItem(
                    id = ID_EARPIECE,
                    name = "Phone earpiece",
                    type = CallAudioEndpointType.EARPIECE,
                    isActive = true
                ),
                CallAudioEndpointItem(
                    id = ID_SPEAKER,
                    name = "Speaker",
                    type = CallAudioEndpointType.SPEAKER,
                    isActive = false
                )
            )
        } else {
            available
        }

        val matchingActive = validAvailable.firstOrNull { it.id == confirmedActiveEndpointId }
        val resolvedActive = if (matchingActive != null) {
            matchingActive
        } else {
            // Active endpoint (such as Bluetooth) disconnected — automatically select a valid available fallback
            val fallback = validAvailable.firstOrNull { it.type == CallAudioEndpointType.WIRED_HEADSET }
                ?: validAvailable.firstOrNull { it.type == CallAudioEndpointType.EARPIECE }
                ?: validAvailable.firstOrNull { it.type == CallAudioEndpointType.SPEAKER }
                ?: validAvailable.first()
            onFallbackRequested?.invoke(fallback)
            fallback
        }

        if (resolvedActive.type != CallAudioEndpointType.SPEAKER) {
            lastNonSpeakerEndpointId = resolvedActive.id
        }

        pendingEndpointId = null
        val updatedList = validAvailable.map { item ->
            item.copy(isActive = item.id == resolvedActive.id)
        }
        val activeItem = updatedList.first { it.id == resolvedActive.id }

        var mask = 0
        for (ep in updatedList) {
            mask = mask or when (ep.type) {
                CallAudioEndpointType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
                CallAudioEndpointType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                CallAudioEndpointType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                CallAudioEndpointType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
                CallAudioEndpointType.STREAMING -> CallAudioState.ROUTE_STREAMING
            }
        }
        val activeRoute = when (activeItem.type) {
            CallAudioEndpointType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            CallAudioEndpointType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallAudioEndpointType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallAudioEndpointType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            CallAudioEndpointType.STREAMING -> CallAudioState.ROUTE_STREAMING
        }

        currentLegacyRoute = activeRoute
        supportedLegacyRouteMask = mask
        currentSnapshot = CallAudioRoutingSnapshot(
            activeEndpoint = activeItem,
            availableEndpoints = updatedList,
            pendingEndpointId = null,
            currentLegacyRouteMask = activeRoute,
            supportedLegacyRouteMask = mask
        )
        onStateChanged(currentSnapshot)
    }

    /**
     * Requests Android Telecom to switch call audio to the specified [targetEndpoint].
     *
     * CRITICAL: Does NOT mutate [currentSnapshot.activeEndpoint] before Telecom confirms the
     * routing request succeeded!
     */
    fun requestEndpointChange(
        service: InCallService?,
        targetEndpoint: CallAudioEndpointItem,
        customDispatcher: ((CallAudioEndpointItem, (Boolean) -> Unit) -> Unit)? = null
    ): Boolean {
        val available = currentSnapshot.availableEndpoints
        val verifiedTarget = available.firstOrNull { it.id == targetEndpoint.id }
            ?: available.firstOrNull { it.type == targetEndpoint.type }

        if (verifiedTarget == null) {
            onRoutingError("${targetEndpoint.name} is not currently available.")
            return false
        }

        if (verifiedTarget.id == currentSnapshot.activeEndpoint.id && pendingEndpointId == null) {
            return true
        }

        // Record pending endpoint without changing activeEndpoint until confirmed
        pendingEndpointId = verifiedTarget.id
        currentSnapshot = currentSnapshot.copy(pendingEndpointId = verifiedTarget.id)
        onStateChanged(currentSnapshot)

        // Allow unit-test / custom dispatcher injection to verify pre-confirmation vs post-confirmation state
        if (customDispatcher != null) {
            customDispatcher(verifiedTarget) { succeeded ->
                if (succeeded) {
                    applyAvailableAndActiveEndpoints(
                        available = currentSnapshot.availableEndpoints,
                        confirmedActiveEndpointId = verifiedTarget.id
                    )
                } else {
                    pendingEndpointId = null
                    currentSnapshot = currentSnapshot.copy(pendingEndpointId = null)
                    onStateChanged(currentSnapshot)
                    onRoutingError("Could not switch audio to ${verifiedTarget.name}.")
                }
            }
            return true
        }

        if (service == null) {
            pendingEndpointId = null
            currentSnapshot = currentSnapshot.copy(pendingEndpointId = null)
            onStateChanged(currentSnapshot)
            onRoutingError("Audio control unavailable outside active Telecom session.")
            return false
        }

        // 1. Android 14+ (API 34+) CallEndpoint API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasReceivedPlatformEndpoints &&
            platformEndpointsById.isNotEmpty()
        ) {
            val platformObj = (platformEndpointsById[verifiedTarget.id] as? CallEndpoint)
                ?: platformEndpointsById.values.filterIsInstance<CallEndpoint>().firstOrNull {
                    mapPlatformType(it.endpointType) == verifiedTarget.type
                }
            if (platformObj != null) {
                return requestPlatformEndpointSwitch(service, platformObj)
            }
        }

        // 2. Compatibility API (API 24–33 or when platform CallEndpoints are not populated)
        return requestLegacyAudioRouteSwitch(service, verifiedTarget)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestPlatformEndpointSwitch(
        service: InCallService,
        platformEndpoint: CallEndpoint
    ): Boolean {
        val targetId = platformEndpoint.identifier.toString()
        pendingEndpointId = targetId
        return try {
            service.requestCallEndpointChange(
                platformEndpoint,
                ContextCompat.getMainExecutor(service),
                object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) {
                        onPlatformCallEndpointChanged(platformEndpoint)
                    }

                    override fun onError(error: CallEndpointException) {
                        pendingEndpointId = null
                        recomputeAndPublish()
                        onRoutingError("Could not switch audio to ${platformEndpoint.endpointName}.")
                    }
                }
            )
            true
        } catch (_: Throwable) {
            pendingEndpointId = null
            recomputeAndPublish()
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun requestLegacyAudioRouteSwitch(
        service: InCallService,
        target: CallAudioEndpointItem
    ): Boolean {
        return try {
            when (target.type) {
                CallAudioEndpointType.BLUETOOTH -> {
                    if ((supportedLegacyRouteMask and CallAudioState.ROUTE_BLUETOOTH) == 0 &&
                        connectedBluetoothDevicesById.isEmpty()
                    ) {
                        pendingEndpointId = null
                        recomputeAndPublish()
                        onRoutingError("No connected Bluetooth audio device is available.")
                        return false
                    }
                    val specificBtDevice = connectedBluetoothDevicesById[target.id]
                        ?: connectedBluetoothDevicesById.values.firstOrNull()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && specificBtDevice != null) {
                        try {
                            service.requestBluetoothAudio(specificBtDevice)
                        } catch (_: Throwable) {
                        }
                    }
                    service.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                    true
                }
                CallAudioEndpointType.SPEAKER -> {
                    service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                    true
                }
                CallAudioEndpointType.WIRED_HEADSET -> {
                    service.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
                    true
                }
                CallAudioEndpointType.EARPIECE -> {
                    service.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                    true
                }
                CallAudioEndpointType.STREAMING -> {
                    service.setAudioRoute(CallAudioState.ROUTE_STREAMING)
                    true
                }
            }
        } catch (_: Throwable) {
            pendingEndpointId = null
            recomputeAndPublish()
            onRoutingError("Could not switch audio to ${target.name}.")
            false
        }
    }

    /**
     * Smart toggle used when Speaker action is triggered from notification or 2-endpoint state:
     * - If currently on Speaker: returns to the last active non-speaker endpoint (e.g. Bluetooth if still connected,
     *   otherwise Wired Headset or Earpiece).
     * - If not on Speaker: requests switching to Speaker.
     */
    fun toggleSpeakerSmart(
        service: InCallService?,
        customDispatcher: ((CallAudioEndpointItem, (Boolean) -> Unit) -> Unit)? = null
    ): Boolean {
        val snap = currentSnapshot
        val target = if (snap.isSpeakerActive) {
            val previousNonSpeaker = lastNonSpeakerEndpointId?.let { prevId ->
                snap.availableEndpoints.firstOrNull { it.id == prevId && it.type != CallAudioEndpointType.SPEAKER }
            }
            previousNonSpeaker
                ?: snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.BLUETOOTH }
                ?: snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.WIRED_HEADSET }
                ?: snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.EARPIECE }
                ?: return false
        } else {
            snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.SPEAKER }
                ?: return false
        }
        return requestEndpointChange(service, target, customDispatcher)
    }

    /**
     * Switches to Bluetooth if available and not currently active, or switches to Earpiece/Wired Headset
     * if currently on Bluetooth.
     */
    fun toggleBluetoothSmart(
        service: InCallService?,
        customDispatcher: ((CallAudioEndpointItem, (Boolean) -> Unit) -> Unit)? = null
    ): Boolean {
        val snap = currentSnapshot
        val btEndpoint = snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.BLUETOOTH }
        if (btEndpoint == null) {
            onRoutingError("No connected Bluetooth audio device detected.")
            return false
        }
        val target = if (snap.isBluetoothActive) {
            snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.WIRED_HEADSET }
                ?: snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.EARPIECE }
                ?: snap.availableEndpoints.firstOrNull { it.type == CallAudioEndpointType.SPEAKER }
                ?: return false
        } else {
            btEndpoint
        }
        return requestEndpointChange(service, target, customDispatcher)
    }

    private fun recomputeAndPublish() {
        val snapshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasReceivedPlatformEndpoints &&
            platformEndpointsById.isNotEmpty()
        ) {
            buildSnapshotFromPlatformEndpoints()
        } else {
            buildSnapshotFromLegacyAudioState()
        }
        currentSnapshot = snapshot
        onStateChanged(snapshot)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun buildSnapshotFromPlatformEndpoints(): CallAudioRoutingSnapshot {
        val rawEndpoints = platformEndpointsById.values.filterIsInstance<CallEndpoint>()
        val items = ArrayList<CallAudioEndpointItem>(rawEndpoints.size)
        var mask = 0

        for (ep in rawEndpoints) {
            val id = ep.identifier.toString()
            val type = mapPlatformType(ep.endpointType)
            val rawName = ep.endpointName.toString().trim()
            val friendlyName = when (type) {
                CallAudioEndpointType.BLUETOOTH -> {
                    if (rawName.isNotEmpty() && !rawName.equals("Bluetooth", ignoreCase = true)) {
                        rawName
                    } else {
                        externalBluetoothProductName?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                    }
                }
                CallAudioEndpointType.SPEAKER -> "Speaker"
                CallAudioEndpointType.WIRED_HEADSET -> {
                    externalWiredHeadsetProductName?.takeIf { it.isNotBlank() }
                        ?: rawName.ifEmpty { "Wired headset" }
                }
                CallAudioEndpointType.EARPIECE -> "Phone earpiece"
                CallAudioEndpointType.STREAMING -> rawName.ifEmpty { "Streaming audio" }
            }
            val isActive = id == activePlatformEndpointId
            items.add(
                CallAudioEndpointItem(
                    id = id,
                    name = friendlyName,
                    type = type,
                    isActive = isActive
                )
            )
            mask = mask or when (type) {
                CallAudioEndpointType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                CallAudioEndpointType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                CallAudioEndpointType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
                CallAudioEndpointType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
                CallAudioEndpointType.STREAMING -> CallAudioState.ROUTE_STREAMING
            }
        }

        // Sort in intuitive selector order: Bluetooth -> Speaker -> Phone earpiece -> Wired headset
        val sorted = sortEndpointsForSelector(items)
        val activeItem = sorted.firstOrNull { it.isActive }
            ?: sorted.firstOrNull { it.type == CallAudioEndpointType.EARPIECE }
            ?: sorted.firstOrNull()
            ?: CallAudioEndpointItem(
                id = ID_EARPIECE,
                name = "Phone earpiece",
                type = CallAudioEndpointType.EARPIECE,
                isActive = true
            )

        val finalSorted = sorted.map { it.copy(isActive = it.id == activeItem.id) }
        val activeRouteMask = when (activeItem.type) {
            CallAudioEndpointType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallAudioEndpointType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallAudioEndpointType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            CallAudioEndpointType.STREAMING -> CallAudioState.ROUTE_STREAMING
            CallAudioEndpointType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        }

        return CallAudioRoutingSnapshot(
            activeEndpoint = activeItem.copy(isActive = true),
            availableEndpoints = finalSorted,
            pendingEndpointId = pendingEndpointId,
            currentLegacyRouteMask = activeRouteMask,
            supportedLegacyRouteMask = if (mask != 0) mask else supportedLegacyRouteMask
        )
    }

    private fun buildSnapshotFromLegacyAudioState(): CallAudioRoutingSnapshot {
        val items = mutableListOf<CallAudioEndpointItem>()
        val mask = supportedLegacyRouteMask
        val route = currentLegacyRoute

        // 1. Bluetooth endpoints (preserved whenever supportedRouteMask includes ROUTE_BLUETOOTH)
        val btSupported = (mask and CallAudioState.ROUTE_BLUETOOTH) != 0
        if (btSupported) {
            if (connectedBluetoothNamesById.isNotEmpty()) {
                for ((btId, btName) in connectedBluetoothNamesById) {
                    val isThisBtActive = route == CallAudioState.ROUTE_BLUETOOTH &&
                        (activeBluetoothEndpointId == btId || connectedBluetoothNamesById.size == 1)
                    items.add(
                        CallAudioEndpointItem(
                            id = btId,
                            name = btName,
                            type = CallAudioEndpointType.BLUETOOTH,
                            isActive = isThisBtActive,
                            bluetoothAddress = btId.removePrefix("endpoint_bt_").takeIf { it != btId }
                        )
                    )
                }
            } else {
                val fallbackBtName = externalBluetoothProductName?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                items.add(
                    CallAudioEndpointItem(
                        id = ID_BLUETOOTH_DEFAULT,
                        name = fallbackBtName,
                        type = CallAudioEndpointType.BLUETOOTH,
                        isActive = route == CallAudioState.ROUTE_BLUETOOTH
                    )
                )
            }
        }

        // 2. Speaker endpoint
        if ((mask and CallAudioState.ROUTE_SPEAKER) != 0 || route == CallAudioState.ROUTE_SPEAKER) {
            items.add(
                CallAudioEndpointItem(
                    id = ID_SPEAKER,
                    name = "Speaker",
                    type = CallAudioEndpointType.SPEAKER,
                    isActive = route == CallAudioState.ROUTE_SPEAKER
                )
            )
        }

        // 3. Wired headset endpoint (when connected)
        val wiredConnected = (mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0 ||
            route == CallAudioState.ROUTE_WIRED_HEADSET
        if (wiredConnected) {
            val wiredName = externalWiredHeadsetProductName?.takeIf { it.isNotBlank() } ?: "Wired headset"
            items.add(
                CallAudioEndpointItem(
                    id = ID_WIRED_HEADSET,
                    name = wiredName,
                    type = CallAudioEndpointType.WIRED_HEADSET,
                    isActive = route == CallAudioState.ROUTE_WIRED_HEADSET
                )
            )
        }

        // 4. Phone earpiece endpoint
        // Note: Some Android versions omit ROUTE_EARPIECE from supportedRouteMask only while a wired
        // headset is physically plugged into the 3.5mm/USB jack, but keep it available whenever
        // Bluetooth or Speaker is used. Always include Earpiece when supported or when no wired headset replaces it.
        if ((mask and CallAudioState.ROUTE_EARPIECE) != 0 ||
            route == CallAudioState.ROUTE_EARPIECE ||
            !wiredConnected
        ) {
            items.add(
                CallAudioEndpointItem(
                    id = ID_EARPIECE,
                    name = "Phone earpiece",
                    type = CallAudioEndpointType.EARPIECE,
                    isActive = route == CallAudioState.ROUTE_EARPIECE
                )
            )
        }

        val sorted = sortEndpointsForSelector(items)
        val activeItem = sorted.firstOrNull { it.isActive }
            ?: sorted.firstOrNull { it.type == CallAudioEndpointType.EARPIECE }
            ?: sorted.first()
        val finalList = sorted.map { it.copy(isActive = it.id == activeItem.id) }

        return CallAudioRoutingSnapshot(
            activeEndpoint = activeItem.copy(isActive = true),
            availableEndpoints = finalList,
            pendingEndpointId = pendingEndpointId,
            currentLegacyRouteMask = route,
            supportedLegacyRouteMask = mask
        )
    }

    private fun sortEndpointsForSelector(list: List<CallAudioEndpointItem>): List<CallAudioEndpointItem> {
        return list.sortedWith(
            compareBy<CallAudioEndpointItem> {
                when (it.type) {
                    CallAudioEndpointType.BLUETOOTH -> 0
                    CallAudioEndpointType.SPEAKER -> 1
                    CallAudioEndpointType.WIRED_HEADSET -> 2
                    CallAudioEndpointType.EARPIECE -> 3
                    CallAudioEndpointType.STREAMING -> 4
                }
            }.thenBy { it.name }
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun selectFallbackPlatformEndpoint(endpoints: List<CallEndpoint>): CallEndpoint? {
        return endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET }
            ?: endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
            ?: endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
            ?: endpoints.firstOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun mapPlatformType(endpointType: Int): CallAudioEndpointType {
        return when (endpointType) {
            CallEndpoint.TYPE_BLUETOOTH -> CallAudioEndpointType.BLUETOOTH
            CallEndpoint.TYPE_SPEAKER -> CallAudioEndpointType.SPEAKER
            CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioEndpointType.WIRED_HEADSET
            CallEndpoint.TYPE_STREAMING -> CallAudioEndpointType.STREAMING
            CallEndpoint.TYPE_EARPIECE -> CallAudioEndpointType.EARPIECE
            else -> CallAudioEndpointType.EARPIECE
        }
    }

    private fun resolveBluetoothDeviceName(context: Context?, device: BluetoothDevice): String {
        val hasBtConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context != null && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (hasBtConnectPermission) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val alias = device.alias?.trim().orEmpty()
                    if (alias.isNotEmpty()) return alias
                }
                val name = device.name?.trim().orEmpty()
                if (name.isNotEmpty()) return name
            } catch (_: SecurityException) {
            } catch (_: Throwable) {
            }
        }
        return externalBluetoothProductName?.takeIf { it.isNotBlank() } ?: "Bluetooth"
    }

    private fun buildDefaultSnapshot(): CallAudioRoutingSnapshot {
        val earpiece = CallAudioEndpointItem(
            id = ID_EARPIECE,
            name = "Phone earpiece",
            type = CallAudioEndpointType.EARPIECE,
            isActive = true
        )
        val speaker = CallAudioEndpointItem(
            id = ID_SPEAKER,
            name = "Speaker",
            type = CallAudioEndpointType.SPEAKER,
            isActive = false
        )
        return CallAudioRoutingSnapshot(
            activeEndpoint = earpiece,
            availableEndpoints = listOf(speaker, earpiece),
            pendingEndpointId = null,
            currentLegacyRouteMask = CallAudioState.ROUTE_EARPIECE,
            supportedLegacyRouteMask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
        )
    }
}
