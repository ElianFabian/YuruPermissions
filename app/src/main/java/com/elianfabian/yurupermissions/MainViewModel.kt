package com.elianfabian.yurupermissions

import android.Manifest
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianfabian.yuru_permissions.Yuru
import com.elianfabian.yuru_permissions.YuruMultiplePermissionController
import com.elianfabian.yuru_permissions.YuruPermissionController
import com.elianfabian.yuru_permissions.YuruPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Represents the current state of all permissions in the example UI.
 */
data class MainUiState(
	val camera: YuruPermissionState = YuruPermissionState.NotDetermined,
	val contacts: YuruPermissionState = YuruPermissionState.NotDetermined,
	val notifications: YuruPermissionState = YuruPermissionState.NotDetermined,
	val location: Map<String, YuruPermissionState> = emptyMap(),
	val bluetooth: Map<String, YuruPermissionState> = emptyMap(),
	val storage: Map<String, YuruPermissionState> = emptyMap(),
	val pendingSettingsPermissionName: String? = null,
)

/**
 * Sealed interface representing all possible user actions in the UI.
 */
sealed interface MainUiAction {
	data object RequestCamera : MainUiAction
	data object RequestContacts : MainUiAction
	data object RequestNotifications : MainUiAction
	data object RequestLocation : MainUiAction
	data object RequestBluetooth : MainUiAction
	data object RequestStorage : MainUiAction
	data object OpenSettings : MainUiAction
	data object DismissDialog : MainUiAction
}

class MainViewModel : ViewModel() {

	private val yuru = Yuru.getInstance()

	// Controllers for single permissions
	private val cameraController = yuru.singlePermissionController(Manifest.permission.CAMERA)
	private val contactsController = yuru.singlePermissionController(Manifest.permission.READ_CONTACTS)

	// Notification permission only exists on API 33+
	private val notificationsController = if (Build.VERSION.SDK_INT >= 33) {
		yuru.singlePermissionController(Manifest.permission.POST_NOTIFICATIONS)
	}
	else null

	// Controllers for groups of permissions
	private val locationController = yuru.multiplePermissionController(
		Manifest.permission.ACCESS_FINE_LOCATION,
		Manifest.permission.ACCESS_COARSE_LOCATION,
	)

	// Bluetooth permissions changed significantly in API 31
	private val bluetoothController = yuru.multiplePermissionController(
		buildList {
			if (Build.VERSION.SDK_INT >= 31) {
				add(Manifest.permission.BLUETOOTH_SCAN)
				add(Manifest.permission.BLUETOOTH_CONNECT)
				add(Manifest.permission.BLUETOOTH_ADVERTISE)
			}
			else {
				add(Manifest.permission.ACCESS_FINE_LOCATION)
			}
		}
	)

	// Storage permissions changed to Media permissions in API 33
	private val storageController = yuru.multiplePermissionController(
		buildList {
			if (Build.VERSION.SDK_INT >= 33) {
				add(Manifest.permission.READ_MEDIA_IMAGES)
				add(Manifest.permission.READ_MEDIA_VIDEO)
				add(Manifest.permission.READ_MEDIA_AUDIO)
			}
			else {
				add(Manifest.permission.READ_EXTERNAL_STORAGE)
			}
		}
	)

	// Tracks which permission (if any) triggered a "Go to Settings" dialog
	private val _pendingSettingsPermissionName = MutableStateFlow<String?>(null)

	/**
	 * Combined UI state flow that reactively updates whenever any permission state changes.
	 */
	@Suppress("UNCHECKED_CAST")
	val uiState: StateFlow<MainUiState> = combine(
		cameraController.state,
		contactsController.state,
		notificationsController?.state ?: MutableStateFlow(YuruPermissionState.Granted),
		locationController.state,
		bluetoothController.state,
		storageController.state,
		_pendingSettingsPermissionName
	) { states ->
		MainUiState(
			camera = states[0] as YuruPermissionState,
			contacts = states[1] as YuruPermissionState,
			notifications = states[2] as YuruPermissionState,
			location = states[3] as Map<String, YuruPermissionState>,
			bluetooth = states[4] as Map<String, YuruPermissionState>,
			storage = states[5] as Map<String, YuruPermissionState>,
			pendingSettingsPermissionName = states[6] as String?,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5000),
		initialValue = MainUiState(),
	)

	/**
	 * Single entry point for all UI interactions.
	 */
	fun sendAction(action: MainUiAction) {
		when (action) {
			MainUiAction.RequestCamera -> handleRequest(cameraController, "Camera")
			MainUiAction.RequestContacts -> handleRequest(contactsController, "Contacts")
			MainUiAction.RequestNotifications -> {
				notificationsController?.let { handleRequest(it, "Notifications") }
			}
			MainUiAction.RequestLocation -> handleMultipleRequest(locationController, "Location")
			MainUiAction.RequestBluetooth -> handleMultipleRequest(bluetoothController, "Bluetooth")
			MainUiAction.RequestStorage -> handleMultipleRequest(storageController, "Storage")
			MainUiAction.OpenSettings -> {
				_pendingSettingsPermissionName.value = null
			}
			MainUiAction.DismissDialog -> _pendingSettingsPermissionName.value = null
		}
	}

	/**
	 * Generic handler for single permission requests.
	 * If the permission is permanently denied, it triggers the settings dialog.
	 */
	private fun handleRequest(controller: YuruPermissionController, name: String) {
		if (controller.state.value == YuruPermissionState.PermanentlyDenied) {
			_pendingSettingsPermissionName.value = name
		}
		else {
			viewModelScope.launch {
				controller.request()
			}
		}
	}

	/**
	 * Generic handler for multiple permission requests.
	 */
	private fun handleMultipleRequest(controller: YuruMultiplePermissionController, name: String) {
		val isPermanentlyDenied = controller.state.value.values.any { it == YuruPermissionState.PermanentlyDenied }
		if (isPermanentlyDenied) {
			_pendingSettingsPermissionName.value = name
		}
		else {
			viewModelScope.launch {
				controller.request()
			}
		}
	}
}
