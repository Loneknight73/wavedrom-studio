package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import scala.scalajs.js
import scala.util.Try

// Draggable divider between the JSON editor and the waveform pane. The editor
// width lives in the --editor-width CSS variable on the layout element.
object SplitPane {

  private val storageKey = "wavedrom-plus.editorWidth"
  private val minPaneWidth = 240.0

  def init(layout: html.Element, splitter: html.Element): Unit = {
    // Pointer capture is not exposed on scalajs-dom's html.Element
    val capture = splitter.asInstanceOf[js.Dynamic]
    var dragging = false

    def setWidth(px: Double): Unit = {
      val maxWidth = layout.clientWidth - minPaneWidth - splitter.offsetWidth
      val clamped = Math.max(minPaneWidth, Math.min(maxWidth, px))
      layout.style.setProperty("--editor-width", s"${clamped.round}px")
    }

    Try(dom.window.localStorage.getItem(storageKey)).toOption
      .flatMap(Option(_))
      .flatMap(_.toDoubleOption)
      .foreach(setWidth)

    splitter.addEventListener(
      "pointerdown",
      (e: dom.PointerEvent) => {
        e.preventDefault()
        dragging = true
        capture.setPointerCapture(e.pointerId)
        dom.document.body.classList.add("resizing")
      }
    )

    splitter.addEventListener(
      "pointermove",
      (e: dom.PointerEvent) => {
        if (dragging) setWidth(e.clientX - layout.getBoundingClientRect().left)
      }
    )

    def endDrag(e: dom.PointerEvent): Unit = {
      if (dragging) {
        dragging = false
        capture.releasePointerCapture(e.pointerId)
        dom.document.body.classList.remove("resizing")
        val width = layout.style.getPropertyValue("--editor-width")
        Try(
          dom.window.localStorage
            .setItem(storageKey, width.stripSuffix("px").trim)
        )
      }
    }
    splitter.addEventListener("pointerup", endDrag _)
    splitter.addEventListener("pointercancel", endDrag _)

    splitter.addEventListener(
      "dblclick",
      (_: dom.MouseEvent) => {
        layout.style.removeProperty("--editor-width")
        Try(dom.window.localStorage.removeItem(storageKey))
      }
    )
  }
}
