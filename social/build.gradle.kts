plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish) // Maven Publish plugin
}

android {
    namespace = "com.nidoham.social"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Firebase dependencies (as before)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// JitPack-ready Maven publishing
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.nidoham"   // Must match GitHub username
                artifactId = "Social"             // Case-sensitive
                version = "1.0.8"                 // Use tags or version numbers

                pom {
                    name.set("Social Library")
                    description.set("A social networking library for Android with Firebase integration")
                    url.set("https://github.com/nidoham/Social")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("nidoham")
                            name.set("Nidoham")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/nidoham/Social.git")
                        developerConnection.set("scm:git:ssh://github.com/nidoham/Social.git")
                        url.set("https://github.com/nidoham/Social")
                    }
                }
            }
        }
    }
}
