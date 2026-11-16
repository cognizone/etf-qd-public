plugins { java }

val commonRoot = file("common")
check(commonRoot.resolve("publication/publisher.gradle.kts").isFile) {
    "Initialize the pinned dependency first: git submodule update --init"
}
extra["commonRoot"] = commonRoot
extra["product"] = "etf"
apply(from = commonRoot.resolve("publication/publisher.gradle.kts"))

val sourceSets = extensions.getByType<SourceSetContainer>()
fileTree("publication") { include("*.properties") }.files.sorted().forEach { recipeFile ->
    val suffix = recipeFile.nameWithoutExtension.replaceFirstChar { it.uppercaseChar() }
    listOf("refresh", "check").forEach { operation ->
        val task = tasks.register<JavaExec>("$operation$suffix") {
            group = if (operation == "refresh") "publication" else "verification"
            classpath = sourceSets.named("main").get().runtimeClasspath
            mainClass.set("zone.cogni.publication.VocabularyPublication")
            args(rootDir.absolutePath, recipeFile.relativeTo(rootDir).path, operation)
            if (operation == "check") mustRunAfter("refresh$suffix")
        }
        if (operation == "check") tasks.named("check") { dependsOn(task) }
    }
}
