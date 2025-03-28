plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("androidx.room")

}

android {
    namespace = "com.sakhi.mindfulminutes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sakhi.mindfulminutes"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    room {
        schemaDirectory("$projectDir/schemas")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation ("com.github.AnyChart:AnyChart-Android:1.1.5")
    implementation ("com.google.firebase:firebase-auth:23.2.0")
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.firebase:firebase-auth:23.2.0")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.3")

        //ROOMDATABASE DEPENDENCIES
    val room_version = "2.6.1"

    implementation("androidx.room:room-runtime:$room_version")

    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$room_version")

    // optional - RxJava2 support for Room
    implementation("androidx.room:room-rxjava2:$room_version")

    // optional - RxJava3 support for Room
    implementation("androidx.room:room-rxjava3:$room_version")

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation("androidx.room:room-guava:$room_version")

    // optional - Test helpers
    testImplementation("androidx.room:room-testing:$room_version")

    // optional - Paging 3 Integration
    implementation("androidx.room:room-paging:$room_version")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    // Coroutine Support (For background tasks)
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation ("com.squareup.picasso:picasso:2.71828")
    implementation("com.google.firebase:firebase-firestore:25.1.3")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}