package com.elianfabian.yuru_permissions.internal

import androidx.lifecycle.DefaultLifecycleObserver
import com.elianfabian.yuru_permissions.YuruPermissionState
import com.elianfabian.yuru_permissions.testing.FakeYuruMultiplePermissionController
import com.elianfabian.yuru_permissions.testing.FakeYuruPermissionController
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class FakeYuruBackend : YuruBackend {
	private val states = mutableMapOf<String, YuruPermissionState>()
	private val rejectionCount = mutableMapOf<String, Int>()
	private val singleContinuations = mutableMapOf<String, CancellableContinuation<YuruPermissionState>>()
	private val multipleContinuations = mutableMapOf<List<String>, CancellableContinuation<Map<String, YuruPermissionState>>>()
	private val singleControllers = mutableMapOf<String, FakeYuruPermissionControllerImpl>()
	private val multipleControllers = mutableMapOf<List<String>, FakeYuruMultiplePermissionControllerImpl>()

	private val keyedMutex = KeyedMutex<Any>()

	// Result queues for "pre-emptive" actions (e.g. calling accept() before request())
	private val singleResultQueue = mutableMapOf<String, YuruPermissionState>()
	private val multipleResultQueue = mutableMapOf<List<String>, Map<String, YuruPermissionState>>()

	fun clearStorage() {
		states.clear()
		rejectionCount.clear()
		singleResultQueue.clear()
		multipleResultQueue.clear()
		singleContinuations.clear()
		multipleContinuations.clear()
		singleControllers.values.forEach { it.updateInternalState(YuruPermissionState.NotDetermined) }
		multipleControllers.values.forEach { controller ->
			controller.updateInternalState(controller.permissionNames.associateWith { YuruPermissionState.NotDetermined })
		}
	}

	override fun getPermissionState(permissionName: String): YuruPermissionState {
		return states[permissionName] ?: YuruPermissionState.NotDetermined
	}

	override fun getMultiplePermissionState(permissionNames: List<String>): Map<String, YuruPermissionState> {
		return permissionNames.associateWith { getPermissionState(it) }
	}

	override suspend fun requestPermission(permissionName: String): YuruPermissionState {
		return keyedMutex.withLock(permissionName) {
			// If already granted, return immediately
			if (getPermissionState(permissionName) == YuruPermissionState.Granted) {
				return@withLock YuruPermissionState.Granted
			}

			// If a result is already queued, return it immediately
			singleResultQueue.remove(permissionName)?.let {
				processAndApplySingle(permissionName, it)
				return@withLock states[permissionName]!!
			}

			suspendCancellableCoroutine { continuation ->
				singleContinuations[permissionName] = continuation
				continuation.invokeOnCancellation {
					singleContinuations.remove(permissionName)
				}
			}
		}
	}

	override suspend fun requestMultiplePermissions(permissionNames: List<String>): Map<String, YuruPermissionState> {
		return keyedMutex.withLock(permissionNames) {
			// If already granted, return immediately
			if (getMultiplePermissionState(permissionNames).values.all { it == YuruPermissionState.Granted }) {
				return@withLock getMultiplePermissionState(permissionNames)
			}

			// If a result is already queued, return it immediately
			multipleResultQueue.remove(permissionNames)?.let {
				processAndApplyMultiple(it)
				return@withLock permissionNames.associateWith { getPermissionState(it) }
			}

			suspendCancellableCoroutine { continuation ->
				multipleContinuations[permissionNames] = continuation
				continuation.invokeOnCancellation {
					multipleContinuations.remove(permissionNames)
				}
			}
		}
	}

	override fun createSingleController(permissionName: String): YuruPermissionControllerImpl {
		return FakeYuruPermissionControllerImpl(permissionName, this)
	}

	override fun createMultipleController(permissionNames: List<String>): YuruMultiplePermissionControllerImpl {
		return FakeYuruMultiplePermissionControllerImpl(permissionNames, this)
	}

	override fun onYuruInitialized(
		scope: CoroutineScope,
		observer: DefaultLifecycleObserver,
		singleControllers: Map<String, YuruPermissionControllerImpl>,
		multipleControllers: Map<List<String>, YuruMultiplePermissionControllerImpl>,
	) {
		// No-op for fake
	}

	override fun validatePermission(permissionName: String) {
		// No-op for fake environment
	}

	// Internal helper to complete a request
	fun completeSingle(permissionName: String, state: YuruPermissionState) {
		val continuation = singleContinuations.remove(permissionName)
		if (continuation != null) {
			processAndApplySingle(permissionName, state)
			continuation.resume(states[permissionName]!!)
		}
		else {
			// Queue the result if no request is active
			singleResultQueue[permissionName] = state
		}
	}

	fun completeMultiple(permissionNames: List<String>, newStates: Map<String, YuruPermissionState>) {
		val continuation = multipleContinuations.remove(permissionNames)
		if (continuation != null) {
			processAndApplyMultiple(newStates)
			val finalStates = permissionNames.associateWith { getPermissionState(it) }
			continuation.resume(finalStates)
		}
		else {
			// Queue the result if no request is active
			multipleResultQueue[permissionNames] = newStates
		}
	}

	private fun processAndApplySingle(permissionName: String, state: YuruPermissionState) {
		val finalState = processRejection(permissionName, state)
		states[permissionName] = finalState
		updateControllersForPermission(permissionName, finalState)
	}

	private fun processAndApplyMultiple(newStates: Map<String, YuruPermissionState>) {
		val processedStates = newStates.mapValues { (name, state) ->
			processRejection(name, state)
		}

		states.putAll(processedStates)
		updateControllersForMultiple(processedStates.keys.toList())
	}

	private fun processRejection(permissionName: String, state: YuruPermissionState): YuruPermissionState {
		return if (state == YuruPermissionState.Denied) {
			val count = (rejectionCount[permissionName] ?: 0) + 1
			rejectionCount[permissionName] = count
			if (count >= 2) YuruPermissionState.PermanentlyDenied else YuruPermissionState.Denied
		}
		else {
			if (state == YuruPermissionState.Granted) {
				rejectionCount[permissionName] = 0
			}
			state
		}
	}

	private fun updateControllersForPermission(permissionName: String, state: YuruPermissionState) {
		// Update ALL single controllers for this permission
		singleControllers.filterKeys { it == permissionName }.values.forEach {
			it.updateInternalState(state)
		}

		// Update ALL multiple controllers containing this permission
		multipleControllers.forEach { (names, controller) ->
			if (permissionName in names) {
				controller.updateInternalState(names.associateWith { getPermissionState(it) })
			}
		}
	}

	private fun updateControllersForMultiple(affectedPermissions: List<String>) {
		// Update ALL affected single controllers
		affectedPermissions.forEach { name ->
			singleControllers[name]?.updateInternalState(getPermissionState(name))
		}

		// Update ALL affected multiple controllers
		multipleControllers.forEach { (names, controller) ->
			if (names.any { it in affectedPermissions }) {
				controller.updateInternalState(names.associateWith { getPermissionState(it) })
			}
		}
	}

	fun registerController(controller: FakeYuruPermissionControllerImpl) {
		singleControllers[controller.permissionName] = controller
	}

	fun registerMultipleController(controller: FakeYuruMultiplePermissionControllerImpl) {
		multipleControllers[controller.permissionNames] = controller
	}
}

