package wavedrom.studio

// The rich text of `head.text` and `foot.text`: WaveDrom's JsonML, reduced to
// plain text and `tspan` elements.
sealed trait RichText
object RichText {
  final case class Text(value: String) extends RichText
  // `attrs` are kept as written; toSvg decides which of them are drawn
  final case class Span(attrs: Map[String, String], children: Vector[RichText])
      extends RichText

  // Written into the SVG itself, never left to page CSS, so exports are
  // self-contained. These are the values of WaveDrom's default skin.
  private val classAttrs: Map[String, Vector[(String, String)]] = Map(
    "h1" -> Vector("font-size" -> "33.6", "font-weight" -> "bold"),
    "h2" -> Vector("font-size" -> "28", "font-weight" -> "bold"),
    "h3" -> Vector("font-size" -> "24", "font-weight" -> "bold"),
    "h4" -> Vector("font-size" -> "21", "font-weight" -> "bold"),
    "h5" -> Vector("font-size" -> "18.7", "font-weight" -> "bold"),
    "h6" -> Vector("font-size" -> "16", "font-weight" -> "bold"),
    "muted" -> Vector("fill" -> "#aaa"),
    "warning" -> Vector("fill" -> "#f6b900"),
    "error" -> Vector("fill" -> "#f60000"),
    "info" -> Vector("fill" -> "#0041c4"),
    "success" -> Vector("fill" -> "#00ab00")
  )

  // Also the order the attributes are written in
  private val allowedAttrs = Vector(
    "fill",
    "font-weight",
    "font-style",
    "font-size",
    "dy",
    "text-decoration",
    "baseline-shift"
  )

  // The attributes a span is drawn with: its classes first, then its own
  // attributes, which win
  private def drawnAttrs(span: Span): Vector[(String, String)] = {
    val fromClass = span.attrs
      .get("class")
      .toVector
      .flatMap(_.split("\\s+").toVector)
      .flatMap(c => classAttrs.getOrElse(c, Vector.empty))
    val own = span.attrs.toVector.filter { case (k, _) => allowedAttrs.contains(k) }
    val merged = (fromClass ++ own).toMap
    allowedAttrs.flatMap(k => merged.get(k).map(k -> _))
  }

  // The content for inside a `<text>` element
  def toSvg(text: RichText): String = text match {
    case Text(value) => escape(value)
    case span: Span =>
      val attrs = drawnAttrs(span).map { case (k, v) => s""" $k="${escape(v)}"""" }
      s"<tspan${attrs.mkString}>${span.children.map(toSvg).mkString}</tspan>"
  }

  // The largest font size the text asks for, or `default`; used to make room
  def maxFontSize(text: RichText, default: Double): Double = text match {
    case Text(_) => default
    case span: Span =>
      val own = drawnAttrs(span).collectFirst { case ("font-size", v) => v.toDoubleOption }.flatten
      val inner = span.children.map(maxFontSize(_, default))
      (own.toVector ++ inner).maxOption.getOrElse(default)
  }

  private def escape(str: String): String =
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
}
