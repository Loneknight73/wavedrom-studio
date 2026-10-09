package wavedrom.studio

import scala.scalajs.js

class WaveJsonCodecSuite extends munit.FunSuite {

  private def parseOk(json: String): WaveDiagram =
    WaveJsonCodec.parse(json) match {
      case Right(diagram) => diagram
      case Left(err)      => fail(s"expected successful parse, got: $err")
    }

  test("parse reads flat signals") {
    val diagram = parseOk("""{ "signal": [
      { "name": "clk", "wave": "p..." },
      { "name": "req", "wave": "01.0", "node": ".a.b" }
    ] }""")
    assertEquals(
      diagram.signals,
      Vector(
        SignalTrack("clk", "p..."),
        SignalTrack("req", "01.0", node = ".a.b")
      )
    )
    assertEquals(diagram.groups, Vector.empty)
    assertEquals(diagram.edges, Vector.empty)
  }

  test("parse reads data given as an array") {
    val diagram = parseOk(
      """{ "signal": [ { "name": "d", "wave": "==", "data": ["A", "B"] } ] }"""
    )
    assertEquals(diagram.signals.head.data, Vector("A", "B"))
  }

  test("parse reads data given as a space-separated string") {
    val diagram = parseOk(
      """{ "signal": [ { "name": "d", "wave": "===", "data": "A B  C" } ] }"""
    )
    assertEquals(diagram.signals.head.data, Vector("A", "B", "C"))
  }

  test("parse defaults a missing name and wave") {
    val diagram = parseOk(
      """{ "signal": [ { "wave": "01" }, { "name": "b" } ] }"""
    )
    assertEquals(diagram.signals(0).name, "sig_0")
    assertEquals(diagram.signals(1).wave, "")
  }

  test("parse flattens a group into signals and an index range") {
    val diagram = parseOk("""{ "signal": [
      { "name": "x", "wave": "0" },
      [ "G",
        { "name": "a", "wave": "0" },
        { "name": "b", "wave": "1" }
      ]
    ] }""")
    assertEquals(diagram.signals.map(_.name), Vector("x", "a", "b"))
    assertEquals(diagram.signals.map(_.depth), Vector(0, 1, 1))
    assertEquals(diagram.groups, Vector(SignalGroup("G", 0, 1, 2)))
  }

  test("parse handles nested groups") {
    val diagram = parseOk("""{ "signal": [
      [ "Outer",
        { "name": "a", "wave": "0" },
        [ "Inner",
          { "name": "b", "wave": "0" },
          { "name": "c", "wave": "0" }
        ]
      ]
    ] }""")
    assertEquals(diagram.signals.map(_.depth), Vector(1, 2, 2))
    assertEquals(
      diagram.groups.toSet,
      Set(SignalGroup("Outer", 0, 0, 2), SignalGroup("Inner", 1, 1, 2))
    )
  }

  test("parse reads the edge array") {
    val diagram = parseOk(
      """{ "signal": [ { "name": "a", "wave": "0" } ], "edge": ["a~>b go", "b->c"] }"""
    )
    assertEquals(diagram.edges, Vector("a~>b go", "b->c"))
  }

  test("parse reports invalid JSON") {
    val result = WaveJsonCodec.parse("""{ "signal": [ """)
    assert(result.left.exists(_.startsWith("JSON Syntax Error")), result)
  }

  test("parseNodeTokens maps compact node strings to cycle indices") {
    assertEquals(
      WaveJsonCodec.parseNodeTokens("...a..b"),
      Map(3 -> Vector("a"), 6 -> Vector("b"))
    )
  }

  test("parseNodeTokens treats dots inside a spaced token as gaps") {
    assertEquals(
      WaveJsonCodec.parseNodeTokens("..... skkdc ..d.."),
      Map(5 -> Vector("skkdc"), 8 -> Vector("d"))
    )
    assertEquals(
      WaveJsonCodec.parseNodeTokens("a.b ..c"),
      Map(0 -> Vector("a"), 2 -> Vector("b"), 5 -> Vector("c"))
    )
  }

