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

package uk.gov.hmrc.traderservices.controllers

import play.api.libs.json.Reads
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import scala.concurrent.Future
import play.api.mvc.Result
import scala.concurrent.ExecutionContext
import play.api.mvc.Request
import uk.gov.hmrc.traderservices.models.Validator
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import play.api.libs.json.Json
import play.api.mvc.BodyParser
import akka.util.ByteString
import play.api.libs.streams.Accumulator
import akka.stream.scaladsl.Sink
import java.nio.charset.StandardCharsets

trait ControllerHelper {

  type HandleError = (String, String) => Future[Result]

  protected val parseTolerantTextUtf8: BodyParser[String] =
    BodyParser("parseTolerantTextUtf8") { request =>
      val decodeAsUtf8: Sink[ByteString, Future[Either[Result, String]]] =
        Sink
          .fold[Either[Result, String], ByteString](Right("")) {
            case (a, b) => a.map(_ + (b.decodeString(StandardCharsets.UTF_8)))
          }
      Accumulator(decodeAsUtf8)
    }

  protected def withPayload[T](
    f: T => Future[Result]
  )(
    handleError: HandleError
  )(implicit
    request: Request[String],
    reads: Reads[T],
    validate: Validator.Validate[T],
    ec: ExecutionContext
  ): Future[Result] =
    Try(Json.parse(request.body).validate[T]) match {

      case Success(JsSuccess(payload, _)) =>
        validate(payload) match {

          case Valid(a) =>
            f(payload)

          case Invalid(errs) =>
            handleError(
              "ERROR_VALIDATION",
              s"Invalid payload: Validation failed due to ${errs.mkString(", and ")}."
            )
        }

      case Success(JsError(errs)) =>
        handleError(
          "ERROR_JSON",
          s"Invalid payload: Parsing failed due to ${errs
            .map {
              case (path, errors) =>
                s"at path $path with ${errors.map(e => e.messages.mkString(", ")).mkString(", ")}"
            }
            .mkString(", and ")}."
        )

      case Failure(e) =>
        handleError("ERROR_UNKNOWN", s"Could not parse payload due to ${e.getMessage}.")
    }

}
