# transit-clj

Transit is a data format and a set of libraries for conveying values between applications written in different languages. This library provides support for marshalling Transit data to/from Clojure.

* [Rationale](http://i-should-be-a-link)
* [API docs](http://cognitect.github.io/transit-clj/)
* [Specification](http://github.com/cognitect/transit-format)

## Releases and Dependency Information

* Latest release: TBD
* [All Released Versions](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.cognitect%22%20AND%20a%3A%22transit-clj%22)

[Maven](http://maven.apache.org/) dependency information:

```xml
<dependency>
  <groupId>com.cognitect</groupId>
  <artifactId>transit-clj</artifactId>
  <version>0.8.TBD</version>
</dependency>
```

[Leiningen](http://leiningen.org/) dependency information:

```clojure
[com.cognitect/transit-clj "0.8.TBD"]
```

## Usage

```clojure
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import com.cognitect.transit.TransitFactory;
import com.cognitect.transit.Reader;
import com.cognitect.transit.Writer;

// Write the data to a stream
ByteArrayOutputStream baos = new ByteArrayOutputStream();
Writer writer = TransitFactory.writer(TransitFactory.Format.MSGPACK, baos);
writer.write(data);

// Read the data from a stream
ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
Reader reader = TransitFactory.reader(TransitFactory.Format.MSGPACK, bais);
Object data = reader.read();
```

## Default Type Mapping

|Transit type|Write accepts|Read returns|
|------------|-------------|------------|
|null|null|null|
|string|java.lang.String|java.lang.String|
|boolean|java.lang.Boolean|java.lang.Boolean|
|integer|java.lang.Byte, java.lang.Short, java.lang.Integer, java.lang.Long|java.lang.Long|
|decimal|java.lang.Float, java.lang.Double|java.lang.Double|
|keyword|cognitect.transit.Keyword|cognitect.transit.Keyword|
|symbol|cognitect.transit.Symbol|cognitect.transit.Symbol|
|big decimal|java.math.BigDecimal|java.math.BigDecimal|
|big integer|java.math.BigInteger|java.math.BigInteger|
|time|java.util.Date|long|
|uri|java.net.URI, cognitect.transit.URI|cognitect.transit.URI|
|uuid|java.util.UUID|java.util.UUID|
|char|java.lang.Character|java.lang.Character|
|array|Object[],primitive arrays|java.util.ArrayList|
|list|java.util.List|java.util.LinkedList|
|set|java.util.Set|java.util.HashSet|
|map|java.util.Map|java.util.HashMap|
|link|cognitect.transit.Link|cognitect.transit.Link|
|tagged value|cognitect.transit.TaggedValue|cognitect.transit.TaggedValue|
|ratio +|cognitect.transit.Ratio|cognitect.transit.Ratio|

\+ Extension using tagged values


## Contributing 

Please discuss potential problems or enhancements on the [transit-format mailing list](https://groups.google.com/forum/#!forum/transit-format). Issues should be filed using GitHub issues for this project.

Contributing to Cognitect projects requires a signed [Cognitect Contributor Agreement](http://cognitect.com/contributing).


## Copyright and License

Copyright © 2014 Cognitect

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
