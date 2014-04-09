package edu.umd.mith.sga.mss

import scala.io.Source
import scala.util.parsing.combinator._
import scalaz._, Scalaz._
import scalaz.concurrent.{ Future, Task }
import scalaz.stream._

object LineParser extends RegexParsers {
  override val skipWhitespace = false
  val plain = "[^\\[\\]<>]+".r ^^ (Plain(_))
  lazy val deleted: Parser[Deleted] = ("[" ~> text <~ "]") ^^ (t => Deleted(t.toList))
  lazy val unclear: Parser[Unclear] = ("<" ~> text <~ ">") ^^ (t => Unclear(t.toList))
  lazy val text: Parser[Seq[Span]] = rep(plain | deleted | unclear)
  def apply(s: String): Either[Span, List[Span]] = this.parseAll(text, s.trim) match {
    case Failure(_, _) => Left(Plain(s))
    case Success(result, _) => Right(result.toList)
  }
}

object CorpusReader {
  val LinePattern = "^(.+)/(.+):(.+):(.*):(.*):(.*):(.+):([VPM]):([HT])/$".r

  type LineInfo = (Shelfmark, PageNumber, LineNumber, Line)

  def parseLineParts(line: String, number: Int): Task[(String, Task[LineInfo])] = line match {
    case LinePattern(content, sm, fo, ln, pt, pn, wt, wc, hs) =>
      val lineInfo = Future {
        val shelfmark = Metadata.shelfmarks.get(sm).toSuccess(
          CorpusFormatError(f"Invalid shelfmark: $sm%s", number)
        )

        val title = Metadata.workTitles.get(wt).toSuccess(
          CorpusFormatError(f"Invalid work title: $wt%s", number)
        )

        val pageNumber = PageNumber(fo).toSuccess(
          CorpusFormatError(f"Invalid page number: $fo%s", number)
        )

        // We know we're safe since the regular expression has matched.
        val category = Category(wc).get
        val holograph = hs == "H"

        val result = (
          shelfmark.toValidationNel |@|
          title.toValidationNel |@|
          pageNumber.toValidationNel
        )((s, t, p) =>
          (s, p, LineNumber(ln), Line(LineParser(content), (pt, pn), t, category, holograph))
        )

        result.disjunction.leftMap(CorpusFormatErrors(_))
      }

      Task.now(sm -> new Task(lineInfo))
    case _ => Task.fail(CorpusFormatError(f"Invalid line: $line%s", number))
  }

  def lines = io.linesR("data/pbs-mss-corpus.txt")
    .zip(Process.iterate(1)(_ + 1))
    .filter { case (line, _) => line.trim.nonEmpty }
    .evalMap {
      case (line, number) => parseLineParts(line, number)
    }

  def intoPages: Process1[LineInfo, Map[Shelfmark, Volume]] =
    process1.foldMap {
      case (shelfmark, pageNumber, lineNumber, line) => Map(
        shelfmark -> Volume(Vector(pageNumber -> Page(Vector(lineNumber -> line))))
      )
    }

  def byShelfmark(abbrev: String): Task[Option[Volume]] =
    lines.filter(_._1 == abbrev)
      .evalMap(_._2)
      .pipe(intoPages)
      .runLast.map(_.flatMap(_.headOption).map(_._2))
}

