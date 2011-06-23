package com.mimesis.monitor.stats

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

object StatsFromHttpAccess {

  val LogEntrySD = """^(\S+)\s+(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+"(\S+)\s+(\S+)\s+(\S+)"\s+(\S+)\s+(\S+)\s+(\d+).*""".r
  val LogEntryS = """^(\S+)\s+(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+"(\S+)\s+(\S+)\s+(\S+)"\s+(\S+)\s+(\S+).*""".r
  val dateParser = DateTimeFormat.forPattern("dd/MMM/yyyy:HH:mm:ss Z")
  val dateParserFr = dateParser.withLocale(Locale.FRENCH)
  val dateParserEn = dateParser.withLocale(Locale.ENGLISH)

  val statistics4size = new Statistics()
  val statistics4duration = new Statistics()

  def main(args: Array[String]): Unit = {
    val outputRoot = new File(System.getProperty("stats_http.data", "/var/log/stats_http/data"))
    outputRoot.mkdirs()

    for (
      path <- args ;
      line <- Source.fromFile(new File(path), "UTF-8").getLines()
    ) {
      line match {
        case LogEntrySD(host, identUser, authUser, date, method, url, protocol, status, size, duration) =>
          analyseAccessLine(host, date, method, url, status, size, Option(duration))
        case LogEntryS(host, identUser, authUser, date, method, url, protocol, status, size) =>
          analyseAccessLine(host, date, method, url, status, size, None)
        case s if s.trim().length == 0 => () // ignore empty line
        case s => System.err.println("bad format in " + path + " ignore " + s)
      }
    }
    storeInJsonFiles(outputRoot, MetricInfo("http_size", "B"), statistics4size.data)
    storeInJsonFiles(outputRoot, MetricInfo("http_duration", "microsecond"), statistics4duration.data)
  }

  def analyseAccessLine(host : String, date : String, method : String, url : String, status : String, size : String, duration : Option[String]) = {
    println("date", date, "method", method)
    val timestamp = toDateTime(date)
    val key = status + " - " + url
    statistics4size.append(key, toLong(size), timestamp)
    duration.foreach{ d => statistics4duration.append(key, toLong(d), timestamp) }
  }

  private def toDateTime(s : String) : DateTime = {
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

  def groupByDay(v: Iterable[Statistic]) : Map[ReadableInstant, Iterable[Statistic]] = v.groupBy { _.interval.getStart.toDateMidnight() }
  def groupByMonth(v: Iterable[Statistic]) : Map[ReadableInstant, Iterable[Statistic]] = v.groupBy { _.interval.getStart.toDateMidnight().withDayOfMonth(1) }

//  def display(title : String, data: Iterable[Statistic], groupBy: Iterable[Statistic] => Map[ReadableInstant, Iterable[Statistic]]) = {
//    println(title)
//    for (
//      (day, stats) <- groupBy(data);
//      stat <- stats.toList.sortWith(_.total < _.total)
//    ) println(day, stat)
//  }

  case class MetricInfo(name : String, unit : String)

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

    def storeInFileBy(data : Iterable[Statistic], formatterPath : DateTimeFormatter, formatterInterval : DateTimeFormatter, groupBy: Iterable[Statistic] => Map[ReadableInstant, Iterable[Statistic]]) : Iterable[File] = {
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
    private var _data = Map.empty[String, Statistic]

    def append(key: String, v: Long, timestamp: ReadableInstant) {
      _data = _data + ((key, _data.getOrElse(key, { Statistic(new Interval(timestamp, timestamp), key) }).append(v, timestamp)))
    }

    def data = _data.values
  }
}