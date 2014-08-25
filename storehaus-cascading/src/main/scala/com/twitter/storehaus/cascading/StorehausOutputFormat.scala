/*
 * Copyright 2014 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.storehaus.cascading

import org.apache.hadoop.mapred.{ OutputFormat, JobConf, RecordWriter, Reporter }
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.util.Progressable
import com.twitter.storehaus.WritableStore

/**
 * StorehausOuputFormat using a WriteableStore
 */
class StorehausOutputFormat[K, V] extends OutputFormat[K, V] {  

  /**
   * Simple StorehausRecordWriter delegating method-calls to store 
   */
  class StorehausRecordWriter(val conf: JobConf, val store: WritableStore[K, Option[V]]) extends RecordWriter[K, V] {  
    def write(key: K, value: V) = store.put((key, Some(value)))
    def close(reporter: Reporter) = {
      store.close()
      reporter.setStatus("Completed Writing. Closed Store.")
    }
  }

  /**
   * initializes a WritableStore out of serialized JobConf parameters and returns a RecordWriter 
   * putting into that store.
   */
  override def getRecordWriter(fs: FileSystem, conf: JobConf, name: String, progress: Progressable): RecordWriter[K, V] = {
    val tapid = InitializableStoreObjectSerializer.getTapId(conf)
    val store = InitializableStoreObjectSerializer.getWritableStore[K, Option[V]](conf, tapid).get  
    new StorehausRecordWriter(conf, store)
  }
  
  override def checkOutputSpecs(fs: FileSystem, conf: JobConf) = {}

}