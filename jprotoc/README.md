What is jprotoc?
================
jprotoc is a framework for building protoc plugins using Java. As a demo, jprotoc includes a Jdk8 generator, that
generates `CompletableFuture`-based client bindings for gRPC.

Creating a new protoc plugin
============================
To create a new proto plugin using jprotoc you need to:

1. Extend the `Generator` base class and implement a `main()` method.
2. Consume your plugin from the Maven protoc plugin.

See `Jdk8Generator.java` for a complete example.

## Implementing a plugin
Protoc plugins need to be in their own stand-alone Maven module due to the way the protoc Maven plugin consumes
protoc plugins. Documentation for protoc's data structures is in 
[plugin.proto](https://github.com/google/protobuf/blob/master/src/google/protobuf/compiler/plugin.proto) and
[descriptor.proto](https://github.com/google/protobuf/blob/master/src/google/protobuf/descriptor.proto).

Create a main class similar to this:
```java
public class MyGenerator extends Generator {
    public static void main(String[] args) {
        ProtocPlugin.generate(new MyGenerator());
    }

    @Override
    public Stream<PluginProtos.CodeGeneratorResponse.File> generate(PluginProtos.CodeGeneratorRequest request) 
        throws GeneratorException {
        
        // create a map from proto types to java types
        final ProtoTypeMap protoTypeMap = ProtoTypeMap.of(request.getProtoFileList());
    
        // set context attributes by extracting values from the request
        // use protoTypeMap to translate between proto types and java types
        Context ctx = new Context();
        
        // generate code from an embedded resource Mustache template
        String content = applyTemplate("myTemplate.mustache", context);
    
        // create a new file for protoc to write
        PluginProtos.CodeGeneratorResponse.File file = PluginProtos.CodeGeneratorResponse.File
            .newBuilder()
            .setName("fileName")
            .setContent(content)
            .build();
            
        return Collections.singletonList(file).stream();
    }
    
    private class Context {
        // attributes for use in your code template
    }
}
```

For your plugin, you will most likely want to iterate over the internal proto data structures, creating new Files as
you go. For convenience, jprotoc comes bundled with [Mustache.java](https://github.com/spullara/mustache.java) to make
authoring code templates easy. 

## Using your plugin with Maven
To execute your protoc plugin when Maven compiles, add a `<configuration>` section to the Maven protoc plugin in your
POM file. For more documentation, see the Maven protoc plugin's 
[usage documentation](https://www.xolstice.org/protobuf-maven-plugin/examples/protoc-plugin.html).

```xml
<configuration>
    <protocPlugins>
        <protocPlugin>
            <id>MyGenerator</id>
            <groupId>com.something</groupId>
            <artifactId>myPlugin</artifactId>
            <version>${project.version}</version>
            <mainClass>com.something.MyGenerator</mainClass>
        </protocPlugin>
    </protocPlugins>
</configuration>
```

Using the Jdk8 Protoc generator
===============================
1. Add the following to your POM:
    ```xml
    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.4.1.Final</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.5.0</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:3.0.2:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.2.0:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                            <goal>test-compile</goal>
                            <goal>test-compile-custom</goal>
                        </goals>
                        <configuration>
                            <protocPlugins>
                                <protocPlugin>
                                    <id>java8</id>
                                    <groupId>com.salesforce.grpc.contrib</groupId>
                                    <artifactId>jprotoc</artifactId>
                                    <version>${project.version}</version>
                                    <mainClass>com.salesforce.jprotoc.jdk8.Jdk8Generator</mainClass>
                                </protocPlugin>
                            </protocPlugins>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
    ```
2. Run a `mvn build` to generate the java 8 stubs.

3. Reference the java 8 client stubs like this:
    ```java
    MyServiceGrpc8.GreeterCompletableFutureStub stub = MyServiceGrpc8.newCompletableFutureStub(channel);
    ```