package wavedrom.studio

import scala.scalajs.js

sealed trait WaveState
object WaveState {
  case object High extends WaveState // 1, h, H
  case object Low extends WaveState // 0, l, L
  case object PosClock extends WaveState // p, P
  case object NegClock extends WaveState // n, N
  case object Undefined extends WaveState // x, X
  case object HighZ extends WaveState // z, Z
  case object DataBus extends WaveState // =, 2..9
  case object Hold extends WaveState // .

  def fromChar(c: Char): WaveState = c.toLower match {
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

case class NodeLocation(
    x: Double,
    y: Double,
    sigIdx: Int,
    cycle: Int
)
