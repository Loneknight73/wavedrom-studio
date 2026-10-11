package wavedrom.studio

import org.scalajs.dom.html

object SvgRenderer {

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
        val labelWidth = 120 + groupIndent
        val step = state.stepWidth
        val rowH = state.rowHeight
        val totalWidth = labelWidth + (maxCycles * step) + 60
        val totalHeight = (diag.signals.length * rowH) + 60

        val sb = new StringBuilder()
        sb.append(
          // The drawing is laid out at its natural size; zooming only changes
          // the size it is shown at, so everything scales together
          s"""<svg width="${totalWidth * state.zoom}" height="${totalHeight * state.zoom}" viewBox="0 0 $totalWidth $totalHeight" xmlns="http://www.w3.org/2000/svg" class="wave-svg" font-family="'JetBrains Mono', monospace" style="user-select:none">"""
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
          val nameX =
            15 + (sig.depth * 20) + (if (diag.groups.nonEmpty) 12 else 0)

          sb.append(s"""<g class="signal-row" data-sig-idx="$sigIdx">""")

          // The signal's own colour, when it has a valid one, replaces the
          // default stroke of everything but the undefined (x) hatching
          val sigColor = sig.color.flatMap(SignalColor.sanitize)
          def stroke(default: String): String = sigColor.getOrElse(default)

          if (!sig.isSpacer) {
            for (c <- 0 until maxCycles) {
              val activeClass =
                if (state.activeCycle.contains((sigIdx, c))) " active" else ""
              hitsSb.append(
                s"""<rect class="cycle-hit$activeClass" data-sig="$sigIdx" data-cycle="$c" x="${labelWidth + (c * step)}" y="$rowY" width="$step" height="${rowH - 6}" fill="transparent" />"""
              )
            }
          }
          sb.append(
            s"""<text x="$nameX" y="${rowY + 28}" fill="#94a3b8" font-size="12" font-weight="600" font-family="monospace">${escapeHtml(
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

              val isSelected = state.selectedNodeForEdge.contains(nodeName)
              val badgeW = Math.max(18.0, nodeName.length * 6.5 + 8)

              nodesSb.append(s"""
            <g class="node-badge" data-node="${escapeHtml(nodeName)}">
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

            // Vertical Transitions
            if (c > 0) {
              val prevNorm = resolvedStates(c - 1)
              if (prevNorm != normToken) {
                val yFrom = getYLevel(prevNorm, yHigh, yLow, yMid)
                val yTo = getYLevel(normToken, yHigh, yLow, yMid)

                if (
                  yFrom != yTo && prevNorm != WaveState.Undefined && normToken != WaveState.Undefined
                ) {
                  val color = stroke(
                    if (WaveState.isBus(normToken)) BusPalette.style(normToken).stroke
                    else if (WaveState.isBus(prevNorm)) BusPalette.style(prevNorm).stroke
                    else if (
                      prevNorm == WaveState.HighZ || normToken == WaveState.HighZ
                    ) "#f59e0b"
                    else "#10b981"
                  )
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
                  s"""<path d="M $cx $yHigh L ${cx + step} $yHigh" stroke="${stroke("#10b981")}" stroke-width="2.5" fill="none" />"""
                )
              case WaveState.Low =>
                sb.append(
                  s"""<path d="M $cx $yLow L ${cx + step} $yLow" stroke="${stroke("#10b981")}" stroke-width="2.5" fill="none" />"""
                )
              case WaveState.PosClock =>
                val half = step / 2.0
                sb.append(
                  s"""<path d="M $cx $yLow L $cx $yHigh L ${cx + half} $yHigh L ${cx + half} $yLow L ${cx + step} $yLow" stroke="${stroke("#818cf8")}" stroke-width="2" fill="none" />"""
                )
              case WaveState.NegClock =>
                val half = step / 2.0
                sb.append(
                  s"""<path d="M $cx $yHigh L $cx $yLow L ${cx + half} $yLow L ${cx + half} $yHigh L ${cx + step} $yHigh" stroke="${stroke("#818cf8")}" stroke-width="2" fill="none" />"""
                )
              case WaveState.Undefined =>
                sb.append(
                  s"""<rect x="$cx" y="$yHigh" width="$step" height="${yLow - yHigh}" fill="url(#hatch)" stroke="#ef4444" stroke-width="1.5" />"""
                )
              case WaveState.HighZ =>
                sb.append(
                  s"""<line x1="$cx" y1="$yMid" x2="${cx + step}" y2="$yMid" stroke="${stroke("#f59e0b")}" stroke-width="2" stroke-dasharray="4 2" />"""
                )
              case WaveState.DataBus | WaveState.ColoredBus(_) =>
                val style = BusPalette.style(normToken)
                val slope = Math.min(6.0, step / 4.0)
                // A hold continues the segment, and with it the colour; a new
                // bus token always starts a segment of its own
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

                sb.append(
                  s"""<path d="$pathD" fill="${style.fill}" fill-opacity="${style.fillOpacity}" stroke="${stroke(style.stroke)}" stroke-width="2" />"""
                )

                if (token != WaveState.Hold || c == 0) {
                  val labelText =
                    if (busDataCounter < sig.data.length)
                      sig.data(busDataCounter)
                    else ""
                  if (labelText.nonEmpty) {
                    sb.append(
                      s"""<text x="${cx + (step / 2)}" y="${yMid + 4}" fill="${style.text}" font-size="10" font-weight="600" text-anchor="middle">${escapeHtml(
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

        sb.append("<g id=\"cycleHits\">").append(hitsSb.toString).append("</g>")
        sb.append("<g id=\"nodeMarkers\">").append(nodesSb.toString).append("</g>")

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
                      s"""<text x="$midX" y="${midY + 3.5}" fill="#f472b6" font-size="11" font-weight="700" text-anchor="middle">${escapeHtml(
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
        sb.toString()
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
