package edu.umd.mith.sga.mss.oxford

import edu.umd.mith.sga.mss._
import java.io.{ File, PrintWriter }
import org.apache.sanselan._
import scala.xml.PrettyPrinter
import scalaz._, Scalaz._

/** Provides a couple of constructors and serves as the driver for the Oxford
  * import process.
  */
object OxfordImporter extends OxfordCliParser with OxfordFormats {
  def main(args: Array[String]) {
    parser.parse(args)

    val importer = new OxfordImporter(
      getShelfmarkId,
      getShelfmarkAbbrev,
      getManifest,
      imageDir.value,
      getImageInformation
    )

    importer.createTei(new File("output"))
  }
}

/** The constructor here does a lot of work, which isn't ideal, but this is
  * essentially a glorified one-off script, and all of this data is such a
  * mess that it'll need a lot of special attention, anyway.
  */
class OxfordImporter(
  shelfmarkId: String,
  shelfmarkAbbrev: String,
  manifest: List[PageInfo],
  imageDir: Option[File],
  imageInformation: Option[Map[String, (Int, Int)]]
) extends TeiTemplates {
  import OxfordImporter._
  
  /** Save the TEI content into a top-level file and a number of transcription
    * files in the given directory using the given identifier.
    */
  def createTei(outputDir: File) {
    val libraryId = "ox"

    // Parse the data file and create the volume we need.
    val volume = CorpusReader.byShelfmark(shelfmarkAbbrev).run.getOrElse(
      throw new RuntimeException(f"Invalid shelfmark: $shelfmarkAbbrev%s.")
    )

    val sgaId = f"$libraryId%s-$shelfmarkId%s"
    val transcriptionDir = new File(outputDir, sgaId)
    if (!outputDir.exists) outputDir.mkdir()
    if (!transcriptionDir.exists) transcriptionDir.mkdir()

    val pages = manifest.zipWithIndex.collect {
      case (PageInfo(oxfordId, shelfmarkLabel, pageNumberInfo), n) =>
        val idSeq = oxfordId.takeRight(4).toInt

        if (idSeq != n + 1) throw new RuntimeException(
           f"Sequence numbers don't match: $idSeq%d and $n%d in $id%s."
        )
    
        val sgaPageId = f"$libraryId%s-$shelfmarkId%s-$idSeq%04d"

        val size = getImageSize(oxfordId)

        if (size.isEmpty) println(
          s"Warning: unable to determine image size for $oxfordId!"
        )

        val pageNumbers = pageNumberInfo.toOption.fold(List.empty[PageNumber])(
          _.pageNumbers
        )

        val pages = pageNumbers.flatMap(volume.page.get)

        val pageNumberLabel = pageNumberInfo.fold(identity, _.label)

        val content = surfaceTemplate(
          libraryId,
          shelfmarkId,
          idSeq,
          pageNumberLabel,
          shelfmarkLabel,
          size,
          pages
        )
        
        val file = new File(transcriptionDir, f"$sgaPageId%s.xml")
        val writer = new PrintWriter(file)

        writer.write(content)
        writer.close()

        pageNumbers -> (sgaPageId, shelfmarkLabel, file)
    }

    val pageMap = pages.flatMap {
      case (pageNumbers, fileInfo) => pageNumbers.map(_ -> fileInfo)
    }.toMap

    /** This looks convoluted, but essentially we're just creating a map from
      * titles to a list of page identifiers for pages that contain content
      * from each title.
      */
    val works: List[(String, List[String])] = volume.pages.map {
      case (pageNumber, page) =>
        page.lines.map(_._2.title).toSet.toVector.map((title: WorkTitle) =>
          title.name -> pageMap.get(pageNumber).fold(List.empty[String]) {
            case (sgaPageId, _, _) => List(sgaPageId)
          }
        ).toMap
      }.suml.toList.sortBy(-_._2.size)

    /** We assume that all pages will have the same shelfmark label (which
      * is reasonable, since we've selected our pages that way, but does rely
      * on Oxford and Tatsuo agreeing on shelfmarks.
      */
    val shelfmarkLabel = pages.head._2._2

    val tei = teiTemplate(libraryId, shelfmarkId, shelfmarkLabel, pages.map(_._2._3), works)

    /** We output the top-level file after manually printing the processing
      * instructions.
      */
    val prettyPrinter = new PrettyPrinter(4096, 2)
    val writer = new PrintWriter(new File(outputDir, f"$sgaId%s.xml"))
    writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
    writer.write("""<?xml-model href="../../schemata/shelley_godwin_odd.rng"""")
    writer.write("""  type="application/xml" schematypens="http://relaxng.org/ns/structure/1.0"?>""")
    writer.write(prettyPrinter.format(tei))
    writer.close()
  }

  def getImageSize(oxfordId: String): Option[(Int, Int)] =
    imageInformation.flatMap(_.get(oxfordId)).orElse(readImageSize(oxfordId))

  def readImageSize(oxfordId: String): Option[(Int, Int)] = imageDir.map { dir =>
    val image = Sanselan.getBufferedImage(new File(dir, oxfordId + ".tif"))

    (image.getWidth, image.getHeight)
  }
}

