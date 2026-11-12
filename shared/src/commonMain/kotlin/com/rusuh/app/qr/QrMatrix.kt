/*
 * QR Code encoder (byte mode, versions 1-40), pure Kotlin for commonMain.
 *
 * Kotlin port, reduced to byte mode and trimmed, of "QR Code generator library" by Project Nayuki
 * (https://www.nayuki.com/page/qr-code-generator-library): version selection, Reed-Solomon, function
 * patterns, masking and penalty logic.
 *
 * Copyright (c) Project Nayuki. (MIT License)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in all copies or
 *   substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or implied, including
 *   but not limited to the warranties of merchantability, fitness for a particular purpose and
 *   noninfringement. In no event shall the authors or copyright holders be liable for any claim,
 *   damages or other liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the Software.
 */
@file:Suppress("MagicNumber", "TooManyFunctions", "CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")

package com.rusuh.app.qr

import kotlin.math.abs

/** QR error correction level. Higher levels tolerate more damage and hold less data. */
enum class QrEcc(internal val formatBits: Int) {
    Low(1),
    Medium(0),
    Quartile(3),
    High(2),
}

/** The payload does not fit in a version 40 symbol at the requested error correction level. */
class QrPayloadTooLongException(val byteLength: Int, val maxBytes: Int) :
    IllegalArgumentException("QR payload of $byteLength bytes exceeds the $maxBytes byte capacity")

/**
 * An immutable QR Code symbol: a square grid of dark/light modules without the quiet zone.
 *
 * Content is encoded as UTF-8 bytes in byte mode using the smallest version that fits. [encode] never
 * logs or retains the payload text; only the resulting modules are kept.
 */
