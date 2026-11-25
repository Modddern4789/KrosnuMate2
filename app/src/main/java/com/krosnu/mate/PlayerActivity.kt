package com.krosnu.mate

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private lateinit var webPlayer: WebView
    private lateinit var customVideoContainer: FrameLayout
    private lateinit var osdBar: LinearLayout
    private lateinit var osdTitle: TextView
    private lateinit var digitOsd: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var overlayDrawer: LinearLayout
    private lateinit var overlayRecycler: RecyclerView

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var channelList: ArrayList<Channel> = arrayListOf()
    private var currentIndex: Int = 0
    private var digitBuffer: String = ""
    private var retryCount: Int = 0

    @OptIn(UnstableApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        hideSystemBars()

        val raw = (intent.getSerializableExtra("channels") as? ArrayList<Channel>) ?: arrayListOf()
        channelList = filterMembershipChannels(raw)
        currentIndex = intent.getIntExtra("index", 0)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            isFocusable = true
            isFocusableInTouchMode = true
        }

        playerView = PlayerView(this).apply {
            useController = false
            keepScreenOn = true
            isFocusable = false
            setKeepContentOnPlayerReset(true)
            setShutterBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        webPlayer = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val u = request?.url?.toString()?.lowercase() ?: return true
                    if (u.startsWith("intent:") || u.startsWith("market:") ||
                        u.contains("doubleclick") || u.contains("googleads") || u.contains("popads")
                    ) return true
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val kw = channelList.getOrNull(currentIndex)?.matchKeyword ?: ""
                    view?.evaluateJavascript(buildJs(kw), null)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = callback
                    customVideoContainer.removeAllViews()
                    if (view != null) {
                        view.isFocusable = false
                        customVideoContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
                        customVideoContainer.visibility = View.VISIBLE
                        customVideoContainer.bringToFront()
                    }
                    if (overlayDrawer.visibility == View.VISIBLE) overlayDrawer.bringToFront()
                    osdBar.bringToFront()
                }

                override fun onHideCustomView() {
                    customVideoContainer.removeAllViews()
                    customVideoContainer.visibility = View.GONE
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                }
            }
        }
        root.addView(webPlayer, FrameLayout.LayoutParams(-1, -1))

        customVideoContainer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            isFocusable = false
        }
        root.addView(customVideoContainer, FrameLayout.LayoutParams(-1, -1))

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
            isFocusable = false
            setOnClickListener { toggleOverlayDrawer(true) }
        }
        val btnPrev = Button(this).apply {
            text = "▲ CH-"
            isFocusable = false
            setOnClickListener { switchChannel(-1) }
        }
        val btnNext = Button(this).apply {
            text = "▼ CH+"
            isFocusable = false
            setOnClickListener { switchChannel(1) }
        }
        val btnBack = Button(this).apply {
            text = "Geri"
            isFocusable = false
            setOnClickListener { finish() }
        }

        osdBar.addView(osdTitle)
        osdBar.addView(btnList)
        osdBar.addView(btnPrev)
        osdBar.addView(btnNext)
        osdBar.addView(btnBack)

        root.addView(osdBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        root.requestFocus()
        bindOverlayList()

        if (channelList.isNotEmpty()) {
            loadCurrentChannel()
        }
    }

    private fun buildJs(keyword: String): String {
        val kw = keyword.replace("'", "\\'").lowercase()
        return """
            (function() {
                if (window.__k) return; window.__k = 1;
                var kw = '$kw';
                var adSec = 0;

                function tap(el) {
                    if (!el) return;
                    try { el.scrollIntoView({block:'center'}); } catch(e){}
                    ['pointerdown','mousedown','mouseup','click'].forEach(function(m) {
                        try { el.dispatchEvent(new MouseEvent(m, {bubbles:true, cancelable:true, view:window})); } catch(e){}
                    });
                    try { el.click(); } catch(e){}
                }

                function clickChannelKeyword() {
                    if (!kw) return;
                    var els = document.querySelectorAll('a, button, div, li, span, h3, h4, p');
                    for (var i = 0; i < els.length; i++) {
                        var t = (els[i].innerText || '').trim().toLowerCase();
                        if (t === kw || (t.includes(kw) && t.length < 32)) {
                            tap(els[i]);
                            break;
                        }
                    }
                }
                setTimeout(clickChannelKeyword, 400);
                setTimeout(clickChannelKeyword, 1800);

                setInterval(function() {
                    document.querySelectorAll('[class*="login"], [class*="modal"], [class*="popup"], [id*="login"]').forEach(function(el) {
                        var s = (el.innerText || '').toLowerCase();
                        if (s.includes('üyelik') || s.includes('üye ol') || s.includes('giriş')) {
                            el.style.setProperty('display', 'none', 'important');
                        }
                    });

                    var playBtns = document.querySelectorAll('.vjs-big-play-button, .jw-icon-playback, [aria-label*="Play" i], [aria-label*="İzle" i], [class*="play-btn"]');
                    playBtns.forEach(function(p) { if (p.offsetParent !== null) tap(p); });
                    document.querySelectorAll('button, a').forEach(function(b) {
                        var bt = (b.innerText || '').trim().toLowerCase();
                        if ((bt === 'izle' || bt === '▶ izle' || bt === 'başlat' || bt === 'oynat' || bt === 'play') && b.offsetParent !== null) {
                            tap(b);
                        }
                    });

                    var skipBtn = null;
                    document.querySelectorAll('button, a, div, span').forEach(function(el) {
                        var z = ((el.innerText || '') + ' ' + (el.className || '')).toLowerCase();
                        if ((z.includes('skip') || z.includes('reklamı geç') || z.includes('reklami gec') || z.includes('reklamı atla')) && el.offsetParent !== null && z.length < 50) {
                            skipBtn = el;
                        }
                    });
                    if (skipBtn) {
                        adSec++;
                        if (adSec >= 15 || (skipBtn.innerText || '').toLowerCase().includes('skip') || (skipBtn.innerText || '').toLowerCase().includes('geç')) {
                            tap(skipBtn);
                            adSec = 0;
                        }
                    }

                    var v = document.querySelector('video');
                    var f = document.querySelector('iframe[src*="m3u8"], iframe[src*="embed"], iframe[src*="player"], iframe');
                    var target = v || f;
                    if (target) {
                        target.style.setProperty('position', 'fixed', 'important');
                        target.style.setProperty('top', '0', 'important');
                        target.style.setProperty('left', '0', 'important');
                        target.style.setProperty('width', '100vw', 'important');
                        target.style.setProperty('height', '100vh', 'important');
                        target.style.setProperty('z-index', '999999', 'important');
                        target.style.setProperty('background', '#000', 'important');
                        if (v && v.paused) { v.play().catch(function(){}); }
                    }
                }, 1000);
            })();
        """.trimIndent()
    }

    private fun filterMembershipChannels(list: List<Channel>): ArrayList<Channel> {
        val blocked = listOf(
            "login", "signin", "sign-in", "register", "subscribe", "vip", "member",
            "giris", "giriş", "uye", "üye", "abone"
        )
        return ArrayList(list.filter { ch ->
            val target = "${ch.name} ${ch.url}".lowercase()
            blocked.none { kw -> target.contains(kw) }
        })
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
            overlayDrawer.bringToFront()
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
        osdBar.bringToFront()
        handler.removeCallbacksAndMessages("osd")
        handler.postAtTime({ osdBar.visibility = View.GONE }, "osd", SystemClock.uptimeMillis() + 4500)
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
        digitOsd.bringToFront()

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
        }, "digit", SystemClock.uptimeMillis() + 1200)
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        if (player != null) return
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 1500, 3000)
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        loadingSpinner.visibility =
                            if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        loadingSpinner.visibility = View.GONE
                        if (retryCount < 2) {
                            retryCount++
                            handler.postDelayed({ loadCurrentChannel() }, 1500)
                        } else {
                            Toast.makeText(
                                this@PlayerActivity,
                                "Yayın açılamadı",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
            }
        playerView.player = player
    }

    @OptIn(UnstableApi::class)
    private fun loadCurrentChannel() {
        if (channelList.isEmpty()) return
        currentIndex = currentIndex.coerceIn(0, channelList.size - 1)
        val ch = channelList[currentIndex]
        osdTitle.text = String.format("%03d  %s | %s", currentIndex + 1, ch.country, ch.name)
        showOsd()

        val url = ch.url.trim()
        val isDirectMedia = url.contains(".m3u8", true) || url.contains(".mp4", true)

        if (!isDirectMedia) {
            player?.stop()
            playerView.visibility = View.GONE
            loadingSpinner.visibility = View.GONE
            webPlayer.visibility = View.VISIBLE
            webPlayer.loadUrl(url)
            return
        }

        webPlayer.stopLoading()
        webPlayer.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        loadingSpinner.visibility = View.VISIBLE
        initializePlayer()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val mediaItem = MediaItem.fromUri(url)
        val exo = player ?: return

        if (url.contains(".m3u8", true)) {
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            exo.setMediaSource(hlsSource)
        } else {
            exo.setMediaItem(mediaItem)
        }
        exo.prepare()
        exo.play()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (overlayDrawer.visibility == View.VISIBLE) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    toggleOverlayDrawer(false)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            if (event.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                handleRemoteDigit(event.keyCode - KeyEvent.KEYCODE_0)
                return true
            }

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    switchChannel(-1); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    switchChannel(1); return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    toggleOverlayDrawer(true); return true
                }
                KeyEvent.KEYCODE_INFO -> {
                    showOsd(); return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    finish(); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        webPlayer.destroy()
        player?.release()
        player = null
    }
}
