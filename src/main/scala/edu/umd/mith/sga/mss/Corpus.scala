/*
 * #%L
 * The Percy Bysshe Shelley Manuscript Corpus
 * %%
 * Copyright (C) 2011 - 2012 Maryland Institute for Technology in the Humanities
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package edu.umd.mith.sga.mss

import scala.io.Source
import scala.util.parsing.combinator._

object LineParser extends RegexParsers {
  override val skipWhitespace = false
  val plain = "[^\\[\\]<>]+".r ^^ (Plain(_))
  lazy val deleted: Parser[Deleted] = ("[" ~> text <~ "]") ^^ (Deleted(_))
  lazy val unclear: Parser[Unclear] = ("<" ~> text <~ ">") ^^ (Unclear(_))
  lazy val text: Parser[Seq[Span]] = rep(plain | deleted | unclear)
  def apply(s: String) = this.parseAll(text, s.trim) match {
    case Failure(_, _) => Left(Seq(Plain(s)))
    case Success(result, _) => Right(result)
  }
}

class CorpusReader(source: Source) {
  def this() = this(Source.fromFile("data/pbs-mss-corpus.txt"))
  val metadata = new Metadata 
  val LinePattern = "^(.+)/(.+):(.+):(.+):(.*):(.*):(.+):([VPM]):([HT])/$".r

  val categories = Map("V" -> Verse, "P" -> Prose, "M" -> Miscellaneous)

  val volumes: Seq[Volume] = {
    val lines: Seq[((Shelfmark, String, String), Line)] =
      this.source.getLines.filter(_.nonEmpty).map {
        case LinePattern(content, sm, pn, ln, pubt, pubn, wt, wc, ht) => (
          (this.metadata.shelfmarks(sm), pn, ln),
          Line(
            LineParser(content).fold(identity, identity), (pubt, pubn),
            this.metadata.workTitles(wt),
            this.categories(wc),
            ht == "H"
          )
        )
      }.toSeq

    val shelfmarks = lines.map(_._1._1).distinct
    val vs = lines.groupBy(_._1._1).mapValues { volLines =>
      val pages = volLines.map(_._1._2).distinct
      val ps = volLines.groupBy(_._1._2).mapValues { pageLines => Page(
        pageLines.map {
          case ((_, _, ln), line) => ln -> line
        }
      )}
      pages.map(pn => pn -> ps(pn))
    }
    shelfmarks.map(sm => Volume(sm, vs(sm)))
  }

  this.source.close()
}

object MalletConverter extends App {
  def flatten(s: Span, m: Int): Seq[(String, Int)] = s match {
    case Plain(text) => Seq(text -> m)
    case Deleted(spans) => spans.flatMap(flatten(_, 1 | m))
    case Unclear(spans) => spans.flatMap(flatten(_, 2 | m))
  }

  def spansString(ss: Seq[Span]) = ss.flatMap(this.flatten(_, 0).map {
    case (text, _) => text
    //case (text, 1) => text + "$d"
    //case (text, 2) => text + "$u"
    //case (text, 3) => text + "$ud"
  }).mkString(" ")

  val corpus = new CorpusReader
  val writer = new java.io.PrintWriter(args(0))

  corpus.volumes.foreach {
    case Volume(shelfmark, pages) => pages.zipWithIndex.foreach {
      case ((pn, Page(lines)), i) => writer.println("%s-%d _ %s".format(
        pn, i, this.spansString(lines.flatMap(_._2.content))
      ))
    }
  }

  writer.close()
}

