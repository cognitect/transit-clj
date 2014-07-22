{:namespaces
 ({:source-url nil,
   :wiki-url "cognitect.transit-api.html",
   :name "cognitect.transit",
   :doc
   "An implementation of the transit-format for Clojure built\non top of the transit-java library."}),
 :vars
 ({:raw-source-url nil,
   :name "->Reader",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 240,
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
   :line 109,
   :var-type "function",
   :arglists ([w]),
   :doc
   "Positional factory function for class cognitect.transit.Writer.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/->Writer"}
  {:raw-source-url nil,
   :name "default-read-handlers",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 158,
   :var-type "function",
   :arglists ([]),
   :doc
   "Returns a map of default ReadHandlers for\nClojure types. Java types are handled\nby the default ReadHandlers provided by the\ntransit-java library.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/default-read-handlers"}
  {:raw-source-url nil,
   :name "default-write-handlers",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 66,
   :var-type "function",
   :arglists ([]),
   :doc
   "Returns a map of default WriteHandlers for\nClojure types. Java types are handled\nby the default WriteHandlers provided by the\ntransit-java library.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/default-write-handlers"}
  {:raw-source-url nil,
   :name "map-builder",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 220,
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
   :line 43,
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
   :line 270,
   :var-type "function",
   :arglists ([reader]),
   :doc "Reads a value from a reader.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/read"}
  {:raw-source-url nil,
   :name "read-array-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 150,
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
   :line 135,
   :var-type "function",
   :arglists ([from-rep]),
   :doc
   "Creates a transit ReadHandler whose fromRep\nmethod invokes the provided fn.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/read-handler"}
  {:raw-source-url nil,
   :name "read-map-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 142,
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
   :line 242,
   :var-type "function",
   :arglists ([in type] [in type opts]),
   :doc
   "Creates a reader over the provided source `in` using\nthe specified format, one of: :msgpack, :json or :json-verbose.\n\nAn optional opts map may be passed. Supported options are:\n\n:handlers - a map of tags to ReadHandler instances, they are merged\nwith the Clojure default-read-handlers and then with the default ReadHandlers\nprovided by transit-java.\n\n:default-handler - an instance of DefaultReadHandler, used to process\ntransit encoded values for which there is no other ReadHandler; if\n:default-handler is not specified, non-readable values are returned\nas TaggedValues.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/reader"}
  {:raw-source-url nil,
   :name "record-read-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 291,
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
   :line 301,
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
   :line 275,
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
   :line 284,
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
   :line 39,
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
   :line 127,
   :var-type "function",
   :arglists ([writer o]),
   :doc "Writes a value to a transit writer.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/write"}
  {:raw-source-url nil,
   :name "write-handler",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 51,
   :var-type "function",
   :arglists
   ([tag-fn rep-fn]
    [tag-fn rep-fn str-rep-fn]
    [tag-fn rep-fn str-rep-fn verbose-handler-fn]),
   :doc
   "Creates a transit WriteHandler whose tag, rep,\nstringRep, and verboseWriteHandler methods\ninvoke the provided fns.",
   :namespace "cognitect.transit",
   :wiki-url
   "/cognitect.transit-api.html#cognitect.transit/write-handler"}
  {:raw-source-url nil,
   :name "writer",
   :file "src/cognitect/transit.clj",
   :source-url nil,
   :line 111,
   :var-type "function",
   :arglists ([out type] [out type opts]),
   :doc
   "Creates a writer over the privided destination `out` using\nthe specified format, one of: :msgpack, :json or :json-verbose.\n\nAn optional opts map may be passed. Supported options are:\n\n:Handlers - a map of types to WriteHandler instances, they are merged\nwith the default-handlers and then with the default handlers\nprovided by transit-java.",
   :namespace "cognitect.transit",
   :wiki-url "/cognitect.transit-api.html#cognitect.transit/writer"})}
