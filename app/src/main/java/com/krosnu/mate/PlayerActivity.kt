package com.krosnu.mate

import org.json.JSONArray
import java.io.Serializable
import java.net.URL

data class Channel(
    val id: String,
    val country: String,
    val source: String,
    val name: String,
    val category: String,
    val url: String,
    val matchKeyword: String = ""
) : Serializable

object ChannelRepository {

    const val M3U_AZ = "https://iptv-org.github.io/iptv/countries/az.m3u"
    const val M3U_TR = "https://iptv-org.github.io/iptv/countries/tr.m3u"
    const val M3U_RU = "https://iptv-org.github.io/iptv/countries/ru.m3u"

    private val AZ_ORDER = listOf(
        "xəzər", "xezer", "khazar", "aztv", "az tv", "ictimai", "itv",
        "atv", "azad", "space", "arb 24", "arb24", "arb",
        "real tv", "realtv", "baku tv", "bakutv", "idman", "cbc sport",
        "cbc", "medeniyyet", "mədəniyyət", "medeniyet", "dunya", "dünya"
    )

    private val TR_ORDER = listOf(
        "trt 1", "trt1", "atv", "kanal d", "show", "star", "tv8", "tv 8",
        "now", "kanal 7", "kanal7", "beyaz", "trt haber", "cnn",
        "habertürk", "haberturk", "ntv", "a haber", "sözcü", "sozcu",
        "trt spor", "a spor"
    )

    fun sortGroupedByChannel(list: List<Channel>): List<Channel> {
        return list.sortedWith(compareBy<Channel>(
            {
                when (it.country) {
                    "AZ" -> 0
                    "TR" -> 1
                    "RU" -> 2
                    else -> 3
                }
            },
            {
                val lower = it.name.lowercase()
                val order = if (it.country == "AZ") AZ_ORDER else if (it.country == "TR") TR_ORDER else emptyList()
                val idx = order.indexOfFirst { kw -> lower.contains(kw) }
                if (idx == -1) 999 else idx
            },
            { it.name.lowercase() }
        ))
    }

