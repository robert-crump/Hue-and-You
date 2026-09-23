package com.example.hueandyou.colorspace

/** A color in CIELAB (D65 white point): [l] in `[0, 100]`, [a] and [b] unbounded chroma axes. */
data class Lab(val l: Double, val a: Double, val b: Double)
