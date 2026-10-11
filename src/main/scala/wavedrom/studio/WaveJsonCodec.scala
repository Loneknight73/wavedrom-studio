package wavedrom.studio

import scala.scalajs.js
import scala.util.control.NonFatal

object WaveJsonCodec {

  def parse(jsonStr: String): Either[String, WaveDiagram] =
    parseDocument(jsonStr).map(_.diagram)

  def parseDocument(jsonStr: String): Either[String, WaveDocument] = {
    try {
      val parsed = js.JSON.parse(jsonStr)
      val signalArr = parsed.signal.asInstanceOf[js.Array[js.Any]]

      var flatSignals = Vector.empty[SignalTrack]
      var signalObjs = Vector.empty[js.Dynamic]
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
            val isSpacer =
              js.typeOf(obj.name) == "undefined" && js.typeOf(obj.wave) == "undefined"
            val name =
              if (js.typeOf(obj.name) != "undefined") obj.name.toString
              else if (isSpacer) ""
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

            val color =
              if (js.typeOf(obj.color) == "string") Some(obj.color.toString)
              else None

            flatSignals =
              flatSignals :+ SignalTrack(name, wave, node, dataVec, depth, color)
            signalObjs = signalObjs :+ obj
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

      Right(
        WaveDocument(parsed, signalObjs, WaveDiagram(flatSignals, groups, edges))
      )
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

    // Dots are always gaps, one cycle each; a run of other characters is one
    // marker name, so "..d.." is two gaps, the marker d, and two more gaps.
    val runs = """\.+|[^.]+""".r
    tokens.foreach { tok =>
      runs.findAllIn(tok).foreach { run =>
        if (run.head == '.') {
          cycleIdx += run.length
        } else {
          val existing = resultMap.getOrElse(cycleIdx, Vector.empty)
          resultMap = resultMap.updated(cycleIdx, existing :+ run)
          cycleIdx += 1
        }
      }
    }
    resultMap

  }

  // Writes the changed fields of one signal back into the document's JS tree
  // and returns the whole document as JSON. Everything else is left untouched.
  def patchSignal(doc: WaveDocument, sigIdx: Int, updated: SignalTrack): String = {
    val original = doc.diagram.signals(sigIdx)
    val obj = doc.signalObjs(sigIdx)

    if (updated.wave != original.wave) obj.wave = updated.wave
    if (updated.node != original.node) {
      if (updated.node.nonEmpty) obj.node = updated.node
      else js.special.delete(obj, "node")
    }
    if (updated.data != original.data) {
      if (updated.data.nonEmpty) {
        val dataArr = js.Array[js.Any]()
        updated.data.foreach(d => dataArr.push(d))
        obj.data = dataArr
      } else js.special.delete(obj, "data")
    }
    if (updated.color != original.color) {
      updated.color match {
        case Some(color) => obj.color = color
        case None        => js.special.delete(obj, "color")
      }
    }

    format(doc.root)
  }

  // Pretty-prints in the usual WaveJSON layout: objects that hold only plain
  // values (a signal, `head`, `config`...) stay on one line.
  def format(value: js.Any): String = formatValue(value, 0)

  private def isPrimitive(value: js.Any): Boolean =
    value == null || js.typeOf(value) != "object"

  private def isFlat(value: js.Any): Boolean =
    isPrimitive(value) || (js.Array.isArray(value) &&
      value.asInstanceOf[js.Array[js.Any]].forall(isPrimitive))

  private def formatInline(value: js.Any): String =
    if (js.Array.isArray(value))
      value.asInstanceOf[js.Array[js.Any]].map(formatInline).mkString("[", ", ", "]")
    else js.JSON.stringify(value)

  private def formatValue(value: js.Any, depth: Int): String = {
    val pad = "  " * (depth + 1)
    val closePad = "  " * depth

    if (isPrimitive(value)) js.JSON.stringify(value)
    else if (js.Array.isArray(value)) {
      val items = value.asInstanceOf[js.Array[js.Any]]
      if (items.isEmpty) "[]"
      else if (items.length > 1 && js.typeOf(items(0)) == "string") {
        // A signal group: its name shares the line with the opening bracket
        val rest = items.drop(1).map(item => pad + formatValue(item, depth + 1))
        s"[ ${js.JSON.stringify(items(0))},\n" + rest.mkString("", ",\n", s"\n$closePad]")
      } else
        items
          .map(item => pad + formatValue(item, depth + 1))
          .mkString("[\n", ",\n", s"\n$closePad]")
    } else {
      val fields = value.asInstanceOf[js.Dictionary[js.Any]]
      val keys = js.Object.keys(value.asInstanceOf[js.Object])
      if (keys.isEmpty) "{}"
      else if (keys.forall(k => isFlat(fields(k))))
        keys
          .map(k => s"${js.JSON.stringify(k)}: ${formatInline(fields(k))}")
          .mkString("{ ", ", ", " }")
      else
        keys
          .map(k =>
            s"$pad${js.JSON.stringify(k)}: ${formatValue(fields(k), depth + 1)}"
          )
          .mkString("{\n", ",\n", s"\n$closePad}")
    }
  }
}