    fun getDefaultChannels(): List<Channel> = sortGroupedByChannel(
        listOf(
            // YODA.AZ — 29 KANAL (Üyeliksiz, Direkt Video)
            Channel("yoda-1", "AZ", "YODA", "AzTV (Yoda)", "Ümumi", "https://yoda.az/tv/", "AZTV"),
            Channel("yoda-2", "AZ", "YODA", "Xəzər TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "XƏZƏR"),
            Channel("yoda-3", "AZ", "YODA", "İctimai TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "İctimai"),
            Channel("yoda-4", "AZ", "YODA", "Baku TV (Yoda)", "Xəbər", "https://yoda.az/tv/", "Baku TV"),
            Channel("yoda-5", "AZ", "YODA", "İdman TV (Yoda)", "İdman", "https://yoda.az/tv/", "İdman TV"),
            Channel("yoda-6", "AZ", "YODA", "Biznes TV (Yoda)", "Xəbər", "https://yoda.az/tv/", "BIZNES TV"),
            Channel("yoda-7", "AZ", "YODA", "Naxçıvan TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "Naxçıvan TV"),
            Channel("yoda-8", "AZ", "YODA", "Real TV (Yoda)", "Xəbər", "https://yoda.az/tv/", "Real TV"),
            Channel("yoda-9", "AZ", "YODA", "Qafqaz TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "Qafqaz TV"),
            Channel("yoda-10", "AZ", "YODA", "ATV (Yoda)", "Ümumi", "https://yoda.az/tv/", "ATV"),
            Channel("yoda-11", "AZ", "YODA", "ARB 24 (Yoda)", "Xəbər", "https://yoda.az/tv/", "ARB24"),
            Channel("yoda-12", "AZ", "YODA", "APA TV (Yoda)", "Xəbər", "https://yoda.az/tv/", "APA TV"),
            Channel("yoda-13", "TR", "YODA", "Haber Global (Yoda)", "Xəbər", "https://yoda.az/tv/", "Haber Global"),
            Channel("yoda-14", "AZ", "YODA", "TMB TV (Yoda)", "Musiqi", "https://yoda.az/tv/", "TMB TV"),
            Channel("yoda-15", "AZ", "YODA", "TMB Azərbaycan (Yoda)", "Musiqi", "https://yoda.az/tv/", "TMB Azərbaycan"),
            Channel("yoda-16", "AZ", "YODA", "ARB (Yoda)", "Ümumi", "https://yoda.az/tv/", "ARB"),
            Channel("yoda-17", "AZ", "YODA", "Kanal S (Yoda)", "Ümumi", "https://yoda.az/tv/", "Kanal S"),
            Channel("yoda-18", "AZ", "YODA", "Kanal 35 (Yoda)", "Ümumi", "https://yoda.az/tv/", "Kanal 35"),
            Channel("yoda-19", "AZ", "YODA", "ELTV (Yoda)", "Ümumi", "https://yoda.az/tv/", "ELTV"),
            Channel("yoda-20", "AZ", "YODA", "ARB Günəş (Yoda)", "Uşaq", "https://yoda.az/tv/", "ARB GÜNƏŞ"),
            Channel("yoda-21", "AZ", "YODA", "CBC (Yoda)", "Ümumi", "https://yoda.az/tv/", "CBC"),
            Channel("yoda-22", "AZ", "YODA", "Mədəniyyət (Yoda)", "Ümumi", "https://yoda.az/tv/", "Mədəniyyət"),
            Channel("yoda-23", "AZ", "YODA", "Space TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "Space"),
            Channel("yoda-24", "AZ", "YODA", "Vostok TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "Vostok TV"),
            Channel("yoda-25", "AZ", "YODA", "Show Plus TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "Show Plus TV"),
            Channel("yoda-26", "AZ", "YODA", "MTV Azerbaijan (Yoda)", "Musiqi", "https://yoda.az/tv/", "MTV Azerbaijan"),
            Channel("yoda-27", "AZ", "YODA", "SH TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "SH TV"),
            Channel("yoda-28", "AZ", "YODA", "VIP HD (Yoda)", "Ümumi", "https://yoda.az/tv/", "VIP HD"),
            Channel("yoda-29", "AZ", "YODA", "Dunya TV (Yoda)", "Ümumi", "https://yoda.az/tv/", "Dunya TV"),

            // CANLITV.COM — AZƏRBAYCAN & TÜRKİYƏ (Üyeliksiz)
            Channel("az-itv", "AZ", "ITV", "İTV Canlı (Resmi)", "Ümumi", "https://live.itv.az/"),
            Channel("az-1", "AZ", "CANLITV", "Xəzər TV (CanlıTV)", "Ümumi", "https://canlitv.com/xezer-tv"),
            Channel("az-2", "AZ", "CANLITV", "AzTV (CanlıTV)", "Ümumi", "https://canlitv.com/az-tv"),
            Channel("az-3", "AZ", "CANLITV", "İctimai TV (CanlıTV)", "Ümumi", "https://canlitv.com/ictimai-tv"),
            Channel("az-4", "AZ", "CANLITV", "ATV Azad (CanlıTV)", "Ümumi", "https://canlitv.com/azad-tv"),
            Channel("az-5", "AZ", "CANLITV", "Space TV (CanlıTV)", "Ümumi", "https://canlitv.com/space-tv"),
            Channel("az-6", "AZ", "CANLITV", "ARB (CanlıTV)", "Ümumi", "https://canlitv.com/arb-tv"),
            Channel("az-7", "AZ", "CANLITV", "ARB 24 (CanlıTV)", "Xəbər", "https://canlitv.com/arb-24-tv"),
            Channel("az-8", "AZ", "CANLITV", "CBC Sport (CanlıTV)", "İdman", "https://canlitv.com/cbc-sport-izle"),
            Channel("az-9", "AZ", "CANLITV", "İdman TV (CanlıTV)", "İdman", "https://canlitv.com/idman-tv"),
            Channel("az-10", "AZ", "CANLITV", "Dünya TV (CanlıTV)", "Ümumi", "https://canlitv.com/dunyatv-az"),
            Channel("az-11", "AZ", "CANLITV", "ARB Günəş (CanlıTV)", "Uşaq", "https://canlitv.com/arb-gunes-tv"),

            // OPERATIVMM.AZ & TVSTREAM.AZ
            Channel("opr-1", "AZ", "OPERATIV", "AzTV (OperativMM)", "Ümumi", "https://operativmm.az/az/canli-tv", "AZTV"),
            Channel("opr-2", "AZ", "OPERATIV", "İctimai TV (OperativMM)", "Ümumi", "https://operativmm.az/az/canli-tv", "İctimai TV"),
            Channel("opr-3", "AZ", "OPERATIV", "ATV (OperativMM)", "Ümumi", "https://operativmm.az/az/canli-tv", "ATV"),
            Channel("opr-4", "AZ", "OPERATIV", "Xəzər TV (OperativMM)", "Ümumi", "https://operativmm.az/az/canli-tv", "Xəzər TV"),
            Channel("opr-5", "AZ", "OPERATIV", "Space TV (OperativMM)", "Ümumi", "https://operativmm.az/az/canli-tv", "Space TV"),
            Channel("opr-6", "AZ", "OPERATIV", "ARB (OperativMM)", "Ümumi", "https://operativmm.az/az/canli-tv", "ARB"),
            Channel("opr-7", "AZ", "OPERATIV", "Baku TV (OperativMM)", "Xəbər", "https://operativmm.az/az/canli-tv", "Baku TV"),
            Channel("opr-8", "AZ", "OPERATIV", "Real TV (OperativMM)", "Xəbər", "https://operativmm.az/az/canli-tv", "Real TV"),
            Channel("opr-9", "AZ", "OPERATIV", "ARB 24 (OperativMM)", "Xəbər", "https://operativmm.az/az/canli-tv", "ARB 24"),
            Channel("opr-10", "AZ", "OPERATIV", "İdman TV (OperativMM)", "İdman", "https://operativmm.az/az/canli-tv", "İdman TV"),
            Channel("opr-11", "AZ", "OPERATIV", "CBC Sport (OperativMM)", "İdman", "https://operativmm.az/az/canli-tv", "CBC Sport"),
            Channel("tvs-1", "AZ", "TVSTREAM", "Xəzər TV (TVStream)", "Ümumi", "https://tvstream.az/#ana-sehife", "XƏZƏR TV"),
            Channel("tvs-2", "AZ", "TVSTREAM", "AzTV HD (TVStream)", "Ümumi", "https://tvstream.az/#ana-sehife", "AZTV HD"),
            Channel("tvs-3", "AZ", "TVSTREAM", "İctimai TV (TVStream)", "Ümumi", "https://tvstream.az/#ana-sehife", "ICTIMAI TV"),

            // TÜRKİYƏ & İDMAN (CANLITV.COM)
            Channel("tr-tvf", "TR", "TVFREKANSLAR", "TV Frekanslar (Canlı)", "Ümumi", "https://amp.tvfrekanslar.com/cntr"),
            Channel("tr-t24", "TR", "TARAFTARIUM", "Taraftarium24 (İdman)", "İdman", "https://taraftarium24turk-izle.net/"),
            Channel("tr-1", "TR", "CANLITV", "TRT 1 (CanlıTV)", "Ümumi", "https://canlitv.com/trt1-izle"),
            Channel("tr-2", "TR", "CANLITV", "ATV (CanlıTV)", "Ümumi", "https://canlitv.com/atv-canli"),
            Channel("tr-3", "TR", "CANLITV", "Kanal D (CanlıTV)", "Ümumi", "https://canlitv.com/kanal-d-canli-yayin"),
            Channel("tr-4", "TR", "CANLITV", "Show TV (CanlıTV)", "Ümumi", "https://canlitv.com/show-tv-izle-2"),
            Channel("tr-5", "TR", "CANLITV", "Star TV (CanlıTV)", "Ümumi", "https://canlitv.com/star-tv-canli"),
            Channel("tr-6", "TR", "CANLITV", "TV8 (CanlıTV)", "Ümumi", "https://canlitv.com/tv8-izle-4"),
            Channel("tr-7", "TR", "CANLITV", "Now TV (CanlıTV)", "Ümumi", "https://canlitv.com/now-tv-canli-yayin"),
            Channel("tr-8", "TR", "CANLITV", "Kanal 7 (CanlıTV)", "Ümumi", "https://canlitv.com/kanal7"),
            Channel("tr-9", "TR", "CANLITV", "Beyaz TV (CanlıTV)", "Ümumi", "https://canlitv.com/beyaz-tv"),
            Channel("tr-10", "TR", "CANLITV", "TRT Haber (CanlıTV)", "Xəbər", "https://canlitv.com/trt-haber-izle"),
            Channel("tr-11", "TR", "CANLITV", "CNN Türk (CanlıTV)", "Xəbər", "https://canlitv.com/cnn-turk-izle-1"),
            Channel("tr-12", "TR", "CANLITV", "Habertürk (CanlıTV)", "Xəbər", "https://canlitv.com/haberturk"),
            Channel("tr-13", "TR", "CANLITV", "NTV (CanlıTV)", "Xəbər", "https://canlitv.com/ntv-canli-izle"),
            Channel("tr-14", "TR", "CANLITV", "Haber Global (CanlıTV)", "Xəbər", "https://canlitv.com/haber-global"),
            Channel("tr-15", "TR", "CANLITV", "Sözcü TV (CanlıTV)", "Xəbər", "https://canlitv.com/sozcu-tv-izle"),
            Channel("tr-16", "TR", "CANLITV", "TRT Spor (CanlıTV)", "İdman", "https://canlitv.com/trt-spor-izle"),
            Channel("tr-17", "TR", "CANLITV", "TRT Spor Yıldız (CanlıTV)", "İdman", "https://canlitv.com/trt-spor-2"),
            Channel("tr-18", "TR", "CANLITV", "A Spor (CanlıTV)", "İdman", "https://canlitv.com/aspor-canli-izle"),
            Channel("tr-19", "TR", "CANLITV", "TRT Belgesel (CanlıTV)", "Belgesel", "https://canlitv.com/trt-belgesel")
        )
    )

    fun fetchM3UChannels(m3uUrl: String, countryTag: String): List<Channel> {
        return try {
            val text = URL(m3uUrl).readText()
            val lines = text.lines()
            val list = mutableListOf<Channel>()
            var currentName: String? = null
            for (raw in lines) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF:")) {
                    val idx = line.lastIndexOf(',')
                    currentName = if (idx != -1) line.substring(idx + 1).trim() else "Kanal"
                } else if (line.startsWith("http") && currentName != null) {
                    list.add(
                        Channel(
                            id = "m3u-$countryTag-${list.size + 1}",
                            country = countryTag,
                            source = "M3U8",
                            name = currentName,
                            category = "Canlı",
                            url = line
                        )
                    )
                    currentName = null
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fetchRemoteChannels(jsonUrl: String): List<Channel>? {
        return try {
            val text = URL(jsonUrl).readText()
            val arr = JSONArray(text)
            val result = mutableListOf<Channel>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    Channel(
                        id = obj.optString("id", "rem-$i"),
                        country = obj.optString("country", "AZ"),
                        source = obj.optString("source", "REMOTE"),
                        name = obj.optString("name", "Kanal ${i + 1}"),
                        category = obj.optString("category", "Ümumi"),
                        url = obj.getString("url"),
                        matchKeyword = obj.optString("matchKeyword", "")
                    )
                )
            }
            if (result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }
}
