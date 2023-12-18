package com.konovalov.vad.example.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.File


class VoiceRecorder(val callback: AudioCallback , private val context: Context) {

    private val TAG = VoiceRecorder::class.java.simpleName

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private var isListening = false

    private var sampleRate: Int = 0
    private var frameSize: Int = 0

    fun start(sampleRate: Int, frameSize: Int) {
        this.sampleRate = sampleRate
        this.frameSize = frameSize
        stop()

        audioRecord = createAudioRecord()
        if (audioRecord != null) {
            isListening = true
            audioRecord?.startRecording()

            thread = Thread(ProcessVoice())
            thread?.start()
        }
    }

    fun stop() {
        isListening = false
        thread?.interrupt()
        thread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun createWavHeader(
        totalAudioLen: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (bitsPerSample * sampleRate * channels) / 8
        val blockAlign = (channels * bitsPerSample) / 8
        val header = ByteArray(44)

        header[0] = 'R'.toByte()  // RIFF/WAVE header
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()  // 'fmt ' chunk
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1  // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = blockAlign.toByte()  // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()  // bits per sample
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        return header
    }


    private fun saveBufferToFile(bufferList: List<ShortArray>, fileName: String) {
        val filePath = context.getExternalFilesDir(null)?.absolutePath + "/" + fileName
        val file = File(filePath)
        val os = FileOutputStream(file)
        val dos = DataOutputStream(os)

//        val totalAudioLen = bufferList.sumBy { it.size } * 2
//        dos.write(createWavHeader(totalAudioLen, sampleRate, 1, 16))

        bufferList.forEach { buffer ->
            buffer.forEach {
                dos.writeShort(it.toInt())
            }
        }

        dos.close()
        os.close()

        Log.d("saving", "Saved buffer to $filePath")
    }

    companion object {
        private const val bytesPerFloat = 4
        private const val sampleRate = 16000
        private const val maxAudioLengthInSeconds = 30
    }

//    private fun saveBufferToFile(floatAudioData: FloatArray, fileName: String) {
//        // Ensure the audio data doesn't exceed the maximum length
//        val dataToSave = if (floatAudioData.size > maxAudioLengthInSeconds * sampleRate) {
//            floatAudioData.sliceArray(0 until maxAudioLengthInSeconds * sampleRate)
//        } else {
//            floatAudioData
//        }
//
//        val filePath = context.getExternalFilesDir(null)?.absolutePath + "/" + fileName + ".pcm"
//        val file = File(filePath)
//        val os = FileOutputStream(file)
//        val dos = DataOutputStream(os)
//
//        dataToSave.forEach {
//            dos.writeFloat(it)
//        }
//
//        dos.close()
//        os.close()
//
//        Log.d("saving", "Saved PCM data to $filePath")
//    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        try {
            val minBufferSize = maxOf(
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ),
                2 * frameSize
            )

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                return audioRecord
            } else {
                audioRecord.release()
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error can't create AudioRecord ", e)
        }
        return null
    }

//    private inner class ProcessVoice : Runnable {
//        override fun run() {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
//            val size = frameSize
//
//            while (!Thread.interrupted() && isListening) {
//                val buffer = ShortArray(size)
//                audioRecord?.read(buffer, 0, buffer.size)
//                callback.onAudio(buffer)
//
//                Log.d("onAudio", callback.onAudio(buffer).toString())
//
//
//            }
//        }
//    }

//    private inner class ProcessVoice : Runnable {
//        override fun run() {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
//            val size = frameSize
//
//            val voiceData = mutableListOf<ShortArray>()
//
//            while (!Thread.interrupted() && isListening) {
//                val buffer = ShortArray(size)
//                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
//                if (bytesRead > 0) {
//                    if (callback.onAudio(buffer)) { // if it's voice
//                        voiceData.add(buffer)  // save the buffer
//                        saveBufferToFile(voiceData, "recordedVoice.wav")
//                    }
//                }
//            }
//
//            // Here, you can save `voiceData` to a file or process it further.
//        }
//    }


