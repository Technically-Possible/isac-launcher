plugins {
    id("com.android.application") version "8.13.2" apply false
    // Kotlin 2.2 keeps the editor's Kotlin plugin able to read the stdlib metadata.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
