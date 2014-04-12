# transit-clj

A Clojure library designed to ... well, that part is up to you.

## Usage

FIXME

## Testing

This project contains code which may be used to test all
implementations of transit. It will attempt to test each
implementation of transit that you have on your machine.

The only requirement for an implementation to be testable is that it
have a script named `roundtrip` in its `bin` directory. This script
must start a process which accepts transit data on standard in and
then returns transit data on standard out. The process should
roundtrip the incoming data and write it to standard out.

The script must take one argument which can be either `json` or
`msgpack` which will be used to setup the correct encoding.

To run a test, execute the following command in the transit-clj
directory.

```
lein run -m transit.verify
```

To test all implementations, run

```
bin/verify
```

Tests are currently limited to json and a few EDN examples.


### Improvements

Add bin/roundtrip scripts for the other implementations
Get it working with msgpack
Automate the process of cloning (and building if required) implementations
Add generative tests

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
