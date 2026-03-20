plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

fun String.toBuildConfigStringLiteral(): String {
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

val taskMailDefaultBotMailbox = (project.findProperty("taskmailBotMailboxAddress") as String?)
    ?.trim()
    .orEmpty()

android {
    namespace = "net.thunderbird.feature.taskmail.internal"
    resourcePrefix = "taskmail_"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "TASKMAIL_DEFAULT_BOT_MAILBOX",
            taskMailDefaultBotMailbox.toBuildConfigStringLiteral(),
        )
    }

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
    implementation(libs.jsoup)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.android.account)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.ui.compose.testing)
}

codeCoverage {
    lineCoverage = 0
}
