import java.io.File
import java.io.PrintWriter
import java.util.*

/**
  * this class generates parsers for Kotlin data classes,
  *
  * it is null safe, all not null type (T, not T?) has a default value, which is not null
  *
  * for T?, it is null
  * for numbers, it is 0 of the type
  * for string, it is ""
  * for boolean, it is false
  * for data objects, it is the data object will all fields of the default value
  *
  * TODO if these defaults is not what we have in mind, we need to created a withDefault method
  *
  * thus, it is safe to declare a type as not null, even when the json string is null for the type
  *
  * when this happens, it will print a warning
  */


abstract class Global {

  val ExtraCodeStart = "/* EXTRA CODE START */"
  val ExtraCodeMark = "/* EXTRA CODE MARK */"
  val ExtraCodeEnd = "/* EXTRA CODE END */"

  val ParseFinishStart = "/* PARSE FINISH START */"
  val ParseFinishMark = "/* PARSE FINISH MARK */"
  val ParseFinishEnd = "/* PARSE FINISH END */"

  fun indent(i: String, statement: String) = {
    statement.split("\n").filter{ !it.trim().isEmpty() }.map {i + it }.joinToString("\n")
  }
}




abstract class Type(open val name: String) : Global() {

  abstract fun default(): String
  abstract fun parserObject(): String
  abstract fun parse(): String

  val n get() = NullableType(this)
  val nullName get() = if (name.endsWith("?")) name else  name + "?"
}

object IntType : Type("Int") {
  override fun default() = "0"
  override fun parse() = "jp.valueAsInt"
  override fun parserObject() = "JsonAdapter.intAdapter"
}
object LongType : Type("Long") {
  override fun default() = "0L"
  override fun parse() = "jp.valueAsLong"
  override fun parserObject() = "JsonAdapter.longAdapter"
}
object DoubleType : Type("Double") {
  override fun default() = "0.0"
  override fun parse() = "jp.valueAsDouble"
  override fun parserObject() = "JsonAdapter.doubleAdapter"
}
object BooleanType : Type("Boolean") {
  override fun default() = "false"
  override fun parse() = "jp.valueAsBoolean"
  override fun parserObject() = "JsonAdapter.booleanAdapter"
}
object StringType : Type("String") {
  override fun default() = "\"\""
  override fun parse() = "jp.getValueAsString(\"\")"
  override fun parserObject() = "JsonAdapter.stringAdapter"
}


data class NullableType(val t: Type) : Type(t.name + "?") {
  override fun default() = "null"
  override fun parserObject() = t.parserObject() +".nullAdapter()"
  override fun parse() = parserObject() + ".parse(jp)"
}

data class ListType(val t: Type) : Type("List<${t.name}>") {
  override fun default() = "emptyList()"
  override fun parserObject() = t.parserObject() +".arrayAdapter()"
  override fun parse() = parserObject() + ".parse(jp)"
}

data class JvmType(override val name: String, val defaultStr: String) : Type(name) {
  override fun default() = defaultStr
  override fun parserObject() = throw Exception("Not allowed")
  override fun parse() = throw Exception("Not allowed")
}

data class HandmadeObjectType(override val name: String) : Type(name) {
  override fun default() = "${name}.Companion.empty"
  override fun parserObject() = name + ".Companion"
  override fun parse() = parserObject() + ".parse(jp)"
}

data class Field(val name: String, val ty: Type, val jsonName: String?, val isPrivate: Boolean = false) {

  val jn= jsonName ?: name
  fun parse() = """"$jn" -> { $name = ${ty.parse()} }"""

  fun declareTemp() = "var $name: ${ty.name} = ${ty.default()}"

  fun tempToField() = name // if (ty.isInstanceOf[NullableType]) name else name + "!!"

  fun declare() = "        ${(if (isPrivate) "private " else "@JvmField ")}val $name: ${ty.name}"

  fun serialize() {
    if (ty == IntType || ty == DoubleType || ty == LongType) {
      """jg.writeNumberField("$jn", t.$name)"""
    } else if (ty == StringType) {
      """jg.writeStringField("$jn", t.$name)"""
    } else if (ty == BooleanType) {
      """jg.writeBooleanField("$jn", t.$name)"""
    } else if (ty is NullableType) {
      """if (t.$name != null) { jg.writeFieldName("$jn"); ${ty.t.parserObject()}.serialize(t.$name, jg, true) }"""
    } else {
      """jg.writeFieldName("$jn"); ${ty.parserObject()}.serialize(t.$name, jg, true)"""
    }
  }
}



data class ObjectType(override val name: String, val fields: List<Field>) : Type(name) {
  override fun default() = "$name.Companion.empty"
  fun codgen() =
    """
       |
       |data class $name(
       |${fields.map{it.declare()}.joinToString(",\n")})  : Serializable  {
       |  companion object : ObjectJsonAdapter<$name>() {
       |    override val empty: $name = $name(${fields.map { it.ty.default() }.joinToString(", ")})
       |    override fun parse(jp: JsonParser): $name {
       |      // Our code ensures all parse method will not throw error, if null encountered
       |      if (jp.currentToken != JsonToken.START_OBJECT) {
       |        jp.skipChildren()
       |        return empty
       |      }
       |${indent("      ", fields.map { it.declareTemp() }.joinToString("\n"))}
       |      while (jp.nextToken() != JsonToken.END_OBJECT) {
       |        val fieldName = jp.currentName
       |        jp.nextToken()
       |        when(fieldName) {
       |${indent("          ", fields.map { it.parse()}.joinToString("\n"))}
       |          else -> { logUnknownField(fieldName, ${name}.Companion) }
       |        }
       |        jp.skipChildren()
       |      }
       |      return $name(${fields.map { it.tempToField()}.joinToString(", ")})
       |    }
       |
       |    override fun serializeFields(t: $name, jg: JsonGenerator) {
       |${indent("      ", fields.map { it.serialize()}.joinToString("\n"))}
       |    }
       |  }
       |$ExtraCodeMark
       |}
     """.trimMargin()

