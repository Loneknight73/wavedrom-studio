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
      'P' -> WaveState.PosClockArrow,
      'n' -> WaveState.NegClock,
      'N' -> WaveState.NegClockArrow,
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

  test("fromChar treats digits 2-9 as data buses that keep their colour digit") {
    ('2' to '9').foreach { c =>
      assertEquals(WaveState.fromChar(c), WaveState.ColoredBus(c - '0'), s"token '$c'")
      assert(WaveState.isBus(WaveState.fromChar(c)), s"token '$c'")
    }
    assert(WaveState.isBus(WaveState.DataBus))
    assert(!WaveState.isBus(WaveState.High))
    assert(!WaveState.isBus(WaveState.Hold))
  }

  test("toChar round-trips the canonical tokens") {
    "10pnxz=.23456789".foreach { c =>
      assertEquals(WaveState.toChar(WaveState.fromChar(c)), c)
    }
  }

  test("arrow clocks keep their case") {
    "PN".foreach { c =>
      assertEquals(WaveState.toChar(WaveState.fromChar(c)), c)
    }
  }
}
