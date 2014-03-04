package edu.umd.mith.sga.mss

import scala.io.Source
import scalaz.{ Order, Validation }, scalaz.syntax.validation._

case class CorpusFormatError(message: String, lineNumber: Int) extends Exception(
  f"At line $lineNumber%d: $message%s"
)

case class Volume(shelfmark: Shelfmark, pages: List[(String, Page)]) {
  lazy val page = pages.toMap
}

case class Page(lines: List[(String, Line)]) {
  lazy val line = lines.toMap
}

sealed trait LineNumber

case class ErrorLineNumber(content: String) extends LineNumber
case class ValidLineNumber(number: Int, pre: Option[Char], post: Option[String], stars: Int) extends LineNumber

object LineNumber {
  implicit val ordering: Order[LineNumber] = Order[String].contramap {
    case ErrorLineNumber(content) => content
    case ValidLineNumber(number, pre, post, stars) =>
      val starField = "*" * stars
      val preField = pre.getOrElse(' ')
      val postField = post.getOrElse("")
      f"$starField%-3s$preField%s$number%06d$postField%s"
  }

  private[this] val LineNumberPattern = """^(\*{0,3})([RVMABF]?)(\d{1,4})([a-z]?[i]?)$""".r

  def parse(s: String): LineNumber = s match {
    case LineNumberPattern(starPart, prePart, numberPart, postPart) => ValidLineNumber(
      numberPart.toInt,
      prePart.headOption,
      if (postPart.isEmpty) None else Some(postPart),
      starPart.length
    )
    case _ => ErrorLineNumber(s)
  }
}



case class Line(
  content: List[Span],
  publication: (String, String),
  workTitle: WorkTitle,
  category: Category,
  holograph: Boolean
)  

trait Abbreviated {
  def name: String
  def abbrev: String
}

case class Shelfmark(name: String, abbrev: String) extends Abbreviated
case class WorkTitle(name: String, abbrev: String) extends Abbreviated

sealed trait Category
case object Verse extends Category
case object Prose extends Category
case object Miscellaneous extends Category

sealed trait Span
case class Plain(text: String) extends Span
trait Container extends Span { def spans: List[Span] }
case class Unclear(spans: List[Span]) extends Container
case class Deleted(spans: List[Span]) extends Container

object Metadata {
  private[this] val LinePattern = "^(\\S+) (.*)$".r
  private[this] val workTitlesPath = "/edu/umd/mith/sga/mss/works.txt"
  private[this] val shelfmarksPath = "/edu/umd/mith/sga/mss/shelfmarks.txt"

  private def readAbbrevs(path: String): Map[String, String] = {
    val s = Source.fromInputStream(getClass.getResourceAsStream(path))
    val m = s.getLines.filterNot(_.startsWith("#")).collect {
      case LinePattern(abbrev, name) => abbrev -> name
    }.toMap
    s.close()
    m
  }

  val workTitles: Map[String, WorkTitle] = readAbbrevs(workTitlesPath).map {
    case (abbrev, name) => abbrev -> WorkTitle(name, abbrev)
  }

  val shelfmarks: Map[String, Shelfmark] = readAbbrevs(shelfmarksPath).map {
    case (abbrev, name) if abbrev.startsWith("*") => abbrev.tail -> name
    case p => p
  }.map {
    case (abbrev, name) => abbrev -> Shelfmark(name, abbrev)
  }
}

