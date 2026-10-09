package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.svg
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.util.control.NonFatal

// ============================================================================
// 1. AST & DOMAIN MODEL (Scala 3 Strongly-Typed Engine)
// ============================================================================

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

// ============================================================================
// 2. PARSER & SERIALIZER (Scala.js JSON Codec)
// ============================================================================

object WaveJsonCodec {

  def parse(jsonStr: String): Either[String, WaveDiagram] = {
    try {
      val parsed = js.JSON.parse(jsonStr)
      val signalArr = parsed.signal.asInstanceOf[js.Array[js.Any]]

      var flatSignals = Vector.empty[SignalTrack]
      var groups = Vector.empty[SignalGroup]

      def processSignalList(list: js.Array[js.Any], depth: Int): Unit = {
        list.foreach { item =>
          if (js.Array.isArray(item)) {
            val arr = item.asInstanceOf[js.Array[js.Any]]
            val isFirstString = arr.length > 0 && js.typeOf(arr(0)) == "string"
            val groupName =
              if (isFirstString) arr(0).asInstanceOf[String] else "Group"
            val startIdx = flatSignals.length

            val children = if (isFirstString) arr.slice(1, arr.length) else arr
            processSignalList(children, depth + 1)

            val endIdx = Math.max(startIdx, flatSignals.length - 1)
            groups = groups :+ SignalGroup(groupName, depth, startIdx, endIdx)
          } else if (js.typeOf(item) == "object" && item != null) {
            val obj = item.asInstanceOf[js.Dynamic]
            val name =
              if (js.typeOf(obj.name) != "undefined") obj.name.toString
              else s"sig_${flatSignals.length}"
            val wave =
              if (js.typeOf(obj.wave) != "undefined") obj.wave.toString else ""
            val node =
              if (js.typeOf(obj.node) != "undefined") obj.node.toString else ""

            var dataVec = Vector.empty[String]
            if (js.typeOf(obj.data) != "undefined") {
              if (js.Array.isArray(obj.data)) {
                dataVec = obj.data
                  .asInstanceOf[js.Array[js.Any]]
                  .map(_.toString)
                  .toVector
              } else if (js.typeOf(obj.data) == "string") {
                dataVec = obj.data.toString.split("\\s+").toVector
              }
            }

            flatSignals =
              flatSignals :+ SignalTrack(name, wave, node, dataVec, depth)
          }
        }
      }

      processSignalList(signalArr, 0)

      var edges = Vector.empty[String]
      if (
        js.typeOf(parsed.edge) != "undefined" && js.Array.isArray(parsed.edge)
      ) {
        edges =
          parsed.edge.asInstanceOf[js.Array[js.Any]].map(_.toString).toVector
      }

      Right(WaveDiagram(flatSignals, groups, edges))
    } catch {
      case NonFatal(ex) => Left(s"JSON Syntax Error: ${ex.getMessage}")
    }

  }

  def parseNodeTokens(nodeStr: String): Map[Int, Vector[String]] = {
    if (nodeStr.trim.isEmpty) return Map.empty

    val tokens =
      if (nodeStr.contains(" ")) nodeStr.trim.split("\\s+").toVector
      else nodeStr.map(_.toString).toVector
    var resultMap = Map.empty[Int, Vector[String]]
    var cycleIdx = 0

    tokens.foreach { tok =>
      if (tok.nonEmpty) {
        if (tok.forall(_ == '.')) {
          cycleIdx += tok.length
        } else if (tok == ".") {
          cycleIdx += 1
        } else {
          val existing = resultMap.getOrElse(cycleIdx, Vector.empty)
          resultMap = resultMap.updated(cycleIdx, existing :+ tok)
          cycleIdx += 1
        }
      }
    }
    resultMap

  }

  def serialize(diagram: WaveDiagram): String = {
    val rootObj = js.Dynamic.literal()

// Reconstruct signal hierarchy
    val signalList = js.Array[js.Any]()

    if (diagram.groups.isEmpty) {
      diagram.signals.foreach { sig =>
        signalList.push(signalToJs(sig))
      }
    } else {
      val processedSigIndices = collection.mutable.Set[Int]()

      diagram.groups.filter(_.depth == 0).foreach { g =>
        val groupArr = js.Array[js.Any](g.name)
        for (i <- g.startSigIdx to g.endSigIdx if i < diagram.signals.length) {
          groupArr.push(signalToJs(diagram.signals(i)))
          processedSigIndices.add(i)
        }
        signalList.push(groupArr)
      }

      diagram.signals.zipWithIndex.foreach { case (sig, idx) =>
        if (!processedSigIndices.contains(idx)) {
          signalList.push(signalToJs(sig))
        }
      }
    }

    rootObj.signal = signalList

    if (diagram.edges.nonEmpty) {
      val edgeArr = js.Array[js.Any]()
      diagram.edges.foreach(e => edgeArr.push(e))
      rootObj.edge = edgeArr
    }

    js.JSON.stringify(rootObj, null, 2)

  }

