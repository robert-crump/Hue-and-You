package com.example.hueandyou.colorspace

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// 25^7, used by both the chroma weighting factor G and the rotation term RC.
private const val POW25_7 = 6103515625.0

/**
 * Perceptual color difference (ΔE00) between two CIELAB colors, per Sharma, Wu & Dalal
 * (2005), "The CIEDE2000 color-difference formula".
 */
fun ciede2000(lab1: Lab, lab2: Lab): Double {
    val c1 = hypot(lab1.a, lab1.b)
    val c2 = hypot(lab2.a, lab2.b)
    val cBar7 = ((c1 + c2) / 2.0).pow(7)
    val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + POW25_7)))

    val a1Prime = lab1.a * (1.0 + g)
    val a2Prime = lab2.a * (1.0 + g)
    val c1Prime = hypot(a1Prime, lab1.b)
    val c2Prime = hypot(a2Prime, lab2.b)
    val h1Prime = hueAngleDegrees(a1Prime, lab1.b)
    val h2Prime = hueAngleDegrees(a2Prime, lab2.b)

    val deltaLPrime = lab2.l - lab1.l
    val deltaCPrime = c2Prime - c1Prime
    val cPrimeProduct = c1Prime * c2Prime
    val deltahPrime = when {
        cPrimeProduct == 0.0 -> 0.0
        abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
        h2Prime - h1Prime > 180.0 -> h2Prime - h1Prime - 360.0
        else -> h2Prime - h1Prime + 360.0
    }
    val deltaHPrime = 2.0 * sqrt(cPrimeProduct) * sin(Math.toRadians(deltahPrime / 2.0))

    val lBarPrime = (lab1.l + lab2.l) / 2.0
    val cBarPrime = (c1Prime + c2Prime) / 2.0
    val hBarPrime = when {
        cPrimeProduct == 0.0 -> h1Prime + h2Prime
        abs(h1Prime - h2Prime) <= 180.0 -> (h1Prime + h2Prime) / 2.0
        h1Prime + h2Prime < 360.0 -> (h1Prime + h2Prime + 360.0) / 2.0
        else -> (h1Prime + h2Prime - 360.0) / 2.0
    }

    val t = 1.0 -
        0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
        0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
        0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
        0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

    val deltaTheta = 30.0 * exp(-((hBarPrime - 275.0) / 25.0).pow(2))
    val cBarPrime7 = cBarPrime.pow(7)
    val rc = 2.0 * sqrt(cBarPrime7 / (cBarPrime7 + POW25_7))
    val sl = 1.0 + (0.015 * (lBarPrime - 50.0).pow(2)) / sqrt(20.0 + (lBarPrime - 50.0).pow(2))
    val sc = 1.0 + 0.045 * cBarPrime
    val sh = 1.0 + 0.015 * cBarPrime * t
    val rt = -sin(Math.toRadians(2.0 * deltaTheta)) * rc

    val lTerm = deltaLPrime / sl
    val cTerm = deltaCPrime / sc
    val hTerm = deltaHPrime / sh
    return sqrt(lTerm.pow(2) + cTerm.pow(2) + hTerm.pow(2) + rt * cTerm * hTerm)
}

private fun hueAngleDegrees(aPrime: Double, b: Double): Double {
    if (aPrime == 0.0 && b == 0.0) return 0.0
    val degrees = Math.toDegrees(atan2(b, aPrime))
    return if (degrees < 0.0) degrees + 360.0 else degrees
}
