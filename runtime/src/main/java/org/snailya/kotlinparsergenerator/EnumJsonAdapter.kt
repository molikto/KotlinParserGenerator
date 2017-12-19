package org.snailya.kotlinparsergenerator

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import org.snailya.kotlinparsergenerator.JsonAdapter


interface IntEnum {
  val value: Int
}

interface StringEnum {
  val value: String
}

enum class IntEnumTest(override val value: Int) : IntEnum {
  TEST(1);
}

open class IntEnumJsonAdapter<T : IntEnum>(val enums: Array<T>, override val empty: T) : JsonAdapter<T>() {

  override fun parse(jp: JsonParser): T {
    val i = jp.valueAsInt
    return enums.firstOrNull { it.value == i }
        ?: empty
  }

  override fun serialize(t: T, jg: JsonGenerator, writeStartEndObject: Boolean) = jg.writeNumber(t.value)
}

open class StringEnumJsonAdapter<T : StringEnum>(val enums: Array<T>, override val empty: T) : JsonAdapter<T>() {

  override fun parse(jp: JsonParser): T {
    val i = jp.valueAsString
    return enums.firstOrNull { it.value == i }
        ?: empty
  }

  override fun serialize(t: T, jg: JsonGenerator, writeStartEndObject: Boolean) = jg.writeString(t.value)
}
