package com.elianfabian.yuru_permissions.internal

import androidx.activity.result.ActivityResultLauncher

internal class ActivityResultLauncherHolder<I> {

	var launcher: ActivityResultLauncher<I>? = null

	fun launch(input: I) {
		launcher?.launch(input) ?: error("Launcher has not been initialized")
	}

	fun unregister() {
		launcher?.unregister() ?: error("Launcher has not been initialized")
	}
}
