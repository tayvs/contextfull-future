//package java.lang
//
//// TODO: runtime error because of java.lang package. Mb try to write this staff in java??
//class ThreadLocalsHolder private(state: ThreadLocal.ThreadLocalMap) {
//  def injectState(): Unit = Thread.currentThread().threadLocals = state
//
//  def clean(): Unit = Thread.currentThread().threadLocals = null
//
//  def copy(): ThreadLocalsHolder = new ThreadLocalsHolder(ThreadLocalMapExtractor.copyThreadLocalMap(state))
//}
//
//object ThreadLocalsHolder {
//
//  def getThreadLocalMap(t: Thread) = {
//    ThreadLocalMapExtractor.getThreadLocalMap(Thread.currentThread())
//  }
//
//  def apply(): ThreadLocalsHolder = new ThreadLocalsHolder(/*ThreadLocal.getMap(Thread.currentThread()) */ Thread.currentThread().threadLocals)
//
//  def apply[T](threadLocal: ThreadLocal[T], value: T) = new ThreadLocalsHolder(new ThreadLocal.ThreadLocalMap(threadLocal, value))
//}