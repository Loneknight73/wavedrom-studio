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

  private val minStepWidth = 15.0
  private val maxStepWidth = 250.0
  private val stateKeys = "10pnxz=."
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
  private lazy val tabBar =
    new TabBar(byId[html.Element]("tabBar"), selectTab, closeTab, () => addTab(AppState.open(Samples.blank)))

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
    dom.window.addEventListener("beforeunload", onBeforeUnload _)

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
    showStepWidth()
    loadFromEditor()
  }

  private def selectTab(index: Int): Unit = {
    workspace.select(index)
    showActiveTab()
  }

  private def addTab(tab: AppState): Unit = {
    workspace.add(tab)
    showActiveTab()
  }

  private def closeTab(index: Int): Unit = {
    workspace.tabs.lift(index).foreach { tab =>
      val discard = !tab.isDirty ||
        dom.window.confirm(s"Close ${tab.title} without saving its changes?")
      if (discard) {
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
    showStepWidth()
    render()
  }

  private def showStepWidth(): Unit = {
    zoomRange.value = state.stepWidth.toInt.toString
    zoomValue.textContent = s"${state.stepWidth.toInt}px"
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
    val command = (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey
    val key = e.key.toLowerCase

    if (key == "escape" && popover.isOpen) closePopover()
    else if (command && key == "o") {
      e.preventDefault()
      openFiles()
    } else if (command && key == "s") {
      e.preventDefault()
      saveActive()
    } else if (!inField && !modifier && popover.isOpen && key.length == 1 && stateKeys.contains(key)) {
      e.preventDefault()
      popover.setState(key.charAt(0))
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
