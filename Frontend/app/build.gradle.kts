plugins {
    alias(libs.plugins.android.application)
}

val itineraryAssets = tasks.register<Sync>("syncItineraryAssets") {
    from(rootProject.file("../docs/itinerary-contract")) {
        include("itinerary.schema.json", "examples/*.json")
    }
    from(rootProject.file("../docs/profile")) {
        include("profile.schema.json")
    }
    into(layout.buildDirectory.dir("generated/itineraryAssets"))
}

android {
    sourceSets.getByName("main").assets.directories.add(layout.buildDirectory.dir("generated/itineraryAssets").get().asFile.path)
    namespace = "com.example.odyssey"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.odyssey"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("preBuild").configure { dependsOn(itineraryAssets) }

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
