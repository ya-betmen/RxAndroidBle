package com.polidea.rxandroidble.internal.operations;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.os.DeadObjectException;
import android.support.annotation.NonNull;

import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble.exceptions.BleException;
import com.polidea.rxandroidble.exceptions.BleGattCallbackTimeoutException;
import com.polidea.rxandroidble.exceptions.BleGattOperationType;
import com.polidea.rxandroidble.internal.QueueOperation;
import com.polidea.rxandroidble.internal.connection.BluetoothGattProvider;
import com.polidea.rxandroidble.internal.connection.ConnectionStateChangeListener;
import com.polidea.rxandroidble.internal.connection.RxBleGattCallback;
import com.polidea.rxandroidble.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble.internal.util.BleConnectionCompat;

import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleTransformer;
import io.reactivex.functions.Action;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.DisposableSingleObserver;

import static com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState.CONNECTED;
import static com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState.CONNECTING;
import static com.polidea.rxandroidble.internal.DeviceModule.CONNECT_TIMEOUT;
import static com.polidea.rxandroidble.internal.connection.ConnectionComponent.NamedBooleans.AUTO_CONNECT;
import static com.polidea.rxandroidble.internal.util.DisposableUtil.disposableSingleEmitter;

public class ConnectOperation extends QueueOperation<BluetoothGatt> {

    private final BluetoothDevice bluetoothDevice;
    private final BleConnectionCompat connectionCompat;
    private final RxBleGattCallback rxBleGattCallback;
    private final BluetoothGattProvider bluetoothGattProvider;
    private final TimeoutConfiguration connectTimeout;
    private final boolean autoConnect;
    private final ConnectionStateChangeListener connectionStateChangedAction;

    @Inject
    ConnectOperation(
            BluetoothDevice bluetoothDevice,
            BleConnectionCompat connectionCompat,
            RxBleGattCallback rxBleGattCallback,
            BluetoothGattProvider bluetoothGattProvider,
            @Named(CONNECT_TIMEOUT) TimeoutConfiguration connectTimeout,
            @Named(AUTO_CONNECT) boolean autoConnect,
            ConnectionStateChangeListener connectionStateChangedAction) {
        this.bluetoothDevice = bluetoothDevice;
        this.connectionCompat = connectionCompat;
        this.rxBleGattCallback = rxBleGattCallback;
        this.bluetoothGattProvider = bluetoothGattProvider;
        this.connectTimeout = connectTimeout;
        this.autoConnect = autoConnect;
        this.connectionStateChangedAction = connectionStateChangedAction;
    }

    @Override
    protected void protectedRun(final ObservableEmitter<BluetoothGatt> emitter, final QueueReleaseInterface queueReleaseInterface) {
        final Action queueReleaseAction = new Action() {
            @Override
            public void run() {
                queueReleaseInterface.release();
            }
        };
        final DisposableSingleObserver<BluetoothGatt> disposableGattObserver = getConnectedBluetoothGatt()
                .compose(wrapWithTimeoutWhenNotAutoconnecting())
                // when there are no subscribers there is no point of continuing work -> next will be disconnect operation
                .doFinally(queueReleaseAction)
                .subscribeWith(disposableSingleEmitter(emitter));
        emitter.setDisposable(disposableGattObserver);

        if (autoConnect) {
            // with autoConnect the connection may be established after a really long time
            queueReleaseInterface.release();
        }
    }

    private SingleTransformer<BluetoothGatt, BluetoothGatt> wrapWithTimeoutWhenNotAutoconnecting() {
        return new SingleTransformer<BluetoothGatt, BluetoothGatt>() {
            @Override
            public Single<BluetoothGatt> apply(Single<BluetoothGatt> bluetoothGattSingle) {
                return autoConnect
                        ? bluetoothGattSingle
                        : bluetoothGattSingle
                        .timeout(connectTimeout.timeout, connectTimeout.timeoutTimeUnit, connectTimeout.timeoutScheduler,
                                prepareConnectionTimeoutError());
            }
        };
    }

    @NonNull
    private Single<BluetoothGatt> prepareConnectionTimeoutError() {
        return Single.fromCallable(new Callable<BluetoothGatt>() {
            @Override
            public BluetoothGatt call() {
                throw new BleGattCallbackTimeoutException(bluetoothGattProvider.getBluetoothGatt(), BleGattOperationType.CONNECTION_STATE);
            }
        });
    }

    /**
     * Emits BluetoothGatt and completes after connection is established.
     *
     * @return BluetoothGatt after connection reaches {@link com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState#CONNECTED}
     * state.
     * @throws com.polidea.rxandroidble.exceptions.BleDisconnectedException if connection was disconnected/failed before it was established.
     */
    @NonNull
    private Single<BluetoothGatt> getConnectedBluetoothGatt() {
        // start connecting the BluetoothGatt
        // note: Due to different Android BLE stack implementations it is not certain whether `connectGatt()` or `BluetoothGattCallback`
        // will emit BluetoothGatt first
        return Single.create(new SingleOnSubscribe<BluetoothGatt>() {

            @Override
            public void subscribe(final SingleEmitter<BluetoothGatt> emitter) throws Exception {
                final DisposableSingleObserver<BluetoothGatt> disposableGattObserver = getBluetoothGattAndChangeStatusToConnected()
                        // when the connected state will be emitted bluetoothGattProvider should contain valid Gatt
                        .delaySubscription(
                                rxBleGattCallback
                                        .getOnConnectionStateChange()
                                        .filter(new Predicate<RxBleConnection.RxBleConnectionState>() {
                                            @Override
                                            public boolean test(RxBleConnection.RxBleConnectionState rxBleConnectionState)
                                                    throws Exception {
                                                return rxBleConnectionState == CONNECTED;
                                            }
                                        })
                        )
                        // disconnect may happen even if the connection was not established yet
                        .mergeWith(rxBleGattCallback.<BluetoothGatt>observeDisconnect().firstOrError())
                        .firstOrError()
                        .subscribeWith(disposableSingleEmitter(emitter));

                emitter.setDisposable(disposableGattObserver);
                connectionStateChangedAction.onConnectionStateChange(CONNECTING);

                        /*
                        * Apparently the connection may be established fast enough to introduce a race condition so the subscription
                        * must be established first before starting the connection.
                        * https://github.com/Polidea/RxAndroidBle/issues/178
                        * */

                final BluetoothGatt bluetoothGatt = connectionCompat
                        .connectGatt(bluetoothDevice, autoConnect, rxBleGattCallback.getBluetoothGattCallback());
                        /*
                        * Update BluetoothGatt when connection is initiated. It is not certain
                        * if this or RxBleGattCallback.onConnectionStateChange will be first.
                        * */
                bluetoothGattProvider.updateBluetoothGatt(bluetoothGatt);
            }
        });
    }

    private Single<BluetoothGatt> getBluetoothGattAndChangeStatusToConnected() {
        return Single.fromCallable(
                new Callable<BluetoothGatt>() {
                    @Override
                    public BluetoothGatt call() {
                        connectionStateChangedAction.onConnectionStateChange(CONNECTED);
                        return bluetoothGattProvider.getBluetoothGatt();
                    }
                });
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleDisconnectedException(deadObjectException, bluetoothDevice.getAddress());
    }
}