internal class FakeYuruPermissionControllerImpl(
	permissionName: String,
	private val fakeBackend: FakeYuruBackend,
) : YuruPermissionControllerImpl(permissionName, fakeBackend), FakeYuruPermissionController {

	init {
		fakeBackend.registerController(this)
	}

	override fun accept() = fakeBackend.completeSingle(permissionName, YuruPermissionState.Granted)
	override fun reject() = fakeBackend.completeSingle(permissionName, YuruPermissionState.Denied)
	override fun updateState(newState: YuruPermissionState) {
		fakeBackend.completeSingle(permissionName, newState)
	}
}

internal class FakeYuruMultiplePermissionControllerImpl(
	permissionNames: List<String>,
	private val fakeBackend: FakeYuruBackend,
) : YuruMultiplePermissionControllerImpl(permissionNames, fakeBackend), FakeYuruMultiplePermissionController {

	init {
		fakeBackend.registerMultipleController(this)
	}

	override fun acceptAll() = fakeBackend.completeMultiple(permissionNames, permissionNames.associateWith { YuruPermissionState.Granted })
	override fun rejectAll() = fakeBackend.completeMultiple(permissionNames, permissionNames.associateWith { YuruPermissionState.Denied })
	override fun updateStates(newStates: Map<String, YuruPermissionState>) = fakeBackend.completeMultiple(permissionNames, newStates)
}
