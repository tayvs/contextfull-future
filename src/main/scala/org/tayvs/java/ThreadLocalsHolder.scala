package org.tayvs.java

// TODO: runtime error because of java.lang package. Mb try to write this staff in java??
class ThreadLocalsHolder private(state: Object) {
  def injectState(): Unit = ThreadLocalMapExtractor.assignThreadLocalMap(Thread.currentThread(), state)

  def clean(): Unit = ThreadLocalMapExtractor.assignThreadLocalMap(Thread.currentThread(), null)

  def copy(): ThreadLocalsHolder = new ThreadLocalsHolder(ThreadLocalMapExtractor.copyThreadLocalMap(state))
}

object ThreadLocalsHolder {

  def getThreadLocalMap(t: Thread) = {
    ThreadLocalMapExtractor.getThreadLocalMap(t)
  }

  def apply(): ThreadLocalsHolder = new ThreadLocalsHolder(getThreadLocalMap(Thread.currentThread()))

//  def apply[T](threadLocal: ThreadLocal[T], value: T) = new ThreadLocalsHolder(new ThreadLocal.ThreadLocalMap(threadLocal, value))
}