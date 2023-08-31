object Boot2 extends App {

  println("#" * 42)
  println("# Future type is " + scala.concurrent.Future.successful(42).getClass.getName)

}
