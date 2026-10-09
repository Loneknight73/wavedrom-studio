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
}
