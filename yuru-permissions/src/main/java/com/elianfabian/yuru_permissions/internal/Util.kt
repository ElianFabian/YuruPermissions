package com.elianfabian.yuru_permissions.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.elianfabian.activity_provider.ActivityProvider
import com.elianfabian.yuru_permissions.YuruPermissionState

@SuppressLint("ObsoleteSdkInt")
internal fun getPermissionState(
	activity: Activity,
	permissionName: String,
): YuruPermissionState {
	val sharedPrefs = activity.getSharedPreferences("yuru_permissions_prefs", Context.MODE_PRIVATE)

	return if (ContextCompat.checkSelfPermission(
			activity,
			permissionName,
		) == PackageManager.PERMISSION_GRANTED
	) {
		sharedPrefs.edit { remove(permissionName) }
		YuruPermissionState.Granted
	}
	else {
		if (Build.VERSION.SDK_INT >= 23) {
			if (activity.shouldShowRequestPermissionRationale(permissionName)) {
				sharedPrefs.edit { putBoolean(permissionName, true) }
				YuruPermissionState.Denied
			}
			else {
				if (sharedPrefs.getBoolean(permissionName, false)) {
					YuruPermissionState.PermanentlyDenied
				}
				else YuruPermissionState.NotDetermined
			}
		}
		else {
			if (sharedPrefs.getBoolean(permissionName, false)) {
				YuruPermissionState.Denied
			}
			else YuruPermissionState.NotDetermined
		}
	}
}

internal fun isValidSystemPermission(permissionName: String): Boolean {
	val context = ActivityProvider.getActivityOrNull() ?: return false

	return try {
		context.packageManager.getPermissionInfo(permissionName, 0)
		true
	}
	catch (_: PackageManager.NameNotFoundException) {
		false
	}
}
