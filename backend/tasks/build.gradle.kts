dependencies {
    api(project(":shared"))
    implementation(project(":identity"))
    implementation(project(":workspace"))
    implementation(project(":automation"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