//    private inner class ProcessVoice : Runnable {
//        override fun run() {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
//            val size = frameSize
//
//            val voiceData = mutableListOf<ShortArray>()
//            var startTime = System.currentTimeMillis()  // Initialize start time
//
//            while (!Thread.interrupted() && isListening) {
//                val buffer = ShortArray(size)
//                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
//                if (bytesRead > 0) {
//                    if (callback.onAudio(buffer)) { // if it's voice
//                        voiceData.add(buffer)  // save the buffer
//                    }
//                }
//
//                // Check if 30 seconds have passed
//                if (System.currentTimeMillis() - startTime >= 30_000) {
//                    saveBufferToFile(voiceData, "recordedVoice_${System.currentTimeMillis()}.wav")
//                    voiceData.clear()  // Clear the buffer
//                    startTime = System.currentTimeMillis()  // Reset the start time
//                }
//            }
//
//            // Optional: Save any remaining data after the loop ends
//            if (voiceData.isNotEmpty()) {
//                saveBufferToFile(voiceData, "recordedVoice_${System.currentTimeMillis()}.wav")
//            }
//        }
//    }


    private fun convertToFloatArray(voiceData: MutableList<ShortArray>): FloatArray {
        val totalSize = voiceData.sumBy { it.size }
        val floatArray = FloatArray(totalSize)
        var index = 0

        voiceData.forEach { buffer ->
            buffer.forEach {
                floatArray[index++] = it.toFloat() / Short.MAX_VALUE
            }
        }

        return floatArray
    }

//    private inner class ProcessVoice : Runnable {
//        override fun run() {
//            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
//            val size = frameSize
//
//            val voiceData = mutableListOf<ShortArray>()
//            var totalFramesRecorded = 0  // Initialize frame counter
//
//            while (!Thread.interrupted() && isListening) {
//                val buffer = ShortArray(size)
//                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
//                if (bytesRead > 0) {
//                    if (callback.onAudio(buffer)) { // if it's voice
//                        voiceData.add(buffer)  // save the buffer
//                        totalFramesRecorded += bytesRead
//                    }
//                }
//
//                // Check if accumulated audio length is 30 seconds
//                if (totalFramesRecorded >= 30 * sampleRate) {
//                    val floatAudioData = convertToFloatArray(voiceData)
//                    saveBufferToFile(
//                        floatAudioData,
//                        "recordedVoice_${System.currentTimeMillis()}.pcm"
//                    )
//                    voiceData.clear()  // Clear the buffer
//                    totalFramesRecorded = 0  // Reset the frame counter
//                }
//
//                // Optional: Save any remaining data after the loop ends
//                if (voiceData.isNotEmpty()) {
//                    val floatAudioData = convertToFloatArray(voiceData)
//                    saveBufferToFile(
//                        floatAudioData,
//                        "recordedVoice_${System.currentTimeMillis()}.pcm"
//                    )
//                }
//
////                // Check if accumulated audio length is 30 seconds
////                // 30 seconds * sampleRate = total number of frames for 30 seconds
////                if (totalFramesRecorded >= 30 * sampleRate) {
////                    saveBufferToFile(voiceData, "recordedVoice_${System.currentTimeMillis()}.pcm")
////                    voiceData.clear()  // Clear the buffer
////                    totalFramesRecorded = 0  // Reset the frame counter
////                }
////            }
////
////            // Optional: Save any remaining data after the loop ends
////            if (voiceData.isNotEmpty()) {
////                saveBufferToFile(voiceData, "recordedVoice_${System.currentTimeMillis()}.pcm")
////            }
//            }
//        }
//
//    }
        //    interface AudioCallback {
//        fun onAudio(audioData: ShortArray)
//    }


    private inner class ProcessVoice : Runnable {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val size = frameSize

            val voiceData = mutableListOf<ShortArray>()
            var totalFramesRecorded = 0  // Initialize frame counter

            while (!Thread.interrupted() && isListening) {
                val buffer = ShortArray(size)
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0 && callback.onAudio(buffer)) { // if it's voice
                    voiceData.add(buffer)  // save the buffer
                    totalFramesRecorded += bytesRead


                    // Check if accumulated audio length is 30 seconds
                    if (totalFramesRecorded >= 30 * sampleRate) {
//                        val floatAudioData = convertToFloatArray(voiceData)
                        saveBufferToFile(
                            voiceData,
                            "recordedVoice_${System.currentTimeMillis()}.pcm"
                        )
                        voiceData.clear()  // Clear the buffer
                        totalFramesRecorded = 0  // Reset the frame counter
                    }


                }

                // Check if accumulated audio length is 30 seconds
//                if (totalFramesRecorded >= 30 * sampleRate) {
//                val floatAudioData = convertToFloatArray(voiceData)
//                saveBufferToFile(
//                    floatAudioData,
//                    "recordedVoice_${System.currentTimeMillis()}.pcm"
//                )
//                voiceData.clear()  // Clear the buffer
//                totalFramesRecorded = 0  // Reset the frame counter
//                }
            }
        }
    }

    interface AudioCallback {
            fun onAudio(audioData: ShortArray): Boolean
        }
    }
