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
import java.util.WeakHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production implementation of [YuruBackend] that interacts with the Android system.
 */
internal class RealYuruBackend : YuruBackend {

	private val _singleLaunchers = mutableMapOf<String, ActivityResultLauncherHolder<String>>()
	private val _multipleLaunchers = mutableMapOf<List<String>, ActivityResultLauncherHolder<Array<String>>>()

	private val _keyByController = WeakHashMap<Any, String>()

	private val _singleContinuations = mutableMapOf<String, CancellableContinuation<YuruPermissionState>>()
	private val _multipleContinuations = mutableMapOf<List<String>, CancellableContinuation<Map<String, YuruPermissionState>>>()

	private val _keyedMutex = KeyedMutex<Any>()

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
		return _keyedMutex.withLock(permissionName) {
			val currentState = getPermissionState(permissionName)
			if (currentState == YuruPermissionState.Granted) {
				return@withLock YuruPermissionState.Granted
			}

			val holder = _singleLaunchers[permissionName] ?: error("Launcher not initialized for $permissionName")

			suspendCancellableCoroutine { continuation ->
				_singleContinuations[permissionName] = continuation

				continuation.invokeOnCancellation {
					_singleContinuations.remove(permissionName)
				}

				try {
					holder.launch(permissionName)
				} catch (e: Exception) {
					_singleContinuations.remove(permissionName)?.resumeWithException(e)
				}
			}
		}
	}

	override suspend fun requestMultiplePermissions(permissionNames: List<String>): Map<String, YuruPermissionState> {
		return _keyedMutex.withLock(permissionNames) {
			val currentState = getMultiplePermissionState(permissionNames)
			if (currentState.values.all { it == YuruPermissionState.Granted }) {
				return@withLock currentState
			}

			val holder = _multipleLaunchers[permissionNames] ?: error("Launcher not initialized for $permissionNames")

			suspendCancellableCoroutine { continuation ->
				_multipleContinuations[permissionNames] = continuation

				continuation.invokeOnCancellation {
					_multipleContinuations.remove(permissionNames)
				}

				try {
					holder.launch(permissionNames.toTypedArray())
				} catch (e: Exception) {
					_multipleContinuations.remove(permissionNames)?.resumeWithException(e)
				}
			}
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
				// Create snapshots to avoid ConcurrentModificationException if new controllers are added during iteration
				val singles = singleControllers.values.toList()
				val multiples = multipleControllers.values.toList()

				singles.forEach { controller ->
					refreshLauncher(activity, controller)
				}
				multiples.forEach { controller ->
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
		holder.unregister()

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
		holder.unregister()

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
