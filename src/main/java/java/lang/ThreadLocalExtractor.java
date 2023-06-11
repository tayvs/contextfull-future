//package java.lang;
//
//public class ThreadLocalExtractor {
//
//
//    public static Object getThreadLocals(Thread t) {
//        return t.threadLocals;
//    }
//
//    public static void setThreadLocals(Thread t, Object tl) throws Exception {
//        if (tl instanceof ThreadLocal.ThreadLocalMap) t.threadLocals = (ThreadLocal.ThreadLocalMap) tl;
//        else throw new Exception("tl should be instacne of ThreadLocalMap");
//    }
//
//}
