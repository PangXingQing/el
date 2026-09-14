package com.englishstudy.app.reader

/** 音素大类：用于给音素标签配色（元音蓝 / 辅音绿 / 重音橙） */
enum class IpaGroup { VOWEL, CONSONANT, STRESS, OTHER }

/**
 * 单个音素的说明（面向零基础）
 *
 * @param symbol 音素符号
 * @param group 大类
 * @param category 细分类别，如"长元音""爆破音（清）"
 * @param tip 发音要领
 * @param exampleWord 例词
 * @param exampleIpa 例词音标
 */
data class IpaInfo(
    val symbol: String,
    val group: IpaGroup,
    val category: String,
    val tip: String,
    val exampleWord: String = "",
    val exampleIpa: String = ""
)

/**
 * 音标串里的一个元素
 * @param text 展示文本（音素符号 / 重音符号 / 可选项）
 * @param info 对应的说明，null 表示词库里还没有这个符号
 */
data class IpaToken(
    val text: String,
    val info: IpaInfo?
)

/**
 * IPA 音素词库 + 音标串切分
 *
 * 有道返回的音标是一个连续字符串（如 ˌɒpəˈtjuːnəti），
 * 这里用"最长匹配"把它切成一个个音素，方便逐个学习。
 */
object IpaKnowledge {

