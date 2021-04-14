import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

val envoyControlPlaneVersion: String by project
val envoyControlPlaneSha256: String by project
val projectGroup: String by project
val projectDescription: String by project

plugins {
    `java-library`
    kotlin("jvm")
    id("com.github.ben-manes.versions")
    id("com.google.protobuf")
    id("de.undercouch.download")
    id("org.jlleitschuh.gradle.ktlint")

    // Publish build artifacts to an Apache Maven repository
    `maven-publish`
}

group = projectGroup
description = projectDescription
version = if (System.getenv("GITHUB_REF") == "refs/tags/v$envoyControlPlaneVersion") {
    envoyControlPlaneVersion
} else {
    "$envoyControlPlaneVersion-SNAPSHOT"
}

repositories {
    gradlePluginPortal()
}

// Publish package
publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/veehaitch/${project.name}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Download, verify, and extract Enovy Java control plane

val envoyControlPlaneZipFilePath = "${buildDir.absolutePath}/tmp/envoy-control-plane-v$envoyControlPlaneVersion.zip"

tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadEnvoyControlPlaneZip") {
    description = "Task to automatically download the Envoy Control Plane (v$envoyControlPlaneVersion)"
    group = "Envoy Control Plane Protos"
    src("https://github.com/envoyproxy/java-control-plane/archive/v$envoyControlPlaneVersion.zip")
    dest(File(envoyControlPlaneZipFilePath))
    overwrite(true)
    onlyIfModified(true)
    useETag("all")
}

tasks.register<de.undercouch.gradle.tasks.download.Verify>("verifyEnvoyControlPlaneZip") {
    dependsOn("downloadEnvoyControlPlaneZip")
    src(File(envoyControlPlaneZipFilePath))
    algorithm("SHA-256")
    checksum(envoyControlPlaneSha256)
}

tasks.register<Copy>("deflateEnvoyControlPlaneZip") {
    dependsOn("verifyEnvoyControlPlaneZip")
    description = "Task to automatically download and unzip the Envoy Control Plane (v$envoyControlPlaneVersion)"
    group = "Envoy Control Plane Protos"
    from(zipTree(envoyControlPlaneZipFilePath)) {
        include("java-control-plane-$envoyControlPlaneVersion/api/src/main/proto/**")
        eachFile {
            @Suppress("SpreadOperator")
            relativePath = RelativePath(
                true,
                *relativePath.segments
                    .dropWhile { it != "proto" }.drop(1).toTypedArray()
            )
        }
        includeEmptyDirs = false
    }
    into("src/main/proto")
}

tasks.clean {
    delete()
    val protoSrcDir = File("$projectDir/src/main/proto/")
    protoSrcDir.listFiles { _, s -> s != "README.md" }?.forEach {
        delete(it)
    }
}

// Dependencies

val gRPCKotlinStubVersion: String by project
val gRPCVersion: String by project
val javaxAnnotationApiVersion: String by project
val kotlinCoroutinesVersion: String by project
val protobufVersion: String by project

dependencies {
    protobuf(files())
    implementation(kotlin("stdlib"))
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")

    api("com.google.protobuf:protobuf-java-util:$protobufVersion")
    api("io.grpc:grpc-kotlin-stub:$gRPCKotlinStubVersion")
}

// Java 11+

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// GrpcKt

protobuf {
    protoc {
        if (osdetector.classifier == "osx-aarch_64") {
            // XXX: protoc does not yet support Apple Silicon
            artifact = "com.google.protobuf:protoc:$protobufVersion:osx-x86_64"
        } else {
            artifact = "com.google.protobuf:protoc:$protobufVersion"
        }
    }
    plugins {
        id("grpc") {
            if (osdetector.classifier == "osx-aarch_64") {
                // XXX: protoc does not yet support Apple Silicon
                artifact = "io.grpc:protoc-gen-grpc-java:$gRPCVersion:osx-x86_64"
            } else {
                artifact = "io.grpc:protoc-gen-grpc-java:$gRPCVersion"
            }
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$gRPCKotlinStubVersion:jdk7@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}

tasks.withType<GenerateProtoTask> {
    dependsOn("deflateEnvoyControlPlaneZip")
}

tasks.withType<ProcessResources> {
    dependsOn("deflateEnvoyControlPlaneZip")
}

// Dependency locking

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}
