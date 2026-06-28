package com.elianfabian.yuru_permissions.internal

import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.elianfabian.activity_provider.ActivityProvider
import com.elianfabian.activity_result_bridge.ActivityResultBridge
import com.elianfabian.yuru_permissions.YuruPermissionState
import com.elianfabian.yuru_permissions.allAreGranted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Production implementation of [YuruBackend] that interacts with the Android system.
 */
internal class RealYuruBackend : YuruBackend {

	private val activityResultBridge = ActivityResultBridge.getInstance()


	override fun getPermissionState(permissionName: String): YuruPermissionState {
		return getPermissionState(
			activity = ActivityProvider.getActivityOrNull() ?: return YuruPermissionState.NotDetermined,
			permission = permissionName,
		)
	}

	override fun getMultiplePermissionState(permissionNames: List<String>): Map<String, YuruPermissionState> {
		val activity = ActivityProvider.getActivityOrNull() ?: return permissionNames.associateWith { YuruPermissionState.NotDetermined }
		return permissionNames.associateWith { name ->
			getPermissionState(
				activity = activity,
				permission = name,
			)
		}
	}

	override suspend fun requestPermission(permissionName: String): YuruPermissionState {
		val currentState = getPermissionState(permissionName)
		if (currentState == YuruPermissionState.Granted) {
			return YuruPermissionState.Granted
		}

		// We just simply need to wait for the result, we then compute the state ourselves
		activityResultBridge.launch(
			contract = ActivityResultContracts.RequestPermission(),
			input = permissionName,
		)

		return getPermissionState(permissionName)
	}

	override suspend fun requestMultiplePermissions(permissionNames: List<String>): Map<String, YuruPermissionState> {
		val currentState = getMultiplePermissionState(permissionNames)
		if (currentState.allAreGranted) {
			return currentState
		}

		// We just simply need to wait for the result, we then compute the state ourselves
		activityResultBridge.launch(
			contract = ActivityResultContracts.RequestMultiplePermissions(),
			input = permissionNames.toTypedArray(),
		)

		return getMultiplePermissionState(permissionNames)
	}

	override fun createSingleController(permissionName: String): YuruPermissionControllerImpl {
		return YuruPermissionControllerImpl(permissionName, this)
	}

	override fun createMultipleController(permissionNames: List<String>): YuruMultiplePermissionControllerImpl {
		return YuruMultiplePermissionControllerImpl(permissionNames, this)
	}

	override fun onYuruInitialized(
		scope: CoroutineScope,
		observer: DefaultLifecycleObserver,
		singleControllers: Map<String, YuruPermissionControllerImpl>,
		multipleControllers: Map<List<String>, YuruMultiplePermissionControllerImpl>,
	) {
		// ProcessLifecycleOwner needs to be called on the main thread
		scope.launch(Dispatchers.Main.immediate) {
			ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
		}
	}

	override fun validatePermission(permissionName: String) {
		require(isValidSystemPermission(permissionName)) {
			"$permissionName is not a valid system permission"
		}

		checkManifestPermission(permissionName)
	}
}
