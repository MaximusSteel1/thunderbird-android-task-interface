plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "net.thunderbird.feature.taskmail.api"
    resourcePrefix = "taskmail_api_"
}

dependencies {
    implementation(projects.core.ui.navigation)

    testImplementation(libs.assertk)
}

codeCoverage {
    lineCoverage = 0
}
