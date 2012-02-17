package com.mimesis.monitor.stats

import java.io.File
import collection.JavaConversions._
import io.Source
import org.specs2.mutable._
import org.joda.time._
import net.liftweb.json._
import StatsFromHttpAccess._
import net.liftweb.json.JsonDSL._
import org.specs2.execute.Failure

class StatsFromHttpAccessSpec extends Specification {

  implicit val formats = DefaultFormats
  val parser = new AccessDataParser(Array("utm_campaign", "foo"))

  implicit def fileToExtFile(file : File) = new {
    def /(path : String) = new File(file, path)
  }

  def createOutputRoot(folderName : String) = {
    val testDir = (new File("./target/test")).getCanonicalFile
    val outputDir = testDir / ((new java.util.Date).getTime + folderName)
    outputDir.mkdirs()
    outputDir
  }

  def equalsAD(parser : AccessDataParser, line : String , expected : AccessData) = {
    parser.parse(line) match {
      case Left(err) => err must_== None
      case Right(actual) => actual must_== expected
    }
  }
  
  "stats" should {
    "parse http access log" in  {
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?_=1310468661091 HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?...", "200", 1266, Some(140949))
      )
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data? HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?...", "200", 1266, Some(140949))
      )
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data", "200", 1266, Some(140949))
      )
    }
    "parse http access log with query param to keep" in  {
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data", "200", 1266, Some(140949))
      )
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?_=1310468661091&utm_campaign= HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?...", "200", 1266, Some(140949))
      )
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?_=1310468661091&utm_campaign=toto HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?utm_campaign=toto...", "200", 1266, Some(140949))
      )
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?utm_campaign=toto&_=1310468661091 HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?utm_campaign=toto...", "200", 1266, Some(140949))
      )
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?utm_campaign=toto HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?utm_campaign=toto...", "200", 1266, Some(140949))
      )
    }
    "parse http access log with query param to keep order from config" in  {
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?utm_campaign=toto&foo=33 HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?utm_campaign=toto&foo=33...", "200", 1266, Some(140949))
      )      
      equalsAD(parser
        ,"""81.252.204.221 - - [12/Jul/2011:11:04:19 +0000] "GET /shared/js-user-data?foo=33&utm_campaign=toto HTTP/1.1" 200 1266 140949 "http://www.platform.content.bmnation.net/rooms" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/534.30 (KHTML, like Gecko) Ubuntu/11.04 Chromium/12.0.742.112 Chrome/12.0.742.112 Safari/534.30""""
        ,AccessData("81.252.204.221", parser.toDateTime("12/Jul/2011:11:04:19 +0000"), "GET", "/shared/js-user-data?utm_campaign=toto&foo=33...", "200", 1266, Some(140949))
      )      
    }
    "generate separate files for 2 different days" in {
      val outputRoot = createOutputRoot("test2days")
      val stats = new Statistics4SizeAndDuration
      val dt = (new DateTime()).withDayOfMonth(10) //to have 2 chars and no overlap in dt2
      val dt2 = dt.plusDays(2)
      val accessDatas = List(
        AccessData("host.test", dt, "POST", "rooms", "200", 22, Some(333)),
        AccessData("host.test", dt, "POST", "rooms", "200", 10, Some(100)),
        AccessData("host.test", dt2, "POST", "rooms", "200", 100, Some(1000))
      )

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
