import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

/**
 * Compile-only stubs of the Simple Books 1.3.0 API (tools/simplebooks-api-stub). They are never packaged;
 * at runtime Simple Books loads SimpleBooksCompat itself when it is installed.
 */
fun Project.addSimpleBooksApi() {
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    if (sourceSets.findByName("simplebooksApi") != null) return
    val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    val api = sourceSets.create("simplebooksApi")
    api.java.setSrcDirs(listOf(rootProject.file("tools/simplebooks-api-stub")))
    api.resources.setSrcDirs(emptyList<Any>())
    afterEvaluate {
        api.compileClasspath = main.compileClasspath
        val mainCompile = tasks.named<JavaCompile>("compileJava").get()
        tasks.named<JavaCompile>("compileSimplebooksApiJava") {
            sourceCompatibility = mainCompile.sourceCompatibility
            targetCompatibility = mainCompile.targetCompatibility
            javaCompiler.set(mainCompile.javaCompiler)
        }
        mainCompile.dependsOn(tasks.named("compileSimplebooksApiJava"))
        mainCompile.classpath += files(api.java.classesDirectory)
    }
}
