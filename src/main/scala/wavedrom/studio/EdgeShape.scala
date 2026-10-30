package wavedrom.studio

// The shapes of the strings in the `edge` list, such as `a-|->b 3 cycles`.
// Everything here is pure; the renderer only draws what it is given.
object EdgeShape {

  // How the line runs between the two nodes
  enum Family {
    case Spline, SplineOut, SplineIn, ElbowH, ElbowV, ElbowMid, Line, Tee
  }

  case class Shape(token: String, family: Family) {
    // `<` at the start puts an arrowhead on the first node, `>` at the end on
    // the second
    def startArrow: Boolean = token.startsWith("<")
    def endArrow: Boolean = token.endsWith(">")
  }

  case class Parsed(from: String, shape: Shape, to: String, label: Option[String])

  case class Geometry(
      path: String,
      labelX: Double,
      labelY: Double,
      startArrow: Boolean,
      endArrow: Boolean,
      tee: Boolean
  )

  import Family.*

  // Listed longest first, so `-|->` is never read as `-|-` followed by `>`
  val shapes: Vector[Shape] = Vector(
    "<-|->" -> ElbowMid, "<-|>" -> ElbowH, "<-~>" -> SplineOut,
    "-|->" -> ElbowMid, "<->" -> Line, "<~>" -> Spline,
    "-|-" -> ElbowMid, "-|>" -> ElbowH, "|->" -> ElbowV,
    "-~>" -> SplineOut, "~->" -> SplineIn,
    "-|" -> ElbowH, "|-" -> ElbowV, "-~" -> SplineOut, "~-" -> SplineIn,
    "->" -> Line, "~>" -> Spline, "<-" -> Line,
    "-" -> Line, "~" -> Spline, "+" -> Tee
  ).map { case (token, family) => Shape(token, family) }

  private val pattern = {
    val alternatives =
      shapes.map(s => java.util.regex.Pattern.quote(s.token)).mkString("|")
    s"""^([a-zA-Z0-9_]+)\\s*($alternatives)\\s*([a-zA-Z0-9_]+)(?:\\s+(.*))?$$""".r
  }

  def parse(edge: String): Option[Parsed] =
    edge.trim match {
      case pattern(from, token, to, label) =>
        shapes.find(_.token == token).map { shape =>
          Parsed(from, shape, to, Option(label).map(_.trim).filter(_.nonEmpty))
        }
      case _ => None
    }

  // The curves and elbows are the ones WaveDrom draws (its arc-shape.js)
  def geometry(shape: Shape, from: (Double, Double), to: (Double, Double)): Geometry = {
    val (x1, y1) = from
    val (x2, y2) = to
    val dx = x2 - x1
    val dy = y2 - y1
    val midX = x1 + dx / 2
    val midY = y1 + dy / 2

    def spline(c1x: Double, c1y: Double, c2x: Double, c2y: Double, labelX: Double) =
      (s"M $x1 $y1 C $c1x $c1y, $c2x $c2y, $x2 $y2", labelX)

    val (path, labelX) = shape.family match {
      case Spline     => spline(x1 + 0.7 * dx, y1, x1 + 0.3 * dx, y2, midX)
      case SplineOut  => spline(x1 + 0.7 * dx, y1, x2, y2, x1 + 0.75 * dx)
      case SplineIn   => spline(x1, y1, x1 + 0.3 * dx, y2, x1 + 0.25 * dx)
      case ElbowH     => (s"M $x1 $y1 L $x2 $y1 L $x2 $y2", x2)
      case ElbowV     => (s"M $x1 $y1 L $x1 $y2 L $x2 $y2", x1)
      case ElbowMid   => (s"M $x1 $y1 L $midX $y1 L $midX $y2 L $x2 $y2", midX)
      case Line | Tee => (s"M $x1 $y1 L $x2 $y2", midX)
    }
    Geometry(path, labelX, midY, shape.startArrow, shape.endArrow, shape.family == Tee)
  }
}
