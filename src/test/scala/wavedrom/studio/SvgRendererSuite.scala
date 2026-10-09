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
}
