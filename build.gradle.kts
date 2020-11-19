plugins {
	// application
    kotlin("js") version "1.3.61"
}

repositories {
    mavenCentral()
}

// application {
// 	mainClassName = "symbolic.MainKt"
// }


dependencies {
    implementation(kotlin("stdlib-js"))
}

kotlin.target.browser { }
