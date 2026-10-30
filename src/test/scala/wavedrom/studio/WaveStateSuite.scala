package wavedrom.studio

class WaveStateSuite extends munit.FunSuite {

  test("fromChar maps every wave token") {
    val expected = Map(
      '1' -> WaveState.High,
      'h' -> WaveState.SharpHigh,
      'H' -> WaveState.HighArrow,
      '0' -> WaveState.Low,
      'l' -> WaveState.SharpLow,
      'L' -> WaveState.LowArrow,
      'p' -> WaveState.PosClock,
      'P' -> WaveState.PosClockArrow,
      'n' -> WaveState.NegClock,
      'N' -> WaveState.NegClockArrow,
      'x' -> WaveState.Undefined,
      'X' -> WaveState.Undefined,
      'z' -> WaveState.HighZ,
      'Z' -> WaveState.HighZ,
      '=' -> WaveState.DataBus,
      '.' -> WaveState.Hold,
      '|' -> WaveState.Gap,
      'u' -> WaveState.PullUp,
      'd' -> WaveState.PullDown
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
    "10hlpnxz=.|ud23456789".foreach { c =>
      assertEquals(WaveState.toChar(WaveState.fromChar(c)), c)
    }
  }

  test("sharp levels are levels with a vertical edge, and gaps are holds") {
    assertEquals(WaveState.level(WaveState.SharpHigh), WaveState.High)
    assertEquals(WaveState.level(WaveState.SharpLow), WaveState.Low)
    assert(WaveState.isSharp(WaveState.SharpHigh))
    assert(WaveState.isSharp(WaveState.LowArrow))
    assert(!WaveState.isSharp(WaveState.High))
    assert(WaveState.isHold(WaveState.Hold))
    assert(WaveState.isHold(WaveState.Gap))
    assert(!WaveState.isHold(WaveState.PullUp))
  }

  test("arrow clocks keep their case") {
    "PNHL".foreach { c =>
      assertEquals(WaveState.toChar(WaveState.fromChar(c)), c)
    }
  }
}
