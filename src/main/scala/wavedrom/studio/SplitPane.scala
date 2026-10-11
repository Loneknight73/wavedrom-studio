package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import scala.scalajs.js
import scala.util.Try

// The layout of the JSON editor and the waveform pane: side by side or
// stacked, with a draggable divider between them. The editor's size lives in
// the --editor-width (side by side) or --editor-height (stacked) CSS variable
// on the layout element, and each orientation remembers its own size.
object SplitPane {

  private val layoutKey = "wavedrom-studio.layout"
  private val widthKey = "wavedrom-studio.editorWidth"
  private val heightKey = "wavedrom-studio.editorHeight"
  private val minPaneWidth = 240.0
  private val minPaneHeight = 120.0

  private def stored(key: String): Option[String] =
    Try(dom.window.localStorage.getItem(key)).toOption.flatMap(Option(_))

  private def store(key: String, value: Option[String]): Unit =
    Try(value match {
      case Some(v) => dom.window.localStorage.setItem(key, v)
      case None    => dom.window.localStorage.removeItem(key)
    })

  def init(
      layout: html.Element,
      splitter: html.Element,
      layoutButton: html.Button
  ): Unit = {
    // Pointer capture is not exposed on scalajs-dom's html.Element
    val capture = splitter.asInstanceOf[js.Dynamic]
    var dragging = false

    def stacked: Boolean = layout.classList.contains("stacked")
    def sizeVar: String = if (stacked) "--editor-height" else "--editor-width"
    def sizeKey: String = if (stacked) heightKey else widthKey

    def setSize(px: Double): Unit = {
      val (total, divider, min) =
        if (stacked)
          (layout.clientHeight.toDouble, splitter.offsetHeight.toDouble, minPaneHeight)
        else
          (layout.clientWidth.toDouble, splitter.offsetWidth.toDouble, minPaneWidth)
      val clamped = Math.max(min, Math.min(total - min - divider, px))
      layout.style.setProperty(sizeVar, s"${clamped.round}px")
    }

    // Applies the remembered size of the current orientation
    def restoreSize(): Unit = {
      layout.style.removeProperty("--editor-width")
      layout.style.removeProperty("--editor-height")
      stored(sizeKey).flatMap(_.toDoubleOption).foreach(setSize)
    }

    def setStacked(value: Boolean): Unit = {
      layout.classList.toggle("stacked", value)
      layoutButton.textContent = if (value) "Side by side" else "Stack panes"
      layoutButton.title =
        if (value) "Put the JSON editor and the waveform side by side"
        else "Put the JSON editor above the waveform"
      restoreSize()
    }

    setStacked(stored(layoutKey).contains("stacked"))

    layoutButton.addEventListener(
      "click",
      (_: dom.Event) => {
        setStacked(!stacked)
        store(layoutKey, Option.when(stacked)("stacked"))
      }
    )

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
        if (dragging) {
          val box = layout.getBoundingClientRect()
          setSize(if (stacked) e.clientY - box.top else e.clientX - box.left)
        }
      }
    )

    def endDrag(e: dom.PointerEvent): Unit = {
      if (dragging) {
        dragging = false
        capture.releasePointerCapture(e.pointerId)
        dom.document.body.classList.remove("resizing")
        val size = layout.style.getPropertyValue(sizeVar)
        store(sizeKey, Some(size.stripSuffix("px").trim))
      }
    }
    splitter.addEventListener("pointerup", endDrag _)
    splitter.addEventListener("pointercancel", endDrag _)

    splitter.addEventListener(
      "dblclick",
      (_: dom.MouseEvent) => {
        layout.style.removeProperty(sizeVar)
        store(sizeKey, None)
      }
    )
  }
}