    private val ALL: List<IpaInfo> = listOf(
        // ===== 元音：单元音 =====
        IpaInfo("iː", IpaGroup.VOWEL, "长元音", "嘴角向两边拉开、像微笑，舌前部抬高，比汉语\"衣\"更紧更长。", "see", "/siː/"),
        IpaInfo("ɪ", IpaGroup.VOWEL, "短元音", "短促放松的\"衣\"，舌位比 iː 略低、口型不紧张，别拖长。", "sit", "/sɪt/"),
        IpaInfo("i", IpaGroup.VOWEL, "短元音（弱读）", "词尾的轻短\"衣\"，介于 iː 和 ɪ 之间，一带而过即可。", "happy", "/ˈhæpi/"),
        IpaInfo("e", IpaGroup.VOWEL, "短元音", "舌尖抵下齿、口半开，接近汉语\"诶\"但更短更扁。", "bed", "/bed/"),
        IpaInfo("æ", IpaGroup.VOWEL, "短元音", "口张大、嘴角向两边咧，介于\"哎\"和\"啊\"之间（汉语没有）。", "cat", "/kæt/"),
        IpaInfo("ɑː", IpaGroup.VOWEL, "长元音", "口张大、舌位靠后，接近汉语\"啊\"，要拉长。", "car", "/kɑː/"),
        IpaInfo("ɑ", IpaGroup.VOWEL, "长元音（美）", "美音的\"啊\"，比英音 ɑː 稍短、位置更靠前。", "hot", "/hɑt/"),
        IpaInfo("ɒ", IpaGroup.VOWEL, "短元音（英）", "口张大、双唇略收圆，短促的\"奥\"（美音多用 ɑ）。", "hot", "/hɒt/"),
        IpaInfo("ɔː", IpaGroup.VOWEL, "长元音", "双唇收圆并向前突出，舌后部抬起，拉长的\"奥\"。", "law", "/lɔː/"),
        IpaInfo("ʊ", IpaGroup.VOWEL, "短元音", "双唇略圆但放松，短促的\"乌\"，不要用力噘嘴。", "book", "/bʊk/"),
        IpaInfo("uː", IpaGroup.VOWEL, "长元音", "双唇用力收圆前突，舌后部抬高，拉长的\"乌\"。", "food", "/fuːd/"),
        IpaInfo("ʌ", IpaGroup.VOWEL, "短元音", "口半开、双唇不用力，短促的\"阿\"，位置在口腔中部。", "cup", "/kʌp/"),
        IpaInfo("ɜː", IpaGroup.VOWEL, "长元音", "舌位在口腔中央、双唇自然，像把\"额\"拉长（英音不卷舌）。", "bird", "/bɜːd/"),
        IpaInfo("ɝ", IpaGroup.VOWEL, "长元音（美）", "美音 ɜː 的卷舌版：舌头中部上翘，带明显 r 音色。", "bird", "/bɝd/"),
        IpaInfo("ə", IpaGroup.VOWEL, "弱元音（最常见）", "英语里出现最多的音：双唇放松、轻轻带过，所有非重读音节常读成它。", "about", "/əˈbaʊt/"),
        IpaInfo("ɚ", IpaGroup.VOWEL, "弱元音（美）", "美音 ə 的卷舌版，词尾 -er / -or 基本都是它。", "teacher", "/ˈtiːtʃɚ/"),

        // ===== 元音：双元音 =====
        IpaInfo("eɪ", IpaGroup.VOWEL, "双元音", "从 e 平滑滑向 ɪ，前重后轻，像\"诶\"到\"衣\"。", "day", "/deɪ/"),
        IpaInfo("aɪ", IpaGroup.VOWEL, "双元音", "从 a 平滑滑向 ɪ，像\"啊\"到\"衣\"。", "my", "/maɪ/"),
        IpaInfo("ɔɪ", IpaGroup.VOWEL, "双元音", "从 ɔ 平滑滑向 ɪ，像\"奥\"到\"衣\"。", "boy", "/bɔɪ/"),
        IpaInfo("aʊ", IpaGroup.VOWEL, "双元音", "从 a 平滑滑向 ʊ，像\"啊\"到\"乌\"。", "now", "/naʊ/"),
        IpaInfo("əʊ", IpaGroup.VOWEL, "双元音（英）", "从 ə 滑向 ʊ，像\"欧\"，结尾嘴唇收圆。", "go", "/ɡəʊ/"),
        IpaInfo("oʊ", IpaGroup.VOWEL, "双元音（美）", "美音写法，从 o 滑向 ʊ，和英音 əʊ 对应。", "go", "/ɡoʊ/"),
        IpaInfo("ɪə", IpaGroup.VOWEL, "双元音（英）", "从 ɪ 滑向 ə，现代英音里常直接读成 ɪː。", "near", "/nɪə/"),
        IpaInfo("eə", IpaGroup.VOWEL, "双元音（英）", "从 e 滑向 ə。", "hair", "/heə/"),
        IpaInfo("ʊə", IpaGroup.VOWEL, "双元音（英）", "从 ʊ 滑向 ə。", "tour", "/tʊə/"),

        // ===== 辅音：爆破音 =====
        IpaInfo("p", IpaGroup.CONSONANT, "爆破音（清）", "双唇紧闭再突然放开，送气、声带不振动（嘴前放纸会动）。", "pen", "/pen/"),
        IpaInfo("b", IpaGroup.CONSONANT, "爆破音（浊）", "口型同 p，但声带振动、几乎不送气。", "bed", "/bed/"),
        IpaInfo("t", IpaGroup.CONSONANT, "爆破音（清）", "舌尖抵上齿龈再弹开，送气。", "tea", "/tiː/"),
        IpaInfo("d", IpaGroup.CONSONANT, "爆破音（浊）", "口型同 t，但声带振动。", "dog", "/dɒɡ/"),
        IpaInfo("k", IpaGroup.CONSONANT, "爆破音（清）", "舌后部抵住软腭再放开，送气。", "cat", "/kæt/"),
        IpaInfo("g", IpaGroup.CONSONANT, "爆破音（浊）", "口型同 k，但声带振动。", "go", "/ɡəʊ/"),

        // ===== 辅音：摩擦音 =====
        IpaInfo("f", IpaGroup.CONSONANT, "摩擦音（清）", "上齿轻触下唇，气流从缝隙擦出。", "fish", "/fɪʃ/"),
        IpaInfo("v", IpaGroup.CONSONANT, "摩擦音（浊）", "口型同 f，但声带振动（汉语没有，注意别读成 w）。", "very", "/ˈveri/"),
        IpaInfo("θ", IpaGroup.CONSONANT, "摩擦音（清）", "舌尖轻轻伸到上下齿之间，气流擦出（汉语没有）。", "think", "/θɪŋk/"),
        IpaInfo("ð", IpaGroup.CONSONANT, "摩擦音（浊）", "口型同 θ，但声带振动（汉语没有）。", "this", "/ðɪs/"),
        IpaInfo("s", IpaGroup.CONSONANT, "摩擦音（清）", "舌尖靠近齿龈，气流从中间细缝挤出来。", "sun", "/sʌn/"),
        IpaInfo("z", IpaGroup.CONSONANT, "摩擦音（浊）", "口型同 s，但声带振动。", "zoo", "/zuː/"),
        IpaInfo("ʃ", IpaGroup.CONSONANT, "摩擦音（清）", "舌面抬向硬腭、双唇略前突，比汉语\"西\"更厚重。", "she", "/ʃiː/"),
        IpaInfo("ʒ", IpaGroup.CONSONANT, "摩擦音（浊）", "口型同 ʃ，但声带振动。", "vision", "/ˈvɪʒn/"),
        IpaInfo("h", IpaGroup.CONSONANT, "摩擦音（清）", "气流从张开的喉咙呼出，声带不振动、不带摩擦杂音。", "hat", "/hæt/"),

        // ===== 辅音：破擦音 =====
        IpaInfo("tʃ", IpaGroup.CONSONANT, "破擦音（清）", "t 和 ʃ 连在一起读，像汉语\"吃\"但更靠前。", "chair", "/tʃeə/"),
        IpaInfo("dʒ", IpaGroup.CONSONANT, "破擦音（浊）", "d 和 ʒ 连读，像汉语\"知\"，要带声带振动。", "job", "/dʒɒb/"),

        // ===== 辅音：鼻音 / 其它 =====
        IpaInfo("m", IpaGroup.CONSONANT, "鼻音", "双唇闭合，气流从鼻腔出来。", "man", "/mæn/"),
        IpaInfo("n", IpaGroup.CONSONANT, "鼻音", "舌尖抵上齿龈，气流从鼻腔出来。", "no", "/nəʊ/"),
        IpaInfo("ŋ", IpaGroup.CONSONANT, "鼻音", "舌后部抵软腭、气流从鼻腔出，像\"英\"的尾音。", "sing", "/sɪŋ/"),
        IpaInfo("l", IpaGroup.CONSONANT, "舌侧音", "舌尖抵上齿龈，气流从舌头两侧流出。", "leg", "/leɡ/"),
        IpaInfo("r", IpaGroup.CONSONANT, "近音", "舌尖向上卷，但不碰任何部位（汉语的 r 是摩擦音，发音不一样）。", "red", "/red/"),
        IpaInfo("j", IpaGroup.CONSONANT, "半元音", "舌面抬向硬腭，像\"耶\"的开头。", "yes", "/jes/"),
        IpaInfo("w", IpaGroup.CONSONANT, "半元音", "双唇先收圆再迅速放开，像\"我\"的开头。", "we", "/wiː/"),

        // ===== 重音与记号 =====
        IpaInfo("ˈ", IpaGroup.STRESS, "主重音", "紧跟在它后面的音节要读得最重、最长、最清楚；一个单词通常只有一个。", "support", "/səˈpɔːt/"),
        IpaInfo("ˌ", IpaGroup.STRESS, "次重音", "读得比主重音轻，但比其它音节稍强；长单词里很常见。", "opportunity", "/ˌɒpəˈtjuːnəti/"),
        IpaInfo("ː", IpaGroup.STRESS, "长音记号", "加在元音后面，表示这个音要拉长约一倍。", "see", "/siː/"),
        IpaInfo("(r)", IpaGroup.OTHER, "可选音 / 连接音", "英音里这个 r 只在后面接元音时才读出来；美音则一律读 r。", "far away", "/fɑːr əˈweɪ/")
    )

