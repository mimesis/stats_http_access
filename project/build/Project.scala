import sbt._

class Project(info: ProjectInfo) extends AppOnlyProject(info) with MimesisRepository{
  val jodaTime = "joda-time" % "joda-time" % "1.6.2"
  val specs2 = "org.specs2" %% "specs2" % "1.4" % "test"

//  val zipArtifact = Artifact(artifactID, "zip", "zip")
//  val product = outputPath / (artifactID + "-" + version + ".zip")
//  val sources = List(
//    ("src" / "main" ##) / "webapp" ** "*",
//    ("src" / "main" / "app" ##) ** "*"
//  ) map (x => x filter (!_.isDirectory)) reduceLeft (_ +++ _)
//
//  override def packageAction = fileTask(List(product) from sources) {
//    FileUtilities.zip(sources.get, product, true, log)
//  } dependsOn(super.packageAction) describedAs("Creates a ZIP artifact.")

  def packageAppAction = assemblyAction(
    zipFile
    , List(
      (appZipFiles, pathInZipSame())
      , (libZipFiles, pathInZipFlatInSubdir("lib"))
      , (jarPath, pathInZipFlatInSubdir("lib"))
      , ((mainSourcePath ##) / "webapp" ** "*", pathInZipSame())
    )
    , Some(baseName + "-" + version)
  ) named "packageApp" describedAs "Package the app as a zip"

  lazy val packageApp = packageAppAction
  override def packageAction = packageAppAction dependsOn(super.packageAction)

  def specs2Framework = new TestFramework("org.specs2.runner.SpecsFramework")
  override def testFrameworks = super.testFrameworks ++ Seq(specs2Framework)
}

import collection.immutable.Set._
import sbt._

trait MimesisRepository extends BasicDependencyProject {
  val releasesRepo = "mimesis-releases-repo" at "http://10.101.0.202:8081/nexus/content/repositories/releases/"
  val snapshotsRepo = "mimesis-snapshots-repo" at "http://10.101.0.202:8081/nexus/content/repositories/snapshots/"
  val publicRepo = "public-repo" at "http://10.101.0.202:8081/nexus/content/groups/public"
  val publicSnapshotsRepo = "public-snapshots-repo" at "http://10.101.0.202:8081/nexus/content/groups/public-snapshots"
  val nexusRep = "Nexus" at "http://10.101.0.202:8081/nexus/content/repositories/3rdparty"

  lazy val publishTo =
    if (version.toString.contains("SNAPSHOT")) {
      snapshotsRepo
    } else {
      releasesRepo
    }
  lazy val nexusLogin = propertyOptional[String](System.getProperty("user.name"))
  lazy val nexusPassword = propertyOptional[String](System.getProperty("user.name"))
  Credentials.add("Sonatype Nexus Repository Manager", "10.101.0.202", nexusLogin.value, nexusPassword.value)

  override def repositories = Set(publicRepo, publicSnapshotsRepo, releasesRepo, snapshotsRepo, nexusRep)
}
