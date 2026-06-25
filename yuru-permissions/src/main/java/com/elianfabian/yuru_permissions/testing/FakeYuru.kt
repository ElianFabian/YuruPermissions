package com.elianfabian.yuru_permissions.testing

import androidx.lifecycle.DefaultLifecycleObserver
import com.elianfabian.yuru_permissions.Yuru
import com.elianfabian.yuru_permissions.YuruMultiplePermissionController
import com.elianfabian.yuru_permissions.YuruPermissionController
import com.elianfabian.yuru_permissions.YuruPermissionState
import com.elianfabian.yuru_permissions.internal.FakeYuruBackend
import com.elianfabian.yuru_permissions.internal.FakeYuruMultiplePermissionControllerImpl
import com.elianfabian.yuru_permissions.internal.FakeYuruPermissionControllerImpl
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A simulated [Yuru] environment for testing.
 *
 * This implementation allows developers to manually control permission states
 * and simulate user interactions like accepting or rejecting permissions without
 * an Android environment.
 */
public class FakeYuru internal constructor(
	private val fakeBackend: FakeYuruBackend = FakeYuruBackend(),
) : Yuru(fakeBackend) {

	/**
	 * Resets all simulated permissions to [YuruPermissionState.NotDetermined].
	 */
	public fun clearStorage() {
		fakeBackend.clearStorage()
	}

	override fun getOrCreateSinglePermissionController(permissionName: String): FakeYuruPermissionController {
		return super.getOrCreateSinglePermissionController(permissionName) as FakeYuruPermissionController
	}

	override fun getOrCreateMultiplePermissionController(permissionNames: List<String>): FakeYuruMultiplePermissionController {
		return super.getOrCreateMultiplePermissionController(permissionNames) as FakeYuruMultiplePermissionController
	}
}

/**
 * Controller interface for a single permission in a fake environment.
 */
public interface FakeYuruPermissionController : YuruPermissionController {
	/** Simulates the user accepting the permission. */
	public fun accept()
	/** Simulates the user rejecting the permission. */
	public fun reject()
	/** Directly updates the permission state. */
	public fun updateState(newState: YuruPermissionState)
}

/**
 * Controller interface for multiple permissions in a fake environment.
 */
public interface FakeYuruMultiplePermissionController : YuruMultiplePermissionController {
	/** Simulates the user accepting all permissions. */
	public fun acceptAll()
	/** Simulates the user rejecting all permissions. */
	public fun rejectAll()
	/** Directly updates multiple permission states. */
	public fun updateStates(newStates: Map<String, YuruPermissionState>)
}
