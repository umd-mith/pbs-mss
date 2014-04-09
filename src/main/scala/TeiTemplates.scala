package edu.umd.mith.sga.mss

trait TeiTemplates {
  def clean(s: String): String = s

  def spanString(span: Span): String = span match {
    case Plain(text) => text
    case Unclear(spans) =>
      "<unclear>" + spans.map(spanString).mkString + "</unclear>"
    case Deleted(spans) =>
      """<del rend="strikethrough">""" + spans.map(spanString).mkString + "</del>"
  }

  def lineTemplate(line: Line) = 
    f"<line>${ line.spans.map(spanString).mkString }%s</line>\n"

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
    w: Int,
    h: Int,
    partsOf: List[String],
    lines: Seq[Line]
  ) = {
    val id = f"$library%s-$shelfmark%s-$seq%04d"

    f"""
<?xml version="1.0" encoding="ISO-8859-1"?><?xml-model href="../../../schemata/shelley-godwin-page.rnc"
  type="application/relax-ng-compact-syntax"?><?xml-stylesheet type="text/xsl"
  href="../../../xsl/page-proof.xsl"?>
<surface xmlns="http://www.tei-c.org/ns/1.0" xmlns:sga="http://shelleygodwinarchive.org/ns/1.0" ulx="0" uly="0"
  lrx="$w%d" lry="$h%d" xml:id="$id%s" partOf="${ partsOf.mkString(" ") }%s"
  sga:shelfmark="$shelfmarkLabel%s" sga:folio="$folioLabel%s"
  <graphic url="http://shelleygodwinarchive.org/images/$library%s/$id%s.jp2"/>
  <zone type="main">
    ${ lines.map(lineTemplate).mkString("\n") }%s
  </zone>
<surface>
"""
  }
}