  private def signalToJs(sig: SignalTrack): js.Dynamic = {
    val obj = js.Dynamic.literal(name = sig.name, wave = sig.wave)
    if (sig.node.nonEmpty) obj.node = sig.node
    if (sig.data.nonEmpty) {
      val dataArr = js.Array[js.Any]()
      sig.data.foreach(d => dataArr.push(d))
      obj.data = dataArr
    }
    obj
  }
}

// ============================================================================
// 3. REACTIVE APPLICATION STATE & CONTROLLER
// ============================================================================

class AppState {
  var jsonText: String = ""
  var diagram: Option[WaveDiagram] = None
  var stepWidth: Double = 45.0
  val rowHeight: Double = 52.0
  var selectedNodeForEdge: Option[String] = None

  var activeMenuSigIdx: Int = -1
  var activeMenuCycleIdx: Int = -1

  def updateFromText(newText: String): Either[String, WaveDiagram] = {
    jsonText = newText
    WaveJsonCodec.parse(newText) match {
      case Right(parsed) =>
        diagram = Some(parsed)
        Right(parsed)
      case Left(err) =>
        Left(err)
    }
  }

  def updateCycleState(
      sigIdx: Int,
      cycleIdx: Int,
      newState: WaveState
  ): Option[String] = {
    diagram.map { current =>
      val sig = current.signals(sigIdx)
      val waveChars = sig.wave.toCharArray

      val resolvedBefore = collection.mutable.ArrayBuffer[WaveState]()
      var cur: WaveState = WaveState.Low
      waveChars.foreach { c =>
        val st = WaveState.fromChar(c)
        if (st != WaveState.Hold) cur = st
        resolvedBefore.append(cur)
      }

      if (cycleIdx + 1 < waveChars.length && waveChars(cycleIdx + 1) == '.') {
        val prevResolvedNext = resolvedBefore(cycleIdx)
        if (newState != prevResolvedNext) {
          waveChars(cycleIdx + 1) = WaveState.toChar(prevResolvedNext)
        }
      }

      waveChars(cycleIdx) = WaveState.toChar(newState)
      val updatedSig = sig.copy(wave = new String(waveChars))
      val updatedSignals = current.signals.updated(sigIdx, updatedSig)
      val updatedDiagram = current.copy(signals = updatedSignals)

      diagram = Some(updatedDiagram)
      jsonText = WaveJsonCodec.serialize(updatedDiagram)
      jsonText
    }

  }
}

// ============================================================================
// 4. SVG WAVEFORM RENDER ENGINE
// ============================================================================

object SvgRenderer {

