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

enum class SomeEnum {
  enum1, enum2;
  companion object : EnumJsonAdapter<SomeEnum>(arrayOf(SomeEnum.enum1, SomeEnum.enum2), SomeEnum.enum1)
}
  