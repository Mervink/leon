/* Copyright 2009-2016 EPFL, Lausanne */

package leon.io

import leon.annotation._
import leon.lang.Option

// NOTE I couldn't use java.io.FileOutputStream as a field of FileOutputStream... Leon doesn't
//      accept it. Instead, the stream is opened and closed everytime an operation is
//      carried out. Not efficient but better than nothing. The C99 implementation doesn't
//      suffer from this issue.
//
// NOTE Don't attempt to create a FileOutputStream directly. Use FileOutputStream.open instead.
//
// NOTE Don't forget to close the stream.

@library
object FileOutputStream {

  /**
   * Open a new stream to write into `filename`, erasing any previous
   * content of the file or creating a new one if needed.
   */
  @extern
  @cCode.function(code =
    """
    |FILE* __FUNCTION__(char* filename) {
    |  FILE* this = fopen(filename, "w");
    |  // this == NULL on failure
    |  return this;
    |}
    """
  )
  def open(filename: String): FileOutputStream = {
    // FIXME Importing leon.lang.Option doesn't mean it is imported, why?
    new FileOutputStream(
      try {
        // Check whether the stream can be opened or not (and empty the file)
        val out = new java.io.FileWriter(filename, false)
        out.close()
        leon.lang.Some[String](filename)
      } catch {
        case _: Throwable => leon.lang.None[String]
      }
    )
  }

}

@library
@cCode.typedef(alias = "FILE*", include = "stdio.h")
case class FileOutputStream private (var filename: Option[String]) {

  /**
   * Close the stream; return `true` on success.
   *
   * NOTE The stream must not be used afterward, even on failure.
   */
  @cCode.function(code =
    """
    |bool __FUNCTION__(FILE* this) {
    |  if (this != NULL)
    |    return fclose(this) == 0;
    |  else
    |    return true;
    |}
    """
  )
  def close(): Boolean = {
    filename = leon.lang.None[String]
    true // This implementation never fails
  }

  /**
   * Test whether the stream is opened or not.
   *
   * NOTE This is a requirement for all write operations.
   */
  @cCode.function(code =
    """
    |bool __FUNCTION__(FILE* this) {
    |  return this != NULL;
    |}
    """
  )
  // We assume the stream to be opened if and only if the filename is defined.
  def isOpen(): Boolean = filename.isDefined

  /**
   * Append an integer to the stream and return `true` on success.
   *
   * NOTE The stream must be opened first.
   */
  @extern
  @cCode.function(code =
    """
    |bool __FUNCTION__(FILE* this, int32_t x) {
    |  return fprintf(this, "%"PRIi32, x) >= 0;
    |}
    """,
    includes = "inttypes.h"
  )
  def write(x: Int): Boolean = {
    require(isOpen)
    try {
      val out = new java.io.FileWriter(filename.get, true)
      out.append(x.toString)
      out.close()
      true
    } catch {
      case _: Throwable => false
    }
  }

  /**
   * Append a character to the stream and return `true` on success.
   *
   * NOTE The stream must be opened first.
   */
  @extern
  @cCode.function(code =
    """
    |bool __FUNCTION__(FILE* this, char c) {
    |  return fprintf(this, "%c", c) >= 0;
    |}
    """
  )
  def write(c: Char): Boolean = {
    require(isOpen)
    try {
      val out = new java.io.FileWriter(filename.get, true)
      out.append(c)
      out.close()
      true
    } catch {
      case _: Throwable => false
    }
  }

  /**
   * Append a string to the stream and return `true` on success.
   *
   * NOTE The stream must be opened first.
   */
  @extern
  @cCode.function(code =
    """
    |bool __FUNCTION__(FILE* this, char* s) {
    |  return fprintf(this, "%s", s) >= 0;
    |}
    """
  )
  def write(s: String): Boolean = {
    require(isOpen)
    try {
      val out = new java.io.FileWriter(filename.get, true)
      out.append(s)
      out.close()
      true
    } catch {
      case _: Throwable => false
    }
  }

}

