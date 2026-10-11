package wavedrom.studio

import scala.scalajs.js

sealed trait WaveState
object WaveState {
  case object High extends WaveState // 1, h, H
  case object Low extends WaveState // 0, l, L
  case object PosClock extends WaveState // p
  case object NegClock extends WaveState // n
  case object PosClockArrow extends WaveState // P: arrow on the rising edge
  case object NegClockArrow extends WaveState // N: arrow on the falling edge
  case object Undefined extends WaveState // x, X
  case object HighZ extends WaveState // z, Z
  case object DataBus extends WaveState // =, 2..9
  case object Hold extends WaveState // .

  // Clocks are case sensitive: the upper-case forms carry an arrow
  def fromChar(c: Char): WaveState = c match {
    case 'P' => PosClockArrow
    case 'N' => NegClockArrow
    case _   => fromLowerChar(c.toLower)
  }

  private def fromLowerChar(c: Char): WaveState = c match {
    case '1' | 'h'      => High
    case '0' | 'l'      => Low
    case 'p'            => PosClock
    case 'n'            => NegClock
    case 'x'            => Undefined
    case 'z'            => HighZ
    case '='            => DataBus
    case d if d.isDigit => DataBus
    case '.'            => Hold
    case _              => Low
  }

  def toChar(state: WaveState): Char = state match {
    case High      => '1'
    case Low       => '0'
    case PosClock  => 'p'
    case NegClock  => 'n'
    case PosClockArrow => 'P'
    case NegClockArrow => 'N'
    case Undefined => 'x'
    case HighZ     => 'z'
    case DataBus   => '='
    case Hold      => '.'
  }
}

case class SignalTrack(
    name: String,
    wave: String,
    node: String = "",
    data: Vector[String] = Vector.empty,
    depth: Int = 0
) {
  def parsedStates: Vector[WaveState] = wave.map(WaveState.fromChar).toVector

  // An empty `{}` entry in WaveJSON: a blank row between signals
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
