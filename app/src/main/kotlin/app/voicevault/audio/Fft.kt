package app.voicevault.audio

/**
 * FFT radix-2 iterativa, in place. Serve solo per la stima del peso vocale
 * (inclinazione spettrale e H1-H2), non e' sul percorso critico dell'audio.
 */
class Fft(val size: Int) {

    init {
        require(size > 0 && (size and (size - 1)) == 0) { "La dimensione della FFT deve essere una potenza di 2" }
    }

    private val cosTable = FloatArray(size / 2) { Math.cos(2.0 * Math.PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { Math.sin(2.0 * Math.PI * it / size).toFloat() }

    /** Finestra di Hann, precalcolata. */
    val window = FloatArray(size) { (0.5 - 0.5 * Math.cos(2.0 * Math.PI * it / (size - 1))).toFloat() }

    fun transform(re: FloatArray, im: FloatArray) {
        require(re.size == size && im.size == size)

        // Inversione dei bit.
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= size) {
            val step = size / len
            var i = 0
            while (i < size) {
                var k = 0
                for (m in i until i + len / 2) {
                    val n = m + len / 2
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val tr = re[n] * wr - im[n] * wi
                    val ti = re[n] * wi + im[n] * wr
                    re[n] = re[m] - tr
                    im[n] = im[m] - ti
                    re[m] += tr
                    im[m] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}
