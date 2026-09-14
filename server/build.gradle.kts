plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktor)
    `java-test-fixtures`
}

group = "com.kazox.aufschlag"
version = "1.0.0"
application {
    mainClass = "com.kazox.aufschlag.ApplicationKt"
}

tasks.named<JavaExec>("run") {
    // Dev-only JWT signing secret so `./gradlew :server:run` needs no env setup.
    // AppConfig deliberately has no fallback — production must set JWT_SECRET.
    if (environment["JWT_SECRET"] == null) {
        environment("JWT_SECRET", "dev-only-secret-do-not-use-in-production")
    }
    // Same for mail: log instead of send unless a real MAIL_API_KEY is provided.
    if (environment["MAIL_API_KEY"] == null && environment["MAIL_LOG_ONLY"] == null) {
        environment("MAIL_LOG_ONLY", "true")
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverBodyLimit)
    implementation(libs.ktor.serverForwardedHeader)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javaTime)
    implementation(libs.javaJwt)
    implementation(libs.bcrypt)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.testcontainers.postgresql)

    // TestDatabase/TestServer (Testcontainers Postgres + a real embedded server) are exposed as
    // test fixtures so client modules can run smoke tests against a real embedded server.
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.ktor.serverCore)
    testFixturesImplementation(libs.ktor.serverNetty)
    testFixturesImplementation(libs.hikari)
    testFixturesApi(libs.exposed.core)
    testFixturesApi(libs.exposed.jdbc)
}
