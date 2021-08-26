package uk.gov.hmrc.traderservices.stubs

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

object MessageUtils {

  private val chunkSize: Int = 2400

  /**
    * Reads byte stream, wraps as Base64 and calculates SHA-256 checksum.
    *
    * @param io input stream
    * @return (source bytes, base64 encoded message, checkum, length)
    */
  final def read(io: InputStream): (Array[Byte], String, String, Int) = {
    val digest = MessageDigest.getInstance("SHA-256")
    val chunk = Array.ofDim[Byte](chunkSize)
    val encoder = Base64.getEncoder()
    var hasNext = true
    val rawBuffer = ByteBuffer.allocate(10 * 1024 * 1024)
    val encodedBuffer = ByteBuffer.allocate(14 * 1024 * 1024)
    var fileSize = 0
    while (hasNext) {
      for (i <- 0 until chunkSize) chunk.update(i, 0)
      val readLength = io.read(chunk)
      hasNext = readLength != -1
      if (readLength >= 0) {
        fileSize = fileSize + readLength
        val chunkBytes =
          if (readLength == chunk.length) chunk
          else chunk.take(readLength)
        digest.update(chunkBytes)
        val encoded = encoder.encode(chunkBytes)
        encodedBuffer.put(encoded)
        rawBuffer.put(chunkBytes)
      }
    }
    val bytes = Array.ofDim[Byte](rawBuffer.position())
    rawBuffer.clear()
    rawBuffer.get(bytes)
    val encoded = Array.ofDim[Byte](encodedBuffer.position())
    encodedBuffer.clear()
    encodedBuffer.get(encoded)
    io.close()
    val checksum = digest.digest()
    val contentBase64 = new String(encoded, StandardCharsets.UTF_8)
    (bytes, contentBase64, convertBytesToHex(checksum), bytes.length)
  }

  final def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes)
      sb.append(String.format("%02x", Byte.box(b)))
    sb.toString
  }

}
