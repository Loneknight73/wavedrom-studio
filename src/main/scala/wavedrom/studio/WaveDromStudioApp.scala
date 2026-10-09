package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html

object Dom {
  def byId[T <: dom.Element](id: String): T =
    dom.document.getElementById(id).asInstanceOf[T]
}

// ============================================================================
// MAIN SCALA.JS ENTRY POINT & UI CONTROLLER
// ============================================================================

object WaveDromStudioApp {
  import Dom.byId

  private val minStepWidth = 15.0
  private val maxStepWidth = 250.0
  private val stateKeys = "10pnxz=."

  private val state = new AppState()

  private lazy val jsonEditor = byId[html.TextArea]("jsonEditor")
  private lazy val svgContainer = byId[html.Div]("svgContainer")
  private lazy val statusIndicator = byId[html.Element]("statusIndicator")
  private lazy val zoomRange = byId[html.Input]("zoomRange")
  private lazy val zoomValue = byId[html.Element]("zoomValue")
  private lazy val undoBtn = byId[html.Button]("undoBtn")
  private lazy val popover = new CyclePopover(applyEdit)

  def main(args: Array[String]): Unit = {
    if (dom.document.readyState != "loading") init()
    else dom.window.addEventListener("DOMContentLoaded", (_: dom.Event) => init())
  }

  private def init(): Unit = {
    SplitPane.init(byId[html.Element]("studioLayout"), byId[html.Element]("splitter"))

    jsonEditor.addEventListener("input", (_: dom.Event) => loadFromEditor())

    svgContainer.addEventListener("click", onSvgClick _)
    svgContainer.addEventListener("wheel", onSvgWheel _)
    dom.document.addEventListener("click", onDocumentClick _)
    dom.document.addEventListener("keydown", onKeyDown _)

    zoomRange.addEventListener(
      "input",
      (_: dom.Event) => zoomRange.value.toDoubleOption.foreach(setStepWidth)
    )
    byId[html.Button]("zoomOutBtn").addEventListener(
      "click",
      (_: dom.Event) => setStepWidth(state.stepWidth / 1.25)
    )
    byId[html.Button]("zoomInBtn").addEventListener(
      "click",
      (_: dom.Event) => setStepWidth(state.stepWidth * 1.25)
    )
    undoBtn.addEventListener("click", (_: dom.Event) => undo())
    byId[html.Button]("clearSelectionBtn").addEventListener(
      "click",
      (_: dom.Event) => {
        state.selectedNodeForEdge = None
        render()
      }
    )

    setStepWidth(state.stepWidth)
    loadFromEditor()
  }

  // --- Rendering -----------------------------------------------------------

  private def render(): Unit = {
    SvgRenderer.render(state, svgContainer)
    undoBtn.disabled = !state.canUndo
  }

  private def loadFromEditor(): Unit = {
    popover.hide()
    state.activeCycle = None
    state.updateFromText(jsonEditor.value) match {
      case Right(_) =>
        statusIndicator.textContent = "Syntax OK"
        statusIndicator.className = "status-badge status-ok"
        render()
      case Left(err) =>
        statusIndicator.textContent = err
        statusIndicator.className = "status-badge status-error"
        undoBtn.disabled = !state.canUndo
    }
  }

  // --- Graphical editing ---------------------------------------------------

  private def applyEdit(edit: SignalTrack => SignalTrack): Unit = {
    for {
      (sigIdx, _) <- state.activeCycle
      newJson <- state.applyEdit(sigIdx, edit)
    } jsonEditor.value = newJson
    loadFromEditor()
  }

  private def undo(): Unit = {
    state.undo().foreach(previousJson => jsonEditor.value = previousJson)
    loadFromEditor()
  }

  private def closePopover(): Unit = {
    popover.hide()
    if (state.activeCycle.nonEmpty) {
      state.activeCycle = None
      render()
    }
  }

  private def openPopover(
      sigIdx: Int,
      cycleIdx: Int,
      clientX: Double,
      clientY: Double
  ): Unit = {
    state.diagram.flatMap(_.signals.lift(sigIdx)).foreach { track =>
      state.activeCycle = Some((sigIdx, cycleIdx))
      render()
      popover.show(track, cycleIdx, clientX, clientY)
    }
  }

  private def toggleNodeSelection(nodeName: String): Unit = {
    state.selectedNodeForEdge =
      if (state.selectedNodeForEdge.contains(nodeName)) None else Some(nodeName)
    render()
  }

  // --- Zoom ----------------------------------------------------------------

  private def setStepWidth(width: Double): Unit = {
    state.stepWidth =
      Math.max(minStepWidth, Math.min(maxStepWidth, width)).round.toDouble
    zoomRange.value = state.stepWidth.toInt.toString
    zoomValue.textContent = s"${state.stepWidth.toInt}px"
    render()
  }

  // --- Event handlers ------------------------------------------------------

  private def onSvgClick(e: dom.MouseEvent): Unit = {
    val target = e.target.asInstanceOf[dom.Element]
    val nodeBadge = target.closest("[data-node]")
    val cycleHit = target.closest(".cycle-hit")
    if (nodeBadge != null) toggleNodeSelection(nodeBadge.getAttribute("data-node"))
    else if (cycleHit != null)
      openPopover(
        cycleHit.getAttribute("data-sig").toInt,
        cycleHit.getAttribute("data-cycle").toInt,
        e.clientX,
        e.clientY
      )
  }

  private def onSvgWheel(e: dom.WheelEvent): Unit = {
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault()
      setStepWidth(state.stepWidth * (if (e.deltaY < 0) 1.1 else 1 / 1.1))
    }
  }

  private def onDocumentClick(e: dom.MouseEvent): Unit = {
    if (popover.isOpen) {
      val target = e.target.asInstanceOf[dom.Element]
      // A click on a cycle has just (re)opened the popover; leave it alone
      if (!popover.contains(target) && target.closest(".cycle-hit") == null)
        closePopover()
    }
  }

  private def onKeyDown(e: dom.KeyboardEvent): Unit = {
    val inField = e.target match {
      case _: html.Input | _: html.TextArea | _: html.Select => true
      case _                                                 => false
    }
    val modifier = e.ctrlKey || e.metaKey || e.altKey
    val key = e.key.toLowerCase

    if (key == "escape" && popover.isOpen) closePopover()
    else if (!inField && !modifier && popover.isOpen && key.length == 1 && stateKeys.contains(key)) {
      e.preventDefault()
      popover.setState(key.charAt(0))
    } else if (!inField && (e.ctrlKey || e.metaKey) && key == "z") {
      e.preventDefault()
      undo()
    }
  }
}
