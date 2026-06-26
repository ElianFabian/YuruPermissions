package com.elianfabian.yuru_permissions

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.elianfabian.yuru_permissions.internal.RealYuruBackend
import com.elianfabian.yuru_permissions.internal.YuruBackend
import com.elianfabian.yuru_permissions.internal.YuruMultiplePermissionControllerImpl
import com.elianfabian.yuru_permissions.internal.YuruPermissionControllerImpl
import com.elianfabian.yuru_permissions.testing.FakeYuru
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Main entry point for the Yuru Permissions library.
 *
 * This interface provides a centralized way to manage and request permissions.
 */
public interface Yuru {
	/**
	 * Gets an existing [YuruPermissionController] for the given [permission],
	 * or creates a new one if it doesn't exist.
	 */
	public fun singlePermissionController(permission: String): YuruPermissionController

	/**
	 * Convenience method to get or create a controller for multiple permissions.
	 * Requires at least 2 permission names.
	 */
	public fun multiplePermissionController(
		first: String,
		second: String,
		vararg remaining: String,
	): YuruMultiplePermissionController

	/**
	 * Gets an existing [YuruMultiplePermissionController] for the given [permissions],
	 * or creates a new one if it doesn't exist.
	 */
	public fun multiplePermissionController(
		permissions: List<String>,
	): YuruMultiplePermissionController

	public companion object : Yuru {
		private val delegate: Yuru by lazy { BaseYuruImpl(RealYuruBackend()) }

		override fun singlePermissionController(permission: String): YuruPermissionController {
			return delegate.singlePermissionController(permission)
		}

		override fun multiplePermissionController(
			first: String,
			second: String,
			vararg remaining: String,
		): YuruMultiplePermissionController {
			return delegate.multiplePermissionController(first, second, *remaining)
		}

		override fun multiplePermissionController(permissions: List<String>): YuruMultiplePermissionController {
			return delegate.multiplePermissionController(permissions)
		}

		/**
		 * Creates a [FakeYuru] environment for testing.
		 */
		public fun createSimulatedYuruEnvironment(): FakeYuru {
			return FakeYuru()
		}
	}
}


/**
 * Internal base implementation for [Yuru].
 */
public open class BaseYuruImpl internal constructor(
	internal val backend: YuruBackend,
) : Yuru {
	private val _scope by lazy { CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()) }

	private val _singlePermissionControllers = mutableMapOf<String, YuruPermissionControllerImpl>()
	private val _multiplePermissionControllers = mutableMapOf<List<String>, YuruMultiplePermissionControllerImpl>()

	private val _lifecycleObserver = object : DefaultLifecycleObserver {
		override fun onStart(owner: LifecycleOwner) {
			// Automatically refresh permission states when the app returns to the foreground
			_singlePermissionControllers.forEach { (_, controller) ->
				controller.refreshState()
			}
			_multiplePermissionControllers.forEach { (_, controller) ->
				controller.refreshState()
			}
		}
	}

	init {
		// Initialize the backend with the necessary lifecycle and scope information
		backend.onYuruInitialized(
			scope = _scope,
			observer = _lifecycleObserver,
			singleControllers = _singlePermissionControllers,
			multipleControllers = _multiplePermissionControllers
		)
	}

	override fun singlePermissionController(permission: String): YuruPermissionController {
		backend.validatePermission(permission)

		return _singlePermissionControllers.getOrPut(permission) {
			backend.createSingleController(permission)
		}
	}

	override fun multiplePermissionController(
		first: String,
		second: String,
		vararg remaining: String,
	): YuruMultiplePermissionController {
		val permissionsList = buildList {
			add(first)
			add(second)
			addAll(remaining)
		}

		return multiplePermissionController(permissionsList)
	}

	override fun multiplePermissionController(
		permissions: List<String>,
	): YuruMultiplePermissionController {
		require(permissions.size >= 2) {
			"At least 2 permission names must be provided"
		}

		// Normalize the permission list to ensure consistency in the map keys
		val sanitizedPermissionNames = permissions.distinct().sorted()

		sanitizedPermissionNames.forEach { backend.validatePermission(it) }

		return _multiplePermissionControllers.getOrPut(sanitizedPermissionNames) {
			backend.createMultipleController(sanitizedPermissionNames)
		}
	}
}
