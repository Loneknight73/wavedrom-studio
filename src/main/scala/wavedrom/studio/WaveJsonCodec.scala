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
        WaveDocument(
          parsed,
          signalObjs,
          WaveDiagram(
            flatSignals,
            groups,
            edges,
            parseCaption(parsed.head),
            parseCaption(parsed.foot)
          )
        )
      )
    } catch {
      case NonFatal(ex) => Left(s"JSON Syntax Error: ${ex.getMessage}")
    }

  }

  private def isObject(value: js.Any): Boolean =
    value != null && js.typeOf(value) == "object" && !js.Array.isArray(value)

  // `head` or `foot`. A value of the wrong type is ignored, so a mistake
  // costs the drawing that part, not the whole document.
  private def parseCaption(value: js.Any): Option[Caption] =
    if (!isObject(value)) None
    else {
      val obj = value.asInstanceOf[js.Dynamic]
      val rawEvery = obj.every.asInstanceOf[js.Any]
      val every =
        if (js.typeOf(rawEvery) == "number") {
          val d = rawEvery.asInstanceOf[Double]
          if (d >= 1 && d == Math.floor(d) && d < Int.MaxValue) d.toInt else 1
        } else 1
      Some(
        Caption(
          parseRichText(obj.text.asInstanceOf[js.Any]),
          parseLabels(obj.tick.asInstanceOf[js.Any]),
          parseLabels(obj.tock.asInstanceOf[js.Any]),
          every
        )
      )
    }

  private def parseLabels(value: js.Any): Option[CycleLabels] =
    js.typeOf(value) match {
      case "number" =>
        val d = value.asInstanceOf[Double]
        if (d.isNaN || d.isInfinite) None else Some(CycleLabels.CountFrom(d))
      case "string" =>
        Some(CycleLabels.Listed(value.asInstanceOf[String].trim.split("\\s+").toVector.filter(_.nonEmpty)))
      case "object" if js.Array.isArray(value) =>
        Some(CycleLabels.Listed(value.asInstanceOf[js.Array[js.Any]].map(_.toString).toVector))
      case _ => None
    }

  // A string, or JsonML: ["tspan", {attrs}?, child, ...]. Elements other than
  // `tspan` are dropped together with their content.
  private def parseRichText(value: js.Any): Option[RichText] =
    js.typeOf(value) match {
      case "string" =>
        val s = value.asInstanceOf[String]
        if (s.isEmpty) None else Some(RichText.Text(s))
      case "object" if js.Array.isArray(value) =>
        val arr = value.asInstanceOf[js.Array[js.Any]].toVector
        val isTspan = arr.headOption.exists(h =>
          js.typeOf(h) == "string" && h.asInstanceOf[String] == "tspan"
        )
        if (!isTspan) None
        else {
          val rest = arr.tail
          val attrObj = rest.headOption.filter(isObject)
          val attrs = attrObj.toVector.flatMap { a =>
            a.asInstanceOf[js.Dictionary[js.Any]].toVector.collect {
              case (k, v) if js.typeOf(v) == "string" || js.typeOf(v) == "number" =>
                k -> v.toString
            }
          }.toMap
          val children = (if (attrObj.isDefined) rest.tail else rest).flatMap(parseRichText)
          Some(RichText.Span(attrs, children))
        }
      case _ => None
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

  // Sets the document's `edge` list (creating it, or dropping it when empty)
  // and returns the whole document as JSON. Everything else is left untouched.
  def patchEdges(doc: WaveDocument, edges: Vector[String]): String = {
    if (edges.nonEmpty) {
      val arr = js.Array[js.Any]()
      edges.foreach(e => arr.push(e))
      doc.root.edge = arr
    } else js.special.delete(doc.root, "edge")

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
