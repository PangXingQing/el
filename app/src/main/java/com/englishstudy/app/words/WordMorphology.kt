package com.englishstudy.app.words

import android.content.res.AssetManager

/**
 * 词形还原：把 walked / children / went 这类变形还原成原形。
 *
 * 三步走（思路同 WordNet 的 morphy）：
 * 1. **不规则表**（`assets/morphology/irregular.txt`，取自 WordNet 的 *.exc）：
 *    went → go、children → child、better → well
 * 2. **缩写还原**：can't → can、isn't → is、you're → you
 * 3. **词尾规则**：-s / -es / -ies / -ves / -ed / -ied / -ing / -er / -est / -ly，
 *    含双写辅音（running → run、stopped → stop）与补 e（making → make）
 *
 * 规则推导出的候选**必须能在词库里查到才算数**（由 [WordRepository] 判定），
 * 这一步是准确性的关键：
 * - `accusing` → `accuse`（accuse 在词库里 ✓ 判定为已认识）
 * - `lens` → `len`（len 不在词库里 ✗ 否决，仍按生词处理）
 */
object WordMorphology {

    private const val ASSET_IRREGULAR = "morphology/irregular.txt"

    /** 不规则变形表：变形 → 原形；null 表示尚未加载 */
    @Volatile
    private var irregular: Map<String, String>? = null

    /** 缩写里不能靠"砍后缀"还原的几个 */
    private val CONTRACTIONS = mapOf(
        "can't" to "can",
        "won't" to "will",
        "shan't" to "shall",
        "ain't" to "be"
    )

    /** 缩写后缀：you're → you、we've → we、he'll → he、I'd → I、I'm → I */
    private val CONTRACTION_SUFFIXES = listOf(
        "'re", "'ve", "'ll", "'d", "'m", "’re", "’ve", "’ll", "’d", "’m"
    )

    /** 加载不规则表（后台线程调用；失败就只用规则，不影响主流程） */
    fun warmUp(assets: AssetManager) {
        if (irregular != null) return
        synchronized(this) {
            if (irregular != null) return

            val table = HashMap<String, String>(2048)
            runCatching {
                assets.open(ASSET_IRREGULAR).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val text = line.removePrefix("\uFEFF").trim()
                        if (text.isEmpty() || text.startsWith("#")) continue
                        val parts = text.split(' ', '\t')
                        if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                            table[parts[0]] = parts[1]
                        }
                    }
                }
            }
            irregular = table
        }
    }

    /** 不规则表 / 缩写表里给出的原形，没有则返回 null */
    fun irregularLemma(word: String): String? {
        irregular?.get(word)?.let { return it }
        return CONTRACTIONS[word]
    }

    /**
     * 按词尾规则推导出的候选原形。
     *
     * 这里不做取舍（顺序也不代表可能性），调用方逐个拿去词库验证即可。
     */
    fun candidates(word: String): List<String> {
        val result = ArrayList<String>(8)
        if (word.length < 3) return result

        // 允许单字母原形：I'm → I、I've → I、I'd → I
        fun add(candidate: String?) {
            if (candidate != null && candidate.isNotEmpty() && candidate != word) result.add(candidate)
        }

        // 缩写：isn't → is、you're → you
        for (suffix in CONTRACTION_SUFFIXES) {
            if (word.endsWith(suffix)) add(word.dropLast(suffix.length))
        }
        if (word.endsWith("n't") || word.endsWith("n’t")) add(word.dropLast(3))

        // 复数 / 第三人称单数
        if (word.endsWith("ies")) add(word.dropLast(3) + "y")
        if (word.endsWith("ves")) {
            add(word.dropLast(3) + "f")
            add(word.dropLast(3) + "fe")
        }
        if (word.endsWith("es")) add(word.dropLast(2))
        if (word.endsWith("s") && !word.endsWith("ss")) add(word.dropLast(1))

        // 过去式 / 过去分词
        if (word.endsWith("ied")) add(word.dropLast(3) + "y")
        if (word.endsWith("ed")) {
            val stem = word.dropLast(2)
            add(stem)
            add(stem + "e")
            add(dropDoubledConsonant(stem))
        }

        // 现在分词
        if (word.endsWith("ing")) {
            val stem = word.dropLast(3)
            add(stem)
            add(stem + "e")
            add(dropDoubledConsonant(stem))
        }

        // 比较级 / 最高级
        if (word.endsWith("ier")) add(word.dropLast(3) + "y")
        if (word.endsWith("iest")) add(word.dropLast(4) + "y")
        if (word.endsWith("er")) {
            add(word.dropLast(2))
            add(word.dropLast(2) + "e")
        }
        if (word.endsWith("est")) {
            add(word.dropLast(3))
            add(word.dropLast(3) + "e")
        }

        // 副词
        if (word.endsWith("ily")) add(word.dropLast(3) + "y")
        if (word.endsWith("ly")) add(word.dropLast(2))

        return result
    }

    /** running → runn → run；stopped → stopp → stop */
    private fun dropDoubledConsonant(stem: String): String? {
        if (stem.length < 3) return null
        val last = stem[stem.length - 1]
        if (last != stem[stem.length - 2]) return null
        if (!last.isLetter() || last in "aeiou") return null   // 双写的是辅音，且不会是元音
        return stem.dropLast(1)
    }
}
