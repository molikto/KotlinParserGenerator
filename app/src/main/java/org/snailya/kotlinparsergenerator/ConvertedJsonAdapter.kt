package snailya.org.kotlinparsergenerator


import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser

import java.io.IOException

abstract class ConvertedJsonAdapter<T, K>(private val from: JsonAdapter<T>) : JsonAdapter<K>() {

  // TODO is this wrong?
  override val empty: K
    get() = to(from.empty)

  @Throws(IOException::class)
  final override fun parse(jp: JsonParser): K {
    return to(from.parse(jp))
  }

  abstract fun to(from: T): K

  /**
   * if to is null, T must be null
   * @param to
   * *
   * @return
   */
  abstract fun from(to: K): T

  @Throws(IOException::class)
  final override fun serialize(t: K, jg: JsonGenerator, writeStartEndObject: Boolean) {
    from.serialize(from(t), jg, writeStartEndObject)
  }
}
