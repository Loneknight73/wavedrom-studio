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

  test("parseNodeTokens returns nothing for an empty string") {
    assertEquals(WaveJsonCodec.parseNodeTokens(""), Map.empty)
  }

  test("serialize round-trips an ungrouped diagram") {
    val diagram = WaveDiagram(
      signals = Vector(
        SignalTrack("clk", "p..."),
        SignalTrack("req", "01.0", node = ".a.b"),
        SignalTrack("bus", "x==x", data = Vector("D0", "D1"))
      ),
      edges = Vector("a~>b done")
    )
    assertEquals(
      WaveJsonCodec.parse(WaveJsonCodec.serialize(diagram)),
      Right(diagram)
    )
  }

  test("serialize omits empty node, data and edge") {
    val json = WaveJsonCodec.serialize(
      WaveDiagram(Vector(SignalTrack("clk", "p...")))
    )
    val root = js.JSON.parse(json)
    val sig = root.signal.asInstanceOf[js.Array[js.Dynamic]](0)
    assertEquals(js.typeOf(sig.node), "undefined")
    assertEquals(js.typeOf(sig.data), "undefined")
    assertEquals(js.typeOf(root.edge), "undefined")
  }
}
