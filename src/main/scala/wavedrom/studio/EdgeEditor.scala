package wavedrom.studio

// Edits to the `edge` list of a document. Everything here is pure; an index
// that is out of range, or an entry that does not parse, leaves the list as it is.
object EdgeEditor {

  // Node names as the edge strings can carry them
  def isNodeName(name: String): Boolean =
    name.nonEmpty && name.forall(c => (c < 128 && c.isLetterOrDigit) || c == '_')

  // The shapes by what they look like, in the order the arrow popover lists them
  val styleGroups: Vector[(String, Vector[EdgeShape.Shape])] = {
    def of(tokens: String*) =
      tokens.toVector.map(t => EdgeShape.shapes.find(_.token == t).get)
    Vector(
      "Curves" -> of("~>", "<~>", "~", "-~>", "~->", "<-~>", "-~", "~-"),
      "Elbows" -> of("-|>", "-|->", "<-|>", "<-|->", "|->", "-|-", "-|", "|-"),
      "Straight lines" -> of("->", "<->", "<-", "-"),
      "Measurement" -> of("+")
    )
  }

  def format(from: String, shape: EdgeShape.Shape, to: String, label: Option[String]): String =
    s"$from${shape.token}$to" + cleanLabel(label).fold("")(" " + _)

  private def cleanLabel(label: Option[String]): Option[String] =
    label.map(_.trim).filter(_.nonEmpty)

  def add(
      edges: Vector[String],
      from: String,
      shape: EdgeShape.Shape,
      to: String,
      label: Option[String]
  ): Vector[String] =
    edges :+ format(from, shape, to, label)

  // Style and label together, as the popover applies them; from and to stay
  def update(
      edges: Vector[String],
      index: Int,
      shape: EdgeShape.Shape,
      label: Option[String]
  ): Vector[String] =
    rewrite(edges, index)(p => format(p.from, shape, p.to, label))

  def restyle(edges: Vector[String], index: Int, shape: EdgeShape.Shape): Vector[String] =
    rewrite(edges, index)(p => format(p.from, shape, p.to, p.label))

  def relabel(edges: Vector[String], index: Int, label: Option[String]): Vector[String] =
    rewrite(edges, index)(p => format(p.from, p.shape, p.to, label))

  def delete(edges: Vector[String], index: Int): Vector[String] =
    if (edges.indices.contains(index)) edges.patch(index, Nil, 1) else edges

  private def rewrite(edges: Vector[String], index: Int)(
      build: EdgeShape.Parsed => String
  ): Vector[String] =
    (for {
      entry <- edges.lift(index)
      parsed <- EdgeShape.parse(entry)
    } yield edges.updated(index, build(parsed))).getOrElse(edges)
}
