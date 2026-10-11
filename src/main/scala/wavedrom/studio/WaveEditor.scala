package wavedrom.studio

// Pure edit operations on a single signal. Each returns a new SignalTrack with
// wave, node and data kept consistent with one another.
object WaveEditor {

  private def isBusStart(c: Char): Boolean =
    WaveState.isBus(WaveState.fromChar(c))

  private def busStartsBefore(wave: String, pos: Int): Int =
    wave.take(pos).count(isBusStart)

  // The token in effect at `cycle`: the last non-hold character at or before it
  private def effectiveChar(wave: String, cycle: Int): Option[Char] =
    wave.take(cycle + 1).reverseIterator.find(_ != '.')

  // Index into `data` of the bus segment covering `cycle`, if there is one
  private def busDataIndex(wave: String, cycle: Int): Option[Int] = {
    val start = wave.lastIndexWhere(_ != '.', cycle)
    if (cycle < wave.length && start >= 0 && isBusStart(wave(start)))
      Some(busStartsBefore(wave, start))
    else None
  }

  def setCycle(track: SignalTrack, cycle: Int, newChar: Char): SignalTrack = {
    var wave = track.wave.padTo(cycle + 1, '.')
    var data = track.data

    // A hold right after the edited cycle must keep showing what it showed
    // before, so it takes over the token it was holding.
    val carried = effectiveChar(wave, cycle)
    val newEffective =
      if (newChar == '.') effectiveChar(wave, cycle - 1) else Some(newChar)
    if (
      cycle + 1 < wave.length && wave(cycle + 1) == '.' && newEffective != carried
    ) {
      val carriedChar = carried.getOrElse('0')
      if (isBusStart(carriedChar)) {
        // The held bus segment is split in two: the second half keeps its label
        val idx = busStartsBefore(wave, cycle + 1)
        if (idx >= 1 && idx <= data.length)
          data = data.patch(idx, Seq(data(idx - 1)), 0)
      }
      wave = wave.updated(cycle + 1, carriedChar)
    }

    val oldChar = wave(cycle)
    val idx = busStartsBefore(wave, cycle)
    if (isBusStart(oldChar) && !isBusStart(newChar)) {
      if (idx < data.length) data = data.patch(idx, Nil, 1)
    } else if (!isBusStart(oldChar) && isBusStart(newChar)) {
      if (idx < data.length) data = data.patch(idx, Seq(""), 0)
    }
    wave = wave.updated(cycle, newChar)

    track.copy(wave = wave, data = data)
  }

  // Turns the signal, from `cycle` to its end, into a clock active on both
  // edges: `HLHL...`, one grid cycle per half period. It starts on the level
  // opposite to the one before it, so its first cycle opens with an edge.
  def doubleEdgeClockFrom(track: SignalTrack, cycle: Int): SignalTrack = {
    val end = Math.max(track.wave.length, cycle + 1)
    val before = effectiveChar(track.wave, cycle - 1).map(WaveState.fromChar)
    val startsLow = before.exists {
      case WaveState.High | WaveState.HighArrow | WaveState.NegClock |
          WaveState.NegClockArrow =>
        true
      case _ => false
    }
    (cycle until end).foldLeft(track) { (t, c) =>
      val high = ((c - cycle) % 2 == 0) != startsLow
      setCycle(t, c, if (high) 'H' else 'L')
    }
  }

  def insertHoldAfter(track: SignalTrack, cycle: Int): SignalTrack = {
    val wave = track.wave.padTo(cycle + 1, '.').patch(cycle + 1, ".", 0)
    val slots = nodeSlots(track.node)
    val node =
      if (slots.length > cycle + 1)
        formatNodes(slots.patch(cycle + 1, Seq(""), 0))
      else track.node
    track.copy(wave = wave, node = node)
  }

