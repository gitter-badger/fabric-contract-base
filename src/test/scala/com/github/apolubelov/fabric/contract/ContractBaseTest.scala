package com.github.apolubelov.fabric.contract

import java.nio.charset.StandardCharsets

import com.github.apolubelov.fabric.contract.annotation.{ContractInit, ContractOperation}
import com.github.apolubelov.fabric.contract.codec.{GsonCodec, TypeNameResolver}
import org.hyperledger.fabric.shim.ledger.CompositeKey
import org.hyperledger.fabric.shim.{Chaincode, ChaincodeStub}
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
@RunWith(classOf[JUnitRunner])
class ContractBaseTest extends FunSuite {

    private val NamesResolver = new TypeNameResolver() {
        override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

        override def resolveNameByType(clazz: Class[_]): String = "dummy"
    }

    import GsonCodec._

    val TEST_CONTRACT: ContractBase = new ContractBase(
        ContractCodecs(
            defaultTextCodec = GsonCodec(gsonOptions = _.encodeTypes(typeFieldName = "#TYPE#", typeNamesResolver = NamesResolver))
        )
    ) {

        @ContractInit
        def testInit(context: ContractContext, p1: String, p2: Int, p3: Float, p4: Double, asset: Dummy): Unit = {
            context.store.put("p1", p1)
            context.store.put("p2", p2)
            context.store.put("p3", p3)
            context.store.put("p4", p4)
            context.store.put("p5", asset)
        }

        @ContractOperation
        def testPutAsset(context: ContractContext, key: String, asset: Dummy): Unit = {
            context.store.put("k1", asset)
        }

        @ContractOperation
        def testGetAsset(context: ContractContext, key: String): ContractResponse = {
            context.store.get[Dummy](key).map(Success(_)).getOrElse(Error(s"No asset for key: $key"))
        }
    }

    private val DummyAssetJson = s"""{"name":"x","value":"y","#TYPE#":"dummy"}"""
    private val DummyAssetJsonUtf8Bytes = DummyAssetJson.getBytes(StandardCharsets.UTF_8)

    test("init with typed args") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getParameters).thenReturn(List("abc", "1", "2.2", "3.3", DummyAssetJson).asJava)

        val result = performAndLog(() => TEST_CONTRACT.init(api))
        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState("p1", "abc".getBytes(StandardCharsets.UTF_8))
        verify(api).putState("p2", "1".getBytes(StandardCharsets.UTF_8))
        verify(api).putState("p3", "2.2".getBytes(StandardCharsets.UTF_8))
        verify(api).putState("p4", "3.3".getBytes(StandardCharsets.UTF_8))
        verify(api).putState(mkAssetKey("dummy", "p5"), DummyAssetJsonUtf8Bytes)
    }

    //    test("put dummy asset [raw]") {
    //        val api = mock(classOf[ChaincodeStub])
    //        when(api.getFunction).thenReturn("testRawAsset")
    //        when(api.getParameters).thenReturn(List("k1", "dummy", DummyAssetJson).asJava)
    //
    //        val result = performAndLog(() => TEST_CONTRACT.invoke(api))
    //
    //        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)
    //
    //        verify(api).putState(mkAssetKey("dummy", "k1"), DummyAssetJsonUtf8Bytes)
    //    }

    test("put dummy asset") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testPutAsset")
        when(api.getParameters).thenReturn(List("k1", DummyAssetJson).asJava)

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)

        verify(api).putState(mkAssetKey("dummy", "k1"), DummyAssetJsonUtf8Bytes)
    }

    test("get dummy asset success") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testGetAsset")
        when(api.getParameters).thenReturn(List("k1").asJava)
        when(api.getState(mkAssetKey("dummy", "k1"))).thenReturn(DummyAssetJsonUtf8Bytes)

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.SUCCESS)
        assert(result.getPayload sameElements DummyAssetJsonUtf8Bytes)
    }

    test("get dummy asset error") {
        val api = mock(classOf[ChaincodeStub])
        when(api.getFunction).thenReturn("testGetAsset")
        when(api.getParameters).thenReturn(List("k1").asJava)
        when(api.getState(mkAssetKey("dummy", "k1"))).thenReturn(null)

        val result = performAndLog(() => TEST_CONTRACT.invoke(api))

        assert(result.getStatus == Chaincode.Response.Status.INTERNAL_SERVER_ERROR)
        assert(result.getMessage == "No asset for key: k1")
    }

    def mkAssetKey(aType: String, key: String): String = new CompositeKey(aType, key).toString

    def performAndLog(f: () => Chaincode.Response): Chaincode.Response = {
        val result = f()
        if (result != null) {
            println(s"Status: ${result.getStatus}")
            println(s"Message: ${result.getMessage}")
            println(s"Payload ${
                if (result.getPayload == null) {
                    "null"
                } else {
                    new String(result.getPayload, StandardCharsets.UTF_8)
                }
            }")
        }
        result
    }
}

case class Dummy(
    name: String,
    value: String
)
