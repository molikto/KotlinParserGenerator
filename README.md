

# Kotlin Parser Generator

This is a simple Scala script and some Kotlin file to generate JSON parsering code.

Why another JSON parser library?

Well... because their shouldn't be something like a JSON parser generator library...

Why I say this?

Because everyone might have their own requirements of the library, how fields is mapped etc.

And there are two ways to do these:

* A big library with a lot of configurations
* A small working basic code which can be hacked quickly

And this is of the second kind.


## Just a DEMO. NOT A LIBRARY!

But if you want some extra functionality, I might implement it.


## Why use it?

* hackable: it is 150 line of Scala, plus like 100 line of Kotlin
* it targets to Kotlin, null-field safe (everything has a default value, you can change it to throw exception if you like)
* consistent api (when used in Kotlin)
    * for primitive types: `JsonAdapter.intAdapter` etc.
    * for object/enum types: `TypeName.Companion`
    * array types: `TypeName.Companion.arrayAdapter`, it is kind of ugly for reflection based API to deal with generic lists
    * nullable types: `TypeName.Companion.nullAdapter`

## How to use it?

Copy `ParserGenerator.scala` and files in package `org.snailya.kotlinparsergenerator` to proper places in your project. The object definition is written in
the bottom of `ParserGenerator.scala` file, with small examples


It will require you define a method `logUnknownField` in the package you choose.

If you define some `ConvertedType`, the converter should be defined in same package

## Sample

```scala
object BaseSpec extends Spec("org.snailya.demo.data", new File("app/src/main/java"), Seq.empty) {


  lazy val stringToUri = ConvertedType(string, JvmType("Uri", "defaultUri").?, "stringToUri")

  e("SomeEnum", 0, "enum1", "enum2")

  o("Location",
    f("latitude", double),
    f("longitude", double),
    f("coordinateType", HandmadeObjectType("CoordinateType").?),
    f("countryCode", string.?),
    f("countryName", string.?),
    f("cityName", string.?),
    f("someArray", list(string)),
    f("someUrl", stringToUri),
    f("cached", boolean)
  )
}

BaseSpec.codegen()
```

generates

```kotlin
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
```

Sample converter definition

```kotlin
val  stringToUri = object : ConvertedJsonAdapter<String, Uri?>(stringAdapter) {
  override fun to(from: String): Uri? = if (from.isEmpty()) null else try { Uri.parse(from) } catch (e: Exception) { null }
  override fun from(to: Uri?): String = to?.toString() ?: ""
}
```

## Extra

* The code is currently in Scala, but can be changed to Kotlin easily. (but we cannot use `string.?` then)
* Why not annotation? Because they are not simple, and they are not first-class citizen