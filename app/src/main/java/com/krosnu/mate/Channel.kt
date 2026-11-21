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

    private fun blog(country: String, name: String, cat: String = "Ümumi"): Channel =
        Channel("bct-${name.hashCode()}", country, "CANLITVIZLE", "$name (BlogCanli)", cat, "https://blog.canlitvizle.tv/", name)

    fun getDefaultChannels(): List<Channel> {
        val list = mutableListOf(
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
            Channel("ctm-1", "AZ", "CANLITVMOB", "Xəzər TV (CanlıTV.mobile)", "Ümumi", "https://www.canlitv.mobile/xezer-tv"),
            Channel("ctm-2", "AZ", "CANLITVMOB", "CBC Sport (CanlıTV.mobile)", "İdman", "https://www.canlitv.mobile/cbcsport-canli"),
            Channel("ctm-3", "AZ", "CANLITVMOB", "İctimai TV (CanlıTV.mobile)", "Ümumi", "https://www.canlitv.mobile/ictimai-tv"),
            Channel("ctm-4", "AZ", "CANLITVMOB", "İdman TV (CanlıTV.mobile)", "İdman", "https://www.canlitv.mobile/idmantvcanli-izle"),
            Channel("tvvin-1", "TR", "TVVIN", "TV.VIN Canlı Yayınlar", "Ümumi", "https://tv.vin/live"),
            Channel("az-itv", "AZ", "ITV", "İTV Canlı (Resmi)", "Ümumi", "https://live.itv.az/"),
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
            Channel("mb-az-1", "AZ", "MEDIABAY", "AzTV (Mediabay)", "Ümumi", "https://mediabay.tv/tv", "AzTV"),
            Channel("mb-az-2", "AZ", "MEDIABAY", "İctimai TV (Mediabay)", "Ümumi", "https://mediabay.tv/tv", "Ictimai TV"),
            Channel("mb-az-3", "AZ", "MEDIABAY", "Xəzər TV (Mediabay)", "Ümumi", "https://mediabay.tv/tv", "Xezer"),
            Channel("mb-ru-1", "RU", "MEDIABAY", "Россия 24 (Mediabay)", "Xəbər", "https://mediabay.tv/tv", "Россия 24"),
            Channel("mb-ru-2", "RU", "MEDIABAY", "Мир 24 (Mediabay)", "Xəbər", "https://mediabay.tv/tv", "Мир 24"),
            Channel("tvs-1", "AZ", "TVSTREAM", "Xəzər TV (TVStream)", "Ümumi", "https://tvstream.az/#ana-sehife", "XƏZƏR TV"),
            Channel("tvs-2", "AZ", "TVSTREAM", "AzTV HD (TVStream)", "Ümumi", "https://tvstream.az/#ana-sehife", "AZTV HD"),
            Channel("tvs-3", "AZ", "TVSTREAM", "İctimai TV (TVStream)", "Ümumi", "https://tvstream.az/#ana-sehife", "ICTIMAI TV"),
            Channel("az-1", "AZ", "CANLITV", "Xəzər TV (CanlıTV)", "Ümumi", "https://canlitv.com/xezer-tv"),
            Channel("az-2", "AZ", "CANLITV", "AzTV (CanlıTV)", "Ümumi", "https://canlitv.com/az-tv"),
            Channel("az-3", "AZ", "CANLITV", "İctimai TV (CanlıTV)", "Ümumi", "https://canlitv.com/ictimai-tv"),
            Channel("tr-tvf", "TR", "TVFREKANSLAR", "TV Frekanslar (Canlı)", "Ümumi", "https://amp.tvfrekanslar.com/cntr"),
            Channel("tr-t24", "TR", "TARAFTARIUM", "Taraftarium24 (İdman)", "İdman", "https://taraftarium24turk-izle.net/")
        )

        val blogAz = listOf(
            "Az Tv", "CBC Sport", "İctimai Tv", "Arb 24", "Arb Tv", "Xezer Tv", "TMB Tv",
            "İnteraz Tv", "CBC Az Tv", "Sehiyye Tv", "Real Tv", "Dünya Tv", "İdman Tv",
            "Günaz Tv", "Aznews Tv", "ATV Azad Tv", "Baku Tv", "ARB Güneş",
            "Medeniyet Tv", "NTV (Naxçıvan TV)", "Kanal S"
        )
        blogAz.forEach { list.add(blog("AZ", it)) }

        val blogTr = listOf(
            "TRT 1", "ATV", "Tv8", "Kanal D", "Show Tv", "Now TV", "Kanal 7", "Star Tv",
            "Beyaz Tv", "A2 Tv", "TRT Haber", "A Haber", "Habertürk Tv", "CNN Türk",
            "TGRT Haber", "Halk Tv", "Sözcü Tv", "NTV", "Ülke Tv", "Ulusal Kanal",
            "Bloomberg HT", "Akit Tv", "TLC Tv", "D Max", "TRT Spor", "FB Tv", "A Spor",
            "ATV Avrupa", "Euro Star", "Euro D", "Tv8,5", "Tv8 İnt", "TBMM Tv", "TRT 2",
            "TRT Spor 2", "TRT Çocuk", "TRT Müzik", "TRT Belgesel", "TRT Kürdi", "TRT Türk",
            "TRT Diyanet Çocuk", "TRT World", "TRT Avaz", "TRT Eba İlkokul", "TRT Eba Ortaokul",
            "TGRT Belgesel", "PowerTürk Tv", "Number One Tv", "Number One Türk", "Slow Karadeniz Tv",
            "TJK Tv", "Gaziantep Olay Tv", "Power Tv", "Vizyon 58", "Toprak Tv", "Kanal T",
            "TV A", "ON4 Tv", "Güneydoğu TV", "Lider Haber Tv", "NR1 Türk Tv", "TRT Eba Lise",
            "Kent Türk TV", "Mercan Tv", "Kanal 24", "As TV", "Tv 35", "Konya Olay Tv",
            "Damar Tv", "GS Tv", "Tarim Tv", "Bir Tv", "Number One Damar", "TV2",
            "NR1 ASK TV", "Bengütürk Tv", "Number One Dance", "Üniversite Tv", "Ekin Tv",
            "Trakya Türk Tv", "Rumeli Tv", "Bizimev Tv", "Yaşam Tv", "brt 1 Tv", "Güneş Tv",
            "Tek Rümeli TV", "Milli Piyango Tv", "Ful Tv", "Berat Tv", "Cem Tv", "Tv 2020",
            "Tatlıses Tv", "Manisa Tv", "Sports Tv", "Plus Music", "Kanal Ege", "Kanal 42",
            "Habitat Tv", "Kidz Animez", "TGRT EU", "BBN Türk", "Beykent Tv", "Kon Tv",
            "Vav Tv", "Olay TV", "Çay Tv", "Minika Çocuk", "Minika GO", "TV 264",
            "Tokat Süper Tv", "Bitlis Tv", "Diyalog Tv", "Dalga Tv", "Euro 90 Tv", "Kral Tv",
            "Kral Pop Tv", "Vatan Tv", "Tv 360", "Dream Türk", "Marneuli Tv", "Line Tv",
            "İBB TV", "Lalegül Tv", "Semerkand Tv", "TV5", "Meltem Tv", "Bahartürk Tv",
            "Show Türk Tv", "Akıllı Tv", "Gözde Tv", "Flash Tv", "Tay Tv", "Süper Tv",
            "Ada Tv", "Abant Tv", "Medya Haber Tv", "Beşiktaş Tv", "Mesaj Tv", "Köy Tv",
            "Hunat Tv", "Kanal 12", "Deniz Postası", "Kanal Z", "TRT Arapça", "Olay Türk",
            "Tivibu Spor", "FRT Tv", "Dream Tv", "Kay Tv", "Smart Spor HD", "Life Tv",
            "S Sport", "Yol Tv", "Kanal D Romanya", "Tjk Tv 2", "HRT Akdeniz Tv", "Damla Tv",
            "Cadde Tv", "Med Müzik Tv", "GRT Tv", "Cine5", "World Türk", "Yeşilyol Tv",
            "Yeşilçam Tv", "Kanal B", "Cine1", "Mek Tv", "TVNET", "Aksaray ART Tv",
            "Kanal Avrupa", "Sam Tv", "Tempo Tv", "İkra Tv", "Karadeniz Tv", "Dost Tv",
            "Sim Tv", "Yaban Tv", "TV4", "TV1", "One Tv", "TV100", "Kıbrıs Kanal T",
            "BRTV Karabük", "Türksat Tanıtım", "Ton Tv", "Ekol Tv", "Uçankuş Tv", "Flash Haber",
            "Kocaeli Tv", "HT Spor", "Show Max", "Tabii Spor", "Engelsiz Tv", "Doruk Tv",
            "Kanal 26", "Kanal 3", "Kanal 68", "Kent Tv", "Ege Türk Tv", "Fanatik Tv",
            "Anadolu Tv", "Sivas SRT Tv", "Diyanet Tv", "TvEM", "Rehber Tv", "TRT Okul",
            "Pamukkale Tv", "Kıbrıs Genç Tv", "DRT Denizli", "Malatya Er Tv", "Ege Tv",
            "Halk Tv Euro", "Es Tv", "Afyonkarahisar Av Tv", "Halil Tv", "Barış Tv",
            "Mavi Kadeniz Tv", "Çorum Tv", "GZT TV", "Kanal V", "CNBCE", "CHP Tv",
            "Kayseri", "NTV Avrupa", "brt 3", "Bloomberg", "Teve2", "Türk Haber", "Tv 52",
            "Tele1 Tv", "SAT7 Türk", "Kanal 23", "Kaçkar Tv", "Yeni Sinema Tv", "Düğün Tv",
            "Bursa Tv", "Elif TV", "Gonca Tv", "Kanal 34", "Diyar Tv", "Serhat Tv",
            "Kanal Malatya", "Lider Tv", "Bi Kanal TV", "TV 41", "Aksu Tv", "Ekol Sports",
            "tvDen", "tv 2000", "TYT Türk", "Sun Tv", "Kanal Plus", "Çiftçi Tv", "Deha Tv",
            "Altaş Tv", "Merkez Tv", "Kanal 33", "Kanal Urfa", "İçel Tv", "Artı Tv",
            "Karadeniz Türk Tv", "Bein Sports Haber", "Milyon Tv", "Kanal 78", "Marmaris Tv",
            "Yıldız En Tv", "A Para", "Viva Tv", "Günaydın Tv", "Haber Global", "A News",
            "Anadolu Dernek Tv", "Koza Tv", "Sıhhat tv", "Park Tv", "Art Tv", "Kadırga Tv",
            "Ekotürk Tv", "Tarım Türk Tv", "Gala Tv", "Kanal 7 Avrupa", "On6 Tv", "Kıbrıs Tv",
            "brt 2 Tv", "Uzay Haber", "Kanal 15", "Edessa Tv", "ERT Şah Tv", "KRT Tv",
            "Agro Tv", "ER Tv", "Kanal 43", "Kardelen Tv", "Kanal 32", "Kanal Fırat",
            "İlke Tv", "Shopping Tv", "Kanal 5"
        )
        blogTr.forEach { list.add(blog("TR", it)) }

        val blogRu = listOf("Rossiya 24", "RTR Planeta")
        blogRu.forEach { list.add(blog("RU", it)) }

        val blogInt = listOf(
            "Al Jazeera Arabic", "Al Jazeera English", "Al Arabiya", "Al Hadath",
            "Rudaw Tv", "Sterk Tv", "Ronahi Tv", "K24(Kurdistan24)", "Zarok Tv",
            "TV5Monde", "France 24 Francais", "France 24 English", "NHK World Japan",
            "Türkmeneli Tv", "UA Tv", "Spor UA", "1 TV Georgia", "Rustavi 2",
            "Ajara Tv", "GDS Tv", "imedi tv", "Kavkasia Tv", "Marao Tv", "Maestro Tv",
            "Mtavari Arkhi", "Obieqtivi", "Pos Tv", "Pravda Tut", "TV 25 Georgia",
            "Pirveli Radyo", "Comedy Arkhi", "RTL De", "ARD Das Erste De", "ZDF De",
            "ZDF Neo De", "Sat1 De", "ARD Alpha De", "ProSieben (Pro7)", "3Sat",
            "N24", "Radio Bremen", "Vox TV", "Kabel 1", "D Max De", "RTL2", "NTV De",
            "RTL Nitro", "Servus Tv", "Tele5 De", "ORF1", "ORF2", "Sat1 Gold", "NDR",
            "BR De", "WDR", "Deutsche Welle", "Sixx", "Super RTL", "Comedy central live",
            "Nasa Tv", "TLC De", "Eurosport TV", "Kabel1 Doku", "ESPN Brasil", "Arte",
            "Prosieben Maxx", "HR", "SR", "ZDFInfo", "MDR", "ARD", "Kika", "Alex Berlin",
            "SWR Tv", "Welt", "Tagesschau", "Disney De", "SWR RP", "Tagesschau24",
            "Sky News", "CNN International", "BBC News", "F1 Formula 1", "Red Bull TV",
            "Animal Planet", "Cartoon Network", "Disney Channel", "Fashion Tv",
            "Real Madrid Tv", "İran International", "Kabe Tv", "Mekke Tv", "Sunnah tv",
            "Ala Too 24", "Gagauz", "oe24.TV", "Canal Alpha JU", "i24NEWS Hebrew",
            "İran Press", "IRIB News", "Al Alam News", "Alikhbaria TV", "KAN 11"
        )
        blogInt.forEach { list.add(blog("INT", it)) }

        return sortGroupedByChannel(list)
    }

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

    
