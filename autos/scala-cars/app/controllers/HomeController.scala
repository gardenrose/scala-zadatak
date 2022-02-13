package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import io.circe.Json
import io.circe.syntax._
import io.circe.parser.decode
import cats.syntax.either._
import io.circe._
import io.circe.parser._
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.generic.auto._
import java.sql.DriverManager
import java.sql.Connection
import org.apache.spark.sql.SparkSession
import scala.util.control.Breaks._
import org.apache.spark.sql.functions._
import scala.collection.mutable.ArrayBuffer
import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent._
import javax.inject.Singleton
import java.util.NoSuchElementException
import org.apache.spark.sql._
import org.joda.time.format._
import org.joda.time.DateTime

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

// model klasa za reklamu, ista polja kao u bazi podataka
case class Advert(
  id: Int, 
  title : String, 
  fueltype: String, 
  price: Int, 
  isnew: Boolean, 
  mileage: Int, 
  firstregistration: String
)

// ---------------------------- POMOCNE METODE ZA KONTROLERE------------------------------
// povezivanje na bazu koristenjem spark sesije i vracanje dataframe-a, sadrzi sve redove iz baze
  def connectToDB(): org.apache.spark.sql.Dataset[org.apache.spark.sql.Row] = {
    val spark = SparkSession
      .builder()
      .appName("Spark SQL basic example")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    var c = ArrayBuffer[Array[String]]()

    val df = spark.read.format("jdbc")
    .option("url", "jdbc:mysql://localhost/cars_db")
    .option("driver", "com.mysql.jdbc.Driver")
    .option("dbtable", "advert")
    .option("user", "root")
    .option("password", "HasH2019")
    .load()
    df
  }


  // vadi sve redove iz baze i sprema ih u niz objekata klase Advert koje kasnije prevodi u json
  // status code je dio jsona, ali se moze vidjeti i u thunder clientu, kad se pozove Result, npr Created
  def getAllCarAdverts(criteria: String): String = {
    var df = connectToDB()
    val rawData = ArrayBuffer[Array[String]]();
    df.show(10,false)

    criteria match {
      case "none" => df.sort("id").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
      case "title" => df.sort("title").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
      case "fueltype" => df.sort("fueltype").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
      case "isnew" => df.sort("isnew").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
      case "mileage" => df.sort("mileage").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
      case "firstregistration" => df.sort("firstregistration").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
      case "price" => df.sort("price").collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
    }
    import Advert._
    df.collect().foreach(row => println(row))
    var jsonData = ArrayBuffer[Advert]()
    
    
    for (element <- rawData) {
      var adv : Advert = Advert(element(0).toInt,element(1),element(2),element(3).toInt,element(4).toBoolean,element(5).toInt,element(6))
      jsonData += adv
    }

    val advEncoder = Encoder[ArrayBuffer[Advert]].apply(jsonData)

    val fieldList = List(
        ("Code", Json.fromString("200 (OK)")),
        ("Content", advEncoder.asJson))
    Json.fromFields(fieldList).toString

  }


  // izvadi reklamu s odgovarajućim id-om iz baze pomoću opcije where df(id) === id
  def getCertainAdvert(id: Int): String = {
    var df = connectToDB()
    var rawData = ArrayBuffer[Array[String]]()
    var encodedAdvert: Json = "".asJson
    df.where(df("id") === id).collect().foreach(row => rawData += row.toString.stripPrefix("[").stripSuffix("]").trim.split(",").map(_.toString).distinct)
    import Advert._
    if (rawData.isEmpty) {
      "404" 
    } else {
      breakable { for (element <- rawData) {
      var adv : Advert = Advert(element(0).toInt,element(1),element(2),element(3).toInt,element(4).toBoolean,element(5).toInt,element(6))
      encodedAdvert = Encoder[Advert].apply(adv)
      break;
      } }
      val fieldList = List(
        ("Code", Json.fromString("200 (OK)")),
        ("Content", encodedAdvert))
      Json.fromFields(fieldList).toString
      
    }
  }


  // kreira sekvencu podataka od varijabli objekta advert i dodaje kao redak u bazu - append
  import Advert._
  def insertNewToDB(adv: Advert): String = {
    val spark = SparkSession
      .builder()
      .appName("Spark SQL basic example")
      .master("local[*]")
      .getOrCreate()
      import spark.implicits._
      spark.sparkContext.setLogLevel("ERROR")

      val df2 = Seq((adv.id, adv.title, adv.fueltype, adv.price, adv.isnew, adv.mileage, adv.firstregistration)).toDF("id", "title","fueltype","price","isnew","mileage","firstregistration")
      
      df2.write
        .format("jdbc")
        .option("driver","com.mysql.jdbc.Driver")
        .option("url","jdbc:mysql://localhost/cars_db")
        .option("dbtable","advert")
        .option("user", "root")
        .option("password", "HasH2019")
        .mode(SaveMode.Append)
        .save();
      ""

  }

  // pretrazuje redove u bazi i dodaje ih u dataframe, osim ako se id podudara, onda ne dodaje
  // te nove podatke bez tog id-a zapisuje nazad u bazu overwrite metodom
  def removeFromDB(id: Int): String = {
    val spark = SparkSession
      .builder()
      .appName("Spark SQL basic example")
      .master("local[*]")
      .getOrCreate()
      import spark.implicits._
      spark.sparkContext.setLogLevel("ERROR")

      var df = connectToDB()
      var df2 = Seq[(Int,String,String,Int,Boolean,Int,String)]()
      
      df.where(df("id") !== id).collect().foreach(row => row.toString.stripPrefix("[").stripSuffix("]").trim.split(",") match {
        case Array(s1, s2, s3, s4, s5, s6, s7) =>  df2 = df2 :+ ((s1.toInt, s2, s3, s4.toInt, s5.toBoolean, s6.toInt, s7))
        println(s1, s2, s3, s4, s5, s6, s7)
      })
      println(df2)

      
      var df3 = df2.toDF("id", "title","fueltype","price","isnew","mileage","firstregistration")
      df3.write
        .format("jdbc")
        .option("driver","com.mysql.jdbc.Driver")
        .option("url","jdbc:mysql://localhost/cars_db")
        .option("dbtable","advert")
        .option("user","root")
        .option("password","HasH2019")
        .mode(SaveMode.Overwrite)
        .save();
      ""
  }

  // izbrise iz baze odgovarajuci redak i upise na taj id novi s izmijenjenim podacima
  import Advert._
  def modifyInDB(advert: Advert): String = {
    removeFromDB(advert.id)
    insertNewToDB(advert);
    ""
  }

  // provjerava jesu li podaci ispravnog formata za slucaj unosa i izmjene neke reklame
  // provjeravano je ovako jer metoda instanceOf ne radi, vraca false stalno.
  import Advert._
  def validateAdvertData(ad: Advert) : Boolean = {
    // println(ad.price.getClass.toString)
    if (ad.id.getClass.toString=="int" && ad.title.getClass.toString == "class java.lang.String" && 
        ad.fueltype.getClass.toString=="class java.lang.String" && ad.price.getClass.toString=="int" && 
        ad.isnew.getClass.toString=="boolean" && ad.mileage.getClass.toString=="int"
        && ad.firstregistration.getClass.toString=="class java.lang.String") {
      true
      val format = new java.text.SimpleDateFormat("yyyy-MM-dd")
      try {
        DateTime.parse(ad.firstregistration)
        true
      } catch {
        case scala.util.control.NonFatal(e) => println("Wrong date format")
        false
      }
    } else {
      false
    }
    
  }



  //--------------------------------- REQUEST HANDLERS -----------------------------------------------
  // ucitava sve reklame
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(getAllCarAdverts("none"))
  }

  // ucitava reklame sortirane po kriteriju
  def getAllSorted(criteria: String) = Action { implicit request: Request[AnyContent] =>
    Ok(getAllCarAdverts(criteria))
  }

  // ucitava podatke o reklami ako taj id postoji u bazi
  def viewCar(id:Int) = Action {_ => 
    if (getCertainAdvert(id) == "404") {
      NotFound(Json.fromFields(List(
        ("Code", Json.fromString("404 (Not found)")),
        ("Content", None.asJson),
        ("Explanation", "No car advert with given id was found.".asJson))).toString)
    } 
    else {
      Ok(getCertainAdvert(id))
    }
    
  }

  // dodaje novu reklamu za automobil ako to id polje ne postoji u bazi, ako vec postoji, ispisuje poruku
  def createCar = Action(parse.json) {implicit request => 
    val result = io.circe.parser.decode[Advert](request.body.toString)
    result.fold(
      errors => {
      BadRequest(Json.fromFields(List(("400", "Bad request".asJson))).toString)
      },
      advert => {
        val fieldList = List(
        ("Code", Json.fromString("201 (Created)")),
        ("Content", advert.asJson))
        if (getCertainAdvert(advert.id) == "404") {
          if (validateAdvertData(advert)) {
            insertNewToDB(advert)
            Created(Json.fromFields(fieldList).toString)
          } else {
            Ok(Json.fromFields(List(("422", "Unprocessable data".asJson))).toString)
          }
        } else {
          Ok("Car advert with the given id already exists.")
        }
        
      }
    )
  }

  // brise reklamu ako id postoji u bazi
  def deleteAdvert(id: Int) = Action  {_ => 
    if (getCertainAdvert(id) == "404") {
      NotFound(Json.fromFields(List(
        ("Code", Json.fromString("404 (Not found)")),
        ("Content", None.asJson),
        ("Explanation", "No car advert with given id was found.".asJson))).toString)
    } else {
      Ok(removeFromDB(id))
      NoContent
    } 
  }

  // izmjenjuje podatke reklame ako id reklame postoji u bazi
  def modifyAdvert(id: Int) = Action {implicit request =>
    val result = io.circe.parser.decode[Advert](request.body.asJson.get.toString)
    result.fold(
      errors => {
      BadRequest(Json.fromFields(List(("400", "Bad request".asJson))).toString)
      },
      advert => {
        // Ako ne postoji
        if (getCertainAdvert(advert.id) == "404") {
          NotFound(Json.fromFields(List(
            ("Code", Json.fromString("404 (Not found)")),
            ("Content", None.asJson),
            ("Explanation", "No car advert with given id was found.".asJson))).toString)
        } else {
          modifyInDB(advert)
          val fieldList = List(
            ("Code", Json.fromString("200 (OK)")),
            ("Content", advert.asJson))
          Ok(Json.fromFields(fieldList).toString)
        }
        
      }
    )
  }
}