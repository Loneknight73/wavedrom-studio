package wavedrom.studio

class SvgRendererSuite extends munit.FunSuite {

  private def render(json: String): String = {
    val state = new AppState()
    val loaded = state.updateFromText(json)
    assert(loaded.isRight, loaded)
    SvgRenderer.renderToString(state)
  }

  private def occurrences(haystack: String, needle: String): Int =
    haystack.sliding(needle.length).count(_ == needle)

  test("renders a placeholder when no diagram is loaded") {
    assertEquals(
      SvgRenderer.renderToString(new AppState()),
      "No diagram loaded"
    )
  }

  test("renders a placeholder when there are no signals") {
    assertEquals(render("""{ "signal": [] }"""), "No signals defined")
  }

  test("renders one row per signal") {
    val svg = render("""{ "signal": [
      { "name": "clk", "wave": "p..." },
      { "name": "req", "wave": "01.0" },
      { "name": "bus", "wave": "x==x", "data": ["D0", "D1"] }
    ] }""")
    assert(svg.startsWith("<svg"), svg.take(40))
    assert(svg.endsWith("</svg>"), svg.takeRight(40))
    assertEquals(occurrences(svg, """class="signal-row""""), 3)
    assert(svg.contains(">D0</text>"))
    assert(svg.contains(">D1</text>"))
  }

  // --- Colours ------------------------------------------------------------

  private def hueOf(digit: Int): String =
    BusPalette.hues.collectFirst { case (`digit`, hue) => hue }.get

  test("a plain bus is drawn as it always was") {
    val svg = render("""{ "signal": [ { "name": "b", "wave": "x=.x", "data": ["D"] } ] }""")
    assertEquals(
      occurrences(svg, """fill="#0284c7" fill-opacity="0.2" stroke="#38bdf8" stroke-width="2" />"""),
      2
    )
    assert(svg.contains("""fill="#e0f2fe" font-size="10" font-weight="600" text-anchor="middle">D</text>"""), svg)
  }

  test("each bus digit is filled with its own colour") {
    val svg = render("""{ "signal": [ { "name": "b", "wave": "23456789" } ] }""")
    (2 to 9).foreach { digit =>
      assertEquals(occurrences(svg, s"""fill="${hueOf(digit)}" fill-opacity"""), 1, s"digit $digit")
    }
    assertEquals(BusPalette.hues.map(_._2).distinct.length, 8)
    assert(!svg.contains("""fill="#0284c7""""), svg)
  }

  test("a hold continues the colour of the bus before it") {
    val svg = render("""{ "signal": [ { "name": "b", "wave": "3..5.=" } ] }""")
    assertEquals(occurrences(svg, s"""fill="${hueOf(3)}" fill-opacity"""), 3)
    assertEquals(occurrences(svg, s"""fill="${hueOf(5)}" fill-opacity"""), 2)
    assertEquals(occurrences(svg, """fill="#0284c7" fill-opacity"""), 1)
  }

  test("adjacent buses of different colours are separate segments") {
    // Each segment is pinched to a point at both ends, at the row's mid line
    val one = render("""{ "signal": [ { "name": "b", "wave": "3." } ] }""")
    val two = render("""{ "signal": [ { "name": "b", "wave": "34" } ] }""")
    val same = render("""{ "signal": [ { "name": "b", "wave": "33" } ] }""")
    def closedShapes(svg: String): Int = occurrences(svg, " Z\" fill=")
    def pinches(svg: String): Int = "<path d=\"M [0-9.]+ 59(?:\\.0)? L".r.findAllIn(svg).length
    assertEquals(closedShapes(one), 2)
    assertEquals(pinches(one), 1)
    assertEquals(pinches(two), 2)
    assertEquals(pinches(same), 2)
  }

  test("data labels are matched to bus segments whatever their colour") {
    val svg = render("""{ "signal": [
      { "name": "b", "wave": "x2.3=0.7", "data": ["A", "B", "C", "D"] }
    ] }""")
    def labelX(label: String): Double = {
      val m = ("<text x=\"([0-9.]+)\"[^>]*>" + label + "</text>").r.findFirstMatchIn(svg)
      assert(m.nonEmpty, s"label $label")
      m.get.group(1).toDouble
    }
    // labelWidth 120, step 45: the label sits in the middle of the cycle
    // that starts its segment
    assertEquals(labelX("A"), 120 + 1 * 45 + 22.5)
    assertEquals(labelX("B"), 120 + 3 * 45 + 22.5)
    assertEquals(labelX("C"), 120 + 4 * 45 + 22.5)
    assertEquals(labelX("D"), 120 + 7 * 45 + 22.5)
  }

  test("a signal colour replaces the default strokes of its own row only") {
    val svg = render("""{ "signal": [
      { "name": "a", "wave": "01pnz3", "color": "#FF8800" },
      { "name": "b", "wave": "01" }
    ] }""")
    val rows = svg.split("<g class=\"signal-row\"").drop(1)
    val colored = rows(0)
    val plain = rows(1).split("<g id=\"cycleHits\"")(0)
    assert(colored.contains("""stroke="#ff8800" stroke-width="2.5""""), colored)
    assert(colored.contains("""stroke="#ff8800" stroke-width="2" stroke-dasharray="4 2""""), colored)
    List("#10b981", "#818cf8", "#f59e0b", "#38bdf8").foreach { default =>
      assert(!colored.contains(s"""stroke="$default""""), s"$default in $colored")
    }
    // The bus keeps the fill of its digit; only its outline follows the signal
    assert(
      colored.contains(s"""fill="${hueOf(3)}" fill-opacity="0.3" stroke="#ff8800""""),
      colored
    )
    assert(plain.contains("""stroke="#10b981""""), plain)
    assert(!plain.contains("#ff8800"), plain)
  }

  test("undefined cycles stay red on a coloured signal") {
    val svg = render("""{ "signal": [ { "name": "a", "wave": "x", "color": "#ff8800" } ] }""")
    assert(svg.contains("""fill="url(#hatch)" stroke="#ef4444""""), svg)
  }

  test("an invalid signal colour never reaches the SVG and the default is used") {
    val svg = render(
      """{ "signal": [ { "name": "a", "wave": "01", "color": "red\" onload=\"alert(1)" } ] }"""
    )
    assert(!svg.contains("onload"), svg)
    assert(svg.contains("""stroke="#10b981" stroke-width="2.5""""), svg)

    val unknown = render("""{ "signal": [ { "name": "a", "wave": "01", "color": "notacolour" } ] }""")
    assert(!unknown.contains("notacolour"), unknown)
    assert(unknown.contains("""stroke="#10b981" stroke-width="2.5""""), unknown)
  }

  test("renders an edge between two resolved nodes") {
    val svg = render("""{ "signal": [
      { "name": "req", "wave": "01.0", "node": ".a.." },
      { "name": "ack", "wave": "0.10", "node": "..b." }
    ], "edge": ["a~>b grant"] }""")
    assert(svg.contains("""id="edgesLayer""""))
    assert(svg.contains("""marker-end="url(#arrowEnd)""""))
    assert(svg.contains(">grant</text>"))
  }

  test("skips an edge whose nodes do not exist") {
    val svg = render("""{ "signal": [
      { "name": "req", "wave": "01.0", "node": ".a.." }
    ], "edge": ["a->missing"] }""")
    assert(!svg.contains("marker-end="))
  }

  private val twoSignals = """{ "signal": [
    { "name": "req", "wave": "01.0", "node": ".a.." },
    {},
    { "name": "ack", "wave": "0.10" }
  ] }"""

  test("emits a click target per cycle for every signal except spacers") {
    val svg = render(twoSignals)
    // 2 signals x 8 columns (the minimum diagram width)
    assertEquals(occurrences(svg, """class="cycle-hit""""), 16)
    assert(svg.contains("""data-sig="2" data-cycle="7""""))
    assert(!svg.contains("""data-sig="1""""))
  }

  test("marks nodes with data attributes instead of inline handlers") {
    val svg = render(twoSignals)
    assert(svg.contains("""data-node="a""""))
    assert(!svg.contains("onclick"))
  }

  test("places node markers on the leading edge of their cycle") {
    val state = new AppState()
    state.updateFromText(twoSignals)
    val svg = SvgRenderer.renderToString(state)
    // labelWidth 120 + cycle 1 * step 45; the badge is centred on that x
    val centre = 120.0 + 1 * state.stepWidth
    val badge = """<text x="([0-9.]+)" y="[0-9.]+" fill="#ffffff"""".r
    assertEquals(badge.findFirstMatchIn(svg).map(_.group(1).toDouble), Some(centre))
  }

  test("zoom scales the displayed size and leaves the drawing itself alone") {
    val state = new AppState()
    state.updateFromText(twoSignals)
    val natural = SvgRenderer.renderToString(state)
    state.zoom = 2.0
    val zoomed = SvgRenderer.renderToString(state)

    val size = """<svg width="([0-9.]+)" height="([0-9.]+)" viewBox="0 0 ([0-9.]+) ([0-9.]+)"""".r
    def sizes(svg: String): Vector[Double] =
      size.findFirstMatchIn(svg).get.subgroups.map(_.toDouble).toVector

    val Vector(w, h, vw, vh) = sizes(natural)
    assertEquals((w, h), (vw, vh))
    assertEquals(sizes(zoomed), Vector(2 * w, 2 * h, vw, vh))
    assertEquals(zoomed.drop(zoomed.indexOf('>')), natural.drop(natural.indexOf('>')))
  }

  test("highlights the cycle being edited") {
    val state = new AppState()
    state.updateFromText(twoSignals)
    state.activeCycle = Some((2, 3))
    val svg = SvgRenderer.renderToString(state)
    assert(
      svg.contains("""class="cycle-hit active" data-sig="2" data-cycle="3"""")
    )
    assertEquals(occurrences(svg, "cycle-hit active"), 1)
  }
}
