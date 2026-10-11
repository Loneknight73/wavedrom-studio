package wavedrom.studio

import org.scalajs.dom.html

object SvgRenderer {

  // The colours of the classic WaveDrom skin. They are written into the SVG
  // itself (never left to page CSS) so an exported drawing looks the same.
  private object Palette {
    val ink = "#000" // signal lines, names, bus outlines and labels
    val paper = "#fff" // bus fill and the backing of labels drawn over waves
    val grid = "#888" // dotted cycle boundaries
    val tick = "#666" // cycle numbers
    val edge = "#0041c4" // arrows and splines between nodes
    val selectedFill = "#fff3bf" // node picked for an edge
  }

  private val fontFamily = "Helvetica, Arial, sans-serif"

  // Space between a signal's name and its wave, and between nested braces
  private val nameGap = 8
  private val braceGap = 26

  def render(state: AppState, container: html.Div): Unit =
    container.innerHTML = renderToString(state)

  def renderToString(state: AppState): String = {
    state.diagram match {
      case None =>
        """No diagram loaded"""
      case Some(diag) =>
        if (diag.signals.isEmpty) {
          return """No signals defined"""
        }

        var maxCycles = diag.signals.map(_.wave.length).maxOption.getOrElse(8)
        maxCycles = Math.max(maxCycles, 8)

        val maxGroupDepth =
          (diag.groups.map(_.depth) ++ diag.signals.map(_.depth)).maxOption
            .getOrElse(0)
        val groupIndent = maxGroupDepth * 22
        // The name column is as wide as the longest name (estimated, since
        // text cannot be measured here), with the group braces just left of it
        val nameWidth = diag.signals.map(_.name.length).maxOption.getOrElse(0) * 6.5
        val braceLevels = diag.groups.map(_.depth).maxOption.map(_ + 1).getOrElse(0)
        val braceSpace = if (braceLevels > 0) 20 + braceLevels * braceGap else 0
        val labelWidth =
          Math.max(120.0 + groupIndent, Math.ceil(nameWidth + nameGap + braceSpace + 8))
        val step = state.stepWidth
        val rowH = state.rowHeight
        val totalWidth = labelWidth + (maxCycles * step) + 60
        val totalHeight = (diag.signals.length * rowH) + 60
        // Width of the slanted edge between two levels
        val slant = Math.min(4.0, step / 4.0)

        val sb = new StringBuilder()
        sb.append(
          // The drawing is laid out at its natural size; zooming only changes
          // the size it is shown at, so everything scales together
          s"""<svg width="${totalWidth * state.zoom}" height="${totalHeight * state.zoom}" viewBox="0 0 $totalWidth $totalHeight" xmlns="http://www.w3.org/2000/svg" class="wave-svg" font-family="$fontFamily" style="user-select:none">"""
        )

        // Defs Section. The arrowheads stop short of the node they point at,
        // clear of the patch its name is drawn on
        sb.append(s"""
      <defs>
        <pattern id="hatch" width="4" height="4" patternTransform="rotate(45 0 0)" patternUnits="userSpaceOnUse">
          <line x1="2" y1="0" x2="2" y2="4" stroke="${Palette.ink}" stroke-width="0.6" />
        </pattern>
        <marker id="arrowEnd" viewBox="0 0 10 10" refX="16" refY="5" markerWidth="9" markerHeight="9" orient="auto">
          <path d="M 0 1.5 L 10 5 L 0 8.5 z" fill="${Palette.edge}" />
        </marker>
        <marker id="arrowStart" viewBox="0 0 10 10" refX="-6" refY="5" markerWidth="9" markerHeight="9" orient="auto">
          <path d="M 10 1.5 L 0 5 L 10 8.5 z" fill="${Palette.edge}" />
        </marker>
      </defs>
    """)

        // Half a pixel off the grid, so 1px lines land on whole pixels
        sb.append(
          """<g transform="translate(0.5 0.5)" stroke-linecap="round" stroke-linejoin="round">"""
        )

        // 1. Time Ruler
        sb.append("<g id=\"timeRuler\">")
        for (c <- 0 until maxCycles) {
          val cx = labelWidth + (c * step)
          sb.append(
            s"""<line x1="$cx" y1="25" x2="$cx" y2="${totalHeight - 20}" stroke="${Palette.grid}" stroke-width="0.5" stroke-dasharray="1 3" />"""
          )
          sb.append(
            s"""<text x="${cx + (step / 2)}" y="18" fill="${Palette.tick}" font-size="9" text-anchor="middle">$c</text>"""
          )
        }
        val endX = labelWidth + (maxCycles * step)
        sb.append(
          s"""<line x1="$endX" y1="25" x2="$endX" y2="${totalHeight - 20}" stroke="${Palette.grid}" stroke-width="0.5" stroke-dasharray="1 3" />"""
        )
        sb.append("</g>")

        // 2. Curly Brackets for Groups
        if (diag.groups.nonEmpty) {
          sb.append("<g id=\"groupBrackets\">")
          diag.groups.foreach { g =>
            val topY = 35 + (g.startSigIdx * rowH) + 8
            val bottomY = 35 + (g.endSigIdx * rowH) + 40
            // The innermost brace hugs the names; outer ones step leftwards
            val bx = labelWidth - nameGap - nameWidth - 12 -
              (braceLevels - 1 - g.depth) * braceGap
            val midY = (topY + bottomY) / 2.0
            val r = Math.min(8.0, (bottomY - topY) / 4.0)

            val bracePath =
              s"M ${bx + r} $topY Q $bx $topY $bx ${topY + r} L $bx ${midY - r} Q $bx $midY ${bx - r} $midY Q $bx $midY $bx ${midY + r} L $bx ${bottomY - r} Q $bx $bottomY ${bx + r} $bottomY"
            sb.append(
              s"""<path d="$bracePath" stroke="${Palette.ink}" stroke-width="1" fill="none" />"""
            )
            sb.append(
              s"""<text x="${bx - 8}" y="$midY" fill="${Palette.ink}" font-size="11" text-anchor="middle" transform="rotate(-90 ${bx - 8} $midY)">${escapeHtml(
                  g.name
                )}</text>"""
            )
          }
          sb.append("</g>")
        }

        // Map for Node Coordinates
        var nodeLocations = Map.empty[String, NodeLocation]

        // Click targets and node badges are collected here and emitted after
        // all the waves, so filled wave shapes do not swallow the clicks
        val hitsSb = new StringBuilder()
        val nodesSb = new StringBuilder()

        // 3. Render Signal Tracks
        diag.signals.zipWithIndex.foreach { case (sig, sigIdx) =>
          val rowY = 35 + (sigIdx * rowH)
          val yHigh = rowY + 12
          val yLow = rowY + 36
          val yMid = rowY + 24

          sb.append(s"""<g class="signal-row" data-sig-idx="$sigIdx">""")

          // The signal's own colour, when it has a valid one, replaces black
          // for every line of the row; fills and text keep their colours
          val ink = sig.color.flatMap(SignalColor.sanitize).getOrElse(Palette.ink)
          def stroke(d: String): String = line(d, ink)

          if (!sig.isSpacer) {
            for (c <- 0 until maxCycles) {
              val activeClass =
                if (state.activeCycle.contains((sigIdx, c))) " active" else ""
              hitsSb.append(
                s"""<rect class="cycle-hit$activeClass" data-sig="$sigIdx" data-cycle="$c" x="${labelWidth + (c * step)}" y="${rowY + 6}" width="$step" height="${rowH - 4}" fill="transparent" />"""
              )
            }
          }
          // Names sit to the left of the waves, flush against them
          sb.append(
            s"""<text x="${labelWidth - nameGap}" y="${rowY + 28}" fill="${Palette.ink}" font-size="11" text-anchor="end">${escapeHtml(
                sig.name
              )}</text>"""
          )

          // Node Markers
          val parsedNodes = WaveJsonCodec.parseNodeTokens(sig.node)
          parsedNodes.foreach { case (cycleIdx, nodeNames) =>
            nodeNames.foreach { nodeName =>
              // Markers sit on the cycle's leading edge: the rising edge of a
              // `p` clock, the falling edge of an `n` clock, and the point
              // where data changes
              val nx = labelWidth + cycleIdx * step
              val ny = yMid
              nodeLocations = nodeLocations.updated(
                nodeName,
                NodeLocation(nx, ny, sigIdx, cycleIdx)
              )

              // A name on a small patch of paper, as WaveDrom draws it; the
              // patch is outlined only while the node is picked for an edge
              val isSelected = state.selectedNodeForEdge.contains(nodeName)
              val badgeW = Math.max(10.0, nodeName.length * 5.5 + 4)

              nodesSb.append(s"""
            <g class="node-badge" data-node="${escapeHtml(nodeName)}">
              <rect x="${nx - badgeW / 2}" y="${ny - 6}" width="$badgeW" height="12" rx="2" fill="${
                  if (isSelected) Palette.selectedFill else Palette.paper
                }" stroke="${
                  if (isSelected) Palette.edge else "none"
                }" stroke-width="1" />
              <text class="node-label" x="$nx" y="${ny + 3}" fill="${Palette.ink}" font-size="9" text-anchor="middle">${escapeHtml(
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

          // `H` and `L` are resolved to their plain levels; the arrow is added
          // on the cycle that carries the token itself
          states.foreach { st =>
            if (st != WaveState.Hold) curState = WaveState.level(st)
            resolvedStates.append(curState)
          }

          var busDataCounter = 0
          // Bus labels are drawn after the row's shapes, so the fill of a
          // later cycle cannot cover a label centred over several cycles
          val labelsSb = new StringBuilder()

          for (c <- states.indices) {
            val cx = labelWidth + (c * step)
            val token = states(c)
            val normToken = resolvedStates(c)
            val prevNorm = if (c > 0) Some(resolvedStates(c - 1)) else None
            val nextNorm = resolvedStates.lift(c + 1)
            // How far into the cycle the slanted edge from the previous
            // state reaches; a level starts where that edge ends
            var lead = 0.0

            // Transitions
            prevNorm.filter(_ != normToken).foreach { prev =>
              val yFrom = getYLevel(prev, yHigh, yLow, yMid)
              val yTo = getYLevel(normToken, yHigh, yLow, yMid)

              normToken match {
                case WaveState.High | WaveState.Low | WaveState.HighZ =>
                  if (prev == WaveState.Undefined) {
                    // The hatched region closes onto the new level
                    lead = slant
                    sb.append(
                      s"""<path d="M $cx $yHigh L ${cx + lead} $yTo L $cx $yLow Z" fill="url(#hatch)" stroke="none" />"""
                    )
                    sb.append(
                      stroke(s"M $cx $yHigh L ${cx + lead} $yTo L $cx $yLow")
                    )
                  } else if (yFrom != yTo) {
                    lead = slant
                    sb.append(stroke(s"M $cx $yFrom L ${cx + lead} $yTo"))
                    // `H` and `L` mark a full-height edge as an active one
                    val fullEdge = yFrom == yHigh || yFrom == yLow
                    if (fullEdge && token == WaveState.HighArrow)
                      sb.append(clockArrow(cx + lead / 2, yMid, up = true, ink))
                    else if (fullEdge && token == WaveState.LowArrow)
                      sb.append(clockArrow(cx + lead / 2, yMid, up = false, ink))
                  }
                case WaveState.DataBus | WaveState.ColoredBus(_) =>
                  // A bus starts from its pinch point on the boundary, so
                  // the edge that reaches it stays vertical
                  if (yFrom != yTo && prev != WaveState.Undefined) {
                    sb.append(stroke(s"M $cx $yFrom L $cx $yTo"))
                  }
                case _ =>
                // A clock begins with a full-height edge of its own, and an
                // undefined region draws its own opening
              }
            }

            // Waveform Shapes
            normToken match {
              case WaveState.High =>
                sb.append(stroke(s"M ${cx + lead} $yHigh L ${cx + step} $yHigh"))
              case WaveState.Low =>
                sb.append(stroke(s"M ${cx + lead} $yLow L ${cx + step} $yLow"))
              case WaveState.PosClock | WaveState.PosClockArrow =>
                val half = Math.floor(step / 2.0)
                sb.append(
                  stroke(
                    s"M $cx $yLow L $cx $yHigh L ${cx + half} $yHigh L ${cx + half} $yLow L ${cx + step} $yLow"
                  )
                )
                if (normToken == WaveState.PosClockArrow)
                  sb.append(clockArrow(cx, yMid, up = true, ink))
              case WaveState.NegClock | WaveState.NegClockArrow =>
                val half = Math.floor(step / 2.0)
                sb.append(
                  stroke(
                    s"M $cx $yHigh L $cx $yLow L ${cx + half} $yLow L ${cx + half} $yHigh L ${cx + step} $yHigh"
                  )
                )
                if (normToken == WaveState.NegClockArrow)
                  sb.append(clockArrow(cx, yMid, up = false, ink))
              case WaveState.Undefined =>
                // Hatching between a top and a bottom line. It opens from
                // the level of whatever came before, and pinches shut where
                // a bus follows; a level that follows closes it instead.
                val busSlope = Math.min(6.0, step / 4.0)
                val xEnd = cx + step
                val opening = prevNorm.filter(_ != WaveState.Undefined).map { prev =>
                  val w = if (WaveState.isBus(prev)) busSlope else slant
                  (s"M $cx ${getYLevel(prev, yHigh, yLow, yMid)} L ${cx + w}", cx + w)
                }
                val (open, xStart) = opening.getOrElse((s"M $cx", cx))
                val pinched = nextNorm.exists(WaveState.isBus)
                val xFlat = if (pinched) xEnd - busSlope else xEnd
                val close = if (pinched) s" L $xEnd $yMid" else ""
                val top = s"$open $yHigh L $xFlat $yHigh$close"
                val bottom = s"$open $yLow L $xFlat $yLow$close"

                sb.append(
                  s"""<path d="$top L $xFlat $yLow L $xStart $yLow Z" fill="url(#hatch)" stroke="none" />"""
                )
                sb.append(stroke(top))
                sb.append(stroke(bottom))
              case WaveState.HighZ =>
                sb.append(stroke(s"M ${cx + lead} $yMid L ${cx + step} $yMid"))
              case WaveState.DataBus | WaveState.ColoredBus(_) =>
                val style = BusPalette.style(normToken)
                val slope = Math.min(6.0, step / 4.0)
                // A hold continues the value, and with it the colour; a new
                // bus token always starts a value of its own
                val isCont =
                  c > 0 && token == WaveState.Hold && WaveState.isBus(resolvedStates(c - 1))
                val isNextCont =
                  c + 1 < states.length && states(c + 1) == WaveState.Hold
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

                // The outline is drawn apart from the fill and left open on
                // the sides where the value runs on, so a held value reads
                // as one box rather than one box per cycle
                var outlineD =
                  if (!isCont)
                    s"M ${cx + slope} $yLow L $cx $yMid L ${cx + slope} $yHigh L $xRightPinch $yHigh "
                  else s"M $cx $yHigh L $xRightPinch $yHigh "
                outlineD += (if (!isNextCont)
                               s"L ${cx + step} $yMid L $xRightPinch $yLow "
                             else s"M ${cx + step} $yLow ")
                outlineD += (if (!isCont) s"L ${cx + slope} $yLow"
                             else s"L $cx $yLow")

                sb.append(
                  s"""<path class="bus-fill" d="$pathD" fill="${style.fill}" stroke="none" />"""
                )
                sb.append(stroke(outlineD))

                if (token != WaveState.Hold || c == 0) {
                  val labelText =
                    if (busDataCounter < sig.data.length)
                      sig.data(busDataCounter)
                    else ""
                  if (labelText.nonEmpty) {
                    // Centred over the whole value, however long it is held
                    val span =
                      1 + states.drop(c + 1).takeWhile(_ == WaveState.Hold).length
                    labelsSb.append(
                      s"""<text class="bus-label" x="${cx + (span * step / 2.0)}" y="${yMid + 4}" fill="${style.text}" font-size="11" text-anchor="middle">${escapeHtml(
                          labelText
                        )}</text>"""
                    )
                  }
                  busDataCounter += 1
                }
              case _ => // Hold and arrowed levels are resolved above
            }
          }

          sb.append(labelsSb.toString)
          sb.append("</g>")
        }

        sb.append("<g id=\"cycleHits\">").append(hitsSb.toString).append("</g>")

        // 4. Render Edges / Connection Arrows
        if (diag.edges.nonEmpty) {
          sb.append("<g id=\"edgesLayer\" pointer-events=\"none\">")
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
                    // WaveDrom's spline: it leaves and arrives horizontally
                    val dx = x2 - x1
                    val c1x = x1 + dx * 0.7; val c1y = y1
                    val c2x = x1 + dx * 0.3; val c2y = y2

                    pathD = s"M $x1 $y1 C $c1x $c1y, $c2x $c2y, $x2 $y2"
                    midX = 0.125 * x1 + 0.375 * c1x + 0.375 * c2x + 0.125 * x2
                    midY = 0.125 * y1 + 0.375 * c1y + 0.375 * c2y + 0.125 * y2
                  }

                  sb.append(
                    s"""<path d="$pathD" fill="none" stroke="${Palette.edge}" stroke-width="1" $markerStart$markerEnd />"""
                  )

                  if (labelText != null && labelText.trim.nonEmpty) {
                    // The label sits on the line, on a patch of paper
                    val label = labelText.trim
                    val labelW = label.length * 6.0 + 6
                    sb.append(
                      s"""<rect x="${midX - labelW / 2}" y="${midY - 7}" width="$labelW" height="14" fill="${Palette.paper}" />"""
                    )
                    sb.append(
                      s"""<text x="$midX" y="${midY + 3.5}" fill="${Palette.ink}" font-size="11" text-anchor="middle">${escapeHtml(
                          label
                        )}</text>"""
                    )
                  }
                }
              case _ =>
            }
          }
          sb.append("</g>")
        }

        // Node names go on top of the edges that meet at them, as in WaveDrom
        sb.append("<g id=\"nodeMarkers\">").append(nodesSb.toString).append("</g>")

        sb.append("</g>")
        sb.append("</svg>")
        sb.toString()
    }

  }

  // A hairline, the only kind of line a wave is drawn with
  private def line(d: String, color: String): String =
    s"""<path d="$d" stroke="$color" stroke-width="1" fill="none" />"""

  // The arrowhead WaveDrom puts on the working edge of a `P` or `N` clock:
  // a solid triangle half way up the edge, pointing the way the edge goes
  private def clockArrow(x: Double, yMid: Double, up: Boolean, color: String): String = {
    val tip = if (up) yMid - 4 else yMid + 4
    val base = if (up) yMid + 3 else yMid - 3
    s"""<path class="clock-arrow" d="M ${x - 3.5} $base L ${x + 3.5} $base L $x $tip Z" fill="$color" stroke="none" />"""
  }

  private def getYLevel(
      state: WaveState,
      yHigh: Double,
      yLow: Double,
      yMid: Double
  ): Double = state match {
    case WaveState.High | WaveState.NegClock | WaveState.NegClockArrow => yHigh
    case WaveState.Low | WaveState.PosClock | WaveState.PosClockArrow  => yLow
    case _                                                             => yMid
  }

  private def escapeHtml(str: String): String = {
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}
