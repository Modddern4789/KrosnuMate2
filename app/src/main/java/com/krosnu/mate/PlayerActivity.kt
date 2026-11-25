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
                    view?.evaluateJavascript(autoControllerScript(), null)
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

    private fun autoControllerScript(): String {
        val parts = listOf(
            "(function(){if(window.__krosnuInit)return;window.__krosnuInit=true;",
            "['keydown','keypress','keyup'].forEach(function(ev){",
            "window.addEventListener(ev,function(e){if(e.isTrusted){e.stopImmediatePropagation();e.preventDefault();}},true);",
            "});",
            "var adSec=0;var fsDone=false;",
            "function collectDocs(root,out){out=out||[];if(!root)return out;out.push(root);",
            "root.querySelectorAll('iframe').forEach(function(f){try{if(f.contentDocument)collectDocs(f.contentDocument,out);}catch(e){}});",
            "return out;}",
            "function tap(el){if(!el)return;",
            "try{el.scrollIntoView({block:'center',inline:'center'});}catch(e){}",
            "['pointerdown','mousedown','mouseup','click'].forEach(function(act){",
            "try{el.dispatchEvent(new MouseEvent(act,{bubbles:true,cancelable:true,view:window}));}catch(e){}});",
            "try{el.click();}catch(e){}}",
            "function hideAuth(doc){",
            "doc.querySelectorAll('a,button,div,span,form,section,aside,input').forEach(function(el){",
            "var txt=(el.innerText||el.textContent||'').trim().toLowerCase();",
            "var isAuth=['giriş','giriş yap','üye ol','üyelik','login','sign in','register'].indexOf(txt)>=0;",
            "if(isAuth&&el.offsetWidth<320&&el.offsetHeight<120){el.style.setProperty('display','none','important');}",
            "if(el.tagName&&el.tagName.toLowerCase()==='input'&&el.type==='password'){",
            "var box=el.closest('form,div,section')||el;",
            "if(box&&box.offsetHeight<420){box.style.setProperty('display','none','important');}}});}",
            "function startMedia(docs){docs.forEach(function(doc){",
            "var btns=doc.querySelectorAll('button,div,span,a');",
            "for(var i=0;i<btns.length;i++){var b=btns[i];var c=((b.className||'')+' '+(b.getAttribute('aria-label')||'')).toLowerCase();",
            "if(c.indexOf('play')>=0&&b.offsetParent!==null){tap(b);break;}}",
            "doc.querySelectorAll('video').forEach(function(v){if(v.paused){try{var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}}});});}",
            "function enterFs(docs){for(var i=0;i<docs.length;i++){var doc=docs[i];",
            "if(doc.fullscreenElement||doc.webkitFullscreenElement){fsDone=true;return true;}",
            "var els=doc.querySelectorAll('button,div,span,a');",
            "for(var j=0;j<els.length;j++){var f=els[j];var c=((f.className||'')+' '+(f.getAttribute('aria-label')||'')).toLowerCase();",
            "if(c.indexOf('fullscreen')>=0&&f.offsetParent!==null){tap(f);fsDone=true;return true;}}",
            "var vid=doc.querySelector('video');",
            "if(vid){var req=vid.requestFullscreen||vid.webkitRequestFullscreen;",
            "if(req){try{req.call(vid);fsDone=true;return true;}catch(e){}}}}return false;}",
            "setInterval(function(){var docs=collectDocs(document);",
            "docs.forEach(function(d){hideAuth(d);});",
            "startMedia(docs);",
            "var skipTarget=null;var adVid=null;",
            "for(var i=0;i<docs.length;i++){var d=docs[i];",
            "var list=d.querySelectorAll('button,a,div,span,[role=\"button\"]');",
            "for(var j=0;j<list.length;j++){var el=list[j];",
            "var info=((el.innerText||el.textContent||'')+' '+(el.className||'')+' '+(el.id||'')).toLowerCase();",
            "var hit=info.indexOf('skip')>=0||info.indexOf('reklamı geç')>=0||info.indexOf('geç')>=0||info.indexOf('atla')>=0;",
            "if(hit&&el.offsetParent!==null){skipTarget=el;break;}}",
            "if(skipTarget)break;",
            "var vids=d.querySelectorAll('video');",
            "for(var k=0;k<vids.length;k++){var v=vids[k];if(!v.paused&&v.duration>0&&v.duration<=35){adVid=v;break;}}}",
            "if(skipTarget||adVid){adSec+=1;if(adSec>=15){",
            "if(skipTarget){tap(skipTarget);}",
            "if(adVid&&isFinite(adVid.duration)){try{adVid.currentTime=Math.max(adVid.currentTime,adVid.duration-0.2);}catch(e){}}",
            "adSec=0;fsDone=false;setTimeout(function(){enterFs(collectDocs(document));},500);}}",
            "else{adSec=0;if(!fsDone){enterFs(docs);}}",
            "},1000);})();"
        )
        return parts.joinToString("")
    }

    private fun filterMembershipChannels(list: List<Channel>): ArrayList<Channel> {
        val blockedKeywords = listOf(
            "login", "signin", "sign-in", "register", "subscribe", "vip", "member",
            "giris", "giriş", "uye", "üye", "abone"
        )
        return ArrayList(list.filter { ch ->
            val target = "${ch.name} ${ch.url}".lowercase()
            blockedKeywords.none { kw -> target.contains(kw) }
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
            .setBufferDurationsMs(5000, 15000, 1500, 2500)
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
                            loadCurrentChannel()
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
        val isWebStream = url.startsWith("http://", true) || url.startsWith("https:
