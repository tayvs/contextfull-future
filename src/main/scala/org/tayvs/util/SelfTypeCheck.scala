package org.tayvs.util

object SelfTypeCheck extends App {

  abstract class OriginalFuture {
    def method(): OriginalFuture = this
  }

  class OriginalFutureImpl extends OriginalFuture {
    override def method(): OriginalFuture = this
  }

  trait NewFuture extends OriginalFuture { self =>
    override def method(): this.type = this
  }

  class NewFutureImpl extends NewFuture {
    override def method(): this.type = this
  }


  println((new OriginalFutureImpl).method().getClass.getName)
  println((new NewFutureImpl).method().getClass.getName)

}
