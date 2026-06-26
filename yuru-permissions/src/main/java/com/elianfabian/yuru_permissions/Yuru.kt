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
	 * Gets an existing [YuruPermissionController] for the given [permissionName],
	 * or creates a new one if it doesn't exist.
	 */
	public fun getOrCreateSinglePermissionController(permissionName: String): YuruPermissionController

	/**
	 * Convenience method to get or create a controller for multiple permissions.
	 * Requires at least 2 permission names.
	 */
	public fun getOrCreateMultiplePermissionController(
		permissionName0: String,
		permissionName1: String,
		vararg permissionName: String,
	): YuruMultiplePermissionController

	/**
	 * Gets an existing [YuruMultiplePermissionController] for the given [permissionNames],
	 * or creates a new one if it doesn't exist.
	 */
	public fun getOrCreateMultiplePermissionController(
		permissionNames: List<String>,
	): YuruMultiplePermissionController

	public companion object : Yuru {
		private val delegate: Yuru by lazy { YuruImpl(RealYuruBackend()) }

		override fun getOrCreateSinglePermissionController(permissionName: String): YuruPermissionController {
			return delegate.getOrCreateSinglePermissionController(permissionName)
		}

		override fun getOrCreateMultiplePermissionController(
			permissionName0: String,
			permissionName1: String,
			vararg permissionName: String,
		): YuruMultiplePermissionController {
			return delegate.getOrCreateMultiplePermissionController(permissionName0, permissionName1, *permissionName)
		}

		override fun getOrCreateMultiplePermissionController(permissionNames: List<String>): YuruMultiplePermissionController {
			return delegate.getOrCreateMultiplePermissionController(permissionNames)
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
public open class YuruImpl internal constructor(
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

	override fun getOrCreateSinglePermissionController(permissionName: String): YuruPermissionController {
		backend.validatePermission(permissionName)

		return _singlePermissionControllers.getOrPut(permissionName) {
			backend.createSingleController(permissionName)
		}
	}

	override fun getOrCreateMultiplePermissionController(
		permissionName0: String,
		permissionName1: String,
		vararg permissionName: String,
	): YuruMultiplePermissionController {
		val permissionsList = buildList {
			add(permissionName0)
			add(permissionName1)
			addAll(permissionName)
		}

		return getOrCreateMultiplePermissionController(permissionsList)
	}

	override fun getOrCreateMultiplePermissionController(
		permissionNames: List<String>,
	): YuruMultiplePermissionController {
		require(permissionNames.isNotEmpty()) {
			"At least 2 permission names must be provided"
		}

		// Normalize the permission list to ensure consistency in the map keys
		val sanitizedPermissionNames = permissionNames.distinct().sorted()

		sanitizedPermissionNames.forEach { backend.validatePermission(it) }

		return _multiplePermissionControllers.getOrPut(sanitizedPermissionNames) {
			backend.createMultipleController(sanitizedPermissionNames)
		}
	}
}
