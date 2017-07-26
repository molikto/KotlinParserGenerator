package org.snailya.kotlinparsergenerator


import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringWriter
import java.util.*
import java.util.Collections.emptyList

object JsonAdapterGlobalConfig {
  var globalUnknownFieldReporter: (String, JsonAdapter<*>) -> Unit = { _, _ -> }

  fun <T> logKnownField(str: String, companion: JsonAdapter<T>) {
    globalUnknownFieldReporter.invoke(str, companion)
  }
}

class JsonParsingException(parent: Exception) : Exception(parent)

@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
abstract class JsonAdapter<T> {



  private var aa: JsonAdapter<List<T>>? = null
  fun arrayAdapter(): JsonAdapter<List<T>> {
    if (aa == null) aa = arrayAdapter(this)
    return aa!!
  }

  private var na: JsonAdapter<T?>? = null

  fun nullAdapter(): JsonAdapter<T?> {
    if (na == null) na = nullAdapter(this)
    return na!!
  }
//
//    private var ma: JsonAdapter<Map<String, T>>? = null
//    fun MAP_ADAPTER(): JsonAdapter<Map<String, T>> {
//        if (ma == null) ma = MAP_ADAPTER(this)
//        return ma!!
//    }

  /**
   * input can be json null, output can also be null
   */
  abstract fun parse(jp: JsonParser): T

  abstract val empty: T

  /**
   * null must be transformed to json null, so in some cases the whole code will take this shortcut
   */
  abstract fun serialize(t: T, jg: JsonGenerator, writeStartEndObject: Boolean)


  open fun parse(bytes: ByteArray): T {
    try {
      val jsonParser = jsonFactory().createParser(bytes)
      jsonParser.nextToken()
      val t = parse(jsonParser)
      jsonParser.close()
      return t
    } catch (e: Exception) {
      throw JsonParsingException(e)
    }
  }


  fun parse(`is`: InputStream): T {
    try {
      val jsonParser = jsonFactory().createParser(`is`)
      jsonParser.nextToken()
      val t = parse(jsonParser)
      jsonParser.close()
      return t
    } catch (e: Exception) {
      throw JsonParsingException(e)
    }
  }


  fun parse(jsonString: String): T {
    try {
      val jsonParser = jsonFactory().createParser(jsonString)
      jsonParser.nextToken()
      val t = parse(jsonParser)
      jsonParser.close()
      return t
    } catch (e: Exception) {
      throw JsonParsingException(e)
    }
  }

  //	public byte[] serialize(T object) throws IOException {
  //		ByteArrayOutputStream out = new ByteArrayOutputStream();
  //		JsonGenerator generator = jsonFactory().createGenerator(out);
  //		serialize(object, generator, true);
  //		generator.close();
  //		return out.toByteArray();
  //	}

  fun serialize(obj: T): String {
    val sw = StringWriter()
    val jsonGenerator = jsonFactory().createGenerator(sw)
    serialize(obj, jsonGenerator, true)
    jsonGenerator.close()
    sw.close()
    return sw.toString()
  }

  /**
   * Serialize an object to an OutputStream.

   * @param object The object to serialize.
   * *
   * @param os The OutputStream being written to.
   */
  fun serialize(`object`: T, os: OutputStream) {
    val jsonGenerator = jsonFactory().createGenerator(os)
    serialize(`object`, jsonGenerator, true)
    jsonGenerator.close()
  }

