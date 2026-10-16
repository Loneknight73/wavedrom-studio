enablePlugins(ScalaJSPlugin)

import scala.util.control.NonFatal

name := "wavedrom-studio"
version := "0.0.1"
licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))

scalaVersion := "3.3.4"

// Include scalajs-dom for browser DOM and SVG manipulation
libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.8.0",
  "org.scalameta" %%% "munit" % "1.0.4" % Test
)

// Run the main method automatically upon script load
scalaJSUseMainModuleInitializer := true

// FastLink / FullLink output configuration
// Produces target/scala-3.3.4/wavedrom-studio-fastopt/main.js
scalaJSLinkerConfig ~= {
  _.withModuleKind(ModuleKind.NoModule)
   .withSourceMap(true)
}

lazy val launch = taskKey[Unit]("Compile and launch Wavedrom Studio in the browser")
launch := {
  val _ = (Compile / fastLinkJS).value
  val htmlPath = (baseDirectory.value / "index.html").toURI.toString
  val log = streams.value.log
  log.info(s"Launching Wavedrom Studio at: $htmlPath")
  try {
    val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
    val isMac = System.getProperty("os.name").toLowerCase.contains("mac")
    val cmd = if (isWindows) Seq("cmd", "/c", "start", htmlPath)
              else if (isMac) Seq("open", htmlPath)
              else Seq("xdg-open", htmlPath)
    scala.sys.process.Process(cmd).!
  } catch {
    case NonFatal(e) =>
      log.warn(s"Could not automatically open browser: ${e.getMessage}")
      log.info(s"Open index.html manually in your browser: $htmlPath")
  }
}

// The bare minimum needed to run the studio: index.html plus the optimised
// script and the LICENSE that the MIT terms require copies to carry, in
// target/dist/wavedrom-studio-<version>/ and zipped next to it.
// The folder works opened straight from disk or from any static web server.
lazy val dist = taskKey[File]("Package index.html and the optimised script for release")
dist := {
  val log = streams.value.log
  val _ = (Compile / fullLinkJS).value
  val linked = (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value / "main.js"

  val releaseName = s"${name.value}-${version.value}"
  val distDir = target.value / "dist"
  val outDir = distDir / releaseName
  IO.delete(outDir)
  IO.createDirectory(outDir)

  // The source map stays behind, so its reference is dropped from the script
  val script = IO.readLines(linked).filterNot(_.startsWith("//# sourceMappingURL="))
  IO.writeLines(outDir / "main.js", script)

  // The page loads the development build; point it at the packaged script
  val devScript = """<script src="target/[^"]*/main\.js"></script>""".r
  val page = IO.read(baseDirectory.value / "index.html")
  if (devScript.findAllIn(page).length != 1)
    sys.error("index.html: expected exactly one <script src=\"target/.../main.js\"> tag")
  IO.write(outDir / "index.html", devScript.replaceFirstIn(page, """<script src="main.js"></script>"""))

  IO.copyFile(baseDirectory.value / "LICENSE", outDir / "LICENSE")

  val zip = distDir / s"$releaseName.zip"
  val files = Seq("index.html", "main.js", "LICENSE")
  IO.zip(files.map(f => (outDir / f) -> s"$releaseName/$f"), zip, None)

  files.foreach(f => log.info(f"  $f%-12s ${(outDir / f).length / 1024}%5d KB"))
  log.info(s"Release folder: $outDir")
  log.info(s"Release zip:    $zip (${zip.length / 1024} KB)")
  zip
}
