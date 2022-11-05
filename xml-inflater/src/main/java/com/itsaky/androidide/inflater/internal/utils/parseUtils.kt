/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.inflater.internal.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue.complexToDimension
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import androidx.core.text.isDigitsOnly
import com.android.aaptcompiler.AaptResourceType
import com.android.aaptcompiler.AaptResourceType.ARRAY
import com.android.aaptcompiler.AaptResourceType.ATTR
import com.android.aaptcompiler.AaptResourceType.BOOL
import com.android.aaptcompiler.AaptResourceType.DIMEN
import com.android.aaptcompiler.AaptResourceType.INTEGER
import com.android.aaptcompiler.AaptResourceType.STRING
import com.android.aaptcompiler.ArrayResource
import com.android.aaptcompiler.AttributeResource
import com.android.aaptcompiler.BasicString
import com.android.aaptcompiler.BinaryPrimitive
import com.android.aaptcompiler.ConfigDescription
import com.android.aaptcompiler.RawString
import com.android.aaptcompiler.Reference
import com.android.aaptcompiler.ResourceEntry
import com.android.aaptcompiler.ResourceName
import com.android.aaptcompiler.ResourceTable
import com.android.aaptcompiler.ResourceTablePackage
import com.android.aaptcompiler.StyledString
import com.android.aaptcompiler.Value
import com.android.aaptcompiler.android.ResValue.DataType.DIMENSION
import com.android.aaptcompiler.android.stringToFloat
import com.android.aaptcompiler.tryParseBool
import com.android.aaptcompiler.tryParseFlagSymbol
import com.android.aaptcompiler.tryParseInt
import com.android.aaptcompiler.tryParseReference
import com.itsaky.androidide.projects.api.AndroidModule
import com.itsaky.androidide.utils.ILogger
import java.util.regex.Pattern

private var currentModule: AndroidModule? = null
private val log = ILogger.newInstance("ParseUtilsKt")
private val HEX_COLOR: Pattern = Pattern.compile("#[a-fA-F\\d]{6,8}")

private val stringResolver =
  fun(it: Value?): String? {
    return when (it) {
      is BasicString -> it.ref.value()
      is RawString -> it.value.value()
      is StyledString -> it.ref.value()
      else -> null
    }
  }

private val intResolver =
  fun(it: Value?): Int? {
    return if (it is BinaryPrimitive) {
      it.resValue.data
    } else null
  }

inline fun <reified T> ((Value?) -> T?).arrayResolver(value: Value?): Array<T>? {
  return if (value is ArrayResource) {
    Array(value.elements.size) { invoke(value.elements[it]) ?: return null }
  } else emptyArray()
}

val module: AndroidModule
  get() =
    currentModule ?: throw IllegalStateException("You must call startParse(AndroidModule) first")

fun startParse(m: AndroidModule) {
  currentModule = m
}

fun endParse() {
  currentModule = null
}

fun parseString(value: String): String {
  if (value[0] == '@') {
    return parseReference(
      value = value,
      expectedType = STRING,
      def = value,
      resolver = stringResolver
    )
  }
  return value
}

fun parseStringArray(value: String, def: Array<String>? = emptyArray()): Array<String>? {
  return parseArray(value = value, def = def, resolver = stringResolver)
}

fun parseIntegerArray(value: String, def: IntArray? = intArrayOf()): IntArray? {
  return parseArray(value = value, def = def?.toTypedArray(), resolver = intResolver)?.toIntArray()
}

@JvmOverloads
inline fun <reified T> parseArray(
  value: String,
  def: Array<T>? = emptyArray(),
  noinline resolver: (Value?) -> T?
): Array<T>? {
  if (value[0] == '@') {
    return parseReference(
      value = value,
      expectedType = ARRAY,
      def = emptyArray(),
      resolver = resolver::arrayResolver
    )
  }
  return def
}

@JvmOverloads
fun parseInteger(value: String, def: Int = 0): Int {
  if (value.isDigitsOnly()) {
    tryParseInt(value)?.resValue?.apply {
      return data
    }
  }

  if (value[0] == '@') {
    return parseReference(value, INTEGER, def, intResolver)
  }

  return def
}

@JvmOverloads
fun parseBoolean(value: String, def: Boolean = false): Boolean {
  tryParseBool(value)?.resValue?.apply {
    return data == -1
  }

  if (value[0] == '@') {
    val resolver: (Value?) -> Boolean? =
      fun(resValue): Boolean {
        return if (resValue is BinaryPrimitive) {
          resValue.resValue.data == -1
        } else def
      }
    return parseReference(value, BOOL, def, resolver)
  }

  return def
}

@JvmOverloads
fun parseDrawable(context: Context, value: String, def: Drawable = unknownDrawable()): Drawable {
  if (HEX_COLOR.matcher(value).matches()) {
    return parseColorDrawable(context, value)
  }

  //    if (value[0] == '@') {
  //      val (pck, type, name) = parseResourceReference(value)
  //      return if(pck == null) {
  //        parseDrawableResRef(type, name, value)
  //      } else {
  //        parseQualifiedDrawableResRef()
  //      }
  //    }
  return def
}

@JvmOverloads
fun parseColorDrawable(context: Context, value: String, def: Int = Color.TRANSPARENT): Drawable {
  val color = parseColor(context, value, def)
  return newColorDrawable(color)
}

@JvmOverloads
fun parseColor(context: Context, value: String, def: Int = Color.TRANSPARENT): Int {
  val color =
    try {
      Color.parseColor(value)
    } catch (err: Throwable) {
      log.warn("Unable to parse color code", value)
      def
    }

  // TODO Parse other types of colors

  return color
}

