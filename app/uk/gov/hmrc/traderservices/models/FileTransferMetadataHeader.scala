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

case class FileTransferMetadataHeader(
  caseReferenceNumber: String,
  applicationName: String,
  correlationId: String,
  conversationId: String,
  sourceFileName: String,
  sourceFileMimeType: String,
  checksum: String,
  batchSize: Int = 1,
  batchCount: Int = 1,
  fileSize: Int = 1024,
  checksumAlgorithm: String = "SHA-256",
  sourceSystem: String = "Digital",
  sourceSystemType: String = "AWS",
  interfaceName: String = "traderServices",
  interfaceVersion: String = "1.0",
  sourceLocation: String = "S3 Bucket",
  destinationSystem: String = "CDCM"
) {

  val properties: Map[String, String] = Map(
    "case_reference"   -> caseReferenceNumber,
    "application_name" -> applicationName
  )

  def toXmlString: String =
    s"""<?xml version="1.0" encoding="utf-8"?>
       |<mdg:BatchFileInterfaceMetadata 
       |xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
       |xmlns:mdg="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema" 
       |xsi:schemaLocation="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema BatchFileInterfaceMetadataXSD-1.0.7.xsd">
       |<mdg:sourceSystem>$sourceSystem</mdg:sourceSystem>
       |<mdg:sourceSystemType>$sourceSystemType</mdg:sourceSystemType>
       |<mdg:interfaceName>$interfaceName</mdg:interfaceName>
       |<mdg:interfaceVersion>$interfaceVersion</mdg:interfaceVersion>
       |<mdg:correlationID>$correlationId</mdg:correlationID>
       |<mdg:conversationID>$conversationId</mdg:conversationID>
       |<mdg:sequenceNumber>$batchCount</mdg:sequenceNumber>
       |<mdg:batchID>$conversationId</mdg:batchID>
       |<mdg:batchSize>$batchSize</mdg:batchSize>
       |<mdg:batchCount>$batchCount</mdg:batchCount>
       |<mdg:checksum>$checksum</mdg:checksum>
       |<mdg:checksumAlgorithm>$checksumAlgorithm</mdg:checksumAlgorithm>
       |<mdg:fileSize>$fileSize</mdg:fileSize>
       |<mdg:compressed>false</mdg:compressed>
       |<mdg:encrypted>false</mdg:encrypted>
       |<mdg:properties>
       |${properties.map {
      case (key, value) =>
        s"""<mdg:property>
       |<mdg:name>$key</mdg:name>
       |<mdg:value>$value</mdg:value>
       |</mdg:property>""".stripMargin
    }.mkString}
       |</mdg:properties>
       |<mdg:sourceLocation>$sourceLocation</mdg:sourceLocation>
       |<mdg:sourceFileName>${FileTransferMetadataHeader
      .refineFileName(sourceFileName, correlationId)}</mdg:sourceFileName>
       |<mdg:sourceFileMimeType>$sourceFileMimeType</mdg:sourceFileMimeType>
       |<mdg:destinations>
       |<mdg:destination>
       |<mdg:destinationSystem>$destinationSystem</mdg:destinationSystem>
       |</mdg:destination>
       |</mdg:destinations>
       |</mdg:BatchFileInterfaceMetadata>""".stripMargin.replaceAll("\n", "")

}

object FileTransferMetadataHeader {

  final def refineFileName(sourceFileName: String, correlationId: String) = {
    val asciiOnly = sourceFileName
      .replaceAll(s"[^\\p{ASCII}]", "?")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("'", "&apos;")
      .replaceAll("\"", "&quot;")
    val lastDot = asciiOnly.lastIndexOf(".")
    if (lastDot >= 0)
      asciiOnly.substring(0, lastDot) + "_" + correlationId + asciiOnly.substring(lastDot)
    else
      asciiOnly + "_" + correlationId
  }
}
