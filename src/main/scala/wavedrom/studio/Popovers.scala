package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html

object Popovers {
  // Puts a visible popover next to the pointer, kept inside the viewport
  def place(root: html.Element, clientX: Double, clientY: Double): Unit = {
    val margin = 8.0
    val maxLeft = dom.window.innerWidth - root.offsetWidth - margin
    val maxTop = dom.window.innerHeight - root.offsetHeight - margin
    root.style.left = s"${Math.max(margin, Math.min(maxLeft, clientX + 12))}px"
    root.style.top = s"${Math.max(margin, Math.min(maxTop, clientY + 12))}px"
  }
}
