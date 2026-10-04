plugins { application }
repositories { mavenCentral() }
dependencies {
    implementation("dev.langchain4j:langchain4j-typesafe:1.21.0-beta31")
    implementation("dev.langchain4j:langchain4j-http-client-jdk:1.21.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
application { mainClass.set("NativeJevValidation") }
