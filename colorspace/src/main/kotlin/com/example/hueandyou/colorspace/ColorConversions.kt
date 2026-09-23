package com.example.hueandyou.colorspace

import kotlin.math.pow
import kotlin.math.roundToInt
import me.tatarka.google.material.hct.Hct as MaterialHct

// sRGB companding constants (IEC 61966-2-1), and the D65-referenced sRGB<->XYZ matrices,
// shared here so linear RGB and XYZ both live on CIE's usual 0-100 scale (white Y = 100).
private const val SRGB_GAMMA_THRESHOLD = 0.040449936
private const val LINEAR_GAMMA_THRESHOLD = 0.0031308

private val SRGB_TO_XYZ = arrayOf(
    doubleArrayOf(0.41233895, 0.35762064, 0.18051042),
    doubleArrayOf(0.2126, 0.7152, 0.0722),
    doubleArrayOf(0.01932141, 0.11916382, 0.95034478),
)

private val XYZ_TO_SRGB = arrayOf(
    doubleArrayOf(3.2413774792388685, -1.5376652402851851, -0.49885366846268053),
    doubleArrayOf(-0.9691452513005321, 1.8758853451067872, 0.04156585616912061),
    doubleArrayOf(0.05562093689691305, -0.20395524564742123, 1.0571799111220335),
)

private val WHITE_POINT_D65 = doubleArrayOf(95.047, 100.0, 108.883)

// CIE 1976 Lab piecewise constants: epsilon = (6/29)^3, kappa = (29/3)^3.
private const val LAB_EPSILON = 0.008856451679035631
private const val LAB_KAPPA = 903.2962962962963

/** Decodes the RGB channels of an ARGB int (alpha ignored) into linear light. */
fun argbToLinearRgb(argb: Int): LinearRgb = LinearRgb(
    r = channelToLinear((argb shr 16) and 0xFF),
    g = channelToLinear((argb shr 8) and 0xFF),
    b = channelToLinear(argb and 0xFF),
)

/** Encodes linear light RGB back into an opaque ARGB int. */
fun linearRgbToArgb(linear: LinearRgb): Int {
    val r = linearToChannel(linear.r)
    val g = linearToChannel(linear.g)
    val b = linearToChannel(linear.b)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun channelToLinear(channel: Int): Double {
    val normalized = channel / 255.0
    val linear = if (normalized <= SRGB_GAMMA_THRESHOLD) {
        normalized / 12.92
    } else {
        ((normalized + 0.055) / 1.055).pow(2.4)
    }
    return linear * 100.0
}

private fun linearToChannel(value: Double): Int {
    val normalized = value / 100.0
    val srgb = if (normalized <= LINEAR_GAMMA_THRESHOLD) {
        normalized * 12.92
    } else {
        1.055 * normalized.pow(1.0 / 2.4) - 0.055
    }
    return (srgb * 255.0).roundToInt().coerceIn(0, 255)
}

/** Converts linear light RGB to CIELAB (D65). */
fun linearRgbToLab(linear: LinearRgb): Lab {
    val xyz = multiply(SRGB_TO_XYZ, linear.r, linear.g, linear.b)
    val fx = labF(xyz[0] / WHITE_POINT_D65[0])
    val fy = labF(xyz[1] / WHITE_POINT_D65[1])
    val fz = labF(xyz[2] / WHITE_POINT_D65[2])
    return Lab(
        l = 116.0 * fy - 16.0,
        a = 500.0 * (fx - fy),
        b = 200.0 * (fy - fz),
    )
}

/** Converts CIELAB (D65) back to linear light RGB. */
fun labToLinearRgb(lab: Lab): LinearRgb {
    val fy = (lab.l + 16.0) / 116.0
    val fx = fy + lab.a / 500.0
    val fz = fy - lab.b / 200.0
    val x = labInvF(fx) * WHITE_POINT_D65[0]
    val y = labInvF(fy) * WHITE_POINT_D65[1]
    val z = labInvF(fz) * WHITE_POINT_D65[2]
    val linear = multiply(XYZ_TO_SRGB, x, y, z)
    return LinearRgb(linear[0], linear[1], linear[2])
}

private fun multiply(matrix: Array<DoubleArray>, x: Double, y: Double, z: Double): DoubleArray =
    DoubleArray(3) { row -> matrix[row][0] * x + matrix[row][1] * y + matrix[row][2] * z }

private fun labF(t: Double): Double =
    if (t > LAB_EPSILON) t.pow(1.0 / 3.0) else (LAB_KAPPA * t + 16.0) / 116.0

private fun labInvF(f: Double): Double {
    val cubed = f * f * f
    return if (cubed > LAB_EPSILON) cubed else (116.0 * f - 16.0) / LAB_KAPPA
}

/** Converts an ARGB int directly to CIELAB (D65), via linear RGB. */
fun argbToLab(argb: Int): Lab = linearRgbToLab(argbToLinearRgb(argb))

/** Converts CIELAB (D65) directly to an opaque ARGB int, via linear RGB. */
fun labToArgb(lab: Lab): Int = linearRgbToArgb(labToLinearRgb(lab))

/** Converts an ARGB int to HCT, delegating the CAM16/HCT solver to Material Color Utilities. */
fun argbToHct(argb: Int): Hct {
    val hct = MaterialHct.fromInt(argb)
    return Hct(hct.hue, hct.chroma, hct.tone)
}

/** Converts HCT back to an opaque ARGB int, delegating to Material Color Utilities. */
fun hctToArgb(hct: Hct): Int = MaterialHct.from(hct.hue, hct.chroma, hct.tone).toInt()
