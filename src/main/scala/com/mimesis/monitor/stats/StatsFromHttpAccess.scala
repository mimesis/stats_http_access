package com.mimesis.monitor.stats

import org.joda.time.DateMidnight
import java.util.Locale
import org.joda.time.format.ISODateTimeFormat
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.io.OutputStreamWriter
import java.io.FileWriter
import org.joda.time.format.DateTimeFormatter
import org.joda.time.DateTimeZone
import org.joda.time.ReadableInstant
import org.joda.time.Interval
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.Date
import scala.collection.immutable.Map
import scala.io.Source
import java.io.File
import org.joda.time.DateTimeFieldType

object StatsFromHttpAccess {


  def main(args: Array[String]): Unit = {
    val outputRoot = new File(System.getProperty("stats_http.data", "/var/log/stats_http/data"))
    val qparamsToKeep = System.getProperty("stats_http.qparams.tokeep", "").split('&')
    outputRoot.mkdirs()
    val parser = new AccessDataParser(qparamsToKeep)
    val s4sd = new Statistics4SizeAndDuration()
    for (
      path <- args ;
      line <- Source.fromFile(new File(path), "UTF-8").getLines()
    ) {
      analyze(s4sd, parser, line, path)
    }
    storeInJsonFiles(outputRoot, MetricInfo("http_size", "B"), s4sd.size.data)
    storeInJsonFiles(outputRoot, MetricInfo("http_duration", "microsecond"), s4sd.duration.data)
  }

  def analyze(s4sd : Statistics4SizeAndDuration, parser : AccessDataParser, line : String, path : String) = {
    parser.parse(line) match {
      case Right(data) => s4sd.append(data)
      case Left(None) => ()
      case Left(Some(msg)) => println("WARN " + path + " " + msg)
    }
  }

  def groupByDay(v: Iterable[Statistic]) : Map[ReadableInstant, Iterable[Statistic]] = v.groupBy { _.interval.getStart.toDateMidnight() }
  def groupByMonth(v: Iterable[Statistic]) : Map[ReadableInstant, Iterable[Statistic]] = v.groupBy { _.interval.getStart.toDateMidnight().withDayOfMonth(1) }

//  def display(title : String, data: Iterable[Statistic], groupBy: Iterable[Statistic] => Map[ReadableInstant, Iterable[Statistic]]) = {
//    println(title)
//    for (
//      (day, stats) <- groupBy(data);
//      stat <- stats.toList.sortWith(_.total < _.total)
//    ) println(day, stat)
//  }


  def storeInJsonFiles(root : File, metric : MetricInfo, data : Iterable[Statistic]) {
    def storeInFile(f : File, stats : Iterable[Statistic], interval : String) : File = {
      val formatter = ISODateTimeFormat.dateTime()
      val jsonStats = for ( stat <- stats.toList) yield {
        """["%s", "%s", "%s", %d, %d, %d, %d, %d]""".format(formatter.print(stat.interval.getStart), formatter.print(stat.interval.getEnd), stat.key, stat.nb, stat.total, stat.min, stat.avg, stat.max)
      }
      val statisticsJson= jsonStats.mkString("\"statistics\" : [\n", ",\n", "\n]")
      val metaJson = """"metric" : "%s", "unit": "%s", "interval" : "%s"""".format(metric.name, metric.unit, interval);

      f.getParentFile.mkdirs()
      val fw = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"))
      try {
        val json = "{\n" + metaJson +",\n" + statisticsJson + "\n}"
        fw.write(json)
      } finally {
        if (fw != null) {
          fw.close()
        }
      }
      f
    }

    def storeInFileBy(
      data : Iterable[Statistic],
      formatterPath : DateTimeFormatter,
      formatterInterval : DateTimeFormatter,
      groupBy: Iterable[Statistic] => Map[ReadableInstant, Iterable[Statistic]]) : Iterable[File] = {
      for (
        (instant, stats) <- groupBy(data)
      ) yield storeInFile(new File(root, formatterPath.print(instant) + "/" + metric.name + ".json"), stats, formatterInterval.print(instant))
    }

    val dayPath = DateTimeFormat.forPattern("yyyyMM/dd")
    val dayInterval = DateTimeFormat.forPattern("yyyy-MM-dd")
    val monthPath = DateTimeFormat.forPattern("yyyyMM")
    val monthInterval = DateTimeFormat.forPattern("yyyy-MM")

    storeInFileBy(data, dayPath, dayInterval, groupByDay).foreach{ f => println("update "  + f) }
    storeInFileBy(data, monthPath, monthInterval, groupByMonth).foreach{ f => println("update "  + f) }
  }

