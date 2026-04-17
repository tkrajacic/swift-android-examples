import java.io.File

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.weatherlib"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.swift.swiftkit:swiftkit-core:1.0-SNAPSHOT")
}

// Helper function to get swiftly executable path
fun getSwiftlyPath(): File {
    val fromConfig = project.findProperty("swiftly.path") as? String ?: System.getenv("SWIFTLY_PATH")
    if (fromConfig != null) {
        return file(fromConfig)
    }

    // Try to find swiftly in common locations
    val homeDir = System.getProperty("user.home")
    val possiblePaths = listOf(
        "$homeDir/.swiftly/bin/swiftly",
        "$homeDir/.local/share/swiftly/bin/swiftly",
        "$homeDir/.local/bin/swiftly",
        "/usr/local/bin/swiftly",
        "/opt/homebrew/bin/swiftly",
        "/root/.local/share/swiftly/bin/swiftly"
    )

    for (path in possiblePaths) {
        val f = file(path)
        if (f.exists()) {
            return f
        }
    }

    throw GradleException("Swift SDK path not found. Please set swiftly.path in the gradle.properties file or set SWIFTLY_PATH environment variable.")
}

fun getSwiftSDKPath(): File {
    val fromConfig = project.findProperty("swift.sdk.path") as? String ?: System.getenv("SWIFT_SDK_PATH")
    if (fromConfig != null) {
        return file(fromConfig)
    }

    // If no custom path is set, try to find the Swift SDK in common locations.
    val homeDir = System.getProperty("user.home")
    val possiblePaths = listOf(
        "$homeDir/Library/org.swift.swiftpm/swift-sdks/",     // Common on macOS
        "$homeDir/.config/swiftpm/swift-sdks/",               // Common on Linux
        "$homeDir/.swiftpm/swift-sdks/",                      // Older location
        "/root/.swiftpm/swift-sdks/"                            // For builds running as root
    )

    // Iterate through the list of possible paths.
    for (path in possiblePaths) {
        val f = file(path)
        if (f.exists()) {
            return f
        }
    }

    throw GradleException("Swift SDK path not found. Please set swift.sdk.path in the gradle.properties file or set SW_SDK_PATH environment variable.")
}

// List of Swift runtime libraries we want to include
val swiftRuntimeLibs = listOf(
    "swiftCore", "swift_Concurrency", "swift_StringProcessing", "swift_RegexParser",
    "swift_Builtin_float", "swift_math", "swiftAndroid", "dispatch",
    "BlocksRuntime", "swiftSwiftOnoneSupport", "swiftDispatch", "Foundation",
    "FoundationEssentials", "FoundationInternationalization", "_FoundationICU", "swiftSynchronization"
)
val sdkName = "swift-6.3.1-RELEASE_android.artifactbundle"
val swiftVersion = "6.3.1"
val minSdk = android.defaultConfig.minSdk!!

/**
 * Android ABIs and their Swift triple mappings
 */
val abis = mapOf(
    "arm64-v8a"     to mapOf("triple" to "aarch64-unknown-linux-android$minSdk", "androidSdkLibDirectory" to "swift-aarch64", "ndkDirectory" to "aarch64-linux-android"),
    "armeabi-v7a"   to mapOf("triple" to "armv7-unknown-linux-android$minSdk", "androidSdkLibDirectory" to "swift-armv7", "ndkDirectory" to "arm-linux-android"),
    "x86_64"        to mapOf("triple" to "x86_64-unknown-linux-android$minSdk", "androidSdkLibDirectory" to "swift-x86_64", "ndkDirectory" to "x86_64-linux-android")
)

fun parseAbiList(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

val explicitSwiftAbis = (project.findProperty("swift.abis") as? String)
    ?: System.getenv("SWIFT_ABIS")
val injectedBuildAbi = project.findProperty("android.injected.build.abi") as? String
val requestedTasks = gradle.startParameter.taskNames
val isReleaseLikeBuild = requestedTasks.any {
    it.contains("release", ignoreCase = true) ||
        it.contains("bundle", ignoreCase = true) ||
        it.contains("publish", ignoreCase = true)
}

val hostDefaultAbi = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64-v8a"
    "x86_64", "amd64" -> "x86_64"
    else -> null
}

val selectedSwiftAbisSource = when {
    explicitSwiftAbis != null -> "swift.abis/SWIFT_ABIS"
    isReleaseLikeBuild -> "release-default-all"
    !injectedBuildAbi.isNullOrBlank() -> "android.injected.build.abi"
    hostDefaultAbi != null -> "host-os.arch"
    else -> "default-all"
}

