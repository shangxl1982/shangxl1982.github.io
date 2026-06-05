plugins {
    `java-library`
}

dependencies {
    implementation(project(":lsmplus-api"))
    implementation(project(":lsmplus-utils"))
    implementation(project(":lsmplus-storage"))
    implementation(project(":lsmplus-exception"))
    implementation(project(":lsmplus-monitoring"))
    
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
