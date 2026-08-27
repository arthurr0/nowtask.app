plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":identity"))
    implementation(project(":workspace"))
    implementation(project(":tasks"))
    implementation(project(":automation"))
    implementation(project(":analytics"))
    implementation(project(":integrations"))
    implementation(project(":realtime"))
    implementation(project(":exports"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")

    // Spring Boot 4 split autoconfiguration into separate modules, flyway-core alone does not run the migrations
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.postgresql:postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "nowtask.jar"
}
