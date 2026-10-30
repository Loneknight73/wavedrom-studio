package wavedrom.studio

case class Sample(id: String, fileName: String, json: String)

// Built-in example diagrams, listed in the toolbar's sample menu
object Samples {

  val full: Sample = Sample(
    "full",
    "full-demo.json",
    """{
  "head": { "text": ["tspan", { "class": "h3" }, "Memory Bus Transaction"], "tick": 0 },
  "signal": [
    { "name": "clk",     "wave": "p..........." },
    { "name": "req",     "wave": "0..1..0.....", "node": "...a..b...." },
    { "name": "ack",     "wave": "0....1..0...", "node": ".....c..d.." },
    {},
    [ "Data Bus",
      { "name": "addr",  "wave": "x.=.=.=....x", "data": ["0x1000", "0x1004", "0x1008"] },
      { "name": "wdata", "wave": "x.===......x", "data": ["D0", "D1", "D2"] },
      { "name": "rdata", "wave": "z.....===..z", "data": ["R0", "R1", "R2"] }
    ]
  ],
  "edge": [
    "a~>c Request Ack",
    "c->b Handshake",
    "b~>d Complete"
  ],
  "foot": { "text": ["tspan", { "class": "muted" }, "Request/acknowledge handshake, then address, write data and read data"] }
}"""
  )

  val all: Vector[Sample] = Vector(
    full,
    Sample(
      "spi",
      "spi.json",
      """{
  "head": { "text": "SPI Mode 0 Transfer", "tock": 1 },
  "signal": [
    { "name": "CS#",  "wave": "10.......1" },
    { "name": "SCLK", "wave": "0.p.p.p.p0" },
    { "name": "MOSI", "wave": "z.2.2.2.2z", "data": ["B3", "B2", "B1", "B0"] },
    { "name": "MISO", "wave": "z.2.2.2.2z", "data": ["D3", "D2", "D1", "D0"] }
  ],
  "foot": { "text": "MSB first; data is sampled on the rising edges of SCLK" }
}"""
    ),
    Sample(
      "i2c",
      "i2c.json",
      """{
  "head": { "text": ["tspan", ["tspan", { "class": "h4" }, "I2C "], ["tspan", { "class": "info" }, "address phase"]], "tick": 0, "every": 2 },
  "signal": [
    { "name": "SCL", "wave": "1..p.p.p.1.", "node": "..a...b..." },
    { "name": "SDA", "wave": "10.2.2.201.", "data": ["A2", "A1", "A0"], "node": ".s.......p" }
  ],
  "edge": [
    "s~>a Start Condition",
    "b~>p Stop Condition"
  ],
  "foot": { "text": "Start and stop are SDA edges while SCL is high" }
}"""
    ),
    Sample(
      "ddr",
      "ddr.json",
      """{
  "head": { "text": ["tspan", { "class": "h4" }, "DDR Read Burst"] },
  "signal": [
    { "name": "CK_t",  "wave": "p......" },
    { "name": "CK_2x", "wave": "HLHLHLH" },
    { "name": "DQS_t", "wave": "z.p.p.z" },
    { "name": "DQ",    "wave": "z.====z", "data": ["Q0", "Q1", "Q2", "Q3"] }
  ],
  "foot": { "text": ["tspan", { "class": "muted" }, "Data is valid on both edges of DQS"], "tock": 0 }
}"""
    ),
    Sample(
      "groups",
      "groups.json",
      """{
  "head": { "text": "Grouped Signals" },
  "signal": [
    [ "Core Signals",
      { "name": "sys_clk", "wave": "p......" },
      { "name": "sys_rst", "wave": "10....." }
    ],
    {},
    [ "Peripheral bus",
      { "name": "valid", "wave": "01.0..." },
      { "name": "ready", "wave": "0.1.0.." },
      { "name": "payload", "wave": "x.=.x..", "data": ["0xCAFE"] }
    ]
  ],
  "foot": { "text": ["tspan", "Groups can be nested; ", ["tspan", { "font-style": "italic" }, "{}"], " adds a blank row"] }
}"""
    ),
    Sample(
      "colors",
      "colors.json",
      """{
  "head": { "text": ["tspan", ["tspan", { "class": "error" }, "Colour"], "-", ["tspan", { "class": "info" }, "coded"], " bus states"] },
  "signal": [
    { "name": "clk",   "wave": "P........", "color": "#3b9dff" },
    { "name": "state", "wave": "2.3.4.5.2", "data": ["IDLE", "REQ", "WAIT", "XFER", "IDLE"] },
    { "name": "burst", "wave": "x.6789=.x", "data": ["B0", "B1", "B2", "B3", "END"] },
    { "name": "irq",   "wave": "0....1.0.", "color": "#f0605a" }
  ],
  "foot": { "text": "Digits 2-9 pick a bus fill; color sets a signal's lines", "tock": 0, "every": 2 }
}"""
    ),
    Sample(
      "edge-arrows",
      "edge-arrows.json",
      """{
  "head": { "text": ["tspan", { "class": "h4" }, "Edge Arrows"], "tick": 0 },
  "signal": [
    { "name": "req",  "wave": "0.1...0..1..", "node": ".a.......e.." },
    { "name": "gnt",  "wave": "0..1....0..1", "node": "....b......f" },
    { "name": "data", "wave": "x.=.=.=.=.=.", "data": ["A", "B", "C", "D", "E"], "node": "......c....i" },
    { "name": "addr", "wave": "1..0..1..0..", "node": "..g....d..h." },
    { "name": "done", "wave": "0...1...0..1", "node": ".I...J..j..." }
  ],
  "edge": [
    "a~b t1", "c-~a t2", "c-~>d time 3", "d~-e", "e~>f", "f->g",
    "g-~>h", "h~>i some text", "h~->j",
    "b-|a t1", "a-|c t2", "b-|-c t3", "c-|->e t4", "e-|>f more text",
    "e|->d t6", "c-g", "f-h", "g<->h 3 ms", "I+J 5 ms"
  ],
  "foot": { "text": ["tspan", "Splines ", ["tspan", { "class": "info" }, "~ -~ ~-"], ", elbows ", ["tspan", { "class": "info" }, "-| |- -|-"], ", lines and the ", ["tspan", { "class": "info" }, "+"], " measurement"] }
}"""
    )
  )

  // Starting point for a new, untitled document
  val blank: String = """{
  "signal": [
    { "name": "clk", "wave": "p......." },
    { "name": "sig", "wave": "0......." }
  ]
}"""

  def byId(id: String): Option[Sample] = all.find(_.id == id)
}
