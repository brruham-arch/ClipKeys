package com.clipkeys.app

import android.content.Context

object WordPredictor {

    // ── Kamus dasar ──────────────────────────────────────────────────────────
    private val BASE_WORDS = listOf(
        // Indonesia umum
        "yang","dan","di","ini","itu","dengan","untuk","dari","ke","ada","tidak","saya",
        "anda","kita","mereka","akan","bisa","sudah","juga","atau","pada","karena","lebih",
        "harus","dapat","dalam","tahun","setelah","ketika","seperti","bahwa","sangat",
        "namun","masih","semua","atas","berbagai","beberapa","tersebut","selain","tetapi",
        "antara","bagi","sebelum","menjadi","melalui","sendiri","lagi","memang","kemudian",
        "setiap","terhadap","sebagai","tentang","sebuah","kami","kalau","maka","apakah",
        "cara","sedang","waktu","orang","lain","perlu","belum","tapi","jadi","mau","dia",
        "satu","dua","tiga","empat","lima","enam","tujuh","delapan","sembilan","sepuluh",
        "baru","lama","besar","kecil","panjang","pendek","baik","buruk","benar","salah",
        "cepat","lambat","mudah","sulit","bagus","tinggi","rendah","panas","dingin",
        "hari","malam","pagi","siang","sore","minggu","bulan","sekarang","nanti",
        "kemarin","besok","sini","sana","siapa","mengapa","bagaimana","berapa",
        "selalu","kadang","sering","jarang","sekali","bahkan","meski","walaupun",
        "sehingga","supaya","agar","jika","apabila","saat","selama","sejak","sampai",
        "hingga","melainkan","memiliki","membuat","mengatakan","menurut","merasa",
        "terlihat","termasuk","digunakan","menggunakan","merupakan","membantu",
        "berhasil","penting","pertama","kedua","ketiga","akhir","awal","tengah",
        "depan","belakang","kiri","kanan","bawah","dekat","jauh","sama","berbeda",
        "banyak","sedikit","cukup","kurang","penuh","kosong","senang","sedih",
        "marah","takut","kaget","bingung","lelah","lapar","haus",
        "pergi","datang","pulang","masuk","keluar","naik","turun","buka","tutup",
        "ambil","taruh","simpan","hapus","kirim","terima","baca","tulis","bicara",
        "dengar","lihat","cari","temukan","belajar","bekerja","bermain","tidur",
        "makan","minum","bangun","berjalan","berlari",
        "terima kasih","selamat pagi","selamat malam","selamat siang","sampai jumpa",
        "tolong","maaf","permisi","oke","baik","siap","sudah","belum","tidak tahu",
        // Teknologi / coding
        "function","variable","string","integer","boolean","return","import","export",
        "class","object","method","array","local","global","print","output","input",
        "error","debug","build","compile","release","android","kotlin","java","script",
        "lua","python","server","client","database","network","internet","wifi",
        "download","upload","install","update","version","module","library","package",
        "true","false","null","void","public","private","static","final","override",
        "interface","abstract","extends","implements","constructor","parameter",
        "callback","coroutine","suspend","launch","async","await","thread","handler",
        "activity","fragment","service","intent","context","view","layout","adapter",
        "recyclerview","listview","textview","edittext","button","imageview",
        "samp","moonloader","aml","gloss","mimgui","hook","patch","offset","address",
        "memory","pointer","struct","enum","typedef","include","define","pragma",
        // English
        "the","and","for","are","but","not","you","all","can","was","one","our","out",
        "get","has","him","his","how","new","now","old","see","two","way","who","did",
        "its","let","put","say","she","too","use","able","also","back","call","come",
        "each","even","find","first","from","give","good","great","have","here","high",
        "into","just","know","last","left","like","long","look","made","make","many",
        "more","most","move","much","must","name","need","next","open","over","part",
        "play","real","right","said","same","seem","show","some","such","take","than",
        "that","them","then","there","they","this","time","told","turn","very","want",
        "well","went","were","what","when","with","word","work","year","your","about",
        "after","again","being","could","every","found","going","house","large","learn",
        "never","night","often","other","place","point","small","sound","still","study",
        "their","think","thing","those","three","under","until","water","which","while",
        "world","would","write","above","across","always","around","before","below",
        "between","change","during","enough","follow","important","interest","problem",
        "question","really","should","something","together","through","without","already",
        "another","because","different","however","include","nothing","program","several",
        "special","support","system","number","people","return","school","social",
        "please","simple","thanks","hello","sorry","okay","great","perfect","awesome"
    )

