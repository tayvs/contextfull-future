package org.tayvs.util

class ContextHolder(private var initContext: Map[String, Any]) {

  private[util] def getContext: Map[String, Any] = initContext

  private[util] def addContext(
                                key: String,
                                value: Any
                              ): Unit = {
    val newContext = getContext + (key -> value)
    initContext = newContext
  }

  private[util] def copy = new ContextHolder(initContext)

  private[util] def inject = ContextHolder.contextHolder.set(this)

  private[util] def clean = ContextHolder.contextHolder.remove()

  override def toString: String = s"ContextHolder($initContext)"
}

object ContextHolder {

  def apply(): ContextHolder = empty

  val empty = new ContextHolder(Map.empty)

  private[util] val contextHolder = new ThreadLocal[ContextHolder]()

  def readContext: ContextHolder = contextHolder.get()

  def getContext: Map[String, Any] = contextHolder.get().getContext

  def addContext(
                  key: String,
                  value: Any
                ): Unit = {
    contextHolder.get().addContext(key, value)
  }

}