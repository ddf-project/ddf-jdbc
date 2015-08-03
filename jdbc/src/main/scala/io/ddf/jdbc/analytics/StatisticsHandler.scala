package io.ddf.jdbc.analytics

import java.util

import io.ddf.DDF
import io.ddf.analytics._
import io.ddf.content.Schema
import io.ddf.exception.DDFException
import io.ddf.jdbc.JdbcDDFManager
import io.ddf.jdbc.content.SqlArrayResultCommand
import org.apache.commons.lang.StringUtils

import scala.collection.JavaConversions._

class StatisticsHandler(ddf: DDF) extends AStatisticsSupporter(ddf) {

  val ddfManager: JdbcDDFManager = ddf.getManager.asInstanceOf[JdbcDDFManager]

  //count all,sum,mean,variance,notNullCount,min,max
  protected def SUMMARY_FUNCTIONS = "COUNT(*), SUM(%s), AVG(%s), VAR_SAMP(%s),COUNT(%s), MIN(%s), MAX(%s)"


  override def getSummaryImpl: Array[Summary] = {
    val summaries: util.List[Summary] = new util.ArrayList[Summary]
    val numericColumns: util.List[Schema.Column] = this.getNumericColumns
    val sqlCommand: util.List[String] = new util.ArrayList[String]
    numericColumns.foreach { column =>
      sqlCommand.add(String.format(SUMMARY_FUNCTIONS, column.getName, column.getName, column.getName, column.getName, column.getName, column.getName, column.getName))
    }
    var sql: String = StringUtils.join(sqlCommand, ", ")
    val tableName = this.getDDF.getTableName
    sql = String.format("select %s from %s", sql, tableName)
    val result = SqlArrayResultCommand(ddfManager.defaultDataSourceName, tableName, sql).result.get(0)
    var i: Int = 0
    numericColumns.foreach { column =>
      val count = if (result(i) == null) -1 else result(i).toString.toLong
      val sum = if (result(i + 1) == null) Double.NaN else result(i + 1).toString.toDouble
      val mean = if (result(i + 2) == null) Double.NaN else result(i + 2).toString.toDouble
      val variance = if (result(i + 3) == null) Double.NaN else result(i + 3).toString.toDouble
      val naCount = if (result(i + 4) == null) -1 else result(i + 4).toString.toLong
      val min = if (result(i + 5) == null) Double.NaN else result(i + 5).toString.toDouble
      val max = if (result(i + 6) == null) Double.NaN else result(i + 6).toString.toDouble
      val summary: Summary = new ExtSummary(count, sum, mean, variance, naCount, min, max)
      summaries.add(summary)
      i = i + 7
    }
    summaries.toArray(new Array[Summary](summaries.size))
  }


  class ExtSummary(_count: Long, _sum: Double, _mean: Double, _variance: Double, _naCount: Long,
                   _min: Double, _max: Double) extends Summary(_count, _mean, 0, _naCount, _min, _max) {
    override def stdev() = {
      Math.sqrt(_variance)
    }

    override def variance() = {
      _variance
    }

    override def sum() = {
      _sum
    }


  }

  private def getCategoricalColumns: util.List[Schema.Column] = {
    this.getDDF.getSchema.getColumns.filter(column => column.getColumnClass eq Schema.ColumnClass.FACTOR)
  }

  private def getNumericColumns: util.List[Schema.Column] = {
    this.getDDF.getSchema.getColumns.filter(col => col.isNumeric)
  }

  @throws(classOf[DDFException])
  def getSimpleSummaryImpl: Array[SimpleSummary] = {

    val categoricalColumns: util.List[Schema.Column] = this.getCategoricalColumns
    val simpleSummaries: util.List[SimpleSummary] = new util.ArrayList[SimpleSummary]
    categoricalColumns.foreach { column =>
      val sqlCmd: String = String.format("select distinct(%s) from %s where %s is not null", column.getName, this.getDDF.getTableName, column.getName)
      val values: util.List[String] = ddf.getSqlHandler.sql(sqlCmd).getRows
      val summary: CategoricalSimpleSummary = new CategoricalSimpleSummary
      summary.setValues(values)
      summary.setColumnName(column.getName)
      simpleSummaries.add(summary)
    }
    val numericColumns: util.List[Schema.Column] = this.getNumericColumns
    val sqlCommand: util.List[String] = new util.ArrayList[String]
    for (column <- numericColumns) {
      sqlCommand.add(String.format("min(%s), max(%s)", column.getName, column.getName))
    }
    var sql: String = StringUtils.join(sqlCommand, ", ")
    val tableName = this.getDDF.getTableName
    sql = String.format("select %s from %s", sql, tableName)
    val result = SqlArrayResultCommand(ddfManager.defaultDataSourceName, tableName, sql).result.get(0)
    var i: Int = 0
    for (column <- numericColumns) {
      val summary: NumericSimpleSummary = new NumericSimpleSummary
      summary.setColumnName(column.getName)
      val min = result(i).toString
      val max = result(i + 1).toString
      summary.setMin(if (min == null) Double.NaN else min.toDouble)
      summary.setMax(if (max == null) Double.NaN else max.toDouble)
      simpleSummaries.add(summary)
      i = i + 2
    }
    simpleSummaries.toArray(new Array[SimpleSummary](simpleSummaries.size))
  }


}
