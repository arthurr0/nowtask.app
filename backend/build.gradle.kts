import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    // The plugin sits on the whole build's classpath, but only the :app module, which produces the jar, applies it
    id("org.springframework.boot") version "4.1.0" apply false
}

allprojects {
    group = "app.nowtask"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    dependencies {
        // BOM Spring Boota zamiast wtyczki io.spring.dependency-management
        "api"(platform(SpringBootPlugin.BOM_COORDINATES))
        "testImplementation"(platform(SpringBootPlugin.BOM_COORDINATES))
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
}
