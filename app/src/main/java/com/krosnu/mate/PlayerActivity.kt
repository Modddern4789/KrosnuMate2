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

        val rawChannels = (intent.getSerializableExtra("channels") as? ArrayList<Channel>) ?: arrayListOf()
        channelList = filterMembershipChannels(rawChannels)
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
                ): Boolean = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val js = """
                        (function() {
                            if (window.__krosnuPlayerAutoInstalled) return;
                            window.__krosnuPlayerAutoInstalled = true;

                            // Site icinde klavye/kumanda tuslarini engelle (sadece mouse/touch kalsin)
                            ['keydown', 'keypress', 'keyup'].forEach(function(ev) {
                                window.addEventListener(ev, function(e) {
                                    if (e.isTrusted) {
                                        e.stopImmediatePropagation();
                                        e.preventDefault();
                                    }
                                }, true);
                            });

                            var adTimer = 0;
                            var autoFsDone = false;

                            function getAllDocuments(rootDoc, list) {
                                list = list || [];
                                if (!rootDoc) return list;
                                list.push(rootDoc);
                                var frames = rootDoc.querySelectorAll('iframe');
                                frames.forEach(function(f) {
                                    try {
                                        if (f.contentDocument) {
                                            getAllDocuments(f.contentDocument, list);
                                        }
                                    } catch (err) {}
                                });
                                return list;
                            }

                            function fireClick(el) {
                                if (!el) return;
                                try { el.scrollIntoView({ block: 'center', inline: 'center' }); } catch (e) {}
                                ['pointerdown', 'mousedown', 'mouseup', 'click'].forEach(function(type) {
                                    try {
                                        el.dispatchEvent(new MouseEvent(type, {
                                            bubbles: true,
                                            cancelable: true,
                                            view: window
                                        }));
                                    } catch (e) {}
                                });
                                try { el.click(); } catch (e) {}
                            }

                            function hideMembershipUi(doc) {
                                doc.querySelectorAll('a,button,div,span,form,section,aside,input').forEach(function(el) {
                                    var t = (el.innerText || el.textContent || '').trim().toLowerCase();
                                    var isAuthBtn =
                                        t === 'giriş' || t === 'giriş yap' || t === 'üye ol' ||
                                        t === 'üyelik' || t === 'login' || t === 'sign in' || t === 'register';
                                    if (isAuthBtn && el.offsetWidth < 320 && el.offsetHeight < 120) {
                                        el.style.setProperty('display', 'none', 'important');
                                    }
                                    if (el.tagName && el.tagName.toLowerCase() === 'input' && el.type === 'password') {
                                        var box = el.closest('form,div,section') || el;
                                        if (box && box.offsetHeight < 420) {
                                            box.style.setProperty('display', '
                                            
