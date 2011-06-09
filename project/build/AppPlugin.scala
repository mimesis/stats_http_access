import sbt._
import java.io.{File, FileInputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.mutable.HashSet

abstract class AppProject(info: ProjectInfo) extends DefaultProject(info) with AppTask {

  lazy val zipName = defaultAssemblyZipName
  lazy val zipFile:Path = defaultAssemblyZipFile
  lazy val zipArtifact = Artifact(baseName + "-app", "zip", "zip")

  lazy val showZipname = task{
    log.warn(zipName + "  ==>  " + zipArtifact.name)
    None
  }

}

abstract class AppOnlyProject(info: ProjectInfo) extends AppProject(info) {
  override def artifacts = super.artifacts + zipArtifact
}


trait AppParameters {
  self : DefaultProject =>

  def baseName = normalizedName // artifactID
  def defaultAssemblyZipName = baseName + "-app-" + version.toString + ".zip"
  def defaultAssemblyZipFile:Path = outputPath / defaultAssemblyZipName
}

trait AppTask extends AppParameters {
  self : DefaultProject =>

  type TranfoPath = (Path) => String

  // Don't put lazy val here
  protected def addScalaLibIfNotEmpty( x : PathFinder) : PathFinder = if (x.get.isEmpty) x else (x +++ Path.fromFile(buildScalaInstance.libraryJar))
  def appZipFiles = descendents((mainSourcePath / "app")##, "*")
  def publicClasspathJars = descendents(publicClasspath, "*.jar")
  def libZipFiles : PathFinder = addScalaLibIfNotEmpty(Path.lazyPathFinder(publicClasspath.get.filter(ClasspathUtilities.isArchive)) +++ jarsOfProjectDependencies)



  def pathInZipSame() : TranfoPath = { (p : Path) => p.relativePathString("/")}
  def pathInZipInSubdir(d : String) : TranfoPath = { (p : Path) => d + "/" + p.relativePathString("/") }
  def pathInZipFlatInSubdir(d : String) : TranfoPath = { (p : Path) => d + "/" + p.name }
  def pathInZipFlatInSubst(subst : (String,String)*) : TranfoPath = { (p : Path) => subst.foldLeft(p.name){(acc, v) => acc.replaceFirst(v._1, v._2)} }

  def assemblyAction(zipFile : Path, appZipFiles: PathFinder, libZipFiles: PathFinder) : Task = assemblyAction(zipFile, List((appZipFiles, pathInZipSame()), (libZipFiles, pathInZipFlatInSubdir("lib"))), None)

  def assemblyAction(zipFile : Path, filesLocs : => List[(PathFinder, TranfoPath)], pathPrefix : Option[String]) : Task = task {
    (fileTask("zip", zipFile from (filesLocs.map(_._1).reduceLeft(_ +++ _))) {
      log.info("Packaging " + zipFile + " ...")
      val outputFile = zipFile.asFile
      val result = FileUtilities.createDirectory(outputFile.getParentFile, log) orElse
        withZipOutput(outputFile, log) { output =>
          val alreadyAdded: HashSet[String] = HashSet.empty[String]
          def add(transfo: TranfoPath)(source: Path) {
            val sourceFile = source.asFile
            if (!sourceFile.isDirectory) {
              val relativePath = pathPrefix.map( _  + "/").getOrElse("") + transfo(source)
              log.debug("\tAdding " + source + " as " + relativePath + " ...")
              if (alreadyAdded contains relativePath) {
                log.debug("\t\t[skipped]")
              } else {
                alreadyAdded addEntry relativePath
                val nextEntry = new ZipEntry(relativePath)
                nextEntry.setTime(sourceFile.lastModified)
                output.putNextEntry(nextEntry)
                FileUtilities.transferAndClose(new FileInputStream(sourceFile), output, log)
                output.closeEntry()
              }
            }
          }
          for ((files, loc) <- filesLocs) {
            files.get.foreach(add(loc))
          }
          None
        }
      if (result.isEmpty) log.info("Packaging complete.")
      result
    }).run
  }

  private def withZipOutput(file: File, log: Logger)(f: ZipOutputStream => Option[String]): Option[String] = {
    FileUtilities.writeStream(file, log) {
      fileOut => {
        val (zipOut, ext) = (new ZipOutputStream(fileOut), "zip")
        Control.trapUnitAndFinally("Error writing " + ext + ": ", log) {
          f(zipOut)
        } {
          zipOut.close
        }
      }
    }
  }
}
