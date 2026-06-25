package com.elianfabian.yuru_permissions.internal

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.elianfabian.activity_provider.ActivityProvider
import com.elianfabian.yuru_permissions.YuruPermissionState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Production implementation of [YuruBackend] that interacts with the Android system.
 */
internal class RealYuruBackend : YuruBackend {

	private val _singleLaunchers = mutableMapOf<String, ActivityResultLauncherHolder<String>>()
	private val _multipleLaunchers = mutableMapOf<List<String>, ActivityResultLauncherHolder<Array<String>>>()

	private val _keyByController = mutableMapOf<Any, String>()

	private val _singleContinuations = mutableMapOf<String, CancellableContinuation<YuruPermissionState>>()
	private val _multipleContinuations = mutableMapOf<List<String>, CancellableContinuation<Map<String, YuruPermissionState>>>()

	override fun getPermissionState(permissionName: String): YuruPermissionState {
		return getPermissionState(
			activity = ActivityProvider.getActivityOrNull() ?: return YuruPermissionState.NotDetermined,
			permissionName = permissionName,
		)
	}

	override fun getMultiplePermissionState(permissionNames: List<String>): Map<String, YuruPermissionState> {
		val activity = ActivityProvider.getActivityOrNull() ?: return permissionNames.associateWith { YuruPermissionState.NotDetermined }
		return permissionNames.associateWith { name ->
			getPermissionState(
				activity = activity,
				permissionName = name,
			)
		}
	}

	override suspend fun requestPermission(permissionName: String): YuruPermissionState {
		val holder = _singleLaunchers[permissionName] ?: error("Launcher not initialized for $permissionName")

		return suspendCancellableCoroutine { continuation ->
			_singleContinuations[permissionName] = continuation
			continuation.invokeOnCancellation { _singleContinuations.remove(permissionName) }
			holder.launch(permissionName)
		}
	}

	override suspend fun requestMultiplePermissions(permissionNames: List<String>): Map<String, YuruPermissionState> {
		val holder = _multipleLaunchers[permissionNames] ?: error("Launcher not initialized for $permissionNames")

		return suspendCancellableCoroutine { continuation ->
			_multipleContinuations[permissionNames] = continuation
			continuation.invokeOnCancellation { _multipleContinuations.remove(permissionNames) }
			holder.launch(permissionNames.toTypedArray())
		}
	}

	override fun createSingleController(permissionName: String): YuruPermissionControllerImpl {
		return YuruPermissionControllerImpl(permissionName, this).also {
			refreshLauncher(ActivityProvider.getActivityOrNull(), it)
		}
	}

	override fun createMultipleController(permissionNames: List<String>): YuruMultiplePermissionControllerImpl {
		return YuruMultiplePermissionControllerImpl(permissionNames, this).also {
			refreshLauncher(ActivityProvider.getActivityOrNull(), it)
		}
	}

	override fun onYuruInitialized(
		scope: CoroutineScope,
		observer: DefaultLifecycleObserver,
		singleControllers: Map<String, YuruPermissionControllerImpl>,
		multipleControllers: Map<List<String>, YuruMultiplePermissionControllerImpl>,
	) {
		scope.launch {
			ProcessLifecycleOwner.get().lifecycle.addObserver(observer)

			ActivityProvider.activity.collect { activity ->
				println("$$$ activity: $activity")
				singleControllers.forEach { (_, controller) ->
					refreshLauncher(activity, controller)
				}
				multipleControllers.forEach { (_, controller) ->
					refreshLauncher(activity, controller)
				}
			}
		}
	}

	override fun validatePermission(permissionName: String) {
		require(isValidSystemPermission(permissionName)) {
			"$permissionName is not a valid system permission"
		}

		checkManifestPermission(permissionName)
	}

	private fun refreshLauncher(activity: ComponentActivity?, controller: YuruPermissionControllerImpl) {
		val holder = _singleLaunchers.getOrPut(controller.permissionName) { ActivityResultLauncherHolder() }
		holder.launcher?.also { launcher ->
			launcher.unregister()
		}

		if (activity == null) {
			return
		}

		holder.launcher = activity.activityResultRegistry.register(
			key = _keyByController.getOrPut(controller) { UUID.randomUUID().toString() },
			contract = ActivityResultContracts.RequestPermission(),
			callback = {
				val result = getPermissionState(controller.permissionName)
				controller.updateInternalState(result)
				_singleContinuations.remove(controller.permissionName)?.resume(result)
			}
		)
	}

	private fun refreshLauncher(activity: ComponentActivity?, controller: YuruMultiplePermissionControllerImpl) {
		val holder = _multipleLaunchers.getOrPut(controller.permissionNames) { ActivityResultLauncherHolder() }
		holder.launcher?.also { launcher ->
			launcher.unregister()
		}

		if (activity == null) {
			return
		}

		holder.launcher = activity.activityResultRegistry.register(
			key = _keyByController.getOrPut(controller) { UUID.randomUUID().toString() },
			contract = ActivityResultContracts.RequestMultiplePermissions(),
			callback = {
				val result = getMultiplePermissionState(controller.permissionNames)
				controller.updateInternalState(result)
				_multipleContinuations.remove(controller.permissionNames)?.resume(result)
			}
		)
	}
}
