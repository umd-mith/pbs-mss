package edu.umd.mith.sga.mss

import scala.io.Source
import scala.util.parsing.combinator._
import scalaz._, Scalaz._
import scalaz.stream._

object LineParser extends RegexParsers {
  override val skipWhitespace = false
  val plain = "[^\\[\\]<>]+".r ^^ (Plain(_))
  lazy val deleted: Parser[Deleted] = ("[" ~> text <~ "]") ^^ (t => Deleted(t.toList))
  lazy val unclear: Parser[Unclear] = ("<" ~> text <~ ">") ^^ (t => Unclear(t.toList))
  lazy val text: Parser[Seq[Span]] = rep(plain | deleted | unclear)
  def apply(s: String) = this.parseAll(text, s.trim) match {
    case Failure(_, _) => Left(Seq(Plain(s)))
    case Success(result, _) => Right(result)
  }
}

class CorpusReader(source: Source) {
  type LineParts = (String, String, String, LineNumber, String, String, String, String, String)
  val LinePattern = "^(.+)/(.+):(.+):(.*):(.*):(.*):(.+):([VPM]):([HT])/$".r

  def parseLineParts(line: String, number: Int): Validation[Throwable, LineParts] = line match {
    case LinePattern(
      content,
      shelfmark,
      foliation,
      lineNumber,
      publicationTitle,
      publicationNumber,
      workTitle,
      workCategory,
      holographStatus
    ) => 
      (
        content,
        shelfmark,
        foliation,
        LineNumber.parse(lineNumber),
        publicationTitle,
        publicationNumber,
        workTitle,
        workCategory,
        holographStatus
      ).success
    case _ => CorpusFormatError(f"Invalid line: $line%s", number).failure
  }

  def lines = io.linesR("data/pbs-mss-corpus.txt")
    .zip(Process.iterate(1)(_ + 1))
    .filter { case (line, _) => line.trim.nonEmpty }
    .map((parseLineParts _).tupled)

  def this() = this(Source.fromFile("data/pbs-mss-corpus.txt"))

  val categories = Map("V" -> Verse, "P" -> Prose, "M" -> Miscellaneous)

  /*val volumes: Seq[Volume] = source.getLines.filter(_.nonEmpty).map {
    case LinePattern(
      content,
      shelfmark,
      foliation,
      lineNumber,
      publicationTitle,
      publicationNumber,
      workTitle,
      workCategory,
      holographStatus) => 
      (Metadata.shelfmarks(sm), pn, ln) -> Line(
        LineParser(content).fold(identity, identity),
        (pubt, pubn),
        Metadata.workTitles(wt),
        categories(wc),
        ht == "H"
      )
  }.groupPartsBy {
    case ((sm, pn, ln), line) => sm -> ((pn, ln), line)
  }.map {
    case (sm, volLines) => Volume(sm, volLines.groupPartsBy {
      case ((pn, ln), line) => pn -> (ln, line)
    }.map {
      case (pn, pageLines) => pn -> Page(pageLines)
    })
  }

  source.close()*/
}

/*object MalletConverter extends App {
  def flatten(s: Span, m: Int): Seq[(String, Int)] = s match {
    case Plain(text) => Seq(text -> m)
    case Deleted(spans) => spans.flatMap(flatten(_, 1 | m))
    case Unclear(spans) => spans.flatMap(flatten(_, 2 | m))
  }

  def spansString(ss: Seq[Span]) = ss.flatMap(flatten(_, 0).map {
    case (text, _) => text
    //case (text, 1) => text + "$d"
    //case (text, 2) => text + "$u"
    //case (text, 3) => text + "$ud"
  }).mkString(" ")

  val corpus = new CorpusReader
  val writer = new java.io.PrintWriter(args(0))

  corpus.volumes.foreach {
    case Volume(shelfmark, pages) => pages.zipWithIndex.foreach {
      case ((pn, Page(lines)), i) => writer.println("%s-%04d _ %s".format(
        shelfmark.abbrev, i + 1, spansString(lines.flatMap(_._2.content))
      ))
    }
  }

  writer.close()
}*/

