package wavedrom.studio

class EdgeShapeSuite extends munit.FunSuite {

  private def shapeOf(edge: String): Option[String] =
    EdgeShape.parse(edge).map(_.shape.token)

  private def geometry(token: String, from: (Double, Double), to: (Double, Double)) =
    EdgeShape.geometry(EdgeShape.shapes.find(_.token == token).get, from, to)

  test("parses every shape") {
    val tokens = Seq(
      "<-|->", "<-|>", "<-~>", "-|->", "<->", "<~>", "-|-", "-|>", "|->",
      "-~>", "~->", "-|", "|-", "-~", "~-", "->", "~>", "<-", "-", "~", "+"
    )
    assertEquals(EdgeShape.shapes.map(_.token), tokens.toVector)
    tokens.foreach { t =>
      val parsed = EdgeShape.parse(s"a${t}b").get
      assertEquals(parsed.shape.token, t)
      assertEquals((parsed.from, parsed.to), ("a", "b"))
    }
  }

  test("the longest shape wins") {
    assertEquals(shapeOf("a-|->b"), Some("-|->"))
    assertEquals(shapeOf("a<-|->b"), Some("<-|->"))
    assertEquals(shapeOf("a-|-b"), Some("-|-"))
    assertEquals(shapeOf("a-|>b"), Some("-|>"))
    assertEquals(shapeOf("a<->b"), Some("<->"))
    assertEquals(shapeOf("a->b"), Some("->"))
    assertEquals(shapeOf("a-b"), Some("-"))
  }

  test("reads the label and tolerates whitespace") {
    assertEquals(EdgeShape.parse("I+J 5 ms").flatMap(_.label), Some("5 ms"))
    assertEquals(EdgeShape.parse("a~>b").flatMap(_.label), None)
    assertEquals(EdgeShape.parse("  req_1 -> ack_2   3 cycles  ").map(p => (p.from, p.to, p.label)),
      Some(("req_1", "ack_2", Some("3 cycles"))))
  }

  test("rejects invalid strings") {
    List("", "a", "ab", "a=>b", "a->", "->b", "a?b", "a-|>", "a b").foreach { s =>
      assertEquals(EdgeShape.parse(s), None, s)
    }
  }

  test("arrowheads follow the ends of the token") {
    assertEquals(EdgeShape.parse("a<-b").map(p => (p.shape.startArrow, p.shape.endArrow)), Some((true, false)))
    assertEquals(EdgeShape.parse("a-|->b").map(p => (p.shape.startArrow, p.shape.endArrow)), Some((false, true)))
    assertEquals(EdgeShape.parse("a<-~>b").map(p => (p.shape.startArrow, p.shape.endArrow)), Some((true, true)))
    assertEquals(EdgeShape.parse("a+b").map(p => (p.shape.startArrow, p.shape.endArrow)), Some((false, false)))
  }

  test("a spline leaves and arrives horizontally") {
    val g = geometry("~>", (0, 0), (100, 20))
    assertEquals(g.path, "M 0 0 C 70 0, 30 20, 100 20")
    assertEquals((g.labelX, g.labelY), (50.0, 10.0))
    assertEquals((g.startArrow, g.endArrow, g.tee), (false, true, false))
  }

  test("the one-sided splines") {
    val out = geometry("-~>", (0, 0), (100, 20))
    assertEquals(out.path, "M 0 0 C 70 0, 100 20, 100 20")
    assertEquals(out.labelX, 75.0)
    val in = geometry("~-", (0, 0), (100, 20))
    assertEquals(in.path, "M 0 0 C 0 0, 30 20, 100 20")
    assertEquals(in.labelX, 25.0)
    assertEquals((in.startArrow, in.endArrow), (false, false))
  }

  test("elbows use absolute coordinates") {
    val h = geometry("-|", (10, 10), (50, 90))
    assertEquals(h.path, "M 10 10 L 50 10 L 50 90")
    assertEquals((h.labelX, h.labelY), (50.0, 50.0))
    val v = geometry("|->", (10, 10), (50, 90))
    assertEquals(v.path, "M 10 10 L 10 90 L 50 90")
    assertEquals(v.labelX, 10.0)
    assertEquals(v.endArrow, true)
    val mid = geometry("<-|->", (10, 10), (50, 90))
    assertEquals(mid.path, "M 10 10 L 30 10 L 30 90 L 50 90")
    assertEquals((mid.labelX, mid.labelY), (30.0, 50.0))
    assertEquals((mid.startArrow, mid.endArrow), (true, true))
  }

  test("straight lines and the tee") {
    val both = geometry("<->", (0, 0), (40, 20))
    assertEquals(both.path, "M 0 0 L 40 20")
    assertEquals((both.labelX, both.labelY), (20.0, 10.0))
    assertEquals((both.startArrow, both.endArrow, both.tee), (true, true, false))
    val tee = geometry("+", (0, 0), (40, 0))
    assertEquals(tee.path, "M 0 0 L 40 0")
    assertEquals((tee.startArrow, tee.endArrow, tee.tee), (false, false, true))
  }
}
