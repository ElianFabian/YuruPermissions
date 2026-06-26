package com.elianfabian.yurupermissions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elianfabian.yuru_permissions.YuruPermissionState
import com.elianfabian.yuru_permissions.allAreGranted
import com.elianfabian.yuru_permissions.allArePermanentlyDenied
import com.elianfabian.yurupermissions.ui.theme.YuruPermissionsTheme

/**
 * Example Activity demonstrating how to use the Yuru Permissions library.
 */
class MainActivity : ComponentActivity() {

	private val viewModel: MainViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			YuruPermissionsTheme {
				val uiState by viewModel.uiState.collectAsState()

				Scaffold(
					modifier = Modifier.fillMaxSize().padding(WindowInsets.navigationBars.asPaddingValues()),
					bottomBar = {
						// Global button to open system app settings
						Button(
							onClick = { openAppSettings() },
							modifier = Modifier
								.fillMaxWidth()
								.padding(16.dp)
						) {
							Text("Open App Settings")
						}
					}
				) { innerPadding ->
					PermissionList(
						uiState = uiState,
						onAction = { action ->
							// Intercept OpenSettings action to handle Intent logic here
							if (action is MainUiAction.OpenSettings) {
								openAppSettings()
							}
							viewModel.sendAction(action)
						},
						modifier = Modifier.padding(innerPadding)
					)

					// Show rationale dialog if a permission was permanently denied
					if (uiState.pendingSettingsPermissionName != null) {
						SettingsDialog(
							permissionName = uiState.pendingSettingsPermissionName!!,
							onDismiss = { viewModel.sendAction(MainUiAction.DismissDialog) },
							onConfirm = {
								openAppSettings()
								viewModel.sendAction(MainUiAction.DismissDialog)
							}
						)
					}
				}
			}
		}
	}

	/**
	 * Navigates the user to this application's system settings page.
	 */
	private fun openAppSettings() {
		val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
			data = Uri.fromParts("package", packageName, null)
		}
		startActivity(intent)
	}
}

/**
 * Scrollable list of permission items and their current states.
 */
@Composable
fun PermissionList(
	uiState: MainUiState,
	onAction: (MainUiAction) -> Unit,
	modifier: Modifier = Modifier,
) {
	LazyColumn(
		modifier = modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		item {
			Text(
				text = "Yuru Permissions Example",
				style = MaterialTheme.typography.headlineMedium,
				modifier = Modifier.padding(bottom = 16.dp)
			)
		}

		item {
			PermissionItem(
				name = "Camera",
				state = uiState.camera,
				onRequest = { onAction(MainUiAction.RequestCamera) }
			)
		}

		item {
			PermissionItem(
				name = "Contacts",
				state = uiState.contacts,
				onRequest = { onAction(MainUiAction.RequestContacts) }
			)
		}

		item {
			PermissionItem(
				name = "Notifications",
				state = uiState.notifications,
				onRequest = { onAction(MainUiAction.RequestNotifications) }
			)
		}

		item {
			MultiplePermissionItem(
				name = "Location",
				states = uiState.location,
				onRequest = { onAction(MainUiAction.RequestLocation) }
			)
		}

		item {
			MultiplePermissionItem(
				name = "Bluetooth",
				states = uiState.bluetooth,
				onRequest = { onAction(MainUiAction.RequestBluetooth) }
			)
		}

		item {
			MultiplePermissionItem(
				name = "Storage",
				states = uiState.storage,
				onRequest = { onAction(MainUiAction.RequestStorage) }
			)
		}
	}
}

/**
 * UI row for a single permission.
 */
@Composable
fun PermissionItem(
	name: String,
	state: YuruPermissionState,
	onRequest: () -> Unit,
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column {
				Text(text = name, fontWeight = FontWeight.Bold)
				Text(
					text = state.name,
					color = when (state) {
						YuruPermissionState.Granted -> Color(0xFF4CAF50)
						YuruPermissionState.Denied -> Color(0xFFF44336)
						YuruPermissionState.PermanentlyDenied -> Color(0xFFB71C1C)
						else -> Color.Gray
					}
				)
			}
			Button(
				onClick = onRequest,
				enabled = state != YuruPermissionState.Granted
			) {
				Text(if (state == YuruPermissionState.Granted) "Granted" else "Request")
			}
		}
		HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
	}
}

/**
 * UI row for a group of permissions.
 */
@Composable
fun MultiplePermissionItem(
	name: String,
	states: Map<String, YuruPermissionState>,
	onRequest: () -> Unit,
) {
	val allGranted = states.allAreGranted

	Column(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(text = name, fontWeight = FontWeight.Bold)
				states.forEach { (permission, state) ->
					Text(
						text = "${permission.split(".").last()}: ${state.name}",
						style = MaterialTheme.typography.bodySmall,
						color = when (state) {
							YuruPermissionState.Granted -> Color(0xFF4CAF50)
							YuruPermissionState.Denied -> Color(0xFFF44336)
							YuruPermissionState.PermanentlyDenied -> Color(0xFFB71C1C)
							else -> Color.Gray
						}
					)
				}
			}
			Button(
				onClick = onRequest,
				enabled = !allGranted
			) {
				Text(if (allGranted) "All Granted" else "Request")
			}
		}
		HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
	}
}

/**
 * Dialog shown when a permission is permanently denied, guiding the user to settings.
 */
@Composable
fun SettingsDialog(
	permissionName: String,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Permission Permanently Denied") },
		text = { Text("The $permissionName permission is permanently denied. You need to enable it manually in settings to use this feature.") },
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text("Go to Settings")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}
