package org.tayvs.java

//package java.lang

import ThreadLocalMapExtractor._
import java.util.concurrent.Executors

object ThreadLocalMapExtractorTest extends App {


  val s: ThreadLocal[String] = new ThreadLocal[String]()

  def getS = s.get()

  def setS(str: String): Unit = s.set(str)

  val e1 = Executors.newSingleThreadExecutor()
  val e2 = Executors.newSingleThreadExecutor()

  //  e1.submit()
  //
  //  val t1 = new ThreadWithS
  //  val t2 = new ThreadWithS

  println(e1.submit(() => getS).get())
  println(e2.submit(() => getS).get())
  println("### both should be nulls")

  ////////////////////////////////

  e1.submit((() => setS("ssss")): Runnable).get

  println(e1.submit(() => getS).get())
  println(e2.submit(() => getS).get())
  println("### first ssss second null")

  //////////////////////////////////

  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)

  println(e1.submit(() => getS).get())
  println(e2.submit(() => getS).get())
  println("### first ssss second ssss")

  /////////////////////////////////

  e1.submit((() => setS("wwwww")): Runnable).get

  println(e1.submit(() => getS).get())
  println(e2.submit(() => getS).get())
  println("### first wwww second ssss")

  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)

  println(e1.submit(() => getS).get())
  println(e2.submit(() => getS).get())

  println("### first wwww second wwww")

  e1.shutdown()
  e2.shutdown()

}
