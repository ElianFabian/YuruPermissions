package com.elianfabian.yuru_permissions

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

public class TestActivity : ComponentActivity() {

	public val permissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) { isGranted: Boolean ->
		println("$$$ isGranted: $isGranted")
	}
}
