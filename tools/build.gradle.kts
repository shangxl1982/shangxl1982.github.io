plugins {
    `java-library`
    application
}

application {
    mainClass.set("org.hyperkv.lsmplus.tools.DiagnosticTool")
}

dependencies {
    implementation(project(":lsmplus-api"))
    implementation(project(":lsmplus-storage"))
    implementation(project(":lsmplus-kvstore"))
    
    implementation("com.google.protobuf:protobuf-java:4.34.1")
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.hyperkv.lsmplus.tools.DiagnosticTool"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
}

tasks.test {
    useJUnitPlatform()
}
