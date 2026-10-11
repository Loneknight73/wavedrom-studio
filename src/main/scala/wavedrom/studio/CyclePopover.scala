package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html

// The floating editor shown when a cycle is clicked (#cyclePopover in
// index.html). Every action is reported through `onEdit` as an edit to apply
// to the signal the popover was opened for.
final class CyclePopover(onEdit: (SignalTrack => SignalTrack) => Unit) {
  import Dom.byId

  private val root = byId[html.Element]("cyclePopover")
  private val targetLabel = byId[html.Element]("popTarget")
  private val nodeInput = byId[html.Input]("popNodeInput")
  private val busSection = byId[html.Element]("popBusSection")
  private val busInput = byId[html.Input]("popBusInput")
  private val busColors = byId[html.Element]("popBusColors")
  private val signalPresets = byId[html.Element]("popSignalPresets")
  private val signalColorInput = byId[html.Input]("popSignalColorInput")

  // The swatches are built from the palettes, so those stay the single
  // source of the colours
  busColors.innerHTML =
    (('=' -> BusPalette.default.fill) +:
      BusPalette.fills.map { case (digit, fill) => ('0' + digit).toChar -> fill })
      .map { case (token, hue) =>
        s"""<button class="pop-swatch" data-bus-color="$token" title="Bus colour $token" style="background:$hue">$token</button>"""
      }
      .mkString
  signalPresets.innerHTML = SignalColor.presets
    .map(color =>
      s"""<button class="pop-swatch" data-signal-color="$color" title="$color" style="background:$color"></button>"""
    )
    .mkString

  private var cycle = 0

  // What the native colour picker shows for a signal with no colour set
  private val defaultPickerColor = "#000000"

  root.addEventListener(
    "click",
    (e: dom.MouseEvent) => {
      val target = e.target.asInstanceOf[dom.Element]
      val stateBtn = target.closest("[data-state]")
      val busColorBtn = target.closest("[data-bus-color]")
      val signalColorBtn = target.closest("[data-signal-color]")
      if (stateBtn != null) setState(stateBtn.getAttribute("data-state").charAt(0))
      else if (busColorBtn != null)
        setBusColor(busColorBtn.getAttribute("data-bus-color").charAt(0))
      else if (signalColorBtn != null)
        setSignalColor(Some(signalColorBtn.getAttribute("data-signal-color")))
      else if (target.closest("#popSignalColorClear") != null) setSignalColor(None)
      else if (target.closest("#popDoubleEdge") != null)
        onEdit(WaveEditor.doubleEdgeClockFrom(_, cycle))
      else if (target.closest("#popInsert") != null)
        onEdit(WaveEditor.insertHoldAfter(_, cycle))
      else if (target.closest("#popDelete") != null)
        onEdit(WaveEditor.deleteCycle(_, cycle))
    }
  )

  byId[html.Form]("popNodeForm").addEventListener(
    "submit",
    (e: dom.Event) => {
      e.preventDefault()
      onEdit(WaveEditor.setNode(_, cycle, nodeInput.value))
    }
  )

  byId[html.Form]("popBusForm").addEventListener(
    "submit",
    (e: dom.Event) => {
      e.preventDefault()
      onEdit(WaveEditor.setBusLabel(_, cycle, busInput.value))
    }
  )

  // "change" fires once, when a colour is settled on in the native picker,
  // so picking one is a single undo step
  signalColorInput.addEventListener(
    "change",
    (_: dom.Event) => setSignalColor(Some(signalColorInput.value))
  )

  def isOpen: Boolean = !root.hasAttribute("hidden")

  def contains(node: dom.Node): Boolean = root.contains(node)

  def setState(stateChar: Char): Unit =
    onEdit(WaveEditor.setCycle(_, cycle, stateChar))

  // `token` is `=` or `2`..`9`
  def setBusColor(token: Char): Unit =
    onEdit(WaveEditor.setBusColor(_, cycle, token))

  def setSignalColor(color: Option[String]): Unit =
    onEdit(WaveEditor.setSignalColor(_, color))

  private def markSelected(swatches: html.Element, attr: String, value: Option[String]): Unit = {
    val buttons = swatches.querySelectorAll(s"[$attr]")
    for (i <- 0 until buttons.length) {
      val button = buttons(i).asInstanceOf[dom.Element]
      if (value.contains(button.getAttribute(attr))) button.classList.add("selected")
      else button.classList.remove("selected")
    }
  }

  def show(
      track: SignalTrack,
      cycleIdx: Int,
      clientX: Double,
      clientY: Double
  ): Unit = {
    cycle = cycleIdx
    targetLabel.textContent = s"${track.name} @ cycle $cycleIdx"
    nodeInput.value = WaveEditor.nodeAt(track, cycleIdx)
    WaveEditor.busLabel(track, cycleIdx) match {
      case Some(label) =>
        busInput.value = label
        busSection.removeAttribute("hidden")
      case None =>
        busSection.setAttribute("hidden", "")
    }
    markSelected(
      busColors,
      "data-bus-color",
      WaveEditor.busToken(track, cycleIdx).map(_.toString)
    )
    val signalColor = track.color.flatMap(SignalColor.sanitize)
    markSelected(signalPresets, "data-signal-color", signalColor)
    signalColorInput.value =
      signalColor.flatMap(SignalColor.toPickerValue).getOrElse(defaultPickerColor)

    root.removeAttribute("hidden")
    // Keep the popover inside the viewport
    val margin = 8.0
    val maxLeft = dom.window.innerWidth - root.offsetWidth - margin
    val maxTop = dom.window.innerHeight - root.offsetHeight - margin
    root.style.left = s"${Math.max(margin, Math.min(maxLeft, clientX + 12))}px"
    root.style.top = s"${Math.max(margin, Math.min(maxTop, clientY + 12))}px"
  }

  def hide(): Unit = root.setAttribute("hidden", "")
}
