{:namespaces
 ({:source-url nil,
   :wiki-url "cognitect.transit-api.html",
   :name "cognitect.transit",
   :doc
   "An implementation of the transit-format for Clojure built\non top of the transit-java library."}),
 :vars
 ({:raw-source-url nil,
   :name "->HandlerMapContainer",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 29,
   :var-type "function",
   :arglists ([m]),
   :doc
   "Positional factory function for class cognitect.transit.HandlerMapContainer.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/->HandlerMapContainer"}
  {:raw-source-url nil,
   :name "->Reader",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 262,
   :var-type "function",
   :arglists ([r]),
   :doc
   "Positional factory function for class cognitect.transit.Reader.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/->Reader"}
  {:raw-source-url nil,
   :name "->Writer",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 126,
   :var-type "function",
   :arglists ([w]),
   :doc
   "Positional factory function for class cognitect.transit.Writer.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/->Writer"}
  {:file "src/cognitect/transit.clj",
   :raw-source-url nil,
   :source-url nil,
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/default-read-handlers",
   :namespace "cognitect.transit",
   :line 178,
   :var-type "var",
   :doc
   "Returns a map of default ReadHandlers for\nClojure types. Java types are handled\nby the default ReadHandlers provided by the\ntransit-java library.",
   :name "default-read-handlers"}
  {:file "src/cognitect/transit.clj",
   :raw-source-url nil,
   :source-url nil,
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/default-write-handlers",
   :namespace "cognitect.transit",
   :line 84,
   :var-type "var",
   :doc
   "Returns a map of default WriteHandlers for\nClojure types. Java types are handled\nby the default WriteHandlers provided by the\ntransit-java library.",
   :name "default-write-handlers"}
  {:raw-source-url nil,
   :name "list-builder",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 252,
   :var-type "function",
   :arglists ([]),
   :doc
   "Creates an ArrayBuilder that makes Clojure-\ncompatible lists.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/list-builder"}
  {:raw-source-url nil,
   :name "map-builder",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 242,
   :var-type "function",
   :arglists ([]),
   :doc "Creates a MapBuilder that makes Clojure-\ncompatible maps.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/map-builder"}
  {:raw-source-url nil,
   :name "nsed-name",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 50,
   :var-type "function",
   :arglists ([kw-or-sym]),
   :doc
   "Convert a keyword or symbol to a string in\nnamespace/name format.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/nsed-name"}
  {:raw-source-url nil,
   :name "read",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 293,
   :var-type "function",
   :arglists ([reader]),
   :doc
   "Reads a value from a reader. Throws a RuntimeException when\nthe reader's InputStream is empty.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/read"}
  {:raw-source-url nil,
   :name "read-array-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 169,
   :var-type "function",
   :arglists ([from-rep array-reader]),
   :doc
   "Creates a Transit ArrayReadHandler whose fromRep\nand arrayReader methods invoke the provided fns.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/read-array-handler"}
  {:raw-source-url nil,
   :name "read-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 154,
   :var-type "function",
   :arglists ([from-rep]),
   :doc
   "Creates a transit ReadHandler whose fromRep\nmethod invokes the provided fn.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/read-handler"}
  {:raw-source-url nil,
   :name "read-handler-map",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 332,
   :var-type "function",
   :arglists ([custom-handlers]),
   :doc
   "Returns a HandlerMapContainer containing a ReadHandlerMap\ncontaining all the default handlers for Clojure and Java and any\ncustom handlers that you supply, letting you store the return value\nand pass it to multiple invocations of reader.  This can be more\nefficient than repeatedly handing the same raw map of tags -> custom\nhandlers to reader.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/read-handler-map"}
  {:raw-source-url nil,
   :name "read-map-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 161,
   :var-type "function",
   :arglists ([from-rep map-reader]),
   :doc
   "Creates a Transit MapReadHandler whose fromRep\nand mapReader methods invoke the provided fns.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/read-map-handler"}
  {:raw-source-url nil,
   :name "reader",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 264,
   :var-type "function",
   :arglists ([in type] [in type {:keys [handlers default-handler]}]),
   :doc
   "Creates a reader over the provided source `in` using\nthe specified format, one of: :msgpack, :json or :json-verbose.\n\nAn optional opts map may be passed. Supported options are:\n\n:handlers - a map of tags to ReadHandler instances, they are merged\nwith the Clojure default-read-handlers and then with the default ReadHandlers\nprovided by transit-java.\n\n:default-handler - an instance of DefaultReadHandler, used to process\ntransit encoded values for which there is no other ReadHandler; if\n:default-handler is not specified, non-readable values are returned\nas TaggedValues.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/reader"}
  {:raw-source-url nil,
   :name "record-read-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 315,
   :var-type "function",
   :arglists ([type]),
   :doc "Creates a ReadHandler for a record type",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/record-read-handler"}
  {:raw-source-url nil,
   :name "record-read-handlers",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 325,
   :var-type "function",
   :arglists ([& types]),
   :doc "Creates a map of record type tags to ReadHandlers",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/record-read-handlers"}
  {:raw-source-url nil,
   :name "record-write-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 299,
   :var-type "function",
   :arglists ([type]),
   :doc "Creates a WriteHandler for a record type",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/record-write-handler"}
  {:raw-source-url nil,
   :name "record-write-handlers",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 308,
   :var-type "function",
   :arglists ([& types]),
   :doc "Creates a map of record types to WriteHandlers",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/record-write-handlers"}
  {:raw-source-url nil,
   :name "tagged-value",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 46,
   :var-type "function",
   :arglists ([tag rep]),
   :doc "Creates a TaggedValue object.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/tagged-value"}
  {:raw-source-url nil,
   :name "write",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 146,
   :var-type "function",
   :arglists ([writer o]),
   :doc "Writes a value to a transit writer.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/write"}
  {:raw-source-url nil,
   :name "write-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 62,
   :var-type "function",
   :arglists
   ([tag-fn rep-fn]
    [tag-fn rep-fn str-rep-fn]
    [tag-fn rep-fn str-rep-fn verbose-handler-fn]),
   :doc
   "Creates a transit WriteHandler whose tag, rep,\nstringRep, and verboseWriteHandler methods\ninvoke the provided fns.\n\nIf a non-fn is passed as an argument, implemented\nhandler method returns the value unaltered.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/write-handler"}
  {:raw-source-url nil,
   :name "write-handler-map",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 343,
   :var-type "function",
   :arglists ([custom-handlers]),
   :doc
   "Returns a HandlerMapContainer containing a WriteHandlerMap\ncontaining all the default handlers for Clojure and Java and any\ncustom handlers that you supply, letting you store the return value\nand pass it to multiple invocations of writer.  This can be more\nefficient than repeatedly handing the same raw map of types -> custom\nhandlers to writer.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/write-handler-map"}
  {:raw-source-url nil,
   :name "writer",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 128,
   :var-type "function",
   :arglists ([out type] [out type {:keys [handlers]}]),
   :doc
   "Creates a writer over the provided destination `out` using\nthe specified format, one of: :msgpack, :json or :json-verbose.\n\nAn optional opts map may be passed. Supported options are:\n\n:handlers - a map of types to WriteHandler instances, they are merged\nwith the default-handlers and then with the default handlers\nprovided by transit-java.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/writer"})}
