plugins {
    application
}

version = providers.fileContents(layout.projectDirectory.file("VERSION")).asText.get().trim()

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("caretlang.Main")
    applicationName = "caret"
}

tasks.named<CreateStartScripts>("startScripts") {
    outputDir = layout.buildDirectory.dir("caret-start-scripts").get().asFile
}

distributions {
    main {
        contents {
            from("README.md")
            from("LICENSE")
            from("NOTICE")
            from("examples") {
                into("examples")
            }
        }
    }
}

dependencies {
    implementation("org.jline:jline:3.30.0")
    compileOnly("org.jetbrains:annotations:26.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
    doFirst {
        if (args.isEmpty()) {
            throw GradleException("The interactive REPL requires a real terminal. Run ./repl.sh instead.")
        }
    }
}
