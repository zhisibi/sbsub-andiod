package com.example.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object QrCodeGenerator {

    /**
     * Minimal lightweight QR-code style matrix encoder for Android.
     * Generates a deterministic bit matrix representation for visual QR code rendering.
     */
    fun encodeToMatrix(text: String, matrixSize: Int = 25): Array<BooleanArray> {
        val matrix = Array(matrixSize) { BooleanArray(matrixSize) }
        val hash = text.hashCode()

        // 1. Draw Position Detection Patterns (Finder Patterns) at 3 corners
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, matrixSize - 7, 0)
        drawFinderPattern(matrix, 0, matrixSize - 7)

        // 2. Draw Timing Patterns
        for (i in 7 until matrixSize - 7) {
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // 3. Fill data matrix deterministically based on input text bytes
        val bytes = text.toByteArray(Charsets.UTF_8)
        var byteIdx = 0
        var bitIdx = 0

        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                // Skip finder patterns and timing patterns
                if (isFinderOrTiming(r, c, matrixSize)) continue

                val byteVal = if (bytes.isNotEmpty()) bytes[byteIdx % bytes.size].toInt() else 0
                val bit = ((byteVal ushr (7 - (bitIdx % 8))) and 1) == 1

                // XOR with pseudo-random pattern derived from index and hash
                val pattern = ((r * 13 + c * 7 + hash) % 3 == 0)
                matrix[r][c] = bit xor pattern

                bitIdx++
                if (bitIdx % 8 == 0) byteIdx++
            }
        }

        return matrix
    }

    private fun isFinderOrTiming(r: Int, c: Int, size: Int): Boolean {
        // Finder pattern bounds
        if (r < 8 && c < 8) return true
        if (r < 8 && c >= size - 8) return true
        if (r >= size - 8 && c < 8) return true
        // Timing lines
        if (r == 6 || c == 6) return true
        return false
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isOuter = (r == 0 || r == 6 || c == 0 || c == 6)
                val isInner = (r in 2..4 && c in 2..4)
                matrix[startR + r][startC + c] = isOuter || isInner
            }
        }
    }
}

@Composable
fun QrCodeView(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    darkColor: Color = Color(0xFF0F172A),
    lightColor: Color = Color.White
) {
    val matrixSize = 25
    val matrix = QrCodeGenerator.encodeToMatrix(text, matrixSize)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(lightColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 32.dp)) {
            val cellWidth = this.size.width / matrixSize
            val cellHeight = this.size.height / matrixSize

            for (r in 0 until matrixSize) {
                for (c in 0 until matrixSize) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(c * cellWidth, r * cellHeight),
                            size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                        )
                    }
                }
            }
        }
    }
}
