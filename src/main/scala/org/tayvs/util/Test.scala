package org.tayvs.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object Test extends App {

//  val myMapThreadLocal = new ThreadLocal[Map[String, String]]

  def assert(b: Boolean) = {
    if (!b) {
      println("assertion failed")
      throw new Exception("Assertion failed")
    }
  }

//  val contextfull: Promise.DefaultPromise[Int] = new Promise.DefaultPromise(Success(123), new ContextHolder(Map("hello" -> "world")))
//
//  //  val contextfull: Promise.DefaultPromise[Int] =
//  //    new Promise.DefaultPromise(Success(123), new ContextHolderThreadLocalHolder(ThreadLocalsHolder(myMapThreadLocal, Map("hello" -> "world"))))
//
//  val c1 = contextfull.map { int =>
//    println(int)
//    println(ContextHolder.getContext)
//    assert(ContextHolder.getContext == Map("hello" -> "world"))
//    ContextHolder.addContext("key1", "value")
//    println(ContextHolder.getContext)
//    assert(ContextHolder.getContext == Map("hello" -> "world", "key1" -> "value"))
//    //    println(myMapThreadLocal.get())
//    //    myMapThreadLocal.set(myMapThreadLocal.get() + ("key1" -> "value"))
//    //    println(myMapThreadLocal.get())
//    int + 1
//  }
//
//
//  Thread.sleep(50)
//  println("#" * 50)
//
//  val c2 = contextfull.map { int =>
//    println(int)
//    println(ContextHolder.getContext)
//    assert(ContextHolder.getContext == Map("hello" -> "world"))
//    ContextHolder.addContext("key2", "value")
//    println(ContextHolder.getContext)
//    assert(ContextHolder.getContext == Map("hello" -> "world", "key2" -> "value"))
//    int + 1
//  }
//
//
//  Thread.sleep(50)
//  println("#" * 50)
//
//  c1.map { _ =>
//    assert(ContextHolder.getContext == Map("hello" -> "world", "key1" -> "value"))
//    println(ContextHolder.getContext)
//  }
//
////  Thread.sleep(50)
////  println("#" * 50)
////
////  c2.map(_ => println(ContextHolder.getContext))
////    .map { _ =>
////      ContextHolder.addContext("key2", "value2")
////      println(ContextHolder.getContext)
////      Thread.sleep(10)
////    }
////    .flatMap(_ => new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie"))))
//
//  Thread.sleep(20)
//
//  //  new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
//  //    .flatMap(_ => contextfull)
//  //    .foreach(_ => println(ContextHolder.getContext))
//
//
//  //  new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
//  //    .flatMap { _ =>
//  //      Thread.sleep(10)
//  //      contextfull
//  //    }
//  //    .foreach(_ => println(ContextHolder.getContext))
//
//  println("#" * 50)
 new Thread {

//   setName("3")

   new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
     .flatMap { _ =>
       assert(ContextHolder.getContext == Map("cake" -> "is a lie"))
       //      Thread.sleep(10)
       //        val context = ContextHolder.getContext
       //        new Promise.DefaultPromise(Success(424242), new ContextHolder(context + ("Jo" -> "Jo")))
       Future.successful(424242)
     }
     .map { _ =>
       ContextHolder.addContext("Jo", "Jo")
       assert(ContextHolder.getContext == Map("cake" -> "is a lie", "Jo" -> "Jo"))
     }
     .foreach { _ =>
       assert(ContextHolder.getContext == Map("cake" -> "is a lie", "Jo" -> "Jo"))
       println(ContextHolder.getContext)
     }
 }

  //  new Promise.DefaultPromise(Success(4242), new ContextHolder(Map("cake" -> "is a lie")))
  //    .flatMap { _ =>
  //      val context = ContextHolder.getContext
  //      concurrent.Future.successful(123)
  //    }
  //    .foreach(_ => println(ContextHolder.getContext))

  Thread.sleep(50)
  println("#" * 50)

  Future.successful(42)
    .foreach { _ =>
      assert(ContextHolder.getContext == Map.empty)
      println(ContextHolder.getContext)
    }



  //  println(ContextHolder.getContext) // NPE

}
