package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import scala.scalajs.js.annotation.*

@JSExportTopLevel("WaveDromStudioApp")
object WaveDromStudioApp {

  private val state = new AppState()

  def main(args: Array[String]): Unit = main()

  @JSExport
  def main(): Unit = {
    def init(): Unit = {
      val jsonEditor =
        dom.document.getElementById("jsonEditor").asInstanceOf[html.TextArea]
      val svgContainer =
        dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
      val statusIndicator = dom.document
        .getElementById("statusIndicator")
        .asInstanceOf[html.Element]

      def updateView(): Unit = {
        if (jsonEditor != null && svgContainer != null) {
          state.updateFromText(jsonEditor.value) match {
            case Right(_) =>
              if (statusIndicator != null) {
                statusIndicator.textContent = "Syntax OK"
                statusIndicator.className = "status-badge status-ok"
              }
              SvgRenderer.render(state, svgContainer)
            case Left(err) =>
              if (statusIndicator != null) {
                statusIndicator.textContent = err
                statusIndicator.className = "status-badge status-error"
              }
          }
        }
      }

      if (jsonEditor != null && svgContainer != null) {
        updateView()
        jsonEditor.addEventListener("input", (_: dom.Event) => updateView())

        dom.document.addEventListener(
          "click",
          (e: dom.MouseEvent) => {
            val menu = dom.document
              .getElementById("cycleMenu")
              .asInstanceOf[html.Element]
            if (menu != null && menu.style.display != "none") {
              val target = e.target.asInstanceOf[dom.Node]
              if (!menu.contains(target)) {
                menu.style.display = "none"
              }
            }
          }
        )
      }
    }

    if (dom.document.readyState != "loading") {
      init()
    } else {
      dom.window.addEventListener("DOMContentLoaded", (_: dom.Event) => init())
    }
  }

  @JSExport
  def handleNodeClick(nodeName: String): Unit = {
    if (state.selectedNodeForEdge.contains(nodeName)) {
      state.selectedNodeForEdge = None
    } else {
      state.selectedNodeForEdge = Some(nodeName)
    }
    val svgContainer =
      dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
    if (svgContainer != null) SvgRenderer.render(state, svgContainer)
  }

  @JSExport
  def openContextMenu(
      event: dom.MouseEvent,
      sigIdx: Int,
      cycleIdx: Int
  ): Unit = {
    event.preventDefault()
    event.stopPropagation()
    state.activeMenuSigIdx = sigIdx
    state.activeMenuCycleIdx = cycleIdx
    val menu =
      dom.document.getElementById("cycleMenu").asInstanceOf[html.Element]
    if (menu != null) {
      menu.style.display = "block"
      menu.style.left = s"${event.pageX}px"
      menu.style.top = s"${event.pageY}px"
    }
  }

  @JSExport
  def setCycleState(stateChar: String): Unit = {
    if (
      state.activeMenuSigIdx >= 0 && state.activeMenuCycleIdx >= 0 && stateChar.nonEmpty
    ) {
      val st = WaveState.fromChar(stateChar.charAt(0))
      state
        .updateCycleState(state.activeMenuSigIdx, state.activeMenuCycleIdx, st)
        .foreach { newJson =>
          val jsonEditor = dom.document
            .getElementById("jsonEditor")
            .asInstanceOf[html.TextArea]
          val svgContainer =
            dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
          if (jsonEditor != null) jsonEditor.value = newJson
          if (svgContainer != null) SvgRenderer.render(state, svgContainer)
        }
    }
    val menu =
      dom.document.getElementById("cycleMenu").asInstanceOf[html.Element]
    if (menu != null) menu.style.display = "none"
  }

  @JSExport
  def setStepWidth(width: Double): Unit = {
    state.stepWidth = Math.max(15.0, Math.min(250.0, width))
    val svgContainer =
      dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
    if (svgContainer != null) SvgRenderer.render(state, svgContainer)
  }

  @JSExport
  def resetSelection(): Unit = {
    state.selectedNodeForEdge = None
    val svgContainer =
      dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
    if (svgContainer != null) SvgRenderer.render(state, svgContainer)
  }
}
