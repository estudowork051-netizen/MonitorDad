package com.dohguard.shared.remote

/**
 * Gerador puro em Kotlin de matriz QR Code (ISO/IEC 18004, modo Byte, nível de correção L, versões 1..8).
 * Não depende de bibliotecas externas nem de APIs Android, podendo ser renderizado via Compose Canvas
 * e validado diretamente em testes unitários JVM.
 */
object QrCodeGenerator {

    data class QrMatrix(
        val version: Int,
        val size: Int,
        val modules: Array<BooleanArray>
    ) {
        fun isDark(x: Int, y: Int): Boolean = modules[y][x]
    }

    // Capacidade máxima de bytes de dados (EC Level L, Byte mode) para versões 1..8
    private val DATA_CODEWORDS = intArrayOf(0, 19, 34, 55, 80, 108, 136, 156, 194)
    private val EC_CODEWORDS_PER_BLOCK = intArrayOf(0, 7, 10, 15, 20, 26, 18, 20, 24)
    private val NUM_BLOCKS = intArrayOf(0, 1, 1, 1, 1, 1, 2, 2, 2)

    private val ALIGNMENT_POSITIONS = arrayOf(
        intArrayOf(),
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42)
    )

    // GF(256) log / antilog com polinômio primitivo 0x11D
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    fun generate(text: String): QrMatrix {
        val dataBytes = text.toByteArray(Charsets.UTF_8)
        val version = selectVersion(dataBytes.size)
        val size = 17 + 4 * version

        val totalDataCodewords = DATA_CODEWORDS[version]
        val ecPerBlock = EC_CODEWORDS_PER_BLOCK[version]
        val numBlocks = NUM_BLOCKS[version]

        val bitStream = encodeDataBits(dataBytes, totalDataCodewords)
        val codewords = bitsToBytes(bitStream, totalDataCodewords)
        val interleaved = interleaveWithEcc(codewords, numBlocks, ecPerBlock)

        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(version, size, modules, isFunction)
        placeDataBits(interleaved, size, modules, isFunction)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            val candidate = Array(size) { y -> modules[y].clone() }
            applyMask(mask, size, candidate, isFunction)
            drawFormatBits(mask, size, candidate)
            val penalty = calculatePenalty(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
        }

        applyMask(bestMask, size, modules, isFunction)
        drawFormatBits(bestMask, size, modules)
        return QrMatrix(version = version, size = size, modules = modules)
    }

    private fun selectVersion(byteLength: Int): Int {
        for (v in 1..8) {
            // Byte mode overhead: 4 bits mode + 8 bits count + 4 bits terminator <= 2 bytes
            val maxPayloadBytes = DATA_CODEWORDS[v] - 2
            if (byteLength <= maxPayloadBytes) return v
        }
        throw IllegalArgumentException("Payload muito longo para QR Code v1..v8 (${byteLength} bytes)")
    }

    private fun encodeDataBits(data: ByteArray, totalDataCodewords: Int): List<Int> {
        val bits = ArrayList<Int>(totalDataCodewords * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }
        // Mode: Byte = 0100
        appendBits(0b0100, 4)
        // Character count (8 bits for v1..v9)
        appendBits(data.size, 8)
        for (b in data) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val capacityBits = totalDataCodewords * 8
        val terminator = minOf(4, capacityBits - bits.size)
        repeat(terminator) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        var padIndex = 0
        val padBytes = intArrayOf(0xEC, 0x11)
        while (bits.size < capacityBits) {
            appendBits(padBytes[padIndex % 2], 8)
            padIndex++
        }
        return bits
    }

    private fun bitsToBytes(bits: List<Int>, count: Int): IntArray {
        val out = IntArray(count)
        for (i in 0 until count) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + b]
            }
            out[i] = v
        }
        return out
    }

    private fun interleaveWithEcc(data: IntArray, numBlocks: Int, ecPerBlock: Int): IntArray {
        val blockSize = data.size / numBlocks
        val remainder = data.size % numBlocks
        val dataBlocks = ArrayList<IntArray>(numBlocks)
        val ecBlocks = ArrayList<IntArray>(numBlocks)
        val gen = rsGeneratorPoly(ecPerBlock)

        var offset = 0
        for (i in 0 until numBlocks) {
            val len = blockSize + if (i >= numBlocks - remainder) 1 else 0
            val block = data.copyOfRange(offset, offset + len)
            offset += len
            dataBlocks.add(block)
            ecBlocks.add(rsComputeRemainder(block, gen, ecPerBlock))
        }

        val result = IntArray(data.size + numBlocks * ecPerBlock)
        var idx = 0
        val maxDataLen = dataBlocks.maxOf { it.size }
        for (col in 0 until maxDataLen) {
            for (b in 0 until numBlocks) {
                if (col < dataBlocks[b].size) {
                    result[idx++] = dataBlocks[b][col]
                }
            }
        }
        for (col in 0 until ecPerBlock) {
            for (b in 0 until numBlocks) {
                result[idx++] = ecBlocks[b][col]
            }
        }
        return result
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
            }
            poly = next
        }
        return poly
    }

    private fun rsComputeRemainder(data: IntArray, gen: IntArray, degree: Int): IntArray {
        val rem = IntArray(degree)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, degree - 1)
            rem[degree - 1] = 0
            if (factor != 0) {
                for (i in 0 until degree) {
                    rem[i] = rem[i] xor gfMul(gen[i + 1], factor)
                }
            }
        }
        return rem
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun drawFunctionPatterns(
        version: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        fun setFunc(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = dark
                isFunction[y][x] = true
            }
        }

        // Finder patterns + separators
        fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        setFunc(x, y, dist != 2 && dist != 4)
                    }
                }
            }
        }
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)

        // Alignment patterns
        val pos = ALIGNMENT_POSITIONS[version]
        for (ay in pos) {
            for (ax in pos) {
                if ((ax == 6 && ay == 6) ||
                    (ax == 6 && ay == size - 7) ||
                    (ax == size - 7 && ay == 6)
                ) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setFunc(ax + dx, ay + dy, dist != 1)
                    }
                }
            }
        }

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            setFunc(i, 6, dark)
            setFunc(6, i, dark)
        }

        // Dark module
        setFunc(8, size - 8, true)

        // Reserve format info areas
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in size - 8 until size) {
            isFunction[8][i] = true
            isFunction[i][8] = true
        }
        if (version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    isFunction[size - 11 + j][i] = true
                    isFunction[i][size - 11 + j] = true
                }
            }
            drawVersionBits(version, size, modules)
        }
    }

    private fun drawVersionBits(version: Int, size: Int, modules: Array<BooleanArray>) {
        var rem = version
        for (i in 0 until 12) {
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val dark = ((bits ushr i) and 1) != 0
            val a = size - 11 + i % 3
            val b = i / 3
            modules[a][b] = dark
            modules[b][a] = dark
        }
    }

    private fun placeDataBits(
        codewords: IntArray,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        var bitIndex = 0
        val totalBits = codewords.size * 8
        var upward = true
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                val y = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    if (!isFunction[y][x]) {
                        val dark = if (bitIndex < totalBits) {
                            ((codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[y][x] = dark
                        bitIndex++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunction[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> (x * y) % 2 + (x * y) % 3 == 0
                    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                    7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                    else -> false
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun drawFormatBits(mask: Int, size: Int, modules: Array<BooleanArray>) {
        // EC Level L = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        for (i in 0 until 10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) modules[8][i] = ((bits ushr i) and 1) != 0
        modules[8][7] = ((bits ushr 6) and 1) != 0
        modules[8][8] = ((bits ushr 7) and 1) != 0
        modules[7][8] = ((bits ushr 8) and 1) != 0
        for (i in 9 until 15) modules[14 - i][8] = ((bits ushr i) and 1) != 0

        for (i in 0 until 8) modules[size - 1 - i][8] = ((bits ushr i) and 1) != 0
        for (i in 8 until 15) modules[8][size - 15 + i] = ((bits ushr i) and 1) != 0
        modules[size - 8][8] = true
    }

    private fun calculatePenalty(modules: Array<BooleanArray>, size: Int): Int {
        var penalty = 0
        // Horizontal & vertical runs
        for (y in 0 until size) {
            var runColor = modules[y][0]
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = modules[0][x]
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        // 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (modules[y][x + 1] == c && modules[y + 1][x] == c && modules[y + 1][x + 1] == c) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}
