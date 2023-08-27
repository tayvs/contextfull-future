import java.util.concurrent.Executors

object ThreadLocalMapExtractor extends App {


  val s: ThreadLocal[String] = new ThreadLocal[String]()

  val threadClazz = classOf[Thread]
  val threadLocalsField = threadClazz.getDeclaredField("threadLocals")
  threadLocalsField.setAccessible(true)
  println(threadLocalsField)
//  f

//  clazz.getDeclaredFields.foreach(println(_))

  val threadLocalClazz = Class.forName("java.lang.ThreadLocal$ThreadLocalMap")
//  println(threadLocalClazz.getDeclaredMethods.mkString(", "))
  val createInheritedMapMethod = threadLocalClazz.getDeclaredConstructor(threadLocalClazz)
  createInheritedMapMethod.setAccessible(true)
  val createWithFirstElementMapMethod = threadLocalClazz.getDeclaredConstructor(Class.forName("java.lang.ThreadLocal"), classOf[Object])
  createWithFirstElementMapMethod.setAccessible(true)
  println(threadLocalClazz.getDeclaredMethods.filter(_.getName == "set").mkString(", "))
  val setThreadLocalMapMethod = threadLocalClazz.getDeclaredMethod("set", Class.forName("java.lang.ThreadLocal"), classOf[Object])

  val getTableThreadMapMethod = threadLocalClazz.getDeclaredField("table")
  getTableThreadMapMethod.setAccessible(true)

  val entityClazz = Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry")
  println(entityClazz.getMethods.mkString(", "))
  println(entityClazz.getMethods.filter(_.getName == "get").mkString(", "))
  val entityKetMethod = entityClazz.getMethod("get")
  entityKetMethod.setAccessible(true)
  val entityValueMethod = entityClazz.getDeclaredField("value")
  entityValueMethod.setAccessible(true)

  // TODO: reimplement copy. Right now InheritableThreadLocal used for test porpuses
  def copyThreadLocalMap(tlm: Object) = {
    if (tlm == null) tlm
    else {
      val entityArr = getTableThreadMapMethod.get(tlm).asInstanceOf[Array[_]]
      val notNullEntities = entityArr.filter(_ != null)
      val tlmCopy = if (notNullEntities.nonEmpty) {
        val head = notNullEntities.head
        val tail = notNullEntities.tail
        val newInstance = createWithFirstElementMapMethod.newInstance(entityKetMethod.invoke(head), entityValueMethod.get(head))
        tail.foreach(entry => setThreadLocalMapMethod.invoke(newInstance, entityKetMethod.invoke(entry), entityValueMethod.get(entry)))
        newInstance
      } else null
//      val copy = createInheritedMapMethod.newInstance(tlm)
      println(s"tlm copy $tlm")
      tlmCopy
    }
  }

  def getThreadLocalMap(t: Thread) = {
    val clazz = t.getClass
    clazz.getDeclaredFields.foreach(println(_))
    println("#" * 50)
    println(clazz.getDeclaredField("threadLocals"))
    val f = clazz.getDeclaredField("threadLocals")
    f.setAccessible(true)
    val threadLocals = f.get(t)
    println(threadLocals)
//    println(f.get(t).asInstanceOf[ThreadLocals])
  }

  def getThreadLocalsField(t: Thread) = {
    val clazz = t.getClass
//    clazz.getDeclaredFields.foreach(println(_))
//    println("#" * 50)
//    println(clazz.getDeclaredField("threadLocals"))
    val f = clazz.getDeclaredField("threadLocals")
    f.setAccessible(true)
    println(f)
    f
  }

  def reassignThreadLocals(from: Thread, to: Thread) = {
//    val clazz = from.getClass
//    clazz.getDeclaredFields.foreach(println(_))
//    println("#" * 50)
//    println(clazz.getDeclaredField("threadLocals"))
//    val f = clazz.getDeclaredField("threadLocals")
//    f.setAccessible(true)
//    val threadLocals = f.get(from)
//    println(threadLocals)
//    val fromTLField = getThreadLocalsField(from)
//    val toTLField = getThreadLocalsField(to)

    val start = System.nanoTime()
//    val startNano = System.nanoTime()
    //TODO: need to make copy, otherwise they share same link and updating values in one thread automativally change value in another
    threadLocalsField.set(to, copyThreadLocalMap(threadLocalsField.get(from)))
    val end = System.nanoTime()

    println(end - start)
    //    println(f.get(t).asInstanceOf[ThreadLocals])
  }

//  getThreadLocalMap(Thread.currentThread())
//  println(java.lang.ThreadLocalsHolder.getThreadLocalMap(Thread.currentThread()))

  class ThreadWithS extends Thread {
    def getS = s.get()
    def setS(str: String) = s.set(str)
  }

  def getS = s.get()

  def setS(str: String): Unit = s.set(str)

  val e1 = Executors.newSingleThreadExecutor()
  val e2 = Executors.newSingleThreadExecutor()

//  e1.submit()
//
  val t1 = new ThreadWithS
  val t2 = new ThreadWithS

  println(e1.submit(() => getS).get())
  println(e2.submit(() => getS).get())
  println("### both should be nulls")

  ////////////////////////////////

  e1.submit((() => setS("ssss")) : Runnable).get

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
