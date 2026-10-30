package wavedrom.studio

import org.scalajs.dom.html

object SvgRenderer {

  // The colours of the classic WaveDrom skin. They are written into the SVG
  // itself (never left to page CSS) so an exported drawing looks the same.
  private[studio] object Palette {
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
        // A head adds room for its title above the cycle numbers, a foot
        // room for its labels and caption below the waves
        val headSize = diag.head.flatMap(_.text).map(titleSize)
        val footSize = diag.foot.flatMap(_.text).map(titleSize)
        val headTextH = headSize.map(_ + 16).getOrElse(0.0)
        val footTickH = if (diag.foot.exists(_.hasLabels)) 20.0 else 0.0
        val footTextH = footSize.map(_ + 16).getOrElse(0.0)
        val waveTop = headTextH + 35
        val linesEnd = waveTop + (diag.signals.length * rowH) + 5
        val totalHeight = linesEnd + 20 + footTickH + footTextH
        val waveCentreX = labelWidth + (maxCycles * step) / 2.0
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
        <marker id="teeStart" viewBox="0 0 10 10" refX="-3" refY="5" markerWidth="9" markerHeight="9" orient="auto">
          <path d="M 5 0 L 5 10" stroke="${Palette.edge}" stroke-width="1" fill="none" />
        </marker>
        <marker id="teeEnd" viewBox="0 0 10 10" refX="13" refY="5" markerWidth="9" markerHeight="9" orient="auto">
          <path d="M 5 0 L 5 10" stroke="${Palette.edge}" stroke-width="1" fill="none" />
        </marker>
      </defs>
    """)

        // Half a pixel off the grid, so 1px lines land on whole pixels
        sb.append(
          """<g transform="translate(0.5 0.5)" stroke-linecap="round" stroke-linejoin="round">"""
        )

        // 1. Time Ruler
        sb.append("<g id=\"timeRuler\">")
        val rulerY = headTextH + 18
        def cycleLabel(x: Double, y: Double, label: String): Unit =
          sb.append(
            s"""<text x="$x" y="$y" fill="${Palette.tick}" font-size="9" text-anchor="middle">${escapeHtml(label)}</text>"""
          )
        for (c <- 0 until maxCycles) {
          val cx = labelWidth + (c * step)
          sb.append(
            s"""<line x1="$cx" y1="${waveTop - 10}" x2="$cx" y2="$linesEnd" stroke="${Palette.grid}" stroke-width="0.5" stroke-dasharray="1 3" />"""
          )
          // The studio numbers the cycles by default, unlike WaveDrom; a
          // head with its own tick or tock replaces the numbers
          if (!diag.head.exists(_.hasLabels))
            cycleLabel(cx + (step / 2), rulerY, c.toString)
        }
        val endX = labelWidth + (maxCycles * step)
        sb.append(
          s"""<line x1="$endX" y1="${waveTop - 10}" x2="$endX" y2="$linesEnd" stroke="${Palette.grid}" stroke-width="0.5" stroke-dasharray="1 3" />"""
        )
        // Tick labels sit on the cycle lines (the end line included), tock
        // labels between them
        def labelRow(cap: Caption, y: Double): Unit =
          for (c <- 0 to maxCycles if c % cap.every == 0) {
            val cx = labelWidth + (c * step)
            cap.tick.flatMap(_.at(c)).foreach(cycleLabel(cx, y, _))
            if (c < maxCycles)
              cap.tock.flatMap(_.at(c)).foreach(cycleLabel(cx + step / 2, y, _))
          }
        diag.head.foreach(labelRow(_, rulerY))
        diag.foot.foreach(labelRow(_, linesEnd + 14))
        // Title and caption, centred over the waves. The largest span only sets
        // the line height; spans without a size of their own keep the default,
        // as in WaveDrom, instead of inheriting the largest one
        def captionText(cap: Option[Caption], size: Option[Double], y: Double): Unit =
          for (c <- cap; t <- c.text; _ <- size)
            sb.append(
              s"""<text class="caption" x="$waveCentreX" y="$y" fill="${Palette.ink}" font-size="$defaultTitleSize" text-anchor="middle">${RichText.toSvg(t)}</text>"""
            )
        captionText(diag.head, headSize, headSize.getOrElse(0.0) + 4)
        captionText(diag.foot, footSize, linesEnd + footTickH + footSize.getOrElse(0.0) + 6)
        sb.append("</g>")

        // 2. Curly Brackets for Groups
        if (diag.groups.nonEmpty) {
          sb.append("<g id=\"groupBrackets\">")
          diag.groups.foreach { g =>
            val topY = waveTop + (g.startSigIdx * rowH) + 8
            val bottomY = waveTop + (g.endSigIdx * rowH) + 40
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
          val rowY = waveTop + (sigIdx * rowH)
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
            <g class="node-badge" data-node="${escapeHtml(nodeName)}" data-x="$nx" data-y="$ny">
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
            if (!WaveState.isHold(st)) curState = WaveState.level(st)
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
                case WaveState.High | WaveState.Low | WaveState.HighZ |
                    WaveState.PullUp | WaveState.PullDown =>
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
                    if (WaveState.isPull(normToken)) {
                      // The curve takes the whole cycle; the dotted level follows
                      lead = step
                      sb.append(stroke(pullCurve(cx, step, yHigh, yLow, yFrom, yTo)))
                    } else {
                      // h, l, H and L switch at once, as a clock does
                      lead = if (WaveState.isSharp(token)) 0.0 else slant
                      sb.append(stroke(s"M $cx $yFrom L ${cx + lead} $yTo"))
                    }
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
              case WaveState.PullUp | WaveState.PullDown =>
                if (lead < step) {
                  val y = getYLevel(normToken, yHigh, yLow, yMid)
                  sb.append(dotted(s"M ${cx + lead} $y L ${cx + step} $y", ink))
                }
              case WaveState.DataBus | WaveState.ColoredBus(_) =>
                val style = BusPalette.style(normToken)
                val slope = Math.min(6.0, step / 4.0)
                // A hold continues the value, and with it the colour; a new
                // bus token always starts a value of its own
                val isCont =
                  c > 0 && WaveState.isHold(token) && WaveState.isBus(resolvedStates(c - 1))
                val isNextCont =
                  c + 1 < states.length && WaveState.isHold(states(c + 1))
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

                if (!WaveState.isHold(token) || c == 0) {
                  val labelText =
                    if (busDataCounter < sig.data.length)
                      sig.data(busDataCounter)
                    else ""
                  if (labelText.nonEmpty) {
                    // Centred over the whole value, however long it is held
                    val span =
                      1 + states.drop(c + 1).takeWhile(WaveState.isHold).length
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

            if (token == WaveState.Gap)
              sb.append(gapGlyph(cx + step / 2.0, yHigh, ink))
          }

          sb.append(labelsSb.toString)
          sb.append("</g>")
        }

        sb.append("<g id=\"cycleHits\">").append(hitsSb.toString).append("</g>")

        // 4. Render Edges / Connection Arrows
        // The arrows themselves take no clicks; wider invisible copies of them
        // do, so a thin line is easy to hit
        val edgeHitsSb = new StringBuilder()
        if (diag.edges.nonEmpty) {
          sb.append("<g id=\"edgesLayer\" pointer-events=\"none\">")
          // An entry that does not parse, or names an unknown node, is skipped
          diag.edges.zipWithIndex.foreach { case (edgeStr, edgeIdx) =>
            for {
              edge <- EdgeShape.parse(edgeStr)
              p1 <- nodeLocations.get(edge.from)
              p2 <- nodeLocations.get(edge.to)
            } {
              val g = EdgeShape.geometry(edge.shape, (p1.x, p1.y), (p2.x, p2.y))
              sb.append(
                s"""<path d="${g.path}" fill="none" stroke="${Palette.edge}" stroke-width="1" ${markerAttrs(g)} />"""
              )
              edgeHitsSb.append(
                s"""<path class="edge-hit edge-ui" data-edge="$edgeIdx" d="${g.path}" fill="none" stroke="transparent" stroke-width="10" pointer-events="stroke" />"""
              )

              edge.label.foreach { label =>
                // The label sits on the line, on a patch of paper
                val labelW = label.length * 6.0 + 6
                sb.append(
                  s"""<rect x="${g.labelX - labelW / 2}" y="${g.labelY - 7}" width="$labelW" height="14" fill="${Palette.paper}" />"""
                )
                sb.append(
                  s"""<text x="${g.labelX}" y="${g.labelY + 3.5}" fill="${Palette.ink}" font-size="11" text-anchor="middle">${escapeHtml(
                      label
                    )}</text>"""
                )
                // The label covers the line, so it is a handle for the arrow too
                edgeHitsSb.append(
                  s"""<rect class="edge-hit edge-ui" data-edge="$edgeIdx" x="${g.labelX - labelW / 2}" y="${g.labelY - 7}" width="$labelW" height="14" fill="transparent" />"""
                )
              }
            }
          }
          sb.append("</g>")
        }
        sb.append("<g id=\"edgeHits\">").append(edgeHitsSb.toString).append("</g>")

        // The arrow being drawn follows the mouse; the app fills it in
        sb.append(
          s"""<path id="edgePreview" class="edge-ui" d="" fill="none" stroke="${Palette.edge}" stroke-width="1" pointer-events="none" />"""
        )

        // Node names go on top of the edges that meet at them, as in WaveDrom
        sb.append("<g id=\"nodeMarkers\">").append(nodesSb.toString).append("</g>")

        sb.append("</g>")
        sb.append("</svg>")
        sb.toString()
    }

  }

  // Plain text is drawn at this size; rich text may ask for more
  private val defaultTitleSize = 14.0

  private def titleSize(text: RichText): Double =
    RichText.maxFontSize(text, defaultTitleSize)

  // A tee bar closes both ends of a + line. Like the arrowheads, the bars stop
  // short of the node, clear of the patch its name is on.
  private[studio] def markerStartId(g: EdgeShape.Geometry): Option[String] =
    if (g.tee) Some("teeStart") else if (g.startArrow) Some("arrowStart") else None

  private[studio] def markerEndId(g: EdgeShape.Geometry): Option[String] =
    if (g.tee) Some("teeEnd") else if (g.endArrow) Some("arrowEnd") else None

  private def markerAttrs(g: EdgeShape.Geometry): String =
    (markerStartId(g).map(id => s"""marker-start="url(#$id)"""") ++
      markerEndId(g).map(id => s"""marker-end="url(#$id)"""")).mkString(" ")

  // A hairline, the only kind of line a wave is drawn with
  private def line(d: String, color: String): String =
    s"""<path d="$d" stroke="$color" stroke-width="1" fill="none" />"""

  // The line of a pulled-up or pulled-down level
  private def dotted(d: String, color: String): String =
    s"""<path d="$d" stroke="$color" stroke-width="1" stroke-dasharray="1 3" fill="none" />"""

  // The curve into a pulled level, after WaveDrom's 0mu, 1md and zmu bricks
  // (a 20 by 20 grid, 0 at the high level). From the opposite level it is an
  // RC-style charge; from mid height (z, x, a bus) it is the shallower zm form.
  private def pullCurve(
      cx: Double,
      step: Double,
      yHigh: Double,
      yLow: Double,
      yFrom: Double,
      yTo: Double
  ): String = {
    val sx = step / 20.0
    val sy = (yLow - yHigh) / 20.0
    def p(x: Double, y: Double) = s"${cx + x * sx} ${yHigh + y * sy}"
    val up = yTo == yHigh
    if (yFrom == yHigh || yFrom == yLow) {
      // 0mu runs from the bottom to the top; 1md is its mirror image
      val (y0, y1) = if (up) (20.0, 0.0) else (0.0, 20.0)
      s"M ${p(0, y0)} L ${p(3, y0)} C ${p(7, 10)}, ${p(10.1, y1)}, ${p(20, y1)}"
    } else {
      val (y1, c1, c2) = if (up) (0.0, 5.0, 0.0) else (20.0, 15.0, 20.0)
      s"M ${p(0, 10)} L ${p(7, 10)} C ${p(10, c1)}, ${p(15, c2)}, ${p(20, y1)}"
    }
  }

  // The break WaveDrom draws over a `|` cycle, centred on x: a patch of
  // paper hides the line, and two curved strokes mark the gap
  private def gapGlyph(x: Double, yHigh: Double, color: String): String = {
    def p(gx: Double, gy: Double) = s"${x + gx} ${yHigh + gy * 1.2}"
    val patch =
      s"M ${p(7, -2)} L ${p(3, -2)} C ${p(-2, -2)}, ${p(-2, 22)}, ${p(-7, 22)} L ${p(-3, 22)} C ${p(2, 22)}, ${p(2, -2)}, ${p(7, -2)} Z"
    s"""<g class="gap"><path d="$patch" fill="${Palette.paper}" stroke="none" />${line(
        s"M ${p(-7, 22)} C ${p(-2, 22)}, ${p(-2, -2)}, ${p(3, -2)}",
        color
      )}${line(s"M ${p(-3, 22)} C ${p(2, 22)}, ${p(2, -2)}, ${p(7, -2)}", color)}</g>"""
  }

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
    case WaveState.High | WaveState.PullUp | WaveState.NegClock |
        WaveState.NegClockArrow =>
      yHigh
    case WaveState.Low | WaveState.PullDown | WaveState.PosClock |
        WaveState.PosClockArrow =>
      yLow
    case _                                                             => yMid
  }

  private[studio] def escapeHtml(str: String): String = {
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}