class QrMatrix private constructor(
    /** Version 1..40; the side length is `version * 4 + 17`. */
    val version: Int,
    val ecc: QrEcc,
    /** The mask pattern 0..7 chosen by the lowest penalty score. */
    val mask: Int,
    private val modules: BooleanArray,
) {
    /** Side length in modules. */
    val size: Int = version * 4 + 17

    /** True when the module at column [x], row [y] is dark; false when light or out of range. */
    fun isDark(
        x: Int,
        y: Int,
    ): Boolean = x in 0 until size && y in 0 until size && modules[y * size + x]

    companion object {
        const val MIN_VERSION = 1
        const val MAX_VERSION = 40

        /** Most payload bytes a symbol can hold at [ecc] (version 40). */
        fun maxBytes(ecc: QrEcc = QrEcc.Medium): Int = byteCapacity(MAX_VERSION, ecc)

        /** Payload bytes that fit in [version] at [ecc] in byte mode. */
        fun byteCapacity(
            version: Int,
            ecc: QrEcc = QrEcc.Medium,
        ): Int {
            require(version in MIN_VERSION..MAX_VERSION) { "version out of range: $version" }
            return (numDataCodewords(version, ecc) * 8 - 4 - charCountBits(version)) / 8
        }

        /** Smallest version that holds [byteLength] payload bytes at [ecc], or null if none does. */
        fun versionFor(
            byteLength: Int,
            ecc: QrEcc = QrEcc.Medium,
        ): Int? = (MIN_VERSION..MAX_VERSION).firstOrNull { byteLength <= byteCapacity(it, ecc) }

        /**
         * Encodes [payload] (UTF-8, byte mode) at [ecc], default level M.
         *
         * @throws QrPayloadTooLongException when it exceeds the version 40 capacity.
         */
        fun encode(
            payload: String,
            ecc: QrEcc = QrEcc.Medium,
        ): QrMatrix {
            val bytes = payload.encodeToByteArray()
            val version = versionFor(bytes.size, ecc) ?: throw QrPayloadTooLongException(bytes.size, maxBytes(ecc))
            val data = dataCodewords(bytes, version, ecc)
            val all = addEccAndInterleave(data, version, ecc)
            return Builder(version, ecc).build(all)
        }

        private fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

        private fun numRawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val numAlign = version / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun numDataCodewords(
            version: Int,
            ecc: QrEcc,
        ): Int =
            numRawDataModules(version) / 8 -
                ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version] * NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]

        private fun dataCodewords(
            bytes: ByteArray,
            version: Int,
            ecc: QrEcc,
        ): ByteArray {
            val capacityBits = numDataCodewords(version, ecc) * 8
            val bits = ArrayList<Boolean>(capacityBits)

            fun append(
                value: Int,
                length: Int,
            ) {
                for (i in length - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
            }
            append(MODE_BYTE, 4)
            append(bytes.size, charCountBits(version))
            for (b in bytes) append(b.toInt() and 0xFF, 8)
            append(0, minOf(4, capacityBits - bits.size)) // terminator
            append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacityBits) {
                append(pad, 8)
                pad = pad xor (0xEC xor 0x11)
            }
            val result = ByteArray(bits.size / 8)
            for (i in bits.indices) {
                if (bits[i]) result[i ushr 3] = (result[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            }
            return result
        }

        private fun addEccAndInterleave(
            data: ByteArray,
            version: Int,
            ecc: QrEcc,
        ): ByteArray {
            val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]
            val blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version]
            val rawCodewords = numRawDataModules(version) / 8
            val numShortBlocks = numBlocks - rawCodewords % numBlocks
            val shortBlockLen = rawCodewords / numBlocks

            val blocks = ArrayList<ByteArray>(numBlocks)
            val divisor = reedSolomonDivisor(blockEccLen)
            var k = 0
            for (i in 0 until numBlocks) {
                val datLen = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
                val dat = data.copyOfRange(k, k + datLen)
                k += datLen
                val eccBytes = reedSolomonRemainder(dat, divisor)
                val block = ByteArray(shortBlockLen + 1)
                dat.copyInto(block)
                eccBytes.copyInto(block, block.size - blockEccLen)
                blocks.add(block)
            }

            val result = ByteArray(rawCodewords)
            var r = 0
            for (i in 0 until shortBlockLen + 1) {
                for (j in blocks.indices) {
                    // Skip the padding byte in short blocks.
                    if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) result[r++] = blocks[j][i]
                }
            }
            return result
        }

        private fun reedSolomonDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) {
                    result[j] = multiply(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = multiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonRemainder(
            data: ByteArray,
            divisor: ByteArray,
        ): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                result.copyInto(result, 0, 1, result.size)
                result[result.size - 1] = 0
                for (i in result.indices) {
                    result[i] = (result[i].toInt() xor multiply(divisor[i].toInt() and 0xFF, factor)).toByte()
                }
            }
            return result
        }

        /** Product in GF(2^8) modulo 0x11D. */
        private fun multiply(
            x: Int,
            y: Int,
        ): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }

        private const val MODE_BYTE = 0x4

        // ISO/IEC 18004 tables, indexed [ecc.ordinal][version]; index 0 is unused.
        private val ECC_CODEWORDS_PER_BLOCK =
            arrayOf(
                intArrayOf(
                    -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28,
                    28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
                ),
                intArrayOf(
                    -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26,
                    26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
                ),
                intArrayOf(
                    -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26,
                    30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
                ),
                intArrayOf(
                    -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26,
                    28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
                ),
            )

        private val NUM_ERROR_CORRECTION_BLOCKS =
            arrayOf(
                intArrayOf(
                    -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7,
                    8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25,
                ),
                intArrayOf(
                    -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14,
                    16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
                ),
                intArrayOf(
                    -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21,
                    20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68,
                ),
                intArrayOf(
                    -1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25,
                    25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81,
                ),
            )
    }

    /** Mutable working grid: draws function patterns, places codewords, picks the best mask. */
    private class Builder(private val version: Int, private val ecc: QrEcc) {
        private val size = version * 4 + 17
        private val modules = BooleanArray(size * size)
        private val isFunction = BooleanArray(size * size)

        fun build(allCodewords: ByteArray): QrMatrix {
            drawFunctionPatterns()
            drawCodewords(allCodewords)
            var bestMask = 0
            var minPenalty = Int.MAX_VALUE
            for (m in 0 until 8) {
                applyMask(m)
                drawFormatBits(m)
                val penalty = penaltyScore()
                if (penalty < minPenalty) {
                    bestMask = m
                    minPenalty = penalty
                }
                applyMask(m) // XOR undoes it
            }
            applyMask(bestMask)
            drawFormatBits(bestMask)
            return QrMatrix(version, ecc, bestMask, modules.copyOf())
        }

        private fun setFunction(
            x: Int,
            y: Int,
            dark: Boolean,
        ) {
            modules[y * size + x] = dark
            isFunction[y * size + x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                setFunction(6, i, i % 2 == 0)
                setFunction(i, 6, i % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)

            val pos = alignmentPositions()
            val n = pos.size
            for (i in 0 until n) {
                for (j in 0 until n) {
                    val corner = (i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)
                    if (!corner) drawAlignment(pos[i], pos[j])
                }
            }
            drawFormatBits(0) // reserve the area; overwritten once the mask is known
            drawVersion()
        }

        private fun drawFormatBits(mask: Int) {
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412

            for (i in 0..5) setFunction(8, i, bit(bits, i))
            setFunction(8, 7, bit(bits, 6))
            setFunction(8, 8, bit(bits, 7))
            setFunction(7, 8, bit(bits, 8))
            for (i in 9..14) setFunction(14 - i, 8, bit(bits, i))

            for (i in 0..7) setFunction(size - 1 - i, 8, bit(bits, i))
            for (i in 8..14) setFunction(8, size - 15 + i, bit(bits, i))
            setFunction(8, size - 8, true) // always dark
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val b = bit(bits, i)
                val a = size - 11 + i % 3
                val c = i / 3
                setFunction(a, c, b)
                setFunction(c, a, b)
            }
        }

        private fun drawFinder(
            cx: Int,
            cy: Int,
        ) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(abs(dx), abs(dy))
                    val xx = cx + dx
                    val yy = cy + dy
                    if (xx in 0 until size && yy in 0 until size) setFunction(xx, yy, dist != 2 && dist != 4)
                }
            }
        }

        private fun drawAlignment(
            cx: Int,
            cy: Int,
        ) {
            for (dy in -2..2) {
                for (dx in -2..2) {
                    setFunction(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
                }
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y * size + x] && i < data.size * 8) {
                            modules[y * size + x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val invert =
                        when (mask) {
                            0 -> (x + y) % 2 == 0
                            1 -> y % 2 == 0
                            2 -> x % 3 == 0
                            3 -> (x + y) % 3 == 0
                            4 -> (x / 3 + y / 2) % 2 == 0
                            5 -> x * y % 2 + x * y % 3 == 0
                            6 -> (x * y % 2 + x * y % 3) % 2 == 0
                            else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                        }
                    if (invert && !isFunction[y * size + x]) modules[y * size + x] = !modules[y * size + x]
                }
            }
        }

        /** ISO/IEC 18004 penalty rules N1..N4; lower is better. Deterministic for a given grid. */
        private fun penaltyScore(): Int {
            var result = 0
            for (y in 0 until size) result += linePenalty { modules[y * size + it] }
            for (x in 0 until size) result += linePenalty { modules[it * size + x] }

            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = modules[y * size + x]
                    if (c == modules[y * size + x + 1] && c == modules[(y + 1) * size + x] &&
                        c == modules[(y + 1) * size + x + 1]
                    ) {
                        result += PENALTY_N2
                    }
                }
            }

            val dark = modules.count { it }
            val total = size * size
            val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
            return result + k * PENALTY_N4
        }

        /** Runs (N1) and finder-like patterns (N3) along one row or column. */
        private fun linePenalty(at: (Int) -> Boolean): Int {
            var result = 0
            var runColor = false
            var runLength = 0
            val history = IntArray(7)
            for (i in 0 until size) {
                if (at(i) == runColor) {
                    runLength++
                    if (runLength == 5) {
                        result += PENALTY_N1
                    } else if (runLength > 5) {
                        result++
                    }
                } else {
                    addHistory(runLength, history)
                    if (!runColor) result += countPatterns(history) * PENALTY_N3
                    runColor = at(i)
                    runLength = 1
                }
            }
            var len = runLength
            if (runColor) {
                addHistory(len, history)
                len = 0
            }
            len += size
            addHistory(len, history)
            return result + countPatterns(history) * PENALTY_N3
        }

        private fun countPatterns(h: IntArray): Int {
            val n = h[1]
            val core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n
            return (if (core && h[0] >= n * 4 && h[6] >= n) 1 else 0) +
                (if (core && h[6] >= n * 4 && h[0] >= n) 1 else 0)
        }

        private fun addHistory(
            runLength: Int,
            h: IntArray,
        ) {
            val len = if (h[0] == 0) runLength + size else runLength // light border before the first run
            h.copyInto(h, 1, 0, h.size - 1)
            h[0] = len
        }

        private fun bit(
            value: Int,
            index: Int,
        ): Boolean = ((value ushr index) and 1) != 0

        private companion object {
            const val PENALTY_N1 = 3
            const val PENALTY_N2 = 3
            const val PENALTY_N3 = 40
            const val PENALTY_N4 = 10
        }
    }
}
