import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

plugins { base }

// Installed Apache Jena binaries; otherwise use riot/rdfcompare from PATH.
val jenaHome = providers.gradleProperty("jenaHome").orElse(providers.environmentVariable("JENA_HOME"))
val riot = jenaHome.orNull?.let { "$it/bin/riot" } ?: "riot"
val rdfcompare = jenaHome.orNull?.let { "$it/bin/rdfcompare" } ?: "rdfcompare"

fileTree("publication") { include("*.properties") }.files.sorted().forEach { recipeFile ->
    val recipe = Properties().apply { recipeFile.inputStream().use { load(it) } }
    fun value(key: String) = requireNotNull(recipe.getProperty(key)) { "Missing '$key' in $recipeFile" }
    val suffix = recipeFile.nameWithoutExtension.replaceFirstChar { it.uppercaseChar() }
    val url = value("url")
    val format = value("format")
    require(format in setOf("RDF/XML", "TURTLE")) { "Expected RDF/XML or TURTLE in $recipeFile" }
    val snapshot = file(value("snapshot"))
    val published = file(value("output"))
    val stage = layout.buildDirectory.dir("publication/${recipeFile.nameWithoutExtension}").get().asFile
    val downloaded = stage.resolve("download.${snapshot.extension}")

    val fetch = tasks.register("fetch$suffix") {
        description = "Download the pinned source into build/ without changing published files."
        doLast {
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build()
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
                .header("Accept", if (format == "TURTLE") "text/turtle" else "application/rdf+xml").build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()} fetching $url" }
            stage.mkdirs()
            downloaded.writeBytes(response.body())
        }
    }

    // Both paths use exactly the same two Jena commands before publishing.
    mapOf("refresh" to downloaded, "export" to snapshot).forEach { (operation, source) ->
        val stepSuffix = operation.replaceFirstChar { it.uppercaseChar() } + suffix
        val turtle = stage.resolve("$operation.ttl")
        val convert = tasks.register<Exec>("convert$stepSuffix") {
            if (operation == "refresh") dependsOn(fetch)
            commandLine(riot, "--syntax=$format", "--base=$url", "--check", "--strict",
                "--formatted=TURTLE", "--set", "ttl:directiveStyle=at", source.absolutePath)
            // Buffer stdout so a failed command leaves no open file or partial Turtle.
            doFirst { standardOutput = ByteArrayOutputStream() }
            doLast {
                stage.mkdirs()
                turtle.writeBytes((standardOutput as ByteArrayOutputStream).toByteArray())
            }
        }
        val compare = tasks.register<Exec>("compare$stepSuffix") {
            dependsOn(convert)
            commandLine(rdfcompare, source.absolutePath, turtle.absolutePath, format, "TURTLE", url, url)
        }
        tasks.register("$operation$suffix") {
            group = "publication"
            description = if (operation == "refresh") "Fetch, convert, compare, then publish."
                else "Convert the reviewed snapshot, compare, then publish offline."
            dependsOn(compare)
            doLast {
                if (operation == "refresh") source.copyTo(snapshot.apply { parentFile.mkdirs() }, overwrite = true)
                turtle.copyTo(published.apply { parentFile.mkdirs() }, overwrite = true)
            }
        }
    }

    val validate = tasks.register<Exec>("validate$suffix") {
        mustRunAfter("refresh$suffix", "export$suffix")
        commandLine(riot, "--syntax=TURTLE", "--base=$url", "--check", "--strict",
            "--validate", published.absolutePath)
    }
    val check = tasks.register<Exec>("check$suffix") {
        group = "verification"
        description = "Validate public Turtle and compare it with the reviewed source snapshot."
        dependsOn(validate)
        mustRunAfter("refresh$suffix", "export$suffix")
        commandLine(rdfcompare, snapshot.absolutePath, published.absolutePath, format, "TURTLE", url, url)
    }
    tasks.check { dependsOn(check) }
}

val commonRepo = providers.gradleProperty("commonRepo").orElse("../acqf-qcp-public")
tasks.register<Exec>("buildValidation") {
    group = "publication"
    description = "Build the ETF validation artifact using the pinned common model."
    commandLine("python3", "tools/build-exchange-model.py", "--common-repo", commonRepo.get())
}
val checkValidation = tasks.register<Exec>("checkValidation") {
    group = "verification"
    description = "Check the ETF export and validate it without QCP preprocessing."
    mustRunAfter("buildValidation")
    jenaHome.orNull?.let { environment("JENA_HOME", it) }
    commandLine("python3", "tools/build-exchange-model.py", "--common-repo", commonRepo.get(), "--check", "--test")
}
tasks.check { dependsOn(checkValidation) }
