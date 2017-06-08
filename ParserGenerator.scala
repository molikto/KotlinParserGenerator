import java.io.{File, PrintWriter}

import scala.collection.mutable


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

sealed abstract class Type(val name: String) {

  def default(): String
  def parserObject(): String
  def parse(): String

  def ? = NullableType(this)
  def nullName = if (name.endsWith("?")) name else  name + "?"
}

case object IntType extends Type("Int") {
  override def default() = "0"
  override def parse() = "jp.valueAsInt"
  override def parserObject() = "JsonAdapter.intAdapter"
}
case object LongType extends Type("Long") {
  override def default() = "0L"
  override def parse() = "jp.valueAsLong"
  override def parserObject() = "JsonAdapter.longAdapter"
}
case object DoubleType extends Type("Double") {
  override def default() = "0.0"
  override def parse() = "jp.valueAsDouble"
  override def parserObject() = "JsonAdapter.doubleAdapter"
}
case object BooleanType extends Type("Boolean") {
  override def default() = "false"
  override def parse() = "jp.valueAsBoolean"
  override def parserObject() = "JsonAdapter.booleanAdapter"
}
case object StringType extends Type("String") {
  override def default() = "\"\""
  override def parse() = "jp.getValueAsString(\"\")"
  override def parserObject() = "JsonAdapter.stringAdapter"
}
case class Field(name: String, ty: Type, jsonName: Option[String], isPrivate: Boolean = false) {

  val jn= jsonName.getOrElse(name)
  def parse() = s""""$jn" -> { $name = ${ty.parse()} }"""

  def declareTemp() = s"var $name: ${ty.name} = ${ty.default()}"

  def tempToField() = name // if (ty.isInstanceOf[NullableType]) name else name + "!!"

  def declare() = s"        ${(if (isPrivate) "private " else "@JvmField ")}val $name: ${ty.name}"

  def serialize() = ty match {
    case IntType | LongType | DoubleType => s"""jg.writeNumberField("$jn", t.$name)"""
    case StringType => s"""jg.writeStringField("$jn", t.$name)"""
    case BooleanType => s"""jg.writeBooleanField("$jn", t.$name)"""
    case NullableType(t) => s"""if (t.$name != null) { jg.writeFieldName("$jn"); ${t.parserObject()}.serialize(t.$name, jg, true) }"""
    case _ => s"""jg.writeFieldName("$jn"); ${ty.parserObject()}.serialize(t.$name, jg, true)"""
  }
}

def indent(in: String, statement: String) = {
  statement.split("\n").filter(!_.trim.isEmpty).map(in + _).mkString("\n")
}

case class ListType(t: Type) extends Type(s"List<${t.name}>") {
  override def default() = "emptyList()"
  override def parserObject() = t.parserObject() +".arrayAdapter()"
  override def parse() = parserObject() + ".parse(jp)"
}

case class JvmType(override val name: String, defaultStr: String) extends Type(name) {
  override def default() = defaultStr
  override def parserObject() = throw new Exception("Not allowed")
  override def parse() = throw new Exception("Not allowed")
}

case class HandmadeObjectType(override val name: String) extends Type(name) {
  override def default() = s"${name}.Companion.empty"
  override def parserObject() = name + ".Companion"
  override def parse() = parserObject() + ".parse(jp)"
}

