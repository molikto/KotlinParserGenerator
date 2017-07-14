package org.snailya.kotlinparsergenerator


import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import org.snailya.kotlinparsergenerator.JsonAdapter
import java.io.IOException

abstract class ObjectJsonAdapter<T> : JsonAdapter<T>() {

  protected fun skipStart(jsonParser: JsonParser) {
    if (jsonParser.currentToken != JsonToken.START_OBJECT) {
      jsonParser.skipChildren()
      throw IOException("Unexpected token")
    }
  }

  @Throws(IOException::class)
  override fun serialize(t: T, jg: JsonGenerator, writeStartEndObject: Boolean) {
    if (writeStartEndObject) jg.writeStartObject()
    serializeFields(t, jg)
    if (writeStartEndObject) jg.writeEndObject()
  }


  @Throws(IOException::class)
  protected abstract fun serializeFields(t: T, jg: JsonGenerator)
}
