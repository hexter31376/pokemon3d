plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
}

group = "com.pokemon3d"
version = "0.0.1-SNAPSHOT"
description = "pokemon3d"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Runtime model data is mounted externally, never packaged with the application.
tasks.processResources {
    exclude("**/*.glb", "**/manifest.json", "**/manifest-*.json")
}

val testManifestTools = tasks.register<Exec>("testManifestTools") {
    group = "verification"
    description = "Tests the metadata extractor using tiny generated GLBs (requires Python 3.10+)."
    commandLine("python3", "-m", "unittest", "discover", "-s", "tools/tests", "-v")
}

tasks.test {
    dependsOn(testManifestTools)
}

tasks.register<JavaExec>("verifyModelManifest") {
    group = "verification"
    description = "Loads an external manifest with the production repository (-PmanifestPath=...)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.pokemon3d.api.model3d.infrastructure.manifest.VerifyModelManifestKt")
    args(providers.gradleProperty("manifestPath").getOrElse(""))
}

springBoot {
    mainClass.set("com.pokemon3d.api.Pokemon3dApplicationKt")
}
