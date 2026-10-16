# WaveJSON in Wavedrom Studio

Wavedrom Studio reads the WaveJSON format of [WaveDrom](https://wavedrom.com/). This page lists the parts of the format the studio draws, and the one key it adds. For the full format, see the [WaveDrom tutorial](https://wavedrom.com/tutorial.html).

A document is a JSON object with a `signal` list and an optional `edge` list:

```json
{
  "signal": [
    { "name": "clk",  "wave": "p......." },
    { "name": "data", "wave": "x.=.=.x.", "data": ["A", "B"], "node": "..a.b..." }
  ],
  "edge": ["a~>b next"]
}
```

## Signals

Each entry of `signal` is one of three things:

| Entry | Meaning |
|---|---|
| `{ "name": ..., "wave": ... }` | A signal, one row of the diagram. |
| `{}` | A blank row, used as a spacer. |
| `[ "Group name", entry, entry, ... ]` | A group. A brace with its name is drawn to the left of its signals. Groups can be nested. |

A signal can have these keys:

| Key | Meaning |
|---|---|
| `name` | The name shown left of the wave. |
| `wave` | The wave, one character per cycle (see below). |
| `data` | The labels of the bus cycles, in order. Either a list, `["A", "B"]`, or a string of words separated by spaces, `"A B"`. |
| `node` | Node markers, one character per cycle (see [Nodes and edges](#nodes-and-edges)). |
| `color` | The colour of the signal's lines. This key is a Wavedrom Studio extension (see [Signal colour](#signal-colour)). |

## Wave characters

Each character of `wave` is one cycle on the grid.

| Character | State |
|---|---|
| `0` `l` | Low |
| `1` `h` | High |
| `L` | Low, with an arrow on the falling edge that leads to it |
| `H` | High, with an arrow on the rising edge that leads to it |
| `p` | Clock, rising edge at the start of the cycle |
| `n` | Clock, falling edge at the start of the cycle |
| `P` | Clock like `p`, with an arrow on each rising edge |
| `N` | Clock like `n`, with an arrow on each falling edge |
| `x` | Undefined, drawn hatched |
| `z` | High impedance, drawn as a line at mid height |
| `=` | Bus value, white. It takes the next label from `data`. |
| `2` … `9` | Bus value in one of WaveDrom's eight colours. It takes the next label from `data`. |
| `.` | Hold: the previous cycle's state continues |

Upper and lower case mean the same thing, except for `P`, `N`, `H` and `L`, which carry arrows. An unknown character is drawn as low.

A bus label is centred over its value and the hold cycles that follow it, so `"=..="` draws the first label over three cycles.

### Double-edge clocks

`HLHL…` draws a clock that is active on both edges, with an arrow on every edge. Each grid cycle is then half a clock period, so the cycle numbers, markers and edges all count half periods.

```json
{ "name": "ddr_clk", "wave": "HLHLHLHL" }
```

### Bus colours

| Digit | `=` | `2` | `3` | `4` | `5` | `6` | `7` | `8` | `9` |
|---|---|---|---|---|---|---|---|---|---|
| Fill | white | white | yellow | orange | blue | cyan | green | purple | red |

## Signal colour

`color` is not part of standard WaveDrom, which ignores it, so a document that uses it still renders elsewhere. It replaces black for every line of the signal, clock arrows included. Bus fills keep the colour of their digit.

The value is a hex colour (`#f60`, `#ff6600`) or a CSS colour name (`orange`). An invalid value is ignored.

```json
{ "name": "irq", "wave": "0..1.0..", "color": "#f0605a" }
```

## Nodes and edges

`node` places named markers on cycles, which `edge` then connects with arrows.

In `node`, each `.` is an empty cycle, and any other run of characters is one marker on one cycle: in `"..a.b"` the marker `a` is on cycle 2 and `b` on cycle 4. For names longer than one character, separate the parts with spaces: `"..req_start ack"` puts `req_start` on cycle 2 and `ack` on cycle 3.

Each entry of `edge` is a string `from<arrow>to`, followed by an optional label:

| Arrow | Drawing |
|---|---|
| `a->b` | Straight line with an arrowhead at `b` |
| `a<-b` | Straight line with an arrowhead at `a` |
| `a<->b` | Straight line with arrowheads at both ends |
| `a~>b` | Curve with an arrowhead at `b` |
| `a<~>b` | Curve with arrowheads at both ends |

```json
"edge": ["a~>b Request", "b->c 3 cycles"]
```

The label, if any, is drawn halfway along the line.

## Other keys

Other top-level keys, such as `head`, `foot` and `config`, are kept in the document when it is edited and saved, but the studio does not draw them yet.
