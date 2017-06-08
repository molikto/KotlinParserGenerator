package org.snailya.demo.data;


import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import android.util.Log
import android.net.Uri
import java.io.*
import java.util.*
import android.text.TextUtils
import org.snailya.kotlinparsergenerator.*
import snailya.org.kotlinparsergenerator.JsonAdapter
import snailya.org.kotlinparsergenerator.ObjectJsonAdapter
import snailya.org.kotlinparsergenerator.EnumJsonAdapter
import java.io.IOException
import java.text.SimpleDateFormat


data class Location(
        @JvmField val latitude: Double,
        @JvmField val longitude: Double,
        @JvmField val coordinateType: CoordinateType?,
        @JvmField val countryCode: String?,
        @JvmField val countryName: String?,
        @JvmField val cityName: String?,
        @JvmField val someArray: List<String>,
        @JvmField val someUrl: Uri?,
        @JvmField val cached: Boolean)  : Serializable  {
  companion object : ObjectJsonAdapter<Location>() {
    override val empty: Location = Location(0.0, 0.0, null, null, null, null, emptyList(), null, false)
    override fun parse(jp: JsonParser): Location {
      // Our code ensures all parse method will not throw error, if null encountered
      if (jp.currentToken != JsonToken.START_OBJECT) {
        jp.skipChildren()
        return empty
      }
      var latitude: Double = 0.0
      var longitude: Double = 0.0
      var coordinateType: CoordinateType? = null
      var countryCode: String? = null
      var countryName: String? = null
      var cityName: String? = null
      var someArray: List<String> = emptyList()
      var someUrl: Uri? = null
      var cached: Boolean = false
      while (jp.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = jp.currentName
        jp.nextToken()
        when(fieldName) {
          "latitude" -> { latitude = jp.valueAsDouble }
          "longitude" -> { longitude = jp.valueAsDouble }
          "coordinateType" -> { coordinateType = CoordinateType.Companion.nullAdapter().parse(jp) }
          "countryCode" -> { countryCode = JsonAdapter.stringAdapter.nullAdapter().parse(jp) }
          "countryName" -> { countryName = JsonAdapter.stringAdapter.nullAdapter().parse(jp) }
          "cityName" -> { cityName = JsonAdapter.stringAdapter.nullAdapter().parse(jp) }
          "someArray" -> { someArray = JsonAdapter.stringAdapter.arrayAdapter().parse(jp) }
          "someUrl" -> { someUrl = stringToUri.parse(jp) }
          "cached" -> { cached = jp.valueAsBoolean }
          else -> { logUnknownField(fieldName, Location.Companion) }
        }
        jp.skipChildren()
      }
      return Location(latitude, longitude, coordinateType, countryCode, countryName, cityName, someArray, someUrl, cached)
    }

    override fun serializeFields(t: Location, jg: JsonGenerator) {
      jg.writeNumberField("latitude", t.latitude)
      jg.writeNumberField("longitude", t.longitude)
      if (t.coordinateType != null) { jg.writeFieldName("coordinateType"); CoordinateType.Companion.serialize(t.coordinateType, jg, true) }
      if (t.countryCode != null) { jg.writeFieldName("countryCode"); JsonAdapter.stringAdapter.serialize(t.countryCode, jg, true) }
      if (t.countryName != null) { jg.writeFieldName("countryName"); JsonAdapter.stringAdapter.serialize(t.countryName, jg, true) }
      if (t.cityName != null) { jg.writeFieldName("cityName"); JsonAdapter.stringAdapter.serialize(t.cityName, jg, true) }
      jg.writeFieldName("someArray"); JsonAdapter.stringAdapter.arrayAdapter().serialize(t.someArray, jg, true)
      jg.writeFieldName("someUrl"); stringToUri.serialize(t.someUrl, jg, true)
      jg.writeBooleanField("cached", t.cached)
    }
  }
/* EXTRA CODE START */
  fun yourOwnCode() = "Will not be replaced, we mark them using simple Java/Kotlin block comments"
/* EXTRA CODE END */
}
     