package test

import test.MemSQLTestSetup

import java.sql.{DriverManager, ResultSet}

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._

import com.memsql.spark.connector.dataframe.MemSQLDataFrame
import com.memsql.spark.connector.rdd.MemSQLRDD
import com.memsql.spark.connector._

object TestMemSQLDataFrameVeryBasic {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("MemSQLRDD Application")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    val host = "127.0.0.1"
    val port = 3306
    val user = "root"
    val password = ""
    val dbName = "x_db"
    
    MemSQLTestSetup.SetupBasic()

    val df_t = MemSQLDataFrame.MakeMemSQLDF(
      sqlContext,
      host,
      port,
      user,
      password,
      dbName,
      "t")
    val df_s = MemSQLDataFrame.MakeMemSQLDF(
      sqlContext,
      host,
      port,
      user,
      password,
      dbName,
      "s")
    val df_r = MemSQLDataFrame.MakeMemSQLDF(
      sqlContext,
      host,
      port,
      user,
      password,
      dbName,
      "r")

    val dfs = Array(df_t, df_s)//, df_r) TODO: reference tables dont work
    for (i <- 0 until dfs.size)
    {

        // TODO: We dont automatically test that anything is actually pushed down
        // but you can see them being pushed down by reading the memsql tracelog
      
        println(dfs(i).schema)
        println(dfs(i).rdd.toDebugString)
        var results = dfs(i).collect()
        println(results.size)
        assert(results.size == 1000)
        assert(dfs(i).count() == 1000)
    
        assert(dfs(i).filter(dfs(i)("id") === 1).collect().size == 1)
        assert(dfs(i).filter(dfs(i)("id") === 1).count == 1)
        
        assert(dfs(i).filter(dfs(i)("id") <= 1).collect().size == 2)
        assert(dfs(i).filter(dfs(i)("id") <= 1).count == 2)
    
        assert(dfs(i).filter(dfs(i)("id") < 1).collect().size == 1)
        assert(dfs(i).filter(dfs(i)("id") < 1).count == 1)
    
        assert(dfs(i).filter(dfs(i)("id") > 1).collect().size == 998)
        assert(dfs(i).filter(dfs(i)("id") > 1).count == 998)
    
        assert(dfs(i).filter(dfs(i)("id") >= 1).collect().size == 999)
        assert(dfs(i).filter(dfs(i)("id") >= 1).count == 999)
    
        assert(dfs(i).filter(dfs(i)("id").in(dfs(i)("id"))).collect().size == 1000)
      // TODO: Get inlists to work
      //        assert(dfs(i).filter(dfs(i)("id").in(1,2,3,-1)).collect().size == 3)
      //        assert(dfs(i).filter(dfs(i)("id").in(1,2,3,-1)).count == 3)
    
        assert(dfs(i).filter(dfs(i)("data") === "test_data_0000").collect().size == 1)
        assert(dfs(i).filter(dfs(i)("data") === "test_data_0000").count() == 1)
    
        // assert(dfs(i).filter(dfs(i)("data").in("test_data_0000","test_data_0000","not_present")).collect().size == 2)
        // assert(dfs(i).filter(dfs(i)("data").in("test_data_0000","test_data_0000","not_present")).count() == 2)

    }
  }
}

object TestSaveToMemSQLVeryBasic {
  def main(args : Array[String]) {
    val conf = new SparkConf().setAppName("MemSQLRDD Application")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    val host = "127.0.0.1"
    val port = 3306
    val user = "root"
    val password = ""
    val dbName = "x_testsave"
    
    TestUtils.DropAndCreate(dbName)
    
    val rdd = sc.parallelize(
      Array(Row(1,"pieguy"),
            Row(2,"gbop"),
            Row(3,"berrydave"),
            Row(4,"psyduck"),
            Row(null,"null")))
    val schema = StructType(Array(StructField("a",IntegerType,true),
                                  StructField("b",StringType,false)))
    val df1 = sqlContext.createDataFrame(rdd, schema)

    df1.createMemSQLTableAs(host, port, user, password, dbName, "t")

    val df_t = TestUtils.MemSQLDF(sqlContext,dbName, "t")
    assert(df_t.schema.equals(schema))
    assert(df_t.count == 5)
    assert(TestUtils.EqualDFs(df_t, df1))

    df1.saveToMemSQL(host, port, user, password, dbName, "t")

    assert(TestUtils.EqualDFs(df_t, df1.unionAll(df1)))
    
    // the column name matching should work
    df1.select("b","a").saveToMemSQL(host, port, user, password, dbName, "t")
    assert(TestUtils.EqualDFs(df_t, df1.unionAll(df1).unionAll(df1)))

    // and expressions and column renaming
    df1.where(df1("a") < 5).select(df1("a") + 1 as "b",df1("a")).saveToMemSQL(host, port, user, password, dbName, "t")    
    assert (df_t.filter(df_t("b") === "3").count == 1)

  }
}

object TestMemSQLTypes {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("MemSQLRDD Application")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    val host = "127.0.0.1"
    val port = 3306
    val user = "root"
    val password = ""
    val dbName = "alltypes_db"

    TestUtils.DropAndCreate(dbName)
    
    val df_not_null = MemSQLTestSetup.SetupAllMemSQLTypes(sqlContext, false)
    val df_nullable = MemSQLTestSetup.SetupAllMemSQLTypes(sqlContext, true)
    
    assert(df_not_null.count == 3)
    assert(df_nullable.count == 4)
    assert(df_not_null.schema.size == Types.MemSQLTypes.size)
    assert(df_nullable.schema.size == Types.MemSQLTypes.size)

    for (i <- 0 until Types.MemSQLTypes.size)
    {
      val colname = Types.ToCol(Types.MemSQLTypes(i)._1)
      println(colname)

      assert(df_not_null.schema(i).dataType.equals(df_nullable.schema(i).dataType))
      assert(df_not_null.schema(i).name.equals(df_nullable.schema(i).name))
      assert(!df_not_null.schema(i).nullable)
      assert( df_nullable.schema(i).nullable)

      var cd_nn = df_not_null.select(colname).collect.map(_(0))
      var cd_na = df_nullable.select(colname).collect.map(_(0))
      println("not null")
      for (r <- cd_nn)
      {
        println(r)
      }
      println("nullable")
      for (r <- cd_na)
      {
        println(r)
      }
      println("reference")
      for (r <- Types.MemSQLTypes(i)._2)
      {
        println(r)
      }
      assert(cd_na.indexOf(null) != -1)
      assert(cd_nn.indexOf(null) == -1)
      
      assert(cd_na.filter(_ != null).map(_.toString).indexOf(Types.MemSQLTypes(i)._2(0)) != -1)
      assert(cd_na.filter(_ != null).map(_.toString).indexOf(Types.MemSQLTypes(i)._2(1)) != -1)
      assert(cd_na.filter(_ != null).map(_.toString).indexOf(Types.MemSQLTypes(i)._2(2)) != -1)

      assert(cd_nn.map(_.toString).indexOf(Types.MemSQLTypes(i)._2(0)) != -1)
      assert(cd_nn.map(_.toString).indexOf(Types.MemSQLTypes(i)._2(1)) != -1)
      assert(cd_nn.map(_.toString).indexOf(Types.MemSQLTypes(i)._2(2)) != -1)

    }

  }
}