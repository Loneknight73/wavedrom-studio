package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}

object Dom {
  def byId[T <: dom.Element](id: String): T =
    dom.document.getElementById(id).asInstanceOf[T]
}

// ============================================================================
// MAIN SCALA.JS ENTRY POINT & UI CONTROLLER
// ============================================================================

object WaveDromStudioApp {
  import Dom.byId

  private val minZoom = 0.25
  private val maxZoom = 4.0
  private val stateKeys = "10pnxz=.|ud"
  private val busColorKeys = "23456789"
  private val defaultFileName = "waveform.json"

  private val workspace = new Workspace(
    AppState.open(Samples.full.json, Some(Samples.full.fileName)),
    () => AppState.open(Samples.blank)
  )

  // The document in the active tab
  private def state: AppState = workspace.active

  private lazy val jsonEditor = byId[html.TextArea]("jsonEditor")
  private lazy val svgContainer = byId[html.Div]("svgContainer")
  private lazy val statusIndicator = byId[html.Element]("statusIndicator")
  private lazy val zoomRange = byId[html.Input]("zoomRange")
  private lazy val zoomValue = byId[html.Element]("zoomValue")
  private lazy val undoBtn = byId[html.Button]("undoBtn")
  private lazy val sampleSelect = byId[html.Select]("sampleSelect")
  private lazy val fileInput = byId[html.Input]("fileInput")
  private lazy val popover = new CyclePopover(applyEdit)
  private lazy val arrowPopover = new ArrowPopover(startDrawing, updateEdge, deleteEdge)

  // The node an arrow popover was opened on, with its place in the drawing
  private case class SourceNode(name: String, x: Double, y: Double)
  // An arrow being drawn: its style and label are chosen, its end is the mouse
  private case class Drawing(source: SourceNode, shape: EdgeShape.Shape, label: Option[String])

  private var pendingSource: Option[SourceNode] = None
  private var drawing: Option[Drawing] = None
  private lazy val tabBar =
    new TabBar(byId[html.Element]("tabBar"), selectTab, closeTab, () => addTab(AppState.open(Samples.blank)))

  def main(args: Array[String]): Unit = {
    if (dom.document.readyState != "loading") init()
    else dom.window.addEventListener("DOMContentLoaded", (_: dom.Event) => init())
  }

  private def init(): Unit = {
    SplitPane.init(
      byId[html.Element]("studioLayout"),
      byId[html.Element]("splitter"),
      byId[html.Button]("layoutBtn")
    )

    jsonEditor.addEventListener("input", (_: dom.Event) => loadFromEditor())

    svgContainer.addEventListener("click", onSvgClick _)
    svgContainer.addEventListener("mousemove", onSvgMouseMove _)
    svgContainer.addEventListener("wheel", onSvgWheel _)
    dom.document.addEventListener("click", onDocumentClick _)
    dom.document.addEventListener("keydown", onKeyDown _)
    dom.window.addEventListener("beforeunload", onBeforeUnload _)

    zoomRange.addEventListener(
      "input",
      (_: dom.Event) => zoomRange.value.toDoubleOption.foreach(percent => setZoom(percent / 100))
    )
    byId[html.Button]("zoomOutBtn").addEventListener(
      "click",
      (_: dom.Event) => setZoom(state.zoom / 1.25)
    )
    byId[html.Button]("zoomInBtn").addEventListener(
      "click",
      (_: dom.Event) => setZoom(state.zoom * 1.25)
    )
    undoBtn.addEventListener("click", (_: dom.Event) => undo())
    byId[html.Button]("formatBtn").addEventListener("click", (_: dom.Event) => formatJson())
    byId[html.Button]("clearSelectionBtn").addEventListener(
      "click",
      (_: dom.Event) => {
        endEdgeUi()
        render()
      }
    )

    byId[html.Button]("openBtn").addEventListener("click", (_: dom.Event) => openFiles())
    byId[html.Button]("saveBtn").addEventListener("click", (_: dom.Event) => saveActive())
    sampleSelect.addEventListener("change", (_: dom.Event) => openSample())

    showActiveTab()
  }

  // --- Rendering -----------------------------------------------------------

  private def render(): Unit = {
    SvgRenderer.render(state, svgContainer)
    undoBtn.disabled = !state.canUndo
  }