  def render(state: AppState, container: html.Div): Unit = {
    state.diagram match {
      case None =>
        container.innerHTML = """No diagram loaded"""
      case Some(diag) =>
        if (diag.signals.isEmpty) {
          container.innerHTML = """No signals defined"""
          return
        }

        var maxCycles = diag.signals.map(_.wave.length).maxOption.getOrElse(8)
        maxCycles = Math.max(maxCycles, 8)

        val maxGroupDepth =
          (diag.groups.map(_.depth) ++ diag.signals.map(_.depth)).maxOption
            .getOrElse(0)
        val groupIndent = maxGroupDepth * 22
        val labelWidth = 120 + groupIndent
        val step = state.stepWidth
        val rowH = state.rowHeight
        val totalWidth = labelWidth + (maxCycles * step) + 60
        val totalHeight = (diag.signals.length * rowH) + 60

        val sb = new StringBuilder()
        sb.append(
          s"""<svg width="$totalWidth" height="$totalHeight" viewBox="0 0 $totalWidth $totalHeight" xmlns="http://www.w3.org/2000/svg" class="font-mono text-xs select-none">"""
        )

        // Defs Section
        sb.append("""
      <defs>
        <pattern id="hatch" width="8" height="8" patternTransform="rotate(45 0 0)" patternUnits="userSpaceOnUse">
          <line x1="0" y1="0" x2="0" y2="8" stroke="#ef4444" stroke-width="2" opacity="0.4" />
        </pattern>
        <marker id="arrowEnd" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
          <path d="M 0 1 L 10 5 L 0 9 z" fill="#38bdf8" />
        </marker>
        <marker id="arrowStart" viewBox="0 0 10 10" refX="2" refY="5" markerWidth="6" markerHeight="6" orient="auto">
          <path d="M 10 1 L 0 5 L 10 9 z" fill="#38bdf8" />
        </marker>
      </defs>
    """)

        // 1. Time Ruler
        sb.append("<g id=\"timeRuler\">")
        for (c <- 0 until maxCycles) {
          val cx = labelWidth + (c * step)
          sb.append(
            s"""<line x1="$cx" y1="25" x2="$cx" y2="${totalHeight - 20}" stroke="#334155" stroke-dasharray="2 3" opacity="0.5" />"""
          )
          sb.append(
            s"""<text x="${cx + (step / 2)}" y="18" fill="#64748b" font-size="10" text-anchor="middle" font-weight="600">$c</text>"""
          )
        }
        val endX = labelWidth + (maxCycles * step)
        sb.append(
          s"""<line x1="$endX" y1="25" x2="$endX" y2="${totalHeight - 20}" stroke="#334155" stroke-dasharray="2 3" opacity="0.5" />"""
        )
        sb.append("</g>")

        // 2. Curly Brackets for Groups
        if (diag.groups.nonEmpty) {
          sb.append("<g id=\"groupBrackets\">")
          diag.groups.foreach { g =>
            val topY = 35 + (g.startSigIdx * rowH) + 6
            val bottomY = 35 + ((g.endSigIdx + 1) * rowH) - 12
            val bx = 16 + (g.depth * 20)
            val midY = (topY + bottomY) / 2.0
            val r = Math.min(8.0, (bottomY - topY) / 4.0)

            val bracePath =
              s"M ${bx + r} $topY Q $bx $topY $bx ${topY + r} L $bx ${midY - r} Q $bx $midY ${bx - r} $midY Q $bx $midY $bx ${midY + r} L $bx ${bottomY - r} Q $bx $bottomY ${bx + r} $bottomY"
            sb.append(
              s"""<path d="$bracePath" stroke="#38bdf8" stroke-width="2" fill="none" opacity="0.9" />"""
            )
            sb.append(
              s"""<text x="${bx - 8}" y="$midY" fill="#38bdf8" font-size="11" font-weight="700" text-anchor="middle" transform="rotate(-90 ${bx - 8} $midY)">${escapeHtml(
                  g.name
                )}</text>"""
            )
          }
          sb.append("</g>")
        }

        // Map for Node Coordinates
        var nodeLocations = Map.empty[String, NodeLocation]

        // 3. Render Signal Tracks
        diag.signals.zipWithIndex.foreach { case (sig, sigIdx) =>
          val rowY = 35 + (sigIdx * rowH)
          val yHigh = rowY + 12
          val yLow = rowY + 36
          val yMid = rowY + 24
          val nameX =
            15 + (sig.depth * 20) + (if (diag.groups.nonEmpty) 12 else 0)

          sb.append(s"""<g class="signal-row" data-sig-idx="$sigIdx">""")
          sb.append(
            s"""<text x="$nameX" y="${rowY + 28}" fill="#94a3b8" font-size="12" font-weight="600" font-family="monospace">${escapeHtml(
                sig.name
              )}</text>"""
          )

          // Node Markers
          val parsedNodes = WaveJsonCodec.parseNodeTokens(sig.node)
          parsedNodes.foreach { case (cycleIdx, nodeNames) =>
            nodeNames.foreach { nodeName =>
              val nx = labelWidth + (cycleIdx + 0.5) * step
              val ny = yMid
              nodeLocations = nodeLocations.updated(
                nodeName,
                NodeLocation(nx, ny, sigIdx, cycleIdx)
              )

              val isSelected = state.selectedNodeForEdge.contains(nodeName)
              val badgeW = Math.max(18.0, nodeName.length * 6.5 + 8)

              sb.append(s"""
            <g class="cursor-pointer" onclick="window.WaveDromStudioApp.handleNodeClick('${escapeHtml(
                  nodeName
                )}')">
              <rect x="${nx - badgeW / 2}" y="${ny - 7}" width="$badgeW" height="14" rx="7" fill="${
                  if (isSelected) "#f59e0b" else "#0284c7"
                }" stroke="${
                  if (isSelected) "#fbbf24" else "#38bdf8"
                }" stroke-width="1.5" />
              <text x="$nx" y="${ny + 3}" fill="#ffffff" font-size="9" font-weight="700" text-anchor="middle">${escapeHtml(
                  nodeName
                )}</text>
            </g>
          """)
            }
          }

          // Resolved States Calculation
          val states = sig.parsedStates
          val resolvedStates = collection.mutable.ArrayBuffer[WaveState]()
          var curState: WaveState = WaveState.Low

          states.foreach { st =>
            if (st != WaveState.Hold) curState = st
            resolvedStates.append(curState)
          }

          var busDataCounter = 0

          for (c <- states.indices) {
            val cx = labelWidth + (c * step)
            val token = states(c)
            val normToken = resolvedStates(c)

            // Cycle Click Target
            sb.append(s"""
          <rect x="$cx" y="$rowY" width="$step" height="${rowH - 6}" fill="transparent"
                class="cursor-pointer hover:fill-sky-500/10 transition-colors"
                onclick="window.WaveDromStudioApp.openContextMenu(event, $sigIdx, $c)">
          </rect>
        """)

            // Vertical Transitions
            if (c > 0) {
              val prevNorm = resolvedStates(c - 1)
              if (prevNorm != normToken) {
                val yFrom = getYLevel(prevNorm, yHigh, yLow, yMid)
                val yTo = getYLevel(normToken, yHigh, yLow, yMid)

                if (
                  yFrom != yTo && prevNorm != WaveState.Undefined && normToken != WaveState.Undefined
                ) {
                  val color =
                    if (
                      prevNorm == WaveState.DataBus || normToken == WaveState.DataBus
                    ) "#38bdf8"
                    else if (
                      prevNorm == WaveState.HighZ || normToken == WaveState.HighZ
                    ) "#f59e0b"
                    else "#10b981"
                  sb.append(
                    s"""<line x1="$cx" y1="$yFrom" x2="$cx" y2="$yTo" stroke="$color" stroke-width="2" />"""
                  )
                }
              }
            }

            // Waveform Shapes
            normToken match {
              case WaveState.High =>
                sb.append(
                  s"""<path d="M $cx $yHigh L ${cx + step} $yHigh" stroke="#10b981" stroke-width="2.5" fill="none" />"""
                )
              case WaveState.Low =>
                sb.append(
                  s"""<path d="M $cx $yLow L ${cx + step} $yLow" stroke="#10b981" stroke-width="2.5" fill="none" />"""
                )
              case WaveState.PosClock =>
                val half = step / 2.0
                sb.append(
                  s"""<path d="M $cx $yLow L $cx $yHigh L ${cx + half} $yHigh L ${cx + half} $yLow L ${cx + step} $yLow" stroke="#818cf8" stroke-width="2" fill="none" />"""
                )
              case WaveState.NegClock =>
                val half = step / 2.0
                sb.append(
                  s"""<path d="M $cx $yHigh L $cx $yLow L ${cx + half} $yLow L ${cx + half} $yHigh L ${cx + step} $yHigh" stroke="#818cf8" stroke-width="2" fill="none" />"""
                )
              case WaveState.Undefined =>
                sb.append(
                  s"""<rect x="$cx" y="$yHigh" width="$step" height="${yLow - yHigh}" fill="url(#hatch)" stroke="#ef4444" stroke-width="1.5" />"""
                )
              case WaveState.HighZ =>
                sb.append(
                  s"""<line x1="$cx" y1="$yMid" x2="${cx + step}" y2="$yMid" stroke="#f59e0b" stroke-width="2" stroke-dasharray="4 2" />"""
                )
              case WaveState.DataBus =>
                val slope = Math.min(6.0, step / 4.0)
                val isCont =
                  (c > 0 && token == WaveState.Hold && resolvedStates(
                    c - 1
                  ) == WaveState.DataBus)
                val isNextCont = (c + 1 < states.length && states(
                  c + 1
                ) == WaveState.Hold && resolvedStates(
                  c + 1
                ) == WaveState.DataBus)
                val xRightPinch =
                  if (isNextCont) cx + step else cx + step - slope

                var pathD =
                  if (!isCont)
                    s"M $cx $yMid L ${cx + slope} $yHigh L $xRightPinch $yHigh "
                  else s"M $cx $yHigh L $xRightPinch $yHigh "
                pathD += (if (!isNextCont)
                            s"L ${cx + step} $yMid L $xRightPinch $yLow "
                          else s"L ${cx + step} $yLow ")
                pathD += (if (!isCont) s"L ${cx + slope} $yLow Z"
                          else s"L $cx $yLow Z")

                sb.append(
                  s"""<path d="$pathD" fill="#0284c7" fill-opacity="0.2" stroke="#38bdf8" stroke-width="2" />"""
                )

                if (token != WaveState.Hold || c == 0) {
                  val labelText =
                    if (busDataCounter < sig.data.length)
                      sig.data(busDataCounter)
                    else ""
                  if (labelText.nonEmpty) {
                    sb.append(
                      s"""<text x="${cx + (step / 2)}" y="${yMid + 4}" fill="#e0f2fe" font-size="10" font-weight="600" text-anchor="middle">${escapeHtml(
                          labelText
                        )}</text>"""
                    )
                  }
                  busDataCounter += 1
                }
              case WaveState.Hold => // Handled via resolved state
            }
          }

          sb.append("</g>")
        }

        // 4. Render Edges / Connection Arrows
        if (diag.edges.nonEmpty) {
          sb.append("<g id=\"edgesLayer\">")
          val edgePattern =
            """^([a-zA-Z0-9_]+)\s*(~>|->|<-|<->|<~>|-\|>|\|->)\s*([a-zA-Z0-9_]+)(?:\s+(.*))?$""".r

          diag.edges.foreach { edgeStr =>
            edgeStr.trim match {
              case edgePattern(fromNode, edgeType, toNode, labelText) =>
                for {
                  p1 <- nodeLocations.get(fromNode)
                  p2 <- nodeLocations.get(toNode)
                } {
                  val x1 = p1.x; val y1 = p1.y
                  val x2 = p2.x; val y2 = p2.y

                  var pathD = ""
                  var midX = (x1 + x2) / 2.0
                  var midY = (y1 + y2) / 2.0

                  val markerEnd =
                    if (edgeType.contains(">")) "marker-end=\"url(#arrowEnd)\""
                    else ""
                  val markerStart =
                    if (edgeType.contains("<"))
                      "marker-start=\"url(#arrowStart)\""
                    else ""

                  if (
                    edgeType == "->" || edgeType == "<-" || edgeType == "<->"
                  ) {
                    pathD = s"M $x1 $y1 L $x2 $y2"
                  } else if (edgeType == "~>" || edgeType == "<~>") {
                    val dx = x2 - x1
                    val dy = y2 - y1
                    val curveOff =
                      Math.min(50.0, Math.max(25.0, Math.abs(dx) * 0.3))
                    val c1x = x1 + dx * 0.25; val c1y = y1 - curveOff
                    val c2x = x2 - dx * 0.25; val c2y = y2 - curveOff

                    pathD = s"M $x1 $y1 C $c1x $c1y, $c2x $c2y, $x2 $y2"
                    midX = 0.125 * x1 + 0.375 * c1x + 0.375 * c2x + 0.125 * x2
                    midY = 0.125 * y1 + 0.375 * c1y + 0.375 * c2y + 0.125 * y2
                  }

                  sb.append(
                    s"""<path d="$pathD" fill="none" stroke="#38bdf8" stroke-width="1.8" $markerStart$markerEnd />"""
                  )

                  if (labelText != null && labelText.trim.nonEmpty) {
                    sb.append(
                      s"""<text x="$midX" y="${midY + 3.5}" fill="#38bdf8" stroke="#020617" stroke-width="4.5" paint-order="stroke fill" stroke-linejoin="round" font-size="11" font-weight="700" text-anchor="middle">${escapeHtml(
                          labelText.trim
                        )}</text>"""
                    )
                  }
                }
              case _ =>
            }
          }
          sb.append("</g>")
        }

        sb.append("</svg>")
        container.innerHTML = sb.toString()
    }

  }

