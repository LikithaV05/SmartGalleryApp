import java.io.File

plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

val localAppData = System.getenv("LOCALAPPDATA")
if (!localAppData.isNullOrBlank()) {
    allprojects {
        layout.buildDirectory.set(File(localAppData, "SmartGalleryAppBuild/${project.name}"))
    }
}
