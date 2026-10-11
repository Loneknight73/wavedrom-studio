package wavedrom.studio

// How one bus cycle is painted. `stroke` is the outline used when the signal
// has no colour of its own.
case class BusStyle(
    fill: String,
    fillOpacity: Double,
    stroke: String,
    text: String
)

// The colours of the data-bus tokens. WaveDrom's own fills for `2`..`9` are
// pastels meant for a white page (white, yellow, orange, blue, cyan, green,
// purple, red); these are the same hues, tuned for the dark canvas. This is
// the one place to change to swap the palette.
object BusPalette {
  // `=`: the plain bus
  val default: BusStyle = BusStyle("#0284c7", 0.2, "#38bdf8", "#e0f2fe")

  private val fillOpacity = 0.3
  private val labelText = "#f8fafc"

  // Wave digit -> hue. The hue is the outline at full strength and the fill
  // at `fillOpacity`, which keeps the light label text readable.
  val hues: Vector[(Int, String)] = Vector(
    2 -> "#e2e8f0", // white
    3 -> "#facc15", // yellow
    4 -> "#fb923c", // orange
    5 -> "#60a5fa", // blue
    6 -> "#22d3ee", // cyan
    7 -> "#4ade80", // green
    8 -> "#d946ef", // purple
    9 -> "#f87171" // red
  )

  private val styles: Map[Int, BusStyle] = hues.map { case (digit, hue) =>
    digit -> BusStyle(hue, fillOpacity, hue, labelText)
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
    "#f87171",
    "#fb923c",
    "#facc15",
    "#4ade80",
    "#22d3ee",
    "#60a5fa",
    "#c084fc",
    "#f472b6"
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
