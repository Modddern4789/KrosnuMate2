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
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.io.ByteArrayInputStream

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
        val rawIdx = intent.getIntExtra("index", 0)
        currentIndex = if (channelList.isEmpty()) 0 else rawIdx.coerceIn(0, channelList.size - 1)

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
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
                private val adHosts = listOf(
                    "googlesyndication.com", "doubleclick.net", "googleadservices.com",
                    "adservice.google", "pagead2.", "adnxs.com", "popads.net",
                    "yandex.ru/ads", "an.yandex.ru", "taboola.com", "outbrain.com",
                    "smartcell", "kralbet", "tipobet", "multiwin", "popcash", "adsterra"
                )

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val u = request?.url?.toString()?.lowercase() ?: ""
                    if (adHosts.any { u.contains(it) }) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val u = request?.url?.toString()?.lowercase() ?: return false
                    if (adHosts.any { u.contains(it) }) return true
                    return !(u.startsWith("http://") || u.startsWith("https://"))
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loadingSpinner.visibility = View.GONE
                    CookieManager.getInstance().flush()
                    val kw = channelList.getOrNull(currentIndex)?.matchKeyword ?: ""
                    val js = buildJs(kw)
                    view?.evaluateJavascript(js, null)
                    handler.postDelayed({ view?.evaluateJavascript(js, null) }, 2500)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = callback
                    customVideoContainer.removeAllViews()
                    if (view != null) {
                        customVideoContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
                        customVideoContainer.visibility = View.VISIBLE
                        customVideoContainer.bringToFront()
                    }
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

        loadingSpinner = ProgressBar(this).apply { visibility = View.GONE }
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
            text = "☰ Kanallar"
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
        if (channelList.isNotEmpty()) loadCurrentChannel()
    }
        private fun filterMembershipChannels(raw: List<Channel>): ArrayList<Channel> = ArrayList(raw)

    private fun buildJs(keyword: String): String {
        val kw = keyword.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function(){
                if(window.__krosnuInit) return;
                window.__krosnuInit = true;
                ['keydown','keypress','keyup'].forEach(function(ev){
                    window.addEventListener(ev,function(e){e.stopImmediatePropagation();},true);
                });
                function norm(s){
                    return (s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/ə/gi,'e').toLowerCase().trim();
                }
                var kw = norm('$kw');
                setInterval(function(){
                    document.querySelectorAll('ins.adsbygoogle,[id^="google_ads"],[id^="aswift"],[class*="adsbygoogle"],a[href*="bet"],a[href*="multiwin"],[class*="reklam"],[id*="reklam"]').forEach(function(a){a.remove();});
                    if(kw && !window.__krosnuChDone){
                        var items = Array.prototype.slice.call(document.querySelectorAll('li,a,button,[role="button"],[onclick],div,span')).reverse();
                        var found = null;
                        for(var i=0;i<items.length;i++){
                            var el = items[i];
                            if(el.children.length > 1) continue;
                            var t = norm(el.innerText || el.getAttribute('title') || el.getAttribute('alt') || '');
                            if(t && (t === kw || (t.length < 28 && t.indexOf(kw) !== -1))){
                                found = el;
                                break;
                            }
                        }
                        if(found){
                            var r = found.getBoundingClientRect();
                            if((found.offsetParent === null || r.height === 0) && !window.__krosnuMenuOpen){
                                window.__krosnuMenuOpen = true;
                                var menuBtns = Array.prototype.slice.call(document.querySelectorAll('button,a,div,span')).reverse();
                                for(var m=0;m<menuBtns.length;m++){
                                    if(norm(menuBtns[m].innerText) === 'kanal'){ menuBtns[m].click(); break; }
                                }
                            } else if(r.height > 0) {
                                window.__krosnuChDone = true;
                                found.click();
                            }
                        }
                    }
                    var btns = document.querySelectorAll('button,a,[role="button"],.vjs-big-play-button,.vjs-fullscreen-control,.ytp-ad-skip-button,.plyr__control');
                    for(var j=0;j<btns.length;j++){
                        var b = btns[j];
                        if(b.offsetParent === null) continue;
                        var txt = norm(b.innerText);
                        if(txt.indexOf('sayta') !== -1 || txt.indexOf('kecid') !== -1) continue;
                        var meta = norm((b.className||'')+' '+(b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||''));
                        if(txt.length < 25 && /\b(skip|skip ad|reklami gec|reklami atla|buraxmaq|reklami burax)\b/.test(txt + ' ' + meta)){
                            b.click();
                        }
                        if(!window.__krosnuPlayDone && (txt==='izle' || txt==='baslat' || txt==='oynat' || meta.indexOf('vjs-big-play')!==-1 || meta.indexOf('plyr__control--overlaid')!==-1)){
                            window.__krosnuPlayDone = true;
                            b.click();
                        }
                        if(!window.__krosnuFsDone && (txt==='tam ekran' || meta.indexOf('vjs-fullscreen')!==-1 || meta.indexOf('fullscreen')!==-1)){
                            window.__krosnuFsDone = true;
                            b.click();
                        }
                    }
                    if(!kw || window.__krosnuChDone){
                        document.querySelectorAll('header,nav,.header,.top-bar').forEach(function(h){ h.style.display = 'none'; });
                    }
                    var vids = document.querySelectorAll('video');
                    if(vids.length > 0){
                        var vid = vids[0];
                        vid.muted = false;
                        if(vid.paused) vid.play().catch(function(){});
                        var p = vid.parentElement;
                        while(p && p !== document.body){
                            p.style.setProperty('position', 'static', 'important');
                            p.style.setProperty('transform', 'none', 'important');
                            p.style.setProperty('overflow', 'visible', 'important');
                            p.style.setProperty('z-index', 'auto', 'important');
                            p = p.parentElement;
                        }
                        document.documentElement.style.overflow = 'hidden';
                        document.body.style.overflow = 'hidden';
                        vid.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;';
                        return;
                    }
                    var frms = document.querySelectorAll('iframe');
                    var best = null, bestArea = 0;
                    for(var f=0;f<frms.length;f++){
                        var fr = frms[f];
                        var src = (fr.src||'').toLowerCase();
                        if(/doubleclick|googlesyndication|adservice|yandex|banner|ads|recaptcha|facebook|twitter|bet/.test(src)) continue;
                        var area = fr.offsetWidth * fr.offsetHeight;
                        if(fr.offsetWidth > 180 && fr.offsetHeight > 100 && area > bestArea){
                            bestArea = area;
                            best = fr;
                        }
                    }
                    if(best){
                        var fp = best.parentElement;
                        while(fp && fp !== document.body){
                            fp.style.setProperty('position', 'static', 'important');
                            fp.style.setProperty('transform', 'none', 'important');
                            fp.style.setProperty('overflow', 'visible', 'important');
                            fp.style.setProperty('z-index', 'auto', 'important');
                            fp = fp.parentElement;
                        }
                        document.documentElement.style.overflow = 'hidden';
                        document.body.style.overflow = 'hidden';
                        best.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483646!important;border:0!important;background:#000!important;';
                    }
                }, 1000);
            })();
        """.trimIndent()
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
                currentIndex = num - 1
                loadCurrentChannel()
            } else {
                Toast.makeText(this, "$num numaralı kanal yok", Toast.LENGTH_SHORT).show()
            }
        }, "digit", SystemClock.uptimeMillis() + 1200)
    }

    private fun loadCurrentChannel() {
        val ch = channelList[currentIndex]
        retryCount = 0
        osdTitle.text = String.format("%03d  %s | %s", currentIndex + 1, ch.country, ch.name)
        showOsd()
        loadingSpinner.visibility = View.VISIBLE

        val isDirect = ch.url.contains(".m3u8", true) ||
            ch.url.contains(".mpd", true) ||
            ch.source == "M3U8"

        if (isDirect) {
            webPlayer.stopLoading()
            webPlayer.loadUrl("about:blank")
            webPlayer.visibility = View.GONE
            customVideoContainer.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            playStream(ch.url, ch.url)
        } else {
            player?.stop()
            playerView.visibility = View.GONE
            customVideoContainer.visibility = View.GONE
            webPlayer.visibility = View.VISIBLE
            webPlayer.loadUrl(ch.url)
        }
    }

    @OptIn(UnstableApi::class)
    private fun playStream(streamUrl: String, referer: String) {
        player?.release()
        val headers = mapOf(
            "Referer" to referer,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 2500, 5000)
            .build()

        val exo = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        player = exo
        playerView.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    loadingSpinner.visibility = View.GONE
                    retryCount = 0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < 3) {
                    retryCount++
                    handler.postDelayed({ playStream(streamUrl, referer) }, 1500)
                } else {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(this@PlayerActivity, "Yayın açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        })

        val mediaItem = MediaItem.fromUri(streamUrl)
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        exo.setMediaSource(hlsSource)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (overlayDrawer.visibility == View.VISIBLE) {
            if (event.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_BACK) {
                toggleOverlayDrawer(false)
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        val isRemoteKey = code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_CHANNEL_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_CHANNEL_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_INFO || code == KeyEvent.KEYCODE_BACK

        if (!isRemoteKey) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true

        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            handleRemoteDigit(code - KeyEvent.KEYCODE_0)
            return true
        }
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { switchChannel(-1); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { switchChannel(1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { toggleOverlayDrawer(true); true }
            KeyEvent.KEYCODE_INFO -> { showOsd(); true }
            KeyEvent.KEYCODE_BACK -> { finish(); true }
            else -> true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CookieManager.getInstance().flush()
        handler.removeCallbacksAndMessages(null)
        webPlayer.destroy()
        player?.release()
        player = null
    }
}
