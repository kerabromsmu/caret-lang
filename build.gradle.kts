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

val embeddingJar = tasks.register<Jar>("embeddingJar") {
    group = "distribution"
    description = "Builds the Java embedding library JAR."
    archiveBaseName.set("caret-embedding")
    from(sourceSets.main.get().output)
}

val embeddingJavadoc = tasks.register<Javadoc>("embeddingJavadoc") {
    group = "documentation"
    description = "Generates API documentation for the public Java embedding facade."
    source = fileTree("src/main/java/caretlang/embedding") { include("**/*.java") }
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    destinationDir = layout.buildDirectory.dir("docs/embedding-javadoc").get().asFile
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.register<Zip>("embeddingSdkZip") {
    group = "distribution"
    description = "Builds the standalone Java embedding SDK."
    dependsOn(embeddingJar, embeddingJavadoc)
    archiveBaseName.set("caret-java-sdk")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    into("caret-java-sdk-${project.version}") {
        from("EMBEDDING.md") { rename { "README.md" } }
        from("LICENSE", "NOTICE")
        into("lib") {
            from(embeddingJar.flatMap { it.archiveFile })
            from(configurations.runtimeClasspath)
        }
        into("examples") {
            from("src/main/java/caretlang/examples/EmbeddingExample.java")
            from("examples/embedding.caret")
        }
        into("docs/javadoc") { from(embeddingJavadoc.map { it.destinationDir }) }
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
            from("EMBEDDING.md")
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
