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

package uk.gov.hmrc.traderservices.connectors

import play.api.libs.json.Reads
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.JsValidationException
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.HttpReads.Implicits._
import play.mvc.Http.HeaderNames
import play.mvc.Http.MimeTypes
import scala.util.Try

abstract class ReadSuccessOrFailure[A, S <: A: Reads, F <: A: Reads](fallback: (Int, String) => A)(implicit
  mf: Manifest[A]
) {

  implicit val readFromJsonSuccessOrFailure: HttpReads[A] =
    HttpReads[HttpResponse]
      .flatMap { response =>
        val status = response.status
        if (response.body.isEmpty())
          HttpReads.pure(fallback(status, "Error: empty response"))
        else
          response.header(HeaderNames.CONTENT_TYPE) match {
            case None =>
              HttpReads.pure(fallback(status, "Error: missing content-type header"))

            case Some(MimeTypes.JSON) =>
              if (status >= 200 && status < 300)
                Try[HttpReads[A]](implicitly[Reads[S]].reads(response.json) match {
                  case JsSuccess(value, path) => HttpReads.pure(value)
                  case JsError(errors) =>
                    HttpReads.ask.flatMap {
                      case (method, url, response) =>
                        val e = new JsValidationException(method, url, mf.runtimeClass, errors.toString)
                        HttpReads.pure(fallback(status, e.getMessage()))
                    }
                })
                  .fold(e => HttpReads.pure(fallback(status, e.getMessage())), identity)
              else if (status >= 400)
                Try[HttpReads[A]](implicitly[Reads[F]].reads(response.json) match {
                  case JsSuccess(value, path) => HttpReads.pure(value)
                  case JsError(errors) =>
                    HttpReads.ask.flatMap {
                      case (method, url, response) =>
                        val e = new JsValidationException(method, url, mf.runtimeClass, errors.toString)
                        HttpReads.pure(fallback(status, e.getMessage()))
                    }
                })
                  .fold(e => HttpReads.pure(fallback(status, e.getMessage())), identity)
              else
                throw UpstreamErrorResponse(s"Unexpected response status $status", 500)

            case other =>
              throw UpstreamErrorResponse(
                s"Unexpected response type of status $status, expected application/json but got ${other
                  .getOrElse("none")} with body:\n${response.body}",
                500
              )
          }
      }
}
