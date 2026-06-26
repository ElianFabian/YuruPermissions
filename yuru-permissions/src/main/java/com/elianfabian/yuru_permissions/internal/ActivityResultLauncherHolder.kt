package com.elianfabian.yuru_permissions.internal

import androidx.activity.result.ActivityResultLauncher

internal class ActivityResultLauncherHolder<I> {

	var launcher: ActivityResultLauncher<I>? = null

	fun launch(input: I) {
		launcher?.launch(input) ?: error("No active Activity available to request permission")
	}

	fun unregister() {
		launcher?.unregister()
		launcher = null
	}
}
