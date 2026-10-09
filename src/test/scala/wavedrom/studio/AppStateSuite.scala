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

  test("updateCycleState changes the targeted cycle") {
    val state = stateWith("01")
    state.updateCycleState(0, 0, WaveState.High)
    assertEquals(waveOf(state), "11")
  }

  test("updateCycleState preserves the next cycle when it was a hold") {
    val state = stateWith("0....")
    state.updateCycleState(0, 1, WaveState.High)
    assertEquals(waveOf(state), "010..")
  }

  test("updateCycleState keeps jsonText in sync with the diagram") {
    val state = stateWith("0....")
    val returned = state.updateCycleState(0, 1, WaveState.High)
    assertEquals(returned, Some(state.jsonText))
    assertEquals(WaveJsonCodec.parse(state.jsonText), Right(state.diagram.get))
  }

  test("updateCycleState does nothing without a diagram") {
    val state = new AppState()
    assertEquals(state.updateCycleState(0, 0, WaveState.High), None)
  }
}
