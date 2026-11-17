plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.11.0"
}

group = "com.arcade"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    useJUnitPlatform()
    filter {
        excludeTestsMatching("*IT")
    }
}

// 2. Integrációs tesztek: csak az *IT végződésű osztályok futnak
val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (*IT) using Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*IT")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/*.exec"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

tasks.build {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}