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

[MIT](LICENSE)
