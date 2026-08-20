dependencies {
    api(project(":shared"))
    implementation(project(":tasks"))
    implementation(project(":automation"))
    implementation(project(":identity"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