  override fun parserObject() = name + ".Companion"

  override fun parse() = parserObject() + ".parse(jp)"
}
data class EnumType(override val name: String, val vs: List<String>, val defaultPos: Int) : Type(name) {
  override fun default() = name + "." + vs[defaultPos]
  fun codgen() = """
  |enum class $name {
  |  ${vs.joinToString(", ")};
  |  companion object : EnumByOrdinalJsonAdapter<$name>(arrayOf(${vs.map{a -> name + "." + a}.joinToString(", ")}), $name.${vs[defaultPos]})
  |}
  """.trimMargin()

  override fun parserObject() = "${name}.Companion"
  override fun parse() = parserObject() + ".parse(jp)"
}




data class ConvertedType(val json: Type, val jvm: Type, val converter: String) : Type(jvm.name) {
  override fun default() = jvm.default()

  override fun parserObject() = converter

  override fun parse() = parserObject() + ".parse(jp)"
}




open class Spec(val packageName: String, val root: File, val imports: List<String>) : Global() {


  val int = IntType
  val long = LongType
  val double = DoubleType
  val boolean = BooleanType
  val string = StringType

  fun list(t: Type) = ListType(t)


  fun f(name: String, ty: Type) = Field(name, ty, null)
  fun f(name: String,  json: String, ty: Type) = Field(name, ty, json)

  open val defaultImports =
      """
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import android.util.Log
import android.net.Uri
import java.io.*
import java.util.*
import android.text.TextUtils
import android.util.Log
import org.snailya.kotlinparsergenerator.*
import snailya.org.kotlinparsergenerator.JsonAdapter
import snailya.org.kotlinparsergenerator.ObjectJsonAdapter
import snailya.org.kotlinparsergenerator.EnumByOrdinalJsonAdapter
import java.io.IOException
import java.text.SimpleDateFormat
""".trimMargin()

  val dest = File(root.toString() + "/" + packageName.replace('.', '/'))

  fun writeClass(className: String, cont: String) {
    println("writing class " + className)
    var content = cont
    val des = File(dest, className + ".kt")
    des.mkdirs()
    var imps = ArrayList<String>()
    if (des.isFile) {
      val lines: List<String> = des.readLines()
      var extra = ""
      var started = false
      for (line in lines) {
        if (line.startsWith("import ") && !imps.contains(line.trim())) {
          imps.add(line)
        }
        if (line.contains(ExtraCodeStart)) {
          started = true
        } else if (line.contains(ExtraCodeEnd)) {
          started = false
        } else if (started) {
          extra = extra + line + "\n"
        }
      }
      if (!started) {
        extra = ExtraCodeStart + "\n" + extra + ExtraCodeEnd
        content = content.replace(ExtraCodeMark, extra)
      }
      var parseComplete = ""
      started = false
      for (line in lines) {
        if (line.contains(ParseFinishStart)) {
          started = true
        } else if (line.contains(ParseFinishEnd)) {
          started = false
        } else if (started) {
          parseComplete = parseComplete + line + "\n"
        }
      }
      if (!started) {
        parseComplete = ParseFinishStart + "\n" + parseComplete + ParseFinishEnd
        content = content.replace(ParseFinishMark, parseComplete)
      }
    }
    content = "package " + packageName + "\n\n" + (defaultImports + (listOf(imps, imports).flatten()).joinToString("\n")).split("\n").toSortedSet().joinToString("\n") + "\n\n\n" + content
    des.delete()
    des.createNewFile()
    val writer = PrintWriter(des)
    writer.write(content)
    writer.close()
  }

  fun writeFile(des: File, cont: String) {
    des.mkdirs()
    des.delete()
    des.createNewFile()
    val writer = PrintWriter(des)
    writer.write(cont)
    writer.close()
  }


  val os = ArrayList<ObjectType>()
  val es = ArrayList<EnumType>()
  fun o(s: String, vararg fs: Field): ObjectType {
    val o = ObjectType(s, fs.toList())
    os.add(o)
    return o
  }

  fun e(s: String, de: Int, vararg fs: String): EnumType {
    val o = EnumType(s, fs.toList(), de)
    es.add(o)
    return o
  }

  fun codegen() {
    println("codegen...")
    es.forEach{e -> writeClass(e.name, e.codgen())}
    os.forEach{o -> writeClass(o.name, o.codgen())}
  }
}


object BaseSpec : Spec("org.snailya.demo.data", File("library/src/main/java"), emptyList()) {

  val stringToUri = ConvertedType(string, JvmType("Uri", "defaultUri").n, "stringToUri")

  init {
    e("SomeEnum", 0, "enum1", "enum2")

    o("Location",
        f("latitude", double),
        f("longitude", double),
        f("coordinateType", HandmadeObjectType("CoordinateType").n),
        f("countryCode", string.n),
        f("countryName", string.n),
        f("cityName", string.n),
        f("someArray", list(string)),
        f("someUrl", stringToUri),
        f("cached", boolean)
    )
  }
}


BaseSpec.codegen()