    private val TABLE: Map<String, IpaInfo> = ALL.associateBy { it.symbol }

    /** 同一个音的其它写法：开放尾的 ɡ、美式 r 的 ɹ、弱化元音 ɐ */
    private val ALIASES: Map<String, String> = mapOf(
        "ɡ" to "g",
        "ɹ" to "r",
        "ɐ" to "ʌ",
        "ᵊ" to "ə"
    )

    private const val MAX_SYMBOL_LENGTH = 2

    fun lookup(symbol: String): IpaInfo? {
        TABLE[symbol]?.let { return it }
        ALIASES[symbol]?.let { alias -> TABLE[alias]?.let { return it } }

        // 括号音（如 -ful 里的 (ə)）统一按"可选音"解释，
        // 并复用括号内音素本身的要领，例如 (ə) → 弱元音 ə 的说明。
        if (symbol.length >= 3 && symbol.startsWith("(") && symbol.endsWith(")")) {
            val inner = symbol.substring(1, symbol.length - 1)
            val innerInfo = TABLE[inner] ?: ALIASES[inner]?.let { TABLE[it] }
            if (innerInfo != null) {
                return innerInfo.copy(
                    symbol = symbol,
                    category = "可选音 · ${innerInfo.category}",
                    tip = "括号表示这个音可读可不读（单独念就是下面这个音）。${innerInfo.tip}"
                )
            }
        }

        return null
    }

    /**
     * 把音标串切成音素列表。
     * 采用"最长优先"匹配，例如 tjuː → t + j + uː，tʃ → tʃ（而不是 t + ʃ）。
     */
    fun tokenize(ipa: String): List<IpaToken> {
        val tokens = mutableListOf<IpaToken>()
        var index = 0

        while (index < ipa.length) {
            val c = ipa[index]

            when {
                // 跳过斜杠、方括号、空白
                c == '/' || c == '[' || c == ']' || c.isWhitespace() -> index++

                // 括号内容整体作为一个记号，如 (r)
                c == '(' -> {
                    val end = ipa.indexOf(')', index)
                    if (end > index) {
                        val text = ipa.substring(index, end + 1)
                        tokens.add(IpaToken(text, lookup(text)))
                        index = end + 1
                    } else {
                        index++
                    }
                }

                else -> {
                    val matched = matchLongest(ipa, index)
                    if (matched != null) {
                        tokens.add(matched.first)
                        index += matched.second
                    } else {
                        tokens.add(IpaToken(c.toString(), null))
                        index++
                    }
                }
            }
        }

        return tokens
    }

    private fun matchLongest(ipa: String, index: Int): Pair<IpaToken, Int>? {
        val maxLength = minOf(MAX_SYMBOL_LENGTH, ipa.length - index)
        for (length in maxLength downTo 1) {
            val candidate = ipa.substring(index, index + length)
            val info = lookup(candidate)
            if (info != null) {
                return IpaToken(candidate, info) to length
            }
        }
        return null
    }
}
