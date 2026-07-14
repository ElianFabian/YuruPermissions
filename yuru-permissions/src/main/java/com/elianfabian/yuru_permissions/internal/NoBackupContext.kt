package com.elianfabian.yuru_permissions.internal

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/**
 * Internally SharedPreferences is created using the data directory of the application,
 * but we want to force the data directory to be backed up directory so that we never
 * back up our preferences.
 */
internal class NoBackupContext(base: Context) : ContextWrapper(base.applicationContext) {

	override fun getDataDir(): File? {
		return noBackupFilesDir
	}
}