val selectedSwiftAbis = when (selectedSwiftAbisSource) {
    "swift.abis/SWIFT_ABIS" -> parseAbiList(explicitSwiftAbis!!)
    "release-default-all" -> abis.keys.toList()
    "android.injected.build.abi" -> parseAbiList(injectedBuildAbi!!)
    "host-os.arch" -> listOf(hostDefaultAbi!!)
    else -> abis.keys.toList()
}

val unknownSwiftAbis = selectedSwiftAbis.filter { it !in abis.keys }
if (unknownSwiftAbis.isNotEmpty()) {
    throw GradleException("Unknown ABI(s) for Swift build: ${unknownSwiftAbis.joinToString(", ")}. Supported ABIs: ${abis.keys.joinToString(", ")}")
}

val generatedJniLibsDir = layout.buildDirectory.dir("generated/jniLibs")
val swiftSdkPath = "${getSwiftSDKPath().absolutePath}/$sdkName"
val sharedSwiftLibDir = layout.projectDirectory.dir("../../Shared/weather-lib")
val swiftPackageFile = sharedSwiftLibDir.file("Package.swift")
val swiftSourcesDir = sharedSwiftLibDir.dir("Sources/WeatherLibrary")
val generatedJavaDir = sharedSwiftLibDir.dir(".build/plugins/outputs/${layout.projectDirectory.asFile.name.lowercase()}/WeatherLibrary/destination/JExtractSwiftPlugin/src/generated/java")

abstract class BuildSwiftTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

val buildSwiftAll = tasks.register<BuildSwiftTask>("buildSwiftAll") {
    group = "build"
    description = "Builds the Swift code for all Android ABIs."
    outputDir.set(generatedJavaDir)

    // Invalidate only when the effective ABI set changes.
    inputs.property("selectedSwiftAbis", selectedSwiftAbis)

    doFirst {
        println("Swift ABI selection ($selectedSwiftAbisSource): ${selectedSwiftAbis.joinToString(", ")}")
    }
}
// Create a build task for each ABI
val buildSwiftTasks = mutableMapOf<String, TaskProvider<Exec>>()
abis.forEach { (abi, info) ->
    val task = tasks.register<Exec>("buildSwift${abi.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Builds the Swift code for the $abi ABI."

        // Ensure Swift source/config changes invalidate this producer task.
        inputs.file(swiftPackageFile)
        inputs.dir(swiftSourcesDir)
        inputs.property("swiftTriple", info["triple"]!!)
        inputs.property("swiftVersion", swiftVersion)
        inputs.property("swiftSdkPath", swiftSdkPath)

        // We can't conditionally import the swift-java build plugin in Package.swift
        environment("SWIFT_JAVA_BUILD", "1")
        inputs.property("swiftJavaBuild", "1")

        doFirst {
            println("Building Swift for $abi (${info["triple"]})...")
        }
        val outputsDir = layout.projectDirectory.dir("../../Shared/weather-lib/.build/${info["triple"]}/debug")
        outputs.dir(outputsDir)
        workingDir = layout.projectDirectory.asFile
        executable = getSwiftlyPath().absolutePath

        args("run", "swift", "build", "+$swiftVersion", "--swift-sdk", info["triple"]!!, "--disable-sandbox", "--package-path", "../../Shared/weather-lib")
    }

    buildSwiftTasks[abi] = task
}

buildSwiftAll.configure {
    dependsOn(selectedSwiftAbis.map { buildSwiftTasks.getValue(it) })
}

val copyJniLibs = tasks.register<Copy>("copyJniLibs") {
    dependsOn(buildSwiftAll)
    inputs.property("selectedSwiftAbis", selectedSwiftAbis)

    selectedSwiftAbis.forEach { abi ->
        val info = abis.getValue(abi)
        from(layout.projectDirectory.dir("../../Shared/weather-lib/.build/${info["triple"]}/debug")) {
            include("*.so")
            into(abi)
        }
        from(file("$swiftSdkPath/swift-android/ndk-sysroot/usr/lib/${info["ndkDirectory"]}/libc++_shared.so")) {
            into(abi)
        }
        doFirst { println("Copying Swift runtime libraries for $abi...") }
        from(swiftRuntimeLibs.map { "$swiftSdkPath/swift-android/swift-resources/usr/lib/${info["androidSdkLibDirectory"]}/android/lib$it.so" }) {
            into(abi)
        }
    }
    into(generatedJniLibsDir)
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(generatedJniLibsDir.get().asFile.absolutePath)
        variant.sources.java?.addGeneratedSourceDirectory(buildSwiftAll, BuildSwiftTask::outputDir)
    }
}

tasks.named("preBuild").configure { dependsOn(copyJniLibs) }