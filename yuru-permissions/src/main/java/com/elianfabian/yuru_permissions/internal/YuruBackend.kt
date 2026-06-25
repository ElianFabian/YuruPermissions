package com.elianfabian.yuru_permissions.internal

import androidx.lifecycle.DefaultLifecycleObserver
import com.elianfabian.yuru_permissions.YuruPermissionState
import kotlinx.coroutines.CoroutineScope

/**
 * Internal interface defining the operations required for permission management.
 */
internal interface YuruBackend {
	fun getPermissionState(permissionName: String): YuruPermissionState
	fun getMultiplePermissionState(permissionNames: List<String>): Map<String, YuruPermissionState>
	
	suspend fun requestPermission(permissionName: String): YuruPermissionState
	suspend fun requestMultiplePermissions(permissionNames: List<String>): Map<String, YuruPermissionState>

	fun createSingleController(permissionName: String): YuruPermissionControllerImpl
	fun createMultipleController(permissionNames: List<String>): YuruMultiplePermissionControllerImpl

	fun onYuruInitialized(
		scope: CoroutineScope,
		observer: DefaultLifecycleObserver,
		singleControllers: Map<String, YuruPermissionControllerImpl>,
		multipleControllers: Map<List<String>, YuruMultiplePermissionControllerImpl>
	)

	fun validatePermission(permissionName: String)
}
