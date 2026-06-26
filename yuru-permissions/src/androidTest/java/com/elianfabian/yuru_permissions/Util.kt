package com.elianfabian.yuru_permissions

import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

fun UiDevice.findAllowButton() = findObject(
	UiSelector().textMatches("(?i)Allow|While using the app|Precise")
)

fun UiDevice.findDenyButton() = findObject(
	UiSelector().textMatches("(?i)Don['’]t allow|Deny")
)

fun UiDevice.findDontAskAgainCheckbox() = findObject(
	UiSelector().textMatches("(?i)Don['’]t ask again")
)
