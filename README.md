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

### 3. Launch the Studio

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
You can also open [index.html](file:///c:/Users/lanfr/VM%20shared/scala-projects/wavedrom-plus/index.html) directly in any web browser.

---

## 📁 Project Structure

```
wavedrom-plus/
├── WaveDromStudio.scala   # Core Scala.js application (AST, JSON codec, SVG renderer, UI controller)
├── build.sbt              # sbt configuration (Scala 3.3.4, Scala.js 1.18.2, scalajs-dom)
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
