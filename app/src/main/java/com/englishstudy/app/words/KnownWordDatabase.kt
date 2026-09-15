package com.englishstudy.app.words

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 用户词库数据库。
 *
 * 只存用户点「加入词库」新增的词条（通常只有几十条）。
 * 默认词表不在这里——它是 APK 里的一份词表文件
 * （`assets/wordlist/known_words.txt`），启动时整份读进内存，见 [WordRepository]。
 *
 * 这样做的原因：默认词表是只读数据，没必要每次重装都要往 SQLite 里灌 8000+ 条，
 * 读文件进内存的耗时远低于"建库 + 批量 INSERT + 再查回来"。
 */
internal class KnownWordDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_WORD     TEXT    PRIMARY KEY COLLATE NOCASE,
                $COL_SOURCE   TEXT    NOT NULL DEFAULT '$SOURCE_USER',
                $COL_ADDED_AT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 / v2 把默认词表也一起存在库里，v3 起默认词表改放 assets，
        // 这里把历史版本留下的默认词条清掉，只保留用户自己加的词。
        db.execSQL("DELETE FROM $TABLE WHERE $COL_SOURCE <> '$SOURCE_USER'")
    }

    companion object {
        private const val DB_NAME = "known_words.db"

        /**
         * v1：词表只有 CET4+6
         * v2：默认词表扩展为多份词表文件
         * v3：默认词表移出数据库，库里只存用户词条
         */
        private const val DB_VERSION = 3

        const val TABLE = "known_word"
        const val COL_WORD = "word"
        const val COL_SOURCE = "source"
        const val COL_ADDED_AT = "added_at"

        /** 用户点「加入词库」新增的条目 */
        const val SOURCE_USER = "user"
    }
}
