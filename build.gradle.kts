plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "dev.modgraph.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.24.1")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.electronwill.night-config:toml:3.6.7")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testImplementation("org.apache.logging.log4j:log4j-core:2.24.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
