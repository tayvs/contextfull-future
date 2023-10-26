
import java.util.concurrent.Executors
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}

object Boot extends App {

  val myMapThreadLocal = new ThreadLocal[Map[String, String]]

  def assert(b: Boolean, msg: String = "") = {
    if (!b) {
      System.err.println(s"!!!! assertion failed, $msg")
      throw new Exception("Assertion failed")
    }
  }

  println("test started")

  println()
  println("Test1")
  println()

  Future
    .successful(42)
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

  Thread.sleep(100)

  println()
  println("Test2")
  println()

  val counter = new ThreadLocal[Int]

  val promise = Promise[Int]()
  val future = promise.future.map(_ => counter.set(0))

  future
    .map { _ =>
      counter.set(counter.get() + 1)
      println(s"[f1] state setup to 1")
    }
    .map { _ =>
      assert(counter.get() == 1, "[f1]")
      println(s"[f1] state is ${counter.get()}")

      counter.set(counter.get() + 1)
      println(s"[f1] state setup to 2")
    }
    .map { _ =>
      assert(counter.get() == 2, "[f1]")
      println(s"[f1] state is ${counter.get()}")
    }
    .map { _ =>
      assert(counter.get() == 2, "[f1]")
      println(s"[f2] state is ${counter.get()}")
    }

  future
    .map { _ =>
      counter.set(counter.get() + 3)
      println(s"[f2] state setup to 3")
    }
    .map { _ =>
      assert(counter.get() == 3, "[f2]")
      println(s"[f2] state is ${counter.get()}")

      counter.set(counter.get() + 3)
      println(s"[f2] state setup to 6")
    }
    .map { _ =>
      assert(counter.get() == 6, "[f2]")
      println(s"[f2] state is ${counter.get()}")
    }
    .map { _ =>
      assert(counter.get() == 6, "[f2]")
      println(s"[f2] state is ${counter.get()}")
    }

  promise.success(42)
  Thread.sleep(100)

  println()
  println("Test3")
  println()

  val counter2 = new ThreadLocal[Int]
  val ec1 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  val ec2 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  val ec3 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  val ec4 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  Future
    .successful(Seq(42))
    .map { _ =>
      counter2.set(counter2.get() + 1)
      println(s"state setup to 1")
    }(ec1)
    .map { _ =>
      assert(counter2.get() == 1)
      println(s"state is ${counter2.get()}")

      counter2.set(counter2.get() + 1)
      println(s"state setup to 2")
    }(ec2)
    .map { _ =>
      assert(counter2.get() == 2)
      println(s"state is ${counter2.get()}")
    }(ec3)
    .map { _ =>
      assert(counter2.get() == 2)
      println(s"state is ${counter2.get()}")
    }(ec4)

  Thread.sleep(2_000)

  {
    println()
    println("Test4")
    println()

    val counter2 = new ThreadLocal[Int]
    val ec1 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val ec2 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val ec3 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val ec4 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

    Future
      .successful(Seq(42))
      .flatMap { _ =>
        counter2.set(counter2.get() + 1)
        Future(println(s"state setup to 1"))
      }(ec1)
      .flatMap { _ =>
        assert(counter2.get() == 1)
        println(s"state is ${counter2.get()}")

        counter2.set(counter2.get() + 1)
        Future(println(s"state setup to 2"))
      }(ec2)
      .flatMap { _ =>
        assert(counter2.get() == 2)
        Future(println(s"state is ${counter2.get()}"))
      }(ec3)
      .flatMap { _ =>
        Future(assert(counter2.get() == 2))
        //        (println(s"state is ${counter2.get()}"))
      }(ec4)
    //      .futureValue

  }

  Thread.sleep(2_000)
  println("test finished")

  System.exit(0)

//  org.tayvs.util.Promise.fromTry()
}
