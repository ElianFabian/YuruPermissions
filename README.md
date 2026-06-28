[![](https://jitpack.io/v/ElianFabian/YuruPermissions.svg)](https://jitpack.io/#ElianFabian/YuruPermissions)

# YuruPermissions

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**YuruPermissions** is a lightweight, reactive, and testing-friendly Android permissions library. It solves the biggest headaches of the standard Android Permission API by providing reliable states and allowing you to control permissions from anywhere—especially your **ViewModels**.

## Why YuruPermissions?

*   **🚀 Control Anywhere:** No more being forced to handle permissions inside `Activity` or `Fragment`. Request and observe permissions directly from your `ViewModel` or wherever you want.
*   **💎 Reliable Permission States:** Android's native API is ambiguous about whether a permission is "Permanently Denied." Yuru provides 4 clear, reliable states:
    *   `NotDetermined`: Never asked or status unknown.
    *   `Granted`: User said yes.
    *   `Denied`: User said no, but we can ask again (Show Rationale).
    *   `PermanentlyDenied`: User said "Don't ask again" or denied multiple times.
*   **⚡ Reactive by Design:** Every permission controller exposes a `StateFlow<YuruPermissionState>`, making it easy to drive your UI reactively with Jetpack Compose or View system.
*   **🧪 Built for Testing:** Ships with `FakeYuru`, allowing you to unit test your permission-dependent logic without ever touching the Android framework.

Add the JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}
```

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
	implementation("com.github.ElianFabian:YuruPermissions:$version")
}
```

## Basic Usage

### 1. Define your Controller
You can define controllers for single or multiple permissions. These are usually held in your `ViewModel`.

```kotlin
class MyViewModel : ViewModel() {
	
	private val yuru = Yuru.getInstance()
	
    // Single permission
    private val cameraController = yuru.singlePermissionController(Manifest.permission.CAMERA)

    // Multiple permissions
    private val locationController = yuru.multiplePermissionController(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    
    // Observe state reactively
    val cameraState = cameraController.state 
}
```

### 2. Request Permissions
Simply call `.request()` from a coroutine. It suspends until the user makes a choice.

```kotlin
fun onCameraIconClicked() {
    viewModelScope.launch {
        val result = cameraController.request()
        when (result) {
            YuruPermissionState.Granted -> openCamera()
            YuruPermissionState.Denied -> showRationale()
            YuruPermissionState.PermanentlyDenied -> showSettingsDialog()
            else -> {}
        }
    }
}
```

## Unit Testing with FakeYuru

One of Yuru's strongest features is its testability. Use `createSimulatedYuruEnvironment()` to test your ViewModels in isolation.

```kotlin
class MyViewModelTest {
    private val fakeYuru = Yuru.createSimulatedYuruEnvironment()
    private lateinit var viewModel: MyViewModel

    @Test
    fun `when camera permission is granted, feature is enabled`() = runTest {
        val controller = fakeYuru.singlePermissionController(Manifest.permission.CAMERA)
        
        // Simulate user interaction
        controller.accept() 
        
        // Verify your logic
        assertTrue(viewModel.uiState.value.isFeatureEnabled)
    }
}
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