  test("parseNodeTokens returns nothing for an empty string") {
    assertEquals(WaveJsonCodec.parseNodeTokens(""), Map.empty)
  }

  test("parse treats an empty object as a spacer row") {
    val diagram = parseOk(
      """{ "signal": [ { "name": "a", "wave": "0" }, {}, { "name": "b", "wave": "1" } ] }"""
    )
    assertEquals(diagram.signals.map(_.name), Vector("a", "", "b"))
    assertEquals(diagram.signals.map(_.isSpacer), Vector(false, true, false))
  }

  private val richJson = """{
    "signal": [
      { "name": "clk", "wave": "p...", "period": 2 },
      {},
      [ "Bus",
        { "name": "addr", "wave": "x==x", "data": "A0 A1", "node": ".a.." },
        [ "Inner", { "name": "rd", "wave": "0.1." } ]
      ],
      { "name": "last", "wave": "0..." }
    ],
    "edge": ["a~>b go"],
    "head": { "text": "Title" },
    "config": { "hscale": 2 }
  }"""

  private def parseDocOk(json: String): WaveDocument =
    WaveJsonCodec.parseDocument(json) match {
      case Right(doc) => doc
      case Left(err)  => fail(s"expected successful parse, got: $err")
    }

  // Canonical form of a JSON text, for comparing documents structurally
  private def canonical(value: js.Any): String = js.JSON.stringify(value)

  test("patchSignal changes one signal and nothing else") {
    val doc = parseDocOk(richJson)
    assertEquals(doc.diagram.signals.map(_.name), Vector("clk", "", "addr", "rd", "last"))

    val addr = doc.diagram.signals(2)
    val patched = WaveJsonCodec.patchSignal(doc, 2, addr.copy(wave = "x=.x"))

    val expected = js.JSON.parse(richJson)
    expected.signal.asInstanceOf[js.Array[js.Dynamic]](2)
      .asInstanceOf[js.Array[js.Dynamic]](1)
      .wave = "x=.x"
    assertEquals(canonical(js.JSON.parse(patched)), canonical(expected))
  }

  test("format keeps each signal on one line and loses nothing") {
    val formatted = WaveJsonCodec.format(js.JSON.parse(richJson))
    assertEquals(
      canonical(js.JSON.parse(formatted)),
      canonical(js.JSON.parse(richJson))
    )
    val lines = formatted.linesIterator.map(_.trim).toVector
    assert(
      lines.contains(
        """{ "name": "addr", "wave": "x==x", "data": "A0 A1", "node": ".a.." },"""
      ),
      formatted
    )
    assert(lines.contains("{},"), formatted)
    assert(lines.contains(""""head": { "text": "Title" },"""), formatted)
    assert(lines.contains(""""a~>b go""""), formatted)
  }

  test("patchSignal removes node and data when they become empty") {
    val doc = parseDocOk(richJson)
    val addr = doc.diagram.signals(2)
    val patched = WaveJsonCodec.patchSignal(
      doc,
      2,
      addr.copy(node = "", data = Vector.empty)
    )
    val obj = js.JSON.parse(patched).signal.asInstanceOf[js.Array[js.Dynamic]](2)
      .asInstanceOf[js.Array[js.Dynamic]](1)
    assertEquals(js.typeOf(obj.node), "undefined")
    assertEquals(js.typeOf(obj.data), "undefined")
    assertEquals(obj.wave.toString, "x==x")
  }

  test("patchSignal writes changed data as an array") {
    val doc = parseDocOk(richJson)
    val addr = doc.diagram.signals(2)
    val patched =
      WaveJsonCodec.patchSignal(doc, 2, addr.copy(data = Vector("A0", "B1")))
    assertEquals(
      WaveJsonCodec.parse(patched).map(_.signals(2).data),
      Right(Vector("A0", "B1"))
    )
  }
}
