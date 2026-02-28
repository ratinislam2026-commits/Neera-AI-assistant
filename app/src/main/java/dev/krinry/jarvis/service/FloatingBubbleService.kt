package dev.krinry.jarvis.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.krinry.jarvis.R
import dev.krinry.jarvis.agent.AgentLlmEngine
import dev.krinry.jarvis.ai.GroqApiClient
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FloatingBubbleService — Always-on-top overlay bubble for Jarvis AI.
 *
 * Features:
 * - Tap bubble to START recording
 * - Tap again to STOP and process command
 * - Subtitle-style scrolling transcript display
 * - Pulse animation while listening
 * - Draggable bubble
 */
class FloatingBubbleService : Service() {

    companion object {
        private const val TAG = "FloatingBubble"
        const val ACTION_START = "dev.krinry.jarvis.START_BUBBLE"
        const val ACTION_STOP = "dev.krinry.jarvis.STOP_BUBBLE"

        private const val MAX_RECORDING_SECONDS = 10
        private const val SAMPLE_RATE = 16000
        private const val MAX_SUBTITLE_LINES = 4
        private const val SUBTITLE_FADE_DELAY = 4000L

        private val WAKE_WORDS = listOf(
            "krinry", "cranary", "kri nri", "crinary", "cranery", "krinari", "jarvis",
            "crinri", "grini", "krinry,", "jarvis,"
        )

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var subtitleView: View? = null
    private var agentEngine: AgentLlmEngine? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isListening = false
    private var isProcessingCommand = false
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var subtitleHideJob: Job? = null
    private val subtitleHistory = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        agentEngine = AgentLlmEngine(applicationContext)
        agentEngine?.onStatusUpdate = { status ->
            scope.launch(Dispatchers.Main) {
                addSubtitle(status)
                if (status.startsWith("✅") || status.startsWith("❌") ||
                    status.startsWith("⚠️") || status.startsWith("⏹")) {
                    isProcessingCommand = false
                    // Auto-hide subtitles after task completion
                    subtitleHideJob?.cancel()
                    subtitleHideJob = scope.launch {
                        delay(5000)
                        hideSubtitles()
                    }
                }
            }
        }
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (bubbleView == null) {
            createBubble()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        stopRecording()
        bubbleView?.let { windowManager.removeView(it) }
        subtitleView?.let { windowManager.removeView(it) }
        bubbleView = null
        subtitleView = null
        super.onDestroy()
    }

    private fun createBubble() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // === Floating Bubble Button ===
        val bubbleSize = dpToPx(54)
        val bubble = FrameLayout(this).apply {
            background = createBubbleDrawable()
            elevation = dpToPx(8).toFloat()
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            val padding = dpToPx(13)
            setPadding(padding, padding, padding, padding)
        }
        bubble.addView(icon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val bubbleParams = WindowManager.LayoutParams(
            bubbleSize, bubbleSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(10)
            y = dpToPx(400)
        }

        // Touch — drag + tap
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isMoved = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x; initialY = bubbleParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isMoved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMoved = true
                    bubbleParams.x = initialX + dx; bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubble, bubbleParams); true
                }
                MotionEvent.ACTION_UP -> { if (!isMoved) onBubbleTapped(); true }
                else -> false
            }
        }

        windowManager.addView(bubble, bubbleParams)
        bubbleView = bubble

        // === Subtitle overlay (bottom of screen) ===
        val subtitleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10))
            background = createSubtitleBackground()
            visibility = View.GONE
        }

        val subtitleText = TextView(this).apply {
            id = View.generateViewId()
            tag = "subtitle_text"
            text = ""
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = MAX_SUBTITLE_LINES
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 2f, 0x80000000.toInt())
            lineHeight = dpToPx(22)
        }
        subtitleContainer.addView(subtitleText)

        val subtitleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(80)
        }

        windowManager.addView(subtitleContainer, subtitleParams)
        subtitleView = subtitleContainer
    }

    // =========================================================================
    // === Subtitle System ===
    // =========================================================================

    /**
     * Add a new subtitle line. Old lines scroll out, new lines fade in.
     * Works like real-time subtitles on a video.
     */
    private fun addSubtitle(text: String) {
        subtitleHideJob?.cancel()

        subtitleHistory.add(text)
        // Keep only recent lines
        while (subtitleHistory.size > MAX_SUBTITLE_LINES) {
            subtitleHistory.removeAt(0)
        }

        subtitleView?.let { view ->
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                // Slide-in animation
                val slideUp = TranslateAnimation(0f, 0f, dpToPx(30).toFloat(), 0f).apply {
                    duration = 250
                }
                val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 250 }
                val animSet = AnimationSet(true).apply {
                    addAnimation(slideUp)
                    addAnimation(fadeIn)
                }
                view.startAnimation(animSet)
            }

            val tv = view.findViewWithTag<TextView>("subtitle_text")
            tv?.text = subtitleHistory.joinToString("\n")
        }

        // Auto-hide after idle
        subtitleHideJob = scope.launch {
            delay(SUBTITLE_FADE_DELAY)
            if (!isProcessingCommand && !isListening) {
                hideSubtitles()
            }
        }
    }

    private fun hideSubtitles() {
        subtitleView?.let { view ->
            if (view.visibility == View.VISIBLE) {
                val fadeOut = AlphaAnimation(1f, 0f).apply { duration = 300 }
                view.startAnimation(fadeOut)
                scope.launch {
                    delay(300)
                    view.visibility = View.GONE
                    subtitleHistory.clear()
                }
            }
        }
    }

    // =========================================================================
    // === Bubble Tap Logic ===
    // =========================================================================

    private fun onBubbleTapped() {
        if (isProcessingCommand) {
            agentEngine?.cancelTask()
            isProcessingCommand = false
            stopRecording()
            addSubtitle("⏹ Cancelled")
            subtitleHideJob?.cancel()
            subtitleHideJob = scope.launch {
                delay(2000)
                hideSubtitles()
            }
            return
        }
        if (isListening) {
            stopRecordingAndProcess()
        } else {
            startWhisperRecording()
        }
    }

    // =========================================================================
    // === Whisper STT Recording ===
    // =========================================================================

    private fun startWhisperRecording() {
        if (!AutoAgentService.isRunning()) {
            addSubtitle("❌ Enable Accessibility Service first!")
            return
        }

        isListening = true
        animateBubble(true)
        addSubtitle("🎤 Listening... tap to send")

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 4
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        addSubtitle("❌ Microphone unavailable")
                        isListening = false
                        animateBubble(false)
                    }
                    return@launch
                }

                val audioBuffer = mutableListOf<Short>()
                val buffer = ShortArray(bufferSize)

                audioRecord?.startRecording()
                Log.d(TAG, "Whisper recording started")

                val maxSamples = SAMPLE_RATE * MAX_RECORDING_SECONDS

                while (isListening && audioBuffer.size < maxSamples) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read) {
                            audioBuffer.add(buffer[i])
                        }
                    }
                }

                if (audioBuffer.size >= maxSamples) {
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Max recording duration reached")
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                if (audioBuffer.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isListening = false
                        animateBubble(false)
                        addSubtitle("❌ No audio captured")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    isListening = false
                    animateBubble(false)
                    addSubtitle("🔄 Transcribing...")
                }

                val wavFile = saveAsWav(audioBuffer)
                if (wavFile == null) {
                    withContext(Dispatchers.Main) {
                        addSubtitle("❌ Failed to save audio")
                    }
                    return@launch
                }

                val transcript = GroqApiClient.transcribeAudio(
                    applicationContext,
                    wavFile,
                    null
                )

                wavFile.delete()

                withContext(Dispatchers.Main) {
                    if (transcript.isNullOrBlank()) {
                        addSubtitle("❌ Couldn't understand, try again")
                    } else {
                        Log.d(TAG, "Whisper transcript: $transcript")
                        val command = stripWakeWord(transcript)
                        isProcessingCommand = true
                        subtitleHistory.clear() // Fresh subtitle session
                        addSubtitle("🗣️ \"$command\"")
                        agentEngine?.startTask(command, scope)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Whisper recording failed", e)
                withContext(Dispatchers.Main) {
                    isListening = false
                    animateBubble(false)
                    addSubtitle("❌ Error: ${e.message?.take(40)}")
                }
            }
        }
    }

    private fun stopRecordingAndProcess() {
        Log.d(TAG, "User stopped recording manually")
        isListening = false
        animateBubble(false)
        addSubtitle("🔄 Processing...")
    }

    private fun stopRecording() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        animateBubble(false)
    }

    private fun saveAsWav(audioData: List<Short>): File? {
        return try {
            val file = File(cacheDir, "whisper_input_${System.currentTimeMillis()}.wav")
            val totalPcmBytes = audioData.size * 2

            FileOutputStream(file).use { fos ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(36 + totalPcmBytes)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16)
                header.putShort(1)
                header.putShort(1)
                header.putInt(SAMPLE_RATE)
                header.putInt(SAMPLE_RATE * 2)
                header.putShort(2)
                header.putShort(16)
                header.put("data".toByteArray())
                header.putInt(totalPcmBytes)
                fos.write(header.array())

                val pcmBytes = ByteBuffer.allocate(totalPcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                for (sample in audioData) {
                    pcmBytes.putShort(sample)
                }
                fos.write(pcmBytes.array())
            }

            Log.d(TAG, "WAV saved: ${file.absolutePath} (${file.length()} bytes)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save WAV", e)
            null
        }
    }

    // =========================================================================
    // === Helpers ===
    // =========================================================================

    private fun stripWakeWord(command: String): String {
        val lower = command.trim().lowercase()
        for (wake in WAKE_WORDS) {
            if (lower.startsWith(wake)) {
                val stripped = command.substring(wake.length).trim().trimStart(',', ' ')
                if (stripped.isNotEmpty()) return stripped
            }
        }
        return command.trim()
    }

    private fun animateBubble(listening: Boolean) {
        bubbleView?.let { view ->
            val scale = if (listening) 1.25f else 1.0f
            val animator = ValueAnimator.ofFloat(view.scaleX, scale).apply {
                duration = 250
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val value = anim.animatedValue as Float
                    view.scaleX = value
                    view.scaleY = value
                }
            }
            animator.start()

            if (listening) {
                view.animate()
                    .alpha(0.6f)
                    .setDuration(400)
                    .withEndAction {
                        if (isListening) {
                            view.animate().alpha(1f).setDuration(400).withEndAction {
                                if (isListening) animateBubble(true)
                            }.start()
                        }
                    }.start()
            } else {
                view.animate().cancel()
                view.alpha = 1f
            }
        }
    }

    private fun createBubbleDrawable(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            colors = intArrayOf(0xFF6C5CE7.toInt(), 0xFF8E7CF3.toInt())
            cornerRadius = dpToPx(28).toFloat()
            setStroke(dpToPx(2), 0xFFFFFFFF.toInt())
        }
    }

    private fun createSubtitleBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(0xCC0A0A1A.toInt()) // Semi-transparent dark
            cornerRadius = dpToPx(16).toFloat()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
