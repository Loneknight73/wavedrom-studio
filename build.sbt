enablePlugins(ScalaJSPlugin)

import scala.util.control.NonFatal

name := "wavedrom-plus"
version := "0.1.0-SNAPSHOT"

scalaVersion := "3.3.4"

// Include WaveDromStudio.scala in the root directory as well as src/main/scala
Compile / unmanagedSources += baseDirectory.value / "WaveDromStudio.scala"

// Include scalajs-dom for browser DOM and SVG manipulation
libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.8.0"
)

// Run the main method automatically upon script load
scalaJSUseMainModuleInitializer := true

// FastLink / FullLink output configuration
// Produces target/scala-3.3.4/wavedrom-plus-fastopt/main.js
scalaJSLinkerConfig ~= {
  _.withModuleKind(ModuleKind.NoModule)
   .withSourceMap(true)
}

lazy val launch = taskKey[Unit]("Compile and launch WaveDrom Studio in the browser")
launch := {
  val _ = (Compile / fastLinkJS).value
  val htmlPath = (baseDirectory.value / "index.html").toURI.toString
  val log = streams.value.log
  log.info(s"Launching WaveDrom Studio at: $htmlPath")
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