  companion object {
    var jf: JsonFactory? = null
    fun jsonFactory(): JsonFactory {
      if (jf == null) {
        synchronized(JsonAdapter::class.java) {
          if (jf == null) {
            jf = JsonFactory()
          }
        }
      }
      return jf!!
    }


    val unitAdapter: JsonAdapter<Unit> = object : JsonAdapter<Unit>() {

      override val empty = Unit

      override fun parse(jp: JsonParser): Unit {
        return Unit
      }

      override fun serialize(t: Unit, jg: JsonGenerator, writeStartEndObject: Boolean) {
        throw IllegalStateException()
      }
    }


    val doubleAdapter: JsonAdapter<Double> = object : JsonAdapter<Double>() {

      override val empty = 0.0

      override fun parse(jp: JsonParser): Double {
        return jp.valueAsDouble
      }

      override fun serialize(t: Double, jg: JsonGenerator, writeStartEndObject: Boolean) {
        jg.writeNumber(t)
      }
    }

    val longAdapter: JsonAdapter<Long> = object : JsonAdapter<Long>() {

      override val empty = 0L

      override fun parse(jp: JsonParser): Long {
        return jp.valueAsLong
      }

      override fun serialize(t: Long, jg: JsonGenerator, writeStartEndObject: Boolean) {
        jg.writeNumber(t)
      }
    }

    val intAdapter: JsonAdapter<Int> = object : JsonAdapter<Int>() {

      override val empty = 0

      override fun parse(jp: JsonParser): Int {
        return jp.valueAsInt
      }

      override fun serialize(t: Int, jg: JsonGenerator, writeStartEndObject: Boolean) {
        jg.writeNumber(t)
      }
    }

    val stringAdapter: JsonAdapter<String> = object : JsonAdapter<String>() {

      override val empty = ""

      override fun parse(jp: JsonParser): String {
        return jp.getValueAsString("") // this will not throw error!!!
      }

      override fun serialize(t: String, jg: JsonGenerator, writeStartEndObject: Boolean) {
        jg.writeString(t)
      }
    }

//        fun <T> MAP_ADAPTER(from: JsonAdapter<T>): JsonAdapter<Map<String, T>> {
//            return object : JsonAdapter<Map<String, T>>() {
//                @Throws(IOException::class)
//                override fun parse(jp: JsonParser): Map<String, T> {
//                    return parseMap(jp, from)
//                }
//
//                @Throws(IOException::class)
//                override fun serialize(ts: Map<String, T>, jg: JsonGenerator, writeStartEndObject: Boolean) {
//                    serializeMap(ts, jg, from)
//                }
//
//                @Throws(IOException::class)
//                override fun parse(bytes: ByteArray?): Map<String, T>? {
//                    if (bytes == null) {
//                        return null
//                    } else if (bytes.size == 2) {
//                        return HashMap()
//                    } else {
//                        return super.parse(bytes)
//                    }
//                }
//            }
//        }

    fun <T> nullAdapter(from: JsonAdapter<T>): JsonAdapter<T?> {
      return object : JsonAdapter<T?>() {

        override val empty = null

        override fun parse(jp: JsonParser): T? {
          val next = jp.currentToken
          if (next == JsonToken.VALUE_NULL) {
            return null
          } else {
            return from.parse(jp)
          }
        }

        override fun serialize(t: T?, jg: JsonGenerator, writeStartEndObject: Boolean) {
          if (t == null) {
            jg.writeNull()
          } else {
            from.serialize(t)
          }
        }

      }
    }

    fun <T> arrayAdapter(from: JsonAdapter<T>): JsonAdapter<List<T>> {
      return object : JsonAdapter<List<T>>() {

        override val empty = emptyList<T>()

        override fun parse(jp: JsonParser): List<T> {
          return parseArray(jp, from)
        }

        override fun serialize(t: List<T>, jg: JsonGenerator, writeStartEndObject: Boolean) {
          serializeArray(t, jg, from)
        }

        override fun parse(bytes: ByteArray): List<T> {
          if (bytes.size <= 2) {
            return emptyList()
          } else {
            return super.parse(bytes)
          }
        }
      }
    }


    /**
     * helper
     */

    protected fun <K> serializeArray(arr: List<K>, jsonGenerator: JsonGenerator, adapter: JsonAdapter<K>) {
      jsonGenerator.writeStartArray()
      for (element1 in arr) {
        if (element1 != null) {
          adapter.serialize(element1, jsonGenerator, true)
        }
      }
      jsonGenerator.writeEndArray()
    }

//        @Throws(IOException::class)
//        protected fun <K> serializeMap(arr: Map<String, K>?, jsonGenerator: JsonGenerator, adapter: JsonAdapter<K>) {
//            if (arr == null) {
//                jsonGenerator.writeNull()
//            } else {
//                jsonGenerator.writeStartObject()
//                for ((key, value) in arr) {
//                    jsonGenerator.writeFieldName(key)
//                    adapter.serialize(value, jsonGenerator, true)
//                }
//                jsonGenerator.writeEndObject()
//            }
//        }

    protected fun <K> parseArray(jsonParser: JsonParser, adapter: JsonAdapter<K>): List<K> {
      if (jsonParser.currentToken == JsonToken.START_ARRAY) {
        val collection1 = ArrayList<K>(0)
        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
          val value1: K = adapter.parse(jsonParser)
          collection1.add(value1)
        }
        return collection1
      } else {
        return emptyList()
      }
    }

//        @Throws(IOException::class)
//        protected fun <K> parseMap(jsonParser: JsonParser, adapter: JsonAdapter<K>): HashMap<String, K>? {
//            if (jsonParser.currentToken == JsonToken.START_OBJECT) {
//                val map1 = HashMap<String, K>()
//                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
//                    val key1 = jsonParser.text
//                    jsonParser.nextToken()
//                    map1.put(key1, adapter.parse(jsonParser))
//                }
//                return map1
//            } else {
//                return null
//            }
//        }
  }
}
