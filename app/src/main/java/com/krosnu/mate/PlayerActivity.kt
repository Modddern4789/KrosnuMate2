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
                    val script = buildAutomationScript()
                    view?.evaluateJavascript(script, null)
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

    private fun buildAutomationScript(): String {
        return "(function(){" +
            "if(window.__krosnuAuto)return;window.__krosnuAuto=1;" +
            "['keydown','keypress','keyup'].forEach(function(ev){" +
            "window.addEventListener(ev,function(e){if(e.isTrusted){e.stopImmediatePropagation();e.preventDefault();}},true);" +
            "});" +
            "var adTimer=0;var fsDone=false;" +
            "function docsOf(root,out){out=out||[];if(!root)return out;out.push(root);" +
            "root.querySelectorAll('iframe').forEach(function(f){try{if(f.contentDocument)docsOf(f.contentDocument,out);}catch(e){}});" +
            "return out;}" +
            "function clickEl(el){if(!el)return;" +
            "try{el.scrollIntoView({block:'center',inline:'center'});}catch(e){}" +
            "['pointerdown','mousedown','mouseup','click'].forEach(function(t){" +
            "try{el.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window}));}catch(e){}});" +
            "try{el.click();}catch(e){}}" +
            "function hideAuth(d){" +
            "d.querySelectorAll('a,button,div,span,form,section,aside,input').forEach(function(el){" +
            "var txt=(el.innerText||el.textContent||'').trim().toLowerCase();" +
            "var auth=['giriş','giriş yap','üye ol','üyelik','login','sign in','register'].indexOf(txt)>=0;" +
            "if(auth&&el.offsetWidth<320&&el.offsetHeight<120){el.style.setProperty('display','none','important');}" +
            "if(el.tagName&&el.tagName.toLowerCase()==='input'&&el.type==='password'){" +
            "var box=el.closest('form,div,section')||el;" +
            "if(box&&box.offsetHeight<420){box.style.setProperty('display','none','important');}}" +
            "});}" +
            "function autoPlay(ds){ds.forEach(function(d){" +
            "var b=d.querySelector('button[aria-label*=\"play\" i],button[title*=\"play\" i],.vjs-big-play-button,.jw-icon-playback,[class*=\"play-btn\"],[class*=\"play_button\"],[class*=\"big-play\"]');" +
            "if(b&&b.offsetParent!==null){clickEl(b);}" +
            "d.querySelectorAll('video').forEach(function(v){if(v.paused){try{var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}}});" +
            "});}" +
            "function autoFs(ds){for(var i=0;i<ds.length;i++){var d=ds[i];" +
            "if(d.fullscreenElement||d.webkitFullscreenElement){fsDone=true;return true;}" +
            "var f=d.querySelector('button[aria-label*=\"fullscreen\" i],button[title*=\"fullscreen\" i],.vjs-fullscreen-control,.jw-icon-fullscreen,[class*=\"fullscreen-btn\"],[class*=\"fullscreen\"]');" +
            "if(f&&f.offsetParent!==null){clickEl(f);fsDone=true;return true;}" +
            "var v=d.querySelector('video');" +
            "if(v){var r=v.requestFullscreen||v.webkitRequestFullscreen||v.mozRequestFullScreen;" +
            "if(r){try{r.call(v);fsDone=true;return true;}catch(e){}}}}" +
            "return false;}" +
            "setInterval(function(){" +
            "var ds=docsOf(document);" +
            "ds.forEach(function(d){hideAuth(d);});" +
            "autoPlay(ds);" +
            "var skip=null;var adVid=null;" +
            "for(var i=0;i<ds.length;i++){var d=ds[i];" +
            "var c=Array.from(d.querySelectorAll('button,a,div,span,[role=\"button\"]')).find(function(el){" +
            "var t=(el.innerText||el.textContent||'').trim().toLowerCase();" +
            "var cl=(el.className||'').toString().toLowerCase();" +
            "var id=(el.id||'').toString().toLowerCase();" +
            "var mT=t.indexOf('skip')>=0||t.indexOf('reklamı geç')>=0||t.indexOf('geç')>=0||t.indexOf('atla')>=0;" +
            "var mA=cl.indexOf('skip')>=0||id.indexOf('skip')>=0||cl.indexOf('ad-skip')>=0||cl.indexOf('close-ad')>=0;" +
            "var rc=el.getBoundingClientRect?el.getBoundingClientRect():null;" +
            "var vis=(el.offsetParent!==null)||(rc&&rc.width>0&&rc.height>0);" +
            "return vis&&(mT||mA);});" +
            "if(c){skip=c;break;}" +
            "var v=Array.from(d.querySelectorAll('video')).find(function(x){return !x.paused&&x.duration>0&&x.duration<=35;});" +
            "if(v&&!adVid){adVid=v;}}" +
            "if(skip||adVid){adTimer+=1;if(adTimer>=15){" +
            "if(skip){clickEl(skip);}" +
            "if(adVid&&isFinite(adVid.duration)){try{adVid.currentTime=Math.max(adVid.currentTime,adVid.duration-0.2);}catch(e){}}" +
            "adTimer=0;fsDone=false;" +
            "setTimeout(function(){autoFs(docsOf(document));},500);}}" +
            "else{adTimer=0;if
        
