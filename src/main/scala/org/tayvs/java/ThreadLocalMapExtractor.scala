package org.tayvs.java

import java.util.concurrent.Executors

object ThreadLocalMapExtractor /*extends App*/ {

  class ThreadLocalMapWrapper private(val threadLocalMap: Object) extends AnyVal {
    import ThreadLocalMapWrapper._

    def set(key: AnyRef, value: AnyRef) = {
      setThreadLocalMapMethod.invoke(threadLocalMap, key, value)
    }

    def set(entryWrapper: EntryWrapper) = {
      setThreadLocalMapMethod.invoke(threadLocalMap, entryWrapper.key, entryWrapper.value)
    }

    def value = threadLocalMap

  }

  object ThreadLocalMapWrapper {

    // TODO types for parameters
    @inline def apply(key: AnyRef, value: AnyRef): ThreadLocalMapWrapper = {
      new ThreadLocalMapWrapper(createWithFirstElementMapMethod.newInstance(key, value).asInstanceOf[Object])
    }

    @inline def apply(entryWrapper: EntryWrapper): ThreadLocalMapWrapper = ThreadLocalMapWrapper(entryWrapper.key, entryWrapper.value)


    val threadLocalClazz = classOf[ThreadLocal[_]]
    val objectClazz = classOf[Object]
    val threadLocalMapClazz = Class.forName("java.lang.ThreadLocal$ThreadLocalMap")
    val createInheritedMapMethod = threadLocalMapClazz.getDeclaredConstructor(threadLocalMapClazz)
    createInheritedMapMethod.setAccessible(true)
    val createWithFirstElementMapMethod = threadLocalMapClazz.getDeclaredConstructor(threadLocalClazz, objectClazz)
    createWithFirstElementMapMethod.setAccessible(true)
    val setThreadLocalMapMethod = threadLocalMapClazz.getDeclaredMethod("set", threadLocalClazz, objectClazz)
    setThreadLocalMapMethod.setAccessible(true)

    val getTableThreadMapMethod = threadLocalMapClazz.getDeclaredField("table")
    getTableThreadMapMethod.setAccessible(true)
  }

  class EntryWrapper(val entry: Any) extends AnyVal {
    import EntryWrapper._
    def key = entityKeyMethod.invoke(entry)
    def value = entityValueMethod.get(entry)
  }

  object EntryWrapper {

    val entityClazz = Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry")
    val entityKeyMethod = entityClazz.getMethod("get")
    entityKeyMethod.setAccessible(true)
    val entityValueMethod = entityClazz.getDeclaredField("value")
    entityValueMethod.setAccessible(true)
  }


  val s: ThreadLocal[String] = new ThreadLocal[String]()

  val threadClazz = classOf[Thread]
  val threadLocalsField = threadClazz.getDeclaredField("threadLocals")
  threadLocalsField.setAccessible(true)


  val threadLocalClazz = Class.forName("java.lang.ThreadLocal$ThreadLocalMap")
  val getTableThreadMapMethod = threadLocalClazz.getDeclaredField("table")
  getTableThreadMapMethod.setAccessible(true)

  // TODO: reimplement copy. Right now InheritableThreadLocal used for test porpuses
  def copyThreadLocalMap(tlm: Object): Object = {
    val start = System.nanoTime()
    if (tlm == null) tlm
    else {
      val entityArr = getTableThreadMapMethod.get(tlm).asInstanceOf[Array[_]]
      val notNullEntities = entityArr.filter(_ != null)
      val tlmCopy = /*if (notNullEntities.nonEmpty)*/ {
        val head = new EntryWrapper(notNullEntities.head)
        val tail = notNullEntities.tail
        //        val newInstance =  createWithFirstElementMapMethod.newInstance(head.key, head.value)
        val newInstance: ThreadLocalMapWrapper = ThreadLocalMapWrapper(head)
        //        tail.map(new EntryWrapper(_)).foreach(entry => setThreadLocalMapMethod.invoke(newInstance, entry.key, entry.value))
        tail.map(new EntryWrapper(_)).foreach(entry => newInstance.set(entry))
        newInstance.value
      } // else null
      //      val copy = createInheritedMapMethod.newInstance(tlm)
      val end = System.nanoTime()
      println(s"copyThreadLocalMap that is $tlm takes ${end - start} ns")
      tlmCopy
    }
  }

  def reassignThreadLocals(from: Thread, to: Thread) = {
    val start = System.nanoTime()
    //    val startNano = System.nanoTime()
    //TODO: need to make copy, otherwise they share same link and updating values in one thread automativally change value in another
    val tlmToCopy = threadLocalsField.get(from)
    val tlmCopy = time(copyThreadLocalMap(tlmToCopy), t => s"ThreadLocalMap copy outside takes $t ns")
    threadLocalsField.set(to, tlmCopy)
    val end = System.nanoTime()

    println(s"threadLocalMap reassigning takes ${end - start} ns")
  }

  def getThreadLocalMap(from: Thread): Object = copyThreadLocalMap(threadLocalsField.get(from))

  def assignThreadLocalMap(to: Thread, tlm: Any): Unit = threadLocalsField.set(to, tlm)

//  class ThreadWithS extends Thread {
//    def getS = s.get()
//    def setS(str: String) = s.set(str)
//  }
//
//  def getS = s.get()
//
//  def setS(str: String): Unit = s.set(str)
//
//
//  time(classOf[ThreadLocal[_]], t => s"ThreadLocal classOf takes $t")
//  time(Class.forName("java.lang.ThreadLocal"), t => s"ThreadLocal classForName takes $t")
//
//  val e1 = Executors.newSingleThreadExecutor()
//  val e2 = Executors.newSingleThreadExecutor()
//
//  //  e1.submit()
//  //
//  val t1 = new ThreadWithS
//  val t2 = new ThreadWithS
//
//  println(e1.submit(() => getS).get())
//  println(e2.submit(() => getS).get())
//  println("### both should be nulls")
//
//  ////////////////////////////////
//
//  e1.submit((() => setS("ssss")) : Runnable).get
//
//  println(e1.submit(() => getS).get())
//  println(e2.submit(() => getS).get())
//  println("### first ssss second null")
//
//  //////////////////////////////////
//
//  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)
//
//  println(e1.submit(() => getS).get())
//  println(e2.submit(() => getS).get())
//  println("### first ssss second ssss")
//
//  /////////////////////////////////
//
//  e1.submit((() => setS("wwwww")): Runnable).get
//
//  println(e1.submit(() => getS).get())
//  println(e2.submit(() => getS).get())
//  println("### first wwww second ssss")
//
//  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)
//
//  println(e1.submit(() => getS).get())
//  println(e2.submit(() => getS).get())
//
//  println("### first wwww second wwww")
//
//  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)
//  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)
//  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)
//  reassignThreadLocals(e1.submit(() => Thread.currentThread()).get, e2.submit(() => Thread.currentThread()).get)
//
//  e1.shutdown()
//  e2.shutdown()
//
  def time(f: => Any, logMsg: Long => String) = {
    val start = System.nanoTime()
    val fRes = f
    val end = System.nanoTime()
    println(logMsg(end - start))
    fRes
  }
//
//  (1 to 10).foreach(_ => time(println("someString"), t => s"'someString' println takes $t"))
//  (1 to 10).foreach(_ => time(println(s"someString ${42 - 2}"), t => s"'someString' println with string interpolation takes $t"))

}
