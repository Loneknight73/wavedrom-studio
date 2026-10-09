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

  private var cycle = 0

  root.addEventListener(
    "click",
    (e: dom.MouseEvent) => {
      val target = e.target.asInstanceOf[dom.Element]
      val stateBtn = target.closest("[data-state]")
      if (stateBtn != null) setState(stateBtn.getAttribute("data-state").charAt(0))
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

  def isOpen: Boolean = !root.hasAttribute("hidden")

  def contains(node: dom.Node): Boolean = root.contains(node)

  def setState(stateChar: Char): Unit =
    onEdit(WaveEditor.setCycle(_, cycle, stateChar))

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