  case class AccessData(host : String, timestamp : DateTime, method : String, urlpath : String, status : String, size : Long, duration : Option[Long])
  case class MetricInfo(name : String, unit : String)

  class AccessDataParser(queryParamsToKeep : Array[String]) {
    private val LogEntrySD = """^(\S+)\s+(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+"(\S+)\s+(\S+)\s+(\S+)"\s+(\S+)\s+(\S+)\s+(\d+).*""".r
    private val LogEntryS = """^(\S+)\s+(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+"(\S+)\s+(\S+)\s+(\S+)"\s+(\S+)\s+(\S+).*""".r
    private val dateParser = DateTimeFormat.forPattern("dd/MMM/yyyy:HH:mm:ss Z")
    private val dateParserFr = dateParser.withLocale(Locale.FRENCH)
    private val dateParserEn = dateParser.withLocale(Locale.ENGLISH)


    def parse(line : String) : Either[Option[String], AccessData] = {
      val queryParamsToKeepE = queryParamsToKeep.map( _ + "=");
      line match {
        case LogEntrySD(host, identUser, authUser, date, method, url, protocol, status, size, duration) =>
          Right(AccessData(host, toDateTime(date), method, toCompactUrl(url, queryParamsToKeepE), status, toLong(size), Option(duration).map(toLong)))
        case LogEntryS(host, identUser, authUser, date, method, url, protocol, status, size) =>
          Right(AccessData(host, toDateTime(date), method, toCompactUrl(url, queryParamsToKeepE), status, toLong(size), None))
        case s if s.trim().length == 0 =>
          Left(None) // ignore empty line
        case s =>
          Left(Some("bad format : " + s))
      }
    }

    private[stats] def toDateTime(s : String) : DateTime = {
      try {
        dateParserFr.parseDateTime(s).withZone(DateTimeZone.UTC)
      } catch {
        case t : IllegalArgumentException => dateParserEn.parseDateTime(s).withZone(DateTimeZone.UTC)
      }
    }

    private def toLong(s : String) = try {
      if (s != "-") s.toLong else 0
    } catch {
      case t => 0
    }

    private def toCompactUrl(url : String, queryParamsToKeep : Array[String]) = url.indexOf("?") match {
      case -1 => url
      case pos => {
        val nvs = url.substring(pos+1).split('&');
        val query : Array[String] = for {
          tokeep <- queryParamsToKeep
          nv <- nvs
          if nv.length > tokeep.length && nv.startsWith(tokeep)
        } yield nv
        url.substring(0, pos) + query.mkString("?", "&", "...")
      }
    }

  }


  case class Statistic(interval: Interval, key: String, min: Long = Long.MinValue, max: Long = Long.MaxValue, total: Long = 0, nb: Long = 0) {

    def avg: Long = nb match {
      case 0 => 0
      case _ => total / nb
    }

    def append(v: Long, timestamp: ReadableInstant) = {
      val nnb = nb + 1
      nb match {
        case 0 => Statistic(new Interval(timestamp, timestamp), key, v, v, v, nnb)
        case _ => {
          Statistic(extendsInterval(interval, timestamp), key, math.min(min, v), math.max(max, v), total + v, nnb)
        }
      }
    }

    private def extendsInterval(interval: Interval, instant: ReadableInstant) = {
      if (interval.isBefore(instant)) {
        interval.withEnd(instant)
      } else if (interval.isAfter(instant)) {
        interval.withStart(instant)
      } else {
        interval
      }
    }
  }


  class Statistics() {
    private var _data = Map.empty[(String, Int), Statistic] // one Statistic per day

    def append(key: String, v: Long, timestamp: ReadableInstant) {
      val day = timestamp.get(DateTimeFieldType.dayOfYear)
      val index = (key, day)
      val stat = _data.getOrElse(index, Statistic(new Interval(timestamp, timestamp), key))
      val newStat = stat.append(v, timestamp)

      _data += ( index -> newStat)
    }

    def data : Iterable[Statistic] = _data.values
  }

  class Statistics4SizeAndDuration() {
    val size = new Statistics()
    val duration = new Statistics()

    def append(data : AccessData) = {
      val key = data.status + " - " + data.urlpath
      size.append(key, data.size, data.timestamp)
      data.duration.foreach{ d => duration.append(key, d, data.timestamp) }
    }

  }

}
