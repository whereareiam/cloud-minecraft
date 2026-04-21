plugins {
    id("org.incendo.cloud-build-logic.publishing")
}

if (!name.endsWith("-bom")) {
    dependencies {
        JavaPlugin.API_CONFIGURATION_NAME(platform(project(":cloud-minecraft-bom")))
    }
}

indra {
    github("whereareiam", "cloud-minecraft") {
        ci(true)
    }
    mitLicense()
}

publishing {
    repositories {
        maven {
            val realm = (
                System.getenv("PUBLISH_REALM")
                    ?: if ((System.getenv("VERSION") ?: "dev").contains("dev", true)) "development" else "release"
                )
                .lowercase()
            url = uri("https://maven.whereareiam.me/$realm")
            credentials {
                username = System.getenv("PUBLISH_USER") ?: ""
                password = System.getenv("PUBLISH_TOKEN") ?: ""
            }
        }
    }
}

javadocLinks {
    defaultJavadocProvider = "https://www.javadocs.dev/{group}/{name}/{version}"
}
