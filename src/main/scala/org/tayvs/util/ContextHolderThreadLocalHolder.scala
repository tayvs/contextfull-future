package org.tayvs.util

//class ContextHolder(private var initContext: Map[String, Any]) {
class ContextHolderThreadLocalHolder(private var initContext: ThreadLocalsHolder) {

//  private[util] def getContext: Map[String, Any] = initContext
//
//  private[util] def addContext(
//                                key: String,
//                                value: Any
//                              ): Unit = {
//    val newContext = getContext + (key -> value)
//    initContext = newContext
//  }

  private[util] def copy = new ContextHolderThreadLocalHolder(initContext.copy())
  private[util] def inject = initContext.injectState()
  private[util] def clean = initContext.clean()
}

object ContextHolderThreadLocalHolder {

  //Extract all ThreadLocals for further propagating through Future
  // TODO: is it really safe? What about branching?? Looks like we just passing one instance of ThreadLocals
  // ThreadLocal was not designed for concurrent use
  // make sure that {{copy}} method made right copy???
  def apply() = new ContextHolderThreadLocalHolder(ThreadLocalsHolder())

//  val empty = new ContextHolder(Map.empty)
//
//  private[util] val contextHolder = new ThreadLocal[ContextHolder]()
//
//  def getContext: Map[String, Any] = contextHolder.get().getContext
//
//  def addContext(
//                  key: String,
//                  value: Any
//                ): Unit = {
//    contextHolder.get().addContext(key, value)
//  }

}