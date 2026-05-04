package com.mimo.ttsreader.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {

    fun pcm16ToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        buffer.putInt(totalSize)
        buffer.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        buffer.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }

    fun wavToMp3Simple(wavData: ByteArray): ByteArray {
        return wavData
    }

    fun addSilenceToWav(wavData: ByteArray, silenceMs: Int): ByteArray {
        if (silenceMs <= 0) return wavData

        val sampleRate = 24000
        val channels = 1
        val bitsPerSample = 16
        val silenceSamples = (sampleRate * silenceMs / 1000.0).toInt()
        val silenceBytes = silenceSamples * channels * bitsPerSample / 8
        val silence = ByteArray(silenceBytes)

        val pcmOriginal = extractPcmFromWav(wavData)
        val combinedPcm = ByteArray(pcmOriginal.size + silenceBytes)
        System.arraycopy(pcmOriginal, 0, combinedPcm, 0, pcmOriginal.size)
        System.arraycopy(silence, 0, combinedPcm, pcmOriginal.size, silenceBytes)

        return pcm16ToWav(combinedPcm, sampleRate, channels, bitsPerSample)
    }

    fun extractPcmFromWav(wavData: ByteArray): ByteArray {
        if (wavData.size < 44) return wavData

        val riff = String(wavData, 0, 4)
        if (riff != "RIFF") return wavData

        var dataOffset = 12
        while (dataOffset < wavData.size - 8) {
            val chunkId = String(wavData, dataOffset, 4)
            val chunkSize = ByteBuffer.wrap(wavData, dataOffset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int

            if (chunkId == "data") {
                val pcmStart = dataOffset + 8
                val pcmEnd = minOf(pcmStart + chunkSize, wavData.size)
                return wavData.copyOfRange(pcmStart, pcmEnd)
            }
            dataOffset += 8 + chunkSize
        }

        return wavData.copyOfRange(44, wavData.size)
    }

    fun validateAndFixWav(wavData: ByteArray): ByteArray {
        if (wavData.size < 44) return wavData

        val riff = String(wavData, 0, 4)
        if (riff != "RIFF") {
            return pcm16ToWav(wavData, 24000, 1, 16)
        }

        return wavData
    }
}
