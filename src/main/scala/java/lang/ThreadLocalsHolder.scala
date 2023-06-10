package java.lang

// TODO: runtime error because of java.lang package. Mb try to write this staff in java??
class ThreadLocalsHolder private(state: ThreadLocal.ThreadLocalMap) {
  def injectState(): Unit = Thread.currentThread().threadLocals = state

  def clean(): Unit = Thread.currentThread().threadLocals = null

  def copy(): ThreadLocalsHolder = new ThreadLocalsHolder(ThreadLocal.createInheritedMap(state))
}

object ThreadLocalsHolder {
  def apply(): ThreadLocalsHolder = new ThreadLocalsHolder(Thread.currentThread().threadLocals)

  def apply[T](threadLocal: ThreadLocal[T], value: T) = new ThreadLocalsHolder(new ThreadLocal.ThreadLocalMap(threadLocal, value))
}