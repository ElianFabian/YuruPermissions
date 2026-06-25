plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "com.elianfabian.yuru_permissions"
	compileSdk = 37

	defaultConfig {
		minSdk = 21

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	kotlin {
		explicitApi()
	}
}

dependencies {
	implementation(libs.activityProvider)
	implementation(libs.androidx.lifecycleProcess)
	implementation(libs.androidx.activity.ktx)
	implementation(libs.kotlinxCoroutinesAndroid)
	testImplementation(libs.junit)
	testImplementation(libs.kotlinxCoroutinesTest)
	androidTestImplementation(libs.kotlinxCoroutinesTest)
	androidTestImplementation(libs.androidx.uiautomator)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
}
