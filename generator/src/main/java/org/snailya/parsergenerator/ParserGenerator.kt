package org.snailya.parsergenerator

import java.io.File
import java.io.PrintWriter
import java.util.*
import java.util.Collections.emptyList

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


val ExtraCodeStart = "/* EXTRA CODE START */"
val ExtraCodeMark = "/* EXTRA CODE MARK */"
val ExtraCodeEnd = "/* EXTRA CODE END */"

val ParseFinishStart = "/* PARSE FINISH START */"
val ParseFinishMark = "/* PARSE FINISH MARK */"
val ParseFinishEnd = "/* PARSE FINISH END */"

fun indent(i: String, statement: String): String =
  statement.split("\n").filter{ !it.trim().isEmpty() }.map {i + it }.joinToString("\n")



abstract class Type(open val name: String)  {

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
  override fun default() = "$name.Companion.empty"
  override fun parserObject() = name + ".Companion"
  override fun parse() = parserObject() + ".parse(jp)"
}

data class Field(val name: String, val ty: Type, val jsonName: String?, val isPrivate: Boolean = false, val deprecated: String = "", var isOverride: Boolean = false) {

  val jn= jsonName ?: name
  fun parse() = """"$jn" -> { $name = ${ty.parse()} }"""

  fun declareTemp() ="var $name: ${ty.name} = ${ty.default()}"

  fun tempToField() = name // if (ty.isInstanceOf[NullableType]) name else name + "!!"

  fun declare() = "        ${ (if (!deprecated.isEmpty()) "@Deprecated(\"$deprecated\") " else "")}${(if (isPrivate) "private " else "@JvmField ")}${(if (isOverride) "override " else "")}val $name: ${ty.name}"

  fun serialize() =
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



data class ObjectType(override val name: String, private val fields: List<Field>, var implements: String? = null) : Type(name) {
  override fun default() = "$name.Companion.empty"

  private val extraImplements get() = if (implements != null) ("," + implements) else ""

  fun codgen() =
    """
       |
       |data class $name(
       |${fields.map{it.declare()}.joinToString(",\n")})  : Serializable $extraImplements {
       |
       |$ExtraCodeMark
       |
       |
       |  // BELLOW IS GENERATED CODE!!! DON'T CHANGE!!!
       |
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
       |          else -> { JsonAdapterGlobalConfig.logUnknownField(fieldName, ${name}.Companion) }
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
       |}
     """.trimMargin()

  override fun parserObject() = name + ".Companion"

  override fun parse() = parserObject() + ".parse(jp)"
}

data class IntEnumItem(val name: String, val value: Int)


data class IntEnumType(override val name: String, val vs: List<IntEnumItem>, val defaultPos: Int) : Type(name) {
  override fun default() = name + "." + vs[defaultPos].name
  fun codgen() = """
  |enum class $name(override val value: Int) : IntEnum {
  |  ${vs.joinToString(", ") { "${it.name}(${it.value})"}};
  |  companion object : IntEnumJsonAdapter<$name>(arrayOf(${vs.map{a -> name + "." + a.name}.joinToString(", ")}), $name.${vs[defaultPos].name})
  |  override fun toString(): String = Companion.serialize(this)
  |}
  """.trimMargin()

  override fun parserObject() = "${name}.Companion"
  override fun parse() = parserObject() + ".parse(jp)"
}

data class StringEnumItem(val name: String, val value: String)


data class StringEnumType(override val name: String, val vs: List<StringEnumItem>, val defaultPos: Int) : Type(name) {
  override fun default() = name + "." + vs[defaultPos].name
  fun codgen() = """
  |enum class $name(override val value: String) : StringEnum {
  |  ${vs.joinToString(", ") { "${it.name}(\"${it.value}\")"}};
  |  companion object : StringEnumJsonAdapter<$name>(arrayOf(${vs.map{a -> name + "." + a.name}.joinToString(", ")}), $name.${vs[defaultPos].name})
  |  override fun toString(): String = Companion.serialize(this)
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




open class Spec(val packageName: String, val root: File, val imports: List<String>) {


  init {
    println("root is ${root.absolutePath}")
  }
  val int = IntType
  val long = LongType
  val double = DoubleType
  val boolean = BooleanType
  val string = StringType

  fun list(t: Type) = ListType(t)


  fun f(name: String, ty: Type, deprecated: String = "") = Field(name, ty, null, deprecated = deprecated)
  fun f(name: String,  json: String, ty: Type, deprecated: String = "") = Field(name, ty, json, deprecated = deprecated)

  open val defaultImports =
      """
import com.fasterxml.jackson.core.*
import java.io.*
import java.util.*
import org.snailya.kotlinparsergenerator.*
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
    content = "package " + packageName + "\n\n" + (defaultImports + "\n" + (listOf(imps, imports).flatten()).joinToString("\n")).split("\n").toSortedSet().joinToString("\n") + "\n\n\n" + content
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
  val ies = ArrayList<IntEnumType>()
  val ses = ArrayList<StringEnumType>()
  fun o(s: String, vararg fs: Field): ObjectType {
    val o = ObjectType(s, fs.toList())
    os.add(o)
    return o
  }

  operator fun String.div(str: String)  = StringEnumItem(this, str)

  operator fun String.div(str: Int) = IntEnumItem(this, str)

  fun stringEnum(s: String, de: Int, vararg fs: StringEnumItem): StringEnumType {
    val o = StringEnumType(s, fs.toList(), de)
    ses.add(o)
    return o
  }

  fun intEnum(s: String, de: Int, vararg fs: IntEnumItem): IntEnumType {
    val o = IntEnumType(s, fs.toList(), de)
    ies.add(o)
    return o
  }

  fun codegen() {
    println("codegen...")
    ies.forEach{ e -> writeClass(e.name, e.codgen())}
    ses.forEach{ e -> writeClass(e.name, e.codgen())}
    os.forEach{o -> writeClass(o.name, o.codgen())}
  }

}


object BaseSpec : Spec("org.snailya.demo.data", File("generator/src/main/java"), emptyList()) {

  @JvmStatic
  fun main(args: Array<String>): Unit = codegen()

  val stringToUri = ConvertedType(string, JvmType("Uri", "defaultUri").n, "stringToUri")

  init {

    stringEnum("SomeStringEnum",
        0,
        "CLASSIC" / "what",
        "LITE" / "jiji",
        "SPOCK" / "this"
    )
    intEnum("SomeEnum",
        0,
        "CLASSIC" / 1,
        "LITE" / 2,
        "SPOCK" / 3
    )

    o("Location",
        f("latitude", double).apply { isOverride = true },
        f("longitude", double),
        f("coordinateType", HandmadeObjectType("CoordinateType").n),
        f("countryCode", string.n),
        f("countryName", string.n),
        f("cityName", string.n),
        f("someArray", list(string)),
        f("someUrl", stringToUri),
        f("cached", boolean)
    ).apply {
      implements = "What"
    }
  }
}



