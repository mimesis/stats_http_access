package com.mimesis.monitor.stats

import java.io.File
import collection.JavaConversions._
import org.specs2.mutable._
import StatsFromHttpAccess._
import org.joda.time._

class StatsFromHttpAccessSpec extends Specification {

  def createOutputRoot(folderName : String) = {
    val testDir = (new File("./target/test")).getCanonicalFile
    val outputDir = new File(testDir, (new java.util.Date).getTime + folderName)
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
        AccessData("host.test", dt2, "POST", "rooms", "200", 100, Some(1000)))

      accessDatas.foreach(stats.append)
      storeInJsonFiles(outputRoot, MetricInfo("metric", "M"), stats.size.data)
      outputRoot.listFiles()(0).listFiles.map(_.getName).toList must haveTheSameElementsAs(List(
        "metric.json",
        dt.dayOfMonth.getAsText,
        dt2.dayOfMonth.getAsText))
    }
  }
}
