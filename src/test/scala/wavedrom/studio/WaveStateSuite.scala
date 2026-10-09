package wavedrom.studio

class WaveStateSuite extends munit.FunSuite {

  test("fromChar maps every wave token") {
    val expected = Map(
      '1' -> WaveState.High,
      'h' -> WaveState.High,
      'H' -> WaveState.High,
      '0' -> WaveState.Low,
      'l' -> WaveState.Low,
      'L' -> WaveState.Low,
      'p' -> WaveState.PosClock,
      'P' -> WaveState.PosClock,
      'n' -> WaveState.NegClock,
      'N' -> WaveState.NegClock,
      'x' -> WaveState.Undefined,
      'X' -> WaveState.Undefined,
      'z' -> WaveState.HighZ,
      'Z' -> WaveState.HighZ,
      '=' -> WaveState.DataBus,
      '.' -> WaveState.Hold
    )
    expected.foreach { case (c, state) =>
      assertEquals(WaveState.fromChar(c), state, s"token '$c'")
    }
  }

  test("fromChar treats digits 2-9 as data bus") {
    ('2' to '9').foreach { c =>
      assertEquals(WaveState.fromChar(c), WaveState.DataBus, s"token '$c'")
    }
  }

  test("toChar round-trips the canonical tokens") {
    "10pnxz=.".foreach { c =>
      assertEquals(WaveState.toChar(WaveState.fromChar(c)), c)
    }
  }
}