  private def getYLevel(
      state: WaveState,
      yHigh: Double,
      yLow: Double,
      yMid: Double
  ): Double = state match {
    case WaveState.High | WaveState.NegClock => yHigh
    case WaveState.Low | WaveState.PosClock  => yLow
    case _                                   => yMid
  }

  private def escapeHtml(str: String): String = {
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}

// ============================================================================
// 5. MAIN SCALA.JS TOP-LEVEL ENTRY POINT
// ============================================================================

@JSExportTopLevel("WaveDromStudioApp")
object WaveDromStudioApp {

  private val state = new AppState()

  def main(args: Array[String]): Unit = main()

  @JSExport
  def main(): Unit = {
    def init(): Unit = {
      val jsonEditor =
        dom.document.getElementById("jsonEditor").asInstanceOf[html.TextArea]
      val svgContainer =
        dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
      val statusIndicator = dom.document
        .getElementById("statusIndicator")
        .asInstanceOf[html.Element]

      def updateView(): Unit = {
        if (jsonEditor != null && svgContainer != null) {
          state.updateFromText(jsonEditor.value) match {
            case Right(_) =>
              if (statusIndicator != null) {
                statusIndicator.textContent = "Syntax OK"
                statusIndicator.className = "status-badge status-ok"
              }
              SvgRenderer.render(state, svgContainer)
            case Left(err) =>
              if (statusIndicator != null) {
                statusIndicator.textContent = err
                statusIndicator.className = "status-badge status-error"
              }
          }
        }
      }

      if (jsonEditor != null && svgContainer != null) {
        updateView()
        jsonEditor.addEventListener("input", (_: dom.Event) => updateView())

        dom.document.addEventListener(
          "click",
          (e: dom.MouseEvent) => {
            val menu = dom.document
              .getElementById("cycleMenu")
              .asInstanceOf[html.Element]
            if (menu != null && menu.style.display != "none") {
              val target = e.target.asInstanceOf[dom.Node]
              if (!menu.contains(target)) {
                menu.style.display = "none"
              }
            }
          }
        )
      }
    }

    if (dom.document.readyState != "loading") {
      init()
    } else {
      dom.window.addEventListener("DOMContentLoaded", (_: dom.Event) => init())
    }
  }