  private def loadFromEditor(): Unit = {
    popover.hide()
    endEdgeUi()
    state.activeCycle = None
    state.updateFromText(jsonEditor.value) match {
      case Right(_) =>
        statusIndicator.textContent = "Syntax OK"
        statusIndicator.className = "status-badge status-ok"
        render()
      case Left(err) =>
        statusIndicator.textContent = err
        statusIndicator.className = "status-badge status-error"
        // The canvas keeps showing the last diagram that parsed
        render()
    }
    tabBar.render(workspace)
  }

  // --- Tabs ----------------------------------------------------------------

  // Puts the active tab's document into the editor, toolbar and canvas
  private def showActiveTab(): Unit = {
    jsonEditor.value = state.jsonText
    showZoom()
    loadFromEditor()
  }

  private def selectTab(index: Int): Unit = {
    endEdgeUi()
    workspace.select(index)
    showActiveTab()
  }

  private def addTab(tab: AppState): Unit = {
    endEdgeUi()
    workspace.add(tab)
    showActiveTab()
  }

  private def closeTab(index: Int): Unit = {
    workspace.tabs.lift(index).foreach { tab =>
      val discard = !tab.isDirty ||
        dom.window.confirm(s"Close ${tab.title} without saving its changes?")
      if (discard) {
        endEdgeUi()
        workspace.close(index)
        showActiveTab()
      }
    }
  }

  private def openSample(): Unit = {
    Samples.byId(sampleSelect.value).foreach { sample =>
      addTab(AppState.open(sample.json, Some(sample.fileName)))
    }
    sampleSelect.value = ""
  }

  // --- Files ---------------------------------------------------------------

  private def toast(message: String): Unit =
    dom.window.asInstanceOf[js.Dynamic].showToast(message)

  private def describe(error: Throwable): String = error match {
    case js.JavaScriptException(e) => s"${e.asInstanceOf[js.Dynamic].message}"
    case other                     => other.getMessage
  }

  private def openFiles(): Unit = {
    FileIO
      .open(fileInput)
      .flatMap { files =>
        // One at a time, so a file picked twice is also only opened once
        files
          .foldLeft(Future.unit) { (done, file) =>
            done.flatMap(_ => tabShowing(file)).map {
              case Some(index) => workspace.select(index)
              case None =>
                workspace.add(AppState.open(file.text, Some(file.name), file.handle))
            }
          }
          .map(_ => files)
      }
      .onComplete {
        case Success(files) if files.nonEmpty =>
          showActiveTab()
          toast(
            if (files.length == 1) s"Opened ${files.head.name}"
            else s"Opened ${files.length} files"
          )
        case Success(_)     =>
        case Failure(error) => toast(s"Could not open the file: ${describe(error)}")
      }
  }

  // The tab that already has this file open, where the browser can tell
  private def tabShowing(file: OpenedFile): Future[Option[Int]] = {
    val matches = workspace.tabs.map { tab =>
      (tab.fileHandle, file.handle) match {
        case (Some(open), Some(picked)) => FileIO.sameFile(open, picked)
        case _                          => Future.successful(false)
      }
    }
    Future.sequence(matches).map(_.indexOf(true)).map(i => Option.when(i >= 0)(i))
  }

