package wavedrom.studio

class EdgeEditorSuite extends munit.FunSuite {

  private def shape(token: String): EdgeShape.Shape =
    EdgeShape.shapes.find(_.token == token).get

  private val edges = Vector("a~>b 3 cycles", "c->d", "not an edge")

  test("add appends an entry, with its label when there is one") {
    assertEquals(EdgeEditor.add(Vector.empty, "a", shape("-|->"), "b", None), Vector("a-|->b"))
    assertEquals(
      EdgeEditor.add(edges, "x", shape("+"), "y", Some(" 5 ms ")),
      edges :+ "x+y 5 ms"
    )
    // A blank label is no label
    assertEquals(EdgeEditor.add(Vector.empty, "a", shape("->"), "b", Some("  ")), Vector("a->b"))
  }

  test("added entries parse back to what was asked for") {
    val entry = EdgeEditor.add(Vector.empty, "a_1", shape("<-~>"), "b2", Some("two words")).head
    val parsed = EdgeShape.parse(entry).get
    assertEquals((parsed.from, parsed.shape.token, parsed.to), ("a_1", "<-~>", "b2"))
    assertEquals(parsed.label, Some("two words"))
  }

  test("restyle keeps from, to and label") {
    assertEquals(
      EdgeEditor.restyle(edges, 0, shape("-|->")),
      Vector("a-|->b 3 cycles", "c->d", "not an edge")
    )
    assertEquals(EdgeEditor.restyle(edges, 1, shape("<->")).apply(1), "c<->d")
  }

  test("relabel sets, replaces and removes the label") {
    assertEquals(EdgeEditor.relabel(edges, 1, Some("go")).apply(1), "c->d go")
    assertEquals(EdgeEditor.relabel(edges, 0, Some("later")).apply(0), "a~>b later")
    assertEquals(EdgeEditor.relabel(edges, 0, None).apply(0), "a~>b")
  }

  test("update changes style and label together") {
    assertEquals(EdgeEditor.update(edges, 0, shape("+"), Some("1 ms")).apply(0), "a+b 1 ms")
  }

  test("delete removes one entry") {
    assertEquals(EdgeEditor.delete(edges, 1), Vector("a~>b 3 cycles", "not an edge"))
  }

  test("an index out of range, or an entry that does not parse, changes nothing") {
    for (index <- Seq(-1, 3, 99)) {
      assertEquals(EdgeEditor.delete(edges, index), edges)
      assertEquals(EdgeEditor.restyle(edges, index, shape("->")), edges)
      assertEquals(EdgeEditor.relabel(edges, index, Some("x")), edges)
    }
    assertEquals(EdgeEditor.restyle(edges, 2, shape("->")), edges)
    assertEquals(EdgeEditor.relabel(edges, 2, Some("x")), edges)
  }

  test("only names the edge strings can carry are node names") {
    assert(Seq("a", "req_1", "N2").forall(EdgeEditor.isNodeName))
    assert(Seq("", "a-b", "x y", "a.b", "é").forall(!EdgeEditor.isNodeName(_)))
  }

  test("the popover lists every shape once") {
    val listed = EdgeEditor.styleGroups.flatMap(_._2).map(_.token)
    assertEquals(listed.sorted, EdgeShape.shapes.map(_.token).sorted)
    assertEquals(listed.distinct, listed)
  }
}
