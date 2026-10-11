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
  case object DataBus extends WaveState // =
  // 2..9: a data bus in one of WaveDrom's eight colours (see BusPalette)
  final case class ColoredBus(digit: Int) extends WaveState
  case object Hold extends WaveState // .

  def fromChar(c: Char): WaveState = c.toLower match {
    case '1' | 'h'      => High
    case '0' | 'l'      => Low
    case 'p'            => PosClock
    case 'n'            => NegClock
    case 'x'            => Undefined
    case 'z'            => HighZ
    case '='            => DataBus
    case d if d >= '2' && d <= '9' => ColoredBus(d - '0')
    case '.'            => Hold
    case _              => Low
  }

  def isBus(state: WaveState): Boolean = state match {
    case DataBus | ColoredBus(_) => true
    case _                       => false
  }

  def toChar(state: WaveState): Char = state match {
    case High      => '1'
    case Low       => '0'
    case PosClock  => 'p'
    case NegClock  => 'n'
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
    // Extension to WaveJSON: the stroke colour of this signal's waveform
    color: Option[String] = None
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
