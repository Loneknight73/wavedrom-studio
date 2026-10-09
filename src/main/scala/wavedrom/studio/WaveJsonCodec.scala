package wavedrom.studio

import scala.scalajs.js
import scala.util.control.NonFatal

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
