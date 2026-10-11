package wavedrom.studio

class WaveEditorSuite extends munit.FunSuite {

  private def track(
      wave: String,
      node: String = "",
      data: Vector[String] = Vector.empty
  ): SignalTrack = SignalTrack("s", wave, node, data)

  // --- Colours ------------------------------------------------------------

  test("setCycle between bus colours keeps the labels where they were") {
    val edited = WaveEditor.setCycle(track("x2=x", data = Vector("A", "B")), 1, '5')
    assertEquals(edited.wave, "x5=x")
    assertEquals(edited.data, Vector("A", "B"))
  }

  test("setCycle counts coloured buses when it inserts and removes labels") {
    val added = WaveEditor.setCycle(track("3x4", data = Vector("A", "C")), 1, '7')
    assertEquals(added.wave, "374")
    assertEquals(added.data, Vector("A", "", "C"))

    val removed = WaveEditor.setCycle(track("345", data = Vector("A", "B", "C")), 1, '0')
    assertEquals(removed.wave, "305")
    assertEquals(removed.data, Vector("A", "C"))
  }

  test("setCycle splitting a held coloured bus keeps its colour and label") {
    val edited = WaveEditor.setCycle(track("4...", data = Vector("A")), 1, '0')
    assertEquals(edited.wave, "404.")
    assertEquals(edited.data, Vector("A", "A"))
  }

  test("busToken is the token of the bus segment covering the cycle") {
    val t = track("0=.3.x")
    assertEquals(WaveEditor.busToken(t, 0), None)
    assertEquals(WaveEditor.busToken(t, 1), Some('='))
    assertEquals(WaveEditor.busToken(t, 2), Some('='))
    assertEquals(WaveEditor.busToken(t, 4), Some('3'))
    assertEquals(WaveEditor.busToken(t, 5), None)
    assertEquals(WaveEditor.busToken(t, 9), None)
  }

  test("setBusColor recolours the whole segment, from any of its cycles") {
    val t = track("x=..2x", data = Vector("A", "B"))
    assertEquals(WaveEditor.setBusColor(t, 1, '7').wave, "x7..2x")
    assertEquals(WaveEditor.setBusColor(t, 3, '7').wave, "x7..2x")
    assertEquals(WaveEditor.setBusColor(t, 4, '=').wave, "x=..=x")
    assertEquals(WaveEditor.setBusColor(t, 3, '7').data, Vector("A", "B"))
  }

  test("setBusColor turns a cycle that is not a bus into one") {
    val edited = WaveEditor.setBusColor(track("0..2", data = Vector("B")), 1, '4')
    assertEquals(edited.wave, "0402")
    assertEquals(edited.data, Vector("", "B"))
    assertEquals(WaveEditor.setBusColor(track("01"), 3, '9').wave, "01.9")
  }

  test("setBusColor ignores a token that is not a bus") {
    val t = track("x=x")
    assertEquals(WaveEditor.setBusColor(t, 1, '1'), t)
    assertEquals(WaveEditor.setBusColor(t, 1, 'q'), t)
  }

  test("setSignalColor sets, normalises and clears the colour") {
    val t = track("01")
    assertEquals(WaveEditor.setSignalColor(t, Some("#FF8800")).color, Some("#ff8800"))
    assertEquals(WaveEditor.setSignalColor(t, Some(" Red ")).color, Some("red"))
    val colored = t.copy(color = Some("#ff8800"))
    assertEquals(WaveEditor.setSignalColor(colored, None).color, None)
    assertEquals(WaveEditor.setSignalColor(colored, Some("\"/><script>")).color, None)
    assertEquals(WaveEditor.setSignalColor(colored, Some("#ff8800")), colored)
  }

  test("SignalColor.sanitize accepts hex colours and CSS names only") {
    assertEquals(SignalColor.sanitize("#abc"), Some("#abc"))
    assertEquals(SignalColor.sanitize("#ABCDEF80"), Some("#abcdef80"))
    assertEquals(SignalColor.sanitize("rebeccapurple"), Some("rebeccapurple"))
    assertEquals(SignalColor.sanitize("#abcde"), None)
    assertEquals(SignalColor.sanitize("#ggg"), None)
    assertEquals(SignalColor.sanitize("notacolour"), None)
    assertEquals(SignalColor.sanitize("red\" onload=\"x"), None)
    assertEquals(SignalColor.sanitize("url(#hatch)"), None)
    assertEquals(SignalColor.sanitize(""), None)
  }

  test("SignalColor.toPickerValue gives the #rrggbb form") {
    assertEquals(SignalColor.toPickerValue("#F80"), Some("#ff8800"))
    assertEquals(SignalColor.toPickerValue("#ff8800"), Some("#ff8800"))
    assertEquals(SignalColor.toPickerValue("#ff880080"), Some("#ff8800"))
    assertEquals(SignalColor.toPickerValue("red"), None)
  }

  // --- setCycle ------------------------------------------------------------

  test("setCycle changes the targeted cycle") {
    assertEquals(WaveEditor.setCycle(track("01"), 0, '1').wave, "11")
  }