  private def saveActive(): Unit = {
    val tab = state
    val text = tab.jsonText
    FileIO
      .save(tab.fileName.getOrElse(defaultFileName), tab.fileHandle, text)
      .onComplete {
        case Success(Some(saved)) =>
          tab.fileName = Some(saved.name)
          tab.fileHandle = saved.handle
          tab.markSaved(text)
          tabBar.render(workspace)
          toast(s"Saved ${saved.name}")
        case Success(None)  =>
        case Failure(error) => toast(s"Could not save: ${describe(error)}")
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

  // Re-indents the JSON in the compact layout (one signal per line),
  // the same one graphical edits write. Undoable, unlike typing, because it replaces the
  // textarea's value and so discards the browser's own undo history.
  private def formatJson(): Unit = {
    try {
      val formatted = WaveJsonCodec.format(js.JSON.parse(jsonEditor.value))
      state.replaceText(formatted) match {
        case Some(text) =>
          jsonEditor.value = text
          loadFromEditor()
          toast("JSON formatted")
        case None => toast("JSON is already formatted")
      }
    } catch {
      case js.JavaScriptException(e) =>
        toast(s"JSON parse error: ${e.asInstanceOf[js.Dynamic].message}")
    }
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

  private def applyEdgeEdit(edit: Vector[String] => Vector[String]): Unit = {
    state.applyEdgeEdit(edit).foreach(jsonEditor.value = _)
    loadFromEditor()
  }

  private def updateEdge(index: Int, shape: EdgeShape.Shape, label: Option[String]): Unit =
    applyEdgeEdit(EdgeEditor.update(_, index, shape, label))

  private def deleteEdge(index: Int): Unit =
    applyEdgeEdit(EdgeEditor.delete(_, index))

  // --- Drawing arrows ------------------------------------------------------

  private def nodeNameProblem(name: String): String =
    s"The node '$name' cannot be used in an arrow: " +
      "arrow nodes may only contain letters, digits and underscores"

  // Forgets any arrow popover, drawing and node highlight; the caller renders
  private def endEdgeUi(): Unit = {
    arrowPopover.hide()
    pendingSource = None
    drawing = None
    state.selectedNodeForEdge = None
    svgContainer.classList.remove("drawing-arrow")
  }

  private def cancelDrawing(): Unit = {
    endEdgeUi()
    render()
  }

  private def closeArrowPopover(): Unit = {
    arrowPopover.hide()
    pendingSource = None
    if (state.selectedNodeForEdge.nonEmpty) {
      state.selectedNodeForEdge = None
      render()
    }
  }

  private def openArrowPopoverOnNode(badge: dom.Element, clientX: Double, clientY: Double): Unit = {
    val name = badge.getAttribute("data-node")
    if (!EdgeEditor.isNodeName(name)) toast(nodeNameProblem(name))
    else {
      popover.hide()
      state.activeCycle = None
      pendingSource = Some(
        SourceNode(name, badge.getAttribute("data-x").toDouble, badge.getAttribute("data-y").toDouble)
      )
      state.selectedNodeForEdge = Some(name)
      render()
      arrowPopover.showNew(name, clientX, clientY)
    }
  }

  private def openArrowPopoverOnEdge(index: Int, clientX: Double, clientY: Double): Unit =
    for {
      entry <- state.diagram.flatMap(_.edges.lift(index))
      edge <- EdgeShape.parse(entry)
    } {
      popover.hide()
      state.activeCycle = None
      state.selectedNodeForEdge = None
      pendingSource = None
      render()
      arrowPopover.showEdit(index, edge, clientX, clientY)
    }

  // The style is chosen: the arrow now follows the mouse until a node is clicked
  private def startDrawing(shape: EdgeShape.Shape, label: Option[String]): Unit =
    pendingSource.foreach { source =>
      arrowPopover.hide()
      pendingSource = None
      drawing = Some(Drawing(source, shape, label))
      svgContainer.classList.add("drawing-arrow")
    }

  private def finishDrawing(target: String): Unit =
    drawing.foreach { d =>
      if (target == d.source.name) cancelDrawing()
      else if (!EdgeEditor.isNodeName(target)) toast(nodeNameProblem(target))
      else applyEdgeEdit(EdgeEditor.add(_, d.source.name, d.shape, target, d.label))
    }

  // Moves the preview only; the drawing is not rendered again for every move
  private def onSvgMouseMove(e: dom.MouseEvent): Unit =
    for {
      d <- drawing
      svg <- Option(svgContainer.querySelector("svg"))
      preview <- Option(svgContainer.querySelector("#edgePreview"))
    } {
      val badge = e.target.asInstanceOf[dom.Element].closest("[data-node]")
      val end =
        if (badge != null && badge.getAttribute("data-node") != d.source.name)
          (badge.getAttribute("data-x").toDouble, badge.getAttribute("data-y").toDouble)
        else svgPoint(svg.asInstanceOf[dom.svg.SVG], e)
      val g = EdgeShape.geometry(d.shape, (d.source.x, d.source.y), end)
      preview.setAttribute("d", g.path)
      def setMarker(attr: String, id: Option[String]): Unit = id match {
        case Some(name) => preview.setAttribute(attr, s"url(#$name)")
        case None       => preview.removeAttribute(attr)
      }
      setMarker("marker-start", SvgRenderer.markerStartId(g))
      setMarker("marker-end", SvgRenderer.markerEndId(g))
    }

  // The mouse in the drawing's own units, whatever the zoom
  private def svgPoint(svg: dom.svg.SVG, e: dom.MouseEvent): (Double, Double) = {
    val rect = svg.getBoundingClientRect()
    val box = svg.viewBox.baseVal
    // Less the half pixel the drawing is shifted by
    (
      (e.clientX - rect.left) * box.width / rect.width - 0.5,
      (e.clientY - rect.top) * box.height / rect.height - 0.5
    )
  }

  // --- Zoom ----------------------------------------------------------------

  private def setZoom(zoom: Double): Unit = {
    // Kept to whole percents, so the slider and the label show the real value
    state.zoom = Math.max(minZoom, Math.min(maxZoom, (zoom * 100).round / 100.0))
    showZoom()
    render()
  }

  private def zoomPercent: Int = (state.zoom * 100).round.toInt

  private def showZoom(): Unit = {
    zoomRange.value = zoomPercent.toString
    zoomValue.textContent = s"$zoomPercent%"
  }

  // --- Event handlers ------------------------------------------------------

  private def onSvgClick(e: dom.MouseEvent): Unit = {
    val target = e.target.asInstanceOf[dom.Element]
    val nodeBadge = target.closest("[data-node]")
    val cycleHit = target.closest(".cycle-hit")
    val edgeHit = target.closest(".edge-hit")
    if (drawing.nonEmpty) {
      // Only a node ends the arrow; anything else gives it up
      if (nodeBadge != null) finishDrawing(nodeBadge.getAttribute("data-node"))
      else cancelDrawing()
    } else if (nodeBadge != null) openArrowPopoverOnNode(nodeBadge, e.clientX, e.clientY)
    else if (edgeHit != null)
      openArrowPopoverOnEdge(edgeHit.getAttribute("data-edge").toInt, e.clientX, e.clientY)
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
      setZoom(state.zoom * (if (e.deltaY < 0) 1.1 else 1 / 1.1))
    }
  }

  private def onDocumentClick(e: dom.MouseEvent): Unit = {
    if (arrowPopover.isOpen) {
      val target = e.target.asInstanceOf[dom.Element]
      // A click on a node or an arrow has just (re)opened the popover
      if (!arrowPopover.contains(target) && target.closest("[data-node], .edge-hit") == null)
        closeArrowPopover()
    }
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
    val command = (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey
    val key = e.key.toLowerCase

    if (key == "escape" && drawing.nonEmpty) cancelDrawing()
    else if (key == "escape" && arrowPopover.isOpen) closeArrowPopover()
    else if (key == "escape" && popover.isOpen) closePopover()
    else if (command && key == "o") {
      e.preventDefault()
      openFiles()
    } else if (command && key == "s") {
      e.preventDefault()
      saveActive()
    } else if (!inField && !modifier && popover.isOpen && key.length == 1 && stateKeys.contains(key)) {
      e.preventDefault()
      // Shift+P and Shift+N give the clocks that carry an arrow
      val arrowClock = e.shiftKey && (key == "p" || key == "n")
      popover.setState(if (arrowClock) key.charAt(0).toUpper else key.charAt(0))
    } else if (!inField && !modifier && popover.isOpen && e.shiftKey && (key == "h" || key == "l")) {
      // Shift+H and Shift+L give the levels that carry an arrow
      e.preventDefault()
      popover.setState(key.charAt(0).toUpper)
    } else if (!inField && !modifier && popover.isOpen && key.length == 1 && busColorKeys.contains(key)) {
      e.preventDefault()
      popover.setBusColor(key.charAt(0))
    } else if (!inField && command && key == "z") {
      e.preventDefault()
      undo()
    }
  }

  // Makes the browser ask before the page is left with unsaved documents
  private def onBeforeUnload(e: dom.BeforeUnloadEvent): Unit = {
    if (workspace.anyDirty) {
      e.preventDefault()
      e.returnValue = ""
    }
  }
}
