/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

@SuppressWarnings("Duplicates")
public class EndToEndTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        GreeterGrpc.GreeterImplBase svc = new GreeterGrpcRx.GreeterImplBase() {

            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return rxRequest.map(protoRequest -> greet("Hello", protoRequest));
            }

            @Override
            public Observable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return rxRequest.flatMapObservable(protoRequest -> Observable.just(
                        greet("Hello", protoRequest),
                        greet("Hi", protoRequest),
                        greet("Greetings", protoRequest)));
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Observable<HelloRequest> rxRequest) {
                return rxRequest
                        .map(HelloRequest::getName)
                        .toList()
                        .map(names -> greet("Hello", String.join(" and ", names)));
            }

            @Override
            public Observable<HelloResponse> sayHelloBothStream(Observable<HelloRequest> rxRequest) {
                return rxRequest
                        .map(HelloRequest::getName)
                        .buffer(2)
                        .map(names -> greet("Hello", String.join(" and ", names)));
            }

            private HelloResponse greet(String greeting, HelloRequest request) {
                return greet(greeting, request.getName());
            }

            private HelloResponse greet(String greeting, String name) {
                return HelloResponse.newBuilder().setMessage(greeting + " " + name).build();
            }
        };

        server = InProcessServerBuilder.forName("e2e").addService(svc).build().start();
        channel = InProcessChannelBuilder.forName("e2e").usePlaintext(true).build();
    }

    @AfterClass
    public static void stopServer() {
        server.shutdown();
        channel.shutdown();
    }

    @Test
    public void oneToOne() throws IOException {
        GreeterGrpcRx.RxGreeterStub stub = GreeterGrpcRx.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Single<HelloResponse> resp = stub.sayHello(req);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertValue("Hello rxjava");
    }

    @Test
    public void oneToMany() throws IOException {
        GreeterGrpcRx.RxGreeterStub stub = GreeterGrpcRx.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Observable<HelloResponse> resp = stub.sayHelloRespStream(req);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertValues("Hello rxjava", "Hi rxjava", "Greetings rxjava");
    }

    @Test
    public void manyToOne() throws Exception {
        GreeterGrpcRx.RxGreeterStub stub = GreeterGrpcRx.newRxStub(channel);
        Observable<HelloRequest> req = Observable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Single<HelloResponse> resp = stub.sayHelloReqStream(req);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertValue("Hello a and b and c");
    }

    @Test
    public void manyToMany() throws Exception {
        GreeterGrpcRx.RxGreeterStub stub = GreeterGrpcRx.newRxStub(channel);
        Observable<HelloRequest> req = Observable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        Observable<HelloResponse> resp = stub.sayHelloBothStream(req);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertValues("Hello a and b", "Hello c and d", "Hello e");
        testObserver.assertComplete();
    }
}