    // Frekuensi kata yang dipelajari
    private val wordFreq = mutableMapOf<String, Int>()
    // Bigram: kata A → kata B yang sering mengikuti
    private val bigram = mutableMapOf<String, MutableMap<String, Int>>()
    private var lastWord = ""

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences("clipkeys_predict", Context.MODE_PRIVATE)
            // Load word freq
            prefs.getString("freq", null)?.split("|")?.forEach { entry ->
                val p = entry.split(":")
                if (p.size == 2) wordFreq[p[0]] = p[1].toIntOrNull() ?: 1
            }
            // Load bigrams
            prefs.getString("bigram", null)?.split("|")?.forEach { entry ->
                val p = entry.split("->")
                if (p.size == 2) {
                    val from = p[0]
                    val pairs = p[1].split(",")
                    pairs.forEach { pair ->
                        val kv = pair.split(":")
                        if (kv.size == 2) {
                            bigram.getOrPut(from) { mutableMapOf() }[kv[0]] = kv[1].toIntOrNull() ?: 1
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun save(context: Context) {
        try {
            val prefs = context.getSharedPreferences("clipkeys_predict", Context.MODE_PRIVATE)
            val freqStr = wordFreq.entries.joinToString("|") { "${it.key}:${it.value}" }
            val bigramStr = bigram.entries.joinToString("|") { (from, toMap) ->
                "$from->" + toMap.entries.joinToString(",") { "${it.key}:${it.value}" }
            }
            prefs.edit().putString("freq", freqStr).putString("bigram", bigramStr).apply()
        } catch (e: Exception) {}
    }

    /** Dipanggil saat user selesai ketik satu kata */
    fun learnWord(word: String) {
        if (word.length < 2) return
        val w = word.lowercase()
        wordFreq[w] = (wordFreq[w] ?: 0) + 1
        // Pelajari bigram
        if (lastWord.isNotEmpty()) {
            val toMap = bigram.getOrPut(lastWord) { mutableMapOf() }
            toMap[w] = (toMap[w] ?: 0) + 1
        }
        lastWord = w
    }

    /**
     * Dapat semua saran: prefix match + koreksi typo + next word
     * Return: Triple(predictions, corrections, nextWords)
     */
    fun getSuggestions(currentWord: String, prevWord: String = ""): Triple<List<String>, List<String>, List<String>> {
        val predictions = getPredictions(currentWord)
        val corrections = if (currentWord.length >= 3) getCorrections(currentWord, predictions) else emptyList()
        val nextWords = if (currentWord.isEmpty()) getNextWords(prevWord) else emptyList()
        return Triple(predictions, corrections, nextWords)
    }

    // ── 1. PREFIX MATCH ───────────────────────────────────────────────────────
    fun getPredictions(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()

        // Semua kata yang diawali prefix ini
        val learnedMatches = wordFreq.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedByDescending { it.value }
            .map { it.key }

        val baseMatches = BASE_WORDS
            .filter { it.startsWith(p) && it != p && !learnedMatches.contains(it) }

        return (learnedMatches + baseMatches).take(3)
    }

    // ── 2. KOREKSI TYPO (Levenshtein distance) ───────────────────────────────
    fun getCorrections(input: String, alreadyInPredictions: List<String>): List<String> {
        if (input.length < 3) return emptyList()
        val inp = input.lowercase()
        val maxDist = when {
            inp.length <= 4 -> 1
            inp.length <= 7 -> 2
            else -> 2
        }

        // Gabung learned + base, cari yang jaraknya dekat
        val allWords = (wordFreq.keys + BASE_WORDS).distinct()
        val candidates = allWords
            .filter { it != inp && !alreadyInPredictions.contains(it) && Math.abs(it.length - inp.length) <= maxDist }
            .map { it to levenshtein(inp, it) }
            .filter { it.second <= maxDist }
            .sortedWith(compareBy({ it.second }, { -(wordFreq[it.first] ?: 0) }))
            .map { it.first }
            .take(2)

        return candidates
    }

    // ── 3. NEXT WORD SUGGESTION ───────────────────────────────────────────────
    fun getNextWords(prevWord: String): List<String> {
        if (prevWord.isEmpty()) return emptyList()
        val prev = prevWord.lowercase()

        // Cek bigram dulu
        val bigramSuggestions = bigram[prev]?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key }
            ?.take(3)
            ?: emptyList()

        if (bigramSuggestions.isNotEmpty()) return bigramSuggestions

        // Fallback: kata yang sering dipakai secara umum
        return wordFreq.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }

    // ── Levenshtein Distance ─────────────────────────────────────────────────
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
            }
        }
        return dp[a.length][b.length]
    }
}
