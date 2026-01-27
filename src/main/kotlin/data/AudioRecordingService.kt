package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.*

/**
 * Service for recording audio from the microphone.
 * Records audio in WAV format suitable for speech-to-text APIs.
 */
class AudioRecordingService {

    private var targetDataLine: TargetDataLine? = null
    private var isRecording = false
    private val recordedData = ByteArrayOutputStream()

    // Audio format configuration (16kHz, 16-bit, mono - optimal for speech recognition)
    private val audioFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        16000f, // sample rate
        16,     // sample size in bits
        1,      // channels (mono)
        2,      // frame size
        16000f, // frame rate
        false   // little endian
    )

    /**
     * Starts recording audio from the default microphone.
     * @throws AudioException if microphone is not available or already recording
     */
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (isRecording) {
            throw AudioException("Recording is already in progress")
        }

        try {
            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)

            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                throw AudioException("Microphone not supported with required audio format")
            }

            targetDataLine = AudioSystem.getLine(dataLineInfo) as TargetDataLine
            targetDataLine?.open(audioFormat)
            targetDataLine?.start()

            isRecording = true
            recordedData.reset()

            println("[AudioRecording] Recording started")

            // Read audio data in a loop
            val buffer = ByteArray(4096)
            while (isRecording) {
                val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    recordedData.write(buffer, 0, bytesRead)
                }
            }
        } catch (e: LineUnavailableException) {
            throw AudioException("Microphone is not available: ${e.message}", e)
        }
    }

    /**
     * Stops recording and returns the recorded audio data as a WAV file.
     * @return File containing the recorded audio in WAV format
     * @throws AudioException if not currently recording
     */
    suspend fun stopRecording(): File = withContext(Dispatchers.IO) {
        if (!isRecording) {
            throw AudioException("No recording in progress")
        }

        isRecording = false
        targetDataLine?.stop()
        targetDataLine?.close()

        println("[AudioRecording] Recording stopped, captured ${recordedData.size()} bytes")

        // Create temporary WAV file
        val tempFile = File.createTempFile("recording_", ".wav")
        tempFile.deleteOnExit()

        // Write WAV file with proper header
        writeWavFile(tempFile, recordedData.toByteArray(), audioFormat)

        println("[AudioRecording] WAV file created: ${tempFile.absolutePath} (${tempFile.length()} bytes)")

        tempFile
    }

    /**
     * Checks if currently recording.
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Gets available microphone devices.
     */
    fun getAvailableMicrophones(): List<String> {
        val mixers = AudioSystem.getMixerInfo()
        return mixers.mapNotNull { mixerInfo ->
            val mixer = AudioSystem.getMixer(mixerInfo)
            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)
            if (mixer.isLineSupported(dataLineInfo)) {
                mixerInfo.name
            } else null
        }
    }

    /**
     * Writes PCM audio data to a WAV file with proper header.
     */
    private fun writeWavFile(file: File, audioData: ByteArray, format: AudioFormat) {
        file.outputStream().use { fileOutput ->
            // Write WAV header
            val dataSize = audioData.size
            val sampleRate = format.sampleRate.toInt()
            val channels = format.channels
            val bitsPerSample = format.sampleSizeInBits
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8

            // RIFF header
            fileOutput.write("RIFF".toByteArray())
            fileOutput.write(intToLittleEndian(dataSize + 36))
            fileOutput.write("WAVE".toByteArray())

            // fmt subchunk
            fileOutput.write("fmt ".toByteArray())
            fileOutput.write(intToLittleEndian(16)) // subchunk size
            fileOutput.write(shortToLittleEndian(1)) // audio format (PCM)
            fileOutput.write(shortToLittleEndian(channels.toShort()))
            fileOutput.write(intToLittleEndian(sampleRate))
            fileOutput.write(intToLittleEndian(byteRate))
            fileOutput.write(shortToLittleEndian(blockAlign.toShort()))
            fileOutput.write(shortToLittleEndian(bitsPerSample.toShort()))

            // data subchunk
            fileOutput.write("data".toByteArray())
            fileOutput.write(intToLittleEndian(dataSize))
            fileOutput.write(audioData)
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToLittleEndian(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}

/**
 * Exception thrown when audio recording fails.
 */
class AudioException(message: String, cause: Throwable? = null) : Exception(message, cause)