case class ObjectType(override val name: String, fields: Seq[Field]) extends Type(name) {
  override def default() = s"${name}.Companion.empty"
  def codgen() =
    s"""
       |
       |data class $name(
       |${fields.map(_.declare()).mkString(",\n")})  : Serializable  {
       |  companion object : ObjectJsonAdapter<$name>() {
       |    override val empty: $name = $name(${fields.map(_.ty.default()).mkString(", ")})
       |    override fun parse(jp: JsonParser): $name {
       |      // Our code ensures all parse method will not throw error, if null encountered
       |      if (jp.currentToken != JsonToken.START_OBJECT) {
       |        jp.skipChildren()
       |        return empty
       |      }
       |${indent("      ", fields.map(_.declareTemp()).mkString("\n"))}
       |      while (jp.nextToken() != JsonToken.END_OBJECT) {
       |        val fieldName = jp.currentName
       |        jp.nextToken()
       |        when(fieldName) {
       |${indent("          ", fields.map(_.parse()).mkString("\n"))}
       |          else -> { logUnknownField(fieldName, ${name}.Companion) }
       |        }
       |        jp.skipChildren()
       |      }
       |      return $name(${fields.map(_.tempToField()).mkString(", ")})
       |    }
       |
       |    override fun serializeFields(t: $name, jg: JsonGenerator) {
       |${indent("      ", fields.map(_.serialize()).mkString("\n"))}
       |    }
       |  }
       |$ExtraCodeMark
       |}
     """.stripMargin

  override def parserObject() = name + ".Companion"

  override def parse() = parserObject() + ".parse(jp)"
}

case class NullableType(t: Type) extends Type(t.name + "?") {
  override def default() = "null"
  override def parserObject() = t.parserObject() +".nullAdapter()"
  override def parse() = parserObject() + ".parse(jp)"
}
case class EnumType(override val name: String, vs: Seq[String], defaultPos: Int) extends Type(name) {
  override def default() = name + "." + vs(defaultPos)
  def codgen() = s"""
  |enum class $name {
  |  ${vs.mkString(", ")};
  |  companion object : EnumJsonAdapter<$name>(arrayOf(${vs.map(a => name + "." + a).mkString(", ")}), $name.${vs(defaultPos)})
  |}
  """.stripMargin

  override def parserObject() = s"${name}.Companion"
  override def parse() = parserObject() + ".parse(jp)"
}

case class ConvertedType(json: Type, jvm: Type, converter: String) extends Type(jvm.name) {
  override def default() = jvm.default()

  override def parserObject() = converter

  override def parse() = parserObject() + ".parse(jp)"
}


val int = IntType
val long = LongType
val double = DoubleType
val boolean = BooleanType
val string = StringType

def list(t: Type) = ListType(t)


def f(name: String, ty: Type) = Field(name, ty, None)
def f(name: String,  json: String, ty: Type) = Field(name, ty, Some(json))


lazy val DefaultImports =
  s"""
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
import java.io.IOException
import java.text.SimpleDateFormat
""".stripMargin

class Spec(packageName: String, root: File, imports: Seq[String]) {


  lazy val dest = new File(root + "/" + packageName.replace('.', '/'))

  def writeClass(className: String, cont: String): Unit = {
    println("writing class " + className)
    var content = cont
    val des = new File(dest, className + ".kt")
    des.mkdirs()
    var imps = Vector.empty[String]
    if (des.isFile) {
      val lines = io.Source.fromFile(des).getLines().toSeq
      var extra = ""
      var started = false
      for (line <- lines) {
        if (line.startsWith("import ") && !imps.contains(line.trim)) {
          imps = imps :+ line
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
      for (line <- lines) {
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
    content = "package " + packageName + ";\n\n" + (DefaultImports + (imps ++ imports).mkString("\n")).split("\n").distinct.mkString("\n") + "\n" + content
    des.delete()
    des.createNewFile()
    val writer = new PrintWriter(des)
    writer.write(content)
    writer.close()
  }

  def writeFile(des: File, cont: String): Unit = {
    des.mkdirs()
    des.delete()
    des.createNewFile()
    val writer = new PrintWriter(des)
    writer.write(cont)
    writer.close()
  }


  val os = mutable.ArrayBuffer.empty[ObjectType]
  val es = mutable.ArrayBuffer.empty[EnumType]
  def o(s: String, fs: Field*) = {
    val o = ObjectType(s, fs)
    os.append(o)
    o
  }

  def e(s: String, de: Int, fs: String*) = {
    val o = EnumType(s, fs, de)
    es.append(o)
    o
  }

  def codegen(): Unit = {
    es.foreach(e => writeClass(e.name, e.codgen()))
    os.foreach(o => writeClass(o.name, o.codgen()))
  }
}


object BaseSpec extends Spec("org.snailya.kotlinparsergenerator.data", new File("app/src/main/java"), Seq.empty) {

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

