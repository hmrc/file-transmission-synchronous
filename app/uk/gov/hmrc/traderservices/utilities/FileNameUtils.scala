/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.utilities

object FileNameUtils {

  final def sanitize(maxLength: Int)(filename: String, suffix: String): String =
    insertSuffix(escapeAndTrimRight(maxLength - suffix.length() - 1)(filename), escape(suffix))

  final def escapeAndTrimRight(maxLength: Int)(filename: String): String = {
    val escapedFilename = escape(filename)
    if (escapedFilename.length() > maxLength) {
      val (name, extension) = {
        val i = filename.lastIndexOf(".")
        if (i >= 0 && i < filename.length()) (filename.substring(0, i), filename.substring(i))
        else (filename, "")
      }
      val nameFiltered = escape(name.filter(Character.isLetterOrDigit))
      val escapedExtension = escape(extension)
      (if (nameFiltered.nonEmpty) nameFiltered else escape(name))
        .take(maxLength - escapedExtension.length()) + escapedExtension
    } else
      escapedFilename
  }

  final def escape(filename: String): String =
    filename
      .replaceAll(s"[^\\p{ASCII}]", "?")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("'", "&apos;")
      .replaceAll("\"", "&quot;")

  final def insertSuffix(filename: String, suffix: String): String = {
    val lastDot = filename.lastIndexOf(".")
    if (lastDot >= 0) {
      val name = filename.substring(0, lastDot)
      val extension = filename.substring(lastDot)
      name + "_" + suffix + extension
    } else
      filename + "_" + suffix
  }
}
