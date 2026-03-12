package com.clipkeys.app

import android.content.Context

object WordPredictor {

    // Kamus kata umum Indonesia + Inggris
    private val BASE_WORDS = listOf(
        // Indonesia
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
        "hingga","melainkan","memiliki","membuat","mengatakan","menurut","antara",
        "merasa","terlihat","termasuk","digunakan","menggunakan","merupakan","membantu",
        "berhasil","penting","pertama","kedua","ketiga","akhir","awal","tengah",
        "depan","belakang","kiri","kanan","atas","bawah","dalam","luar","dekat","jauh",
        "sama","berbeda","banyak","sedikit","cukup","kurang","penuh","kosong",
        "senang","sedih","marah","takut","kaget","bingung","lelah","lapar","haus",
        "pergi","datang","pulang","masuk","keluar","naik","turun","buka","tutup",
        "ambil","taruh","simpan","hapus","kirim","terima","baca","tulis","bicara",
        "dengar","lihat","cari","temukan","belajar","mengajar","bekerja","bermain",
        "makan","minum","tidur","bangun","berjalan","berlari","terbang","berenang",
        // English
        "the","and","for","are","but","not","you","all","can","her","was","one",
        "our","out","day","get","has","him","his","how","man","new","now","old",
        "see","two","way","who","boy","did","its","let","put","say","she","too",
        "use","able","also","back","call","come","come","each","even","find",
        "first","from","give","good","great","have","here","high","into","just",
        "know","last","left","like","long","look","made","make","many","more",
        "most","move","much","must","name","need","next","open","over","part",
        "play","real","right","said","same","seem","show","side","some","such",
        "take","than","that","them","then","there","they","this","time","told",
        "turn","very","want","well","went","were","what","when","with","word",
        "work","year","your","about","after","again","being","could","every",
        "found","going","great","group","house","large","learn","never","night",
        "often","other","place","point","quite","small","sound","still","study",
        "their","think","thing","those","three","under","until","water","which",
        "while","world","would","write","above","across","always","around","before",
        "below","between","change","during","enough","example","follow","forward",
        "important","interest","problem","question","really","should","something",
        "together","through","without","already","another","because","different",
        "example","however","include","nothing","program","several","special",
        "support","system","number","people","return","school","social","little",
        "public","friend","family","little","please","simple","thanks","hello",
        "function","variable","string","integer","boolean","return","import",
        "class","object","method","array","local","global","print","output","input",
        "error","debug","build","compile","release","android","kotlin","java","script"
    )

    // Frekuensi kata yang dipelajari dari user
    private val learnedWords = mutableMapOf<String, Int>()
    private const val PREFS_KEY = "learned_words"

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences("clipkeys_words", Context.MODE_PRIVATE)
            val json = prefs.getString(PREFS_KEY, null) ?: return
            json.split(",").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) learnedWords[parts[0]] = parts[1].toIntOrNull() ?: 1
            }
        } catch (e: Exception) {}
    }

    fun saveLearnedWords(context: Context) {
        try {
            val json = learnedWords.entries.joinToString(",") { "${it.key}:${it.value}" }
            context.getSharedPreferences("clipkeys_words", Context.MODE_PRIVATE)
                .edit().putString(PREFS_KEY, json).apply()
        } catch (e: Exception) {}
    }

    /** Saat user selesai mengetik kata, pelajari */
    fun learnWord(word: String) {
        if (word.length < 2) return
        val w = word.lowercase()
        learnedWords[w] = (learnedWords[w] ?: 0) + 1
    }

    /** Dapat prediksi dari prefix, maks 3 */
    fun predict(prefix: String): List<String> {
        if (prefix.length < 1) return emptyList()
        val p = prefix.lowercase()

        // Cari di learned words dulu (lebih relevan)
        val learnedMatches = learnedWords.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        // Tambah dari base words jika kurang dari 3
        val baseMatches = BASE_WORDS
            .filter { it.startsWith(p) && it != p && !learnedMatches.contains(it) }
            .take(3 - learnedMatches.size)

        return (learnedMatches + baseMatches).take(3)
    }
}
