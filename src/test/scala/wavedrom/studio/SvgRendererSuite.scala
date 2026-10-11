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

  test("draws in the classic WaveDrom colours, carried by the SVG itself") {
    val svg = render("""{ "signal": [
      { "name": "clk", "wave": "p..." },
      { "name": "bus", "wave": "x=.z", "data": ["D0"], "node": ".a.b" }
    ], "edge": ["a->b t"] }""")
    val colours = "#[0-9a-fA-F]{3,6}\\b".r.findAllIn(svg).toSet
    assertEquals(colours, Set("#000", "#fff", "#888", "#666", "#0041c4"))
    assert(svg.contains("""font-family="Helvetica, Arial, sans-serif""""))
    // The hatching of an undefined region comes with its own pattern
    assert(svg.contains("""<pattern id="hatch""""))
    assert(svg.contains("""fill="url(#hatch)""""))
    // A bus is a white box with a black outline
    assert(svg.contains("""fill="#fff" stroke="none""""))
  }

  test("outlines a held bus value as one box") {
    val state = new AppState()
    state.updateFromText("""{ "signal": [ { "name": "bus", "wave": "=.", "data": ["D0"] } ] }""")
    val svg = SvgRenderer.renderToString(state)
    // The second cycle's outline is open on the boundary it shares with the
    // first (x 165) and pinches shut 6px before its own end
    val x = 120.0 + state.stepWidth
    val end = x + state.stepWidth
    val (high, mid, low) = (47.0, 59.0, 71.0)
    assert(
      svg.contains(
        s"""d="M $x $high L ${end - 6} $high L $end $mid L ${end - 6} $low L $x $low" stroke="#000""""
      ),
      svg
    )
  }

  test("slants the edge between two levels") {
    val state = new AppState()
    state.updateFromText("""{ "signal": [ { "name": "req", "wave": "01" } ] }""")
    val svg = SvgRenderer.renderToString(state)
    // Row 0: low at y 71, high at y 47; the change starts on the boundary of
    // cycle 1 and reaches the new level 4px later
    val x = 120.0 + state.stepWidth
    val (low, high) = (71.0, 47.0)
    assert(svg.contains(s"""d="M $x $low L ${x + 4} $high""""), svg)
    assert(svg.contains(s"""d="M ${x + 4} $high L ${x + state.stepWidth} $high""""), svg)
  }

  // --- Colours ------------------------------------------------------------

  private def fillOf(digit: Int): String =
    BusPalette.fills.collectFirst { case (`digit`, fill) => fill }.get

  private def busFills(svg: String, fill: String): Int =
    occurrences(svg, s"""fill="$fill" stroke="none"""")

  test("a plain bus is white") {
    val svg = render("""{ "signal": [ { "name": "b", "wave": "x=.x", "data": ["D"] } ] }""")
    assertEquals(busFills(svg, "#fff"), 2)
    assert(svg.contains("""fill="#000" font-size="11" text-anchor="middle">D</text>"""), svg)
  }

  test("each bus digit is filled with its own WaveDrom colour") {
    val svg = render("""{ "signal": [ { "name": "b", "wave": "23456789" } ] }""")
    (2 to 9).foreach { digit =>
      assertEquals(busFills(svg, fillOf(digit)), 1, s"digit $digit")
    }
    assertEquals(BusPalette.fills.map(_._2).distinct.length, 8)
    assertEquals(fillOf(3), "#ffffb4")
  }

  test("a hold continues the colour of the bus before it") {
    val svg = render("""{ "signal": [ { "name": "b", "wave": "3..5.=" } ] }""")
    assertEquals(busFills(svg, fillOf(3)), 3)
    assertEquals(busFills(svg, fillOf(5)), 2)
    assertEquals(busFills(svg, "#fff"), 1)
  }

  test("adjacent buses of different colours are separate values") {
    val firstFill = """<path class="bus-fill" d="([^"]+)"""".r
    def first(wave: String): String = {
      val svg = render(s"""{ "signal": [ { "name": "b", "wave": "$wave" } ] }""")
      firstFill.findFirstMatchIn(svg).get.group(1)
    }
    // A new token closes the value before it, whatever its colour; only a
    // hold lets it run on
    assertEquals(first("34"), first("33"))
    assertNotEquals(first("34"), first("3."))
  }

  test("data labels are matched to bus values whatever their colour") {
    val svg = render("""{ "signal": [
      { "name": "b", "wave": "x2.3=0.7", "data": ["A", "B", "C", "D"] }
    ] }""")
    def labelX(label: String): Double = {
      val m = ("<text class=\"bus-label\" x=\"([0-9.]+)\"[^>]*>" + label + "</text>").r
        .findFirstMatchIn(svg)
      assert(m.nonEmpty, s"label $label")
      m.get.group(1).toDouble
    }
    // labelWidth 120, step 45: each label is centred over its whole value
    assertEquals(labelX("A"), 120 + 2 * 45.0)
    assertEquals(labelX("B"), 120 + 3.5 * 45)
    assertEquals(labelX("C"), 120 + 4.5 * 45)
    assertEquals(labelX("D"), 120 + 7.5 * 45)
  }

  test("a signal colour replaces black for the lines of its own row only") {
    val svg = render("""{ "signal": [
      { "name": "a", "wave": "01PnzX3", "color": "#FF8800" },
      { "name": "b", "wave": "01" }
    ] }""")
    val rows = svg.split("<g class=\"signal-row\"").drop(1)
    val colored = rows(0)
    val plain = rows(1).split("<g id=\"cycleHits\"")(0)
    assert(colored.contains("""stroke="#ff8800" stroke-width="1""""), colored)
    assert(!colored.contains("""stroke="#000""""), colored)
    // The arrow of a clock follows the signal as well
    assert(colored.contains("""fill="#ff8800" stroke="none""""), colored)
    // A bus keeps the fill of its digit, and an undefined cycle its hatching
    assertEquals(busFills(colored, fillOf(3)), 1)
    assert(colored.contains("""fill="url(#hatch)""""), colored)
    assert(plain.contains("""stroke="#000""""), plain)
    assert(!plain.contains("#ff8800"), plain)
  }

  test("an invalid signal colour never reaches the SVG and black is used") {
    val svg = render(
      """{ "signal": [ { "name": "a", "wave": "01", "color": "red\" onload=\"alert(1)" } ] }"""
    )
    assert(!svg.contains("onload"), svg)
    assert(svg.contains("""stroke="#000" stroke-width="1""""), svg)

    val unknown = render("""{ "signal": [ { "name": "a", "wave": "01", "color": "notacolour" } ] }""")
    assert(!unknown.contains("notacolour"), unknown)
    assert(unknown.contains("""stroke="#000" stroke-width="1""""), unknown)
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
    // labelWidth 120 + cycle 1 * step 45; the marker is centred on that x
    val centre = 120.0 + 1 * state.stepWidth
    val badge = """<text class="node-label" x="([0-9.]+)"""".r
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

  test("upper-case clocks carry an arrow on every cycle, lower-case ones none") {
    val plain = render("""{ "signal": [ { "name": "clk", "wave": "p..n" } ] }""")
    assertEquals(occurrences(plain, "clock-arrow"), 0)

    val arrows = render("""{ "signal": [ { "name": "clk", "wave": "P..N" } ] }""")
    assertEquals(occurrences(arrows, "clock-arrow"), 4)
  }

  test("a bus label is centred over the whole held value") {
    val svg = render("""{ "signal": [
      { "name": "d", "wave": "x=...=x", "data": ["tail", "one"] }
    ] }""")
    val label = """<text class="bus-label" x="([0-9.]+)"[^>]*>([^<]*)<""".r
    val xs = label.findAllMatchIn(svg).map(m => m.group(2) -> m.group(1).toDouble).toMap
    // labelWidth 120, step 45: "tail" spans cycles 1-4, "one" is cycle 5
    assertEquals(xs("tail"), 120 + 45 * 3.0)
    assertEquals(xs("one"), 120 + 45 * 5.5)
    // Labels come after the row's fills, so no later fill can cover them
    assert(svg.indexOf("bus-label") > svg.lastIndexOf("""class="bus-fill""""))
  }
}
