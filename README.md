# transit-clj

Transit is a data format and a set of libraries for conveying values between applications written in different languages. This library provides support for marshalling Transit data to/from Clojure.

* [Rationale](http://i-should-be-a-link)
* [API docs](http://cognitect.github.io/transit-clj/)
* [Specification](http://github.com/cognitect/transit-format)

This implementation's major.minor version number corresponds to the version of it supports.

_NOTE: Transit is a work in progress and may evolve based on feedback. As a result, while Transit is a great option for transferring data between applications, it should not yet be used for storing data durably over time. This recommendation will change when the specification is complete._

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
(require '[cognitect.transit :as transit])
(import [java.io ByteArrayInputStream ByteArrayOutputStream])

;; Write data to a stream
(def out (ByteArrayOutputStream. 4096))
(def writer (transit/writer out :json))
(transit/write writer "foo")
(transit/write writer {:a [1 2]})

;; Take a peek at the JSON
(.toString out)
;; => "{\"~#'\":\"foo\"} [\"^ \",\"~:a\",[1,2]]"

;; Read data from a stream
(def in (ByteArrayInputStream. (.toByteArray out)))
(def reader (transit/reader in :json))
(prn (transit/read reader))  ;; => "foo"
(prn (transit/read reader))  ;; => {:a [1 2]}
```

## Default Type Mapping

|Transit type|Write accepts|Read returns|
|------------|-------------|------------|
|null|nil|nil|
|string|java.lang.String|java.lang.String|
|boolean|java.lang.Boolean|java.lang.Boolean|
|integer|java.lang.Byte, java.lang.Short, java.lang.Integer, java.lang.Long|java.lang.Long|
|decimal|java.lang.Float, java.lang.Double|java.lang.Double|
|keyword|clojure.lang.Keyword|clojure.lang.Keyword|
|symbol|clojure.lang.Symbol|clojure.lang.Symbol|
|big decimal|java.math.BigDecimal|java.math.BigDecimal|
|big integer|clojure.lang.BigInt,java.math.BigInteger|clojure.lang.BigInt|
|time|java.util.Date|java.util.Date|
|uri|java.net.URI|java.net.URI|
|uuid|java.util.UUID|java.util.UUID|
|char|java.lang.Character|java.lang.Character|
|array|clojure.lang.IPersistentVector, java.util.List|clojure.lang.IPersistentVector|
|list|clojure.lang.ISeq|clojure.lang.ISeq|
|set|clojure.lang.IPersistentSet,java.util.Set|clojure.lang.IPersistentSet|
|map|clojure.lang.IPersistentMap,java.util.Map|clojure.lang.IPersistentMap|
|link|cognitect.transit.Link|cognitect.transit.Link|
|ratio +|clojure.lang.Ratio|clojure.lang.Ratio|

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
