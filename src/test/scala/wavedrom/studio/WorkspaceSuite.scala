package wavedrom.studio

class WorkspaceSuite extends munit.FunSuite {

  private def doc(name: String, wave: String = "01"): AppState =
    AppState.open(
      s"""{ "signal": [ { "name": "s", "wave": "$wave" } ] }""",
      Some(name)
    )

  private def workspaceOf(names: String*): Workspace = {
    val workspace = new Workspace(doc(names.head), () => AppState.open(Samples.blank))
    names.tail.foreach(name => workspace.add(doc(name)))
    workspace
  }

  private def titles(workspace: Workspace): Vector[String] =
    workspace.tabs.map(_.title)

  // --- tabs ----------------------------------------------------------------

  test("add appends a tab and makes it active") {
    val workspace = workspaceOf("a")
    workspace.add(doc("b"))
    assertEquals(titles(workspace), Vector("a", "b"))
    assertEquals(workspace.active.title, "b")
  }

  test("select switches tabs and ignores an unknown index") {
    val workspace = workspaceOf("a", "b", "c")
    workspace.select(0)
    assertEquals(workspace.active.title, "a")
    workspace.select(7)
    assertEquals(workspace.active.title, "a")
  }

  test("closing a tab before the active one keeps the same document active") {
    val workspace = workspaceOf("a", "b", "c")
    workspace.close(0)
    assertEquals(titles(workspace), Vector("b", "c"))
    assertEquals(workspace.active.title, "c")
  }

  test("closing the active tab activates its right-hand neighbour") {
    val workspace = workspaceOf("a", "b", "c")
    workspace.select(1)
    workspace.close(1)
    assertEquals(workspace.active.title, "c")
  }

  test("closing the active last tab activates the one before it") {
    val workspace = workspaceOf("a", "b")
    workspace.close(1)
    assertEquals(workspace.active.title, "a")
  }

  test("closing the only tab leaves a blank untitled one") {
    val workspace = workspaceOf("a")
    workspace.close(0)
    assertEquals(titles(workspace), Vector("untitled"))
    assertEquals(workspace.active.jsonText, AppState.normalizeNewlines(Samples.blank))
    assert(!workspace.active.isDirty)
  }

  test("each tab keeps its own document, zoom and undo history") {
    val workspace = workspaceOf("a", "b")
    workspace.active.zoom = 2.0
    workspace.active.applyEdit(0, WaveEditor.setCycle(_, 0, '1'))
    workspace.select(0)
    assertEquals(workspace.active.diagram.get.signals.head.wave, "01")
    assertEquals(workspace.active.zoom, 1.0)
    assert(!workspace.active.canUndo)
    workspace.select(1)
    assertEquals(workspace.active.diagram.get.signals.head.wave, "11")
    assert(workspace.active.canUndo)
  }

  // --- unsaved changes -----------------------------------------------------

  test("a freshly opened document is not dirty") {
    assert(!doc("a").isDirty)
    assert(!workspaceOf("a", "b").anyDirty)
  }

  test("editing marks only that document dirty, and undo clears it again") {
    val workspace = workspaceOf("a", "b")
    workspace.active.applyEdit(0, WaveEditor.setCycle(_, 0, '1'))
    assertEquals(workspace.tabs.map(_.isDirty), Vector(false, true))
    assert(workspace.anyDirty)
    workspace.active.undo()
    assert(!workspace.anyDirty)
  }

  test("typed text counts as a change even when it does not parse") {
    val state = doc("a")
    state.updateFromText("{")
    assert(state.isDirty)
  }

  test("markSaved clears the dirty state for the text that was written") {
    val state = doc("a")
    state.applyEdit(0, WaveEditor.setCycle(_, 0, '1'))
    val written = state.jsonText
    state.applyEdit(0, WaveEditor.setCycle(_, 1, '0'))
    state.markSaved(written)
    assert(state.isDirty)
    state.markSaved()
    assert(!state.isDirty)
  }

  // --- samples and tab strip -----------------------------------------------

  test("every built-in sample and the blank document parse") {
    (Samples.all.map(_.json) :+ Samples.blank).foreach { json =>
      assert(WaveJsonCodec.parse(json).isRight, json)
    }
    assertEquals(Samples.byId("spi").map(_.fileName), Some("spi.json"))
    assertEquals(Samples.byId("nope"), None)
  }

  test("the tab strip marks the active and the dirty tab") {
    val workspace = workspaceOf("a.json", "<b>.json")
    workspace.active.applyEdit(0, WaveEditor.setCycle(_, 0, '1'))
    workspace.select(0)
    val html = TabBar.renderToString(workspace)
    assert(html.contains("""<div class="tab active" data-tab="0""""), html)
    assert(html.contains("""<div class="tab dirty" data-tab="1""""), html)
    assert(html.contains("&lt;b&gt;.json"), html)
    assert(html.contains("""data-close="1""""), html)
  }
}
