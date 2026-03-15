plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "net.thunderbird.feature.taskmail.internal"
    resourcePrefix = "taskmail_"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.feature.taskmail.api)
    implementation(projects.core.android.account)
    implementation(projects.core.logging.api)
    implementation(projects.core.ui.compose.designsystem)
    implementation(projects.core.ui.compose.theme2.common)
    implementation(projects.core.ui.contract)
    implementation(projects.core.ui.navigation)
    implementation(projects.core.preference.api)
    implementation(projects.core.ui.theme.api)
    implementation(projects.legacy.core)
    implementation(projects.legacy.mailstore)
    implementation(projects.legacy.ui.base)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.android.account)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.ui.compose.testing)
}

codeCoverage {
    lineCoverage = 0
}
