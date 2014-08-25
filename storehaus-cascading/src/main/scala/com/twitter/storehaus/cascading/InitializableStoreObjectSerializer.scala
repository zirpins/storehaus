package com.twitter.storehaus.cascading

import org.apache.hadoop.mapred.JobConf
import com.twitter.util.Try
import com.twitter.storehaus.{ ReadableStore, WritableStore }
import org.apache.hadoop.io.Writable
import com.twitter.storehaus.Store
import scala.reflect.runtime.universe._

/**
 * read and write the name of the object of StorehausCascadingInitializer.
 * Cascading planner seems to be single threaded so we can pass the id 
 * while performing source/sinkConfInit.  
 */
object InitializableStoreObjectSerializer {
  val STORE_TAP_ID = "com.twitter.storehaus.cascading.currenttapid"
  val STORE_CLASS_NAME_READ = "com.twitter.storehaus.cascading.readstoreclass."
  val STORE_CLASS_NAME_WRITE = "com.twitter.storehaus.cascading.writestoreclass."
  
  def setReadableStoreClass[K, V](conf: JobConf, tapid: String, storeSerializer: StorehausCascadingInitializer[K, V]) = {
    if (conf.get(STORE_CLASS_NAME_READ + tapid) == null) conf.set(STORE_CLASS_NAME_READ + tapid, storeSerializer.getClass.getName)
  }
  def setWritableStoreClass[K, V](conf: JobConf, tapid: String, storeSerializer: StorehausCascadingInitializer[K, V]) = {
    if (conf.get(STORE_CLASS_NAME_WRITE + tapid) == null) conf.set(STORE_CLASS_NAME_WRITE + tapid, storeSerializer.getClass.getName)
  }
  def getReadableStore[K, V](conf: JobConf, tapid: String): Try[ReadableStore[K, V]] = {
    Try {
      invokeReflectively("getReadableStore", conf.get(STORE_CLASS_NAME_READ + tapid), conf).get
    }
  }
  def getWritableStore[K, V](conf: JobConf, tapid: String): Try[WritableStore[K, V]] = {
    Try {
      (invokeReflectively[WritableStore[K, V]]("getWritableStore", conf.get(STORE_CLASS_NAME_WRITE + tapid), conf)).get
    }
  }
  def getReadableStoreIntializer[K, V](conf: JobConf, tapid: String): Try[StorehausCascadingInitializer[K, V]] = {
    Try {
      getReflectiveObject(conf.get(STORE_CLASS_NAME_READ + tapid)).asInstanceOf[StorehausCascadingInitializer[K, V]]
    }
  }
  def setTapId(conf: JobConf, tapid: String) = {
    conf.set(STORE_TAP_ID, tapid)
  }
  def getTapId(conf: JobConf): String = {
    conf.get(STORE_TAP_ID)
  }
  
  def getReflectiveObject(objectName: String) = {
    val loadermirror = runtimeMirror(getClass.getClassLoader)
	val module = loadermirror.staticModule(objectName)
	loadermirror.reflectModule(module).instance    
  }
  
  def invokeReflectively[T](methodName: String, objectName: String, conf: JobConf): Option[T] = {
	// If i use scala reflection, i always get a feeling of misunderstanding the whole universe 
	val loadermirror = runtimeMirror(getClass.getClassLoader)
    val instancemirror = loadermirror.reflect(getReflectiveObject(objectName))
	val method = instancemirror.symbol.typeSignature.member(newTermName(methodName)).asMethod
	instancemirror.reflectMethod(method)(conf).asInstanceOf[Option[T]]
  }
}