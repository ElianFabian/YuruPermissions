import com.android.build.api.dsl.LibraryExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.android.library) apply false
	id("maven-publish")
}

subprojects {
	val libraryModules = listOf("yuru-permissions")
	if (name in libraryModules) {
		val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
		group = libs.findVersion("yuruProviderGroup").get().requiredVersion
		version = libs.findVersion("yuruProviderVersion").get().requiredVersion

		plugins.withId("com.android.library") {
			plugins.apply("maven-publish")
			configure<LibraryExtension> {
				publishing {
					singleVariant("release") {
						withSourcesJar()
					}
				}
			}
			afterEvaluate {
				extensions.configure<PublishingExtension> {
					publications {
						create<MavenPublication>("release") {
							from(components.findByName("release") ?: components["release"])

							groupId = project.group.toString()
							artifactId = project.name
							version = project.version.toString()
						}
					}
				}
			}
		}
	}
}
