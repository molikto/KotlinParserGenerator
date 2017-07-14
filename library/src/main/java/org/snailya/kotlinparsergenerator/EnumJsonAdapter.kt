package snailya.org.kotlinparsergenerator

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser

open class EnumByOrdinalJsonAdapter<T : Enum<*>>(val enums: Array<T>, override val empty: T) : JsonAdapter<T>() {

  override fun parse(jp: JsonParser): T {
    val i = jp.valueAsInt
    if (i >= 0 && i < enums.size) return enums[jp.valueAsInt]
    else return empty
  }

  override fun serialize(t: T, jg: JsonGenerator, writeStartEndObject: Boolean) = jg.writeNumber(t.ordinal)
}
