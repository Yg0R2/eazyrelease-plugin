plugins {
    id("org.eazyportal.plugin.kotlin-project-convention")
}

tasks.jar {
    isEnabled = false
}

allprojects {
    repositories {
        maven {
            name = "Jenkins releases"
            url = uri("https://repo.jenkins-ci.org/releases")
        }

        mavenCentral()
    }
}

subprojects {
    if (name != "core-acceptance-test") {
        afterEvaluate {
            dependencies {
                testImplementation("org.assertj", "assertj-core", "+")
                testImplementation("org.junit.jupiter", "junit-jupiter", "+")
                testImplementation("org.mockito", "mockito-inline", "+")
                testImplementation("org.mockito.kotlin", "mockito-kotlin", "+")
            }
        }
    }
}
