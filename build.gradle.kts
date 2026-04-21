plugins {
    alias(libs.plugins.cloud.buildLogic.rootProject.publishing)
    alias(libs.plugins.cloud.buildLogic.rootProject.spotless)
}

allprojects {
    version = System.getenv("VERSION") ?: "dev"
    group = "me.whereareiam"
}

spotlessPredeclare {
    kotlin { ktlint(libs.versions.ktlint.get()) }
    kotlinGradle { ktlint(libs.versions.ktlint.get()) }
}

tasks {
    spotlessCheck {
        dependsOn(gradle.includedBuild("build-logic").task(":spotlessCheck"))
    }
    spotlessApply {
        dependsOn(gradle.includedBuild("build-logic").task(":spotlessApply"))
    }
}