  test("setCycle leaves the following held cycles as they were") {
    assertEquals(WaveEditor.setCycle(track("0...."), 1, '1').wave, "010..")
    assertEquals(WaveEditor.setCycle(track("p..."), 1, '0').wave, "p0p.")
  }

  test("setCycle to hold lets the previous state run on") {
    assertEquals(WaveEditor.setCycle(track("01.0"), 1, '.').wave, "0.10")
  }

  test("setCycle pads the wave when clicking past its end") {
    assertEquals(WaveEditor.setCycle(track("01"), 4, '1').wave, "01..1")
  }

  test("setCycle splitting a held bus keeps its token and its label") {
    val edited = WaveEditor.setCycle(track("3...", data = Vector("A")), 1, '0')
    assertEquals(edited.wave, "303.")
    assertEquals(edited.data, Vector("A", "A"))
  }

  test("setCycle adding a bus segment keeps later labels aligned") {
    val edited =
      WaveEditor.setCycle(track("x=x=", data = Vector("A", "B")), 2, '=')
    assertEquals(edited.wave, "x===")
    assertEquals(edited.data, Vector("A", "", "B"))
  }

  test("setCycle removing a bus segment drops its label") {
    val edited =
      WaveEditor.setCycle(track("x==x", data = Vector("A", "B")), 1, 'x')
    assertEquals(edited.wave, "xx=x")
    assertEquals(edited.data, Vector("B"))
  }

  // --- insert / delete -----------------------------------------------------

  test("insertHoldAfter shifts the wave and the node markers") {
    val edited = WaveEditor.insertHoldAfter(track("01.0", node = ".a.b"), 1)
    assertEquals(edited.wave, "01..0")
    assertEquals(edited.node, ".a..b")
  }

  test("deleteCycle shortens a held segment instead of removing its token") {
    val edited = WaveEditor.deleteCycle(track("01..0", node = "....b"), 1)
    assertEquals(edited.wave, "01.0")
    assertEquals(edited.node, "...b")
  }

  test("deleteCycle removes a single-cycle bus segment and its label") {
    val edited = WaveEditor.deleteCycle(track("x=x", data = Vector("A")), 1)
    assertEquals(edited.wave, "xx")
    assertEquals(edited.data, Vector.empty)
  }

  test("deleteCycle on a held bus keeps the label") {
    val edited =
      WaveEditor.deleteCycle(track("=.=", data = Vector("A", "B")), 0)
    assertEquals(edited.wave, "==")
    assertEquals(edited.data, Vector("A", "B"))
  }

  // --- bus labels ----------------------------------------------------------

  test("busLabel finds the label of the segment covering a cycle") {
    val bus = track("x=.x", data = Vector("A"))
    assertEquals(WaveEditor.busLabel(bus, 0), None)
    assertEquals(WaveEditor.busLabel(bus, 1), Some("A"))
    assertEquals(WaveEditor.busLabel(bus, 2), Some("A"))
    assertEquals(WaveEditor.busLabel(bus, 3), None)
    assertEquals(WaveEditor.busLabel(bus, 9), None)
  }

  test("setBusLabel fills in a missing label") {
    val edited = WaveEditor.setBusLabel(track("x==x"), 2, "Q")
    assertEquals(edited.data, Vector("", "Q"))
    assertEquals(WaveEditor.setBusLabel(edited, 2, "").data, Vector.empty)
  }

  // --- node markers --------------------------------------------------------

  test("setNode writes single-character markers in compact form") {
    val edited = WaveEditor.setNode(track("0....."), 2, "a")
    assertEquals(edited.node, "..a")
    assertEquals(WaveEditor.nodeAt(edited, 2), "a")
    assertEquals(WaveEditor.nodeAt(edited, 3), "")
  }

  test("setNode switches to spaced form for multi-character markers") {
    val single = WaveEditor.setNode(track("0....."), 2, "a")
    val edited = WaveEditor.setNode(single, 4, "req_start")
    assertEquals(edited.node, ".. a . req_start")
    assertEquals(
      WaveJsonCodec.parseNodeTokens(edited.node),
      Map(2 -> Vector("a"), 4 -> Vector("req_start"))
    )
  }

  test("setNode keeps existing markers when the node uses dots inside tokens") {
    val edited = WaveEditor.setNode(track("0" * 12, node = "..... skkdc ..d.."), 10, "e")
    assertEquals(
      WaveJsonCodec.parseNodeTokens(edited.node),
      Map(5 -> Vector("skkdc"), 8 -> Vector("d"), 10 -> Vector("e"))
    )
  }

  test("setNode keeps a lone multi-character marker readable") {
    val edited = WaveEditor.setNode(track("0..."), 0, "ack")
    assertEquals(
      WaveJsonCodec.parseNodeTokens(edited.node),
      Map(0 -> Vector("ack"))
    )
  }

  test("setNode replaces whitespace and clears on an empty name") {
    val named = WaveEditor.setNode(track("0..."), 1, " req start ")
    assertEquals(WaveEditor.nodeAt(named, 1), "req_start")
    assertEquals(WaveEditor.setNode(named, 1, "").node, "")
  }
}
