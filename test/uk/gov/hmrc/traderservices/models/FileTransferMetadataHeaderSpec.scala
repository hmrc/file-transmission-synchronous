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

package uk.gov.hmrc.traderservices.services

import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.models.FileTransferMetadataHeader

class FileTransferMetadataHeaderSpec extends UnitSpec {

  "FileTransferMetadataHeader" should {
    "refine the file name" in {
      FileTransferMetadataHeader.refineFileName("", "") shouldBe "_"
      FileTransferMetadataHeader.refineFileName("foo", "123") shouldBe "foo_123"
      FileTransferMetadataHeader.refineFileName("f.b", "123") shouldBe "f_123.b"
      FileTransferMetadataHeader.refineFileName("foo.bar", "123") shouldBe "foo_123.bar"
      FileTransferMetadataHeader.refineFileName("foo.", "123") shouldBe "foo_123."
      FileTransferMetadataHeader.refineFileName("foo..", "123") shouldBe "foo._123."
      FileTransferMetadataHeader.refineFileName("foo.bar.baz", "123") shouldBe "foo.bar_123.baz"
      FileTransferMetadataHeader.refineFileName("foo..baz", "123") shouldBe "foo._123.baz"
      FileTransferMetadataHeader.refineFileName(".bar", "123") shouldBe "_123.bar"
      FileTransferMetadataHeader.refineFileName("foo.bar", "") shouldBe "foo_.bar"
      FileTransferMetadataHeader.refineFileName("foo&b&ar", "") shouldBe "foo&amp;b&amp;ar_"
      FileTransferMetadataHeader.refineFileName("foo<bar<", "") shouldBe "foo&lt;bar&lt;_"
      FileTransferMetadataHeader.refineFileName("foo>>bar", "") shouldBe "foo&gt;&gt;bar_"
      FileTransferMetadataHeader.refineFileName("foo>>bar", "") shouldBe "foo&gt;&gt;bar_"
      FileTransferMetadataHeader.refineFileName("foo&'bar", "") shouldBe "foo&amp;&apos;bar_"
      FileTransferMetadataHeader.refineFileName(">foo\"&bar", "") shouldBe "&gt;foo&quot;&amp;bar_"
    }
  }

}
