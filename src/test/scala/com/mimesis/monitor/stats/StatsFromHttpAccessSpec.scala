package com.mimesis.monitor.stats

import java.io.File
import collection.JavaConversions._
import io.Source
import org.specs2.mutable._
import org.joda.time._
import net.liftweb.json._
import StatsFromHttpAccess._
import net.liftweb.json.JsonDSL._

class StatsFromHttpAccessSpec extends Specification {

  implicit val formats = DefaultFormats

  implicit def fileToExtFile(file : File) = new {
    def /(path : String) = new File(file, path)
  }

  def createOutputRoot(folderName : String) = {
    val testDir = (new File("./target/test")).getCanonicalFile
    val outputDir = testDir / ((new java.util.Date).getTime + folderName)
    outputDir.mkdirs()
    outputDir
  }

  "stats" should {

    "generate separate files for 2 different days" in {
      val outputRoot = createOutputRoot("test2days")
      val stats = new Statistics4SizeAndDuration
      val dt = (new DateTime()).withDayOfMonth(10) //to have 2 chars and no overlap in dt2
      val dt2 = dt.plusDays(2)
      val accessDatas = List(
        AccessData("host.test", dt, "POST", "rooms", "200", 22, Some(333)),
        AccessData("host.test", dt, "POST", "rooms", "200", 10, Some(100)),
        AccessData("host.test", dt2, "POST", "rooms", "200", 100, Some(1000)))

      accessDatas.foreach(stats.append)
      storeInJsonFiles(outputRoot, MetricInfo("metric", "M"), stats.size.data)

      val monthDir = outputRoot.listFiles()(0)
      monthDir.listFiles.map(_.getName).toList must haveTheSameElementsAs(List(
        "metric.json",
        dt.dayOfMonth.getAsText,
        dt2.dayOfMonth.getAsText))

      // stats for dt
      val json = parse(Source.fromFile(monthDir / dt.dayOfMonth.getAsText / "metric.json").mkString)
      val JArray(values) = (json \ "statistics")(0)
      values(3).extract[Int] must_== 2 // nb of stats
      values(4).extract[Int] must_== 32 // total
      values(5).extract[Int] must_== 10 // min
      values(6).extract[Int] must_== 16 // avg
      values(7).extract[Int] must_== 22 // max
    }
  }
}
