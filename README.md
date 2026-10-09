# WaveDrom Plus Studio

An interactive timing diagram studio inspired by **WaveDrom**, implemented in **Scala 3** and **Scala.js** (`scalajs-dom`).

---

## 🚀 Quick Start

### 1. Prerequisites
- **Java**: JDK 17+ (GraalVM or OpenJDK)
- **sbt**: 1.10.x+
- **Node.js**: v18+ (optional, for local static web server)

### 2. Compile
To compile and link the Scala.js project:
```bash
sbt fastLinkJS
```
This generates `target/scala-3.3.4/wavedrom-plus-fastopt/main.js`.

For continuous recompilation while editing code:
```bash
sbt ~fastLinkJS
```

### 3. Test
```bash
sbt test
```
Runs the munit suites under Node.js.

### 4. Launch the Studio

You can launch the studio using any of the following methods:

#### Option A: Direct sbt launch task
```bash
sbt launch
```
*Compiles Scala.js and opens `index.html` in your default browser.*

#### Option B: Local Web Server (Node.js)
```bash
npm start
# or: node server.js
```
*Starts a zero-dependency static server at [http://localhost:3000](http://localhost:3000) and opens your browser.*

#### Option C: PowerShell Script (Windows)
```powershell
./launch.ps1
```

#### Option D: Direct Browser File
You can also open [index.html](index.html) directly in any web browser.

---

## ✏️ Using the Studio

- **Tabs**: each tab is one diagram with its own JSON, zoom and undo history. `+` adds an empty one, `×` closes it, and a dot marks unsaved changes.
- **Open**: the Open… button or Ctrl+O loads one or more `.json` files, each in a new tab. Picking a built-in sample also opens a new tab.
- **Save**: the Save button or Ctrl+S writes the current tab's JSON. In Edge and Chrome it saves back to the opened file (or asks where to save a new one); in other browsers it downloads the file.
- **Resize the panes**: drag the divider between the JSON editor and the waveform. Double-click it to reset.
- **Stack the panes**: the Stack panes / Side by side button puts the JSON editor above the waveform instead of beside it. Each layout remembers its own divider position.
- **Zoom**: use the slider or the − / + buttons, or hold Ctrl and scroll over the waveform. The drawing scales as a whole, and the pane scrolls when it no longer fits.
- **Edit a wave graphically**: click any cycle to open the cycle editor.
  - Pick a state, or press its key (`1 0 p n x z = .`) while the editor is open.
  - Set a node marker (multi-character names are supported) or, on a bus cycle, its label.
  - Insert a hold cycle after the clicked one, or delete the cycle.
  - Click past the end of a short signal to extend it.
- **Undo**: the Undo button, or Ctrl+Z outside the text editor, reverts graphical edits.

Graphical edits are written back into the JSON without touching anything else in the document.

---

## 📁 Project Structure

```
wavedrom-plus/
├── src/
│   ├── main/scala/wavedrom/studio/
│   │   ├── Model.scala              # AST & domain model (WaveState, SignalTrack, WaveDiagram, ...)
│   │   ├── WaveJsonCodec.scala      # WaveJSON parser & serializer
│   │   ├── WaveEditor.scala         # Pure edit operations on a signal (cycles, nodes, bus labels)
│   │   ├── AppState.scala           # State of one open document, edit write-back & undo
│   │   ├── Workspace.scala          # The open documents (tabs)
│   │   ├── Samples.scala            # Built-in sample diagrams
│   │   ├── FileIO.scala             # Opening and saving local files
│   │   ├── TabBar.scala             # Tab strip
│   │   ├── SvgRenderer.scala        # SVG waveform render engine
│   │   ├── CyclePopover.scala       # Cycle editor popover
│   │   ├── SplitPane.scala          # Draggable divider between the panes
│   │   └── WaveDromStudioApp.scala  # Scala.js entry point & UI controller
│   └── test/scala/wavedrom/studio/  # munit suites
├── build.sbt              # sbt configuration (Scala 3.3.4, Scala.js 1.18.2, scalajs-dom, munit)
├── project/
│   ├── build.properties   # sbt version declaration (1.10.7)
│   └── plugins.sbt        # sbt-scalajs plugin definition
├── index.html             # Studio frontend UI (editor, waveform viewer, zoom, context menu)
├── server.js              # Lightweight zero-dependency local HTTP server
├── package.json           # npm convenience commands
└── launch.ps1             # PowerShell one-click compile & launch script
```

---

## 🛠️ Key Enhancements & Fixes Applied

During the setup of the compilation and runtime infrastructure, several issues in the initial AI-generated code were corrected:

1. **Syntax Fixes in Source**:
   - `val dataArr = js.Arrayjs.Any` corrected to `val dataArr = js.Array[js.Any]()`.
   - Broken `replace(""", """)` in `escapeHtml` corrected to proper HTML entity replacement.
2. **SVG Path Coordinate Spacing**:
   - String interpolations like `M $cx$yLow` and `L $x1$y1` lacked whitespace between X and Y coordinates (producing invalid paths like `M 120.071.0`). Added proper spacing across all waveform and edge connectors.
3. **Regex Pattern Fix**:
   - `edgePattern` had `?$$` which caused regex end-of-string matching to fail. Corrected to `?$`.
4. **Group Header Parsing Warning**:
   - Changed `arr(0).isInstanceOf[String]` to `js.typeOf(arr(0)) == "string"` to prevent Scala 3 compiler warnings on `js.Any` runtime types.
5. **Interactive Lifecycle & Main Entrypoint**:
   - Added standard `main(args: Array[String])` overload alongside `@JSExport def main()`.
   - Added DOM ready-state check so live reload and direct injection initialize reliably.
   - Wired interactive context menu (`#cycleMenu`), zoom step width controller, and node selection handlers.
