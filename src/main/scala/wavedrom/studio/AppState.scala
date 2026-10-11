package wavedrom.studio

import scala.scalajs.js

object AppState {
  // A document as it comes from a file or a sample: loaded and not yet modified
  def open(
      text: String,
      fileName: Option[String] = None,
      fileHandle: Option[js.Any] = None
  ): AppState = {
    val state = new AppState()
    state.fileName = fileName
    state.fileHandle = fileHandle
    state.updateFromText(text)
    state.markSaved()
    state
  }
}

// The state of one open document (one tab)
class AppState {
  var jsonText: String = ""

  var fileName: Option[String] = None
  // FileSystemFileHandle to save back to, where the browser provides one
  var fileHandle: Option[js.Any] = None
  private var savedText: String = ""

  def title: String = fileName.getOrElse("untitled")
  def isDirty: Boolean = jsonText != savedText
  def markSaved(text: String = jsonText): Unit = savedText = text

  var document: Option[WaveDocument] = None
  val stepWidth: Double = 45.0
  // Scale of the whole drawing on screen; 1.0 is its natural size
  var zoom: Double = 1.0
  val rowHeight: Double = 40.0
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

  // Replaces the whole text (e.g. after reformatting) as one undoable step.
  // Returns None when the text is unchanged.
  def replaceText(newText: String): Option[String] =
    if (newText == jsonText) None
    else {
      val previousText = jsonText
      updateFromText(newText)
      undoStack = (previousText :: undoStack).take(maxUndo)
      Some(jsonText)
    }

  def undo(): Option[String] = undoStack match {
    case previousText :: rest =>
      undoStack = rest
      updateFromText(previousText)
      Some(previousText)
    case Nil => None
  }
}
