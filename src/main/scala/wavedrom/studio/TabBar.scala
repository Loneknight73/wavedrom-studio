package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html

final class TabBar(
    container: html.Element,
    onSelect: Int => Unit,
    onClose: Int => Unit,
    onAdd: () => Unit
) {

  container.addEventListener(
    "click",
    (e: dom.MouseEvent) => {
      val target = e.target.asInstanceOf[dom.Element]
      val close = target.closest("[data-close]")
      val tab = target.closest("[data-tab]")
      if (close != null) onClose(close.getAttribute("data-close").toInt)
      else if (tab != null) onSelect(tab.getAttribute("data-tab").toInt)
      else if (target.closest(".tab-add") != null) onAdd()
    }
  )

  def render(workspace: Workspace): Unit =
    container.innerHTML = TabBar.renderToString(workspace)
}

object TabBar {

  def renderToString(workspace: Workspace): String = {
    val sb = new StringBuilder()
    workspace.tabs.zipWithIndex.foreach { case (tab, i) =>
      val classes =
        "tab" + (if (i == workspace.activeIndex) " active" else "") +
          (if (tab.isDirty) " dirty" else "")
      val title = escapeHtml(tab.title)
      val hint = if (tab.isDirty) s"$title (unsaved changes)" else title
      sb.append(
        s"""<div class="$classes" data-tab="$i" title="$hint">""" +
          s"""<span class="tab-name">$title</span>""" +
          """<span class="tab-dirty">&#9679;</span>""" +
          s"""<button class="tab-close" data-close="$i" title="Close">&times;</button>""" +
          "</div>"
      )
    }
    sb.append("""<button class="tab-add" title="New diagram">+</button>""")
    sb.toString()
  }

  private def escapeHtml(str: String): String =
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
}
