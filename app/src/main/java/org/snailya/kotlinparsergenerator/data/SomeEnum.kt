package org.snailya.kotlinparsergenerator.data;


import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import android.util.Log
import android.net.Uri
import java.io.*
import java.util.*
import android.text.TextUtils
import org.snailya.kotlinparsergenerator.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import snailya.org.kotlinparsergenerator.EnumJsonAdapter

enum class SomeEnum {
  enum1, enum2;
  companion object : EnumJsonAdapter<SomeEnum>(arrayOf(SomeEnum.enum1, SomeEnum.enum2), SomeEnum.enum1)
}
  