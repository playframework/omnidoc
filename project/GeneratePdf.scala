import play.twirl.api.Html
import sbt._
import sbt.Keys._
import play.doc._

object GeneratePdf {

  val generatePdf = taskKey[File]("Generate a PDF of the documentation")
  val pdfWorkDir = settingKey[File]("The working directory for PDF generation")

  def settings: Seq[Setting[_]] = Seq(
    pdfWorkDir := target.value / "pdf",
    generatePdf := doGeneratePdf((OmnidocBuild.playdoc in OmnidocBuild.Omnidoc).value, pdfWorkDir.value,
      target.value / "play.pdf", OmnidocBuild.playVersion)
  )

  private def doGeneratePdf(rootDir: File, outDir: File, outFile: File, playVersion: String): File = {

    println("Reading documentation from " + (rootDir / "manual"))

    IO.copyDirectory(rootDir, outDir, overwrite = true)

    val repo = new FilesystemRepository(rootDir / "manual")
    val pageIndex = PageIndex.parseFrom(repo, "Home")
    val playDoc = new PlayDoc(repo, repo, "manual", playVersion, pageIndex, "")

    val renderedPages = playDoc.renderAllPages(false)
    val htmlPages = renderedPages.map {
      case (page, rendered) =>
        // wkhtmltopdf uses the file extension to infer content type for files on the filesystem, so we need to generate
        // them with a .html extension, but this means we need to rewrite all the internal links in the docs to add a .html
        val linksFixed = rendered.replaceAll("<a href=\"([^/\"#]+)([\"#])", "<a href=\"$1.html$2")

        // api docs need to be linked to the online documentation
        val apiLinksFixed = linksFixed.replaceAll("<a href=\"api/([^\"]+)\"",
          "<a href=\"https://playframework.com/documentation/" + playVersion + "/api/$1\"")

        val pageHtml = _root_.html.pageTemplate(playVersion, Html(apiLinksFixed)).body
        val out = outDir / (page + ".html")

        IO.write(out, pageHtml)
        out.getAbsolutePath
    }

    // We wait 30 seconds for all JavaScript to finish executing - note that all pages are loaded at once, so this
    // doesn't apply to each page but in aggregate, so to syntax highlight the whole lot takes quite some time.
    println((Seq("wkhtmltopdf", "--enable-javascript", "--javascript-delay", "30000", "--images", "--enable-local-file-access") ++
      htmlPages :+ outFile.getAbsolutePath).!!)

    outFile
  }

}