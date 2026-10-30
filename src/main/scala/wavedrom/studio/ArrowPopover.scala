package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import SvgRenderer.{Palette, escapeHtml}

// The floating editor for arrows (#arrowPopover in index.html). Opened on a
// node it picks the style of a new arrow; opened on an arrow it restyles,
// relabels or deletes it. What to do with the choice is reported through the
// callbacks.
final class ArrowPopover(
    onStart: (EdgeShape.Shape, Option[String]) => Unit,
    onUpdate: (Int, EdgeShape.Shape, Option[String]) => Unit,
    onDelete: Int => Unit
) {
  import Dom.byId

  private val root = byId[html.Element]("arrowPopover")
  private val title = byId[html.Element]("arrowTitle")
  private val targetLabel = byId[html.Element]("arrowTarget")
  private val styles = byId[html.Element]("arrowStyles")
  private val labelInput = byId[html.Input]("arrowLabelInput")
  private val applyButton = byId[html.Button]("arrowApply")
  private val hint = byId[html.Element]("arrowHint")
  private val deleteSection = byId[html.Element]("arrowDeleteSection")

  // The arrow being edited, as an index into the document's edge list
  private var editing: Option[Int] = None
  private var selected: Option[EdgeShape.Shape] = None

  // A miniature of what each shape draws, from the same geometry the diagram
  // uses. Its colours are written in, like those of the diagram.
  private def preview(shape: EdgeShape.Shape, index: Int): String = {
    val g = EdgeShape.geometry(shape, (5.0, 6.0), (39.0, 22.0))
    val color = Palette.edge
    def marker(id: String, refX: Int, d: String, fill: String) =
      s"""<marker id="$id$index" viewBox="0 0 10 10" refX="$refX" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="$d" fill="$fill" stroke="${if (fill == "none") color else "none"}" /></marker>"""
    val arrowEnd = "M 0 1.5 L 10 5 L 0 8.5 z"
    val arrowStart = "M 10 1.5 L 0 5 L 10 8.5 z"
    val bar = "M 5 0 L 5 10"
    val (start, end) =
      if (g.tee) (marker("s", 5, bar, "none"), marker("e", 5, bar, "none"))
      else (marker("s", 1, arrowStart, color), marker("e", 9, arrowEnd, color))
    val startAttr = if (g.tee || g.startArrow) s""" marker-start="url(#s$index)"""" else ""
    val endAttr = if (g.tee || g.endArrow) s""" marker-end="url(#e$index)"""" else ""
    s"""<svg width="44" height="28" viewBox="0 0 44 28" xmlns="http://www.w3.org/2000/svg"><defs>$start$end</defs><path d="${g.path}" fill="none" stroke="$color" stroke-width="1.2"$startAttr$endAttr /></svg>"""
  }

  styles.innerHTML = {
    var index = 0
    EdgeEditor.styleGroups.map { case (name, shapes) =>
      val buttons = shapes.map { shape =>
        index += 1
        val token = escapeHtml(shape.token)
        s"""<button type="button" class="arrow-style" data-shape="$token" title="$token">${preview(shape, index)}<code>$token</code></button>"""
      }
      s"""<div class="arrow-family">$name</div><div class="arrow-grid">${buttons.mkString}</div>"""
    }.mkString
  }

  private def label: Option[String] =
    Option(labelInput.value.trim).filter(_.nonEmpty)

  root.addEventListener(
    "click",
    (e: dom.MouseEvent) => {
      val target = e.target.asInstanceOf[dom.Element]
      val styleBtn = target.closest("[data-shape]")
      if (styleBtn != null) {
        EdgeShape.shapes.find(_.token == styleBtn.getAttribute("data-shape")).foreach { shape =>
          selected = Some(shape)
          editing match {
            case Some(index) => onUpdate(index, shape, label)
            case None        => onStart(shape, label)
          }
        }
      } else if (target.closest("#arrowCancel") != null) hide()
      else if (target.closest("#arrowDelete") != null) editing.foreach(onDelete)
    }
  )

  byId[html.Form]("arrowLabelForm").addEventListener(
    "submit",
    (e: dom.Event) => {
      e.preventDefault()
      // Only an existing arrow has a style to keep; a new one waits for a click on one
      for { index <- editing; shape <- selected } onUpdate(index, shape, label)
    }
  )

  def isOpen: Boolean = !root.hasAttribute("hidden")

  def contains(node: dom.Node): Boolean = root.contains(node)

  private def markSelected(): Unit = {
    val buttons = styles.querySelectorAll("[data-shape]")
    for (i <- 0 until buttons.length) {
      val button = buttons(i).asInstanceOf[dom.Element]
      if (selected.exists(_.token == button.getAttribute("data-shape")))
        button.classList.add("selected")
      else button.classList.remove("selected")
    }
  }

  // A node was clicked: choose the style of an arrow that starts there
  def showNew(node: String, clientX: Double, clientY: Double): Unit = {
    editing = None
    selected = None
    title.textContent = "Draw Arrow"
    targetLabel.textContent = s"from $node"
    hint.removeAttribute("hidden")
    labelInput.value = ""
    applyButton.setAttribute("hidden", "")
    deleteSection.setAttribute("hidden", "")
    open(clientX, clientY)
  }

  // An arrow was clicked: its style and label are shown, to change or delete
  def showEdit(index: Int, edge: EdgeShape.Parsed, clientX: Double, clientY: Double): Unit = {
    editing = Some(index)
    selected = Some(edge.shape)
    title.textContent = "Edit Arrow"
    targetLabel.textContent = s"${edge.from} to ${edge.to}"
    hint.setAttribute("hidden", "")
    labelInput.value = edge.label.getOrElse("")
    applyButton.removeAttribute("hidden")
    deleteSection.removeAttribute("hidden")
    open(clientX, clientY)
  }

  private def open(clientX: Double, clientY: Double): Unit = {
    markSelected()
    root.removeAttribute("hidden")
    Popovers.place(root, clientX, clientY)
  }

  def hide(): Unit = root.setAttribute("hidden", "")
}