  @JSExport
  def handleNodeClick(nodeName: String): Unit = {
    if (state.selectedNodeForEdge.contains(nodeName)) {
      state.selectedNodeForEdge = None
    } else {
      state.selectedNodeForEdge = Some(nodeName)
    }
    val svgContainer =
      dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
    if (svgContainer != null) SvgRenderer.render(state, svgContainer)
  }

  @JSExport
  def openContextMenu(
      event: dom.MouseEvent,
      sigIdx: Int,
      cycleIdx: Int
  ): Unit = {
    event.preventDefault()
    event.stopPropagation()
    state.activeMenuSigIdx = sigIdx
    state.activeMenuCycleIdx = cycleIdx
    val menu =
      dom.document.getElementById("cycleMenu").asInstanceOf[html.Element]
    if (menu != null) {
      menu.style.display = "block"
      menu.style.left = s"${event.pageX}px"
      menu.style.top = s"${event.pageY}px"
    }
  }

  @JSExport
  def setCycleState(stateChar: String): Unit = {
    if (
      state.activeMenuSigIdx >= 0 && state.activeMenuCycleIdx >= 0 && stateChar.nonEmpty
    ) {
      val st = WaveState.fromChar(stateChar.charAt(0))
      state
        .updateCycleState(state.activeMenuSigIdx, state.activeMenuCycleIdx, st)
        .foreach { newJson =>
          val jsonEditor = dom.document
            .getElementById("jsonEditor")
            .asInstanceOf[html.TextArea]
          val svgContainer =
            dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
          if (jsonEditor != null) jsonEditor.value = newJson
          if (svgContainer != null) SvgRenderer.render(state, svgContainer)
        }
    }
    val menu =
      dom.document.getElementById("cycleMenu").asInstanceOf[html.Element]
    if (menu != null) menu.style.display = "none"
  }

  @JSExport
  def setStepWidth(width: Double): Unit = {
    state.stepWidth = Math.max(15.0, Math.min(250.0, width))
    val svgContainer =
      dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
    if (svgContainer != null) SvgRenderer.render(state, svgContainer)
  }

  @JSExport
  def resetSelection(): Unit = {
    state.selectedNodeForEdge = None
    val svgContainer =
      dom.document.getElementById("svgContainer").asInstanceOf[html.Div]
    if (svgContainer != null) SvgRenderer.render(state, svgContainer)
  }
}
