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

case class Volume(shelfmark: Shelfmark, pages: Seq[(String, Page)]) {
  lazy val page = this.pages.toMap
}

case class Page(lines: Seq[(String, Line)]) {
  lazy val line = this.lines.toMap
}

case class Line(
  content: Seq[Span],
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
trait Container extends Span { def spans: Seq[Span] }
case class Unclear(spans: Seq[Span]) extends Container
case class Deleted(spans: Seq[Span]) extends Container

class Metadata {
  private[this] val LinePattern = "^(\\S+) (.*)$".r
  private[this] val workTitlesPath = "/edu/umd/mith/sga/mss/works.txt"
  private[this] val shelfmarksPath = "/edu/umd/mith/sga/mss/shelfmarks.txt"

  private def readAbbrevs(path: String): Map[String, String] = {
    val s = Source.fromInputStream(this.getClass.getResourceAsStream(path))
    val m = s.getLines.filterNot(_.startsWith("#")).collect {
      case LinePattern(abbrev, name) => abbrev -> name
    }.toMap
    s.close()
    m
  }

  val workTitles = this.readAbbrevs(this.workTitlesPath).map {
    case (abbrev, name) => abbrev -> WorkTitle(name, abbrev)
  } + ("S!" -> WorkTitle("S!", "Shorted Poems (other)"))

  val shelfmarks = this.readAbbrevs(this.shelfmarksPath).map {
    case (abbrev, name) if abbrev.startsWith("*") => abbrev.tail -> name
    case p => p
  }.map {
    case (abbrev, name) => abbrev -> Shelfmark(name, abbrev)
  }
}

