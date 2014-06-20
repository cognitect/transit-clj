# transit-clj

A Clojure library designed to ... well, that part is up to you.

## Usage

FIXME

## Deployment

Running `build/package` will build transit-clj, install to the local
maven reposistory, and prepare for deployment.

Running `build/deploy` will run `build/package`, and push to the
'datomic-maven' S3 bucket.  To deploy, you'll need the AWS CLI tools
installed and configured with permissions to access this bucket (see
https://aws.amazon.com/cli/).

## Testing Tansit implementations

This project contains code which may be used to test all
implementations of transit.

To run a test, execute the following command in the `transit-clj`
directory.

```
bin/verify -impls clj -enc json
```

This will test the `json` version of the `transit-clj` implementation.

The `-enc` option can be either `json` or `msgpack`. If it is omitted
then both with be tested.

The `-impls` option can be any languange implementation. `clj` will
test `transit-clj` and `ruby` will test `transit-ruby`. If this option
is omitted then it will attempt to test all implementations. Multiple
languages can be specified.

```
bin/verify -impls clj ruby
```

To test everything run:

```
bin/verify
```

There is also a `-gen` option to run generative tests. The following usage

```
bin/verify -gen 100
```

will generate 100 random edn structures. There are still some issues
around equality testing that need to be fixed to eliminate false
positives.

Timing information can also be collected for each implementation.

```
bin/verify -time
```

Tests are currently limited to json.


### Test requirements

Testing `transit-clj` requires Maven.

Testing `transit-java` requires Maven.

Testing `transit-ruby` requires Ruby 1.9 and Bundler `gem install bundler`.


### Supporting testing

The only requirement for an implementation to be testable is that it
have a script named `roundtrip` in its `bin` directory. This script
must start a process which accepts transit data on standard in and
returns transit data on standard out. The process should read the
transit data, then write it to standard out.

The script must take one argument which can be either `json` or
`msgpack` which will be used to setup the correct encoding.


### Improvements

Add bin/roundtrip scripts for the other implementations
Get it working with msgpack
Improve equality testing for generative tests (compariting floats returned from Ruby)

### Building the simple-examples

To build the simple examples, simply make sure the software is up to date
and run the script bin/exemplars. This will generate a large number of example
files in simple-examples. By convention we keep the latest versions of the
examples files checked in in the transit repo.


## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
