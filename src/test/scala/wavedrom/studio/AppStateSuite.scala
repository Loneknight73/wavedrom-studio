package wavedrom.studio

class AppStateSuite extends munit.FunSuite {

  private def stateWith(wave: String): AppState = {
    val state = new AppState()
    val loaded = state.updateFromText(
      s"""{ "signal": [ { "name": "s", "wave": "$wave" } ] }"""
    )
    assert(loaded.isRight, loaded)
    state
  }

  private def waveOf(state: AppState): String =
    state.diagram.get.signals.head.wave

  test("updateFromText keeps the previous diagram on a parse error") {
    val state = stateWith("01")
    assert(state.updateFromText("{").isLeft)
    assertEquals(waveOf(state), "01")
  }

  test("applyEdit updates the diagram and keeps jsonText in sync") {
    val state = stateWith("0....")
    val returned = state.applyEdit(0, WaveEditor.setCycle(_, 1, '1'))
    assertEquals(waveOf(state), "010..")
    assertEquals(returned, Some(state.jsonText))
    assertEquals(WaveJsonCodec.parse(state.jsonText), Right(state.diagram.get))
  }

  test("applyEdit ignores an edit that changes nothing") {
    val state = stateWith("01")
    val before = state.jsonText
    assertEquals(state.applyEdit(0, WaveEditor.setCycle(_, 0, '0')), None)
    assertEquals(state.jsonText, before)
    assert(!state.canUndo)
  }

  test("applyEdit does nothing without a diagram or for an unknown signal") {
    assertEquals(new AppState().applyEdit(0, identity), None)
    assertEquals(stateWith("01").applyEdit(5, WaveEditor.setCycle(_, 0, '1')), None)
  }

  test("colour edits are written into the JSON and can be undone") {
    val state = stateWith("x=.x")
    val before = state.jsonText

    state.applyEdit(0, WaveEditor.setBusColor(_, 2, '5'))
    assertEquals(waveOf(state), "x5.x")
    val recoloured = state.jsonText

    state.applyEdit(0, WaveEditor.setSignalColor(_, Some("#ff8800")))
    assertEquals(state.diagram.get.signals.head.color, Some("#ff8800"))
    assert(state.jsonText.contains(""""color": "#ff8800""""), state.jsonText)

    // Setting the colour it already has is not an edit
    assertEquals(state.applyEdit(0, WaveEditor.setSignalColor(_, Some("#FF8800"))), None)

    assertEquals(state.undo(), Some(recoloured))
    assertEquals(state.diagram.get.signals.head.color, None)
    assertEquals(state.undo(), Some(before))
    assertEquals(waveOf(state), "x=.x")
    assert(!state.canUndo)
  }

  test("replaceText is one undoable step, and a no-op when nothing changes") {
    val state = stateWith("01")
    val original = state.jsonText
    val reformatted = """{ "signal": [ { "name": "s", "wave": "01" } ] }""".replace(" ", "")
    assertEquals(state.replaceText(reformatted), Some(reformatted))
    assertEquals(state.jsonText, reformatted)
    assertEquals(state.replaceText(reformatted), None)
    assertEquals(state.undo(), Some(original))
    assertEquals(waveOf(state), "01")
  }

  test("undo restores the text from before each edit") {
    val state = stateWith("01")
    val original = state.jsonText
    state.applyEdit(0, WaveEditor.setCycle(_, 0, '1'))
    val afterFirst = state.jsonText
    state.applyEdit(0, WaveEditor.setCycle(_, 1, 'x'))
    assertEquals(waveOf(state), "1x")

    assertEquals(state.undo(), Some(afterFirst))
    assertEquals(waveOf(state), "11")
    assertEquals(state.undo(), Some(original))
    assertEquals(waveOf(state), "01")
    assertEquals(state.undo(), None)
  }

  test("a document with Windows line endings is not modified once shown") {
    val windows = "{\r\n  \"signal\": [\r\n    { \"name\": \"a\", \"wave\": \"01\" }\r\n  ]\r\n}"
    val state = AppState.open(windows)
    assert(!state.isDirty)
    assert(!state.jsonText.contains('\r'))

    // The text editor hands back the same text with line feeds only
    state.updateFromText(windows.replace("\r\n", "\n"))
    assert(!state.isDirty)

    state.markSaved(windows)
    assert(!state.isDirty)
  }

  test("no built-in sample looks modified when it is opened") {
    (Samples.blank +: Samples.all.map(_.json)).foreach { json =>
      val state = AppState.open(json)
      state.updateFromText(json.replace("\r\n", "\n"))
      assert(!state.isDirty, json.take(40))
    }
  }

  test("the nodes of the full demo sit on the edges of its handshake") {
    val signals = AppState.open(Samples.full.json).diagram.get.signals
    def edgeCycles(name: String): Set[Int] = {
      val wave = signals.find(_.name == name).get.wave
      wave.indices.filter(c => c > 0 && wave(c) != '.').toSet
    }
    def nodeCycles(name: String): Set[Int] =
      WaveJsonCodec.parseNodeTokens(signals.find(_.name == name).get.node).keySet
    assertEquals(nodeCycles("req"), edgeCycles("req"))
    assertEquals(nodeCycles("ack"), edgeCycles("ack"))
  }
}
