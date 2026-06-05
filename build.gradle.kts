plugins {
    id("java")
    id("com.google.protobuf") version "0.9.4" apply false
}

group = "org.hyperkv.lsmplus"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("com.google.protobuf:protobuf-java:4.34.1")
        implementation("org.slf4j:slf4j-api:2.0.17")
        runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
        testImplementation(platform("org.junit:junit-bom:6.0.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core:5.23.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
