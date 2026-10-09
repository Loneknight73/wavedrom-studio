package wavedrom.studio

class AppState {
  var jsonText: String = ""
  var document: Option[WaveDocument] = None
  var stepWidth: Double = 45.0
  val rowHeight: Double = 52.0
  var selectedNodeForEdge: Option[String] = None

  // (signal index, cycle index) currently open in the cycle popover
  var activeCycle: Option[(Int, Int)] = None

  // JSON text as it was before each graphical edit, most recent first
  private var undoStack = List.empty[String]
  private val maxUndo = 200

  def diagram: Option[WaveDiagram] = document.map(_.diagram)

  def canUndo: Boolean = undoStack.nonEmpty

  def updateFromText(newText: String): Either[String, WaveDiagram] = {
    jsonText = newText
    WaveJsonCodec.parseDocument(newText) match {
      case Right(parsed) =>
        document = Some(parsed)
        Right(parsed.diagram)
      case Left(err) =>
        Left(err)
    }
  }

  // Applies an edit to one signal and writes it back into the JSON text.
  // Returns the new text, or None when the edit changed nothing.
  def applyEdit(
      sigIdx: Int,
      edit: SignalTrack => SignalTrack
  ): Option[String] = {
    for {
      doc <- document
      original <- doc.diagram.signals.lift(sigIdx)
      updated = edit(original)
      if updated != original
    } yield {
      val previousText = jsonText
      updateFromText(WaveJsonCodec.patchSignal(doc, sigIdx, updated))
      undoStack = (previousText :: undoStack).take(maxUndo)
      jsonText
    }
  }

  def undo(): Option[String] = undoStack match {
    case previousText :: rest =>
      undoStack = rest
      updateFromText(previousText)
      Some(previousText)
    case Nil => None
  }
}
