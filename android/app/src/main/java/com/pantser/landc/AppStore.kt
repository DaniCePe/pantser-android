package com.pantser.landc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local-only persistence. All label metadata lives in SharedPreferences and PDFs in filesDir. */
class AppStore(context: Context) {
    data class LabelRecord(
        val id: Long,
        val size: String,
        val fileName: String,
        val createdAt: Long,
        val printCount: Int = 0,
        val lastPrintedAt: Long = 0L
    )

    private val prefs = context.getSharedPreferences("label_store", Context.MODE_PRIVATE)
    private val pdfDir = File(context.filesDir, "labels").apply { mkdirs() }
    private val cacheDir = context.cacheDir

    init {
        cleanupDuplicates()
        performAutomaticCleanup()
    }

    fun performAutomaticCleanup() {
        if (setting("automatic_cleanup", "true") != "true") return
        val cutoff = System.currentTimeMillis() - 7 * 86_400_000L
        val all = records()
        val toRemove = all.filter { 
            val referenceTime = if (it.lastPrintedAt > 0) it.lastPrintedAt else it.createdAt
            referenceTime < cutoff 
        }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { File(pdfDir, it.fileName).delete() }
            val remaining = all.filter { it !in toRemove }
            save(remaining)
        }
    }

    /* Raw size identity (mirrors the web app since its v1.3.0): a size is
       identified by its exact string as entered (createLabel already
       outer-trims). "32 x32", "32 x 32" and "32 X 32" are different sizes
       with different PDFs. normalizeSize was removed for this. */
    fun strictKey(size: String): String = size

    private fun javaHash(s: String): Int {
        var h = 0
        for (ch in s) h = 31 * h + ch.code
        return h
    }

    fun fileNameForSize(size: String): String {
        /* Keep [A-Za-z0-9], replace runs of anything else with one
           underscore, strip edge underscores. A name that lost
           information (spacing, case is kept, punctuation) gets a hash
           suffix so distinct raw sizes never share a file. */
        val clean = size.replace("[^A-Za-z0-9]+".toRegex(), "_").trim('_')
        val converted = size.replace(' ', '_')
        val lossy = converted != clean
        if (clean.length > 50) return "size_adv_${javaHash(size)}.pdf"
        if (lossy) return "size_${clean}_${javaHash(size)}.pdf"
        return "size_$clean.pdf"
    }

    fun findRecordForSize(size: String): LabelRecord? {
        return records().find { it.size == size }
    }

    private fun cleanupDuplicates() {
        /* Consolidate on the raw key: only records for the exact same
           size string merge; every record's file is migrated to the
           raw-derived file name (records merged by the old normalized
           naming stay as they are, as their own entries). */
        val all = records()
        if (all.isEmpty()) return
        val groups = all.groupBy { strictKey(it.size) }
        var changed = false
        val consolidated = mutableListOf<LabelRecord>()
        
        for ((_, recordsForSize) in groups) {
            if (recordsForSize.size > 1) {
                changed = true
                val main = recordsForSize.maxByOrNull { it.printCount } ?: recordsForSize[0]
                val totalPrints = recordsForSize.sumOf { it.printCount }
                val latestPrint = recordsForSize.maxOfOrNull { it.lastPrintedAt } ?: 0L
                
                recordsForSize.forEach { r ->
                    if (r.id != main.id && r.fileName != main.fileName) {
                        val f = File(pdfDir, r.fileName)
                        if (f.exists()) f.delete()
                    }
                }
                
                val idealFileName = fileNameForSize(main.size)
                if (main.fileName != idealFileName) {
                    val oldFile = File(pdfDir, main.fileName)
                    val newFile = File(pdfDir, idealFileName)
                    if (oldFile.exists()) oldFile.renameTo(newFile)
                }
                
                consolidated.add(main.copy(
                    printCount = totalPrints,
                    lastPrintedAt = latestPrint,
                    fileName = idealFileName
                ))
            } else {
                val record = recordsForSize[0]
                val idealFileName = fileNameForSize(record.size)
                if (record.fileName != idealFileName) {
                    changed = true
                    val oldFile = File(pdfDir, record.fileName)
                    val newFile = File(pdfDir, idealFileName)
                    if (oldFile.exists()) oldFile.renameTo(newFile)
                    consolidated.add(record.copy(fileName = idealFileName))
                } else {
                    consolidated.add(record)
                }
            }
        }
        
        if (changed) {
            consolidated.sortByDescending { it.id }
            save(consolidated)
        }
    }

    fun pdfDirectory(): File = pdfDir

    fun records(): MutableList<LabelRecord> {
        val result = mutableListOf<LabelRecord>()
        val array = try { JSONArray(prefs.getString("records", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            result += LabelRecord(o.optLong("id"), o.optString("size"), o.optString("file"), o.optLong("created"), o.optInt("prints"), o.optLong("lastPrinted"))
        }
        return result
    }

    fun add(record: LabelRecord) {
        val list = records(); list.add(0, record); save(list)
    }

    fun markPrinted(id: Long) {
        val now = System.currentTimeMillis()
        val total = prefs.getInt("total_printed", 0) + 1
        prefs.edit().putInt("total_printed", total).apply()
        save(records().map { if (it.id == id) it.copy(printCount = it.printCount + 1, lastPrintedAt = now) else it })
    }

    fun recordCreation(size: String) {
        val unique = try { JSONArray(prefs.getString("unique_sizes", "[]")) } catch (_: Exception) { JSONArray() }
        var found = false
        for (i in 0 until unique.length()) {
            if (unique.getString(i) == size) found = true
        }
        if (!found) {
            unique.put(size)
            prefs.edit().putString("unique_sizes", unique.toString()).apply()
        }
    }

    fun totalPrinted(): Int = prefs.getInt("total_printed", 0)
    fun uniqueCreatedCount(): Int = try { JSONArray(prefs.getString("unique_sizes", "[]")).length() } catch (_: Exception) { 0 }

    fun resetStats() {
        prefs.edit().putInt("total_printed", 0).putString("unique_sizes", "[]").apply()
        save(records().map { it.copy(printCount = 0, lastPrintedAt = 0L) })
    }

    fun remove(id: Long) {
        save(records().filterNot { it.id == id })
    }

    fun removeOlderThan(cutoff: Long): Int {
        val old = records().filter { it.createdAt < cutoff }
        old.forEach { File(pdfDir, it.fileName).delete() }
        save(records().filter { it.createdAt >= cutoff })
        return old.size
    }

    fun fileFor(record: LabelRecord) = File(pdfDir, record.fileName)

    fun cacheSize(): Long = pdfDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache(): Boolean {
        return cacheDir.deleteRecursively()
    }

    fun setting(key: String, default: String) = prefs.getString(key, default) ?: default
    fun setSetting(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    private fun save(list: List<LabelRecord>) {
        val array = JSONArray()
        list.forEach { r -> array.put(JSONObject().apply {
            put("id", r.id); put("size", r.size); put("file", r.fileName); put("created", r.createdAt); put("prints", r.printCount); put("lastPrinted", r.lastPrintedAt)
        }) }
        prefs.edit().putString("records", array.toString()).apply()
    }
}
