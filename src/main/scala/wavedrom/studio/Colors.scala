package wavedrom.studio

// How one bus cycle is painted: its fill and the colour of its label. The
// outline is the signal's line colour.
case class BusStyle(fill: String, text: String)

// The colours of the data-bus tokens: WaveDrom's own fills for `2`..`9`.
// This is the one place to change to swap the palette.
object BusPalette {
  private val labelText = "#000"

  // `=`: the plain bus, white like `2`
  val default: BusStyle = BusStyle("#fff", labelText)

  // Wave digit -> fill
  val fills: Vector[(Int, String)] = Vector(
    2 -> "#fff", // white
    3 -> "#ffffb4", // yellow
    4 -> "#ffe0b9", // orange
    5 -> "#b9e0ff", // blue
    6 -> "#ccfdfe", // cyan
    7 -> "#cdfdc5", // green
    8 -> "#f0c1fb", // purple
    9 -> "#f5c2c0" // red
  )

  private val styles: Map[Int, BusStyle] = fills.map { case (digit, fill) =>
    digit -> BusStyle(fill, labelText)
  }.toMap

  def style(state: WaveState): BusStyle = state match {
    case WaveState.ColoredBus(digit) => styles.getOrElse(digit, default)
    case _                           => default
  }
}

// The optional per-signal colour: the `color` key of a signal object, which
// is an extension to WaveJSON that standard WaveDrom ignores.
object SignalColor {
  // Offered as swatches in the cycle editor
  val presets: Vector[String] = Vector(
    "#d32f2f", // red
    "#e65100", // orange
    "#9a7400", // ochre
    "#2e7d32", // green
    "#00838f", // teal
    "#0041c4", // blue
    "#6a1b9a", // purple
    "#c2185b" // pink
  )

  private val hex = "#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})".r

  private val named: Set[String] =
    """aliceblue antiquewhite aqua aquamarine azure beige bisque black
      |blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse
      |chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan
      |darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta
      |darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen
      |darkslateblue darkslategray darkslategrey darkturquoise darkviolet
      |deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite
      |forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green
      |greenyellow grey honeydew hotpink indianred indigo ivory khaki lavender
      |lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan
      |lightgoldenrodyellow lightgray lightgreen lightgrey lightpink
      |lightsalmon lightseagreen lightskyblue lightslategray lightslategrey
      |lightsteelblue lightyellow lime limegreen linen magenta maroon
      |mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen
      |mediumslateblue mediumspringgreen mediumturquoise mediumvioletred
      |midnightblue mintcream mistyrose moccasin navajowhite navy oldlace
      |olive olivedrab orange orangered orchid palegoldenrod palegreen
      |paleturquoise palevioletred papayawhip peachpuff peru pink plum
      |powderblue purple rebeccapurple red rosybrown royalblue saddlebrown
      |salmon sandybrown seagreen seashell sienna silver skyblue slateblue
      |slategray slategrey snow springgreen steelblue tan teal thistle tomato
      |turquoise violet wheat white whitesmoke yellow
      |yellowgreen""".stripMargin.split("\\s+").toSet

  // The colour in a form that is safe to put in an SVG attribute: a hex
  // colour or a CSS colour name, lower-cased. None for anything else, since
  // the JSON is free text typed by the user.
  def sanitize(raw: String): Option[String] = {
    val value = raw.trim.toLowerCase
    if (hex.matches(value) || named.contains(value)) Some(value) else None
  }

  // `#rrggbb`, the only form <input type="color"> accepts
  def toPickerValue(color: String): Option[String] =
    sanitize(color).filter(_.startsWith("#")).map {
      case c if c.length == 7 => c
      case c if c.length == 9 => c.take(7)
      case c                  => "#" + c.slice(1, 4).flatMap(ch => s"$ch$ch")
    }
}
