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

package uk.gov.hmrc.traderservices.utilities

import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.utilities.CommonUtils.LocalDateTimeUtils
class CommonUtilsSpec extends UnitSpec {
  "LocalDateTimeUtils" should {
    "parse date string correctly" in {
      "2020-11-03T15:29:28.601Z".toLocaDateTime.toString shouldBe "2020-11-03T15:29:28.601"
      "2020-01-12T00:00:00.000Z".toLocaDateTime.toString shouldBe "2020-01-12T00:00"
      "2020-02-29T15:29:28.601Z".toLocaDateTime.toString shouldBe "2020-02-29T15:29:28.601"
    }
  }
}
