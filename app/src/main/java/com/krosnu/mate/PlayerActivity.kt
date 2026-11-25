package com.krosnu.mate

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlayerActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var osdBar: LinearLayout
    private lateinit var osdTitle: TextView
    private lateinit var digitOsd: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var overlayDrawer: LinearLayout
    private lateinit var overlayRecycler: RecyclerView

    private var player: ExoPlayer? = null
    private var sniffer: StreamSniffer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var channelList: ArrayList<Channel> = arrayListOf()
    private var currentIndex: Int = 0
    private var digitBuffer: String = ""
    private var retryCount: Int = 0

    @OptIn(UnstableApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        hideSystemBars()

        channelList = (intent.getSerializableExtra("channels") as? ArrayList<Channel>) ?: arrayListOf()
        currentIndex = intent.getIntExtra("index", 0)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        playerView = PlayerView(this).apply {
            useController = false
            keepScreenOn = true
            setKeepContentOnPlayerReset(true)
            setShutterBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        loadingSpinner = ProgressBar(this).apply {
            visibility = View.GONE
        }
        root.addView(loadingSpinner, FrameLayout.LayoutParams(120, 120, Gravity.CENTER))

        overlayDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E60B111D"))
            setPadding(24, 28, 24, 28)
            visibility = View.GONE
        }
        val drawerTitle = TextView(this).apply {
            text = "KANALLAR (▲/▼ seç, OK aç)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            setPadding(12, 0, 12, 16)
        }
        overlayRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        overlayDrawer.addView(drawerTitle)
        overlayDrawer.addView(overlayRecycler, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(overlayDrawer, FrameLayout.LayoutParams(520, -1, Gravity.START))

        digitOsd = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#DD0B111D"))
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 28f
            setPadding(36, 18, 36, 18)
            visibility = View.GONE
        }
        root.addView(
            digitOsd,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                setMargins(0, 36, 48, 0)
            }
        )

        osdBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC141B22"))
            setPadding(36, 22, 36, 22)
        }

        osdTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val btnList = Button(this).apply {
            text = "☰ Kanallar (OK)"
            isFocusable = true
            setOnClickListener { toggleOverlayDrawer(true) }
        }
        val btnPrev = Button(this).apply {
            text = "▲ CH-"
            isFocusable = true
            setOnClickListener { switchChannel(-1) }
        }
        val btnNext = Button(this).apply {
            text = "▼ CH+"
            isFocusable = true
            setOnClickListener { switchChannel(1) }
        }
        val btnBack = Button(this).apply {
            text = "Geri"
            isFocusable = true
            setOnClickListener { finish() }
        }

        osdBar.addView(osdTitle)
        osdBar.addView(btnList)
        osdBar.addView(btnPrev)
        osdBar.addView(btnNext)
        osdBar.addView(btnBack)

        root.addView(osdBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnClickListener {
            if (overlayDrawer.visibility == View.VISIBLE) toggleOverlayDrawer(false)
            else showOsd()
        }

        setContentView(root)
        sniffer = StreamSniffer(this)
        bindOverlayList()

        if (channelList.isNotEmpty()) {
            loadCurrentChannel()
        }
    }

    private fun createRowBg(isCurrent: Boolean): StateListDrawable {
        val focused = GradientDrawable().apply {
            setColor(Color.parseColor("#1E3A5F"))
            setStroke(4, Color.parseColor("#38BDF8"))
            cornerRadius = 8f
        }
        val normal = GradientDrawable().apply {
            setColor(if (isCurrent) Color.parseColor("#1B2D3E") else Color.TRANSPARENT)
            cornerRadius = 8f
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun bindOverlayList() {
        overlayRecycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                    setPadding(24, 20, 24, 20)
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun getItemCount() = channelList.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ch = channelList[position]
                val tv = holder.itemView as TextView
                tv.text = String.format("%03d  %s | %s", position + 1, ch.country, ch.name)
                tv.background = createRowBg(position == currentIndex)
                tv.setOnClickListener {
                    currentIndex = position
                    retryCount = 0
                    toggleOverlayDrawer(false)
                    loadCurrentChannel()
                }
            }
        }
    }

    private fun toggleOverlayDrawer(show: Boolean) {
        if (show) {
            overlayDrawer.visibility = View.VISIBLE
            overlayRecycler.adapter?.notifyDataSetChanged()
            overlayRecycler.scrollToPosition(currentIndex)
            overlayRecycler.post {
                overlayRecycler.findViewHolderForAdapterPosition(currentIndex)?.itemView?.requestFocus()
                    ?: overlayRecycler.requestFocus()
            }
        } else {
            overlayDrawer.visibility = View.GONE
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showOsd() {
        osdBar.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages("osd")
        handler.postAtTime({ osdBar.visibility = View.GONE }, "osd", android.os.SystemClock.uptimeMillis() + 4500)
    }

    private fun switchChannel(delta: Int) {
        if (channelList.isEmpty()) return
        retryCount = 0
        currentIndex = (currentIndex + delta + channelList.size) % channelList.size
        loadCurrentChannel()
    }

    private fun handleRemoteDigit(digit: Int) {
        digitBuffer += digit.toString()
        if (digitBuffer.length > 3) digitBuffer = digit.toString()
        digitOsd.text = digitBuffer.padStart(3, '0')
        digitOsd.visibility = View.VISIBLE

        handler.removeCallbacksAndMessages("digit")
        handler.postAtTime({
            digitOsd.visibility = View.GONE
            val num = digitBuffer.toIntOrNull() ?: 0
            digitBuffer = ""
            if (num in 1..channelList.size) {
                retryCount = 0
                currentIndex = num - 1
                loadCurrentChannel()
            } else {
                Toast.makeText(this, "$num numaralı kanal yok", Toast.LENGTH_SHORT).show()
            }
        }, "digit", android.os.SystemClock.uptimeMillis() + 1200)
    }

    private fun loadCurrentChannel() {
        val ch = channelList[currentIndex]
        osdTitle.text = String.format("%03d  %s | %s", currentIndex + 1, ch.country, ch.name)
        showOsd()
        loadingSpinner.visibility = View.VISIBLE

        sniffer?.sniffStream(
            channel = ch,
            onFound = { streamUrl, referer ->
                playStream(streamUrl, referer)
            },
            onFailed = { reason ->
                loadingSpinner.visibility = View.GONE
                Toast.makeText(this, "${ch.name}: $reason", Toast.LENGTH_SHORT).show()
            }
        )
    }

    @OptIn(UnstableApi::class)
    private fun playStream(streamUrl: String, referer: String) {
        val headers = mapOf(
            "Referer" to referer,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val exo = player ?: run {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 1500, 3000)
                .build()
            ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build()
                .also { created ->
                    player = created
                    playerView.player = created
                    created.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                retryCount = 0
                                loadingSpinner.visibility = View.GONE
                            } else if (state == Player.STATE_BUFFERING) {
                                loadingSpinner.visibility = View.VISIBLE
                            } else if (state == Player.STATE_ENDED) {
                                loadCurrentChannel()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (retryCount < 3) {
                                retryCount++
                                Toast.makeText(this@PlayerActivity, "Yayın yenileniyor ($retryCount/3)...", Toast.LENGTH_SHORT).show()
                                handler.postDelayed({ loadCurrentChannel() }, 1500)
                            } else {
                                loadingSpinner.visibility = View.GONE
                                Toast.makeText(this@PlayerActivity, "Yayın koptu — ▲/▼ ile yedek kaynağa geçin", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
        }

        val mediaItem = MediaItem.fromUri(streamUrl)
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
        exo.setMediaSource(hlsSource)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (overlayDrawer.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                toggleOverlayDrawer(false)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            handleRemoteDigit(keyCode - KeyEvent.KEYCODE_0)
            return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                switchChannel(-1); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                switchChannel(1); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleOverlayDrawer(true); true
            }
            KeyEvent.KEYCODE_INFO -> {
                showOsd(); true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish(); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sniffer?.cleanup()
        player?.release()
        player = null
    }
}
