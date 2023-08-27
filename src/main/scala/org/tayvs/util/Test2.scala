package org.tayvs.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object Test2 extends App {

  val myMapThreadLocal = new ThreadLocal[Map[String, String]]

  def assert(b: Boolean) = {
    if (!b) {
      println("assertion failed")
      throw new Exception("Assertion failed")
    }
  }

  println("test started")

  new Promise.DefaultPromise(Success(42))
    .map { _ =>
      val state = Map("cake" -> "is a lie")
      myMapThreadLocal.set(Map("cake" -> "is a lie"))
      println(s"state setup $state")
    }
    .map { _ =>
      assert(myMapThreadLocal.get() == Map("cake" -> "is a lie"))
      println(s"state is ${myMapThreadLocal.get()}")

      val newState = Map("cake" -> "is a lie", "Jo" -> "Jo")
      myMapThreadLocal.set(newState)
      println(s"state setup $newState")
    }
    .map { _ =>
      assert(myMapThreadLocal.get() == Map("cake" -> "is a lie", "Jo" -> "Jo"))
      println(s"state is ${myMapThreadLocal.get()}")
    }


  Thread.sleep(5_000)

  println("test finished")
}