fun unknownDrawable(): Drawable {
  return newColorDrawable(Color.TRANSPARENT)
}

fun newColorDrawable(color: Int): Drawable {
  return ColorDrawable(color)
}

@JvmOverloads
fun parseDimension(
  context: Context,
  value: String?,
  def: Float = LayoutParams.WRAP_CONTENT.toFloat(),
): Float {
  if (value.isNullOrBlank()) {
    return def
  }
  val displayMetrics = context.resources.displayMetrics
  val c = value[0]
  if (c.isDigit()) {
    val (dataType, data, _) = stringToFloat(value) ?: return def
    if (dataType != DIMENSION) {
      return def
    }

    return complexToDimension(data, displayMetrics)
  } else if (c == '@') {
    val resolver: (Value?) -> Float? = {
      if (it is BinaryPrimitive) {
        complexToDimension(it.resValue.data, displayMetrics)
      } else null
    }
    return parseReference(value, DIMEN, def, resolver)
  } else {
    return when (value) {
      "wrap_content" -> LayoutParams.WRAP_CONTENT.toFloat()
      "fill_parent",
      "match_parent", -> LayoutParams.MATCH_PARENT.toFloat()
      else -> {
        log.warn("Cannot infer type of dimension resource: '$value'")
        def
      }
    }
  }
}

fun parseFloat(value: String, defValue: Float): Float {
  return try {
    value.toFloat()
  } catch (err: Throwable) {
    defValue
  }
}

@JvmOverloads
fun parseGravity(value: String, def: Int = defaultGravity()): Int {
  val attr = findAttributeResource("android", ATTR, "gravity") ?: return defaultGravity()
  return parseFlag(attr = attr, value = value, def = defaultGravity())
}

fun parseFlag(attr: AttributeResource, value: String, def: Int = -1): Int {
  return tryParseFlagSymbol(attr, value)?.resValue?.data ?: def
}

fun defaultGravity(): Int {
  return Gravity.START or Gravity.TOP
}

fun <T> parseReference(
  value: String,
  expectedType: AaptResourceType,
  def: T,
  resolver: (Value?) -> T?
): T {
  val (pck, type, name) = parseResourceReference(value) ?: return def
  if (type != expectedType) {
    throwInvalidResType(expectedType, value)
  }
  return if (pck.isNullOrBlank()) {
    resolveUnqualifiedResourceReference(
      type = type,
      name = name,
      value = value,
      def = def,
      resolver = resolver
    )
  } else {
    resolveQualifiedResourceReference(
      pck = pck,
      type = type,
      name = name,
      def = def,
      resolver = resolver
    )
  }
}

fun <T> resolveUnqualifiedResourceReference(
  type: AaptResourceType,
  name: String,
  value: String?,
  def: T,
  resolver: (Value?) -> T?
): T {
  val (table, _, pack, entry) = lookupUnqualifedResource(type, name, value) ?: return def
  return resolveResourceReference(
    table = table,
    pck = pack,
    entry = entry,
    type = type,
    name = name,
    def = def,
    resolver = resolver
  )
}

fun <T> resolveQualifiedResourceReference(
  pck: String,
  type: AaptResourceType,
  name: String,
  def: T,
  resolver: (Value?) -> T?
): T {
  val table =
    module.findResourceTableForPackage(pck, type)
      ?: throw IllegalArgumentException("Resource table for package '$pck' not found.")

  return resolveResourceReference(
    table = table,
    type = type,
    pck = pck,
    name = name,
    def = def,
    resolver = resolver
  )
}

fun <T> resolveResourceReference(
  table: ResourceTable,
  type: AaptResourceType,
  pck: String,
  name: String,
  def: T,
  resolver: (Value?) -> T?
): T {
  val result =
    table.findResource(ResourceName(pck = pck, type = type, entry = name))
      ?: run { throw IllegalArgumentException("$type resource '$name' not found") }
  return resolveResourceReference(
    table,
    result.tablePackage,
    result.entry,
    type,
    name,
    def,
    resolver
  )
}

fun <T> resolveResourceReference(
  table: ResourceTable,
  pck: ResourceTablePackage,
  entry: ResourceEntry,
  type: AaptResourceType,
  name: String,
  def: T,
  resolver: (Value?) -> T?
): T {
  val dimenValue = entry.findValue(ConfigDescription())!!.value
  if (dimenValue is Reference) {
    return resolveResourceReference(
      table = table,
      type = type,
      pck = pck.name,
      name = dimenValue.name.entry!!,
      def = def,
      resolver = resolver
    )
  }

  return resolver(dimenValue)
    ?: run {
      log.warn("Unable to resolve dimension reference '$name'")
      def
    }
}

private fun parseResourceReference(value: String): Triple<String?, AaptResourceType, String>? {
  return tryParseReference(value)?.let {
    val pck = it.reference.name.pck
    val type = it.reference.name.type
    val name = it.reference.name.entry!!

    if (type == ATTR) {
      // this an attribute reference
      // TODO(itsaky): When the UI designer allows the user to choose a theme for the UI designer,
      //   This should resolve the referred attribute and then return its pck, type and name
      return null
    }
    return Triple(pck, type, name)
  }
}

private fun throwInvalidResType(type: AaptResourceType, value: String) {
  throw IllegalArgumentException(
    "Value must be a reference to a ${type.tagName} resource type. '$value'"
  )
}
