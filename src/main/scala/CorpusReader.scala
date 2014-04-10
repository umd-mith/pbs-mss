package edu.umd.mith.sga.mss

import scala.util.parsing.combinator._
import scalaz._, Scalaz._
import scalaz.concurrent.{ Future, Task }
import scalaz.stream._

/** Parsing logic for marked-up line text.
  */
object LineParser extends RegexParsers {
  override val skipWhitespace = false
  val plain = "[^\\[\\]<>]+".r ^^ (Plain(_))
  lazy val deleted: Parser[Deleted] = ("[" ~> text <~ "]") ^^ (t => Deleted(t.toList))
  lazy val unclear: Parser[Unclear] = ("""<\??""".r ~> text <~ ">") ^^ (t => Unclear(t.toList))
  lazy val text: Parser[Seq[Span]] = rep(plain | deleted | unclear)

  /** There are many "ill-formed" lines in the corpus, and we generally just
    * want to pass these through, since a human will be reviewing in any case.
    * If we are unable to parse a line, we pass it through as a single chunk
    * on the left side, while successful parses are returned with their full
    * structure on the right side.
    */
  def apply(s: String): Either[Span, List[Span]] = parseAll(text, s.trim) match {
    case Failure(_, _) => Left(Plain(s))
    case Success(result, _) => Right(result.toList)
  }
}

/** Provides access to the data file in the form of Scalaz stream processes.
  */
object CorpusReader {
  val LinePattern = "^(.+)/(.+):(.+):(.*):(.*):(.*):(.+):([VPM]):([HT])/$".r

  type LineInfo = (Shelfmark, PageNumber, LineNumber, Line)

  /** We generally want to filter the lines by shelfmark abbreviation, and this
    * method gives us access to the abbreviation before any further processing
    * happens.
    */
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

  /** Provides parsed line actions.
    */
  def lines = io.linesR("data/pbs-mss-corpus.txt")
    .zip(Process.iterate(1)(_ + 1))
    .filter { case (line, _) => line.trim.nonEmpty }
    .evalMap {
      case (line, number) => parseLineParts(line, number)
    }

  /** After filtering by shelfmark abbreviation, we often want to group lines
    * into volumes and pages, which we can accomplish pretty straightforwardly
    * through the monoid instances for these types.
    */
  def intoPages: Process1[LineInfo, Map[Shelfmark, Volume]] =
    process1.foldMap {
      case (shelfmark, pageNumber, lineNumber, line) => Map(
        shelfmark -> Volume(Vector(pageNumber -> Page(Vector(lineNumber -> line))))
      )
    }

  /** In the simplest and most common case, we just want a single volume that
    * we've identified by its shelfmark abbreviation.
    */
  def byShelfmark(abbrev: String): Task[Option[Volume]] =
    lines.filter(_._1 == abbrev)
      .evalMap(_._2)
      .pipe(intoPages)
      .runLast.map(_.flatMap(_.headOption).map(_._2))
}

