package com.elianfabian.yuru_permissions

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing and requesting a single Android permission.
 */
public interface YuruPermissionController {

	/**
	 * A [StateFlow] that emits the current [YuruPermissionState] of the permission.
	 */
	public val state: StateFlow<YuruPermissionState>

	/**
	 * Requests the permission from the user.
	 *
	 * - If the permission is already [YuruPermissionState.Granted], it returns immediately.
	 * - If the permission was never asked or was [YuruPermissionState.Denied], it triggers the system dialog.
	 * - This method suspends until the user interacts with the system dialog and a result is available.
	 *
	 * @return The final [YuruPermissionState] after the request.
	 */
	public suspend fun request(): YuruPermissionState
}

/**
 * Interface for managing and requesting a group of Android permissions together.
 */
public interface YuruMultiplePermissionController {

	/**
	 * A [StateFlow] that emits the current state of all permissions in this group.
	 * The map keys are the permission names, and the values are their respective [YuruPermissionState].
	 */
	public val state: StateFlow<Map<String, YuruPermissionState>>

	/**
	 * Requests all permissions in the group from the user.
	 *
	 * - If all permissions are already [YuruPermissionState.Granted], it returns immediately.
	 * - Otherwise, it triggers the system dialog for any non-granted permissions.
	 * - This method suspends until the system dialog flow is complete.
	 *
	 * @return A map containing the final [YuruPermissionState] for each requested permission.
	 */
	public suspend fun request(): Map<String, YuruPermissionState>
}

/**
 * Represents the possible states of an Android permission.
 */
public enum class YuruPermissionState {

	/**
	 * The state when the user has not yet been asked for this permission,
	 * or when the permission state cannot be determined (e.g. app just started).
	 *
	 * This state also occurs if:
	 * - The user selects "Only this time" and the granted state eventually expires.
	 * - The user dismisses the permission dialog without making a choice.
	 */
	NotDetermined,

	/**
	 * The permission has been explicitly granted by the user.
	 */
	Granted,

	/**
	 * The permission was denied by the user, but they can still be asked again.
	 *
	 * This usually means the system "Rationale" should be shown to explain
	 * why the app needs the permission.
	 */
	Denied,

	/**
	 * The permission has been permanently denied.
	 *
	 * The system will no longer show a dialog for this permission.
	 * To enable it, the user must go to the App Settings manually.
	 */
	PermanentlyDenied;


	/**
	 * Helper property that returns true if the state is [NotDetermined].
	 */
	public val isNotDetermined: Boolean get() = this == NotDetermined

	/**
	 * Helper property that returns true if the state is [Granted].
	 */
	public val isGranted: Boolean get() = this == Granted

	/**
	 * Helper property that returns true if the state is [Denied].
	 */
	public val isDenied: Boolean get() = this == Denied

	/**
	 * Helper property that returns true if the state is [PermanentlyDenied].
	 */
	public val isPermanentlyDenied: Boolean get() = this == PermanentlyDenied
}


public val Map<String, YuruPermissionState>.allAreNotDetermined: Boolean
	get() = this.values.all { it == YuruPermissionState.NotDetermined } || this.isEmpty()

public val Map<String, YuruPermissionState>.allAreGranted: Boolean
	get() = this.values.all { it == YuruPermissionState.Granted } || this.isEmpty()

public val Map<String, YuruPermissionState>.allAreDenied: Boolean
	get() = this.values.all { it == YuruPermissionState.Denied } || this.isEmpty()

public val Map<String, YuruPermissionState>.allArePermanentlyDenied: Boolean
	get() = this.values.all { it == YuruPermissionState.PermanentlyDenied } && this.isNotEmpty()
