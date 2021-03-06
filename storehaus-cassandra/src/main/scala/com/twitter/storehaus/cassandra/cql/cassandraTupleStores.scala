/*
 * Copyright 2014 SEEBURGER AG
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
package com.twitter.storehaus.cassandra.cql

import com.datastax.driver.core.Row
import com.twitter.concurrent.Spool
import com.twitter.storehaus.{IterableStore, ReadableStore, QueryableStore, Store}
import com.twitter.storehaus.cassandra.cql.cascading.CassandraCascadingRowMatcher
import com.twitter.util.{Future, FutureTransformer, Return, Throw}
import shapeless.Tuples._
import shapeless._

/**
 * AbstractCQLCassandraCompositeStore-wrapper for Tuples to HList. This makes it 
 * easy to use this store with tuples, which can in turn be serialized easily.
 */
class CassandraTupleStore[RKT <: Product, CKT <: Product, V, RK <: HList, CK <: HList, RS <: HList, CS <: HList]
		(store: AbstractCQLCassandraCompositeStore[RK, CK, V, RS, CS], paramToPreventWritingDownTypes: (RKT, CKT))
		(implicit ev1: HListerAux[RKT, RK],
		    ev2: HListerAux[CKT, CK],
		    ev3: TuplerAux[RK, RKT],
		    ev4: TuplerAux[CK, CKT])
	extends Store[(RKT, CKT), V]  
    with CassandraCascadingRowMatcher[(RKT, CKT), V] 
    with QueryableStore[String, ((RKT, CKT), V)]
    with IterableStore[(RKT, CKT), V] {

  override def get(k: (RKT, CKT)): Future[Option[V]] = {
    store.get((k._1.hlisted, k._2.hlisted))
  }
  
  override def multiPut[K1 <: (RKT, CKT)](kvs: Map[K1, Option[V]]): Map[K1, Future[Unit]] = {
    val resultMap = store.multiPut(kvs.map(kv => ((kv._1._1.hlisted, kv._1._2.hlisted), kv._2)))
    resultMap.map(kv => ((kv._1._1.tupled, kv._1._2.tupled).asInstanceOf[K1], kv._2))
  }
  
  override def getKeyValueFromRow(row: Row): ((RKT, CKT), V) = {
    val (keys, value) = store.getKeyValueFromRow(row)
    ((keys._1.tupled, keys._2.tupled), value)
  }
  
  override def getColumnNamesString: String = store.getColumnNamesString
  
  override def queryable: ReadableStore[String, Seq[((RKT, CKT), V)]] = new Object with ReadableStore[String, Seq[((RKT, CKT), V)]] {
    override def get(whereCondition: String): Future[Option[Seq[((RKT, CKT), V)]]] = store.queryable.get(whereCondition).transformedBy {
      new FutureTransformer[Option[Seq[((RK, CK), V)]], Option[Seq[((RKT, CKT), V)]]] {
        override def map(value: Option[Seq[((RK, CK), V)]]): Option[Seq[((RKT, CKT), V)]] = value match {
          case Some(seq) => Some(seq.view.map(res => ((res._1._1.tupled, res._1._2.tupled).asInstanceOf[(RKT, CKT)], res._2)))
          case _ => None
        }
      }
    }
  }
  
  override def getAll: Future[Spool[((RKT, CKT), V)]] = queryable.get("").transform {
    case Throw(y) => Future.exception(y)
    case Return(x) => IterableStore.iteratorToSpool(x.getOrElse(Seq[((RKT, CKT), V)]()).view.iterator)   
  }
}

/**
 * Muti-valued version of CassandraTupleStore
 */
class CassandraTupleMultiValueStore[RKT <: Product, CKT <: Product, V <: Product, RK <: HList, CK <: HList, VL <: HList, RS <: HList, CS <: HList, VS <: HList]
		(store: CQLCassandraMultivalueStore[RK, CK, VL, RS, CS, VS], 
		    paramToPreventWritingDownTypes1: (RKT, CKT),
		    paramToPreventWritingDownTypes2: V)
		(implicit ev1: HListerAux[RKT, RK],
		    ev2: HListerAux[CKT, CK],
		    ev3: HListerAux[V, VL],
		    ev4: TuplerAux[RK, RKT],
		    ev5: TuplerAux[CK, CKT],
		    ev6: TuplerAux[VL, V])
	extends Store[(RKT, CKT), V]  
    with CassandraCascadingRowMatcher[(RKT, CKT), V] 
    with QueryableStore[String, ((RKT, CKT), V)]
    with IterableStore[(RKT, CKT), V] {

  override def get(k: (RKT, CKT)): Future[Option[V]] = {
    store.get((k._1.hlisted, k._2.hlisted)).flatMap(opt => Future(opt.map(res => res.tupled)))
  }
  
  override def multiPut[K1 <: (RKT, CKT)](kvs: Map[K1, Option[V]]): Map[K1, Future[Unit]] = {
    val resultMap = store.multiPut(kvs.map(kv => ((kv._1._1.hlisted, kv._1._2.hlisted), kv._2.map(_.hlisted))))
    resultMap.map(kv => ((kv._1._1.tupled, kv._1._2.tupled).asInstanceOf[K1], kv._2))
  }
  
  override def getKeyValueFromRow(row: Row): ((RKT, CKT), V) = {
    val (keys, value) = store.getKeyValueFromRow(row)
    ((keys._1.tupled, keys._2.tupled), value.tupled)
  }
  
  override def getColumnNamesString: String = store.getColumnNamesString
  
  override def queryable: ReadableStore[String, Seq[((RKT, CKT), V)]] = new Object with ReadableStore[String, Seq[((RKT, CKT), V)]] {
    override def get(whereCondition: String): Future[Option[Seq[((RKT, CKT), V)]]] = store.queryable.get(whereCondition).transformedBy {
      new FutureTransformer[Option[Seq[((RK, CK), VL)]], Option[Seq[((RKT, CKT), V)]]] {
        override def map(value: Option[Seq[((RK, CK), VL)]]): Option[Seq[((RKT, CKT), V)]] = value match {
          case Some(seq) => Some(seq.view.map(res => ((res._1._1.tupled, res._1._2.tupled), res._2.tupled)))
          case _ => None
        }
      }
    }
  }
  
  override def getAll: Future[Spool[((RKT, CKT), V)]] = queryable.get("").transform {
    case Throw(y) => Future.exception(y)
    case Return(x) => IterableStore.iteratorToSpool(x.getOrElse(Seq[((RKT, CKT), V)]()).view.iterator)   
  }
}

