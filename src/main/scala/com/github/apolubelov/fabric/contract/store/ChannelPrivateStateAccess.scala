package com.github.apolubelov.fabric.contract.store

import org.hyperledger.fabric.shim.ChaincodeStub
import org.hyperledger.fabric.shim.ledger.{CompositeKey, KeyValue, QueryResultsIterator}

/*
 * @author Alexey Polubelov
 */
class ChannelPrivateStateAccess(
    api: ChaincodeStub,
    collection: String
) extends RawStateAccess {

    override def putState(key: String, data: Array[Byte]): Unit = api.putPrivateData(collection, key, data)

    override def getState(key: String): Array[Byte] = api.getPrivateData(collection, key)

    override def delState(key: String): Unit = api.delPrivateData(collection, key)

    override def getStateByPartialCompositeKey(key: CompositeKey): QueryResultsIterator[KeyValue] = api.getPrivateDataByPartialCompositeKey(collection, key)
}