package org.tayvs.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object Test extends App {

//  val myMapThreadLocal = new ThreadLocal[Map[String, String]]

  val contextfull: Promise.DefaultPromise[Int] = new Promise.DefaultPromise(Success(123), new ContextHolder(Map("hello" -> "world")))

  //  val contextfull: Promise.DefaultPromise[Int] =
  //    new Promise.DefaultPromise(Success(123), new ContextHolderThreadLocalHolder(ThreadLocalsHolder(myMapThreadLocal, Map("hello" -> "world"))))

  val c1 = contextfull.map { int =>
    println(int)
    println(ContextHolder.getContext)
    ContextHolder.addContext("key1", "value")
    println(ContextHolder.getContext)
    //    println(myMapThreadLocal.get())
    //    myMapThreadLocal.set(myMapThreadLocal.get() + ("key1" -> "value"))
    //    println(myMapThreadLocal.get())
    int + 1
  }
  //  Thread.sleep(50)
  //
  //  val c2 = contextfull.map { int =>
  //    println(int)
  //    println(ContextHolder.getContext)
  //    ContextHolder.addContext("key2", "value")
  //    println(ContextHolder.getContext)
  //    int + 1
  //  }
  //  Thread.sleep(50)

  c1.map(_ => println(ContextHolder.getContext))
  //  Thread.sleep(50)
  //  c2.map(_ => println(ContextHolder.getContext))
  //    .map { _ =>
  //      ContextHolder.addContext("key2", "value2")
  //      println(ContextHolder.getContext)
  //      Thread.sleep(10)
  //    }
  //    .flatMap(_ => new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie"))))

    Thread.sleep(20)

  //  new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
  //    .flatMap(_ => contextfull)
  //    .foreach(_ => println(ContextHolder.getContext))


  //  new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
  //    .flatMap { _ =>
  //      Thread.sleep(10)
  //      contextfull
  //    }
  //    .foreach(_ => println(ContextHolder.getContext))

  println("#" * 50)

    new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
      .flatMap { _ =>
  //      Thread.sleep(10)
//        val context = ContextHolder.getContext
//        new Promise.DefaultPromise(Success(424242), new ContextHolder(context + ("Jo" -> "Jo")))
        Future.successful(424242)
          .map(_ => ContextHolder.addContext("Jo", "Jo"))
      }
      .foreach(_ => println(ContextHolder.getContext))

  //  new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
  //    .flatMap { _ =>
  //      val context = ContextHolder.getContext
  //      concurrent.Future.successful(123)
  //    }
  //    .foreach(_ => println(ContextHolder.getContext))

  Thread.sleep(50)

  //  println(ContextHolder.getContext) // NPE

}
