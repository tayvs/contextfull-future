import org.scalatest.concurrent.Futures
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.funsuite.{AnyFunSuite, AsyncFunSuite}
import org.scalatest.matchers.should.Matchers
import org.tayvs.util.{Future, Promise}

import java.util.concurrent.{Executor, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class ContextfullFuture extends AsyncFunSuite with Matchers with Futures {

  test("Future.successful with no init Thead Local state should init it in chain and propagate further through 'map' chains on global executionContext") {
    val myMapThreadLocal = new ThreadLocal[Map[String, String]]
    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null: Executor)

    Future
      .successful(42)
      .map { _ => myMapThreadLocal.set(Map("cake" -> "is a lie")) }(executionContext)
      .map { _ =>
        myMapThreadLocal.get() shouldBe Map("cake" -> "is a lie")

        val newState = Map("cake" -> "is a lie", "Jo" -> "Jo")
        myMapThreadLocal.set(newState)
      }(executionContext)
      .map { _ => myMapThreadLocal.get() shouldBe Map("cake" -> "is a lie", "Jo" -> "Jo") }(executionContext)
//      .futureValue
  }

  test("Future.successful with no init Thead Local state should init it in chain and propagate further through 'map' chains on Single Tread executionContext") {
    val myMapThreadLocal = new ThreadLocal[Map[String, String]]
    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

    Future
      .successful(42)
      .map { _ => myMapThreadLocal.set(Map("cake" -> "is a lie")) }(executionContext)
      .map { _ =>
        myMapThreadLocal.get() shouldBe Map("cake" -> "is a lie")

        val newState = Map("cake" -> "is a lie", "Jo" -> "Jo")
        myMapThreadLocal.set(newState)
      }(executionContext)
      .map { _ => myMapThreadLocal.get() shouldBe Map("cake" -> "is a lie", "Jo" -> "Jo") }(executionContext)
//      .futureValue
  }


  test("Future from Promise with no init Thead Local state init state before future flow split and each flow should has own copy that modify independently on global executionContext") {
    val counter = new ThreadLocal[Int]
    implicit val executionContext = ExecutionContext.fromExecutor(null: Executor)

    val promise = Promise[Int]()
    val future = promise.future.map(_ => counter.set(0))(executionContext)

    val f1 = future
      .map { _ =>
        counter.set(counter.get() + 1)
        println(s"[f1] ${Thread.currentThread()} state setup to 1")
      }(executionContext)
      .map { _ =>
        assert(counter.get() == 1, "[f1]")
        println(s"[f1] state is ${counter.get()}")

        counter.set(counter.get() + 1)
        println(s"[f1] state setup to 2")
      }(executionContext)
      .map { _ =>
        assert(counter.get() == 2, "[f1]")
        println(s"[f1] state is ${counter.get()}")
      }(executionContext)
      .map { _ =>
        assert(counter.get() == 2, "[f1]")
        //        println(s"[f2] state is ${counter.get()}")
        counter.get()
      }(executionContext)

    val f2 = future
      .map { _ =>
        counter.set(counter.get() + 3)
        println(s"[f2] ${Thread.currentThread()} state setup to 3")
      }(executionContext)
      .map { _ =>
        assert(counter.get() == 3, "[f2]")
        println(s"[f2] state is ${counter.get()}")

        counter.set(counter.get() + 3)
        println(s"[f2] state setup to 6")
      }(executionContext)
      .map { _ =>
        assert(counter.get() == 6, "[f2]")
        println(s"[f2] state is ${counter.get()}")
      }(executionContext)
      .map { _ =>
        assert(counter.get() == 6, "[f2]")
        //        println(s"[f2] state is ${counter.get()}")
        counter.get()
      }(executionContext)

    promise.success(42)

    f1.zipWith(f2) { (v1, v2) =>
      v1 shouldBe 2
      v2 shouldBe 6
    }(executionContext)
//      .futureValue
  }

  test("Future from Promise with no init Thead Local state init state before future flow split and each flow should has own copy that modify independently on single Thread executionContext") {
    val counter = new ThreadLocal[Int]
    implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

    val promise = Promise[Int]()
    val future = promise.future.map(_ => counter.set(0))

    val f1 = future
      .map { _ =>
        counter.set(counter.get() + 1)
        println(s"[f1] ${Thread.currentThread()} state setup to 1")
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
        //        println(s"[f2] state is ${counter.get()}")
        counter.get()
      }

    val f2 = future
      .map { _ =>
        counter.set(counter.get() + 3)
        println(s"[f2] ${Thread.currentThread()} state setup to 3")
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
        //        println(s"[f2] state is ${counter.get()}")
        counter.get()
      }

    promise.success(42)

    f1.zipWith(f2) { (v1, v2) =>
      v1 shouldBe 2
      v2 shouldBe 6
    }
//      .futureValue
  }

  test("Future.successful should propagate ThreadLocal state though 'map' through different executionContext") {
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
//        println(s"state is ${counter2.get()}")
      }(ec4)
//      .futureValue
  }

  test("Future.successful should propagate ThreadLocal state though 'flatMap' with Future.successful through different executionContext") {
    val counter2 = new ThreadLocal[Int]
    val ec1 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val ec2 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val ec3 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    val ec4 = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

    Future
      .successful(Seq(42))
      .flatMap { _ =>
        counter2.set(counter2.get() + 1)
        Future.successful(println(s"state setup to 1"))
      }(ec1)
      .flatMap { _ =>
        assert(counter2.get() == 1)
        println(s"state is ${counter2.get()}")

        counter2.set(counter2.get() + 1)
        Future.successful(println(s"state setup to 2"))
      }(ec2)
      .flatMap { _ =>
        assert(counter2.get() == 2)
        Future.successful(println(s"state is ${counter2.get()}"))
      }(ec3)
      .flatMap { _ =>
        Future.successful(assert(counter2.get() == 2))
//        (println(s"state is ${counter2.get()}"))
      }(ec4)
//      .futureValue
  }

  // TODO: Future.apply do not read the context
  test("Future.successful should propagate ThreadLocal state though 'flatMap' with Future with delay through different executionContext") {
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


}
