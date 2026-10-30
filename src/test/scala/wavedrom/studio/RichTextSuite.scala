package wavedrom.studio

import wavedrom.studio.RichText.{Span, Text}

class RichTextSuite extends munit.FunSuite {

  test("plain text is escaped") {
    assertEquals(RichText.toSvg(Text("a<b & \"c\"")), "a&lt;b &amp; &quot;c&quot;")
  }

  test("classes become inline attributes") {
    val svg = RichText.toSvg(Span(Map("class" -> "h1 error"), Vector(Text("x"))))
    assertEquals(
      svg,
      """<tspan fill="#f60000" font-weight="bold" font-size="33.6">x</tspan>"""
    )
    assertEquals(
      RichText.toSvg(Span(Map("class" -> "muted"), Vector.empty)),
      """<tspan fill="#aaa"></tspan>"""
    )
    assert(!svg.contains("class="))
  }

  test("an explicit attribute wins over a class") {
    val svg = RichText.toSvg(Span(Map("class" -> "error", "fill" -> "red"), Vector(Text("x"))))
    assertEquals(svg, """<tspan fill="red">x</tspan>""")
  }

  test("attributes outside the allowed list are dropped, values escaped") {
    val span = Span(
      Map("onclick" -> "evil()", "style" -> "x", "dy" -> "2", "fill" -> "\"><script>"),
      Vector(Text("t"))
    )
    val svg = RichText.toSvg(span)
    assert(!svg.contains("onclick") && !svg.contains("style"), svg)
    assert(svg.contains("""dy="2""""), svg)
    assert(svg.contains("""fill="&quot;&gt;&lt;script&gt;""""), svg)
  }

  test("nested spans are rendered in order") {
    val tree = Span(Map.empty, Vector(Text("a"), Span(Map("font-style" -> "italic"), Vector(Text("b")))))
    assertEquals(RichText.toSvg(tree), """<tspan>a<tspan font-style="italic">b</tspan></tspan>""")
  }

  test("the largest font size decides how much room the text needs") {
    assertEquals(RichText.maxFontSize(Text("x"), 14), 14.0)
    val tree = Span(Map.empty, Vector(Span(Map("class" -> "h2"), Vector(Text("x")))))
    assertEquals(RichText.maxFontSize(tree, 14), 28.0)
  }
}
