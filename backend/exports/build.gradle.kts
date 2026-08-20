dependencies {
    api(project(":shared"))
    implementation(project(":tasks"))
    implementation(project(":workspace"))
    implementation(project(":identity"))
    implementation(project(":automation"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.apache.poi:poi-ooxml:5.4.1")
}
