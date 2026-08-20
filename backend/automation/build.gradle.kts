dependencies {
    api(project(":shared"))
    implementation(project(":identity"))
    implementation(project(":integrations"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
