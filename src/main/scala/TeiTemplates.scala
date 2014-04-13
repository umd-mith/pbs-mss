package edu.umd.mith.sga.mss

import java.io.File
import scala.xml._

trait TeiTemplates {
  def cleanString(s: String): String = s
    .replaceAll("\u00A0", " ")
    .replaceAll("&", "&amp;")
    .replaceAll("--", "&#8212;")
    .replaceAll("<", "&lt;").replaceAll(">", "&gt;")

  def spanString(span: Span): String = span match {
    case Plain(text) => cleanString(text)
    case Unclear(spans) =>
      "<unclear>" + spans.flatMap(spanString).mkString + "</unclear>"
    case Deleted(spans) =>
      """<del rend="strikethrough">""" + spans.flatMap(spanString).mkString + "</del>"
  }

  def lineTemplate(line: Line) = 
    f"\n    <line>${ line.spans.map(spanString).mkString }%s</line>"

  /** We're using a string-based template because the native XML support for
    * processing instructions and custom indentation and line break rules in
    * Scala is shamefully bad.
    */
  def surfaceTemplate(
    library: String,
    shelfmark: String,
    seq: Int,
    folioLabel: String,
    shelfmarkLabel: String,
    size: Option[(Int, Int)],
    pages: Seq[Page]
  ) = {
    val id = f"$library%s-$shelfmark%s-$seq%04d"

    val corners = size.fold("") {
      case (w, h) => f"""\n  ulx="0" uly="0" lrx="$w%d" lry="$h%d""""
    }

    f"""<?xml version="1.0" encoding="ISO-8859-1"?><?xml-model href="../../../schemata/shelley-godwin-page.rnc"
  type="application/relax-ng-compact-syntax"?><?xml-stylesheet type="text/xsl"
  href="../../../xsl/page-proof.xsl"?>
<surface xmlns="http://www.tei-c.org/ns/1.0" xmlns:mith="http://mith.umd.edu/sc/ns1#"$corners%s
  xml:id="$id%s" partOf="#$library%s-$shelfmark%s"
  mith:shelfmark="$shelfmarkLabel%s" mith:folio="$folioLabel%s">
  <graphic url="http://shelleygodwinarchive.org/images/$library%s/$id%s.jp2"/>
  <zone type="main">${ pages.flatMap(_.lines.map(_._2)).map(lineTemplate).mkString }%s
  </zone>
</surface>
"""
  }

  def teiTemplate(
    library: String,
    shelfmark: String,
    shelfmarkLabel: String,
    files: List[File],
    works: List[(String, List[String])]
  ) = {
    val id = f"$library%s-$shelfmark%s"

    val fileIncludes = files.map { file =>
      val path = file.getPath.split(File.separator).takeRight(2).mkString(File.separator)
      <xi:include href={ path }/>
    }

    /** Here we need to combine consecutive identifiers into single locus
      * elements. The logic is messy but not that complicated.
      */
    val items = works.map {
      case (title, ids) =>
        val (loci, locus) = ids.foldLeft((List.empty[List[String]], List.empty[String])) {
          case ((loci, locus @ (last :: _)), id)
            if last.takeRight(4).toInt == id.takeRight(4).toInt - 1 =>
              (loci, id :: locus)
          case ((loci, locus), id) => ((loci :+ locus), List(id)) 
        }

        val allLoci = (loci :+ locus).filterNot(_.isEmpty).map { ids =>
          <locus target={ ids.reverse.map("#" + _).mkString(" ") }/>
        }

        <msItem>
          <bibl>
            <title>{ title }</title>
          </bibl>
          <locusGrp>{ allLoci }</locusGrp>
        </msItem>
    }

    <TEI xmlns="http://www.tei-c.org/ns/1.0"
         xmlns:xi="http://www.w3.org/2001/XInclude">
    <teiHeader>
        <fileDesc>
            <titleStmt>
                <title type="main">Frankenstein, Draft Notebook A</title>
                <title type="sub">An electronic transcription</title>
            </titleStmt>
            <editionStmt>
                <edition>Shelley-Godwin Archive edition, <date>2012-2014</date>
                </edition>
            </editionStmt>
            <publicationStmt>
                <distributor>Oxford University</distributor>
                <address>
                    <addrLine>
                        <ref target="http://www.shelleygodwinarchive.org/">http://www.shelleygodwinarchive.org/</ref>
                    </addrLine>
                </address>
                <availability status="free">
                    <licence target="http://creativecommons.org/publicdomain/zero/1.0/">
                        <p>CC0 1.0 Universal.</p>
                        <p> To the extent possible under law, the creators of the metadata records for the Shelley-Godwin Archive 
                            have waived all copyright and related or neighboring rights to this work.</p>
                    </licence>
                </availability>
                <pubPlace>Oxford, UK, and College Park, MD</pubPlace>
            </publicationStmt>
            <sourceDesc>
                <msDesc>
                    <msIdentifier>
                        <settlement>Oxford</settlement>
                        <repository>Bodleian Library, University of Oxford</repository>
                        <idno type="Bod">{ shelfmarkLabel }</idno>
                    </msIdentifier>
                    <msContents>
                      <msItem xml:id={ id }>
                        <bibl status="">
                          <author>Percy Shelley</author>
                        </bibl>
                        { items }
                      </msItem>
                    </msContents>
                    <physDesc>
                        <handDesc>
                            <handNote xml:id="pbs"><persName>Percy Shelley</persName></handNote>
                        </handDesc>
                    </physDesc>
                </msDesc>
            </sourceDesc>
        </fileDesc>
        <revisionDesc>
        </revisionDesc>
    </teiHeader>
    <sourceDoc>{ fileIncludes }
    </sourceDoc>
</TEI>
  }  
}

