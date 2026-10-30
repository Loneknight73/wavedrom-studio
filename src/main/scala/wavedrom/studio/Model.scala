package wavedrom.studio

import scala.scalajs.js

sealed trait WaveState
object WaveState {
  case object High extends WaveState // 1
  case object Low extends WaveState // 0
  // h and l: levels like 1 and 0, but the edge into them is vertical, as in a clock
  case object SharpHigh extends WaveState // h
  case object SharpLow extends WaveState // l
  case object HighArrow extends WaveState // H: high, arrow on the rising edge
  case object LowArrow extends WaveState // L: low, arrow on the falling edge
  case object PosClock extends WaveState // p
  case object NegClock extends WaveState // n
  case object PosClockArrow extends WaveState // P: arrow on the rising edge
  case object NegClockArrow extends WaveState // N: arrow on the falling edge
  case object Undefined extends WaveState // x, X
  case object HighZ extends WaveState // z, Z
  case object DataBus extends WaveState // =
  // 2..9: a data bus in one of WaveDrom's eight colours (see BusPalette)
  final case class ColoredBus(digit: Int) extends WaveState
  case object Hold extends WaveState // .
  case object Gap extends WaveState // |: a hold with a break drawn over it
  case object PullUp extends WaveState // u: dotted high level
  case object PullDown extends WaveState // d: dotted low level

  // Clocks and levels are case sensitive: the upper-case forms carry an
  // arrow. `HLHL...` is a clock active on both edges, half a period per cycle.
  def fromChar(c: Char): WaveState = c match {
    case 'P' => PosClockArrow
    case 'N' => NegClockArrow
    case 'H' => HighArrow
    case 'L' => LowArrow
    case _   => fromLowerChar(c.toLower)
  }

  private def fromLowerChar(c: Char): WaveState = c match {
    case '1'            => High
    case 'h'            => SharpHigh
    case '0'            => Low
    case 'l'            => SharpLow
    case 'u'            => PullUp
    case 'd'            => PullDown
    case '|'            => Gap
    case 'p'            => PosClock
    case 'n'            => NegClock
    case 'x'            => Undefined
    case 'z'            => HighZ
    case '='            => DataBus
    case d if d >= '2' && d <= '9' => ColoredBus(d - '0')
    case '.'            => Hold
    case _              => Low
  }

  // The plain level an arrowed level is drawn as
  def level(state: WaveState): WaveState = state match {
    case HighArrow | SharpHigh => High
    case LowArrow | SharpLow   => Low
    case other                 => other
  }

  // The edge into these is vertical; into 1 and 0 it is slanted
  def isSharp(state: WaveState): Boolean = state match {
    case SharpHigh | SharpLow | HighArrow | LowArrow => true
    case _                                           => false
  }

  def isPull(state: WaveState): Boolean = state == PullUp || state == PullDown

  // A cycle that continues the previous one
  def isHold(state: WaveState): Boolean = state == Hold || state == Gap

  def isBus(state: WaveState): Boolean = state match {
    case DataBus | ColoredBus(_) => true
    case _                       => false
  }

  def toChar(state: WaveState): Char = state match {
    case High      => '1'
    case Low       => '0'
    case SharpHigh => 'h'
    case SharpLow  => 'l'
    case PullUp    => 'u'
    case PullDown  => 'd'
    case Gap       => '|'
    case PosClock  => 'p'
    case NegClock  => 'n'
    case PosClockArrow => 'P'
    case HighArrow => 'H'
    case LowArrow  => 'L'
    case NegClockArrow => 'N'
    case Undefined => 'x'
    case HighZ     => 'z'
    case DataBus   => '='
    case ColoredBus(digit) => ('0' + digit).toChar
    case Hold      => '.'
  }
}

case class SignalTrack(
    name: String,
    wave: String,
    node: String = "",
    data: Vector[String] = Vector.empty,
    depth: Int = 0,
    // An addition of Wavedrom Studio, not in WaveDrom: the stroke colour of this signal's waveform
    color: Option[String] = None
) {
  def parsedStates: Vector[WaveState] = wave.map(WaveState.fromChar).toVector

  // An empty `{}` entry: a blank row between signals
  def isSpacer: Boolean = name.isEmpty && wave.isEmpty
}

case class SignalGroup(
    name: String,
    depth: Int,
    startSigIdx: Int,
    endSigIdx: Int
)

case class EdgeConnection(
    fromNode: String,
    edgeType: String,
    toNode: String,
    label: Option[String]
)

case class WaveDiagram(
    signals: Vector[SignalTrack],
    groups: Vector[SignalGroup] = Vector.empty,
    edges: Vector[String] = Vector.empty,
    head: Option[js.Dynamic] = None,
    foot: Option[js.Dynamic] = None
)

// A parsed diagram together with the JS tree it came from. signalObjs(i) is
// the original object behind diagram.signals(i), so edits can be written back
// without disturbing the rest of the document.
case class WaveDocument(
    root: js.Dynamic,
    signalObjs: Vector[js.Dynamic],
    diagram: WaveDiagram
)

case class NodeLocation(
    x: Double,
    y: Double,
    sigIdx: Int,
    cycle: Int
)
