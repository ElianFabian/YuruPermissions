@file:SuppressLint("InlinedApi")

package com.elianfabian.yuru_permissions.internal

import android.annotation.SuppressLint
import com.elianfabian.yuru_permissions.YuruMultiplePermissionController
import com.elianfabian.yuru_permissions.YuruPermissionController
import com.elianfabian.yuru_permissions.YuruPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Internal base implementation for [YuruPermissionController].
 */
internal open class YuruPermissionControllerImpl(
	val permissionName: String,
	private val backend: YuruBackend,
) : YuruPermissionController {

	private val _state = MutableStateFlow(backend.getPermissionState(permissionName))
	override val state: StateFlow<YuruPermissionState> = _state.asStateFlow()

	override suspend fun request(): YuruPermissionState {
		if (state.value == YuruPermissionState.Granted) {
			return YuruPermissionState.Granted
		}

		val result = backend.requestPermission(permissionName)
		_state.value = result
		return result
	}

	internal fun refreshState() {
		_state.value = backend.getPermissionState(permissionName)
	}

	internal fun updateInternalState(newState: YuruPermissionState) {
		_state.value = newState
	}
}

/**
 * Internal base implementation for [YuruMultiplePermissionController].
 */
internal open class YuruMultiplePermissionControllerImpl(
	val permissionNames: List<String>,
	private val backend: YuruBackend,
) : YuruMultiplePermissionController {

	private val _state = MutableStateFlow(backend.getMultiplePermissionState(permissionNames))
	override val state = _state.asStateFlow()

	override suspend fun request(): Map<String, YuruPermissionState> {
		if (state.value.all { it.value == YuruPermissionState.Granted }) {
			return state.value
		}

		val result = backend.requestMultiplePermissions(permissionNames)
		_state.value = result
		return result
	}

	internal fun refreshState() {
		_state.value = backend.getMultiplePermissionState(permissionNames)
	}

	internal fun updateInternalState(newState: Map<String, YuruPermissionState>) {
		_state.value = newState
	}
}
