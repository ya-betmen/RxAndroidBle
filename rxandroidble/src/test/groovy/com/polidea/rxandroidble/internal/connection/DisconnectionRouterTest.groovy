package com.polidea.rxandroidble.internal.connection

import android.bluetooth.BluetoothGatt
import com.polidea.rxandroidble.RxBleAdapterStateObservable
import com.polidea.rxandroidble.exceptions.BleDisconnectedException
import com.polidea.rxandroidble.exceptions.BleGattException
import com.polidea.rxandroidble.exceptions.BleGattOperationType
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper
import io.reactivex.subjects.PublishSubject
import org.robospock.RoboSpecification
import spock.lang.Unroll

import static com.polidea.rxandroidble.RxBleAdapterStateObservable.BleAdapterState.*

class DisconnectionRouterTest extends RoboSpecification {

    String mockMacAddress = "1234"
    PublishSubject<RxBleAdapterStateObservable.BleAdapterState> mockAdapterStateSubject = PublishSubject.create()
    DisconnectionRouter objectUnderTest

    def createObjectUnderTest(boolean isBluetoothAdapterOnInitially) {
        def mockBleAdapterWrapper = Mock(RxBleAdapterWrapper)
        mockBleAdapterWrapper.isBluetoothEnabled() >> isBluetoothAdapterOnInitially
        objectUnderTest = new DisconnectionRouter(mockMacAddress, mockBleAdapterWrapper, mockAdapterStateSubject)
    }

    def "should emit exception from .as*Observable() when got one from .onDisconnectedException()"() {

        given:
        createObjectUnderTest(true)
        BleDisconnectedException testException = new BleDisconnectedException(mockMacAddress)
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        when:
        objectUnderTest.onDisconnectedException(testException)

        then:
        errorTestSubscriber.assertError(testException)

        and:
        valueTestSubscriber.assertValue(testException)
    }

    def "should emit exception from .asGenericObservable() when got one from .onDisconnectedException() even before subscription"() {

        given:
        createObjectUnderTest(true)
        BleDisconnectedException testException = new BleDisconnectedException(mockMacAddress)
        objectUnderTest.onDisconnectedException(testException)

        when:
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()

        then:
        errorTestSubscriber.assertError(testException)
    }

    def "should emit exception from .asExactObservable() when got one from .onDisconnectedException() even before subscription"() {

        given:
        createObjectUnderTest(true)
        BleDisconnectedException testException = new BleDisconnectedException(mockMacAddress)
        objectUnderTest.onDisconnectedException(testException)

        when:
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        then:
        valueTestSubscriber.assertValue(testException)
    }

    def "should emit exception from .as*Observable() when got one from .onGattConnectionStatusException()"() {

        given:
        createObjectUnderTest(true)
        BleGattException testException = new BleGattException(Mock(BluetoothGatt), BluetoothGatt.GATT_FAILURE, BleGattOperationType.CONNECTION_STATE)
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        when:
        objectUnderTest.onGattConnectionStateException(testException)

        then:
        errorTestSubscriber.assertError(testException)

        and:
        valueTestSubscriber.assertValue(testException)
    }

    def "should emit exception from .asGenericObservable() when got one from .onGattConnectionStatusException() even before subscription"() {

        given:
        createObjectUnderTest(true)
        BleGattException testException = new BleGattException(Mock(BluetoothGatt), BluetoothGatt.GATT_FAILURE, BleGattOperationType.CONNECTION_STATE)
        objectUnderTest.onGattConnectionStateException(testException)

        when:
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()

        then:
        errorTestSubscriber.assertError(testException)
    }

    def "should emit exception from .asExactObservable() when got one from .onGattConnectionStatusException() even before subscription"() {

        given:
        createObjectUnderTest(true)
        BleGattException testException = new BleGattException(Mock(BluetoothGatt), BluetoothGatt.GATT_FAILURE, BleGattOperationType.CONNECTION_STATE)
        objectUnderTest.onGattConnectionStateException(testException)

        when:
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        then:
        valueTestSubscriber.assertValue(testException)
    }

    @Unroll
    def "should emit exception from .as*Observable() when adapterStateObservable emits STATE_TURNING_ON/STATE_TURNING_OFF/STATE_OFF"() {

        given:
        createObjectUnderTest(true)
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        when:
        mockAdapterStateSubject.onNext(bleAdapterState)

        then:
        errorTestSubscriber.assertError({ BleDisconnectedException e -> e.bluetoothDeviceAddress == mockMacAddress })

        and:
        valueTestSubscriber.assertAnyOnNext { BleDisconnectedException e -> e.bluetoothDeviceAddress == mockMacAddress }

        and:
        valueTestSubscriber.assertValueCount(1)

        where:
        bleAdapterState << [ STATE_TURNING_ON, STATE_TURNING_OFF, STATE_OFF ]
    }

    @Unroll
    def "should emit exception from .asGenericObservable() when adapterStateObservable emits STATE_TURNING_ON/STATE_TURNING_OFF/STATE_OFF even before subscription"() {

        given:
        createObjectUnderTest(true)
        mockAdapterStateSubject.onNext(bleAdapterState)

        when:
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()

        then:
        errorTestSubscriber.assertError({ BleDisconnectedException e -> e.bluetoothDeviceAddress == mockMacAddress })

        where:
        bleAdapterState << [ STATE_TURNING_ON, STATE_TURNING_OFF, STATE_OFF ]
    }

    @Unroll
    def "should emit exception from .asExactObservable() when adapterStateObservable emits STATE_TURNING_ON/STATE_TURNING_OFF/STATE_OFF even before subscription"() {

        given:
        createObjectUnderTest(true)
        mockAdapterStateSubject.onNext(bleAdapterState)

        when:
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        then:
        valueTestSubscriber.assertAnyOnNext { BleDisconnectedException e -> e.bluetoothDeviceAddress == mockMacAddress }

        and:
        valueTestSubscriber.assertValueCount(1)

        where:
        bleAdapterState << [ STATE_TURNING_ON, STATE_TURNING_OFF, STATE_OFF ]
    }

    def "should emit exception from .asGenericObservable() when RxBleAdapterWrapper.isEnabled() returns false"() {

        given:
        createObjectUnderTest(false)

        when:
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()

        then:
        errorTestSubscriber.assertError({ BleDisconnectedException e -> e.bluetoothDeviceAddress == mockMacAddress })
    }

    def "should emit exception from .asExactObservable() when RxBleAdapterWrapper.isEnabled() returns false"() {

        given:
        createObjectUnderTest(false)

        when:
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        then:
        valueTestSubscriber.assertAnyOnNext { BleDisconnectedException e -> e.bluetoothDeviceAddress == mockMacAddress }

        and:
        valueTestSubscriber.assertValueCount(1)
    }

    def "should not emit exception from .asObservable() when adapterStateObservable emits STATE_ON"() {

        given:
        createObjectUnderTest(true)
        def errorTestSubscriber = objectUnderTest.asErrorOnlyObservable().test()
        def valueTestSubscriber = objectUnderTest.asValueOnlyObservable().test()

        when:
        mockAdapterStateSubject.onNext(RxBleAdapterStateObservable.BleAdapterState.STATE_ON)

        then:
        errorTestSubscriber.assertNoErrors()

        and:
        valueTestSubscriber.assertNoValues()
    }
}
