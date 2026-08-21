dependencies {
    api(project(":shared"))
    // Deliberately without a dependency on :tasks. The module reacts to events and knows tasks by key.
    implementation(project(":identity"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
}
