# hbase-region-inspector

A visual dashboard of HBase region statistics.

![](screenshot/hbase-region-inspector.png)

## Usage

Download the executable binary that matches the version of your HBase cluster
and start it with the following command-line arguments.

```
usage: hbase-region-inspector [OPTIONS] ┌ QUORUM[/ZKPORT] ┐ PORT [INTERVAL]
                                        └ CONFIG_FILE     ┘
  Options
    --admin       Enable drag-and-drop interface
    --no-system   Hide system tables
    --help        Show this message
```

### Accessing secured cluster

To access a secured HBase cluster, you have to prepare the following
configuration files:

- The main properties file
- JAAS login configuration
- Kerberos configuration (usually `/etc/krb5.conf`)
- Kerberos keytab (optional, but recommended)

You can find the examples in [conf-examples](conf-examples/).

### Environment variables

- `DEBUG` - Enable debug logs when set
- `JVM_OPTS` - JVM options

## Development

### Prerequisites

- [NPM](https://www.npmjs.com/)
- [Leiningen](https://github.com/technomancy/leiningen)

```sh
# Using Homebrew on Mac OS X
brew install npm leiningen
```

### Setting up REPL on tmux panes

```sh
# For HBase 0.98 and above
./hacking

# HBase 0.94 (CDH4)
./hacking cdh4
```

### Build

```sh
# For HBase 0.98 and above
make

# HBase 0.94 (CDH4)
make profile=cdh4
```

## License

This software is licensed under the [Apache 2 license](LICENSE.txt), quoted below.

Copyright 2015 Kakao Corp. <http://www.kakaocorp.com>

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
