# The JSON format of Wavedrom Studio

Wavedrom Studio documents are JSON files. Their format is very similar to the WaveJSON format of [WaveDrom](https://wavedrom.com/): a `signal` list of signals, each with a `wave` string, and an optional `edge` list. The two share most of their notation, and a simple diagram may work in both.

There is no attempt at compatibility, though. Wavedrom Studio supports only part of what WaveDrom does, may interpret some of it differently, and adds a few things of its own. Do not assume that a file written here will work in WaveDrom, or the other way round. [Where it differs](#where-it-differs-from-wavedrom) lists the differences known today. For WaveDrom's own format, see the [WaveDrom tutorial](https://wavedrom.com/tutorial.html).

This page describes what Wavedrom Studio understands.

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
| `2` … `9` | Bus value in one of eight colours, taken from the ones WaveDrom uses. It takes the next label from `data`. |
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

`color` is an addition of Wavedrom Studio. WaveDrom has no such key, and nothing here says how other tools treat it. It replaces black for every line of the signal, clock arrows included. Bus fills keep the colour of their digit.

The value is a hex colour (`#f60`, `#ff6600`) or a CSS colour name (`orange`). An invalid value is ignored.

```json
{ "name": "irq", "wave": "0..1.0..", "color": "#f0605a" }
```

## Nodes and edges

`node` places named markers on cycles, which `edge` then connects with arrows.

In `node`, each `.` is an empty cycle, and any other run of characters is one marker on one cycle: in `"..a.b"` the marker `a` is on cycle 2 and `b` on cycle 4. For names longer than one character, which is an addition of Wavedrom Studio, separate the parts with spaces: `"..req_start ack"` puts `req_start` on cycle 2 and `ack` on cycle 3.

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

Keys the studio does not use, such as `head`, `foot` and `config` at the top level, and `period` and `phase` on a signal, are kept in the document when it is edited and saved, but they have no effect on the drawing.

## Where it differs from WaveDrom

This list covers what is known today. It is not a complete comparison, and WaveDrom itself changes over time.

- **Added here:** the `color` key of a signal, and multi-character node names separated by spaces.
- **Not supported:** the keys listed under [Other keys](#other-keys), which are kept but do not affect the drawing. Wave characters that are not in the table above, such as `|` for a gap, or `u` and `d`, are drawn as low. Edge arrows other than the five listed above are not drawn.
- **Possibly different:** how wide a cycle is, how labels are placed, and the finer details of the drawing follow this program's own renderer, so a diagram can look different here and in WaveDrom.
