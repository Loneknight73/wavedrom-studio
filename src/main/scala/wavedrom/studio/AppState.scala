package wavedrom.studio

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
