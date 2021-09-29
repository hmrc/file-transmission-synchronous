/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.models

import uk.gov.hmrc.traderservices.utilities.FileNameUtils
import uk.gov.hmrc.traderservices.support.UnitSpec

class FileNameUtilsSpec extends UnitSpec {

  "FileNameUtils" should {

    "sanitize the file name" in {
      val name = "a" * 72 + ".abc"
      FileNameUtils.sanitize(20)(name, "foobar") shouldBe "aaaaaaaaa_foobar.abc"
      FileNameUtils.sanitize(15)(name, "foo") shouldBe "aaaaaaa_foo.abc"
      FileNameUtils.sanitize(10)(name, "foo") shouldBe "aa_foo.abc"
      FileNameUtils.sanitize(5)(name, "foo") shouldBe "_foo.abc"
      FileNameUtils.sanitize(20)("aaaaa<>bbbbb.jpeg", "foo123") shouldBe "aaaaabbb_foo123.jpeg"
      FileNameUtils.sanitize(20)("aaaaa<>.jpeg", "foo123") shouldBe "aaaaa_foo123.jpeg"
      FileNameUtils.sanitize(20)("<a>.<b>", "foo123") shouldBe "a_foo123.&lt;b&gt;"
      FileNameUtils.sanitize(20)("<a>.b", "foo123") shouldBe "&lt;a&gt;_foo123.b"
      FileNameUtils.sanitize(20)("\"a\".b", "foo123") shouldBe "a_foo123.b"
      FileNameUtils.sanitize(30)("\"a\".b", "foo12") shouldBe "&quot;a&quot;_foo12.b"
      FileNameUtils.sanitize(30)("\"ó\".b", "fooó") shouldBe "&quot;?&quot;_foo?.b"
      FileNameUtils.sanitize(30)("\"ó\".ó", "fooó") shouldBe "&quot;?&quot;_foo?.?"
      FileNameUtils.sanitize(30)("+++.+", "foo+") shouldBe "+++_foo+.+"
      FileNameUtils.sanitize(30)("+a++.+", "foo+") shouldBe "+a++_foo+.+"
      FileNameUtils.sanitize(10)("+a++.*", "foo-") shouldBe "a_foo-.*"
    }

    "trim right preserving the suffix" in {
      val MAX = 93
      val escapeAndTrimRight = FileNameUtils.escapeAndTrimRight(MAX) _

      escapeAndTrimRight("") shouldBe ""
      escapeAndTrimRight("a") shouldBe "a"
      escapeAndTrimRight("a.a") shouldBe "a.a"
      escapeAndTrimRight("a" * MAX) shouldBe "a" * MAX
      escapeAndTrimRight("a" * (MAX + 1)) shouldBe "a" * MAX
      escapeAndTrimRight("a" * (MAX - 5) + ".ext") shouldBe "a" * (MAX - 5) + ".ext"
      escapeAndTrimRight("a" * (MAX - 4) + ".ext") shouldBe "a" * (MAX - 4) + ".ext"
      escapeAndTrimRight("a" * (MAX + 1) + ".ext") shouldBe "a" * (MAX - 4) + ".ext"
      escapeAndTrimRight("a" * (MAX - 2) + ".") shouldBe "a" * (MAX - 2) + "."
      escapeAndTrimRight("a" * (MAX - 1) + ".") shouldBe "a" * (MAX - 1) + "."
      escapeAndTrimRight("a" * MAX + ".") shouldBe "a" * (MAX - 1) + "."
      escapeAndTrimRight("-" * MAX) shouldBe "-" * MAX
      escapeAndTrimRight("-" * (MAX - 5) + ".ext") shouldBe "-" * (MAX - 5) + ".ext"
      escapeAndTrimRight("-" * (MAX - 4) + ".ext") shouldBe "-" * (MAX - 4) + ".ext"
      escapeAndTrimRight("-" * (MAX + 1) + ".ext") shouldBe "-" * (MAX - 4) + ".ext"

      escapeAndTrimRight(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus tempor egestas viverra usce."
      ) shouldBe "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus tempor egestas viverra usce."
      escapeAndTrimRight(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum cursus, erat sed fringilla lacinia, sem nulla vulputate mauris, at tincidunt eros.ext"
      ) shouldBe "LoremipsumdolorsitametconsecteturadipiscingelitVestibulumcursuseratsedfringillalaciniasem.ext"
      escapeAndTrimRight(
        "123orem_ipsum_dolor_sit_amet-----consec9999999999tetur-adipiscing elit_Vestibulum***12cursus,!!![erat]+sed+fringilla (lacinia), sem/nulla/vulputate /_mauris,~at&tincidunt@eros.ext"
      ) shouldBe "123oremipsumdolorsitametconsec9999999999teturadipiscingelitVestibulum12cursuseratsedfring.ext"

    }

    "escape xml-syntax characters" in {
      FileNameUtils.escape("foo&b&ar") shouldBe "foo&amp;b&amp;ar"
      FileNameUtils.escape("foo<bar<") shouldBe "foo&lt;bar&lt;"
      FileNameUtils.escape("foo>>bar") shouldBe "foo&gt;&gt;bar"
      FileNameUtils.escape("foo&'bar") shouldBe "foo&amp;&apos;bar"
      FileNameUtils.escape(">foo\"&bar") shouldBe "&gt;foo&quot;&amp;bar"
    }

    "escape non-ASCII characters" in {
      FileNameUtils.escape("") shouldBe ""
      FileNameUtils.escape("foobar") shouldBe "foobar"
      FileNameUtils.escape("fooó") shouldBe "foo?"
      FileNameUtils.escape("fooółŻbarŁ") shouldBe "foo???bar?"
      FileNameUtils.escape("ółź>ŚĆŃ") shouldBe "???&gt;???"
    }

    "insert suffix before the extension" in {
      FileNameUtils.insertSuffix("", "") shouldBe "_"
      FileNameUtils.insertSuffix("foo", "123") shouldBe "foo_123"
      FileNameUtils.insertSuffix("f.b", "123") shouldBe "f_123.b"
      FileNameUtils.insertSuffix("foo.bar", "123") shouldBe "foo_123.bar"
      FileNameUtils.insertSuffix("foo.", "123") shouldBe "foo_123."
      FileNameUtils.insertSuffix("foo..", "123") shouldBe "foo._123."
      FileNameUtils.insertSuffix("foo.bar.baz", "123") shouldBe "foo.bar_123.baz"
      FileNameUtils.insertSuffix("foo..baz", "123") shouldBe "foo._123.baz"
      FileNameUtils.insertSuffix(".bar", "123") shouldBe "_123.bar"
      FileNameUtils.insertSuffix("foo.bar", "") shouldBe "foo_.bar"
    }
  }
}