  def deleteCycle(track: SignalTrack, cycle: Int): SignalTrack = {
    val slots = nodeSlots(track.node)
    val node =
      if (slots.length > cycle) formatNodes(slots.patch(cycle, Nil, 1))
      else track.node

    val wave = track.wave
    if (cycle >= wave.length) track.copy(node = node)
    else {
      // Deleting the first cycle of a held segment shortens the segment
      // instead of removing its token.
      val heldAfter =
        wave(cycle) != '.' && cycle + 1 < wave.length && wave(cycle + 1) == '.'
      val pos = if (heldAfter) cycle + 1 else cycle
      val idx = busStartsBefore(wave, pos)
      val data =
        if (isBusStart(wave(pos)) && idx < track.data.length)
          track.data.patch(idx, Nil, 1)
        else track.data
      track.copy(wave = wave.patch(pos, "", 1), node = node, data = data)
    }
  }

  // --- Bus labels ----------------------------------------------------------

  // The label of the bus segment covering `cycle`; None when it is not a bus
  def busLabel(track: SignalTrack, cycle: Int): Option[String] =
    busDataIndex(track.wave, cycle).map(i => track.data.lift(i).getOrElse(""))

  def setBusLabel(track: SignalTrack, cycle: Int, label: String): SignalTrack =
    busDataIndex(track.wave, cycle) match {
      case None => track
      case Some(idx) =>
        val data = track.data.padTo(idx + 1, "").updated(idx, label.trim)
        track.copy(data = data.reverse.dropWhile(_.isEmpty).reverse)
    }

  // --- Colours ------------------------------------------------------------

  // The wave character of the bus segment covering `cycle`: `=` or `2`..`9`
  def busToken(track: SignalTrack, cycle: Int): Option[Char] = {
    val start = track.wave.lastIndexWhere(_ != '.', cycle)
    if (cycle < track.wave.length && start >= 0 && isBusStart(track.wave(start)))
      Some(track.wave(start))
    else None
  }

  // Recolours the whole bus segment covering `cycle`; a cycle that is not on
  // a bus becomes a one-cycle bus of that colour. `token` is `=` or `2`..`9`.
  def setBusColor(track: SignalTrack, cycle: Int, token: Char): SignalTrack =
    if (!isBusStart(token)) track
    else if (busToken(track, cycle).isEmpty) setCycle(track, cycle, token)
    else {
      val start = track.wave.lastIndexWhere(_ != '.', cycle)
      track.copy(wave = track.wave.updated(start, token))
    }

  // None, or a value that is not a colour, puts the signal back to default
  def setSignalColor(track: SignalTrack, color: Option[String]): SignalTrack =
    track.copy(color = color.flatMap(SignalColor.sanitize))

  // --- Node markers --------------------------------------------------------

  def nodeAt(track: SignalTrack, cycle: Int): String =
    nodeSlots(track.node).lift(cycle).getOrElse("")

  def setNode(track: SignalTrack, cycle: Int, name: String): SignalTrack = {
    val cleaned = name.trim.replaceAll("\\s+", "_")
    val marker = if (cleaned.forall(_ == '.')) "" else cleaned
    val slots = nodeSlots(track.node).padTo(cycle + 1, "").updated(cycle, marker)
    track.copy(node = formatNodes(slots))
  }

  // One entry per cycle, "" where there is no marker
  private def nodeSlots(node: String): Vector[String] = {
    val byCycle = WaveJsonCodec.parseNodeTokens(node)
    if (byCycle.isEmpty) Vector.empty
    else
      Vector.tabulate(byCycle.keys.max + 1)(c =>
        byCycle.get(c).flatMap(_.headOption).getOrElse("")
      )
  }

  private def formatNodes(slots: Vector[String]): String = {
    val trimmed = slots.reverse.dropWhile(_.isEmpty).reverse
    if (trimmed.forall(_.length <= 1))
      // Standard WaveDrom form: one character per cycle
      trimmed.map(s => if (s.isEmpty) "." else s).mkString
    else {
      // Multi-character names: space-separated tokens, runs of dots for gaps
      val tokens = Vector.newBuilder[String]
      var gap = 0
      trimmed.foreach { s =>
        if (s.isEmpty) gap += 1
        else {
          if (gap > 0) tokens += "." * gap
          gap = 0
          tokens += s
        }
      }
      val spaced = tokens.result().mkString(" ")
      // A lone name would be read back as one marker per character
      if (spaced.contains(" ")) spaced else spaced + " ."
    }
  }
}
