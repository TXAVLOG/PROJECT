package ms.txams.vv.core.data.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converters for Room database
 * Chuyển đổi các kiểu dữ liệu phức tạp sang JSON để lưu trữ trong SQLite
 */
class TXADatabaseConverters {

    private val gson = Gson()

    // String List converter
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return if (value == null) null else {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, type)
        }
    }

    // Long List converter
    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return if (value == null) null else {
            val type = object : TypeToken<List<Long>>() {}.type
            gson.fromJson(value, type)
        }
    }

    // Integer List converter
    @TypeConverter
    fun fromIntegerList(value: List<Int>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toIntegerList(value: String?): List<Int>? {
        return if (value == null) null else {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson(value, type)
        }
    }

    // String to String Map converter
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return if (value == null) null else {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(value, type)
        }
    }

    // String to Any Map converter (for custom tags)
    @TypeConverter
    fun fromCustomTags(value: Map<String, Any>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toCustomTags(value: String?): Map<String, Any>? {
        return if (value == null) null else {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(value, type)
        }
    }

    // Float Array converter (for audio visualization data)
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFloatArray(value: String?): FloatArray? {
        return if (value == null) null else {
            val type = object : TypeToken<FloatArray>() {}.type
            gson.fromJson(value, type)
        }
    }

    // Double Array converter
    @TypeConverter
    fun fromDoubleArray(value: DoubleArray?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDoubleArray(value: String?): DoubleArray? {
        return if (value == null) null else {
            val type = object : TypeToken<DoubleArray>() {}.type
            gson.fromJson(value, type)
        }
    }

    // Boolean Array converter
    @TypeConverter
    fun fromBooleanArray(value: BooleanArray?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toBooleanArray(value: String?): BooleanArray? {
        return if (value == null) null else {
            val type = object : TypeToken<BooleanArray>() {}.type
            gson.fromJson(value, type)
        }
    }

    // URI converter (for file paths and album art)
    @TypeConverter
    fun fromUri(value: android.net.Uri?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toUri(value: String?): android.net.Uri? {
        return if (value == null) null else android.net.Uri.parse(value)
    }

    // Date/Time converter (Long timestamp)
    @TypeConverter
    fun fromDate(value: java.util.Date?): Long? {
        return value?.time
    }

    @TypeConverter
    fun toDate(value: Long?): java.util.Date? {
        return if (value == null) null else java.util.Date(value)
    }

    // UUID converter
    @TypeConverter
    fun fromUuid(value: java.util.UUID?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toUuid(value: String?): java.util.UUID? {
        return if (value == null) null else java.util.UUID.fromString(value)
    }

    // Enum converter (for playback states, repeat modes, etc.)
    @TypeConverter
    fun fromEnum(value: Enum<*>?): String? {
        return value?.name
    }

    @TypeConverter
    inline fun <reified T : Enum<T>> toEnum(value: String?): T? {
        return if (value == null) null else enumValueOf<T>(value)
    }

    // Color converter (Int to Hex string)
    @TypeConverter
    fun fromColor(value: Int?): String? {
        return value?.let { String.format("#%06X", 0xFFFFFF and it) }
    }

    @TypeConverter
    fun toColor(value: String?): Int? {
        return if (value == null) null else {
            try {
                android.graphics.Color.parseColor(value)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    // JSON Object converter (for complex metadata)
    @TypeConverter
    fun fromJsonObject(value: org.json.JSONObject?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toJsonObject(value: String?): org.json.JSONObject? {
        return if (value == null) null else {
            try {
                org.json.JSONObject(value)
            } catch (e: org.json.JSONException) {
                null
            }
        }
    }

    // JSON Array converter
    @TypeConverter
    fun fromJsonArray(value: org.json.JSONArray?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toJsonArray(value: String?): org.json.JSONArray? {
        return if (value == null) null else {
            try {
                org.json.JSONArray(value)
            } catch (e: org.json.JSONException) {
                null
            }
        }
    }

    // Bundle converter (for complex data structures)
    @TypeConverter
    fun fromBundle(value: android.os.Bundle?): String? {
        return if (value == null) null else {
            // Convert Bundle to Map then to JSON
            val map = mutableMapOf<String, Any?>()
            for (key in value.keySet()) {
                map[key] = value.get(key)
            }
            gson.toJson(map)
        }
    }

    @TypeConverter
    fun toBundle(value: String?): android.os.Bundle? {
        return if (value == null) null else {
            try {
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map = gson.fromJson<Map<String, Any?>>(value, type)
                val bundle = android.os.Bundle()
                for ((key, mapValue) in map) {
                    when (mapValue) {
                        is String -> bundle.putString(key, mapValue)
                        is Int -> bundle.putInt(key, mapValue)
                        is Long -> bundle.putLong(key, mapValue)
                        is Float -> bundle.putFloat(key, mapValue)
                        is Double -> bundle.putDouble(key, mapValue)
                        is Boolean -> bundle.putBoolean(key, mapValue)
                        // Add more types as needed
                    }
                }
                bundle
            } catch (e: Exception) {
                null
            }
        }
    }

    // Bitmap converter (for album art thumbnails - store as Base64)
    @TypeConverter
    fun fromBitmap(value: android.graphics.Bitmap?): String? {
        return if (value == null) null else {
            val outputStream = java.io.ByteArrayOutputStream()
            value.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
        }
    }

    @TypeConverter
    fun toBitmap(value: String?): android.graphics.Bitmap? {
        return if (value == null) null else {
            try {
                val bytes = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
    }
}
