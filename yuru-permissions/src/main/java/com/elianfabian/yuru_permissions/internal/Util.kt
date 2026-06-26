package com.elianfabian.yuru_permissions.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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

internal fun checkManifestPermission(permissionName: String) {
	val context = ActivityProvider.getActivityOrNull() ?: return

	val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

	try {
		val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
			context.packageManager.getPackageInfo(
				context.packageName,
				PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
			)
		}
		else {
			@Suppress("DEPRECATION")
			context.packageManager.getPackageInfo(
				context.packageName,
				PackageManager.GET_PERMISSIONS
			)
		}

		val isDeclared = packageInfo.requestedPermissions?.contains(permissionName) == true

		if (!isDeclared) {
			val errorMessage = "The permission '$permissionName' is NOT declared in your AndroidManifest.xml. " +
				"The permission request will fail silently without showing the native dialog."

			if (isDebuggable) {
				// 🔴 Crash immediately during development so the developer notices it instantly
				throw IllegalArgumentException("YuruPermissions Error: $errorMessage")
			}
			else {
				// ⚠️ Just log it in production to prevent crashing an end-user's device due to OS glitches
				Log.e("YuruPermissions", errorMessage)
			}
		}
	}
	catch (e: Exception) {
		// Prevent crashing in production if the PackageManager fails to query the package info
		if (e is IllegalStateException && isDebuggable) {
			throw e
		}
		Log.e("YuruPermissions", "Failed to verify manifest permissions due to an unexpected system error.", e)
	}
}
