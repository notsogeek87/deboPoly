plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 intègre Kotlin : déclaré ici uniquement pour fixer la version de Kotlin (catalogue de versions).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
}
