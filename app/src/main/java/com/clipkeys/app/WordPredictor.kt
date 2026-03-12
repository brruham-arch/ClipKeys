package com.clipkeys.app

import android.content.Context

object WordPredictor {

    // Kata dari database eksternal (id_words.txt) + frekuensinya
    private val dbWords = mutableMapOf<String, Int>() // word -> freq

    // Kata yang dipelajari dari user
    private val wordFreq = mutableMapOf<String, Int>()
    // Bigram: kata A → kata B yang sering mengikuti
    private val bigram = mutableMapOf<String, MutableMap<String, Int>>()
    private var lastWord = ""
    private var dbLoaded = false

    fun init(context: Context) {
        loadDatabase(context)
        loadUserData(context)
    }

    /** Load id_words.txt dari assets */
    private fun loadDatabase(context: Context) {
        if (dbLoaded) return
        try {
            context.assets.open("id_words.txt").bufferedReader().forEachLine { line ->
                val parts = line.trim().split(" ")
                if (parts.size >= 2) {
                    val word = parts[0].lowercase()
                    val freq = parts[1].toIntOrNull() ?: 1
                    // Hanya kata yang valid (huruf saja, panjang 2-20)
                    if (word.length in 2..20 && word.all { it.isLetter() }) {
                        dbWords[word] = freq
                    }
                }
            }
            dbLoaded = true
        } catch (e: Exception) {
            // File tidak ada, fallback ke BASE_WORDS
        }
    }

    private fun loadUserData(context: Context) {
        try {
            val prefs = context.getSharedPreferences("clipkeys_predict", Context.MODE_PRIVATE)
            prefs.getString("freq", null)?.split("|")?.forEach { entry ->
                val p = entry.split(":")
                if (p.size == 2) wordFreq[p[0]] = p[1].toIntOrNull() ?: 1
            }
            prefs.getString("bigram", null)?.split("|")?.forEach { entry ->
                val p = entry.split("->")
                if (p.size == 2) {
                    val from = p[0]
                    p[1].split(",").forEach { pair ->
                        val kv = pair.split(":")
                        if (kv.size == 2) bigram.getOrPut(from) { mutableMapOf() }[kv[0]] = kv[1].toIntOrNull() ?: 1
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun save(context: Context) {
        try {
            val prefs = context.getSharedPreferences("clipkeys_predict", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("freq", wordFreq.entries.joinToString("|") { "${it.key}:${it.value}" })
                .putString("bigram", bigram.entries.joinToString("|") { (from, toMap) ->
                    "$from->" + toMap.entries.joinToString(",") { "${it.key}:${it.value}" }
                })
                .apply()
        } catch (e: Exception) {}
    }

    fun learnWord(word: String) {
        if (word.length < 2) return
        val w = word.lowercase()
        wordFreq[w] = (wordFreq[w] ?: 0) + 1
        if (lastWord.isNotEmpty()) {
            bigram.getOrPut(lastWord) { mutableMapOf() }.let {
                it[w] = (it[w] ?: 0) + 1
            }
        }
        lastWord = w
    }

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

        // User's learned words (prioritas tertinggi)
        val userMatches = wordFreq.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedByDescending { it.value }
            .map { it.key }

        // Database 50k kata
        val dbMatches = dbWords.entries
            .filter { it.key.startsWith(p) && it.key != p && !userMatches.contains(it.key) }
            .sortedByDescending { it.value }
            .map { it.key }

        return (userMatches + dbMatches).take(3)
    }

    // ── 2. KOREKSI TYPO (Levenshtein) ─────────────────────────────────────────
    fun getCorrections(input: String, alreadyInPredictions: List<String>): List<String> {
        if (input.length < 3) return emptyList()
        val inp = input.lowercase()
        val maxDist = if (inp.length <= 5) 1 else 2

        // Cari di database + user words
        // Batasi pencarian ke kata dengan panjang mirip untuk performa
        val candidates = (dbWords.entries.asSequence() + wordFreq.entries.asSequence())
            .filter { (word, _) ->
                word != inp &&
                !alreadyInPredictions.contains(word) &&
                Math.abs(word.length - inp.length) <= maxDist
            }
            .map { (word, freq) -> Triple(word, levenshtein(inp, word), freq) }
            .filter { it.second <= maxDist }
            .sortedWith(compareBy({ it.second }, { -it.third }))
            .map { it.first }
            .distinct()
            .take(2)
            .toList()

        return candidates
    }

    // ── 3. NEXT WORD ──────────────────────────────────────────────────────────
    fun getNextWords(prevWord: String): List<String> {
        if (prevWord.isEmpty()) return emptyList()
        val prev = prevWord.lowercase()

        val bigramSuggestions = bigram[prev]?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key }
            ?.take(3)

        if (!bigramSuggestions.isNullOrEmpty()) return bigramSuggestions

        return wordFreq.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    // ── Levenshtein ───────────────────────────────────────────────────────────
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
            else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[a.length][b.length]
    }

    fun dbSize() = dbWords.size
}
